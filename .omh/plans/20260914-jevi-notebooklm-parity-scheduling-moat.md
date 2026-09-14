# Ralplan: JEVI vs NotebookLM + Scheduling Moat

Date: 2026-09-14 · Lane: Intent → plan (ralplan) · Repo: edukasyon (master @ 365dbc0)
Artifact note: `omh` CLI not on PATH this session → plan written directly to the named `.omh/plans/` location instead of `omh hermes plan --record`. Acceptance = user says go; execution NOT started from this document.

## 1. Observed repo facts (evidence phase)
- Citations ALREADY clickable: `AiScreen.kt:183 onCitationClick → viewModel.openCitation() → SourceViewer` pager (`viewerChunks/viewerIndex`, ViewModels.kt:1032-1044). Cites carry `local:<chunkId>` / `web:<n>` ids.
- AI chat actions auto-apply today: `AiActions.kt executeOne()` handles `add_schedule` (weekly DayOfWeek only), `add_task`, `add_exam`, `add_note` — NO confirmation UI, result only shown as `statusMessage`. `addScheduleItem` + `ReminderScheduler` both already injected in `AiViewModel`.
- SR plumbing partially exists: `Flashcard` has `easeFactor/intervalDays/nextReviewAt`; `Daos.kt` has due-today queries; review recording lives in `JeviRepository(Impl)/JeviUseCases`. Missing: due-today surfaced anywhere, review reminder type (`ReminderType` enum has no REVIEW), scheduler push.
- TTS: ZERO (no `TextToSpeech` anywhere). Voice input: none. Follow-up chips: none.
- `StudyPlan(dayOfWeek, startTime, examId…)` + `AiAnalyzeScheduleUseCase` + `GoogleCalendarSync` exist. `AcademicOverviewCard` shows upcoming exams/tasks — a morning-brief skeleton.
- Reasoning arrives complete-only from backend (`AiChatResponse.reasoning`); `streamingReasoning` field + collapsible UI already committed (365dbc0). True live streaming needs SSE — **evidence gap: hcnsec streaming support unstudied**.
- Providers: free-tier only, no card. edge-tts (MS neural voices via Python pkg) is the card-free backend TTS lane; Cerebras lane is dead for text anyway (402).

## 2. Options & tradeoffs
### A. Tappable citations → enhance only (P0-small)
Rejected: rebuild (exists). Do: highlighted chunk text (bold/underline the cited span in viewer), source-name chips under reply, `web:` cites open in browser. ~1 file each.
### B. "Jevi speaks" Audio Overviews (P0, flagship)
Options: (1) device `TextToSpeech` — offline, free, Huawei voice quality unknown; (2) backend edge-tts on Render → MP3 download + local cache — neural two-voice "podcast" per answer/deck, needs net + ~free-tier CPU; (3) hybrid: single-answer read-aloud on device, deck "Audio Overview" via backend with cache.
**Chosen: (3) hybrid.** Rejected (1)-only: no wow-parity; (2)-only: read-aloud dies offline.
### C. Scheduling agent (P0, the moat — NLM cannot do this)
Extend `AiActionParser/AiActions` with `propose_study_blocks` (dated blocks + reason + which exam/deadline drives it) rendered as a **confirm card** (accept → `addScheduleItem`/task + `ReminderScheduler` + widget refresh; edit time; dismiss). AppContextBuilder already feeds schedule; add exams/deadlines due-window so planning answers ("when should I study for Friday's calc exam?") are grounded. Rejected: separate endpoint (breaks chat-native flow); auto-apply (irritating + today's silent-apply is itself a bug-class).
### D. Spaced repetition surfaced + pushed (P0)
Own SM-2 (fields already there) — rejected: importing lib (weight, and half-implemented). Do: due-today count on JEVI hub + Home; review session entry point filtering `dueForReview` query; new `ReminderType.REVIEW` wired to the new custom-alarm-sound channels (just shipped).
### E. One-tap artifacts (P1)
"Study guide / FAQ" from deck or conversation → markdown rendered in-app + SAF share/export. PDF export: **evidence gap** — repo has PDF *input*; output path unverified (if `pdf` lib absent, ship .md + system print-to-PDF first).
### F. Follow-up suggestion chips (P1)
Backend returns `suggestions: [String]` (≤3) in response schema; chips under reply prefill input. Cheap prompt-line addition, big guided-tutor feel.
### G. Voice input (P2)
`RecognizerIntent`/EXTRA_AUDIO free path, mic button in chat. Rejected: cloud STT (card).
### H. Streaming reasoning (P2, blocked on research)
SSE through hcnsec lane — must research provider support first (in-plan research stage when scheduled, not now).
### I. YouTube/web-URL sources + mind-map (P2)
Deferred; URL-source via backend transcript fetch is next-most-likable NLM parity but quota-risky.

## 3. Risk register
| Risk | Mitigation |
|---|---|
| Render free-tier CPU/timeout for TTS audio gen | ≤2-min clips, queue-in-background, on-device fallback; fail → toast + device TTS |
| Huawei TTS missing voices | detect `TextToSpeech.isLanguageAvailable`, hide read-aloud if absent |
| Offline-first doctrine | audio cache in app files; chip/artifact generation cached per message |
| Free-model quota (hcnsec auto lane) | suggestions+TTS add no extra LLM calls; podcast script = 1 call/deck, cached |
| DB migration (review metadata on quizzes too) | bump `DatabaseMigrations`, nullable columns, existing 57-node + Kotlin tests stay green |
| Widget architecture regression | none of A-F touch widget/ except review-count badge = additive `CheckTileFactory` data only |
| Silent action-apply UX bug (today) | confirm card makes it a feature, not a side effect |
| Cite-viewer highlight perf on long chunks | substring span precomputed server-side in `CitedSource` (string offset), no regex at render |

## 4. Acceptance criteria + verification (per shipped slice)
Build order = waves 1→2 below; each slice: scoped `git diff --stat`, `./gradlew :androidApp:assembleDebug` green, backend `node --test "tests/*.test.js"` 71+ pass, plus:
- **A (citation enhance):** tapping `web:` cite opens browser (Robolectric or manual check-listed); local cite viewer highlights matched span (unit test: offset math on sample chunk text).
- **B (TTS):** device read-aloud speaks + stop/pause on lifecycle (logcat `TTS init status=0`); deck Audio Overview downloads once, plays offline on 2nd launch (file exists in cache dir); airplane-mode: cached decks play, uncached toast.
- **C (scheduler):** "plan my week before Friday calc exam" → proposal card with ≥1 dated block; accept → row in schedule + `ReminderScheduler` scheduled id present in logcat + dismiss leaves DB untouched (Room count test); existing `add_schedule`/`add_task` behavior unchanged (regression grep of `executeOne` branches).
- **D (SR surface):** hub shows due-today count = `SELECT COUNT(*) … due query` vs UI string; completing a review mutates easeFactor/nextReviewAt per SM-2 (unit test with known vectors: quality 5 first review → interval 1→6→15 pattern); REVIEW reminder fires only when due-count > 0 (worker unit test).
- **E/F:** guide .md contains ≥1 citation marker and shares via SAF (manual pass listed); `suggestions` parsed (backend test fixture) and ≤3 chips shown, tap prefills exact text (composable unit test).
- **Verification commands:** `git -C <repo> diff --stat <scoped-paths>`; `./gradlew :androidApp:assembleDebug`; `cd backend && node --test "tests/*.test.js"`; `./gradlew :androidApp:testDebugUnitTest --tests "*<SliceTest>"`.
- Device pass on Huawei remains the single unverifiable-here item (TTS voices, notification sound).

## 5. Rejected / deferred, recorded
- Rebuilt citations (already work); paid cloud TTS/STT (no card); SM-2 library (fields exist); separate scheduling endpoint; full NLM mind-map now (P2).
- Open tradeoffs: podcast voice count (2 vs 1) pending Render load test; PDF export lib pending evidence gap; SSE pending provider research.

## 6. Handoff (prepared_not_observed — NOT started)
On acceptance: ultrawork coordinated lanes, Wave 1 = {A, C-scheduling, D-SR}, Wave 2 = {B-TTS, F-chips, E-artifacts}, same lane discipline as 365dbc0 (build-only subagents, parent integration + assembleDebug fan-in, no auto-push).
