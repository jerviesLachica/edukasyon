/**
 * AI provider adapter — wraps OpenAI-compatible chat completions.
 * Extracted from server.js for use by AiSafetyGateway.
 */

function envModelList(name) {
  return (process.env[name] || '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

const BASE_ALLOWED_MODELS = ['auto', 'nemotron-3.5-lightning-free'];
// Operators extend the catalog via env (e.g. NVIDIA NIM ids) without code changes.
const CONFIGURED_MODELS = envModelList('ALLOWED_MODELS');
const ALLOWED_MODELS = [...new Set([...BASE_ALLOWED_MODELS, ...CONFIGURED_MODELS])];
const VISION_CAPABLE_MODELS = [
  ...new Set([
    'nemotron-3.5-lightning-free',
    ...envModelList('VISION_CAPABLE_MODELS'),
    ...CONFIGURED_MODELS,
  ]),
];
const DEFAULT_TEXT_MODEL = 'auto';
// Vision default is the canonical Zen slug — the wire layer maps it to the
// upstream multimodal model (`auto` routing returns a text-only model,
// 400: does not support multimodal). Zen vision (when configured) goes first
// in the chain; Gemini / OrcaRouter / hcnsec remain as fallbacks.
const DEFAULT_VISION_MODEL = 'nemotron-3.5-lightning-free';
// OrcaRouter free tier for faster vision (5-12s vs 60-120s).
// free tier: 10 RPM, ~1440/day, $0 forever. Falls back to MiniMax-M3 on 429.
// NOTE: `orcarouter/auto` is key-permission gated (403 model_access_denied
// on free keys) — default stays on the explicit free model. Override via
// ORCA_MODEL env if the key ever gains auto access.
const ORCA_VISION_MODEL = process.env.ORCA_MODEL || 'z-ai/glm-5.3-flash-free';

const LEGACY_VISION_ALIASES = {
  'agnes-2.5-flash': 'step-3.7-flash',
};

const FAST_TEXT_MODEL = process.env.FAST_TEXT_MODEL || 'stepaudio-2.5-chat';

function normalizeModelSlug(slug) {
  if (typeof slug !== 'string') return slug;
  const trimmed = slug.trim();
  return LEGACY_VISION_ALIASES[trimmed] || trimmed;
}

// Maps a resolved slug to the model id actually sent upstream.
function toWireModelSlug(slug, { isVision = false, provider = 'hcnsec' } = {}) {
  const normalized = normalizeModelSlug(slug);
  const textFallback = process.env.TEXT_MODEL || 'glm-4.5-air';
  const visionFallback = process.env.VISION_MODEL || 'step-3.7-flash';

  // OrcaRouter provider (if explicitly requested or auto-selected)
  if (provider === 'orca') {
    if (isVision && (normalized === 'nemotron-3.5-lightning-free' || normalized === 'auto')) {
      return ORCA_VISION_MODEL;
    }
    return normalized === 'auto' ? textFallback : normalized;
  }
  if (provider === 'groq') {
    if (isVision) return process.env.GROQ_VISION_MODEL || 'llama-3.2-11b-vision-preview';
    return process.env.GROQ_MODEL || 'llama-3.3-70b-versatile';
  }
  if (provider === 'gemini') {
    if (isVision) return process.env.GEMINI_VISION_MODEL || 'gemini-2.0-flash';
    return process.env.GEMINI_MODEL || 'gemini-2.0-flash';
  }
  if (provider === 'openrouter') {
    if (isVision) return process.env.OPENROUTER_VISION_MODEL || 'google/gemini-2.0-flash-exp:free';
    return process.env.OPENROUTER_MODEL || 'meta-llama/llama-3.3-70b-instruct:free';
  }
  if (provider === 'cerebras') {
    return process.env.CEREBRAS_MODEL || 'llama-3.3-70b';
  }
  // Default hcnsec provider
  if (isVision && (normalized === 'nemotron-3.5-lightning-free' || normalized === 'auto')) {
    return visionFallback;
  }
  if (normalized === 'nemotron-3.5-lightning-free') return textFallback;
  if (normalized === 'auto') return textFallback;
  return normalized;
}

function envModel(name, fallback) {
  const value = normalizeModelSlug(process.env[name]);
  return ALLOWED_MODELS.includes(value) ? value : fallback;
}

function createAiProvider(config = {}) {
  const AI_BASE_URL = (
    config.baseUrl ||
    process.env.AI_BASE_URL ||
    process.env.TEXT_AI_BASE_URL ||
    process.env.VISION_AI_BASE_URL ||
    'https://api.hcnsec.cn/v1'
  ).replace(/\/$/, '');

  const AI_API_KEY =
    config.apiKey ||
    process.env.AI_API_KEY ||
    process.env.TEXT_AI_API_KEY ||
    process.env.VISION_AI_API_KEY;

  const TEXT_MODEL = envModel('TEXT_MODEL', envModel('AI_TEXT_MODEL', DEFAULT_TEXT_MODEL));
  const VISION_MODEL = envModel('VISION_MODEL', envModel('AI_VISION_MODEL', DEFAULT_VISION_MODEL));
  const DEFAULT_MODEL = envModel('AI_MODEL', DEFAULT_TEXT_MODEL);

  // OrcaRouter secondary provider for fast vision
  const ORCA_API_KEY = config.orcaApiKey || process.env.ORCA_API_KEY || '';
  const ORCA_BASE_URL = (
    config.orcaBaseUrl ||
    process.env.ORCA_BASE_URL ||
    'https://api.orcarouter.ai/v1'
  ).replace(/\/$/, '');

  // Groq free tier provider (30 RPM, 14,400 RPD, ultra-fast 500+ tok/s)
  const GROQ_API_KEY = config.groqApiKey || process.env.GROQ_API_KEY || '';
  const GROQ_BASE_URL = (
    config.groqBaseUrl ||
    process.env.GROQ_BASE_URL ||
    'https://api.groq.com/openai/v1'
  ).replace(/\/$/, '');
  const GROQ_TEXT_MODEL = process.env.GROQ_MODEL || 'llama-3.3-70b-versatile';
  const GROQ_FAST_MODEL = process.env.GROQ_FAST_MODEL || 'llama-3.1-8b-instant';
  const GROQ_VISION_MODEL = process.env.GROQ_VISION_MODEL || 'llama-3.2-11b-vision-preview';

  // Google Gemini free provider (15 RPM, 1,500 RPD, 1M TPM, 0 cost)
  const GEMINI_API_KEY = config.geminiApiKey || process.env.GEMINI_API_KEY || process.env.GEMINI_EMBEDDING_API_KEY || '';
  const GEMINI_BASE_URL = (
    config.geminiBaseUrl ||
    process.env.GEMINI_BASE_URL ||
    'https://generativelanguage.googleapis.com/v1beta/openai'
  ).replace(/\/$/, '');
  const GEMINI_TEXT_MODEL = process.env.GEMINI_MODEL || 'gemini-2.0-flash';
  const GEMINI_FAST_MODEL = process.env.GEMINI_FAST_MODEL || 'gemini-2.0-flash';
  const GEMINI_VISION_MODEL = process.env.GEMINI_VISION_MODEL || 'gemini-2.5-flash';

  // OpenRouter free provider (20 RPM on :free models)
  const OPENROUTER_API_KEY = config.openRouterApiKey || process.env.OPENROUTER_API_KEY || '';
  const OPENROUTER_BASE_URL = (
    config.openRouterBaseUrl ||
    process.env.OPENROUTER_BASE_URL ||
    'https://openrouter.ai/api/v1'
  ).replace(/\/$/, '');
  const OPENROUTER_TEXT_MODEL = process.env.OPENROUTER_MODEL || 'meta-llama/llama-3.3-70b-instruct:free';
  const OPENROUTER_FAST_MODEL = process.env.OPENROUTER_FAST_MODEL || 'meta-llama/llama-3.3-70b-instruct:free';
  const OPENROUTER_VISION_MODEL = process.env.OPENROUTER_VISION_MODEL || 'google/gemini-2.0-flash-exp:free';

  // Cerebras free provider (30 RPM, 1M TPM)
  const CEREBRAS_API_KEY = config.cerebrasApiKey || process.env.CEREBRAS_API_KEY || '';
  const CEREBRAS_BASE_URL = (
    config.cerebrasBaseUrl ||
    process.env.CEREBRAS_BASE_URL ||
    'https://api.cerebras.ai/v1'
  ).replace(/\/$/, '');
  const CEREBRAS_TEXT_MODEL = process.env.CEREBRAS_MODEL || 'llama-3.3-70b';
  const CEREBRAS_FAST_MODEL = process.env.CEREBRAS_FAST_MODEL || 'llama3.1-8b';

  // OpenCode Zen provider for the text fast lane + vision-first
  const ZEN_API_KEY = config.zenApiKey || process.env.ZEN_API_KEY || process.env.OPENCODE_API_KEY || '';
  const ZEN_ENABLED = String(config.zenEnabled || process.env.ZEN_ENABLED || '').toLowerCase() === 'true';
  const ZEN_BASE_URL = (
    config.zenBaseUrl ||
    process.env.ZEN_BASE_URL ||
    'https://opencode.ai/zen/v1'
  ).replace(/\/$/, '');
  const ZEN_TEXT_MODEL = process.env.ZEN_TEXT_MODEL || 'nemotron-3.5-lightning-free';
  const ZEN_TEXT_MODELS = [...new Set([
    ZEN_TEXT_MODEL,
    'deepseek-v4-flash-free',
    'mimo-v2.5-free',
    'nemotron-3-ultra-free',
    'ling-3.0-flash-fin-free',
  ])];
  const ZEN_VISION_MODEL = process.env.ZEN_VISION_MODEL || 'deepseek-v4-flash-vision-exp';

  // Round-robin load balancing configuration
  const ROUND_ROBIN_ENABLED = String(config.roundRobinEnabled ?? process.env.ROUND_ROBIN_ENABLED ?? 'true').toLowerCase() !== 'false';

  const providerCooldowns = new Map();

  function markProviderCooldown(providerName, durationMs = 60000) {
    providerCooldowns.set(providerName, Date.now() + durationMs);
  }

  function isProviderInCooldown(providerName) {
    const until = providerCooldowns.get(providerName);
    if (!until) return false;
    if (Date.now() >= until) {
      providerCooldowns.delete(providerName);
      return false;
    }
    return true;
  }

  let roundRobinIndex = 0;

  function getAvailableProviders({ isVision = false, isFastText = false } = {}) {
    const list = [];
    if (hasAiKey) {
      list.push({
        name: 'hcnsec',
        baseUrl: AI_BASE_URL,
        apiKey: AI_API_KEY,
        model: isVision ? (process.env.VISION_MODEL || 'step-3.7-flash') : (isFastText ? FAST_TEXT_MODEL : (process.env.TEXT_MODEL || 'glm-4.5-air')),
        supportsVision: true,
      });
    }
    if (GROQ_API_KEY) {
      list.push({
        name: 'groq',
        baseUrl: GROQ_BASE_URL,
        apiKey: GROQ_API_KEY,
        model: isVision ? GROQ_VISION_MODEL : (isFastText ? GROQ_FAST_MODEL : GROQ_TEXT_MODEL),
        supportsVision: true,
      });
    }
    if (GEMINI_API_KEY) {
      list.push({
        name: 'gemini',
        baseUrl: GEMINI_BASE_URL,
        apiKey: GEMINI_API_KEY,
        model: isVision ? GEMINI_VISION_MODEL : (isFastText ? GEMINI_FAST_MODEL : GEMINI_TEXT_MODEL),
        supportsVision: true,
      });
    }
    if (OPENROUTER_API_KEY) {
      list.push({
        name: 'openrouter',
        baseUrl: OPENROUTER_BASE_URL,
        apiKey: OPENROUTER_API_KEY,
        model: isVision ? OPENROUTER_VISION_MODEL : (isFastText ? OPENROUTER_FAST_MODEL : OPENROUTER_TEXT_MODEL),
        supportsVision: isVision,
      });
    }
    if (CEREBRAS_API_KEY && !isVision) {
      list.push({
        name: 'cerebras',
        baseUrl: CEREBRAS_BASE_URL,
        apiKey: CEREBRAS_API_KEY,
        model: isFastText ? CEREBRAS_FAST_MODEL : CEREBRAS_TEXT_MODEL,
        supportsVision: false,
      });
    }
    if (ORCA_API_KEY && isVision) {
      list.push({
        name: 'orca',
        baseUrl: ORCA_BASE_URL,
        apiKey: ORCA_API_KEY,
        model: ORCA_VISION_MODEL,
        supportsVision: true,
      });
    }
    return list;
  }

  function getRoundRobinOrder({ isVision = false, isFastText = false } = {}) {
    const list = getAvailableProviders({ isVision, isFastText });
    if (list.length <= 1 || !ROUND_ROBIN_ENABLED) return list;

    const ready = list.filter((p) => !isProviderInCooldown(p.name));
    const cooling = list.filter((p) => isProviderInCooldown(p.name));

    const pool = ready.length > 0 ? ready : list;
    const start = (roundRobinIndex++) % pool.length;
    const rotated = [...pool.slice(start), ...pool.slice(0, start)];
    return [...rotated, ...(ready.length > 0 ? cooling : [])];
  }

  const hasAiKey = Boolean(AI_API_KEY);

  function resolveModel(requestedModel, defaultModel = DEFAULT_MODEL) {
    const normalized = normalizeModelSlug(requestedModel);
    if (normalized && ALLOWED_MODELS.includes(normalized)) return normalized;
    if (requestedModel) console.warn(`Ignoring invalid model override: ${requestedModel}`);
    return defaultModel;
  }

  function resolveVisionModel(requestedModel) {
    const normalized = normalizeModelSlug(requestedModel);
    if (normalized && VISION_CAPABLE_MODELS.includes(normalized)) return normalized;
    if (normalized && normalized !== TEXT_MODEL && normalized !== 'auto') {
      console.warn(`Ignoring non-vision model override for vision request: ${requestedModel}`);
    }
    return VISION_MODEL;
  }

  function resolveTextModel(requestedModel) {
    return resolveModel(requestedModel, TEXT_MODEL);
  }

  function resolveChatModel(requestedModel, hasVisionAttachment) {
    if (hasVisionAttachment) {
      const normalized = normalizeModelSlug(requestedModel);
      // All vision requests route to nemotron-3.5-lightning-free (most reliable for images).
      // Legacy `step-3.7-flash` / `agnes-2.5-flash` clients also land here via normalizeModelSlug.
      if (normalized && VISION_CAPABLE_MODELS.includes(normalized)) return normalized;
      return 'nemotron-3.5-lightning-free';
    }
    return resolveTextModel(normalizeModelSlug(requestedModel));
  }

  function providerHeaders(apiKey) {
    return {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    };
  }

  function isRetryableModelError(message) {
    // 402/401/403 included: dead/quota-less keys must fall back, not fail.
    // 5\d\d covers 500, 502, 503, 504, 520, 522, 524 Cloudflare and gateway errors.
    return /5\d\d|429|410|404|402|401|403|400|NO_UPSTREAM|empty response|timeout|rate limit|payment_required|quota/i.test(String(message || ''));
  }

  function modelFallbackChain(primaryModel, { isVision = false } = {}) {
    const normalizedPrimary = normalizeModelSlug(primaryModel);
    const chain = [];
    if (isVision) {
      // Gemini goes FIRST when configured — free AI Studio tier, own key.
      if (GEMINI_API_KEY) {
        chain.push(GEMINI_VISION_MODEL);
      }
      // OrcaRouter next — fast free tier (5-12s), rate-limited at 10 RPM
      if (ORCA_API_KEY) {
        chain.push(ORCA_VISION_MODEL);
      }
      // Then primary (maps to MiniMax-M3 over wire via hcnsec — slow but unlimited)
      if (!chain.includes(normalizedPrimary) && normalizedPrimary !== 'auto') {
        chain.push(normalizedPrimary);
      }
      // Other vision-capable models as further fallback
      for (const candidate of VISION_CAPABLE_MODELS) {
        if (candidate === normalizedPrimary || candidate === 'auto' || candidate === ORCA_VISION_MODEL) continue;
        if (!chain.includes(candidate)) chain.push(candidate);
      }
    } else {
      chain.push(normalizedPrimary);
    }
    if (primaryModel !== TEXT_MODEL && !chain.includes(TEXT_MODEL)) chain.push(TEXT_MODEL);
    if (primaryModel !== DEFAULT_MODEL && !chain.includes(DEFAULT_MODEL)) chain.push(DEFAULT_MODEL);
    return chain;
  }

  // Reasoning split helpers (from server.js)
  const REASONING_OPENER = /^(?:Got it|Okay|OK|Alright|Sure|Right|So,?\s|Let me|I'll|I need to|First,?|Wait,?|Hmm|Well,?\s)/i;
  const REASONING_PHRASES = /\b(?:let's tackle|I need to make|the user (?:is|wants|asked)|make (?:it|sure)|I should|I'll (?:start|need|make|write|draft)|thinking about|planning to|appropriate for a student|word essay|this essay|this response|my approach)\b/i;
  const ANSWER_TRANSITION = /\n(?:---+|\*\*\*+)\s*\n|\n(?=#{1,3}\s+\S)|\n\n(?=(?:Here(?:'s| is)|Below (?:is|are)|The following|I've written|My (?:essay|answer|response)|(?:Essay|Answer|Response):))/i;

  function reasoningScore(text) {
    const sample = text.slice(0, 600);
    let score = 0;
    if (REASONING_OPENER.test(sample)) score += 2;
    if (REASONING_PHRASES.test(sample)) score += 2;
    if (/\bWait,\s/.test(sample)) score += 1;
    if (sample.includes('...') && sample.length < 900) score += 1;
    return score;
  }

  function looksLikeFinalAnswer(text) {
    const trimmed = text.trim();
    if (/^#{1,3}\s+\S/.test(trimmed)) return true;
    if (text.length > 900 && !REASONING_OPENER.test(text.slice(0, 120))) return true;
    return /\n#{1,3}\s+\S/.test(text);
  }

  function splitUntaggedReasoningPreamble(text) {
    const trimmed = (text || '').trim();
    if (!trimmed) return { reply: '', reasoning: null };
    const score = reasoningScore(trimmed);
    if (score < 3) return { reply: trimmed, reasoning: null };
    const transition = ANSWER_TRANSITION.exec(trimmed);
    if (transition && transition.index >= 40) {
      const preamble = trimmed.slice(0, transition.index).trim();
      const answer = trimmed.slice(transition.index).trim();
      if (preamble && answer && reasoningScore(preamble) >= 2) {
        return { reply: answer, reasoning: preamble };
      }
    }
    if (score >= 4 && !looksLikeFinalAnswer(trimmed)) {
      return { reply: '', reasoning: trimmed };
    }
    return { reply: trimmed, reasoning: null };
  }

  function splitEmbeddedReasoning(text) {
    if (!text || typeof text !== 'string') return { reply: '', reasoning: null };
    const reasoningParts = [];
    let reply = text;
    const tagPatterns = [
      { regex: /<(?:think|redacted_reasoning)>([\s\S]*?)<\/(?:think|redacted_reasoning)>/gi, strip: /<(?:think|redacted_reasoning)>[\s\S]*?<\/(?:think|redacted_reasoning)>/gi },
      { regex: /([\s\S]*?)<\/think>/gi, strip: /[\s\S]*?<\/think>/gi },
      { regex: /<reasoning>([\s\S]*?)<\/reasoning>/gi, strip: /<reasoning>[\s\S]*?<\/reasoning>/gi },
      { regex: /<thought>([\s\S]*?)<\/thought>/gi, strip: /<thought>[\s\S]*?<\/thought>/gi },
    ];
    for (const { regex, strip } of tagPatterns) {
      const matches = [...reply.matchAll(regex)];
      if (matches.length > 0) {
        reasoningParts.push(...matches.map((m) => m[1].trim()).filter(Boolean));
        reply = reply.replace(strip, '');
      }
    }
    const fenceRegex = /```(?:thinking|reasoning|thought)\s*([\s\S]*?)```/gi;
    const fenceMatches = [...reply.matchAll(fenceRegex)];
    if (fenceMatches.length > 0) {
      reasoningParts.push(...fenceMatches.map((m) => m[1].trim()).filter(Boolean));
      reply = reply.replace(fenceRegex, '');
    }
    reply = reply.replace(/\n{3,}/g, '\n\n').trim();
    const untagged = splitUntaggedReasoningPreamble(reply);
    if (untagged.reasoning) reasoningParts.push(untagged.reasoning);
    reply = untagged.reply;
    const reasoning = reasoningParts.join('\n\n').trim() || null;
    return { reply, reasoning };
  }

  function extractProviderReasoning(message) {
    if (!message || typeof message !== 'object') return null;
    for (const key of ['reasoning_content', 'reasoning', 'thinking']) {
      if (typeof message[key] === 'string' && message[key].trim()) return message[key].trim();
    }
    return null;
  }

  function parseChatCompletionResult(data) {
    if (data && data.error) {
      const msg = typeof data.error === 'object' ? (data.error.message || JSON.stringify(data.error)) : data.error;
      throw new Error(`Upstream AI error: ${msg}`);
    }
    const choice = data?.choices?.[0];
    const message = choice?.message;
    if (!message) throw new Error(data?.error?.message ? `AI API error: ${data.error.message}` : 'AI API returned empty response');
    const finishReason = choice?.finish_reason || null;
    let rawContent = typeof message.content === 'string' ? message.content : '';
    if (rawContent.includes('\\n')) {
      rawContent = rawContent.replace(/\\n/g, '\n').replace(/\\r/g, '\r');
    }
    const providerReasoning = extractProviderReasoning(message);
    const embedded = splitEmbeddedReasoning(rawContent);
    const reasoningParts = [providerReasoning, embedded.reasoning].filter(Boolean);
    let reasoning = reasoningParts.join('\n\n').trim() || null;
    let reply = embedded.reply.trim();
    // Heuristic laundering: when splitEmbeddedReasoning files the WHOLE content
    // as reasoning (thinking models write CoT into content and get truncated
    // before the answer), surface that text as the reply — but flag it
    // (replyHeuristic) so callers know the "answer" is really deliberation and
    // clients render it verbatim instead of re-splitting it away.
    // Provider-side reasoning_content is NEVER laundered: the real reasoning
    // channel is not an answer — leave reply empty so chatCompletion's
    // no-thinking retry can produce one (and fail empty-response otherwise).
    let replyHeuristic = false;
    if (!reply && embedded.reasoning && !providerReasoning) {
      reply = embedded.reasoning;
      reasoning = null;
      replyHeuristic = true;
    }
    const toolCalls = Array.isArray(message.tool_calls) ? message.tool_calls : [];
    if (!reply && !reasoning && toolCalls.length === 0) throw new Error('AI API returned empty response');
    return {
      reply: reply || (reasoning ? '' : ''),
      reasoning,
      replyHeuristic,
      finishReason,
      model: data.model || null,
      toolCalls,
    };
  }

  async function chatCompletionOnce(messages, { temperature = 0.7, maxTokens = 2048, model, signal, responseFormat, reasoning, tools, toolChoice, baseUrl, apiKey } = {}) {
    const payload = { model, messages, temperature, max_tokens: maxTokens };
    // Structured-output hint; providers that don't support it are handled by the caller's fallback.
    if (responseFormat) payload.response_format = responseFormat;
    if (tools && Array.isArray(tools) && tools.length > 0) {
      payload.tools = tools;
      if (toolChoice) payload.tool_choice = toolChoice;
    }
    // reasoning parameter (e.g. for nemotron-3.5-lightning-free or OpenRouter thinking)
    // Pass object ({ effort: 'medium' }) or string reasoning_effort ('low'/'medium'/'high')
    if (reasoning && typeof reasoning !== 'string') {
      payload.reasoning = reasoning;
    } else if (typeof reasoning === 'string') {
      payload.reasoning_effort = reasoning;
    }
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

  async function chatCompletion(messages, { temperature = 0.7, maxTokens = 2048, model, isVision = false, isFastText = false, thinking: thinkingOpt, signal, responseFormat, reasoning, tools, toolChoice, wireModelOverride } = {}) {
    const hasAnyKey = hasAiKey || Boolean(ZEN_API_KEY) || Boolean(ORCA_API_KEY) || Boolean(GROQ_API_KEY) || Boolean(GEMINI_API_KEY) || Boolean(OPENROUTER_API_KEY) || Boolean(CEREBRAS_API_KEY);
    if (!hasAnyKey) throw new Error('AI provider not configured (set AI_API_KEY, GROQ_API_KEY, GEMINI_API_KEY, OPENROUTER_API_KEY or ORCA_API_KEY)');
    // Thinking requests (explicit flag from effort, else the REASONING
    // model / Zen slug) go to hcnsec `auto` — never Cerebras.
    const thinkingRequested = typeof thinkingOpt === 'boolean'
      ? thinkingOpt
      : (!isVision && normalizeModelSlug(model) === 'nemotron-3.5-lightning-free');

    function buildWireCandidates(thinking) {
      if (wireModelOverride) {
        return [{ model: toWireModelSlug(wireModelOverride, { isVision }), provider: 'hcnsec' }];
      }
      const chain = [];
      const primary = thinking ? 'auto' : (model || (isVision ? VISION_MODEL : TEXT_MODEL));

      // Thinking always routes to hcnsec textFallback (glm-4.5-air / step-3.7-flash)
      if (thinking) {
        const wire = toWireModelSlug('auto', { isVision: false });
        return [{ model: wire, provider: 'hcnsec' }];
      }

      // Vision requests follow vision hierarchy: Zen vision -> Gemini -> Orca -> Groq -> hcnsec
      if (isVision) {
        if (ZEN_API_KEY && ZEN_ENABLED) {
          chain.push({ model: ZEN_VISION_MODEL, provider: 'zen' });
        }
        for (const candidate of modelFallbackChain(primary, { isVision: true })) {
          let provider = 'hcnsec';
          let wire = candidate;
          if (candidate === GEMINI_VISION_MODEL && GEMINI_API_KEY) {
            provider = 'gemini';
            wire = GEMINI_VISION_MODEL;
          } else if (candidate === ORCA_VISION_MODEL && ORCA_API_KEY) {
            provider = 'orca';
            wire = ORCA_VISION_MODEL;
          } else {
            wire = toWireModelSlug(candidate, { isVision: true });
          }
          if (wire && !chain.find((c) => c.provider === provider && c.model === wire)) {
            chain.push({ model: wire, provider });
          }
        }
        if (GROQ_API_KEY && !chain.find((c) => c.provider === 'groq')) {
          chain.push({ model: GROQ_VISION_MODEL, provider: 'groq' });
        }
        return chain.length ? chain : [{ model: 'step-3.7-flash', provider: 'hcnsec' }];
      }

      // Zen (OpenCode) fast lane goes FIRST for non-thinking text-only chat when enabled
      if (ZEN_API_KEY && ZEN_ENABLED) {
        for (const zenModel of ZEN_TEXT_MODELS) {
          chain.push({ model: zenModel, provider: 'zen' });
        }
      }

      // Non-thinking text chat: round robin across active configured text providers!
      const normalizedReq = model ? normalizeModelSlug(model) : null;
      const isExplicitCustomModel = normalizedReq && normalizedReq !== 'auto' && normalizedReq !== DEFAULT_TEXT_MODEL;

      const roundRobinPool = getRoundRobinOrder({ isVision: false, isFastText });
      for (const p of roundRobinPool) {
        let wire = p.model;
        if (isExplicitCustomModel && p.name === 'hcnsec') {
          wire = toWireModelSlug(normalizedReq, { isVision: false, provider: p.name });
        }
        if (wire && !chain.find((c) => c.provider === p.name && c.model === wire)) {
          chain.push({ model: wire, provider: p.name });
        }
      }

      for (const candidate of modelFallbackChain(primary, { isVision: false })) {
        const wire = toWireModelSlug(candidate, { isVision: false });
        if (wire && !chain.find((c) => c.provider === 'hcnsec' && c.model === wire)) {
          chain.push({ model: wire, provider: 'hcnsec' });
        }
      }

      return chain.length ? chain : [{ model: 'step-3.7-flash', provider: 'hcnsec' }];
    }

    async function runChain(candidates) {
      let lastError;
      for (let i = 0; i < candidates.length; i += 1) {
        const { model: candidate, provider } = candidates[i];
        const baseUrl = provider === 'orca' ? ORCA_BASE_URL :
                        provider === 'cerebras' ? CEREBRAS_BASE_URL :
                        provider === 'zen' ? ZEN_BASE_URL :
                        provider === 'gemini' ? GEMINI_BASE_URL :
                        provider === 'groq' ? GROQ_BASE_URL :
                        provider === 'openrouter' ? OPENROUTER_BASE_URL :
                        AI_BASE_URL;

        const apiKey = provider === 'orca' ? ORCA_API_KEY :
                       provider === 'cerebras' ? CEREBRAS_API_KEY :
                       provider === 'zen' ? ZEN_API_KEY :
                       provider === 'gemini' ? GEMINI_API_KEY :
                       provider === 'groq' ? GROQ_API_KEY :
                       provider === 'openrouter' ? OPENROUTER_API_KEY :
                       AI_API_KEY;

        try {
          if (i > 0) console.warn(`[ai] Seamless failover: retrying with model=${candidate} (provider=${provider})`);
          const result = await chatCompletionOnce(messages, {
            temperature,
            maxTokens,
            model: candidate,
            signal,
            responseFormat,
            reasoning,
            tools,
            toolChoice,
            baseUrl,
            apiKey,
          });
          return { ...result, model: result.model || candidate, provider };
        } catch (err) {
          if (err.message && err.message.includes('429')) {
            console.warn(`[ai] Provider ${provider} rate limited (429); marking cooldown and trying next fallback`);
            markProviderCooldown(provider, 60000);
          } else if (/5\d\d|timeout/i.test(String(err.message || ''))) {
            console.warn(`[ai] Provider ${provider} error (${String(err.message).slice(0, 80)}); marking cooldown`);
            markProviderCooldown(provider, 30000);
          }
          // Some providers reject response_format outright — drop it and retry the same model once.
          if (responseFormat && /response_format|unsupported|invalid.*format/i.test(String(err.message || ''))) {
            console.warn(`[ai] response_format rejected by ${provider}; retrying without it`);
            try {
              const retry = await chatCompletionOnce(messages, {
                temperature,
                maxTokens,
                model: candidate,
                signal,
                baseUrl,
                apiKey,
              });
              return { ...retry, model: retry.model || candidate, provider };
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

    const first = await runChain(buildWireCandidates(thinkingRequested));
    // Thinking-mode starvation: the `auto` router wrote its chain-of-thought
    // into content and got truncated (finish_reason=length) before the final
    // answer — replyHeuristic (laundered CoT) or empty (provider reasoning).
    // Retry the chain ONCE with thinking=false so the Zen fast lane can
    // produce a real answer; a heuristic reply is better than none, but an
    // empty reply after the retry is a hard failure.
    const starved = thinkingRequested && !isVision && first.finishReason === 'length' && (!first.reply || first.replyHeuristic);
    if (starved) {
      let retry = null;
      try {
        retry = await runChain(buildWireCandidates(false));
      } catch (err) {
        console.warn(`[ai] thinking-mode no-thinking retry failed: ${String(err.message || err).slice(0, 160)}`);
      }
      if (retry && retry.reply && !retry.replyHeuristic) return retry;
      if (!first.reply) throw new Error('AI API returned empty response');
      return first;
    }
    return first;
  }

  async function chatCompletionText(messages, options = {}) {
    const result = await chatCompletion(messages, options);
    return result.reply;
  }

  function extractJson(text) {
    if (typeof text !== 'string') throw new Error('extractJson received non-string input');
    let raw = text.trim();
    // Unwrap all fences, keep inner content (global)
    raw = raw.replace(/```(?:json)?\s*([\s\S]*?)```/gi, (_, inner) => inner.trim()).trim();

    // If upstream returned escaped quotes, unescape them first
    if (raw.includes('\\"')) {
      try {
        const cleaned = raw.replace(/\\"/g, '"');
        const p = JSON.parse(cleaned);
        if (Array.isArray(p)) return { cards: p, questions: p, items: p, rawArray: p };
        return p;
      } catch (_) {}
    }

    // Fast path: try direct parse
    try {
      const direct = JSON.parse(raw);
      if (Array.isArray(direct)) {
        return { cards: direct, questions: direct, items: direct, rawArray: direct };
      }
      return direct;
    } catch (_) {}

    // Check if output is a JSON array wrapped in prose
    const firstBracket = raw.indexOf('[');
    const firstBrace = raw.indexOf('{');
    if (firstBracket >= 0 && (firstBrace < 0 || firstBracket < firstBrace)) {
      const lastBracket = raw.lastIndexOf(']');
      if (lastBracket > firstBracket) {
        try {
          const arr = JSON.parse(raw.slice(firstBracket, lastBracket + 1));
          if (Array.isArray(arr)) {
            return { cards: arr, questions: arr, items: arr, rawArray: arr };
          }
        } catch (_) {}
      }
    }

    // Strip everything before the first '{' to handle leading prose
    if (firstBrace > 0) {
      raw = raw.slice(firstBrace);
    }
    raw = raw.trim();
    try {
      return JSON.parse(raw);
    } catch (_) {
      // Balanced-brace scan for candidates containing "classes" or "cards" etc.
      const candidates = [];
      let depth = 0;
      let inStr = false;
      let esc = false;
      let start = -1;
      for (let i = 0; i < raw.length; i++) {
        const ch = raw[i];
        if (esc) {
          esc = false;
          continue;
        }
        if (ch === '\\') {
          esc = true;
          continue;
        }
        if (ch === '"') inStr = !inStr;
        if (!inStr) {
          if (ch === '{') {
            if (depth === 0) start = i;
            depth++;
          } else if (ch === '}') {
            depth--;
            if (depth === 0 && start >= 0) {
              candidates.push(raw.slice(start, i + 1));
              start = -1;
            }
          }
        }
      }
      // Sort candidates by length (prefer larger objects)
      for (const cand of candidates.sort((a, b) => b.length - a.length)) {
        try {
          const p = JSON.parse(cand);
          // Prefer objects with expected keys for our endpoints
          if (p && (Array.isArray(p.classes) || Array.isArray(p.cards) || Array.isArray(p.questions) || p.items)) return p;
        } catch (__) {}
      }
      // Last resort: find first { to last } and try parsing
      const s = raw.indexOf('{');
      const e = raw.lastIndexOf('}');
      if (s >= 0 && e > s) {
        try {
          return JSON.parse(raw.slice(s, e + 1));
        } catch (__) {}
      }
      throw new Error('AI returned no parsable JSON');
    }
  }

  function requestHasVisionContent(body = {}) {
    if (body.imageBase64) return true;
    const messages = body.messages;
    if (!Array.isArray(messages)) return false;
    return messages.some((msg) => messageContentHasVision(msg?.content));
  }

  function messageContentHasVision(content) {
    if (!content) return false;
    if (typeof content === 'string') return /data:image\/[^;]+;base64,/i.test(content);
    if (!Array.isArray(content)) return false;
    return content.some((part) => {
      if (!part || typeof part !== 'object') return false;
      if (part.type === 'image_url' && part.image_url) return true;
      if (part.type === 'image' && part.image) return true;
      if (typeof part.text === 'string' && /data:image\/[^;]+;base64,/i.test(part.text)) return true;
      return false;
    });
  }

  return {
    hasAiKey,
    hasGroqKey: Boolean(GROQ_API_KEY),
    hasGeminiKey: Boolean(GEMINI_API_KEY),
    hasOpenRouterKey: Boolean(OPENROUTER_API_KEY),
    hasCerebrasKey: Boolean(CEREBRAS_API_KEY),
    hasZenKey: Boolean(ZEN_API_KEY),
    zenActive: ZEN_ENABLED && Boolean(ZEN_API_KEY),
    AI_BASE_URL,
    GROQ_BASE_URL,
    GEMINI_BASE_URL,
    OPENROUTER_BASE_URL,
    CEREBRAS_BASE_URL,
    GROQ_TEXT_MODEL,
    GROQ_FAST_MODEL,
    GROQ_VISION_MODEL,
    GEMINI_TEXT_MODEL,
    GEMINI_FAST_MODEL,
    GEMINI_VISION_MODEL,
    OPENROUTER_TEXT_MODEL,
    OPENROUTER_FAST_MODEL,
    OPENROUTER_VISION_MODEL,
    CEREBRAS_TEXT_MODEL,
    CEREBRAS_FAST_MODEL,
    ZEN_BASE_URL,
    ZEN_TEXT_MODEL,
    ZEN_TEXT_MODELS,
    ZEN_VISION_MODEL,
    DEFAULT_MODEL,
    TEXT_MODEL,
    VISION_MODEL,
    FAST_TEXT_MODEL,
    fastTextModel: FAST_TEXT_MODEL,
    ALLOWED_MODELS,
    resolveChatModel,
    resolveVisionModel,
    resolveTextModel,
    modelFallbackChain,
    chatCompletion,
    chatCompletionText,
    extractJson,
    requestHasVisionContent,
    getAvailableProviders,
    getRoundRobinOrder,
    markProviderCooldown,
    isProviderInCooldown,
  };
}

module.exports = { createAiProvider, ALLOWED_MODELS, VISION_CAPABLE_MODELS, FAST_TEXT_MODEL, normalizeModelSlug, toWireModelSlug };
