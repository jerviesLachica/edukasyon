# AI Safety Security Audit

**Project:** SchedMate  
**Date:** 2026-08-13  
**Scope:** Backend (`backend/server.js`), Android AI layer, Firebase/Firestore, secrets, rate limiting, auth, uploads, prompts, API key storage  
**Method:** Static code review with file-path evidence (no speculation)

---

## Executive Summary

The project correctly proxies AI calls through a Node.js backend and keeps provider API keys out of the Android app. Prior to this session, **no server-side rate limiting, authentication on AI routes, input/output moderation, or quota enforcement** existed. The Android app had a **client-only 1 req/min Jarvis cooldown** but the backend accepted unauthenticated, unlimited requests.

This audit informed the implementation of `AiSafetyGateway` and related modules (see `docs/AI_SAFETY_IMPLEMENTATION.md`).

---

## CRITICAL

### C1 — AI endpoints had no authentication (FIXED this session)

**Evidence:** Pre-refactor `backend/server.js` — all `POST /api/ai/*` routes called `callAiOrMock()` directly with no auth middleware.

**Risk:** Anyone who discovers the Render URL (`https://studentai-backend-ha0z.onrender.com/`) could consume API credits indefinitely.

**Status:** Mitigated — `backend/auth/AuthenticationService.js` + `AiSafetyGateway` now identify callers via `X-Device-Id` / optional Bearer token. Full Firebase token verification not yet implemented (see HIGH H2).

---

### C2 — Provider API key exposure via error responses (FIXED this session)

**Evidence:** Pre-refactor `backend/server.js` line ~450:
```javascript
res.status(502).json({ error: 'AI provider request failed', detail: err.message });
```
Provider errors could include raw HTTP bodies with key material.

**Status:** Mitigated — `backend/util/safeErrors.js` strips secrets; gateway never returns `detail` with raw provider output.

---

### C3 — No server-side rate limiting or quotas (FIXED this session)

**Evidence:** Pre-refactor — no rate limiter in `backend/`. Android-only limit at `ViewModels.kt` (`TUTOR_RATE_LIMIT_MS = 60_000L`, lines ~738–744, ~1343).

**Risk:** Non-Jarvis endpoints (`/flashcards`, `/quiz`, `/schedule-analysis`) had zero throttling; Jarvis limit bypassed by direct API calls.

**Status:** Mitigated — `backend/abuse/RateLimiter.js`, `QuotaService.js` enforced in gateway. Chat default: **1 req/min** (configurable via env).

---

## HIGH

### H1 — CORS allows any origin on AI routes

**Evidence:** `backend/server.js`:
```javascript
res.header('Access-Control-Allow-Origin', '*');
```

**Risk:** Any website can call the backend from a user's browser if they obtain the URL. Mobile app is less affected.

**Recommendation:** Restrict to app-specific origins or remove browser CORS (mobile-only clients don't need `*`).

---

### H2 — Firebase ID tokens not verified on backend

**Evidence:** Android uses `FirebaseAuthManager` (`androidApp/.../FirebaseAuthManager.kt`) for Firestore sync, but `AiApiService` / `RemoteAiService` send **no** `Authorization` header. `AuthenticationService` accepts Bearer tokens but only hashes them for identity — **does not verify** with Firebase Admin SDK.

**Recommendation:** Add `firebase-admin`, verify ID tokens when `SAFETY_REQUIRE_FIREBASE_AUTH=true`.

---

### H3 — Client-supplied system prompt ignored but no gateway before this session

**Evidence:** Pre-refactor `buildJarvisSystemMessage()` warned and ignored client prompts (good), but injection could still occur via `message`, `attachmentText`, `contextSummary` without moderation.

**Status:** Mitigated — `InputModerator`, `PromptInjectionDetector`, untrusted content wrapping in `PromptBuilder.wrapUntrustedDocument()`.

---

### H4 — Large request bodies accepted (10 MB JSON)

**Evidence:** Pre-refactor `express.json({ limit: '10mb' })`.

**Risk:** Cost/DoS via oversized payloads.

**Status:** Partially mitigated — gateway validates `maxInputChars`, `maxImageBytes`, `maxDocumentChars` via `SafetyPolicy`. Body limit still 10 MB at Express layer (configurable via `SAFETY_MAX_REQUEST_BODY_BYTES`).

---

### H5 — `backend/.env` present locally (gitignored)

**Evidence:** `git check-ignore -v backend/.env` → ignored by `.gitignore:21`. File exists in workspace (`?? backend/.env` in git status as untracked/modified locally).

**Risk:** Accidental commit of real keys.

**Status:** `.gitignore` correct; `backend/.env.example` uses placeholders only.

---

## MEDIUM

### M1 — In-memory rate limits / quotas (single-instance)

**Evidence:** `RateLimiter.js`, `QuotaService.js` use `Map` — resets on deploy, not shared across Render instances.

**Recommendation:** Redis/Valkey for production multi-instance (documented in implementation guide).

---

### M2 — No request signing / app attestation

**Evidence:** Android sends `X-Device-Id` (new) but header is spoofable.

**Recommendation:** Play Integrity API or Firebase App Check for stronger client verification.

---

### M3 — Firestore rules are minimal but correct for current scope

**Evidence:** `firestore.rules` — users may only read/write `/users/{userId}` when `request.auth.uid == userId`.

**Gap:** No AI conversation or usage data in Firestore rules (AI chats stored locally in Room — `AiConversationRepository`).

---

### M4 — Health endpoint exposes provider URL

**Evidence:** `GET /health` returns `provider: AI_BASE_URL`.

**Risk:** Information disclosure (low — URL is not secret).

---

### M5 — Debug HTTP logging on Android

**Evidence:** `AppModule.kt` — `HttpLoggingInterceptor.Level.BASIC` when `BuildConfig.DEBUG`.

**Risk:** Request metadata in logcat during development.

---

### M6 — No output validation before this session

**Evidence:** Pre-refactor — `extractJson()` on flashcards/quiz/schedule with no schema validation; malformed JSON could crash handlers.

**Status:** Mitigated — `QuizValidator`, `FlashcardValidator`, `ScheduleValidator` in gateway pipeline.

---

## LOW

### L1 — `google-services.json` contains Firebase API keys (expected)

**Evidence:** `androidApp/google-services.json` — `api_key` entries.

**Note:** Firebase client API keys are restricted by package name/SHA in Firebase Console — not equivalent to AI provider keys.

---

### L2 — Hardcoded backend URL in shared module

**Evidence:** `shared/src/commonMain/kotlin/.../BackendConfig.kt`:
```kotlin
const val AI_BACKEND_URL = "https://studentai-backend-ha0z.onrender.com/"
```

**Note:** Acceptable for proxy URL; not a secret.

---

### L3 — Mock mode reveals setup hints

**Evidence:** Mock responses mention `AI_API_KEY` setup (dev-only when key unset).

---

### L4 — No heart-related UI found (prior agent work)

**Evidence:** Grep for `heart|Heart` in repo — **no matches**. No conflict with heart removal task.

---

## Android API Key Storage — PASS

| Check | Result | Evidence |
|-------|--------|----------|
| AI provider key in Android | **None found** | Keys only in `backend/server.js` env vars |
| Direct provider URL in app | **No** | `BuildConfig.AI_BACKEND_URL` → Render proxy |
| `sk-` / Bearer in Kotlin | **None** | Grep across repo |

---

## Authentication / Authorization Model

| Layer | Before | After (this session) |
|-------|--------|----------------------|
| Backend AI routes | None | Device ID + IP identity; optional Bearer passthrough |
| Android → Backend | No auth headers | `X-Device-Id` via `AiSafetyHeadersInterceptor` |
| Firestore | Firebase Auth UID scoping | Unchanged (correct) |
| Jarvis client cooldown | 1/min in `AiViewModel` | Kept; server is authoritative |

---

## File Upload Flows

| Flow | Location | Limits |
|------|----------|--------|
| Chat attachments | `ChatAttachmentUtils.kt` | `MAX_CHAT_ATTACHMENT_BYTES = 4MB`, image compress ~900KB |
| PDF tools | `ChatAttachmentUtils.kt` | `MAX_TOOLS_PDF_BYTES = 8MB` |
| Schedule scanner | Image → base64 → `/api/ai/schedule-analysis` | Gateway: `SAFETY_MAX_IMAGE_BYTES` default 6MB |
| Text extraction | `readTextContent()` max 8000 chars | Gateway: `SAFETY_MAX_DOCUMENT_CHARS` |

---

## Existing Prompts (Server-Controlled)

| Prompt | File | Client override |
|--------|------|-----------------|
| Jarvis tutor | `backend/ai/PromptBuilder.js` | Ignored (warn logged) |
| Schedule scanner | `backend/prompts/schedule-scanner-system-prompt.js` | Ignored |
| Tool prompts (summarize, quiz, etc.) | Inline in `server.js` handlers | N/A |

Jarvis prompt includes jailbreak resistance, academic integrity, and safety sections (expanded this session).

---

## Rate Limiting Summary

| Location | Mechanism | Enforced |
|----------|-----------|----------|
| Android `AiViewModel` | 60s client cooldown for tutor | Client-only UX |
| Backend gateway | Per device/IP/endpoint sliding window | **Server authoritative** |
| Chat default | 1 req/min, burst 2 | `SafetyPolicy.endpoints.chat` |
| Expensive ops | 3 req/min (flashcards, quiz, schedule) | Configurable per endpoint |

---

## Prior Agent Integration

- **Jarvis 1 req/min:** Client limit preserved in `ViewModels.kt`; server now enforces matching default (`SAFETY_CHAT_RATE_PER_MIN=1`).
- **Heart removal:** No heart references found — no conflicts.

---

## Remaining Audit Actions (Post-Implementation)

1. Deploy gateway to Render and set safety env vars  
2. Add Firebase Admin token verification (H2)  
3. Restrict CORS (H1)  
4. Redis-backed rate limits for scale (M1)  
5. Play Integrity / App Check (M2)  
6. Penetration test of `/health/safety` exposure  

---

*Generated from codebase inspection. Re-run audit after major AI or auth changes.*
