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
| `AI_API_KEY` | No* | — | API key for the OpenAI-compatible provider |
| `AI_MODEL` | No | `auto` | Model name sent to `/chat/completions` |
| `AI_BASE_URL` | No | `https://api.hcnsec.cn/v1` | Provider base URL |
| `PORT` | No | `8080` | HTTP port |

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
| GET | `/health` | Health check (`aiConfigured` shows if key is set) |

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

The Android app points at `https://studentai-backend.onrender.com/` (see `androidApp/build.gradle.kts`).

1. Push this repo to GitHub/GitLab/Bitbucket.
2. Open [Render Blueprint](https://dashboard.render.com/blueprint/new) and connect the repo (uses root `render.yaml`).
3. Set **AI_API_KEY** in the Render Dashboard (copy from your local `backend/.env`).
4. After deploy, verify: `curl https://studentai-backend.onrender.com/health`
5. If Render assigns a different URL, update `AI_BACKEND_URL` in `androidApp/build.gradle.kts`.

**Env vars on Render:** `AI_API_KEY` (secret), `AI_MODEL=auto`, `AI_BASE_URL=https://api.hcnsec.cn/v1`. `PORT` is set automatically.

## Android app setup

Debug and release builds use the cloud backend URL — no local `npm start` required.

For local backend development only:

```bash
cd backend && npm install && cp .env.example .env && npm start
```

Temporarily change `AI_BACKEND_URL` in `build.gradle.kts` to `http://10.0.2.2:8080/` (emulator) or your LAN IP (physical device).
