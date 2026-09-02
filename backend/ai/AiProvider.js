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

const BASE_ALLOWED_MODELS = ['auto', 'step-3.7-flash'];
// Operators extend the catalog via env (e.g. NVIDIA NIM ids) without code changes.
const CONFIGURED_MODELS = envModelList('ALLOWED_MODELS');
const ALLOWED_MODELS = [...new Set([...BASE_ALLOWED_MODELS, ...CONFIGURED_MODELS])];
const VISION_CAPABLE_MODELS = [
  ...new Set([
    'step-3.7-flash',
    'auto',
    ...envModelList('VISION_CAPABLE_MODELS'),
    ...CONFIGURED_MODELS,
  ]),
];
const DEFAULT_TEXT_MODEL = 'auto';
const DEFAULT_VISION_MODEL = 'step-3.7-flash';

function envModel(name, fallback) {
  const value = process.env[name];
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

  const hasAiKey = Boolean(AI_API_KEY);

  function resolveModel(requestedModel, defaultModel = DEFAULT_MODEL) {
    if (requestedModel && ALLOWED_MODELS.includes(requestedModel)) return requestedModel;
    if (requestedModel) console.warn(`Ignoring invalid model override: ${requestedModel}`);
    return defaultModel;
  }

  function resolveVisionModel(requestedModel) {
    if (requestedModel && VISION_CAPABLE_MODELS.includes(requestedModel)) return requestedModel;
    if (requestedModel && requestedModel !== TEXT_MODEL && requestedModel !== 'auto') {
      console.warn(`Ignoring non-vision model override for vision request: ${requestedModel}`);
    }
    return VISION_MODEL;
  }

  function resolveTextModel(requestedModel) {
    return resolveModel(requestedModel, TEXT_MODEL);
  }

  function resolveChatModel(requestedModel, hasVisionAttachment) {
    if (hasVisionAttachment) {
      // Honor explicit user choice: auto stays auto for vision; step uses step (quota applies).
      if (requestedModel === 'step-3.7-flash') return 'step-3.7-flash';
      return 'auto';
    }
    return resolveTextModel(requestedModel);
  }

  function providerHeaders(apiKey) {
    return {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
    };
  }

  function isRetryableModelError(message) {
    return /503|502|429|410|404|400|NO_UPSTREAM|empty response|timeout|rate limit/i.test(String(message || ''));
  }

  function modelFallbackChain(primaryModel, { isVision = false } = {}) {
    const chain = [primaryModel];
    if (isVision) {
      for (const candidate of VISION_CAPABLE_MODELS) {
        if (candidate !== primaryModel && !chain.includes(candidate)) chain.push(candidate);
      }
      // hcnsec.cn proxy: auto may route to an available vision backend when step-3.7-flash is unavailable
      if (!chain.includes('auto')) chain.push('auto');
      return chain;
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
    const message = data.choices?.[0]?.message;
    if (!message) throw new Error('AI API returned empty response');
    const rawContent = typeof message.content === 'string' ? message.content : '';
    const providerReasoning = extractProviderReasoning(message);
    const embedded = splitEmbeddedReasoning(rawContent);
    const reasoningParts = [providerReasoning, embedded.reasoning].filter(Boolean);
    const reasoning = reasoningParts.join('\n\n').trim() || null;
    // Some models (e.g. step-3.7-flash) put the structured answer in `reasoning`
    // and leave `content` empty when response_format=json_object is requested.
    // Use reasoning as a fallback so downstream extractJson can still parse JSON.
    let reply = embedded.reply.trim();
    if (!reply && reasoning) {
      reply = reasoning;
    }
    if (!reply && !reasoning) throw new Error('AI API returned empty response');
    return {
      reply: reply || (reasoning ? '' : '(No response text)'),
      reasoning,
      model: data.model || null,
    };
  }

  async function chatCompletionOnce(messages, { temperature = 0.7, maxTokens = 2048, model, signal, responseFormat, reasoning } = {}) {
    const payload = { model, messages, temperature, max_tokens: maxTokens };
    // Structured-output hint; providers that don't support it are handled by the caller's fallback.
    if (responseFormat) payload.response_format = responseFormat;
    // step-3.7-flash and similar models support a `reasoning` parameter to control
    // thinking mode. Passing 'none' should disable reasoning output and return JSON
    // directly in `content` instead of `reasoning`.
    if (reasoning) payload.reasoning = reasoning;
    const res = await fetch(`${AI_BASE_URL}/chat/completions`, {
      method: 'POST',
      headers: providerHeaders(AI_API_KEY),
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

  async function chatCompletion(messages, { temperature = 0.7, maxTokens = 2048, model, isVision = false, signal, responseFormat, reasoning } = {}) {
    if (!hasAiKey) throw new Error('AI provider not configured (set AI_API_KEY)');
    const models = modelFallbackChain(model || (isVision ? VISION_MODEL : TEXT_MODEL), { isVision });
    let lastError;
    for (let i = 0; i < models.length; i += 1) {
      const candidate = models[i];
      try {
        if (i > 0) console.warn(`[ai] Retrying with fallback model=${candidate}`);
        const result = await chatCompletionOnce(messages, { temperature, maxTokens, model: candidate, signal, responseFormat, reasoning });
        return { ...result, model: result.model || candidate };
      } catch (err) {
        // Some providers reject response_format outright — drop it and retry the same model once.
        if (responseFormat && /response_format|unsupported|invalid.*format/i.test(String(err.message || ''))) {
          console.warn('[ai] response_format rejected; retrying without it');
          const retry = await chatCompletionOnce(messages, { temperature, maxTokens, model: candidate, signal });
          return { ...retry, model: retry.model || candidate };
        }
        lastError = err;
        const hasNext = i < models.length - 1;
        if (!hasNext || !isRetryableModelError(err.message)) throw err;
        console.warn(`[ai] Model ${candidate} failed: ${String(err.message || err).slice(0, 160)}`);
      }
    }
    throw lastError || new Error('AI API request failed');
  }

  async function chatCompletionText(messages, options = {}) {
    const result = await chatCompletion(messages, options);
    return result.reply;
  }

  function extractJson(text) {
    if (typeof text !== 'string') throw new Error('extractJson received non-string input');
    let raw = text.trim();
    // Unwrap all fences, keep inner content (global)
    raw = raw.replace(/```(?:json)?\s*([\s\S]*?)```/gi, (_, inner) => inner.trim());
    // Strip everything before the first '{' to handle leading prose
    const firstBrace = raw.indexOf('{');
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
    AI_BASE_URL,
    DEFAULT_MODEL,
    TEXT_MODEL,
    VISION_MODEL,
    ALLOWED_MODELS,
    resolveChatModel,
    resolveVisionModel,
    resolveTextModel,
    chatCompletion,
    chatCompletionText,
    extractJson,
    requestHasVisionContent,
  };
}

module.exports = { createAiProvider, ALLOWED_MODELS, VISION_CAPABLE_MODELS };
