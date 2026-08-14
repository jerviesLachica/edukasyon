# SchedMate Backend

Secure proxy between the SchedMate Android app and the hcnsec.cn OpenAI-compatible AI provider.

## Security

- **Never** embed API keys in the Android app or commit them to git
- Store API keys in `backend/.env` only (gitignored)
- The Android app only knows the backend URL via `BuildConfig.AI_BACKEND_URL`
- Use `backend/.env.example` as a template — placeholders only

## Single-provider architecture

| Use case | Provider | Default base URL | Model |
|----------|----------|------------------|-------|
| Chat (text-only) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |
| Chat (image attachments) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `step-3.7-flash` (auto-routed) |
| Schedule image analysis | hcnsec.cn | `https://api.hcnsec.cn/v1` | `step-3.7-flash` (auto-routed) |
| Study tools (summarize, flashcards, quiz, study-plan) | hcnsec.cn | `https://api.hcnsec.cn/v1` | `auto` |

Optional client override for **text-only** chat: `step-3.7-flash` (Profile → AI Settings → Step 3.7 Flash). Vision requests always route to `step-3.7-flash` on the server — the client does not need to set a vision model.

The Android app never calls hcnsec.cn directly — only this backend proxy does.

## Web search

Set `TAVILY_API_KEY` on the backend to enable web search. In Jarvis chat, start a
message with `/search`, for example `/search current renewable energy statistics`.
Jarvis receives the returned sources as untrusted reference material and cites them
as `[1]`, `[2]`, and so on. The Tavily key stays on the server and is never sent to
the Android app.

## Environment variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AI_API_KEY` | No* | — | API key for hcnsec.cn. Legacy `TEXT_AI_API_KEY` / `VISION_AI_API_KEY` also accepted. |
| `AI_BASE_URL` | No | `https://api.hcnsec.cn/v1` | OpenAI-compatible base URL |
| `AI_MODEL` | No | `auto` | Legacy default (text routing uses `TEXT_MODEL`) |
| `TEXT_MODEL` / `AI_TEXT_MODEL` | No | `auto` | Model for text chat and study tools |
| `VISION_MODEL` / `AI_VISION_MODEL` | No | `step-3.7-flash` | Model for image chat and schedule analysis |
| `PORT` | No | `8080` | HTTP port |
| `TAVILY_API_KEY` | No | — | Enables `/search` web search in chat via Tavily |

### Allowed models

| Slug | Use |
|------|-----|
| `auto` | Default for text chat and study tools |
| `step-3.7-flash` | Vision (images, schedule scanner, PDF OCR) + optional text reasoning override |

### Model routing (automatic)

| Request type | Model used |
|--------------|------------|
| Chat (text-only) | `TEXT_MODEL` (`auto`), or client `step-3.7-flash` for reasoning |
| Chat (image in `imageBase64` or message content) | `VISION_MODEL` (`step-3.7-flash`) — client `auto` is ignored |
| Schedule analysis | `VISION_MODEL` (`step-3.7-flash`) — always vision |
| Summarize / flashcards / quiz / study-plan | `TEXT_MODEL` (`auto`), or client `step-3.7-flash` override |

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
| `VISION_MODEL` | `step-3.7-flash` |

`PORT` is set automatically by Render.

## Android app setup

Debug and release builds use the cloud backend URL — no local `npm start` required.

## Schedule scanner system prompt

Schedule image analysis (`POST /api/ai/schedule-analysis`) uses a dedicated server-controlled prompt — **not** the Jarvis chat prompt.

- Full training prompt: `backend/prompts/schedule-scanner-prompt.txt` (sections 1–75)
- Android output contract override: `backend/prompts/android-output-contract.txt` (section 76)
- Wired in: `backend/prompts/schedule-scanner-system-prompt.js` → `backend/server.js`

After changing prompt files, **redeploy the backend** (e.g. push to Render) for production Android builds to use the updated prompt.

Regenerate the Android mirror constant:

```bash
python backend/prompts/generate-android-prompt.py
```

Profile → **AI Settings**: Auto (default) or Step 3.7 Flash (`step-3.7-flash`) for stronger reasoning.

Chat has no per-minute cooldown. Step 3.7 Flash allows 25 requests every 10 minutes;
hourly and daily safety quotas still apply.

For local backend development only:

```bash
cd backend && npm install && cp .env.example .env && npm start
```

Temporarily change `AI_BACKEND_URL` in `build.gradle.kts` to `http://10.0.2.2:8080/` (emulator) or your LAN IP (physical device).
