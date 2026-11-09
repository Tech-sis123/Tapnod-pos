// Nigerian voices through the YarnGPT API (https://yarngpt.ai/api-docs).
// Only used when YARNGPT_API_KEY is set; without it the phone uses its own voice.
//
// YarnGPT works in jobs: POST the text, get a job id, poll until the audio is ready.

const crypto = require('crypto');

const BASE = process.env.YARNGPT_BASE || 'https://api.yarngpt.ai/api/v1';
const KEY = process.env.YARNGPT_API_KEY || '';

const enabled = () => Boolean(KEY);

async function call(method, path, body) {
  const headers = { Authorization: 'Bearer ' + KEY, Accept: 'application/json' };
  if (body) {
    headers['Content-Type'] = 'application/json';
    // YarnGPT requires a fresh idempotency key on every new job.
    headers['Idempotency-Key'] = crypto.randomBytes(16).toString('hex');
  }
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch {}
  if (!res.ok) {
    const msg = (json && (json.user_message || json.message || json.error)) || text.slice(0, 200);
    throw new Error(`YarnGPT ${res.status}: ${typeof msg === 'string' ? msg : JSON.stringify(msg)}`);
  }
  return json;
}

// The docs don't pin down the voice list shape, so accept the common ones.
let voiceCache = null;
let voiceCacheAt = 0;
async function voices() {
  if (!enabled()) return [];
  if (voiceCache && Date.now() - voiceCacheAt < 3600_000) return voiceCache;
  const data = await call('GET', '/voices');
  const list = Array.isArray(data) ? data : data.voices || data.data || data.items || [];
  voiceCache = list
    .map((v) => (typeof v === 'string' ? { id: v } : v))
    .map((v) => ({
      id: v.id || v.voice || v.name,
      name: v.display_name || v.label || v.name || v.id,
      gender: v.gender || '',
      languages: v.languages || v.language || [],
      description: v.description || v.style || '',
      default: Boolean(v.default),
    }))
    .filter((v) => v.id);
  voiceCacheAt = Date.now();
  return voiceCache;
}

// Turns text into an mp3 link. Resolves with the audio URL (valid for about an hour).
async function speak(text, voice, { timeoutMs = 30000 } = {}) {
  const body = { text, output_format: 'mp3' };
  if (voice) body.voice = voice;
  const job = await call('POST', '/tts', body);
  const id = job.job_id || job.id;
  if (!id) throw new Error('YarnGPT did not return a job id');
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    await new Promise((r) => setTimeout(r, 700));
    const s = await call('GET', '/status/' + encodeURIComponent(id));
    if (s.status === 'completed' && s.audio_url) return s.audio_url;
    if (s.status === 'failed') throw new Error(s.user_message || 'YarnGPT could not make the audio');
  }
  throw new Error('YarnGPT took too long');
}

module.exports = { enabled, voices, speak };
