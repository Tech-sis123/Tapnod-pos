// Phone-call approvals through Infobip (outbound IVR). Nobody has to touch anything:
// the POS asks, this server starts the call, Infobip rings her, and her key press approves.
//
// How it works:
// 1. Once, we create an IVR "scenario" on Infobip (a small script, see SCRIPT below).
// 2. For each payment we launch that scenario at her number, passing the request id and a signed token.
// 3. When she answers, the script asks this server what to say (POST /api/ib/step). We reply with
//    { reply, want, step, done }. Infobip reads `reply` aloud, collects 1 digit (want=choice) or
//    4 digits (want=pin), and sends them back on the next turn. It loops until done=yes, then hangs up.
// 4. When the call ends, Infobip posts a report to /api/ib/report so the POS knows if she never picked up.
//
// Trial accounts can only call the number you verified when you signed up.
// No packages: plain HTTPS.

const crypto = require('crypto');

const BASE = (process.env.IB_BASE_URL || '').replace(/^https?:\/\//, '').replace(/\/+$/, '');
const KEY = process.env.IB_API_KEY || '';
const FROM = (process.env.IB_FROM || '').replace(/[^\d]/g, '');
const FIXED_SCENARIO = process.env.IB_SCENARIO_ID || '';

const enabled = () => Boolean(BASE && KEY);

async function ib(method, path, body) {
  const res = await fetch(`https://${BASE}${path}`, {
    method,
    headers: { Authorization: 'App ' + KEY, 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  const text = await res.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch {}
  if (!res.ok) {
    const err = data.requestError && data.requestError.serviceException;
    throw new Error(`Infobip ${res.status}: ${(err && (err.text || err.messageId)) || text.slice(0, 300)}`);
  }
  return data;
}

// The call script. ${...} are scenario variables: the ones we pass at launch, plus every field
// of this server's JSON reply (collectResponse). It talks to us in a loop until we say done=yes.
function script(stepUrl) {
  const body = '{"r":"${rid}","t":"${tok}","step":"${step}","digits":"${digits}"}';
  return [
    {
      repeat: [
        { request: stepUrl, options: { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, collectResponse: true } },
        { say: '${reply}', options: { language: 'en', speechRate: 0.9 } },
        {
          switch: 'want',
          case: {
            choice: [{ collectInto: 'digits', options: { maxInputLength: 1, timeout: 12 } }],
            pin: [{ collectInto: 'digits', options: { maxInputLength: 4, timeout: 15 } }],
            __default: [{ pause: 1 }],
          },
        },
      ],
      while: "${done == 'no'}",
    },
    'hangup',
  ];
}

// One scenario per server address. Reused across restarts (looked up by name), made only if missing.
const scenarios = new Map();
async function scenarioFor(origin) {
  if (FIXED_SCENARIO) return FIXED_SCENARIO;
  if (scenarios.has(origin)) return scenarios.get(origin);
  const stepUrl = origin + '/api/ib/step';
  const name = 'tapnod-approval-' + crypto.createHash('sha256').update(JSON.stringify(script(stepUrl))).digest('hex').slice(0, 10);
  let id = '';
  try {
    const found = await ib('GET', '/voice/ivr/1/scenarios?name=' + encodeURIComponent(name));
    const list = Array.isArray(found) ? found : found.results || [];
    const hit = list.find((s) => s.name === name);
    if (hit) id = hit.id;
  } catch (e) { console.warn('Infobip scenario lookup failed, creating a new one:', e.message); }
  if (!id) {
    const made = await ib('POST', '/voice/ivr/1/scenarios', {
      name,
      description: 'Tapnod: read a payment aloud, 1 approves, 2 declines',
      script: script(stepUrl),
    });
    id = made.id;
    console.log(`Infobip scenario created: ${id} (${name})`);
  }
  scenarios.set(origin, id);
  return id;
}

// Ring her. `to` is +234..., Infobip wants 234...
async function dial(to, { origin, requestId, token }) {
  const scenarioId = await scenarioFor(origin);
  const msg = {
    scenarioId,
    destinations: [{ to: String(to).replace(/[^\d]/g, '') }],
    parameters: { rid: requestId, tok: token, step: 'start', digits: '', done: 'no', want: '', reply: '' },
    notifyUrl: `${origin}/api/ib/report`,
    notifyContentType: 'application/json',
    callbackData: requestId,
    ringTimeout: 40,
  };
  if (FROM) msg.from = FROM;
  const data = await ib('POST', '/voice/ivr/1/messages', { messages: [msg] });
  const m = (data.messages || [])[0] || {};
  const group = m.status && m.status.groupName;
  if (group && !['PENDING', 'DELIVERED'].includes(group)) throw new Error(`Infobip refused the call: ${m.status.description || m.status.name || group}`);
  return m.messageId || data.bulkId || '';
}

// voiceStep() (server.js) builds the call with these three helpers, the same way it does for
// Africa's Talking XML. Here they make plain objects, and toReply() turns them into our JSON answer.
const say = (text) => ({ say: text });
function getDigits(prompt, numDigits, nextUrl) {
  const step = new URL(nextUrl, 'http://x').searchParams.get('step') || '';
  return { ask: prompt, want: numDigits === 4 ? 'pin' : 'choice', step };
}
const join = (items) => items;
function toReply(items) {
  const ask = items.find((i) => i.ask);
  if (ask) return { reply: ask.ask, want: ask.want, step: ask.step, done: 'no', digits: '' };
  return { reply: items.map((i) => i.say).filter(Boolean).join(' ') || 'Goodbye.', want: '', step: 'end', done: 'yes', digits: '' };
}

module.exports = { enabled, dial, say, getDigits, join, toReply };
