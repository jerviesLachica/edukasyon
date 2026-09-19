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
      const chain = provider.modelFallbackChain('nemotron-3.5-lightning-free', { isVision: true });
      assert.ok(chain.length > 0, 'chain should not be empty');
      assert.strictEqual(chain[0], 'z-ai/glm-5.3-flash-free', 'OrcaRouter model should be FIRST (fast path)');
      assert.ok(chain.includes('nemotron-3.5-lightning-free'), 'nemotron-3.5-lightning-free should be in chain as hcnsec fallback');
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

    it('vision fallback chain should skip OrcaRouter and wire to step-3.7-flash', () => {
      const chain = provider.modelFallbackChain('nemotron-3.5-lightning-free', { isVision: true });
      assert.ok(!chain.includes('z-ai/glm-5.3-flash-free'), 'OrcaRouter model should not be in chain without API key');
      // When converted to wire model, nemotron-3.5-lightning-free becomes step-3.7-flash
      const { toWireModelSlug } = require('../ai/AiProvider');
      const wire = toWireModelSlug(chain[0], { isVision: true });
      assert.strictEqual(wire, 'step-3.7-flash', 'Primary vision wire model should be step-3.7-flash');
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
    // The real 2026-09 policy error: OpenCode's free tier rejects server-side use.
    const missingSessionId = () => ({
      ok: false,
      status: 400,
      text: async () => JSON.stringify({ type: 'error', error: { type: 'MissingSessionID', message: "OpenCode's free tier can only be used in OpenCode" } }),
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

    function providerWithZenEnabled() {
      return createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-...ec',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'mock-...ca',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-...en',
        zenEnabled: true,
      });
    }

    it('Zen fast lane is OPT-IN: with a key but no enable flag, text goes straight to hcnsec', async () => {
      const provider = providerWithZen();
      assert.strictEqual(provider.zenActive, false, 'key alone must not activate Zen');
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
        assert.ok(!String(c.url).includes('opencode.ai'), `must not hit Zen when not enabled, got ${c.url}`);
      }
      assert.ok(String(calls[0].url).includes('hcnsec'), 'first call is hcnsec');
    });

    it('enabled Zen: text chat hits Zen FIRST with the first Zen model', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
        zenEnabled: true,
      });
      assert.strictEqual(provider.zenActive, true);
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

    it('enabled zen fast lane walks all five models in order before hcnsec', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
        zenEnabled: true,
      });
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

    it('zen MissingSessionID policy 400 advances to the next candidate, then hcnsec', async () => {
      const provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'mock-key-hcnsec',
        zenBaseUrl: 'https://opencode.ai/zen/v1',
        zenApiKey: 'mock-key-zen',
        zenEnabled: true,
      });
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        if (String(url).includes('opencode.ai')) return missingSessionId();
        return okReply('auto');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'hi' }],
        { isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      const zenCalls = calls.filter((c) => String(c.url).includes('opencode.ai'));
      assert.strictEqual(zenCalls.length, 5, 'all five zen candidates are walked');
      assert.ok(String(calls[calls.length - 1].url).includes('hcnsec'), 'ends on hcnsec');
    });

    it('zen 403 advances to the next candidate (401/402/403/429 are retryable)', async () => {
      const provider = providerWithZenEnabled();
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

    it('thinking requests (nemotron-3.5-lightning-free slug) go to hcnsec textFallback, never Zen', async () => {
      const provider = providerWithZenEnabled();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('glm-4.5-air');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'think hard' }],
        { model: 'nemotron-3.5-lightning-free', isVision: false },
      );
      assert.strictEqual(result.reply, 'hello');
      assert.ok(calls.length >= 1, 'should make at least one call');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('opencode.ai'), 'thinking must not hit Zen');
        assert.ok(String(c.url).includes('hcnsec'), 'thinking stays on hcnsec');
      }
      assert.strictEqual(calls[0].body.model, 'glm-4.5-air', 'thinking resolves to hcnsec glm-4.5-air');
    });

    it('explicit thinking:true forces hcnsec glm-4.5-air even for AUTO model', async () => {
      const provider = providerWithZenEnabled();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('glm-4.5-air');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'think hard' }],
        { model: 'auto', isVision: false, thinking: true },
      );
      assert.strictEqual(result.reply, 'hello');
      for (const c of calls) {
        assert.ok(!String(c.url).includes('opencode.ai'), 'explicit thinking must not hit Zen');
      }
      assert.strictEqual(calls[0].body.model, 'glm-4.5-air');
    });

    it('explicit thinking:false sends nemotron-3.5-lightning-free slug to Zen fast path', async () => {
      const provider = providerWithZenEnabled();
      globalThis.fetch = async (url, opts) => {
        calls.push({ url, body: JSON.parse(opts.body) });
        return okReply('nemotron-3.5-lightning-free');
      };
      const result = await provider.chatCompletion(
        [{ role: 'user', content: 'quick' }],
        { model: 'nemotron-3.5-lightning-free', isVision: false, thinking: false },
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
        zenEnabled: true,
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
        zenEnabled: true,
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
        apiKey: "k-hcnsec",
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: "k-orca",
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

// Thinking-mode answer collapse: hcnsec `auto` writes chain-of-thought into
// message.content and gets truncated by max_tokens before the final answer.
// The heuristic splitter files the whole blob as reasoning with an empty
// reply; the old code laundered it back into reply, and Android re-demoted it
// — the student saw a thinking panel and NO answer. Fix: mark laundered
// replies with replyHeuristic and retry the chain once with thinking=false.
describe('AiProvider thinking-mode answer collapse', () => {
  let realFetch;
  let calls;

  const truncationContent = [
    "Okay, let's tackle this essay. I need to make sure the tone is appropriate for a student.",
    'First, I should think about the structure — maybe an intro, three body paragraphs, and a conclusion...',
    'Wait, the user is asking for an outline, not the full essay. I should adjust my approach.',
    'Let me plan the main points. I\'ll start with the thesis, then supporting evidence...',
  ].join('\n\n');

  const completionResponse = (message, model, finishReason) => ({
    ok: true,
    status: 200,
    json: async () => ({ choices: [{ message, finish_reason: finishReason }], model }),
  });

  beforeEach(() => {
    realFetch = globalThis.fetch;
    calls = [];
  });

  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  function hcnsecOnly() {
    return createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: "test-hcn-1",
    });
  }

  it('(a) truncated heuristic CoT in content: reply laundered, reasoning null, replyHeuristic true', async () => {
    const provider = hcnsecOnly();
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return completionResponse({ content: truncationContent }, 'auto', 'length');
    };
    const result = await provider.chatCompletion(
      [{ role: 'user', content: 'write an essay outline' }],
      { model: 'auto', isVision: false },
    );
    assert.ok(result.reply.length > 0, 'reply should not be empty');
    assert.ok(result.reply.includes("Okay, let's tackle"), 'laundered reply should carry the CoT text');
    assert.strictEqual(result.reasoning, null, 'laundered reasoning must not stay in reasoning');
    assert.strictEqual(result.replyHeuristic, true, 'laundered heuristic reply must be flagged');
    assert.strictEqual(result.finishReason, 'length', 'finish_reason must be surfaced');
  });

  it('(b) provider reasoning_content is NEVER laundered into reply', async () => {
    const provider = hcnsecOnly();
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return completionResponse(
        { content: '', reasoning_content: 'The student wants help; I should be encouraging and concise...' },
        'auto',
        'stop',
      );
    };
    const result = await provider.chatCompletion(
      [{ role: 'user', content: 'hi' }],
      { model: 'auto', isVision: false },
    );
    assert.ok(!result.reply, 'provider reasoning must never become the reply');
    assert.ok(result.reasoning.includes('encouraging'), 'provider reasoning stays in the reasoning channel');
    assert.ok(!result.replyHeuristic, 'no heuristic laundering happened');
    assert.strictEqual(calls.length, 1);
  });

  it('(b3) truncated provider-only reasoning: retry runs, still no real answer -> empty response error', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'mock-key-hcnsec',
      zenBaseUrl: 'https://opencode.ai/zen/v1',
      zenApiKey: 'mock-key-zen',
    });
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return completionResponse(
        { content: '', reasoning_content: 'The student wants help; I should be encouraging...' },
        'auto',
        'length',
      );
    };
    await assert.rejects(
      () =>
        provider.chatCompletion(
          [{ role: 'user', content: 'think hard' }],
          { model: 'auto', isVision: false, thinking: true },
        ),
      /empty response/i,
    );
    assert.ok(calls.length >= 2, 'the no-thinking retry must have run before failing');
  });

  it('(b2) provider reasoning_content + empty content surfaces reasoning, replyHeuristic false', async () => {
    const provider = hcnsecOnly();
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      // content with real text so the call succeeds; provider reasoning must
      // stay in reasoning and must NOT flip replyHeuristic.
      return completionResponse(
        {
          content: 'Photosynthesis converts light energy into chemical energy inside chloroplasts.',
          reasoning_content: 'The student wants a short explanation...',
        },
        'auto',
        'stop',
      );
    };
    const result = await provider.chatCompletion(
      [{ role: 'user', content: 'explain photosynthesis' }],
      { model: 'auto', isVision: false },
    );
    assert.ok(result.reply.includes('Photosynthesis converts light'), 'real content stays the reply');
    assert.ok(result.reasoning.includes('short explanation'), 'provider reasoning kept separate');
    assert.strictEqual(result.replyHeuristic, false, 'provider-side reasoning is never a heuristic reply');
  });

  it('(c) thinking-mode truncated CoT triggers ONE no-thinking retry whose content becomes the reply', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'mock-key-hcnsec',
      zenBaseUrl: 'https://opencode.ai/zen/v1',
      zenApiKey: 'mock-key-zen',
      zenEnabled: true,
    });
    let n = 0;
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      n += 1;
      if (n === 1) {
        return completionResponse({ content: truncationContent }, 'auto', 'length');
      }
      return completionResponse({ content: 'THE REAL ANSWER' }, 'nemotron-3.5-lightning-free', 'stop');
    };
    const result = await provider.chatCompletion(
      [{ role: 'user', content: 'write an essay outline' }],
      { model: 'auto', isVision: false, thinking: true },
    );
    assert.strictEqual(result.reply, 'THE REAL ANSWER', 'retry answer replaces the laundered CoT');
    assert.ok(!result.replyHeuristic, 'real retry answer is not heuristic');
    const chatCalls = calls.filter((c) => String(c.url).includes('/chat/completions'));
    assert.ok(chatCalls.length >= 2, `expected a second chat/completions fetch, got ${chatCalls.length}`);
    assert.strictEqual(chatCalls[0].body.model, 'glm-4.5-air', 'thinking attempt leads with hcnsec glm-4.5-air');
    assert.ok(String(chatCalls[1].url).includes('opencode.ai'), 'no-thinking retry takes the Zen fast lane');
    assert.notStrictEqual(chatCalls[1].body.model, 'auto', 'retry must not stay on the thinking auto router');
  });

  it('(c2) retry that also fails returns the first result with replyHeuristic kept', async () => {
    const provider = hcnsecOnly();
    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return completionResponse({ content: truncationContent }, 'auto', 'length');
    };
    const result = await provider.chatCompletion(
      [{ role: 'user', content: 'write an essay outline' }],
      { model: 'auto', isVision: false, thinking: true },
    );
    assert.ok(result.reply.includes("Okay, let's tackle"), 'first laundered reply is kept');
    assert.strictEqual(result.replyHeuristic, true, 'replyHeuristic kept when retry did not help');
    assert.strictEqual(result.reasoning, null, 'laundered reasoning stays out of reasoning');
  });
});

describe('Multi-Provider Round-Robin & Failover', () => {
  let realFetch;
  let calls;

  const okReply = (text, model = 'test-model') => ({
    ok: true,
    status: 200,
    json: async () => ({ choices: [{ message: { content: text } }], model }),
  });

  const errorReply = (status, msg = 'error') => ({
    ok: false,
    status,
    text: async () => JSON.stringify({ error: { message: msg } }),
  });

  beforeEach(() => {
    realFetch = globalThis.fetch;
    calls = [];
  });

  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it('rotates starting provider across multiple configured free providers (Round-Robin)', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'k-hcnsec',
      groqBaseUrl: 'https://api.groq.com/openai/v1',
      groqApiKey: 'k-groq',
      openRouterBaseUrl: 'https://openrouter.ai/api/v1',
      openRouterApiKey: 'k-openrouter',
    });

    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      return okReply('success');
    };

    // Call 1
    await provider.chatCompletion([{ role: 'user', content: 'q1' }]);
    // Call 2
    await provider.chatCompletion([{ role: 'user', content: 'q2' }]);
    // Call 3
    await provider.chatCompletion([{ role: 'user', content: 'q3' }]);

    assert.strictEqual(calls.length, 3);
    const urls = calls.map((c) => String(c.url));
    // Verify that across 3 calls, all 3 providers were hit as primary
    assert.ok(urls.some((u) => u.includes('hcnsec')), 'hcnsec should be called');
    assert.ok(urls.some((u) => u.includes('groq')), 'groq should be called');
    assert.ok(urls.some((u) => u.includes('openrouter')), 'openrouter should be called');
  });

  it('automatically fails over to next provider when primary returns 429 rate limit', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'k-hcnsec',
      groqBaseUrl: 'https://api.groq.com/openai/v1',
      groqApiKey: 'k-groq',
    });

    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      if (String(url).includes('hcnsec')) {
        return errorReply(429, 'Rate limit exceeded');
      }
      return okReply('Groq answer', 'llama-3.3-70b-versatile');
    };

    const result = await provider.chatCompletion([{ role: 'user', content: 'test question' }]);
    assert.strictEqual(result.reply, 'Groq answer');
    assert.strictEqual(result.provider, 'groq');
    assert.ok(calls.length >= 2, 'should have attempted hcnsec then groq');
  });

  it('automatically fails over to next provider when primary returns 500 server error', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'k-hcnsec',
      geminiBaseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
      geminiApiKey: 'k-gemini',
    });

    globalThis.fetch = async (url, opts) => {
      calls.push({ url, body: JSON.parse(opts.body) });
      if (String(url).includes('hcnsec')) {
        return errorReply(500, 'Internal Server Error');
      }
      return okReply('Gemini answer', 'gemini-2.0-flash');
    };

    const result = await provider.chatCompletion([{ role: 'user', content: 'test question' }]);
    assert.strictEqual(result.reply, 'Gemini answer');
    assert.strictEqual(result.provider, 'gemini');
  });

  it('marks rate-limited provider into cooldown and deprioritizes it', async () => {
    const provider = createAiProvider({
      baseUrl: 'https://api.hcnsec.cn/v1',
      apiKey: 'k-hcnsec',
      groqBaseUrl: 'https://api.groq.com/openai/v1',
      groqApiKey: 'k-groq',
    });

    provider.markProviderCooldown('hcnsec', 60000);
    assert.strictEqual(provider.isProviderInCooldown('hcnsec'), true);

    const pool = provider.getRoundRobinOrder();
    // Groq is ready, hcnsec is cooling down, so Groq should be first
    assert.strictEqual(pool[0].name, 'groq');
  });

  it('omits text-only providers from vision candidate chain', () => {
    const provider = createAiProvider({
      apiKey: 'k-hcnsec',
      cerebrasApiKey: 'k-cerebras',
      groqApiKey: 'k-groq',
    });

    const active = provider.getAvailableProviders({ isVision: true });
    const cerebrasInVision = active.some((p) => p.name === 'cerebras');
    assert.strictEqual(cerebrasInVision, false, 'Cerebras must not be in vision pool');
  });
});

