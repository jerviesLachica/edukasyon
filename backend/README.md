# StudentAI Backend

Secure proxy between the StudentAI Android app and OpenAI-compatible AI providers.

## Security

- **Never** embed API keys in the Android app or commit them to git
- Store `AI_API_KEY` in `backend/.env` only (gitignored)
- The Android app only knows the backend URL via `BuildConfig.AI_BACKEND_URL`
- Use `backend/.env.example` as a template — placeholders only

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AI_API_KEY` | No* | — | API key for [agentrouter.org](https://agentrouter.org) |
| `AI_MODEL` | No | `claude-opus-4-8` | Default **vision** model for image chat attachments and schedule analysis (`claude-opus-4-8` or `claude-opus-5`). Client may override vision model via optional `model` field (allowlisted). |
| `TEXT_MODEL` | No | `claude-opus-4-8` | Model for **text-only** chat and study tools (summarize, flashcards, quiz, study-plan). |
| `AI_BASE_URL` | No | `https://agentrouter.org/v1` | OpenAI-compatible provider base URL (must include `/v1`) |
| `AI_USER_AGENT` | No | `QwenCode/0.2.0 (linux; x64)` | User-Agent sent to agentrouter.org (required — generic clients are rejected) |
| `PORT` | No | `8080` | HTTP port |

### Smart model routing

| Request type | Model used |
|--------------|------------|
| Chat with `imageBase64` | Client-requested `claude-opus-4-8` / `claude-opus-5`, or server `AI_MODEL` |
| Chat text-only (incl. `attachmentText`) | `TEXT_MODEL` (`claude-opus-4-8`) |
| Schedule analysis | `AI_MODEL` (vision) |
| Summarize / flashcards / quiz / study-plan | `TEXT_MODEL` (`claude-opus-4-8`) |

\* Without `AI_API_KEY`, the server runs in **mock mode** and returns sample responses (no crash).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/chat` | AI tutor chat |
| POST | `/api/ai/schedule-analysis` | Schedule image OCR/vision |
| POST | `/api/ai/summarize` | Note summarization |
| POST | `/api/ai/flashcards` | Flashcard generation |
| POST | `/api/ai/quiz` | Quiz generation |
| POST | `/api/ai/study-plan` | Study plan generation |
| GET | `/health` | Health check (`aiConfigured`, `visionModel`, `textModel`, `allowedModels`, `routingPolicy`) |

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

The Android app points at the Render backend URL via `BuildConfig.AI_BACKEND_URL` (see `androidApp/build.gradle.kts`, currently `https://studentai-backend-ha0z.onrender.com/`). The app never calls agentrouter.org directly — only this backend proxy does.

1. Push this repo to GitHub/GitLab/Bitbucket.
2. Open [Render Blueprint](https://dashboard.render.com/blueprint/new) and connect the repo (uses root `render.yaml`).
3. Set **AI_API_KEY** in the Render Dashboard (copy from your local `backend/.env`).
4. After deploy, verify: `curl https://studentai-backend-ha0z.onrender.com/health`
5. If Render assigns a different URL, update `AI_BACKEND_URL` in `androidApp/build.gradle.kts`.

**Env vars on Render:**

| Key | Value |
|-----|-------|
| `AI_API_KEY` | Your agentrouter.org key (mark as Secret in Dashboard) |
| `AI_MODEL` | `claude-opus-4-8` or `claude-opus-5` (vision default) |
| `TEXT_MODEL` | `claude-opus-4-8` (text-only chat and study tools) |
| `AI_BASE_URL` | `https://agentrouter.org/v1` |
| `AI_USER_AGENT` | `QwenCode/0.2.0 (linux; x64)` |

`PORT` is set automatically by Render.

## Android app setup

Debug and release builds use the cloud backend URL — no local `npm start` required.

For local backend development only:

```bash
cd backend && npm install && cp .env.example .env && npm start
```

Temporarily change `AI_BACKEND_URL` in `build.gradle.kts` to `http://10.0.2.2:8080/` (emulator) or your LAN IP (physical device).
