/**
 * Tests for dual-provider AI routing (hcnsec + OrcaRouter).
 * Verifies vision requests route to OrcaRouter first (fast, free),
 * fall back to MiniMax-M3 (slow, unlimited) on 429 or error.
 *
 * Plus Zen (OpenCode) text-chat routing: non-thinking text-only requests
 * go to Zen first (fast lane of free models), fall back to hcnsec;
 * thinking stays on hcnsec auto. Cerebras was removed from the chain.
 */

const { describe, it, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { createAiProvider } = require('../ai/AiProvider');

describe('AiProvider OrcaRouter Integration', () => {
  let provider;

  describe('With OrcaRouter key configured', () => {
    beforeEach(() => {
      provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'sk-hcnsec-test',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'sk-orca-test',
      });
    });

    it('vision model fallback chain should have OrcaRouter FIRST, hcnsec LAST', () => {
      const chain = provider.modelFallbackChain('agnes-2.5-flash', { isVision: true });
      assert.ok(chain.length > 0, 'chain should not be empty');
      assert.strictEqual(chain[0], 'z-ai/glm-5.3-flash-free', 'OrcaRouter model should be FIRST (fast path)');
      assert.ok(chain.includes('agnes-2.5-flash'), 'agnes-2.5-flash should be in chain as hcnsec fallback');
    });

    it('wire model mapping should preserve OrcaRouter model id', () => {
      const wire = require('../ai/AiProvider').toWireModelSlug('z-ai/glm-5.3-flash-free', { isVision: true, provider: 'orca' });
      assert.strictEqual(wire, 'z-ai/glm-5.3-flash-free', 'OrcaRouter model should pass through unchanged');
    });
  });

  describe('Without OrcaRouter key (fallback)', () => {
    beforeEach(() => {
      provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'sk-hcnsec-test',
        // No orcaApiKey
      });
    });

    it('vision fallback chain should skip OrcaRouter and wire to MiniMax-M3', () => {
      const chain = provider.modelFallbackChain('agnes-2.5-flash', { isVision: true });
      assert.ok(!chain.includes('z-ai/glm-5.3-flash-free'), 'OrcaRouter model should not be in chain without API key');
      // When converted to wire model, agnes-2.5-flash becomes MiniMax-M3
      const { toWireModelSlug } = require('../ai/AiProvider');
      const wire = toWireModelSlug(chain[0], { isVision: true });
      assert.strictEqual(wire, 'MiniMax-M3', 'Primary vision wire model should be MiniMax-M3');
    });
  });

  describe('Zen text-chat routing', () => {
    let realFetch;
    let calls;

    const okReply = (model) => ({
      ok: true,
      status: 200,
      json: async () => ({ choices: [{ message: { content: 'hello' } }], model }),
    });
    const rateLimited = () => ({
      ok: false,
      status: 429,
      text: async () => 'rate limited',
    });

    beforeEach(() => {
      realFetch = globalThis.fetch;
      calls = [];
    });

    afterEach(() => {
      globalThis.fetch = realFetch;
    });

    function providerWithZen() {
      return createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'mock-key-orca',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
      });
    }

    it('text chat hits Zen FIRST with the first Zen model', async () => {
      const provider = providerWithZen();
      assert.strictEqual(provider.hasZenKey, true);
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('nemotron-3.5-lightning-free');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 1, 'should make at least one call');
      assert.ok(
        calls[0].url.startsWith('https://opencode.ai/zen/v1/chat/completions'),
        `first call should go to Zen, got ${calls[0].url}`,
      );
      assert.strictEqual(calls[0].body.model, 'nemotron-3.5-lightning-free');
    });

    it('zen fast lane walks all five models in order before hcnsec', async () => {
      const provider = providerWithZen();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('opencode.ai')) return rateLimited();
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      const zenCalls = calls.filter((c) => String(c.url).includes('opencode.ai'));
      assert.deepStrictEqual(
        zenCalls.map((c) => c.body.model),
        [
          'nemotron-3.5-lightning-free',
          'deepseek-v4-flash-free',
          'mimo-v2.5-free',
          'nemotron-3-ultra-free',
          'ling-3.0-flash-fin-free',
        ],
      );
      assert.ok(String(calls[calls.length - 1].url).includes('hcnsec'), 'final fallback is hcnsec');
    });

    it('text chat falls back to hcnsec when Zen returns 429', async () => {
      const provider = providerWithZen();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('opencode.ai')) return rateLimited();
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 2, 'should retry after Zen 429');
      assert.ok(String(calls[0].url).includes('opencode.ai'), 'first call is Zen');
      assert.ok(String(calls[calls.length - 1].url).includes('hcnsec'), 'fallback call is hcnsec');
    });

    it('zen 403 advances to the next candidate (401/402/403/429 are retryable)', async () => {
      const provider = providerWithZen();
      let n = 0;
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        n += 1;
        if (n === 1) return { ok: false, status: 403, text: async () => 'forbidden' };
        return okReply('deepseek-v4-flash-free');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.strictEqual(calls.length, 2, 'should advance exactly one candidate after 403');
      assert.strictEqual(calls[1].body.model, 'deepseek-v4-flash-free');
    });

    it('text chat works without a Zen key (pure hcnsec, unchanged behavior)', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
      });
      assert.strictEqual(provider.hasZenKey, false);
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(String(calls[0].url).includes('hcnsec'), 'goes straight to hcnsec');
    });

    it('cerebras key alone no longer routes text to Cerebras (removed from chain)', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        cerebrasBaseUrl: 'https://api.cerebras.ai/v1',
        cerebrasApiKey: 'mock-key-cerebras',
      });
      assert.strictEqual(provider.hasCerebrasKey, true);
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('cerebras'), `text must not hit Cerebras, got ${c.url}`);
      }
      assert.ok(String(calls[0].url).includes('hcnsec'), 'goes straight to hcnsec');
    });

    it('thinking requests (agnes slug) go to hcnsec auto, never Zen', async () => {
      const provider = providerWithZen();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'think hard' }],
        { model: 'agnes-2.5-flash', isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 1, 'should make at least one call');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('opencode.ai'), 'thinking must not hit Zen');
        assert.ok(String(c.url).includes('hcnsec'), 'thinking stays on hcnsec');
      }
      assert.strictEqual(calls[0].body.model, 'auto', 'thinking resolves to hcnsec auto');
    });

    it('explicit thinking:true forces hcnsec auto even for AUTO model', async () => {
      const provider = providerWithZen();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'think hard' }],
        { model: 'auto', isVision: false, thinking: true },
      );
      assert.strictEqual(result.reply, 'hello');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('opencode.ai'), 'explicit thinking must not hit Zen');
      }
      assert.strictEqual(calls[0].body.model, 'auto');
    });

    it('explicit thinking:false sends agnes slug to Zen fast path', async () => {
      const provider = providerWithZen();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('nemotron-3.5-lightning-free');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'quick' }],
        { model: 'agnes-2.5-flash', isVision: false, thinking: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(String(calls[0].url).includes('opencode.ai'), 'explicit non-thinking uses Zen fast path');
    });

    it('vision hits Zen vision FIRST with Gemini fallback intact', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'mock-key-orca',
        geminiBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
        geminiApiKey: 'mock-key-gemini',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
      });
      assert.strictEqual(provider.ZEN_VISION_MODEL, 'deepseek-v4-flash-vision-exp');
      const visionMsg = [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,xx' } }] }];
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('deepseek-v4-flash-vision-exp');
      };
      const result = await provider.chatCompletion(visionMsg, { isVision: true });
      assert.strictEqual(result.reply, 'hello');
      assert.ok(String(calls[0].url).includes('opencode.ai'), 'vision first goes to Zen');
      assert.strictEqual(calls[0].body.model, 'deepseek-v4-flash-vision-exp');
    });

    it('vision falls back Zen -> Gemini -> Orca -> hcnsec on 429s', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'mock-key-orca',
        geminiBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
        geminiApiKey: 'mock-key-gemini',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
      });
      const visionMsg = [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,xx' } }] }];
      const denied = () => ({ ok: false, status: 429, text: async () => 'rate limited' });
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('opencode.ai')) return denied();
        if (String(url).includes('googleapis')) return denied();
        if (String(url).includes('orcarouter')) return denied();
        return okReply('MiniMax-M3');
      };
      const result = await provider.chatCompletion(visionMsg, { isVision: true });
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 4, 'should walk the full chain');
      assert.ok(String(calls[0].url).includes('opencode.ai'), '1st is Zen');
      assert.ok(String(calls[1].url).includes('googleapis'), '2nd is Gemini');
      assert.ok(String(calls[2].url).includes('orcarouter'), '3rd is OrcaRouter');
      assert.ok(String(calls[3].url).includes('hcnsec'), '4th is hcnsec');
    });
  });

  describe('Gemini vision routing', () => {
    let realFetch;
    let calls;

    const okReply = (model) => ({
      ok: true,
      status: 200,
      json: async () => ({ choices: [{ message: { content: 'seen' } }], model }),
    });

    beforeEach(() => {
      realFetch = globalThis.fetch;
      calls = [];
    });

    afterEach(() => {
      globalThis.fetch = realFetch;
    });

    const visionMsg = [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,xx' } }] }];

    function providerWithGemini() {
      return createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'k-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'k-orca',
        geminiBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
        geminiApiKey: 'k-gemini',
      });
    }

    it('vision hits Gemini FIRST with the Gemini model', async () => {
      const provider = providerWithGemini();
      assert.strictEqual(provider.hasGeminiKey, true);
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply(provider.GEMINI_VISION_MODEL);
      };
      const result = await provider.chatCompletion(visionMsg, { isVision: true });
      assert.strictEqual(result.reply, 'seen');
      assert.ok(String(calls[0].url).includes('googleapis'), 'first call is Gemini');
      assert.strictEqual(calls[0].body.model, provider.GEMINI_VISION_MODEL);
    });

    it('vision falls back Gemini -> Orca -> hcnsec on 429s', async () => {
      const provider = providerWithGemini();
      const denied = () => ({ ok: false, status: 429, text: async () => 'rate limited' });
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('googleapis')) return denied();
        if (String(url).includes('orcarouter')) return denied();
        return okReply('MiniMax-M3');
      };
      const result = await provider.chatCompletion(visionMsg, { isVision: true });
      assert.strictEqual(result.reply, 'seen');
      assert.ok(calls.length >= 3, 'should walk the full chain');
      assert.ok(String(calls[0].url).includes('googleapis'), '1st is Gemini');
      assert.ok(String(calls[1].url).includes('orcarouter'), '2nd is OrcaRouter');
      assert.ok(String(calls[2].url).includes('hcnsec'), '3rd is hcnsec');
    });

    it('vision without Gemini key keeps Orca-first behavior', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'k-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'k-orca',
      });
      assert.strictEqual(provider.hasGeminiKey, false);
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('x');
      };
      await provider.chatCompletion(visionMsg, { isVision: true });
      assert.ok(String(calls[0].url).includes('orcarouter'), 'Orca still first without Gemini key');
    });
  });
});
