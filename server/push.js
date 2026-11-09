// Web Push (phone notifications) using only Node's built-in crypto.
// Implements VAPID (RFC 8292) and aes128gcm payload encryption (RFC 8291),
// the same thing the `web-push` npm package does.

const crypto = require('crypto');

const b64u = {
  enc: (buf) => Buffer.from(buf).toString('base64url'),
  dec: (str) => Buffer.from(str, 'base64url'),
};

// VAPID keys are derived from a secret, so they stay the same across restarts
// (phones subscribed with the old key would otherwise stop getting notifications).
function vapidFromSecret(secret) {
  const d = crypto.createHash('sha256').update('tapnod-vapid:' + secret).digest();
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.setPrivateKey(d);
  const pub = ecdh.getPublicKey(); // 65 bytes, uncompressed
  const privateKey = crypto.createPrivateKey({
    format: 'jwk',
    key: { kty: 'EC', crv: 'P-256', d: b64u.enc(d), x: b64u.enc(pub.subarray(1, 33)), y: b64u.enc(pub.subarray(33)) },
  });
  return { publicKey: b64u.enc(pub), privateKey };
}

function vapidHeader(endpoint, vapid, subject) {
  const aud = new URL(endpoint).origin;
  const header = b64u.enc(JSON.stringify({ typ: 'JWT', alg: 'ES256' }));
  const payload = b64u.enc(JSON.stringify({ aud, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: subject }));
  const unsigned = header + '.' + payload;
  const sig = crypto.sign('sha256', Buffer.from(unsigned), { key: vapid.privateKey, dsaEncoding: 'ieee-p1363' });
  return `vapid t=${unsigned}.${b64u.enc(sig)}, k=${vapid.publicKey}`;
}

function hmac(key, data) {
  return crypto.createHmac('sha256', key).update(data).digest();
}

// Encrypts `payload` for one browser subscription (RFC 8291, aes128gcm).
function encrypt(subscription, payload) {
  const uaPublic = b64u.dec(subscription.keys.p256dh);
  const authSecret = b64u.dec(subscription.keys.auth);

  const server = crypto.createECDH('prime256v1');
  const asPublic = server.generateKeys();
  const shared = server.computeSecret(uaPublic);

  const prkKey = hmac(authSecret, shared);
  const keyInfo = Buffer.concat([Buffer.from('WebPush: info\0'), uaPublic, asPublic]);
  const ikm = hmac(prkKey, Buffer.concat([keyInfo, Buffer.from([1])]));

  const salt = crypto.randomBytes(16);
  const prk = hmac(salt, ikm);
  const cek = hmac(prk, Buffer.from('Content-Encoding: aes128gcm\0\x01', 'binary')).subarray(0, 16);
  const nonce = hmac(prk, Buffer.from('Content-Encoding: nonce\0\x01', 'binary')).subarray(0, 12);

  const plain = Buffer.concat([Buffer.from(payload), Buffer.from([2])]); // 0x02 = last record
  const cipher = crypto.createCipheriv('aes-128-gcm', cek, nonce);
  const body = Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()]);

  const rs = Buffer.alloc(4);
  rs.writeUInt32BE(4096);
  const head = Buffer.concat([salt, rs, Buffer.from([asPublic.length]), asPublic]);
  return Buffer.concat([head, body]);
}

// Sends one notification. Resolves to the HTTP status (201 = delivered to the push service,
// 404/410 = the subscription is gone and should be forgotten).
async function send(subscription, data, vapid, { subject = 'mailto:demo@tapnod.ng', ttl = 120 } = {}) {
  const body = encrypt(subscription, JSON.stringify(data));
  const res = await fetch(subscription.endpoint, {
    method: 'POST',
    headers: {
      Authorization: vapidHeader(subscription.endpoint, vapid, subject),
      'Content-Encoding': 'aes128gcm',
      'Content-Type': 'application/octet-stream',
      TTL: String(ttl),
      Urgency: 'high',
    },
    body,
  });
  return res.status;
}

module.exports = { vapidFromSecret, vapidHeader, encrypt, send, b64u };
