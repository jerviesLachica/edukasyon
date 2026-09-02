/**
 * Central AI safety gateway — ALL AI routes must pass through this pipeline.
 *
 * Pipeline:
 * 1. Authenticate
 * 2. Validate request schema + size limits
 * 3. Rate limit (per user/device/IP/endpoint)
 * 4. Quota check
 * 5. Input moderation + prompt injection detection
 * 6. Risk assessment
 * 7. Execute handler (builds messages, calls provider)
 * 8. Output moderation + feature-specific validation
 * 9. Log abuse events (privacy-preserving)
 * 10. Return safe errors
 */

const { sanitizeErrorForClient } = require('../util/safeErrors');

class AiSafetyGateway {
  constructor(deps) {
    this.auth = deps.auth;
    this.policy = deps.policy;
    this.rateLimiter = deps.rateLimiter;
    this.quotaService = deps.quotaService;
    this.inputModerator = deps.inputModerator;
    this.outputModerator = deps.outputModerator;
    this.promptInjection = deps.promptInjection;
    this.dataClassifier = deps.dataClassifier;
    this.abuseRisk = deps.abuseRisk;
    this.abuseEvents = deps.abuseEvents;
    this.usageTracker = deps.usageTracker;
    this.provider = deps.provider;
    this.webSearch = deps.webSearch || null;
    this.mockHandler = deps.mockHandler || null;
  }

  /**
   * Express middleware-style handler.
   * @param {import('express').Request} req
   * @param {import('express').Response} res
   * @param {GatewayRouteConfig} config
   */
  async handle(req, res, config) {
    const endpoint = config.endpoint;
    const endpointPolicy = this.policy.endpoints[endpoint] || {};

    try {
      // 1. Authenticate
      const authResult = await this.auth.authenticate(req);
      if (!authResult.ok) {
        return this.sendError(res, authResult.status, authResult.code, authResult.message);
      }
      const identity = authResult.identity;

      // 2. Validate schema + size
      const validation = this.validateRequest(req.body, config);
      if (!validation.ok) {
        this.logEvent('validation_failed', identity, endpoint, { reason: validation.code });
        return this.sendError(res, 400, validation.code, validation.message);
      }

      const inputText = config.extractInputText(req.body);
      const inputChars = inputText.length;

      // 3. Rate limit
      if (endpointPolicy.rateLimitPerMin != null) {
        const rateResult = this.rateLimiter.checkEndpoint(identity, endpoint, endpointPolicy);
        if (!rateResult.allowed) {
          this.logEvent('rate_limited', identity, endpoint, { scope: rateResult.scope });
          return this.sendError(
            res,
            429,
            'RATE_LIMIT_EXCEEDED',
            'Too many requests. Please wait before trying again.',
            { retryAfterMs: rateResult.retryAfterMs }
          );
        }
      }

      // 3b. Step model chat quota (only when client explicitly requests step-3.7-flash)
      if (endpoint === 'chat' && req.body?.model === 'step-3.7-flash') {
        const stepPolicy = this.policy.stepModelChat || { limit: 25, windowMs: 600_000 };
        const stepResult = this.rateLimiter.checkModel(identity, 'step-3.7-flash', stepPolicy);
        if (!stepResult.allowed) {
          this.logEvent('rate_limited', identity, endpoint, { scope: 'step_model', model: 'step-3.7-flash' });
          return this.sendError(
            res,
            429,
            'RATE_LIMIT_EXCEEDED',
            'Step 3.7 Flash limit reached (25 requests every 10 minutes). Switched to Auto is recommended.',
            { retryAfterMs: stepResult.retryAfterMs, model: 'step-3.7-flash' }
          );
        }
      }

      // 4. Quota
      const quotaResult = this.quotaService.checkAndConsume(identity, endpoint, endpointPolicy, this.policy);
      if (!quotaResult.allowed) {
        this.logEvent('quota_exceeded', identity, endpoint, { scope: quotaResult.scope });
        return this.sendError(
          res,
          402,
          'QUOTA_EXCEEDED',
          'Daily or hourly AI quota reached. Try again later.',
          { retryAfterMs: quotaResult.retryAfterMs }
        );
      }

      // 5. Input moderation + injection
      if (this.policy.moderationEnabled && inputText) {
        const moderation = this.inputModerator.moderate(inputText, { endpoint });
        if (!moderation.allowed) {
          this.logEvent('input_blocked', identity, endpoint, {
            score: moderation.score,
            categories: moderation.categories,
          });
          return this.sendError(res, 403, 'CONTENT_BLOCKED', moderation.reason);
        }
      }

      if (this.policy.promptInjectionEnabled && inputText) {
        const injection = this.promptInjection.analyze(inputText);
        if (injection.detected) {
          this.logEvent('injection_detected', identity, endpoint, {
            score: injection.score,
            signals: injection.signals.slice(0, 5),
          });
          return this.sendError(
            res,
            403,
            'PROMPT_INJECTION_BLOCKED',
            'Your message could not be processed. Please ask a straightforward study question.'
          );
        }
      }

      // 6. Risk assessment
      const risk = this.abuseRisk.assess(identity, endpoint);
      if (this.abuseRisk.shouldBlockHighRisk(risk)) {
        this.logEvent('high_risk_blocked', identity, endpoint, { score: risk.score, factors: risk.factors });
        return this.sendError(
          res,
          403,
          'ABUSE_DETECTED',
          'AI access temporarily restricted due to unusual activity. Please try again later.'
        );
      }

      // Mock mode when no API key
      if (!this.provider.hasAiKey) {
        if (this.mockHandler) {
          const mockResult = this.mockHandler(req.body, config);
          return res.json(mockResult);
        }
        return this.sendError(res, 503, 'AI_NOT_CONFIGURED', 'AI service is not configured.');
      }

      // 7. Execute with timeout
      const maxTokens = Math.min(
        endpointPolicy.maxOutputTokens ?? this.policy.maxOutputTokens,
        this.policy.maxOutputTokens
      );

      const controller = new AbortController();
      const timeoutMs = endpointPolicy.requestTimeoutMs ?? this.policy.requestTimeoutMs;
      const timeout = setTimeout(() => controller.abort(), timeoutMs);

      let result;
      try {
        result = await config.handler({
          body: req.body,
          identity,
          provider: this.provider,
          webSearch: this.webSearch,
          maxTokens,
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timeout);
      }

      // 8. Output moderation
      result = await this.moderateAndValidateOutput(result, config);

      // 9. Usage tracking
      const outputChars = JSON.stringify(result).length;
      this.usageTracker.record({
        endpoint,
        identity,
        inputChars,
        outputChars,
        model: result.model || null,
      });

      return res.json(result);
    } catch (err) {
      if (err.name === 'AbortError') {
        this.logEvent('timeout', { logSubject: 'unknown' }, endpoint, {});
        return this.sendError(res, 504, 'REQUEST_TIMEOUT', 'AI request timed out. Please try again.');
      }

      console.error(`[gateway:${endpoint}]`, err.message || err);
      const safe = sanitizeErrorForClient(err);
      return this.sendError(res, safe.status, safe.code, safe.message);
    }
  }

  validateRequest(body, config) {
    if (!body || typeof body !== 'object') {
      return { ok: false, code: 'INVALID_BODY', message: 'Request body must be a JSON object.' };
    }

    if (config.validate) {
      const custom = config.validate(body, this.policy);
      if (!custom.ok) return custom;
    }

    const text = config.extractInputText(body);
    if (text.length > this.policy.maxInputChars) {
      return {
        ok: false,
        code: 'INPUT_TOO_LONG',
        message: `Input exceeds maximum length of ${this.policy.maxInputChars} characters.`,
      };
    }

    if (body.imageBase64) {
      const approxBytes = Math.ceil((body.imageBase64.length * 3) / 4);
      if (approxBytes > this.policy.maxImageBytes) {
        return {
          ok: false,
          code: 'IMAGE_TOO_LARGE',
          message: 'Attached image is too large.',
        };
      }
    }

    if (body.attachmentText && body.attachmentText.length > this.policy.maxDocumentChars) {
      return {
        ok: false,
        code: 'DOCUMENT_TOO_LARGE',
        message: 'Attached document text is too large.',
      };
    }

    return { ok: true };
  }

  async moderateAndValidateOutput(result, config) {
    if (!result || typeof result !== 'object') {
      throw new Error('Handler returned invalid result');
    }

    // Moderate text fields
    if (typeof result.reply === 'string') {
      const mod = this.outputModerator.moderate(result.reply);
      if (mod.blocked) throw new Error(mod.reason || 'Output blocked');
      result = { ...result, reply: mod.text };
    }
    if (typeof result.result === 'string') {
      const mod = this.outputModerator.moderate(result.result);
      if (mod.blocked) throw new Error(mod.reason || 'Output blocked');
      result = { ...result, result: mod.text };
    }
    if (typeof result.reasoning === 'string') {
      const mod = this.outputModerator.moderate(result.reasoning);
      result = { ...result, reasoning: mod.blocked ? null : mod.text };
    }

    if (config.validateOutput) {
      const validated = config.validateOutput(result);
      if (!validated.valid) {
        throw new Error(validated.error || 'Invalid AI output');
      }
      return validated.data;
    }

    return result;
  }

  logEvent(type, identity, endpoint, meta = {}) {
    if (!this.policy.logAbuseEvents) return;
    this.abuseEvents.record({
      type,
      endpoint,
      logSubject: identity?.logSubject || 'unknown',
      authMethod: identity?.authMethod,
      ...meta,
    });
  }

  sendError(res, status, code, message, extra = {}) {
    return res.status(status).json({
      error: message,
      code,
      ...extra,
    });
  }
}

function createGateway(deps) {
  return new AiSafetyGateway(deps);
}

module.exports = { AiSafetyGateway, createGateway };
