# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SchedMate is an offline-first Android student companion app (SchedMate) with a secure Node.js AI proxy backend. The app helps students manage schedules, tasks, assignments, exams, and study goals with AI-powered features.

**Application ID**: `com.edukasyon.studentai` (debug: `com.edukasyon.studentai.debug`)  
**Version**: 1.2.5 (versionCode 8)

## Build Commands

### Android App
```powershell
# Build debug APK
.\gradlew.bat :androidApp:assembleDebug

# Build release APK (requires keystore.properties)
.\gradlew.bat :androidApp:assembleRelease
```

### Backend
```powershell
cd backend
npm install
npm start        # Production (port 8080)
npm run dev      # Development with watch
npm test         # Run tests
```

## Architecture

### Android App (Clean Architecture + MVVM)
- **core/**: AI services, ML Kit, Firebase, notifications, sync workers
- **data/**: Room database, repositories, preferences, Firestore mappers
- **domain/**: Models, repository interfaces, use cases
- **di/**: Hilt dependency injection modules
- **ui/**: Jetpack Compose screens, theme, navigation, components
- **shared/**: Kotlin Multiplatform module (Compose, Ktor)

### Backend (Express.js)
- `/api/ai/*` - AI proxy endpoints (chat, schedule-analysis, summarize, flashcards, quiz, study-plan, assignment-breakdown, focus-plan)
- `/health` - Health check with provider and routing info
- Security layers: AiSafetyGateway, rate limiting, abuse detection, input/output moderation
- AI provider: Routes to hcnsec.cn (OpenAI-compatible) with model routing (`auto` for text, `step-3.7-flash` for vision)

## Key Technologies

- **Android**: Kotlin, Jetpack Compose, Hilt, Room, Ktor, ML Kit, WorkManager
- **Backend**: Node.js, Express, Firebase Admin
- **Database**: Room (local), Firestore (cloud sync)
- **AI**: hcnsec.cn provider via secure backend proxy, NVIDIA NIM for schedule scanning

## Important Patterns

- AI safety headers interceptor adds `X-Ai-Safety-Policy-Version` to requests
- Schedule scanning uses dedicated server-controlled prompts (`backend/prompts/schedule-scanner-prompt.txt`)
- Firestore sync is activated via runtime config in `config/nim_scan_enabled` collection
- Debug builds use `.debug` suffix; release builds require `keystore.properties`

## Environment Variables

### Backend (.env)
| Variable | Default | Description |
|----------|---------|-------------|
| `AI_API_KEY` | — | hcnsec.cn API key |
| `AI_BASE_URL` | `https://api.hcnsec.cn/v1` | API endpoint |
| `TEXT_MODEL` | `auto` | Text chat model |
| `VISION_MODEL` | `step-3.7-flash` | Vision/image model |
| `PORT` | `8080` | Server port |
| `TAVILY_API_KEY` | — | Web search (use `/search` in chat) |

### Android
- `AI_BACKEND_URL` in `BuildConfig` points to Render: `https://studentai-backend-ha0z.onrender.com/`

## Worktrees

Active feature worktrees exist in `.claude/worktrees/`:
- `in-app-update` - In-app update feature
- `scan-accuracy-layout-fix` - Layout improvements

## Recent Changes (from git log)

- NVIDIA NIM activation via runtime Firestore config for schedule scanning
- One-env-var NIM scan activation with safe defaults
- Schedule scanning routed through NVIDIA NIM with pipeline speedups
- Release v1.2.2 (versionCode 6)