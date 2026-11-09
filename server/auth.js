// Passwords, sign-in sessions and fingerprint (WebAuthn passkey) checks, using Node's built-in crypto.

const crypto = require('crypto');

const b64u = {
  enc: (buf) => Buffer.from(buf).toString('base64url'),
  dec: (str) => Buffer.from(String(str || ''), 'base64url'),
};

/* ---------------- passwords (scrypt) ---------------- */

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, 32);
  return `scrypt$${b64u.enc(salt)}$${b64u.enc(hash)}`;
}

function checkPassword(password, stored) {
  const [kind, salt, hash] = String(stored).split('$');
  if (kind !== 'scrypt' || !salt || !hash) return false;
  const got = crypto.scryptSync(password, b64u.dec(salt), 32);
  const want = b64u.dec(hash);
  return got.length === want.length && crypto.timingSafeEqual(got, want);
}

/* ---------------- sessions (signed tokens, 30 days) ---------------- */

function sessions(secret) {
  const key = crypto.createHash('sha256').update('tapnod-session:' + secret).digest();
  const sign = (s) => b64u.enc(crypto.createHmac('sha256', key).update(s).digest());
  return {
    issue(customerId, days = 30) {
      const body = b64u.enc(JSON.stringify({ cid: customerId, exp: Date.now() + days * 864e5 }));
      return body + '.' + sign(body);
    },
    // Returns the customer id, or null if the token is missing, forged or expired.
    read(token) {
      const [body, sig] = String(token || '').split('.');
      if (!body || !sig) return null;
      const want = sign(body);
      if (want.length !== sig.length || !crypto.timingSafeEqual(Buffer.from(want), Buffer.from(sig))) return null;
      try {
        const data = JSON.parse(b64u.dec(body).toString());
        return data.exp > Date.now() ? data.cid : null;
      } catch { return null; }
    },
  };
}

/* ---------------- fingerprint (WebAuthn) ---------------- */

// One-time challenges, kept for 5 minutes.
const challenges = new Map();
function newChallenge(purpose, extra = {}) {
  const id = crypto.randomBytes(12).toString('hex');
  const challenge = b64u.enc(crypto.randomBytes(32));
  challenges.set(id, { challenge, purpose, expires: Date.now() + 300_000, ...extra });
  for (const [k, v] of challenges) if (v.expires < Date.now()) challenges.delete(k);
  return { id, challenge };
}
function takeChallenge(id, purpose) {
  const c = challenges.get(id);
  challenges.delete(id);
  if (!c || c.purpose !== purpose || c.expires < Date.now()) return null;
  return c;
}

class WebAuthnError extends Error {}

// Checks clientDataJSON: right ceremony, our challenge, our website.
function checkClientData(clientDataB64, type, challenge, origin) {
  let cd;
  try { cd = JSON.parse(b64u.dec(clientDataB64).toString()); } catch { throw new WebAuthnError('Bad client data'); }
  if (cd.type !== type) throw new WebAuthnError('Wrong ceremony type');
  if (cd.challenge !== challenge) throw new WebAuthnError('Challenge does not match');
  if (cd.origin !== origin) throw new WebAuthnError(`Wrong website (${cd.origin})`);
}

// Checks authenticatorData: made for our domain, and the finger/screen lock was actually used.
function checkAuthData(authDataB64, rpId) {
  const ad = b64u.dec(authDataB64);
  if (ad.length < 37) throw new WebAuthnError('Bad authenticator data');
  const rpHash = crypto.createHash('sha256').update(rpId).digest();
  if (!ad.subarray(0, 32).equals(rpHash)) throw new WebAuthnError('Passkey belongs to another website');
  const flags = ad[32];
  if (!(flags & 0x01)) throw new WebAuthnError('User presence missing');
  if (!(flags & 0x04)) throw new WebAuthnError('Fingerprint or screen lock was not used');
  return ad;
}

// Registration: the browser hands over the public key directly (response.getPublicKey()),
// so there's no CBOR to parse. We check the client data and authenticator data, then store the key.
function verifyRegistration(body, challenge, origin, rpId) {
  checkClientData(body.clientDataJSON, 'webauthn.create', challenge, origin);
  checkAuthData(body.authenticatorData, rpId);
  if (![-7, -257].includes(Number(body.alg))) throw new WebAuthnError('Unsupported key type');
  try {
    crypto.createPublicKey({ key: b64u.dec(body.publicKey), format: 'der', type: 'spki' });
  } catch { throw new WebAuthnError('Bad public key'); }
  if (!body.id) throw new WebAuthnError('Missing credential id');
  return { id: body.id, public_key: body.publicKey, alg: Number(body.alg) };
}

// Sign-in or approval: the phone signs (authenticatorData + hash of clientData) with the passkey.
function verifyAssertion(body, credential, challenge, origin, rpId) {
  checkClientData(body.clientDataJSON, 'webauthn.get', challenge, origin);
  const ad = checkAuthData(body.authenticatorData, rpId);
  const signed = Buffer.concat([ad, crypto.createHash('sha256').update(b64u.dec(body.clientDataJSON)).digest()]);
  const key = crypto.createPublicKey({ key: b64u.dec(credential.public_key), format: 'der', type: 'spki' });
  const ok = crypto.verify('sha256', signed, key, b64u.dec(body.signature)); // ES256 (DER) and RS256 both use SHA-256
  if (!ok) throw new WebAuthnError('Fingerprint signature is not valid');
}

module.exports = {
  b64u, hashPassword, checkPassword, sessions,
  newChallenge, takeChallenge, verifyRegistration, verifyAssertion, WebAuthnError,
};
