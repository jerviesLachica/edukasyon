# Reviewed plan: AI tutor connected to decks (per-deck conversations)

Status: ACCEPTED by user (2026-09-14). Awaiting explicit go-ahead to build.
Record deviation: `omh` CLI is not installed on this host, so this plan is
recorded at `.hermes/plans/tutor-deck-conversations.md` (repo convention)
instead of via `omh hermes plan --record`.

## Goal (revised per user)
Two separate tutors:
1. **Deck-local tutor, embedded inside each deck** — answers ONLY from that
   deck's topics and cards. Off-topic questions get redirected back to the
   deck, not answered generally.
2. **General AI tutor (Jevi as today)** — unchanged, for general answers.

## Observed repo facts
- `JeviDeck(id/title/subjectId/sourceNoteId/colorHex, cardCount/dueCount/…)`
  — `domain/model/JeviModels.kt`.
- `JeviRepository`: `observeDeck`, `observeDeckFlashcards`,
  `observeDueFlashcards`, `saveFlashcardsToDeck` — deck cards already
  queryable per deck.
- `AiConversation(id/title/type/backendConversationId/createdAt/updatedAt)`
  — **no deck linkage** (`domain/model/AiConversationModels.kt`).
  Types: TUTOR / SUMMARIZE / FLASHCARDS / QUIZ.
- `sendMessage` grounds via `sourceRepository.retrieve(msg, selectedIds, 5)` —
  deck cards are NOT in this path (ViewModels.kt).
- History screen (`AiConversationHistoryScreen`) loads any conversation into
  the shared `AiViewModel`; conversations are unscoped.
- Deck UI lives in `ui/screens/JeviScreens.kt` (+ `JeviViewModels.kt`).
- Chat endpoint input cap is the 32k global (`SAFETY_MAX_INPUT_CHARS`;
  only flashcards/quiz/summarize have the 120k override) — deck grounding
  text must be budgeted (see risks).

## Chosen option: A — embedded deck tutor + untouched general tutor
1. Each deck screen hosts its own tutor thread (embedded panel, not the
   shared Jevi tab). Threads carry `deckId` (new nullable field on
   `AiConversation` + Room migration, default null; old/plain chats
   unaffected).
2. Deck mode grounding = **deck cards exclusively**: due-first, cap ~30
   cards / ~6k chars. Library sources, saved-source retrieval, and auto
   web-search are OFF in deck mode; the deck tutor answers from deck Q/A
   pairs only, with deck-labeled citations.
3. Deck-scoped system steering: stay within the deck's topics; when asked
   something outside, say what deck topics it relates to (or that it's
   outside the deck) instead of answering generally. Exact refusal/redirect
   wording is an implementation detail with one accepted copy pass.
4. Deck screen lists its recent threads; history also filters by deck.
   General tutor (Jevi tab, all existing behavior) is untouched.

## Rejected alternatives
- **B, decks-as-sources only** (deck cards as toggleable Sources entries):
  cheapest, but no conversation separation — fails the stated want.
- **C, auto-conversation per deck**: spams history with empty sessions for
  the same UX as A with more migration surface.

## Risks / critic pass
- **R1 — 32k chat input cap.** History (up to 24k) + deck block can exceed
  `INPUT_TOO_LONG`. Mitigation: hard-cap deck grounding block (~6k chars,
  per-card Q/A trim, due-first); never grow history cap for this.
- **R2 — Room migration.** New nullable column + repository create/update
  paths; old app versions' conversations must open. Mitigation: default null,
  keep all queries backward-compatible; manual upgrade check (no migration
  harness observed in repo).
- **R3 — Context leakage across decks.** Mitigation: grounding block built
  strictly from active `deckId`; acceptance test #3 below.
- **R4 — deck/general separation.** Deck mode excludes library sources,
  saved-source retrieval, and auto web-search by construction (simpler than
  mixing). New sub-risk: auto-search is **server-side** on message text, so
  deck mode needs a per-request opt-out (e.g. `deckMode: true` on the chat
  request → skip web research) — small backend change, included in scope.
- **R5 — scope-steering copy.** The redirect wording (stay-in-deck vs.
  decline) needs one accepted copy pass; model must not silently answer
  generally inside deck mode. Verified by acceptance test #2.
- Open question (implementation detail, not plan blocker): exact
  `AiConversation` Room entity columns + `AiConversationRepository.create`
  signature — read at build time.

## Acceptance criteria (testable)
1. Inside a deck, an embedded tutor thread answers from that deck's cards
   (deck-labeled citations); its threads are listed in the deck and
   resumable.
2. Off-topic question in deck mode → redirected to deck scope, NOT answered
   generally and with no library/web citations attached.
3. General tutor (Jevi tab) behaves exactly as today, including library
   grounding, Sources sheet, and auto-search.
4. Cross-deck isolation: deck A's cards never surface in deck B's tutor.
5. Old conversations (null `deckId`) open and work exactly as today.
6. `./gradlew :androidApp:assembleDebug` green; backend `npm test` 70/70;
   no new permissions; manual device pass on Huawei.

## Verification commands
- `cd C:/Users/HP/AndroidStudioProjects/edukasyon && ./gradlew :androidApp:assembleDebug --no-daemon` → exit 0
- `cd C:/Users/HP/AndroidStudioProjects/edukasyon/backend && npm test` → 70/70
- Device (Huawei, currently offline): deck → ask → cite; history filter;
  cross-deck isolation check.

## Handoff (after acceptance only)
Recommended follow-on: `ultrawork` single-owner persistence — one already-
scoped task, single owner, disjoint files
(`AiConversationModels`, Room entity/migration, `AiConversationRepository`,
`ViewModels` send path, `JeviScreens` entry, history filter). Starts only on
your explicit go-ahead — say "go" and I dispatch it; say "revise" with notes
and I update this plan first.
