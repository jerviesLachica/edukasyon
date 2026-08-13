/**
 * Centralized safety limits loaded from environment.
 * All AI routes read limits from here — never hardcode in handlers.
 */

const ENDPOINT_DEFAULTS = {
  chat: {
    rateLimitPerMin: 1,
    burstPerMin: 2,
    dailyQuota: 100,
    hourlyQuota: 30,
    maxOutputTokens: 2048,
  },
  'schedule-analysis': {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 20,
    hourlyQuota: 10,
    maxOutputTokens: 4096,
  },
  summarize: {
    rateLimitPerMin: 5,
    burstPerMin: 8,
    dailyQuota: 50,
    hourlyQuota: 20,
    maxOutputTokens: 1024,
  },
  flashcards: {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 30,
    hourlyQuota: 15,
    maxOutputTokens: 2048,
  },
  quiz: {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 30,
    hourlyQuota: 15,
    maxOutputTokens: 2048,
  },
  'study-plan': {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 20,
    hourlyQuota: 10,
    maxOutputTokens: 2048,
  },
  'assignment-breakdown': {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 25,
    hourlyQuota: 12,
    maxOutputTokens: 4096,
  },
  'focus-plan': {
    rateLimitPerMin: 3,
    burstPerMin: 5,
    dailyQuota: 25,
    hourlyQuota: 12,
    maxOutputTokens: 2048,
  },
};

function envInt(name, fallback) {
  const raw = process.env[name];
  if (raw == null || raw === '') return fallback;
  const parsed = parseInt(raw, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function envBool(name, fallback) {
  const raw = process.env[name];
  if (raw == null || raw === '') return fallback;
  return !['0', 'false', 'no', 'off'].includes(String(raw).toLowerCase());
}

function loadSafetyPolicy() {
  const globalDaily = envInt('SAFETY_DAILY_QUOTA', 200);
  const globalHourly = envInt('SAFETY_HOURLY_QUOTA', 60);

  const endpoints = {};
  for (const [name, defaults] of Object.entries(ENDPOINT_DEFAULTS)) {
    const prefix = `SAFETY_${name.toUpperCase().replace(/-/g, '_')}`;
    endpoints[name] = {
      rateLimitPerMin: envInt(`${prefix}_RATE_PER_MIN`, defaults.rateLimitPerMin),
      burstPerMin: envInt(`${prefix}_BURST_PER_MIN`, defaults.burstPerMin),
      dailyQuota: envInt(`${prefix}_DAILY_QUOTA`, defaults.dailyQuota ?? globalDaily),
      hourlyQuota: envInt(`${prefix}_HOURLY_QUOTA`, defaults.hourlyQuota ?? globalHourly),
      maxOutputTokens: envInt(`${prefix}_MAX_OUTPUT_TOKENS`, defaults.maxOutputTokens),
    };
  }

  return {
    maxInputChars: envInt('SAFETY_MAX_INPUT_CHARS', 32_000),
    maxOutputTokens: envInt('SAFETY_MAX_OUTPUT_TOKENS', 4096),
    maxImageBytes: envInt('SAFETY_MAX_IMAGE_BYTES', 6_000_000),
    maxDocumentChars: envInt('SAFETY_MAX_DOCUMENT_CHARS', 8_000),
    maxRequestBodyBytes: envInt('SAFETY_MAX_REQUEST_BODY_BYTES', 10 * 1024 * 1024),
    requestTimeoutMs: envInt('SAFETY_REQUEST_TIMEOUT_MS', 90_000),
    globalDailyQuota: globalDaily,
    globalHourlyQuota: globalHourly,
    endpoints,
    requireDeviceId: envBool('SAFETY_REQUIRE_DEVICE_ID', false),
    requireFirebaseAuth: envBool('SAFETY_REQUIRE_FIREBASE_AUTH', false),
    logAbuseEvents: envBool('SAFETY_LOG_ABUSE_EVENTS', true),
    moderationEnabled: envBool('SAFETY_MODERATION_ENABLED', true),
    promptInjectionEnabled: envBool('SAFETY_PROMPT_INJECTION_ENABLED', true),
  };
}

module.exports = { loadSafetyPolicy, ENDPOINT_DEFAULTS };
