# StudentAI Backend

Secure proxy between the StudentAI Android app and the hcnsec.cn OpenAI-compatible AI provider.

## Security

- **Never** embed API keys in the Android app or commit them to git
- Store API keys in `backend/.env` only (gitignored)
- The Android app only knows the backend URL via `BuildConfig.AI_BACKEND_URL`
- Use `backend/.env.example` as a template — placeholders only

## Single-provider architecture

| Use case | Provider | Default base URL | Model |
|----------|----------|------------------|-------|
| Chat (text, images, attachments) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |
| Schedule image analysis | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |
| Study tools (summarize, flashcards, quiz, study-plan) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |

Optional client override: `step-3.7-flash` (Profile → AI Settings → Step 3.7 Flash).

The Android app never calls hcnsec.cn directly — only this backend proxy does.

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AI_API_KEY` | No* | — | API key for hcnsec.cn. Legacy `TEXT_AI_API_KEY` / `VISION_AI_API_KEY` also accepted. |
| `AI_BASE_URL` | No | `https://api.hcnsec.cn/v1` | OpenAI-compatible base URL |
| `AI_MODEL` | No | `auto` | Default model for all requests |
| `TEXT_MODEL` | No | `auto` | Model for text chat and study tools |
| `VISION_MODEL` | No | `auto` | Model for image chat and schedule analysis |
| `PORT` | No | `8080` | HTTP port |

### Allowed models

| Slug | Use |
|------|-----|
| `auto` | Default — text, vision, tools, files |
| `step-3.7-flash` | Reasoning — optional client override via Profile |

### Model routing

| Request type | Model used |
|--------------|------------|
| Chat (any attachment type) | Client `step-3.7-flash` if set, else `TEXT_MODEL` / `VISION_MODEL` (`auto`) |
| Schedule analysis | `VISION_MODEL` (`auto`), or client override |
| Summarize / flashcards / quiz / study-plan | `TEXT_MODEL` (`auto`), or client override |

\* Without `AI_API_KEY`, the server runs in **mock mode** (no crash).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/chat` | AI tutor chat |
| POST | `/api/ai/schedule-analysis` | Schedule image OCR/vision |
| POST | `/api/ai/summarize` | Note summarization |
| POST | `/api/ai/flashcards` | Flashcard generation |
| POST | `/api/ai/quiz` | Quiz generation |
| POST | `/api/ai/study-plan` | Study plan generation |
| GET | `/health` | Health check (provider, models, `allowedModels`, `routingPolicy`) |

## Run locally

```bash
cd backend
npm install
cp .env.example .env   # then edit .env with your real key
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
3. Set **AI_API_KEY** in the Render Dashboard (mark as Secret).
4. After deploy, verify: `curl https://studentai-backend-ha0z.onrender.com/health`
5. If Render assigns a different URL, update `AI_BACKEND_URL` in `androidApp/build.gradle.kts`.

**Env vars on Render:**

| Key | Value |
|-----|-------|
| `AI_API_KEY` | Your hcnsec.cn key (Secret) |
| `AI_BASE_URL` | `https://api.hcnsec.cn/v1` |
| `AI_MODEL` | `auto` |
| `TEXT_MODEL` | `auto` |
| `VISION_MODEL` | `auto` |

`PORT` is set automatically by Render.

## Android app setup

Debug and release builds use the cloud backend URL — no local `npm start` required.

Profile → **AI Settings**: Auto (default) or Step 3.7 Flash (`step-3.7-flash`) for stronger reasoning.

For local backend development only:

```bash
cd backend && npm install && cp .env.example .env && npm start
```

Temporarily change `AI_BACKEND_URL` in `build.gradle.kts` to `http://10.0.2.2:8080/` (emulator) or your LAN IP (physical device).
