# AI Safety Implementation Guide

**Project:** SchedMate  
**Date:** 2026-08-13  
**Phases covered this session:** 1 (audit), 2–6 (core infrastructure), partial 11 (Android UX), partial 12 (backend tests)

---

## What Was Built

### Backend modular structure

```
backend/
├── ai/
│   ├── AiSafetyGateway.js      # Central pipeline for ALL AI routes
│   ├── AiProvider.js           # OpenAI-compatible provider adapter
│   └── PromptBuilder.js        # Trusted Jarvis prompt + untrusted content wrapping
├── safety/
│   ├── InputModerator.js
│   ├── OutputModerator.js
│   ├── PromptInjectionDetector.js
│   ├── SafetyPolicy.js
│   └── DataClassifier.js
├── abuse/
│   ├── RateLimiter.js
│   ├── QuotaService.js
│   ├── AbuseRiskService.js
│   ├── AbuseEventRepository.js
│   └── UsageTracker.js
├── auth/
│   └── AuthenticationService.js
├── validation/
│   └── AiResponseValidator.js  # Chat, flashcard, quiz, schedule validators
├── util/
│   └── safeErrors.js
└── tests/
    └── safety.test.js
```

### Gateway pipeline (every AI route)

1. Authenticate (`X-Device-Id`, optional Bearer, IP fallback)  
2. Validate schema + size limits  
3. Rate limit (per user/device/IP/endpoint)  
4. Quota check (hourly + daily, per-endpoint + global)  
5. Input moderation + prompt injection detection  
6. Abuse risk scoring  
7. Handler execution (trusted system prompt separate from user content)  
8. Provider call with timeout  
9. Output moderation + feature validators  
10. Privacy-preserving abuse event log  
11. Safe error responses (no stack traces, keys, or raw provider bodies)

### AI endpoints using gateway

| Endpoint | Handler | Validators |
|----------|---------|------------|
| `POST /api/ai/chat` | `handleChat` | Chat result validator |
| `POST /api/ai/schedule-analysis` | `handleScheduleAnalysis` | ScheduleValidator |
| `POST /api/ai/summarize` | `handleSummarize` | Text validator |
| `POST /api/ai/flashcards` | `handleFlashcards` | FlashcardValidator |
| `POST /api/ai/quiz` | `handleQuiz` | QuizValidator |
| `POST /api/ai/study-plan` | `handleStudyPlan` | Items array check |

Additional routes:
- `GET /health` — status + usage snapshot  
- `GET /health/safety` — policy summary + abuse event counts (no secrets)

### Android changes

| File | Purpose |
|------|---------|
| `ui/components/AiSafetyMessages.kt` | User-friendly messages for 429/402/403 codes |
| `core/ai/AiSafetyErrorParser.kt` | Parses backend `{ code, error, retryAfterMs }` JSON |
| `core/network/AiSafetyHeadersInterceptor.kt` | Sends `X-Device-Id` on all backend requests |
| `core/ai/RemoteAiService.kt` | Uses error parser for HttpException |
| `ui/viewmodel/ViewModels.kt` | Tutor cooldown uses `AiSafetyMessages.tutorClientCooldown()` |
| `di/AppModule.kt` | Registers safety headers interceptor |

---

## Environment Variables (Render Dashboard)

Set on **studentai-backend** service:

### Required (existing)

| Variable | Example | Notes |
|----------|---------|-------|
| `AI_API_KEY` | *(secret)* | hcnsec.cn key — **Secret** |
| `AI_BASE_URL` | `https://api.hcnsec.cn/v1` | |
| `TEXT_MODEL` | `auto` | |
| `VISION_MODEL` | `step-3.7-flash` | |

### Safety (new — optional, defaults shown)

| Variable | Default | Description |
|----------|---------|-------------|
| `SAFETY_MAX_INPUT_CHARS` | `32000` | Max combined text input |
| `SAFETY_MAX_OUTPUT_TOKENS` | `4096` | Global output cap |
| `SAFETY_MAX_IMAGE_BYTES` | `6000000` | Base64 image approx limit |
| `SAFETY_MAX_DOCUMENT_CHARS` | `8000` | Attachment text limit |
| `SAFETY_REQUEST_TIMEOUT_MS` | `90000` | Provider call timeout |
| `SAFETY_DAILY_QUOTA` | `200` | Global daily requests per identity |
| `SAFETY_HOURLY_QUOTA` | `60` | Global hourly requests per identity |
| `SAFETY_MODERATION_ENABLED` | `true` | Input/output moderation |
| `SAFETY_PROMPT_INJECTION_ENABLED` | `true` | Injection detector |
| `SAFETY_REQUIRE_DEVICE_ID` | `false` | Set `true` to reject requests without `X-Device-Id` |
| `SAFETY_REQUIRE_FIREBASE_AUTH` | `false` | Future: require verified Firebase token |

### Per-endpoint overrides

Pattern: `SAFETY_{ENDPOINT}_{SETTING}` (endpoint uses underscores, e.g. `SCHEDULE_ANALYSIS`)

| Variable | Default | Description |
|----------|---------|-------------|
| `SAFETY_CHAT_RATE_PER_MIN` | `1` | Jarvis tutor rate limit |
| `SAFETY_CHAT_BURST_PER_MIN` | `2` | Short burst allowance |
| `SAFETY_CHAT_DAILY_QUOTA` | `100` | |
| `SAFETY_CHAT_HOURLY_QUOTA` | `30` | |
| `SAFETY_SCHEDULE_ANALYSIS_RATE_PER_MIN` | `3` | Vision schedule scans |
| `SAFETY_FLASHCARDS_RATE_PER_MIN` | `3` | |
| `SAFETY_QUIZ_RATE_PER_MIN` | `3` | |

---

## Backend Error Codes (Android handles these)

| HTTP | Code | User message source |
|------|------|---------------------|
| 429 | `RATE_LIMIT_EXCEEDED` | `AiSafetyMessages.rateLimitMessage()` |
| 402 | `QUOTA_EXCEEDED` | `AiSafetyMessages.quotaMessage()` |
| 403 | `CONTENT_BLOCKED` | `AiSafetyMessages.contentBlockedMessage()` |
| 403 | `PROMPT_INJECTION_BLOCKED` | `AiSafetyMessages.injectionBlockedMessage()` |
| 403 | `ABUSE_DETECTED` | `AiSafetyMessages.abuseDetectedMessage()` |
| 422 | `OUTPUT_BLOCKED` | `AiSafetyMessages.outputBlockedMessage()` |
| 502 | `PROVIDER_ERROR` | Generic unavailable message |

---

## Running Tests

```bash
cd backend
npm test
```

Tests cover: rate limiter burst, input moderator allow/block, injection detection, quiz validation, safe error sanitization.

---

## Redis Upgrade Path (v2)

Current `RateLimiter` and `QuotaService` use in-memory `Map`:

- **Problem:** Resets on deploy; not shared across Render instances if scaled beyond 1.  
- **Fix:** Replace store with Redis/Valkey keys:
  - `ratelimit:{userId}:{endpoint}` — sorted set or INCR with EXPIRE  
  - `quota:hourly:{userId}:{endpoint}` — INCR with TTL 3600  
  - `quota:daily:{userId}:{endpoint}` — INCR with TTL 86400  

Render Key Value (Valkey) is documented in Render MCP skills.

---

## Remaining Phases (Master Prompt §77 Checklist)

### Phase 7 — Firebase App Check / attestation
- [ ] Enable App Check on Firebase project  
- [ ] Verify App Check token on backend  

### Phase 8 — Firebase Admin auth on backend
- [ ] Add `firebase-admin` dependency  
- [ ] Verify ID tokens in `AuthenticationService`  
- [ ] Android: send Firebase ID token in `Authorization` header  

### Phase 9 — Persistent abuse store
- [ ] File-based or Postgres abuse event persistence  
- [ ] Admin dashboard for abuse review  

### Phase 10 — CORS / network hardening
- [ ] Restrict `Access-Control-Allow-Origin`  
- [ ] Optional API key for backend (separate from provider key)  

### Phase 11 — Android (remaining)
- [ ] Dedicated UI for quota exhausted state on Tools tab  
- [ ] Retry-after countdown for 429  
- [ ] Firebase token injection in OkHttp interceptor  

### Phase 12 — Tests (remaining)
- [ ] Integration tests with mock Express server  
- [ ] Android unit tests for `AiSafetyErrorParser`  
- [ ] Load test rate limiter under concurrency  

### Phase 13 — Observability
- [ ] Structured logging (JSON) to Render log stream  
- [ ] Metrics: requests/min, block rate, quota hits  
- [ ] Alerting on abuse spike  

### Phase 14 — Policy tuning
- [ ] Review false positives from InputModerator  
- [ ] A/B test chat rate limit (1 vs 2/min)  
- [ ] Per-user tier quotas (free vs premium)  

### Phase 15 — Documentation & compliance
- [ ] Privacy policy update (AI data handling)  
- [ ] Student data retention policy for abuse logs  

---

## Deployment Checklist

1. Merge changes and deploy backend to Render  
2. Set safety env vars (at minimum confirm `SAFETY_CHAT_RATE_PER_MIN=1`)  
3. Verify `GET /health` shows `safetyEnabled: true`  
4. Ship Android build with `AiSafetyHeadersInterceptor`  
5. Monitor `GET /health/safety` for abuse event counts after launch  

---

*See also: `docs/AI_SAFETY_AUDIT.md` for pre-implementation findings.*
