/**
 * TTS synthesis for JEVI Audio Overviews.
 * Uses the Microsoft edge-tts engine (@andresaya/edge-tts) which needs no API key.
 *
 * Voice catalog (podcast dialogue mode, Lane B): curated expressive neural
 * voices + legacy `female`/`male` aliases kept for back-compat (AC4). Request
 * validation is a pure function (validateTtsRequest) so the route stays thin
 * and the rules are unit-testable without booting a server.
 */
const { EdgeTTS } = require('@andresaya/edge-tts');

/**
 * Ordered voice catalog. `alias: true` entries are compatibility spellings of
 * another voice and are excluded from the public GET /api/ai/tts/voices list.
 * @type {Array<{id:string,label:string,gender:'female'|'male',locale:string,shortName:string,voiceName:string,alias?:boolean}>}
 */
const VOICE_CATALOG = [
  { id: 'female', label: 'Female (Aria)', gender: 'female', locale: 'en-US', shortName: 'Aria', voiceName: 'en-US-AriaNeural', alias: true },
  { id: 'male', label: 'Male (Guy)', gender: 'male', locale: 'en-US', shortName: 'Guy', voiceName: 'en-US-GuyNeural', alias: true },
  { id: 'aria', label: 'Aria', gender: 'female', locale: 'en-US', shortName: 'Aria', voiceName: 'en-US-AriaNeural' },
  { id: 'guy', label: 'Guy', gender: 'male', locale: 'en-US', shortName: 'Guy', voiceName: 'en-US-GuyNeural' },
  { id: 'jenny', label: 'Jenny', gender: 'female', locale: 'en-US', shortName: 'Jenny', voiceName: 'en-US-JennyNeural' },
  { id: 'davis', label: 'Davis', gender: 'male', locale: 'en-US', shortName: 'Davis', voiceName: 'en-US-DavisNeural' },
  { id: 'sonia', label: 'Sonia', gender: 'female', locale: 'en-GB', shortName: 'Sonia', voiceName: 'en-GB-SoniaNeural' },
];

// Derived view so pre-catalog callers/tests keep their `VOICES` contract
// (exactly the two legacy aliases → Edge voice names).
const VOICES = Object.fromEntries(
  VOICE_CATALOG.filter((v) => v.alias).map((v) => [v.id, v.voiceName])
);

const MAX_CHARS = 4000;
const SYNTHESIS_TIMEOUT_MS = 15000;

// Prosody bounds accepted by the route (percentage / hertz, signed).
const RATE_MIN = -50;
const RATE_MAX = 100;
const PITCH_MIN = -100;
const PITCH_MAX = 100;

/**
 * Resolve a requested voice id against the catalog (exact match — arbitrary
 * strings are rejected as a DoS/abuse guard).
 * @param {unknown} voice
 * @returns {object|null} the catalog entry, or null when unknown
 */
function resolveVoiceId(voice) {
  if (typeof voice !== 'string') return null;
  return VOICE_CATALOG.find((v) => v.id === voice) || null;
}

/**
 * Public voice list for GET /api/ai/tts/voices: real voices only (no
 * female/male aliases), safe fields only (no Edge voiceName internals).
 * @returns {Array<{id:string,label:string,gender:string,locale:string}>}
 */
function publicVoices() {
  return VOICE_CATALOG.filter((v) => !v.alias).map(
    ({ id, label, gender, locale }) => ({ id, label, gender, locale })
  );
}

/**
 * Parse a signed prosody value: number or numeric string with an optional
 * unit suffix ('%' / 'Hz'); leading '+' allowed. Returns {present:false} for
 * nullish/blank, {present:true,value:number}|null via range checks by caller.
 * @param {unknown} value
 * @param {string} suffix unit suffix to tolerate ('%' or 'Hz'); '' for bare numbers
 * @returns {{present:boolean,value?:number}}
 */
function parseSigned(value, suffix) {
  if (value === undefined || value === null) return { present: false };
  if (typeof value === 'number') {
    // NaN/Infinity flow through as {present:true, value:NaN} so the range
    // check rejects them; only nullish/blank strings count as "absent".
    return { present: true, value };
  }
  if (typeof value !== 'string') return { present: true, value: NaN };
  const trimmed = value.trim();
  if (trimmed === '') return { present: false };
  const m = trimmed.match(new RegExp(`^([+-]?\\d+(?:\\.\\d+)?)(?:${suffix})?$`));
  if (!m) return { present: true, value: NaN };
  return { present: true, value: parseFloat(m[1]) };
}

/**
 * Pure request validation for POST /api/ai/tts. Order of checks is fixed:
 * text → length → voice → rate → pitch. `ok:false` results carry the 400
 * {code, message} the route forwards verbatim to gateway.sendError.
 *
 * @param {object} body parsed JSON request body (may be any shape)
 * @returns {{ok:true,text:string,voice:object,prosody:{rate?:number,pitch?:number}}
 *          | {ok:false,code:string,message:string}}
 */
function validateTtsRequest(body) {
  const { text, voice, rate, pitch } = (body && typeof body === 'object' ? body : {}) || {};

  if (!text || !String(text).trim()) {
    return { ok: false, code: 'MISSING_TEXT', message: 'text is required.' };
  }
  if (String(text).length > MAX_CHARS) {
    return { ok: false, code: 'TEXT_TOO_LONG', message: `text must be ${MAX_CHARS} characters or fewer.` };
  }

  // voice is optional → default to the legacy female alias (unchanged behavior).
  let entry = null;
  if (voice === undefined || voice === null || (typeof voice === 'string' && voice.trim() === '')) {
    entry = resolveVoiceId('female');
  } else {
    entry = resolveVoiceId(voice);
    if (!entry) {
      return { ok: false, code: 'INVALID_VOICE', message: 'voice must be a known voice id.' };
    }
  }

  const prosody = {};

  const parsedRate = parseSigned(rate, '%');
  if (!parsedRate.present) {
    // absent → library default
  } else if (!Number.isFinite(parsedRate.value) || parsedRate.value < RATE_MIN || parsedRate.value > RATE_MAX) {
    return {
      ok: false,
      code: 'INVALID_RATE',
      message: `rate must be a signed percent between ${RATE_MIN} and +${RATE_MAX}.`,
    };
  } else {
    prosody.rate = parsedRate.value;
  }

  const parsedPitch = parseSigned(pitch, 'Hz');
  if (!parsedPitch.present) {
    // absent → library default
  } else if (!Number.isFinite(parsedPitch.value) || parsedPitch.value < PITCH_MIN || parsedPitch.value > PITCH_MAX) {
    return {
      ok: false,
      code: 'INVALID_PITCH',
      message: `pitch must be a signed value in hertz between ${PITCH_MIN} and +${PITCH_MAX}.`,
    };
  } else {
    prosody.pitch = parsedPitch.value;
  }

  return { ok: true, text: String(text), voice: entry, prosody };
}

function withTimeout(promise, ms) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('TTS_TIMEOUT')), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Synthesize spoken audio for the given text.
 * Prosody (rate/pitch) is forwarded to EdgeTTS ONLY when provided — with no
 * prosody the call stays byte-identical to pre-dialogue behavior.
 * @param {string} text
 * @param {string} [voice] catalog id (see VOICE_CATALOG); unknown → female fallback
 * @param {{rate?:number|string,pitch?:number|string}} [prosody] native EdgeTTS option values
 * @param {Function} [TtsCtor] EdgeTTS constructor (test seam; defaults to the library)
 * @returns {Promise<Buffer>} MP3 bytes
 */
async function synthesize(text, voice = 'female', { rate, pitch } = {}, TtsCtor = EdgeTTS) {
  const voiceName = (resolveVoiceId(voice) || resolveVoiceId('female')).voiceName;
  const tts = new TtsCtor();
  const options = {};
  if (rate !== undefined && rate !== null) options.rate = rate;
  if (pitch !== undefined && pitch !== null) options.pitch = pitch;
  if (Object.keys(options).length > 0) {
    await tts.synthesize(text, voiceName, options);
  } else {
    await tts.synthesize(text, voiceName);
  }
  const chunks = [];
  for await (const chunk of tts.audio_stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  if (chunks.length === 0) throw new Error('TTS_EMPTY');
  return Buffer.concat(chunks);
}

module.exports = {
  synthesize,
  VOICES,
  VOICE_CATALOG,
  resolveVoiceId,
  publicVoices,
  validateTtsRequest,
  MAX_CHARS,
  SYNTHESIS_TIMEOUT_MS,
  withTimeout,
};
