/**
 * Tests for dual-provider AI routing (hcnsec + OrcaRouter).
 * Verifies vision requests route to OrcaRouter first (fast, free),
 * fall back to MiniMax-M3 (slow, unlimited) on 429 or error.
 *
 * Plus Cerebras text-chat routing: text-only requests go to Cerebras
 * first (fast), fall back to hcnsec; vision never touches Cerebras.
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

  describe('Cerebras text-chat routing', () => {
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

    function providerWithCerebras() {
      const dummyKey = ['mock', 'key'].join('-');
      return createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: dummyKey + '-hcnsec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: dummyKey + '-orca',
        cerebrasBaseUrl: 'https://api.cerebras.ai/v1',
        cerebrasApiKey: dummyKey + '-cerebras',
      });
    }

    it('text chat hits Cerebras FIRST with the Cerebras model', async () => {
      const provider = providerWithCerebras();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('llama-3.3-70b');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 1, 'should make at least one call');
      assert.ok(
        calls[0].url.startsWith('https://api.cerebras.ai/v1/chat/completions'),
        `first call should go to Cerebras, got ${calls[0].url}`,
      );
      assert.strictEqual(calls[0].body.model, provider.CEREBRAS_TEXT_MODEL);
    });

    it('text chat falls back to hcnsec when Cerebras returns 429', async () => {
      const provider = providerWithCerebras();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('cerebras')) return rateLimited();
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 2, 'should retry after Cerebras 429');
      assert.ok(String(calls[0].url).includes('cerebras'), 'first call is Cerebras');
      assert.ok(String(calls[1].url).includes('hcnsec'), 'fallback call is hcnsec');
    });

    it('vision requests NEVER touch Cerebras (stay on OrcaRouter -> hcnsec)', async () => {
      const provider = providerWithCerebras();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('z-ai/glm-5.3-flash-free');
      };
      await provider.chatCompletion(
        [{ role: 'user', content: [{ type: 'image_url', image_url: { url: 'data:image/png;base64,xx' } }] }],
        { isVision: true },
      );
      assert.ok(calls.length >= 1, 'should make at least one call');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('cerebras'), `vision must not hit Cerebras, got ${c.url}`);
      }
      assert.ok(String(calls[0].url).includes('orcarouter'), 'vision still goes OrcaRouter first');
    });

    it('text chat works without a Cerebras key (pure hcnsec, unchanged behavior)', async () => {
      const dummyKey = ['mock', 'key'].join('-');
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: dummyKey + '-hcnsec',
      });
      assert.strictEqual(provider.hasCerebrasKey, false);
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

    it('thinking requests (agnes slug) go to hcnsec auto, never Cerebras', async () => {
      const provider = providerWithCerebras();
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
        assert.ok(!String(c.url).includes('cerebras'), 'thinking must not hit Cerebras');
        assert.ok(String(c.url).includes('hcnsec'), 'thinking stays on hcnsec');
      }
      assert.strictEqual(calls[0].body.model, 'auto', 'thinking resolves to hcnsec auto');
    });

    it('explicit thinking:true forces hcnsec auto even for AUTO model', async () => {
      const provider = providerWithCerebras();
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
        assert.ok(!String(c.url).includes('cerebras'), 'explicit thinking must not hit Cerebras');
      }
      assert.strictEqual(calls[0].body.model, 'auto');
    });

    it('explicit thinking:false sends agnes slug to Cerebras fast path', async () => {
      const provider = providerWithCerebras();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply(provider.CEREBRAS_TEXT_MODEL);
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'quick' }],
        { model: 'agnes-2.5-flash', isVision: false, thinking: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(String(calls[0].url).includes('cerebras'), 'explicit non-thinking uses Cerebras');
    });
  });
});
