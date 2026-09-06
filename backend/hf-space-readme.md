---
title: SchedMate Backend
sdk: docker
emoji: 🤖
colorFrom: blue
colorTo: purple
short_description: Secure AI proxy backend for SchedMate Android app
---

# SchedMate Backend

Express.js backend running on Hugging Face Spaces.

## Setup

1. Go to **Space Settings** → **Variables and secrets**
2. Add these environment variables:

| Key | Value |
|-----|-------|
| `AI_API_KEY` | *(your HCN API key)* |
| `AI_BASE_URL` | `https://api.hcnsec.cn/v1` |
| `TEXT_MODEL` | `auto` |
| `VISION_MODEL` | `MiniMax-M3` |
| `SAFETY_REQUEST_TIMEOUT_MS` | `90000` |
| `SAFETY_SCHEDULE_ANALYSIS_REQUEST_TIMEOUT_MS` | `300000` |
| `SAFETY_RATE_PER_MIN` | `60` |
| `SAFETY_DAILY_QUOTA` | `2000` |
| `SAFETY_HOURLY_QUOTA` | `200` |

3. Deploy the Space
4. Your URL will be: `https://<username>-schedmate-backend.hf.space`

## Optional: Keep Space Awake

Free Spaces sleep after 48 hours of inactivity. To keep it awake:
- Use a free uptime monitor like **UptimeRobot** or **Cron-job.org**
- Ping `https://<username>-schedmate-backend.hf.space/health` every 30 minutes

## Endpoints

- `GET /health` — health check
- `POST /api/ai/schedule-analysis` — schedule scanner (async job)
- `POST /api/ai/chat` — chat completion
- `POST /api/ai/summarize` — summarize
- `POST /api/ai/flashcards` — generate flashcards
- `POST /api/ai/quiz` — generate quiz
- `POST /api/ai/study-plan` — generate study plan
- `POST /api/ai/assignment-breakdown` — assignment breakdown
- `POST /api/ai/focus-plan` — focus plan
