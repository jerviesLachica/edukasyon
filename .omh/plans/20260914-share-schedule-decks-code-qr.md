# Ralplan: Share Schedules & Decks via Private Code / QR

Date: 2026-09-14 · Lane: Intent → plan (ralplan) · Repo: edukasyon (master @ f92da57)
Artifact note: `omh` CLI not on PATH this session → plan written directly to the named `.omh/plans/` location. Acceptance = user says go; execution NOT started from this document.

## 0. Requirement (as stated)
Let users share their already-built **schedule** and **JEVI decks** with classmates via a **private code or QR**, so the recipient imports a ready-made copy instead of running AI scan or manual entry. **Schedule import must play the existing timetable-populate animation** ("the schedule's animation when adding the schedule").

## 1. Observed repo facts (evidence phase — all verified this session)
- Animation EXISTS and is decoupled: `ui/components/ScanningOverlay.kt:397 TimetablePopulateAnimation(classes: List<ExtractedClass>, onComplete)`; driven by `ScheduleScanStatus.CONFIRMING` + `classesBeingImported` (ViewModels.kt:2349 `confirmScannedClasses()` → `dismissPopulateAnimation()`). Reusing it for share-import = set the same state with mapped classes. No new animation code needed.
- `ExtractedClass` (AiService.kt:53): subject/teacher/room/day/startTime/endTime — the exact shape the animation + import path consume.
- Persistence paths exist: `addScheduleItem` use case (ScheduleItemEntity `Daos.kt:61 insert`), `JeviDeckEntity` (`Entities.kt:184`, `Daos.kt:248 insert`), `FlashcardEntity` (`Entities.kt` flashcards, `Daos.kt:316 insert`, linked by `deckId`). Widget refresh already rides the `addScheduleItem` path.
- Firebase IS in the app: `FirebaseAuthManager` (anonymous auth supported — `isAnonymous`, `linkedFromAnonymous`; `firebase.json` has anonymous+google enabled), `FirestoreSyncService`, `firestore.rules` (currently locks everything under `/users/{userId}` — no share collection exists yet), firebase CLI 15.14.0 installed locally.
- Camera + ML Kit already shipped (`camera-* 1.3.4`, `text-recognition 16.0.1`); **no barcode/QR lib** (zxing absent; `mlkit:barcode-scanning` would be new, ~1 MB).
- `BackupData` JSON shapes (schedule + flashcards) prove serializable payloads; `@Serializable` kotlinx already in use.
- Deep links: MainActivity has intent-filters (widget/alarm) but **no custom app scheme** yet — `schedmate://` filter would be added.
- minSdk 24. Android unit tests exist (`androidApp/src/test/kotlin/...`, junit + coroutines-test + room-testing deps). Backend node suite (88 tests) unaffected — this feature doesn't touch the Render backend.

## 2. Options & tradeoffs
### Transport — the real decision
**Chosen: B. Private code + Firestore `shares/{code}` collection (QR = the code, rendered as a `schedmate://share/<6-char-code>` deep link).**
Publisher uploads payload JSON to `shares/<code>` (30-day expiry, view count, creator uid if signed); recipient enters the code OR scans the QR in-app OR uses the OS share sheet (text "SchedMate code: X7K2PQ"). Fetch → preview → import.
- Rejected A: *QR-only, fully offline payload.* Schedule fits ~40 classes; a **deck with 100+ cards = 10–50 KB → impossible in one QR**, needs animated multi-frame QR chunking (major complexity, fragile camera UX). Deck sharing dies.
- Rejected C: *Render backend share table.* Extra endpoint + DB surface on the free cold-start tier; Firestore is already provisioned, auth'd, and the sync engine — sharing is a Firestore-shaped problem (small ephemeral docs).
- Rejected D: *file/attachment share (Drive, .bak)*. Backup JSON already exists for self-transfer; wrong primitive for classmate-to-classmate.
### Deck content shape
Chosen: ship deck metadata + cards (question/answer/topic/difficulty); **strip owner SRS state** (review counts, ease, nextReviewAt recomputed fresh for the recipient). Rejected: preserving progress (privacy: reveals study history; pointless: recipient's memory differs).
### QR generation
zxing `core` only (jvm/kotlin, no camera, tiny) → BitMatrix → Compose canvas. Rejected: ML Kit barcode *encoding* (heavier). For *scanning* the QR: add `com.google.mlkit:barcode-scanning` (unbundled, works without GMS on Huawei like the already-used text-recognition) + keep manual code entry as zero-dep fallback.

## 3. Design (file-backed detail for the executor)
New `core/share/`:
- `SharePayload.kt` — `@Serializable` sealed content: `ScheduleShare(classes: List<SharedClass>)` (fields == ExtractedClass) | `DeckShare(title, description?, colorHex, cards: List<SharedCard(question, answer, topic?)>)`; versioned envelope `{v, kind, createdAt, payload}`.
- `ShareCode.kt` — 6-char Crockford-style alphabet `ABCDEFGHJKMNPQRSTVWXYZ23456789` (no I/L/O/U/vowels-confusables), crypto-random, profanity denylist re-roll; collision check via Firestore get before upload.
- `ShareRepository.kt` — publish() writes `shares/{code}` = `{ kind, payloadJson, createdBy?, createdAt, expiresAt(+30d), views:0 }`; redeem() = get + `expiresAt>now` + one-shot `views+1` best-effort; sizes guarded at publish (≤60 classes, ≤500 cards) with typed errors.

Publish UI: schedule screen overflow "Share schedule" + JEVI deck detail "Share deck" → `ShareSheetDialog` (big monospace code, copy, QR bitmap, system share text).
Redeem UI: Settings/Schedule entry "Redeem share code" → code field **or** "Scan QR" (cameraX PreviewView pattern copied from ScheduleScannerScreen + barcode analyzer) → `RedeemPreviewScreen` (what's inside: "14 classes Mon–Fri" / "Calculus deck — 48 cards", sender optional) → Import:
- schedule: map `SharedClass → ExtractedClass`, set `CONFIRMING + classesBeingImported` → **TimetablePopulateAnimation plays exactly as AI-scan import** → persist via same `importClasses()` extracted from `confirmScannedClasses()` (single shared function; scan path behavior unchanged); dedupe exact (subject,day,start,end) collisions against existing rows, report "imported N, skipped M".
- deck: insert deck + flashcards (fresh SR fields, new UUIDs), then navigate to deck.
Deep link: MainActivity `schedmate://share/<code>` intent-filter → routes to redeem preview (only useful post-install; code entry covers cold start).

Firestore rules (add; `/users` block untouched):
```
match /shares/{code} {
  allow read: if request.time < resource.data.expiresAt;
  allow create: if request.auth != null && isValidShareDoc();   // size/alphabet/kind checks
  allow update: if request.auth != null && resource.data.views == 0 && request.resource.data.views == 1; // first redeem only, best-effort
}
```
Security model = unguessable capability (30^6 ≈ 729M codes, 30-day expiry) + free-tier read volume guard; explicit UI warning "anyone with the code can view until it expires."

## 4. Implementation slices (waves)
1. **Core + tests, no UI**: SharePayload/ShareCode/ShareRepository + unit tests (code alphabet/denylist, JSON round-trip, expiry logic, size guards). Room/instrumental dedupe test for `importClasses` extraction (regression: scan-confirm still animates).
2. **Publish flow**: zxing dep, QR canvas composable, ShareSheetDialog, both entry points.
3. **Redeem flow**: barcode dep + scanner screen, code entry, preview, import with animation (schedule) / insert+navigate (deck), deep-link filter.
4. **Rules + deploy + changelog**: firestore.rules change, `firebase deploy --only firestore:rules` (user confirms project edukasyon-studentai), ChangelogRepository entry.

## 5. Risk register
| Risk | Mitigation |
|---|---|
| Rules mistake widens `/users` data | only additive `/shares` block; dry-run rules review; unit-free manual: unauth read of a users doc must 403 |
| Code enumeration / quota | 6-char 30^6 space + 30d expiry + Firestore free-tier read ceiling; views counter for anomaly signal |
| Huawei lacks camera/ML model | code-entry fallback is first-class, never QR-only |
| Widget/scheduler regressions | reuse `addScheduleItem`/existing insert; no Room schema change (zero migration); `importClasses` extraction is behavior-identical, guarded by slice-1 test |
| Duplicate import (user shares to self/twice) | additive + (subject,day,time) dedupe report |
| Deck names leak via brute preview | payload only fetched after full correct code (get-by-doc, no listing) |
| Anonymous publisher | allowed (create needs `request.auth != null`; anonymous auth exists); shares are ephemeral, no ownership claim needed |
| Payload size abuse (500-card deck) | publish-side guards + rules `isValidShareDoc` byte cap |

## 6. Acceptance criteria (testable)
1. Publish schedule → Firestore `shares/<code>` doc exists, code shown, QR renders, copy/share works; same for a deck (cards included, SRS state absent). **Verify:** emulator + Firestore console (manual) or firebase emulator.
2. Redeem by typing the 6-char code shows correct preview (class/day summary; deck title/card count).
3. Schedule import **plays `TimetablePopulateAnimation`** then inserts rows (widgets refresh); re-importing the same code skips exact duplicates and reports counts.
4. Deck import creates a working deck (cards visible, review queue schedules from scratch).
5. Expired/invalid code → clear error, no crash.
6. Existing AI-scan import path is unchanged (animation + dedupe regression covered by tests).
7. `/users/**` rules still reject unauth access; `/shares` readable by unauth clients, writable only per rules.

## 7. Verification commands (exact)
- `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL
- `./gradlew :androidApp:testDebugUnitTest --tests "*Share*"` → new tests green; plus full `testDebugUnitTest` no-regressions
- `firebase deploy --only firestore:rules --project edukasyon-studentai` (slice 4; user's go; CLI 15.14.0 present)
- Manual pair-check on 2 devices/emulators: publish on A → redeem on B (schedule animation observed).
- Backend untouched → `cd backend && node --test "tests/*.test.js"` still 88/88 (sanity).

## 8. Evidence gaps (recorded, not flattened)
- ML Kit `barcode-scanning` size/Huawei behavior: assumed parity with already-working text-recognition; not yet verified → slice-3 spike on emulator before committing to it (code-entry fallback makes it non-blocking).
- Firestore spark-tier free read quota vs. guessed codes: quota numbers are from memory, not re-verified this session; mitigated anyway by expiry + doc-get-only access.
- zxing BitMatrix → Compose Canvas draw perf: trivial, but unverified in this codebase (no existing QR rendering).

## 9. Planner view / critic pass
- Critic: "why not just share the backup JSON?" — Backup needs a file round-trip and replaces data wholesale; classroom ask is *merge-in one classmate's timetable*, which the additive `importClasses` path serves. "Why not wait for offline QR?" — deck sizes kill it (A rejected on facts).
- Tradeoff kept open: in-app camera scan (adds ML Kit barcode dep) vs. shipping code-entry-only first and scanning in wave 3B. Plan says wave 3; if dep size surprises, split it out — executor may defer QR *scanning* while never deferring QR *display*.
- Testability check: AC 1–3, 5–7 have automated or emulator-observable verification; AC 4 automated via Room + repo tests.

## 10. Handoff (prepared, not started)
On user acceptance → `ultrawork` **coordinated lanes** fit (4 slices with disjoint file ownership: core+tests / publish UI / redeem UI / rules+changelog), mirroring the waves-1/2 pattern already used in this repo. Executor handoff: slices 1→4 in order, master tree only (`.claude/worktrees/*` forbidden), commit per slice, no push without approval.
