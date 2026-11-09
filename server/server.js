// Tapnod demo backend + customer web app.
// No dependencies: plain Node (18+). Run with `node server.js`.
//
// The POS app talks to /api/pos/* (contract in the main README).
// The customer's phone opens / in the browser and talks to /api/customer/*.
// Everything lives in memory, which is fine for a demo.

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const push = require('./push');
const tts = require('./tts');
const i18n = require('./public/i18n');

const PORT = process.env.PORT || 3000;
// Must match "Card token secret" in the POS settings (the POS default is the same).
const TOKEN_SECRET = process.env.TOKEN_SECRET || 'tapnod-demo-secret';
// Demo shortcut: any card the server doesn't know is linked to this customer on first use.
// Empty = unknown cards get "Card not linked" (the real behaviour).
const DEMO_AUTO_LINK = process.env.DEMO_AUTO_LINK ?? 'cus_amaka';
// The test card the POS uses for "Simulate tap" and "Accept any tap".
const DEMO_CARD_NUMBER = '5399000000004821';
const REQUEST_TTL_MS = 120_000;
const LANGS = Object.keys(i18n.LANGS);

const vapid = push.vapidFromSecret(process.env.VAPID_SECRET || TOKEN_SECRET);

const customers = [
  { id: 'cus_amaka', name: 'Amaka Okafor', note: 'Smartphone, screen reader on' },
  { id: 'cus_ngozi', name: 'Mama Ngozi', note: 'Button phone, call to approve' },
  { id: 'cus_tunde', name: 'Tunde Bakare', note: 'Smartphone' },
];

const cards = new Map(); // card_token -> customer id
const requests = new Map(); // request id -> request
const prefs = new Map(); // customer id -> { lang, voice }
const subscriptions = new Map(); // customer id -> Map(endpoint -> push subscription)

// Pre-link the demo card to Amaka, so a fresh server (or a free host that
// restarted while asleep) works straight away without the Link card step.
const demoToken = crypto.createHmac('sha256', TOKEN_SECRET).update(DEMO_CARD_NUMBER).digest('hex');
cards.set(demoToken, 'cus_amaka');

/* ---------------- helpers ---------------- */

function send(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

function readJson(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 1e5) req.destroy();
    });
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve(null); }
    });
  });
}

const STATIC = {
  '/': ['index.html', 'text/html; charset=utf-8'],
  '/index.html': ['index.html', 'text/html; charset=utf-8'],
  '/i18n.js': ['i18n.js', 'application/javascript; charset=utf-8'],
  '/sw.js': ['sw.js', 'application/javascript; charset=utf-8'],
  '/manifest.webmanifest': ['manifest.webmanifest', 'application/manifest+json'],
  '/icon.svg': ['icon.svg', 'image/svg+xml'],
};

function refresh(r) {
  if (r.status === 'pending' && Date.now() > r.expires_at_ms) r.status = 'expired';
  return r;
}

const prefsFor = (id) => prefs.get(id) || { lang: 'en', voice: '' };

function forCustomer(r) {
  const p = prefsFor(r.customer_id);
  const audio = r.audio && r.audio.key === p.lang + '|' + p.voice ? r.audio.url : null;
  return {
    request_id: r.request_id,
    status: r.status,
    amount_kobo: r.amount_kobo,
    currency: r.currency,
    merchant_name: r.merchant_name,
    card_last4: r.card_last4,
    card_scheme: r.card_scheme,
    reference: r.reference,
    expires_at_ms: r.expires_at_ms,
    audio_url: audio,
  };
}

/* ---------------- side effects when a charge arrives ---------------- */

// Phone notification in the customer's language.
async function notify(r) {
  const subs = subscriptions.get(r.customer_id);
  if (!subs || !subs.size) return;
  const { lang } = prefsFor(r.customer_id);
  const a = i18n.moneyText(r.amount_kobo);
  const data = {
    title: i18n.t(lang, 'notifTitle', { a, m: r.merchant_name }),
    body: i18n.t(lang, 'notifBody'),
    request_id: r.request_id,
    url: '/?c=' + r.customer_id,
  };
  await Promise.all([...subs.values()].map(async (sub) => {
    try {
      const status = await push.send(sub, data, vapid);
      if (status === 404 || status === 410) subs.delete(sub.endpoint); // phone unsubscribed
      else if (status >= 400) console.warn('push failed', status);
    } catch (e) {
      console.warn('push error', e.message);
    }
  }));
}

// Nigerian voice audio, made on the server so it's ready when the phone shows the payment.
async function prepareAudio(r) {
  if (!tts.enabled()) return;
  const { lang, voice } = prefsFor(r.customer_id);
  const key = lang + '|' + voice;
  try {
    const url = await tts.speak(i18n.sayFor(lang, 'pending', r).spoken, voice || undefined);
    r.audio = { key, url };
  } catch (e) {
    console.warn('voice failed', e.message);
  }
}

/* ---------------- routes ---------------- */

async function route(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const m = req.method;
  let match;

  if (m === 'GET' && STATIC[p]) {
    const [file, type] = STATIC[p];
    const headers = { 'Content-Type': type, 'Cache-Control': 'no-cache' };
    if (file === 'sw.js') headers['Service-Worker-Allowed'] = '/';
    res.writeHead(200, headers);
    return res.end(fs.readFileSync(path.join(__dirname, 'public', file)));
  }

  if (m === 'GET' && p === '/api/health') return send(res, 200, { ok: true });

  if (m === 'GET' && p === '/api/config') {
    return send(res, 200, { push_public_key: vapid.publicKey, nigerian_voices: tts.enabled(), languages: LANGS });
  }

  if (m === 'GET' && p === '/api/voices') {
    try { return send(res, 200, { voices: await tts.voices() }); }
    catch (e) { return send(res, 502, { message: e.message, voices: [] }); }
  }

  // Voice preview for the settings screen.
  if (m === 'POST' && p === '/api/tts') {
    const b = await readJson(req);
    if (!tts.enabled()) return send(res, 503, { message: 'Nigerian voices are not switched on (no YarnGPT key).' });
    if (!b || typeof b.text !== 'string' || !b.text.trim() || b.text.length > 300) {
      return send(res, 400, { message: 'Text is required (max 300 characters).' });
    }
    try { return send(res, 200, { audio_url: await tts.speak(b.text, b.voice || undefined) }); }
    catch (e) { return send(res, 502, { message: e.message }); }
  }

  // ---- POS side (matches the Tapnod POS README) ----
  if (m === 'GET' && p === '/api/pos/customers') return send(res, 200, { customers });

  if (m === 'POST' && p === '/api/pos/cards/link') {
    const b = await readJson(req);
    if (!b || !b.card_token || !customers.some((c) => c.id === b.customer_id)) {
      return send(res, 400, { message: 'Pick a customer and tap a card.' });
    }
    cards.set(b.card_token, b.customer_id);
    return send(res, 200, { ok: true });
  }

  if (m === 'POST' && p === '/api/pos/requests') {
    const b = await readJson(req);
    if (!b || !Number.isInteger(b.amount_kobo) || b.amount_kobo <= 0 || !b.card_token) {
      return send(res, 400, { message: 'Amount and card are required.' });
    }
    let customerId = cards.get(b.card_token);
    if (!customerId && customers.some((c) => c.id === DEMO_AUTO_LINK)) {
      customerId = DEMO_AUTO_LINK;
      cards.set(b.card_token, customerId);
    }
    if (!customerId) return send(res, 404, { error: 'card_not_linked', message: 'Card not linked to Tapnod.' });

    for (const r of requests.values()) {
      if (r.customer_id === customerId && r.status === 'pending') r.status = 'cancelled';
    }

    const id = 'req_' + crypto.randomBytes(4).toString('hex');
    const r = {
      request_id: id,
      customer_id: customerId,
      amount_kobo: b.amount_kobo,
      currency: b.currency || 'NGN',
      merchant_name: b.merchant_name || 'Merchant',
      terminal_id: b.terminal_id || req.headers['x-terminal-id'] || '',
      card_last4: b.card_last4 || '',
      card_scheme: b.card_scheme || 'CARD',
      status: 'pending',
      reference: 'TPN-' + id.slice(-6).toUpperCase(),
      created_at_ms: Date.now(),
      expires_at_ms: Date.now() + REQUEST_TTL_MS,
      audio: null,
    };
    requests.set(id, r);
    notify(r);
    prepareAudio(r);
    const name = customers.find((c) => c.id === customerId).name;
    return send(res, 201, { request_id: id, customer_name: name, expires_at_ms: r.expires_at_ms });
  }

  if (m === 'GET' && (match = p.match(/^\/api\/pos\/requests\/([\w-]+)$/))) {
    const r = requests.get(match[1]);
    if (!r) return send(res, 404, { message: 'Request not found.' });
    refresh(r);
    return send(res, 200, { status: r.status, reference: r.reference });
  }

  if (m === 'POST' && (match = p.match(/^\/api\/pos\/requests\/([\w-]+)\/cancel$/))) {
    const r = requests.get(match[1]);
    if (r && refresh(r).status === 'pending') r.status = 'cancelled';
    return send(res, 200, { ok: true });
  }

  // ---- customer side (the web app) ----
  if ((match = p.match(/^\/api\/customer\/([\w-]+)(\/.*)?$/)) && !p.startsWith('/api/customer/requests/')) {
    const c = customers.find((x) => x.id === match[1]);
    if (!c) return send(res, 404, { message: 'Unknown customer.' });
    const sub = match[2] || '';

    if (m === 'GET' && sub === '') return send(res, 200, { id: c.id, name: c.name, prefs: prefsFor(c.id) });

    if (m === 'GET' && sub === '/latest') {
      let latest = null;
      for (const r of requests.values()) {
        if (r.customer_id === c.id && (!latest || r.created_at_ms > latest.created_at_ms)) latest = r;
      }
      return send(res, 200, { request: latest ? forCustomer(refresh(latest)) : null });
    }

    if (m === 'POST' && sub === '/prefs') {
      const b = await readJson(req);
      if (!b) return send(res, 400, { message: 'Bad JSON.' });
      const next = {
        lang: LANGS.includes(b.lang) ? b.lang : 'en',
        voice: typeof b.voice === 'string' ? b.voice.slice(0, 64) : '',
      };
      prefs.set(c.id, next);
      return send(res, 200, { prefs: next });
    }

    if (m === 'POST' && sub === '/subscribe') {
      const b = await readJson(req);
      const s = b && b.subscription;
      if (!s || typeof s.endpoint !== 'string' || !/^https:\/\//.test(s.endpoint) || !s.keys || !s.keys.p256dh || !s.keys.auth) {
        return send(res, 400, { message: 'Bad subscription.' });
      }
      if (!subscriptions.has(c.id)) subscriptions.set(c.id, new Map());
      subscriptions.get(c.id).set(s.endpoint, { endpoint: s.endpoint, keys: { p256dh: s.keys.p256dh, auth: s.keys.auth } });
      return send(res, 200, { ok: true });
    }

    if (m === 'POST' && sub === '/test-notification') {
      const subs = subscriptions.get(c.id);
      if (!subs || !subs.size) return send(res, 409, { message: 'Notifications are not set up on this phone yet.' });
      const { lang } = prefsFor(c.id);
      const results = await Promise.all([...subs.values()].map((s) =>
        push.send(s, { title: 'Tapnod', body: i18n.t(lang, 'sample'), url: '/?c=' + c.id }, vapid).catch(() => 0)));
      return send(res, 200, { sent: results.filter((x) => x >= 200 && x < 300).length });
    }
  }

  if (m === 'POST' && (match = p.match(/^\/api\/customer\/requests\/([\w-]+)\/(approve|decline)$/))) {
    const r = requests.get(match[1]);
    if (!r) return send(res, 404, { message: 'Request not found.' });
    if (refresh(r).status !== 'pending') {
      return send(res, 409, { message: 'This payment is already ' + r.status + '.', request: forCustomer(r) });
    }
    r.status = match[2] === 'approve' ? 'approved' : 'declined';
    return send(res, 200, { request: forCustomer(r) });
  }

  send(res, 404, { message: 'Not found.' });
}

http
  .createServer((req, res) => {
    route(req, res).catch((e) => {
      console.error(e);
      send(res, 500, { message: 'Something went wrong on Tapnod.' });
    });
  })
  .listen(PORT, () => {
    console.log(`Tapnod demo running on http://localhost:${PORT}`);
    console.log(`Nigerian voices (YarnGPT): ${tts.enabled() ? 'on' : 'off, set YARNGPT_API_KEY to switch on'}`);
  });
