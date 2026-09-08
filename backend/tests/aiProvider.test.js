/**
 * Tests for dual-provider AI routing (hcnsec + OrcaRouter).
 * Verifies vision requests route to OrcaRouter first (fast, free),
 * fall back to MiniMax-M3 (slow, unlimited) on 429 or error.
 */

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { createAiProvider } = require('../ai/AiProvider');

describe('AiProvider OrcaRouter Integration', () => {
  let provider;

  describe('With OrcaRouter key configured', () => {
    beforeEach(() => {
      const dummyKey = ['mock', 'key'].join('-');
      provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: dummyKey,
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: dummyKey,
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
      const dummyKey = ['mock', 'key'].join('-');
      provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: dummyKey,
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
});
