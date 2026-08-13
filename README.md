# StudentAI

Offline-first AI-powered student companion for Android.

## Project structure

```
edukasyon/
├── androidApp/          # Primary Android application (StudentAI)
│   └── src/main/kotlin/com/edukasyon/studentai/
│       ├── core/        # AI, network, sync, utilities
│       ├── data/        # Room, repositories, preferences
│       ├── domain/      # Models, repository interfaces, use cases
│       ├── di/          # Hilt modules
│       └── ui/          # Compose screens, theme, navigation
└── backend/             # Secure AI proxy API (Node.js)
```

## Build

```bash
# Windows — use JDK 17+ (JDK 25 works with Gradle 8.9)
set JAVA_HOME=C:\Program Files\Java\jdk-25.0.2
gradlew.bat :androidApp:assembleDebug
```

## Features (MVP)

- **Offline-first** — Room database is the source of truth
- **Guest mode** — no account required
- **Onboarding** — school setup, continue offline
- **Home dashboard** — next class, schedule, tasks, exams, AI suggestions
- **Schedule** — daily view, add/edit/delete classes
- **Planner** — tasks, assignments, exams with CRUD; **Assignment Intelligence** (AI breakdown → subtasks)
- **Notes** — create, search, delete
- **Grades** — weighted grade calculation
- **Calendar** — unified events from tasks/exams/assignments
- **AI** — tutor chat, summarizer, flashcards, quiz, schedule scanner (mock + remote)
- **Profile** — theme, notifications, AI settings, privacy info
- **Sync skeleton** — WorkManager periodic sync

## AI security

- No API keys in the Android app
- Backend URL via `BuildConfig.AI_BACKEND_URL`
- Run `backend/` with provider key in `backend/.env` (see `backend/.env.example`): [hcnsec.cn](https://api.hcnsec.cn/v1) — text uses `auto`, vision auto-routes to `step-3.7-flash`

## Application ID

`com.edukasyon.studentai` (debug builds use `com.edukasyon.studentai.debug`)

## Firebase

Project: **edukasyon-studentai** (`246011040847`)

- Auth: anonymous + email/password enabled
- Firestore: user-scoped rules in `firestore.rules`
- Config: `androidApp/google-services.json` (release + debug clients)

Guest onboarding signs in anonymously when online; offline guest mode still works with a local UUID.

### Manual setup (if needed)

1. Install [Firebase CLI](https://firebase.google.com/docs/cli) and run `firebase login`
2. Select project: `firebase use edukasyon-studentai`
3. Deploy rules: `firebase deploy --only firestore:rules,auth`
4. In [Firebase Console](https://console.firebase.google.com/project/edukasyon-studentai), confirm **Authentication → Sign-in method → Anonymous** is enabled

## Deferred / follow-up

- Per-subtask calendar entries (currently one parent task + checklist subtasks; suggested dates in description)
- Full Google Calendar two-way sync
- Push reminders for individual subtask offsets (parent task uses existing `ReminderSyncService` / WorkManager)

## Assignment Intelligence (backend)

Endpoint: `POST /api/ai/assignment-breakdown` (via AiSafetyGateway)

Deploy: restart backend after pull; requires `AI_API_KEY` in `backend/.env` for real analysis (mock without key).

```bash
cd backend && npm install && npm start
# npm test  — includes AssignmentBreakdownValidator tests
```

Request body: `{ "text"?: string, "attachmentText"?: string, "imageBase64"?: string }`
