// Tapnod demo backend + customer web app.
// No dependencies: plain Node (18+). Run with `node server.js`.
//
// The POS app talks to /api/pos/* (contract in the main README).
// The customer's phone opens / in the browser and polls /api/customer/*.
// Everything lives in memory, which is fine for a demo.

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3000;
// Must match "Card token secret" in the POS settings (the POS default is the same).
const TOKEN_SECRET = process.env.TOKEN_SECRET || 'tapnod-demo-secret';
// The test card the POS uses for "Simulate tap" and "Accept any tap".
const DEMO_CARD_NUMBER = '5399000000004821';
const REQUEST_TTL_MS = 120_000;

const customers = [
  { id: 'cus_amaka', name: 'Amaka Okafor', note: 'Smartphone, screen reader on' },
  { id: 'cus_ngozi', name: 'Mama Ngozi', note: 'Button phone, call to approve' },
  { id: 'cus_tunde', name: 'Tunde Bakare', note: 'Smartphone' },
];

// card_token -> customer id
const cards = new Map();
// request id -> request
const requests = new Map();

// Pre-link the demo card to Amaka, so a fresh server (or a free host that
// restarted while asleep) works straight away without the Link card step.
const demoToken = crypto.createHmac('sha256', TOKEN_SECRET).update(DEMO_CARD_NUMBER).digest('hex');
cards.set(demoToken, 'cus_amaka');

/* ---------------- helpers ---------------- */

function send(res, code, body) {
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(JSON.stringify(body));
}

function readJson(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 1e5) req.destroy(); // nobody needs a body this big
    });
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        resolve(null);
      }
    });
  });
}

// A pending request past its deadline becomes expired.
function refresh(r) {
  if (r.status === 'pending' && Date.now() > r.expires_at_ms) r.status = 'expired';
  return r;
}

function shortRef(id) {
  return 'TPN-' + id.slice(-6).toUpperCase();
}

// What the customer's phone is allowed to see (no card token).
function forCustomer(r) {
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
  };
}

/* ---------------- routes ---------------- */

async function route(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const m = req.method;
  let match;

  // ---- customer web app ----
  if (m === 'GET' && (p === '/' || p === '/index.html')) {
    const html = fs.readFileSync(path.join(__dirname, 'public', 'index.html'));
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end(html);
  }

  if (m === 'GET' && p === '/api/health') return send(res, 200, { ok: true });

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
    const customerId = cards.get(b.card_token);
    if (!customerId) return send(res, 404, { error: 'card_not_linked', message: 'Card not linked to Tapnod.' });

    // A new charge replaces anything still pending for this customer.
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
      reference: shortRef(id),
      created_at_ms: Date.now(),
      expires_at_ms: Date.now() + REQUEST_TTL_MS,
    };
    requests.set(id, r);
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
  if (m === 'GET' && (match = p.match(/^\/api\/customer\/([\w-]+)$/))) {
    const c = customers.find((x) => x.id === match[1]);
    if (!c) return send(res, 404, { message: 'Unknown customer.' });
    return send(res, 200, { id: c.id, name: c.name });
  }

  // The newest request for this customer, so the phone can show pending and final states.
  if (m === 'GET' && (match = p.match(/^\/api\/customer\/([\w-]+)\/latest$/))) {
    let latest = null;
    for (const r of requests.values()) {
      if (r.customer_id === match[1] && (!latest || r.created_at_ms > latest.created_at_ms)) latest = r;
    }
    return send(res, 200, { request: latest ? forCustomer(refresh(latest)) : null });
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
  .listen(PORT, () => console.log(`Tapnod demo running on http://localhost:${PORT}`));
