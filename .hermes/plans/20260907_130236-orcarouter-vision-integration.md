# OrcaRouter Vision Integration Plan

**Goal:** Replace slow MiniMax-M3 vision (60-120s per scan) with OrcaRouter's GLM 5.3 Flash (5-12s) while maintaining zero-cost operation and fallback safety.

---

## Current Context

**Assumptions:**
- Backend: Node.js Express with `AiProvider.js` handling AI routing
- Current vision model: `MiniMax-M3` via `https://api.hcnsec.cn/v1` (unlimited, slow)
- OrcaRouter API key available: `sk-orca-5Vi7RYLpPl9kTtLs7ZujMw5mtwhWKD9I15ZeVaUeZk2`
- OrcaRouter free vision model: `z-ai/glm-5.3-flash-free` (5-12s, 10 RPM free tier, $0)
- Tests exist in `backend/tests/*.test.js` using Node.js native test framework

**Rate limits (OrcaRouter free):**
- ~10 requests per minute
- ~1,440 requests per day
- HTTP 429 with `Retry-After` header when exhausted
- No cost ever — free tier never expires

---

## Proposed Approach

1. **Dual-provider routing:** Update `toWireModelSlug()` to route vision requests to OrcaRouter first (fast), fall back to MiniMax-M3 (slow but unlimited) on 429 or network failure.
2. **Environment-driven:** Add `ORCA_API_KEY` and `ORCA_BASE_URL` env vars to Render config; if not set, default to current hcnsec behavior.
3. **Fallback chain:** Model resolution already supports fallback chains — add OrcaRouter's `z-ai/glm-5.3-flash-free` to the vision chain before `MiniMax-M3`.
4. **No schema changes:** Vision API response format is identical (OpenAI-compatible JSON).

---

## Step-by-Step Tasks

### Task 1: Add OrcaRouter constants to AiProvider.js
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js`  
**Time:** 2 min

After line 27 (after `DEFAULT_VISION_MODEL = 'MiniMax-M3'`), add:

```javascript
// OrcaRouter free tier for faster vision (5-12s vs 60-120s).
// free tier: 10 RPM, ~1440/day, $0 forever. Falls back to MiniMax-M3 on 429.
const ORCA_VISION_MODEL = 'z-ai/glm-5.3-flash-free';
```

**Verify:**
```bash
grep -n "ORCA_VISION_MODEL" C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js
# Expected: one match, ~line 29
```

---

### Task 2: Add OrcaRouter provider setup to createAiProvider()
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js`  
**Time:** 3 min

After line 76 (after `VISION_MODEL` initialization), add:

```javascript
  // OrcaRouter secondary provider for fast vision
  const ORCA_API_KEY = config.orcaApiKey || process.env.ORCA_API_KEY || '';
  const ORCA_BASE_URL = (
    config.orcaBaseUrl ||
    process.env.ORCA_BASE_URL ||
    'https://api.orcarouter.ai/v1'
  ).replace(/\/$/, '');
```

**Verify:**
```bash
grep -n "ORCA_API_KEY\|ORCA_BASE_URL" C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js
# Expected: two matches, ~lines 77-83
```

---

### Task 3: Update toWireModelSlug() to support OrcaRouter
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js`  
**Time:** 5 min

Replace the `toWireModelSlug()` function (lines 46-53) with:

```javascript
// Maps a resolved slug to the model id actually sent upstream.
// Vision requests can use OrcaRouter (fast, $0) or fall back to MiniMax-M3 (slow, unlimited).
function toWireModelSlug(slug, { isVision = false, provider = 'hcnsec' } = {}) {
  const normalized = normalizeModelSlug(slug);
  
  // OrcaRouter provider (if explicitly requested or auto-selected)
  if (provider === 'orca') {
    if (isVision && normalized === 'agnes-2.5-flash') {
      return ORCA_VISION_MODEL;
    }
    return normalized;
  }
  
  // Default hcnsec provider
  if (isVision && (normalized === 'agnes-2.5-flash' || normalized === 'auto')) {
    return 'MiniMax-M3';
  }
  if (normalized === 'agnes-2.5-flash') return 'MiniMax-M3';
  return normalized;
}
```

**Verify:**
```bash
grep -A 20 "function toWireModelSlug" C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js | head -25
# Expected: function should accept `provider` parameter
```

---

### Task 4: Add OrcaRouter to modelFallbackChain()
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js`  
**Time:** 4 min

Replace the `modelFallbackChain()` function (lines 123-138) with:

```javascript
  function modelFallbackChain(primaryModel, { isVision = false } = {}) {
    const normalizedPrimary = normalizeModelSlug(primaryModel);
    const chain = [normalizedPrimary];
    
    if (isVision) {
      // Try OrcaRouter first if configured (fast, free tier)
      if (ORCA_API_KEY && !chain.includes(ORCA_VISION_MODEL)) {
        chain.push(ORCA_VISION_MODEL);
      }
      // Add other vision-capable models (fallback to unlimited providers)
      for (const candidate of VISION_CAPABLE_MODELS) {
        if (candidate === normalizedPrimary || candidate === 'auto') continue;
        if (!chain.includes(candidate)) chain.push(candidate);
      }
    }
    
    if (primaryModel !== TEXT_MODEL && !chain.includes(TEXT_MODEL)) chain.push(TEXT_MODEL);
    if (primaryModel !== DEFAULT_MODEL && !chain.includes(DEFAULT_MODEL)) chain.push(DEFAULT_MODEL);
    return chain;
  }
```

**Verify:**
```bash
grep -A 20 "function modelFallbackChain" C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js | head -25
# Expected: chain should include ORCA_VISION_MODEL when ORCA_API_KEY is set
```

---

### Task 5: Update chatCompletion() to route to OrcaRouter
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js`  
**Time:** 6 min

Replace the `chatCompletion()` function signature and body (lines 264-298) to support provider selection.  
At line 264, change function signature to:

```javascript
  async function chatCompletion(messages, { temperature = 0.7, maxTokens = 2048, model, isVision = false, signal, responseFormat, reasoning, wireModelOverride } = {}) {
    if (!hasAiKey && (!isVision || !ORCA_API_KEY)) throw new Error('AI provider not configured (set AI_API_KEY or ORCA_API_KEY)');
    
    // Build wire model chain with provider info
    const wireModels = wireModelOverride
      ? [{ model: toWireModelSlug(wireModelOverride, { isVision }), provider: 'hcnsec' }]
      : (() => {
          const chain = [];
          for (const candidate of modelFallbackChain(model || (isVision ? VISION_MODEL : TEXT_MODEL), { isVision })) {
            // Determine provider for this candidate
            let provider = 'hcnsec';
            let wire = toWireModelSlug(candidate, { isVision, provider });
            
            if (candidate === ORCA_VISION_MODEL && ORCA_API_KEY) {
              provider = 'orca';
              wire = ORCA_VISION_MODEL;
            } else {
              wire = toWireModelSlug(candidate, { isVision });
            }
            
            if (wire && !chain.find(c => c.model === wire)) {
              chain.push({ model: wire, provider });
            }
          }
          return chain;
        })();
    
    const candidates = wireModels.length ? wireModels : [{ model: isVision ? 'MiniMax-M3' : 'auto', provider: 'hcnsec' }];
    let lastError;
    
    for (let i = 0; i < candidates.length; i += 1) {
      const { model: candidate, provider } = candidates[i];
      const baseUrl = provider === 'orca' ? ORCA_BASE_URL : AI_BASE_URL;
      const apiKey = provider === 'orca' ? ORCA_API_KEY : AI_API_KEY;
      
      try {
        if (i > 0) console.warn(`[ai] Retrying with fallback model=${candidate} (provider=${provider})`);
        const result = await chatCompletionOnce(messages, { 
          temperature, maxTokens, model: candidate, signal, responseFormat, reasoning,
          baseUrl, apiKey
        });
        return { ...result, model: result.model || candidate };
      } catch (err) {
        // Handle 429 (rate limit) as retryable
        if (err.message && err.message.includes('429')) {
          console.warn(`[ai] Provider ${provider} rate limited; trying next fallback`);
        }
        
        // Some providers reject response_format — drop it and retry same model once
        if (responseFormat && /response_format|unsupported|invalid.*format/i.test(String(err.message || ''))) {
          console.warn('[ai] response_format rejected; retrying without it');
          try {
            const retry = await chatCompletionOnce(messages, { 
              temperature, maxTokens, model: candidate, signal,
              baseUrl, apiKey
            });
            return { ...retry, model: retry.model || candidate };
          } catch (retryErr) {
            lastError = retryErr;
            if (i < candidates.length - 1 && isRetryableModelError(retryErr.message)) continue;
            throw retryErr;
          }
        }
        
        lastError = err;
        const hasNext = i < candidates.length - 1;
        if (!hasNext || !isRetryableModelError(err.message)) throw err;
        console.warn(`[ai] Model ${candidate} failed: ${String(err.message || err).slice(0, 160)}`);
      }
    }
    throw lastError || new Error('AI API request failed');
  }
```

Also update `chatCompletionOnce()` signature (line 243) to accept baseUrl and apiKey:

```javascript
  async function chatCompletionOnce(messages, { temperature = 0.7, maxTokens = 2048, model, signal, responseFormat, reasoning, baseUrl, apiKey } = {}) {
    const payload = { model, messages, temperature, max_tokens: maxTokens };
    if (responseFormat) payload.response_format = responseFormat;
    if (reasoning && typeof reasoning !== 'string') payload.reasoning = reasoning;
    
    const url = baseUrl || AI_BASE_URL;
    const key = apiKey || AI_API_KEY;
    
    const res = await fetch(`${url}/chat/completions`, {
      method: 'POST',
      headers: providerHeaders(key),
      body: JSON.stringify(payload),
      signal,
    });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`AI API error ${res.status}: ${body.slice(0, 300)}`);
    }
    const data = await res.json();
    return parseChatCompletionResult(data);
  }
```

**Verify:**
```bash
grep -c "provider === 'orca'" C:/Users/HP/AndroidStudioProjects/edukasyon/backend/ai/AiProvider.js
# Expected: count >= 3
```

---

### Task 6: Update render.yaml to include OrcaRouter env vars
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/render.yaml`  
**Time:** 2 min

After line 27 (after `SCAN_AI_API_KEY`), add:

```yaml
      # OrcaRouter for fast vision (~5-12s). Free tier: 10 RPM, $0 forever.
      # Falls back to VISION_MODEL (MiniMax-M3) on rate limit or error.
      - key: ORCA_API_KEY
        sync: false
      - key: ORCA_BASE_URL
        value: https://api.orcarouter.ai/v1
```

**Verify:**
```bash
grep -n "ORCA_API_KEY\|ORCA_BASE_URL" C:/Users/HP/AndroidStudioProjects/edukasyon/render.yaml
# Expected: two matches after line 27
```

---

### Task 7: Write test for OrcaRouter routing
**File:** `C:/Users/HP/AndroidStudioProjects/edukasyon/backend/tests/aiProvider.test.js` (create new)  
**Time:** 8 min

Create file with:

```javascript
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
      provider = createAiProvider({
        baseUrl: 'https://api.hcnsec.cn/v1',
        apiKey: 'sk-hcnsec-test',
        orcaBaseUrl: 'https://api.orcarouter.ai/v1',
        orcaApiKey: 'sk-orca-test',
      });
    });

    it('vision model fallback chain should include OrcaRouter model before MiniMax-M3', () => {
      const chain = provider.modelFallbackChain('agnes-2.5-flash', { isVision: true });
      // Chain should be: [agnes-2.5-flash, z-ai/glm-5.3-flash-free (OrcaRouter), MiniMax-M3, ...]
      assert.ok(chain.length > 0, 'chain should not be empty');
      assert.ok(chain.includes('z-ai/glm-5.3-flash-free'), 'OrcaRouter model should be in chain');
    });

    it('wire model mapping should preserve OrcaRouter model id', () => {
      // When explicitly routing via OrcaRouter, preserve the model id
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

    it('vision fallback chain should skip OrcaRouter and use MiniMax-M3', () => {
      const chain = provider.modelFallbackChain('agnes-2.5-flash', { isVision: true });
      // Chain should not include OrcaRouter model
      assert.ok(!chain.includes('z-ai/glm-5.3-flash-free'), 'OrcaRouter model should not be in chain without API key');
      assert.ok(chain.includes('MiniMax-M3'), 'MiniMax-M3 should be in fallback chain');
    });
  });
});
```

**Verify:**
```bash
npm test -- tests/aiProvider.test.js 2>&1 | head -30
# Expected: all tests pass
```

---

### Task 8: Deploy to Render
**File:** Render Dashboard  
**Time:** 3 min

1. Go to https://dashboard.render.com/ → **studentai-backend** service
2. Click **Environment** tab
3. Add secret:
   - **Key:** `ORCA_API_KEY`
   - **Value:** `sk-orca-5Vi7RYLpPl9kTtLs7ZujMw5mtwhWKD9I15ZeVaUeZk2`
4. Click **Save** (auto-deploys)
5. Wait ~60s for health check to pass
6. Verify endpoint logs:
   ```bash
   curl https://studentai-backend-ha0z.onrender.com/health
   # Expected: {"ok":true,"ai":{"text":"auto","vision":"z-ai/glm-5.3-flash-free"}}
   ```

---

## Tests / Validation

### Unit test suite (verify implementation)
```bash
cd C:/Users/HP/AndroidStudioProjects/edukasyon/backend
npm test
# Expected: all tests pass (new aiProvider.test.js + existing tests)
```

### Local integration test (optional, before deploying)
```bash
export ORCA_API_KEY='sk-orca-5Vi7RYLpPl9kTtLs7ZujMw5mtwhWKD9I15ZeVaUeZk2'
export ORCA_BASE_URL='https://api.orcarouter.ai/v1'
npm start
# POST http://localhost:10000/api/ai/schedule-analysis with a real schedule image
# Expected: response in 5-15s (not 60-120s)
# Check server logs: [ai] Retrying with fallback model=... should NOT appear on success
```

### Production validation (after deploy)
```bash
# Send a schedule analysis request via the Android app or direct API
# Expected latency: 5-15s (not 60-120s)
# Check Render logs:
#   - No "rate limited" errors on first 10 consecutive scans
#   - After ~10th scan, should see "[ai] Retrying with fallback model=MiniMax-M3"
#   - Scans continue working (slow) after rate limit
```

---

## Risks, Tradeoffs, and Open Questions

**Risks:**
- **OrcaRouter 429 rate limit:** Free tier caps at ~10 RPM. If 11+ students scan simultaneously or >1,440 scans/day total, subsequent scans hit 429 and fall back to MiniMax-M3 (60-120s). Mitigation: Add `Retry-After` header handling in fallback logic to wait before retrying.
- **Provider mismatch:** If OrcaRouter API goes down, the fallback is immediate but users see slower scans. Mitigation: Log all fallbacks clearly; consider alerting on excessive 429s.

**Tradeoffs:**
- **Dual provider complexity:** Slightly more code (provider selection in `chatCompletion`), but minimal (single `if provider === 'orca'` check per request).
- **No cost NOW, potential future cost:** OrcaRouter free tier is permanent per their docs, but they reserve the right to change it. Current: $0 forever. If it changes, just remove the env vars and revert to hcnsec-only routing.

**Open Questions:**
- Should we add a prometheus/status endpoint that reports which provider served each request (for observability)?  
  → Out of scope for this plan; can be added later if needed.
- Should we implement adaptive rate-limit awareness (reduce batch size when 429 detected)?  
  → Out of scope; current fallback strategy is sufficient.

---

## Commits (one per task)

1. `Add OrcaRouter constants` → just the const
2. `Add OrcaRouter provider config` → env vars only
3. `Update toWireModelSlug with provider parameter`
4. `Update modelFallbackChain to include OrcaRouter`
5. `Update chatCompletion and chatCompletionOnce for dual routing`
6. `Add OrcaRouter vars to render.yaml`
7. `Add aiProvider.test.js for routing logic`
8. Render deploy (manual, no commit)

**Total time to execute: ~30 minutes**
