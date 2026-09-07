# SchedMate AI Routing Architecture

This document describes the dual-provider AI routing architecture implemented in the SchedMate Node.js backend (`backend/ai/AiProvider.js`).

---

## 1. Overview & Motivation

The backend operates as an offline-first proxy between the Android client (`com.edukasyon.studentai`) and external OpenAI-compatible LLM endpoints. 

Previously, schedule analysis relied on `MiniMax-M3` over the default distributor (`https://api.hcnsec.cn/v1`), taking **60–150+ seconds** per schedule image.

We introduced a **dual-provider fast-path with automatic fallback** using OrcaRouter's free tier (`https://api.orcarouter.ai/v1`).

---

## 2. Multi-Provider Architecture

```
                 Client Request (Android App)
                             │
                             ▼
                    POST /api/ai/*
                             │
                             ▼
                     AiSafetyGateway
       (Auth, Moderation, Rate Limits, Input Sanitization)
                             │
                             ▼
                       AiProvider
                             │
              ┌──────────────┴──────────────┐
              ▼                             ▼
       [Vision Request]              [Text Request]
              │                             │
              ▼                             ▼
   OrcaRouter (Fast Path)             hcnsec (Default)
  Model: z-ai/glm-5.3-flash-free       Model: auto
  Latency: ~5–30s                     Latency: ~2–5s
  Limits: 10 RPM (Free Tier)          Unlimited
              │
    (On HTTP 429 / Error)
              │
              ▼ [Fallback]
       hcnsec Provider
       Model: MiniMax-M3
       Latency: ~60–120s
       Limits: Unlimited
```

---

## 3. Provider Configuration & Models

| Workflow | Primary Provider | Primary Model | Fallback Provider | Fallback Model |
|---|---|---|---|---|
| **Schedule Analysis (Vision)** | **OrcaRouter** (`https://api.orcarouter.ai/v1`) | `z-ai/glm-5.3-flash-free` (~30s) | **hcnsec** (`https://api.hcnsec.cn/v1`) | `MiniMax-M3` (~100s) |
| **Chat with Image (Vision)** | **OrcaRouter** (`https://api.orcarouter.ai/v1`) | `z-ai/glm-5.3-flash-free` (~10–20s) | **hcnsec** (`https://api.hcnsec.cn/v1`) | `MiniMax-M3` (~60s) |
| **Text Chat / Prompts** | **hcnsec** (`https://api.hcnsec.cn/v1`) | `auto` (~2–4s) | N/A | None |

---

## 4. Key Logic & Functions (`backend/ai/AiProvider.js`)

1. **`ORCA_VISION_MODEL`**:
   - Set to `'z-ai/glm-5.3-flash-free'`.
   - Free tier: 10 requests per minute (~1,440/day), zero cost.

2. **`modelFallbackChain(primaryModel, { isVision = false })`**:
   - For vision calls (`isVision: true`):
     - If `ORCA_API_KEY` is present, `ORCA_VISION_MODEL` is prepended as the **first** candidate.
     - `normalizedPrimary` (maps to `MiniMax-M3`) is appended second.
   - For text calls:
     - Maintains standard hcnsec models (`auto`, etc.).

3. **`toWireModelSlug(slug, { isVision, provider })`**:
   - Explicitly handles provider tagging. When `provider === 'orca'`, preserves `z-ai/glm-5.3-flash-free`.
   - When routing to `hcnsec`, maps `auto` or `agnes-2.5-flash` with vision to `MiniMax-M3` (since hcnsec has no multimodal support on `auto`).

4. **`chatCompletion(messages, options)`**:
   - Dynamically selects the `baseUrl` and `apiKey` per candidate in the fallback chain (`ORCA_BASE_URL` vs `AI_BASE_URL`).
   - Catches `429` (rate limits) or upstream timeouts on OrcaRouter and seamlessly retries with the next model in chain (`MiniMax-M3` on hcnsec).

---

## 5. Environment Variables (`render.yaml` & Dashboard)

- `AI_API_KEY`: API key for upstream hcnsec (`sk-...`).
- `AI_BASE_URL`: `https://api.hcnsec.cn/v1`
- `ORCA_API_KEY`: API key for OrcaRouter (`sk-orca-...`).
- `ORCA_BASE_URL`: `https://api.orcarouter.ai/v1`
- `TEXT_MODEL`: `auto`
- `VISION_MODEL`: `MiniMax-M3` (baseline hcnsec vision model)

---

## 6. Verification & Test Suite

All routing logic is covered by unit tests in `backend/tests/aiProvider.test.js`:
- Ensures `ORCA_VISION_MODEL` is the first element in the vision chain when `ORCA_API_KEY` is active.
- Confirms graceful fallback to `MiniMax-M3` when `ORCA_API_KEY` is absent.
- Run tests via:
  ```bash
  cd backend && npm test
  ```
