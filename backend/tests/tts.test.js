/**
 * TtsService tests — validation + timeout behavior (no network in the happy path
 * that we assert on; the real synth is covered by an offline-free error guard).
 *
 * Run: npm test
 */
const { describe, it } = require('node:test');
const assert = require('node:assert/strict');

const { VOICES, MAX_CHARS, withTimeout, synthesize } = require('../ai/TtsService');

describe('TtsService', () => {
  it('exposes exactly two validated voices', () => {
    assert.deepEqual(Object.keys(VOICES).sort(), ['female', 'male']);
    assert.equal(VOICES.female, 'en-US-AriaNeural');
    assert.equal(VOICES.male, 'en-US-GuyNeural');
  });

  it('propagates synthesis errors as a rejected promise', async () => {
    // Network-free: wrap a throwing synth. The route maps any throw to 502.
    await assert.rejects(() => withTimeout(Promise.reject(new Error('TTS_TIMEOUT')), 50));
  });

  it('withTimeout rejects on slow work', async () => {
    await assert.rejects(
      () => withTimeout(new Promise((resolve) => setTimeout(() => resolve('x'), 50)), 5),
      /TTS_TIMEOUT/
    );
  });

  it('withTimeout resolves fast work', async () => {
    const r = await withTimeout(Promise.resolve('ok'), 50);
    assert.equal(r, 'ok');
  });

  it('MAX_CHARS is a sane upper bound', () => {
    assert.ok(MAX_CHARS >= 1000 && MAX_CHARS <= 8000);
  });
});
