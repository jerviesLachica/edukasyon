# Multi-Device Sync

StudentAI syncs structured study data across devices via **Firebase Firestore**. Room remains the offline cache; the cloud is the source of truth when signed in with **Google**.

## Architecture

```
Phone/Tablet (Room)  ↔  Firestore  ↔  Phone/Tablet (Room)
         offline cache     cloud         offline cache
              same Google account → same Firebase UID
```

- **Merge strategy:** last-write-wins using `updatedAt` timestamps (or `reviewedAt` for review records).
- **Auth:** Full bidirectional sync runs when the user is signed in with **Google** (persistent UID across devices).
- **Guest / anonymous:** Local Room data only until the user links Google from **Profile → Cloud Sync**.
- **Paths:** All data is scoped under `users/{uid}/…`.

## Google Sign-In (Spark-safe)

Cross-device sync uses **Firebase Authentication — Google provider**, which is **free on the Firebase Spark plan**. No Cloud Functions, Identity Platform paid tier, or Blaze billing required.

### User flow

1. Complete onboarding (creates a local profile and optional anonymous Firebase session).
2. Open **Profile → Cloud Sync**.
3. Tap **Sign in with Google**.
4. If the device started as anonymous, the app **links** the Google credential to preserve local data under the same UID when possible.
5. If the Google account already exists on another device, the app signs in and **merges via Firestore sync** (last-write-wins).
6. After sign-in, `FirestoreSyncService.syncAll()` runs automatically.
7. **Sign out** keeps local Room data but pauses cloud sync until the user signs in again.

### Spark plan limits (no Blaze)

| Feature | Spark | Used by StudentAI |
|---|---|---|
| Firebase Auth (Google) | Free | Yes |
| Firestore reads/writes | Daily free quota | Sync on sign-in, manual, 6h periodic |
| Cloud Functions | Not on Spark | **Not used** |
| Cloud Storage | Limited free tier | **Not used for sync** |

To stay within free Firestore quotas, the app does **not** sync on every keystroke. Sync triggers:

- Once after successful Google sign-in
- Manual **Sync now** in Profile
- WorkManager periodic job every **6 hours** (network required)
- App launch (Google-signed-in users only, when online)

## MVP — What Syncs Today

| Collection | Firestore path | Entities |
|---|---|---|
| User profile | `users/{uid}` | Display name, school, grade, etc. |
| JEVI decks | `users/{uid}/jevi_decks/{id}` | Deck metadata |
| Flashcards | `users/{uid}/flashcards/{id}` | Cards + SM-2 state |
| Review records | `users/{uid}/jevi_review_records/{id}` | Study history |
| Notes | `users/{uid}/notes/{id}` | Title, content, pins |
| Note tags | `users/{uid}/note_tags/{noteId_tag}` | Tags per note |
| Subjects | `users/{uid}/subjects/{id}` | Academic subjects |
| Schedule | `users/{uid}/schedule_items/{id}` | Weekly schedule |
| Tasks | `users/{uid}/tasks/{id}` | Planner tasks |
| Subtasks | `users/{uid}/subtasks/{id}` | Task checklist items |
| Assignments | `users/{uid}/assignments/{id}` | Assignment tracker |
| Exams | `users/{uid}/exams/{id}` | Exam dates + linked decks |
| Grades | `users/{uid}/grade_entries/{id}` | Grade entries |

## Not Synced (Roadmap)

- **Quizzes / quiz questions** — local AI-generated content; add in v2
- **Study sessions & study plans** — lower priority
- **Calendar events** — derived from tasks/exams; may auto-generate locally
- **AI conversations / chat** — separate backend sync
- **Lecture files** — binary attachments; needs Cloud Storage
- **Notifications** — device-local scheduling
- **Widgets / theme preferences** — per-device UX settings
- **PH holidays cache** — fetched from public API

## Android UX

- **Profile → Cloud Sync:** **Sign in with Google**, signed-in email, **Sign out**, **Sync now**, last synced time, status indicator.
- **Auto-sync:** Lightweight sync on app launch when online + Google signed in.
- **Background sync:** WorkManager periodic job every 6 hours (network required, Google signed in).
- **Offline:** Local Room data always available; sync resumes when online.

## Security

Firestore rules (`firestore.rules` at repo root):

```
match /users/{userId}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

No rule changes are required for Google sign-in — the same UID-scoped paths apply.

Deploy rules:

```bash
firebase deploy --only firestore:rules
```

## Manual Setup (Firebase Console)

1. Enable **Firestore** in your Firebase project (Spark plan is fine).
2. Enable **Anonymous Authentication** (onboarding fallback).
3. Enable **Google** sign-in provider under **Authentication → Sign-in method**.
4. Add your app **SHA-1** and **SHA-256** fingerprints under **Project settings → Your apps → Android** (add to both **StudentAI** and **StudentAI Debug** apps).
   - Debug keystore (this machine, Mar 2026):
     - **SHA-1:** `88:DB:A9:1E:B4:E5:92:9D:68:BA:E7:1F:AC:61:9B:1D:3C:6B:8E:2D`
     - **SHA-256:** `9E:55:D4:3D:EC:20:30:5D:69:88:33:6D:B4:02:E3:79:D1:2B:C1:32:69:8C:06:6F:BE:76:85:08:36:8C:5E:75`
   - Re-check anytime: `.\gradlew :androidApp:signingReport` (set `JAVA_HOME` to a valid JDK if needed)
   - Release: add your release keystore fingerprint when `androidApp/keystore.properties` is configured
5. Download the updated **`google-services.json`** and replace `androidApp/google-services.json`.
   - The file must include non-empty `oauth_client` entries (Android client type 1 + Web client type 3).
   - The Gradle plugin generates `default_web_client_id` at build time — do **not** hardcode it in `strings.xml`.
   - Current web client ID (project-wide): `246011040847-g6pf7tr2o660226btb188frdagp1gi8c.apps.googleusercontent.com`
6. Deploy `firestore.rules` from this repo.

### Verify Google Sign-In config

After step 5, rebuild the app (`Build → Rebuild Project` or `.\gradlew :androidApp:assembleDebug`). Then:

- `androidApp/google-services.json` contains non-empty `oauth_client` arrays.
- Build output includes `default_web_client_id` in `build/generated/res/processDebugGoogleServices/values/values.xml`.
- Profile → Cloud Sync → **Sign in with Google** opens the Google account picker (no “not configured” toast).

If sign-in fails with “no ID token”, SHA fingerprints or the downloaded config are usually missing or stale.

## Key Files

- `FirebaseAuthManager.kt` — anonymous session, Google sign-in, link anonymous → Google, sign out
- `GoogleSignInHelper.kt` — Play Services Auth intent + ID token
- `FirestoreSyncService.kt` — bidirectional sync engine
- `FirestoreEntityMappers.kt` — Room ↔ Firestore maps
- `SyncWorker.kt` — WorkManager background sync
- `ProfileScreen` — Cloud Sync UI in Profile settings
- `UserPreferences.kt` — persisted Google email / linked flag
- `firestore.rules` — user-scoped security rules

## Cross-Device Auth

Use the **same Google account** on phone and tablet. Both devices receive the same Firebase UID, so Firestore data appears on each device after sign-in and sync. Anonymous-only sessions are per-device; link or sign in with Google to enable multi-device parity.
