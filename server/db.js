// Storage for everything that must survive a restart: customers, their fingerprint passkeys,
// linked cards and notification subscriptions.
//
// With DATABASE_URL set it uses Postgres (Render's free database, or any Postgres).
// Without it, it keeps data in memory, which is fine for local testing only.
// Payment requests are short-lived (2 minutes) and live in memory in server.js.

function memoryStore() {
  const customers = new Map();
  const credentials = new Map();
  const cards = new Map();
  const subs = new Map();
  return {
    kind: 'memory',
    async init() {},
    async createCustomer(c) {
      if ([...customers.values()].some((x) => x.account_number === c.account_number)) {
        const e = new Error('duplicate'); e.code = 'DUPLICATE'; throw e;
      }
      customers.set(c.id, { ...c });
      return { ...c };
    },
    async getCustomer(id) { const c = customers.get(id); return c ? { ...c } : null; },
    async getCustomerByAccount(acct) { const c = [...customers.values()].find((x) => x.account_number === acct); return c ? { ...c } : null; },
    async listCustomers() { return [...customers.values()].sort((a, b) => b.created_at - a.created_at).map((c) => ({ ...c })); },
    async updatePrefs(id, lang, voice) { const c = customers.get(id); if (c) { c.lang = lang; c.voice = voice; } },
    async addCredential(cr) { credentials.set(cr.id, { ...cr }); },
    async getCredential(id) { const c = credentials.get(id); return c ? { ...c } : null; },
    async credentialsFor(cid) { return [...credentials.values()].filter((c) => c.customer_id === cid); },
    async linkCard(token, cid) { cards.set(token, cid); },
    async cardOwner(token) { return cards.get(token) || null; },
    async saveSubscription(cid, s) { subs.set(s.endpoint, { customer_id: cid, ...s }); },
    async subscriptionsFor(cid) { return [...subs.values()].filter((s) => s.customer_id === cid); },
    async deleteSubscription(endpoint) { subs.delete(endpoint); },
  };
}

function postgresStore(url) {
  const { Pool } = require('pg');
  const pool = new Pool({
    connectionString: url,
    ssl: process.env.DATABASE_SSL === 'true' ? { rejectUnauthorized: false } : undefined,
    max: 5,
  });
  const q = (text, params) => pool.query(text, params);
  const row = (c) => c && ({ ...c, created_at: Number(c.created_at) });
  return {
    kind: 'postgres',
    async init() {
      await q(`
        CREATE TABLE IF NOT EXISTS customers (
          id TEXT PRIMARY KEY,
          tapnod_id TEXT UNIQUE NOT NULL,
          name TEXT NOT NULL,
          account_number TEXT UNIQUE NOT NULL,
          phone TEXT NOT NULL DEFAULT '',
          password_hash TEXT NOT NULL,
          lang TEXT NOT NULL DEFAULT 'en',
          voice TEXT NOT NULL DEFAULT '',
          created_at BIGINT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS credentials (
          id TEXT PRIMARY KEY,
          customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
          public_key TEXT NOT NULL,
          alg INTEGER NOT NULL,
          created_at BIGINT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS cards (
          token TEXT PRIMARY KEY,
          customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE
        );
        CREATE TABLE IF NOT EXISTS subscriptions (
          endpoint TEXT PRIMARY KEY,
          customer_id TEXT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
          p256dh TEXT NOT NULL,
          auth TEXT NOT NULL
        );`);
    },
    async createCustomer(c) {
      try {
        await q(`INSERT INTO customers (id, tapnod_id, name, account_number, phone, password_hash, lang, voice, created_at)
                 VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
          [c.id, c.tapnod_id, c.name, c.account_number, c.phone, c.password_hash, c.lang, c.voice, c.created_at]);
      } catch (e) {
        if (e.code === '23505') { const d = new Error('duplicate'); d.code = 'DUPLICATE'; throw d; }
        throw e;
      }
      return c;
    },
    async getCustomer(id) { return row((await q('SELECT * FROM customers WHERE id=$1', [id])).rows[0]) || null; },
    async getCustomerByAccount(acct) { return row((await q('SELECT * FROM customers WHERE account_number=$1', [acct])).rows[0]) || null; },
    async listCustomers() { return (await q('SELECT * FROM customers ORDER BY created_at DESC')).rows.map(row); },
    async updatePrefs(id, lang, voice) { await q('UPDATE customers SET lang=$2, voice=$3 WHERE id=$1', [id, lang, voice]); },
    async addCredential(cr) {
      await q(`INSERT INTO credentials (id, customer_id, public_key, alg, created_at) VALUES ($1,$2,$3,$4,$5)
               ON CONFLICT (id) DO UPDATE SET customer_id=$2, public_key=$3, alg=$4`,
        [cr.id, cr.customer_id, cr.public_key, cr.alg, cr.created_at]);
    },
    async getCredential(id) { return (await q('SELECT * FROM credentials WHERE id=$1', [id])).rows[0] || null; },
    async credentialsFor(cid) { return (await q('SELECT * FROM credentials WHERE customer_id=$1', [cid])).rows; },
    async linkCard(token, cid) {
      await q('INSERT INTO cards (token, customer_id) VALUES ($1,$2) ON CONFLICT (token) DO UPDATE SET customer_id=$2', [token, cid]);
    },
    async cardOwner(token) { const r = (await q('SELECT customer_id FROM cards WHERE token=$1', [token])).rows[0]; return r ? r.customer_id : null; },
    async saveSubscription(cid, s) {
      await q(`INSERT INTO subscriptions (endpoint, customer_id, p256dh, auth) VALUES ($1,$2,$3,$4)
               ON CONFLICT (endpoint) DO UPDATE SET customer_id=$2, p256dh=$3, auth=$4`,
        [s.endpoint, cid, s.keys.p256dh, s.keys.auth]);
    },
    async subscriptionsFor(cid) {
      return (await q('SELECT * FROM subscriptions WHERE customer_id=$1', [cid])).rows
        .map((r) => ({ endpoint: r.endpoint, keys: { p256dh: r.p256dh, auth: r.auth } }));
    },
    async deleteSubscription(endpoint) { await q('DELETE FROM subscriptions WHERE endpoint=$1', [endpoint]); },
  };
}

module.exports = process.env.DATABASE_URL ? postgresStore(process.env.DATABASE_URL) : memoryStore();
