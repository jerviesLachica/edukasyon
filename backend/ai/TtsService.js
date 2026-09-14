/**
 * TTS synthesis for JEVI Audio Overviews.
 * Uses the Microsoft edge-tts engine (@andresaya/edge-tts) which needs no API key.
 */
const { EdgeTTS } = require('@andresaya/edge-tts');

const VOICES = {
  female: 'en-US-AriaNeural',
  male: 'en-US-GuyNeural',
};

const MAX_CHARS = 4000;
const SYNTHESIS_TIMEOUT_MS = 15000;

function withTimeout(promise, ms) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error('TTS_TIMEOUT')), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Synthesize spoken audio for the given text.
 * @param {string} text
 * @param {'female'|'male'} [voice]
 * @returns {Promise<Buffer>} MP3 bytes
 */
async function synthesize(text, voice = 'female') {
  const voiceName = VOICES[voice] || VOICES.female;
  const tts = new EdgeTTS();
  await tts.synthesize(text, voiceName);
  const chunks = [];
  for await (const chunk of tts.audio_stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  if (chunks.length === 0) throw new Error('TTS_EMPTY');
  return Buffer.concat(chunks);
}

module.exports = { synthesize, VOICES, MAX_CHARS, SYNTHESIS_TIMEOUT_MS, withTimeout };
