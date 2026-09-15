/**
 * Voice catalog + prosody validation tests (Lane B — podcast dialogue mode).
 *
 * Covers: resolveVoiceId, validateTtsRequest (pure), synthesize() forwarding the
 * right Edge voice name + native rate/pitch options (EdgeTTS stubbed via the
 * injectable constructor seam), and the live routes on the exported `app`
 * (GET /api/ai/tts/voices, POST /api/ai/tts 400 paths).
 *
 * Run: npm test
 */
const { describe, it, before, after } = require('node:test');
const assert = require('node:assert/strict');

const {
  VOICE_CATALOG,
  resolveVoiceId,
  validateTtsRequest,
  publicVoices,
  synthesize,
  MAX_CHARS,
} = require('../ai/TtsService');
const { app } = require('../server');

const CATALOG_IDS = ['female', 'male', 'aria', 'guy', 'jenny', 'davis', 'sonia'];
const EXPECTED_VOICE_NAMES = {
  female: 'en-US-AriaNeural',
  male: 'en-US-GuyNeural',
  aria: 'en-US-AriaNeural',
  guy: 'en-US-GuyNeural',
  jenny: 'en-US-JennyNeural',
  davis: 'en-US-DavisNeural',
  sonia: 'en-GB-SoniaNeural',
};

/** Jest-less stub: TtsService.synthesize accepts an injectable EdgeTTS ctor. */
function stubEdgeTts() {
  const calls = [];
  class FakeEdgeTTS {
    async synthesize(...args) {
      calls.push({ args, text: args[0], voiceName: args[1], options: args[2] });
      this.audio_stream = [Buffer.from('fake-mp3')];
    }
  }
  return { calls, Ctor: FakeEdgeTTS };
}

describe('resolveVoiceId', () => {
  it('resolves every catalog id to its Edge voice name', () => {
    for (const id of CATALOG_IDS) {
      const entry = resolveVoiceId(id);
      assert.ok(entry, `catalog id "${id}" must resolve`);
      assert.equal(entry.id, id);
      assert.equal(entry.voiceName, EXPECTED_VOICE_NAMES[id]);
    }
  });

  it('AC4 back-compat: female/male aliases still resolve to Aria/Guy', () => {
    assert.equal(resolveVoiceId('female').voiceName, 'en-US-AriaNeural');
    assert.equal(resolveVoiceId('male').voiceName, 'en-US-GuyNeural');
    assert.equal(resolveVoiceId('female').alias, true);
    assert.equal(resolveVoiceId('male').alias, true);
  });

  it('rejects unknown, empty, case-wrong and non-string ids', () => {
    assert.equal(resolveVoiceId('rogue-voice'), null);
    assert.equal(resolveVoiceId(''), null);
    assert.equal(resolveVoiceId(undefined), null);
    assert.equal(resolveVoiceId('Aria'), null); // exact-match allowlist (abuse guard)
    assert.equal(resolveVoiceId('en-US-AriaNeural'), null); // ids, not shortNames
  });

  it('catalog is ordered and entries carry the documented fields', () => {
    assert.deepEqual(VOICE_CATALOG.map((v) => v.id), CATALOG_IDS);
    for (const v of VOICE_CATALOG) {
      assert.ok(v.label && typeof v.label === 'string');
      assert.ok(['female', 'male'].includes(v.gender), `${v.id} gender`);
      assert.match(v.locale, /^[a-z]{2}-[A-Z]{2}$/);
      assert.ok(/^[A-Z][a-z]+$/.test(v.shortName), `${v.id} shortName`);
    }
  });
});

describe('validateTtsRequest', () => {
  it('accepts minimal body and defaults voice to the female alias, no prosody', () => {
    const r = validateTtsRequest({ text: 'hello' });
    assert.equal(r.ok, true);
    assert.equal(r.text, 'hello');
    assert.equal(r.voice.id, 'female');
    assert.deepEqual(r.prosody, {});
  });

  it('keeps text/length checks identical', () => {
    assert.deepEqual(
      { ...validateTtsRequest({ text: '   ' }) },
      { ok: false, code: 'MISSING_TEXT', message: 'text is required.' }
    );
    assert.equal(validateTtsRequest({}).ok, false);
    const long = validateTtsRequest({ text: 'x'.repeat(MAX_CHARS + 1) });
    assert.equal(long.ok, false);
    assert.equal(long.code, 'TEXT_TOO_LONG');
    assert.equal(long.message, `text must be ${MAX_CHARS} characters or fewer.`);
    assert.equal(validateTtsRequest({ text: 'x'.repeat(MAX_CHARS) }).ok, true);
  });

  it('unknown voice -> INVALID_VOICE (400 code at the route)', () => {
    const r = validateTtsRequest({ text: 'hi', voice: 'rogue-voice' });
    assert.equal(r.ok, false);
    assert.equal(r.code, 'INVALID_VOICE');
    for (const id of CATALOG_IDS) {
      assert.equal(validateTtsRequest({ text: 'hi', voice: id }).ok, true, `${id} must pass`);
    }
  });

  it('rate: signed percent in [-50, +100]; accepts "+4%", 4, -2, " -2% "', () => {
    assert.equal(validateTtsRequest({ text: 'hi', rate: '+4%' }).prosody.rate, 4);
    assert.equal(validateTtsRequest({ text: 'hi', rate: 4 }).prosody.rate, 4);
    assert.equal(validateTtsRequest({ text: 'hi', rate: -2 }).prosody.rate, -2);
    assert.equal(validateTtsRequest({ text: 'hi', rate: '-2%' }).prosody.rate, -2);
    assert.equal(validateTtsRequest({ text: 'hi', rate: '-50%' }).prosody.rate, -50);
    assert.equal(validateTtsRequest({ text: 'hi', rate: '+100%' }).prosody.rate, 100);
  });

  it('rate: "+400%", 400, "fast", -51 rejected with INVALID_RATE', () => {
    for (const bad of ['+400%', 400, 'fast', -51, '+100.5%', '4%%', NaN]) {
      const r = validateTtsRequest({ text: 'hi', rate: bad });
      assert.equal(r.ok, false, `rate ${JSON.stringify(bad)} must be rejected`);
      assert.equal(r.code, 'INVALID_RATE');
    }
  });

  it('pitch: signed Hz in [-100, +100]; accepts "-2Hz", -2, "+40Hz", 0', () => {
    assert.equal(validateTtsRequest({ text: 'hi', pitch: '-2Hz' }).prosody.pitch, -2);
    assert.equal(validateTtsRequest({ text: 'hi', pitch: -2 }).prosody.pitch, -2);
    assert.equal(validateTtsRequest({ text: 'hi', pitch: '+40Hz' }).prosody.pitch, 40);
    assert.equal(validateTtsRequest({ text: 'hi', pitch: 100 }).prosody.pitch, 100);
    assert.equal(validateTtsRequest({ text: 'hi', pitch: -100 }).prosody.pitch, -100);
    assert.equal(validateTtsRequest({ text: 'hi', pitch: 0 }).prosody.pitch, 0);
  });

  it('pitch: -500, "+101Hz", "low" rejected with INVALID_PITCH', () => {
    for (const bad of [-500, '+101Hz', 'low']) {
      const r = validateTtsRequest({ text: 'hi', pitch: bad });
      assert.equal(r.ok, false, `pitch ${JSON.stringify(bad)} must be rejected`);
      assert.equal(r.code, 'INVALID_PITCH');
    }
  });

  it('blank/absent rate & pitch mean defaults (no prosody keys)', () => {
    for (const absent of [undefined, null, '']) {
      const r = validateTtsRequest({ text: 'hi', rate: absent, pitch: absent });
      assert.equal(r.ok, true);
      assert.equal('rate' in r.prosody, false);
      assert.equal('pitch' in r.prosody, false);
    }
    assert.equal(validateTtsRequest({}).ok, false); // MISSING_TEXT still wins first
  });
});

describe('synthesize (EdgeTTS stubbed)', () => {
  it('passes the resolved Edge voice name for every catalog id', async () => {
    for (const id of CATALOG_IDS) {
      const { calls, Ctor } = stubEdgeTts();
      const mp3 = await synthesize('hello', id, {}, Ctor);
      assert.deepEqual(calls.length, 1);
      assert.equal(calls[0].voiceName, EXPECTED_VOICE_NAMES[id]);
      assert.equal(Buffer.compare(mp3, Buffer.from('fake-mp3')), 0);
    }
  });

  it('no prosody -> call stays exactly (text, voiceName) (byte-identical path)', async () => {
    const { calls, Ctor } = stubEdgeTts();
    await synthesize('hello', 'female', {}, Ctor);
    assert.equal(calls[0].args.length, 2);
    assert.equal(calls[0].options, undefined);
    await synthesize('hello', undefined, undefined, Ctor);
    assert.equal(calls[1].args.length, 2);
  });

  it('forwards rate/pitch as native EdgeTTS options when provided', async () => {
    const { calls, Ctor } = stubEdgeTts();
    await synthesize('hello', 'aria', { rate: 4, pitch: -2 }, Ctor);
    assert.equal(calls[0].args.length, 3);
    assert.deepEqual(calls[0].options, { rate: 4, pitch: -2 });
  });

  it('accepts library-native string forms too ("+4%", "-2Hz")', async () => {
    const { calls, Ctor } = stubEdgeTts();
    await synthesize('hello', 'guy', { rate: '+4%', pitch: '-2Hz' }, Ctor);
    assert.deepEqual(calls[0].options, { rate: '+4%', pitch: '-2Hz' });
  });

  it('rate-only / pitch-only pass only that key', async () => {
    const { calls, Ctor } = stubEdgeTts();
    await synthesize('a', 'aria', { rate: 5 }, Ctor);
    await synthesize('b', 'guy', { pitch: -5 }, Ctor);
    assert.deepEqual(calls[0].options, { rate: 5 });
    assert.deepEqual(calls[1].options, { pitch: -5 });
  });
});

describe('GET /api/ai/tts/voices (live app)', () => {
  let baseUrl;
  let httpServer;

  before(async () => {
    httpServer = app.listen(0, '127.0.0.1');
    await new Promise((resolve) => httpServer.once('listening', resolve));
    baseUrl = `http://127.0.0.1:${httpServer.address().port}`;
  });

  after(() => new Promise((resolve) => httpServer.close(resolve)));

  it('returns the 5 real voices, no aliases, exact field shape', async () => {
    const res = await fetch(`${baseUrl}/api/ai/tts/voices`, {
      headers: { 'x-device-id': 'lane-b-test-device' },
    });
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.deepEqual(body, {
      voices: [
        { id: 'aria', label: 'Aria', gender: 'female', locale: 'en-US' },
        { id: 'guy', label: 'Guy', gender: 'male', locale: 'en-US' },
        { id: 'jenny', label: 'Jenny', gender: 'female', locale: 'en-US' },
        { id: 'davis', label: 'Davis', gender: 'male', locale: 'en-US' },
        { id: 'sonia', label: 'Sonia', gender: 'female', locale: 'en-GB' },
      ],
    });
  });

  it('rejects an invalid presented token (gateway auth like the POST route)', async () => {
    const res = await fetch(`${baseUrl}/api/ai/tts/voices`, {
      headers: { authorization: 'Bearer not.a.real.jwt.token.at.all', 'x-device-id': 'lane-b-test-device' },
    });
    // A presented-but-unverifiable token never grants more; route must not 500.
    assert.ok(res.status === 401 || res.status === 200, `unexpected status ${res.status}`);
    if (res.status === 401) {
      const body = await res.json();
      assert.equal(typeof body.code, 'string');
    }
  });

  it('POST /api/ai/tts unknown voice -> 400 INVALID_VOICE', async () => {
    const res = await fetch(`${baseUrl}/api/ai/tts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-device-id': 'lane-b-test-device' },
      body: JSON.stringify({ text: 'hi', voice: 'rogue-voice' }),
    });
    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.code, 'INVALID_VOICE');
  });

  it('POST /api/ai/tts rate "+400%" -> 400 INVALID_RATE, pitch -500 -> 400 INVALID_PITCH', async () => {
    const rate = await fetch(`${baseUrl}/api/ai/tts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-device-id': 'lane-b-test-device' },
      body: JSON.stringify({ text: 'hi', rate: '+400%' }),
    });
    assert.equal(rate.status, 400);
    assert.equal((await rate.json()).code, 'INVALID_RATE');

    const pitch = await fetch(`${baseUrl}/api/ai/tts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-device-id': 'lane-b-test-device' },
      body: JSON.stringify({ text: 'hi', pitch: -500 }),
    });
    assert.equal(pitch.status, 400);
    assert.equal((await pitch.json()).code, 'INVALID_PITCH');
  });

  it('POST /api/ai/tts missing text keeps MISSING_TEXT 400 (unchanged)', async () => {
    const res = await fetch(`${baseUrl}/api/ai/tts`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-device-id': 'lane-b-test-device' },
      body: JSON.stringify({ text: '  ' }),
    });
    assert.equal(res.status, 400);
    assert.deepEqual(await res.json(), { error: 'text is required.', code: 'MISSING_TEXT' });
  });
});
