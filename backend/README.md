# StudentAI Backend

Secure proxy between the StudentAI Android app and OpenAI-compatible AI providers.

## Security

- **Never** embed API keys in the Android app or commit them to git
- Store API keys in `backend/.env` only (gitignored)
- The Android app only knows the backend URL via `BuildConfig.AI_BACKEND_URL`
- Use `backend/.env.example` as a template — placeholders only

## Dual-provider architecture

| Use case | Provider | Default base URL | Model |
|----------|----------|------------------|-------|
| Text-only chat | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |
| Study tools (summarize, flashcards, quiz, study-plan) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |
| Chat with image attachment | freetokenfaucet.com | `https://freetokenfaucet.com/v1` | `mimo-v2.5-pro` (or client Standard `mimo-v2.5`) |
| Schedule image analysis | freetokenfaucet.com | `https://freetokenfaucet.com/v1` | `mimo-v2.5-pro` |

The Android app never calls hcnsec.cn or freetokenfaucet.com directly — only this backend proxy does.

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TEXT_AI_API_KEY` | No* | — | API key for the text provider (hcnsec.cn). Falls back to legacy `AI_API_KEY` if unset. |
| `TEXT_AI_BASE_URL` | No | `https://api.hcnsec.cn/v1` | OpenAI-compatible base URL for text |
| `TEXT_MODEL` | No | `auto` | Model for text-only chat and study tools |
| `VISION_AI_API_KEY` | No* | — | API key for the vision provider (freetokenfaucet.com). Falls back to legacy `AI_API_KEY` if unset. |
| `VISION_AI_BASE_URL` | No | `https://freetokenfaucet.com/v1` | OpenAI-compatible base URL for vision |
| `VISION_MODEL` | No | `mimo-v2.5-pro` | Default vision model for image chat and schedule analysis (`mimo-v2.5` or `mimo-v2.5-pro`). Client may override via optional `model` field (allowlisted). |
| `PORT` | No | `8080` | HTTP port |

### Smart model routing

| Request type | Provider | Model used |
|--------------|----------|------------|
| Chat with `imageBase64` | Vision | Client-requested `mimo-v2.5` / `mimo-v2.5-pro`, or server `VISION_MODEL` |
| Chat text-only (incl. `attachmentText`) | Text | `TEXT_MODEL` (`auto`) — client model overrides ignored |
| Schedule analysis | Vision | `VISION_MODEL` |
| Summarize / flashcards / quiz / study-plan | Text | `TEXT_MODEL` (`auto`) |

### Vision models (freetokenfaucet.com)

Query `GET https://freetokenfaucet.com/v1/models` with your vision API key. Typical slugs:

| Slug | Use |
|------|-----|
| `mimo-v2.5` | Vision + chat (Standard) |
| `mimo-v2.5-pro` | Vision + chat (Pro) |

\* Without both provider keys, the server runs in **mock mode** for missing providers (no crash).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/chat` | AI tutor chat |
| POST | `/api/ai/schedule-analysis` | Schedule image OCR/vision |
| POST | `/api/ai/summarize` | Note summarization |
| POST | `/api/ai/flashcards` | Flashcard generation |
| POST | `/api/ai/quiz` | Quiz generation |
| POST | `/api/ai/study-plan` | Study plan generation |
| GET | `/health` | Health check (`textConfigured`, `visionConfigured`, providers, models, `routingPolicy`) |

## Run locally

```bash
cd backend
npm install
cp .env.example .env   # then edit .env with your real keys
npm start
```

Server listens on `http://0.0.0.0:8080`.

### Quick test

```bash
curl http://localhost:8080/health
curl -X POST http://localhost:8080/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What is recursion?","subject":"Computer Science"}'
```

## Deploy to Render (production)

The Android app points at the Render backend URL via `BuildConfig.AI_BACKEND_URL` (see `androidApp/build.gradle.kts`, currently `https://studentai-backend-ha0z.onrender.com/`).

1. Push this repo to GitHub/GitLab/Bitbucket.
2. Open [Render Blueprint](https://dashboard.render.com/blueprint/new) and connect the repo (uses root `render.yaml`).
3. Set **TEXT_AI_API_KEY** and **VISION_AI_API_KEY** in the Render Dashboard (mark as Secret).
4. After deploy, verify: `curl https://studentai-backend-ha0z.onrender.com/health`
5. If Render assigns a different URL, update `AI_BACKEND_URL` in `androidApp/build.gradle.kts`.

**Env vars on Render:**

| Key | Value |
|-----|-------|
| `TEXT_AI_API_KEY` | Your hcnsec.cn key (Secret) |
| `TEXT_AI_BASE_URL` | `https://api.hcnsec.cn/v1` |
| `TEXT_MODEL` | `auto` |
| `VISION_AI_API_KEY` | Your freetokenfaucet.com key (Secret) |
| `VISION_AI_BASE_URL` | `https://freetokenfaucet.com/v1` |
| `VISION_MODEL` | `mimo-v2.5-pro` |

`PORT` is set automatically by Render.

## Android app setup

Debug and release builds use the cloud backend URL — no local `npm start` required.

Profile → **AI Settings**: Standard/Pro selects the vision model (`mimo-v2.5` / `mimo-v2.5-pro`) for image chats only. Text-only chat always uses `auto` via the backend.

For local backend development only:

```bash
cd backend && npm install && cp .env.example .env && npm start
```

Temporarily change `AI_BACKEND_URL` in `build.gradle.kts` to `http://10.0.2.2:8080/` (emulator) or your LAN IP (physical device).
