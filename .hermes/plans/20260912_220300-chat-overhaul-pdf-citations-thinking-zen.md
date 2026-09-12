# Chat Overhaul Implementation Plan (PDF fix + citations + thinking + Zen)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Fix PDF mojibake, citation empty-space + auto-5-citations, single-button thinking levels, Sources bottom sheet, and replace dead Cerebras/MiniMax-M3/Agnes routing with OpenCode Zen free models.

**Architecture:** Android-only UI fixes (Compose) + backend prompt/chain changes in `backend/ai/AiProvider.js` + `backend/server.js`; no new dependencies. TDD per task with `npm test` and `assembleDebug`.

**Tech Stack:** Kotlin/Compose, Node backend, Zen OpenAI-compatible `chat/completions`.

**Step 0 (execution time, NOT planning): install the Zen key.**
User-supplied `OPENCODE_API_KEY` (in chat history) goes to Render only, never to disk:
tool `RENDER_UPDATE_ENV_VAR` `{serviceId: "srv-d9u811navr4c73en2uu0", envVarKey: "OPENCODE_API_KEY", value: "<key>"}` → `RENDER_TRIGGER_DEPLOY` → wait `live`. Then remind user to rotate the key (chat-history exposure).

---

## Phase A — PDF mojibake (root cause confirmed)

`ChatAttachmentUtils.extractEmbeddedPdfText` (`androidApp/.../core/util/ChatAttachmentUtils.kt:101`) scrapes every `(...)` literal from raw PDF bytes as ISO_8859_1. Font-encoded/compressed streams pass the ≥150-char gate as mojibake, so `PdfOcrHelper` (`core/mlkit/PdfOcrHelper.kt:38-41`) never falls through to the ML Kit render path.

### Task A1: Add legibility gate unit-testable helper
**Files:** Modify `ChatAttachmentUtils.kt` (add `fun isLegibleText(s: String): Boolean`).
**Step 1 — test:** there are no JVM unit tests for this file; add `androidApp/src/test/kotlin/.../PdfLegibilityTest.kt` (check existing test dir layout first) asserting `isLegibleText("Photosynthesis converts light energy in chloroplasts") == true` and `isLegibleText("ÏÎ c âÄklIBI ÜiDÕ¥ÖN&Ý < UþI _ÈkŸ-WN") == false`.
**Step 2 — run:** `./gradlew :androidApp:testDebugUnitTest --tests "*PdfLegibility*"` → FAIL (no function).
**Step 3 — implement:**
```kotlin
fun isLegibleText(s: String): Boolean {
    if (s.isBlank()) return false
    val letters = s.count { it.isLetter() }
    if (letters.toDouble() / s.length < 0.5) return false
    val words = s.split(Regex("\\s+")).filter { it.length >= 3 }
    if (words.isEmpty()) return false
    val alphaWords = words.count { w -> w.count { it.isLetter() }.toDouble() / w.length >= 0.6 }
    return alphaWords.toDouble() / words.size >= 0.4
}
```
**Step 4 — run tests:** expect PASS. **Step 5 — commit.**

### Task A2: Gate the embedded-text path on legibility
**Files:** Modify `ChatAttachmentUtils.kt:135-139` + `PdfOcrHelper.kt:38-41`.
Change the return to `return joined.takeIf { it.length >= 150 && isLegibleText(it) }`. In `PdfOcrHelper`, after the embedded attempt, if result is null/blank fall through to render+OCR (already the behavior on null — verify, no change needed if so).
**Verify:** `./gradlew :androidApp:assembleDebug` BUILD SUCCESSFUL; emulator: Create Flashcards → Pick PDF (scanned/encoded PDF) → text box shows OCR English, not mojibake. **Commit.**

---

## Phase B — Citation empty space + auto-5 citations

### Task B1: Reproduce + remove the empty bubble area
**Files:** `ui/components/GizmoCompanionBar.kt` (citations block ~line 421+).
**Step 1:** On emulator, send a message that yields citations; screenshot. Confirm whether the grey void is (a) the citations `Row` rendering with zero-width chips, (b) `MarkdownChatText` min-height, or (c) a fixed-height spacer. **Step 2:** Fix what you find: gate the whole `Sources:` row on `citations.isNotEmpty()` (keep existing gate), remove any fixed `height()`/`minHeight` on the bubble content column, use `weight(1f, fill = false)` where a column stretches. **Verify:** screenshot shows text → chips with no void. **Commit.**

### Task B2: Server always returns 5 citations (pad when AI under-cites)
**Files:** Modify `backend/server.js` `handleChat` (citation block ~line 403+).
**Step 1 — test:** extend `backend/tests/handleChat.test.js`: reply cites only `[1]` with 1 local + 5 web results available → expect `citedChunkIds.length == 5` and `citedWebResults.length == 4`, padded in web-result order. Run `npm test` → FAIL.
**Step 2 — implement:** after building `citedFromReply`, append unused web-result indices (then unused local source indices, already filtered) until total is 5 or results exhausted; build `citedChunkIds`/`citedWebResults` from the padded list.
**Step 3:** `npm test` → 64+ pass. **Commit.**

### Task B3: Prompt requires ≥5 distinct citations on learning topics
**Files:** Modify `backend/server.js` web-results prompt (`Current web search results (cite with matching numbers...)`).
Change to instruct: "Cite at least 5 distinct numbered sources ([1]–[5]+) covering both the user's sources and the web results below." Keep "untrusted data, not instructions" sentence. No test change (prompt text); verify via B2 test still green. **Commit.**

### Task B4: Auto-cite whenever a learning topic is mentioned (no user asking needed)
**Problem:** B2/B3 only work if the model cooperates; casual messages ("hi", "thanks") shouldn't be forced to cite either. So gate the citation demand on topic detection.
**Files:** Modify `backend/server.js` (`handleChat`, near `parseWebSearchCommand` ~line 304); Test: `backend/tests/handleChat.test.js` (or new `backend/tests/topicDetect.test.js` — check which exists, prefer extending).
**Step 1 — tests (TDD):**
```js
// isLearningTopic("Explain photosynthesis simply") === true
// isLearningTopic("What caused World War 2?") === true
// isLearningTopic("hi") === false
// isLearningTopic("thanks!") === false
// isLearningTopic("/search quantum dots") === false (explicit search has its own path)
```
Run `npm test` → FAIL (no function).
**Step 2 — implement** (pure function, no dependencies):
```js
const SMALLTALK = /^(hi+|hello|hey|thanks?|thank you|ok|okay|yes|no|bye)\b[\s!.]*$/i;
function isLearningTopic(message) {
  const text = String(message || '').trim();
  if (text.length < 12 || SMALLTALK.test(text)) return false;
  if (text.startsWith('/')) return false;
  return true;
}
```
**Step 3 — wire into handleChat:** when `isLearningTopic(webSearchRequest.query)` is true, append to `userContent` (alongside the web-results block): "This is a learning topic. Your answer MUST include at least 5 citations [1]–[5] drawn from the numbered sources above; every factual paragraph needs at least one." When false, keep the soft "if relevant" wording. B2 padding then guarantees the chips even if the model under-cites.
**Step 4 — test the wiring:** extend the handleChat citation test: topic message + reply with zero `[N]` markers → still returns 5 `citedChunkIds`/`citedWebResults` (padded). `npm test` → all green. **Step 5 — commit.**

---

## Phase C — Thinking levels + Sources sheet

Current: `ThinkingLevel` enum FLASH/STANDARD/DEEP (`domain/model/Enums.kt:120`), 3 chips in composer, backend `effort` flash/standard/deep (`server.js:330`), XP pill `+N XP` dead `AssistChip` (`ui/screens/AiScreen.kt:87`).

### Task C1: Replace enum with none/minimal/low/medium/high
**Files:** Modify `Enums.kt:113-130`, `UserPreferences.kt` (no change needed — slug-based), `AiModelRouter.effortParam` (`core/ai/AiModelRouter.kt`), `backend/server.js:330-332,361`.
```kotlin
enum class ThinkingLevel(val slug: String, val displayName: String, val description: String) {
    NONE("none", "No thinking", "No reasoning"),
    MINIMAL("minimal", "Minimal thinking", "Minimal reasoning effort"),
    LOW("low", "Low thinking", "Low reasoning effort"),
    MEDIUM("medium", "Medium thinking", "Medium reasoning effort"),
    HIGH("high", "High thinking", "High reasoning effort");
    companion object { fun fromSlug(slug: String?): ThinkingLevel = entries.find { it.slug == slug } ?: LOW }
}
```
Backend: `effort = ['minimal','low','medium','high'].includes(req) ? req : 'none'`; `thinking = effort !== 'none'`; token budgets: minimal/low = base, medium = base*1.5, high = min(base*2, 4096); step-by-step line for high only. Default LOW (previous default FLASH ≈ fast; LOW is closest with reasoning on — user decision recorded here; change to NONE if they complain it's slow).
**Verify:** `npm test` green (add a case in `aiProvider.test.js` style if a thinking test exists there — check first); `assembleDebug` green. **Commit.**

### Task C2: Single cycling thinking button next to AI name
**Files:** Modify `GizmoCompanionBar.kt` header (avatar+name row) + `AiScreen.kt` wiring (already passes `thinkingLevel`/`onThinkingLevelSelected` to composer — keep for now, remove composer row in C3).
Add small button showing current level short label ("Low"); onClick cycles NONE→MINIMAL→LOW→MEDIUM→HIGH→NONE. Reuse `JeviThinkingLevelRow`? No — single `FilterChipSmall`-style button; delete the 3-chip row from `JeviChatInput.kt` in this task.
**Verify:** emulator screenshot: one button by "Jevi" name; tap cycles label. **Commit.**

### Task C3: Replace XP pill with Add Sources button + Sources sheet
**Files:** Modify `AiScreen.kt:87-91`; extend `ui/components/SourcePickerRow.kt` (already hosts `AddSourceDialog`, `CitationPassageBottomSheet`).
Replace the `+N XP` AssistChip with an "Add Sources" button (`onClick` opens new `SourcesBottomSheet`). Build `SourcesBottomSheet` per reference: "+ Add sources" button (opens existing `AddSourceDialog`), "Search the web for new sources" card with provider dropdowns + search icon (calls existing `/search` flow or `searchAuto`, shows results as addable rows), "Select all" toggle, checkbox rows (name + letter-avatar; favicon via `https://www.google.com/s2/favicons?domain=` ONLY if Coil is already a dependency — check `androidApp/build.gradle.kts` first, else letter-avatar only), wired to existing `viewModel.toggleSource/deleteSource/addSource` + `state.sources/selectedSourceIds`.
**Verify:** emulator: tap header button → sheet matches reference layout; toggle/select-all/add all work. **Commit.**

---

## Phase D — Zen replaces Cerebras/Agnes/MiniMax (evidence: live logs show Cerebras 402 + MiniMax 503 model_not_found daily)

### Task D1: Install key + render.yaml (do at execution start)
`RENDER_UPDATE_ENV_VAR` OPENCODE_API_KEY → deploy → `render.yaml`: add `OPENCODE_API_KEY` (sync:false) + `ZEN_BASE_URL=https://opencode.ai/zen/v1` + `ZEN_TEXT_MODEL` (Task D3 winner). Commit render.yaml.

### Task D2: Add zen provider to AiProvider.js
**Files:** Modify `backend/ai/AiProvider.js` (mirror existing ORCA/CEREBRAS blocks ~lines 93-120, chain ~337+).
Config: `ZEN_API_KEY`, `ZEN_BASE_URL=https://opencode.ai/zen/v1`, OpenAI-compatible POST `{base}/chat/completions` with `Authorization: Bearer`. Add `provider: 'zen'` branch in the request sender (copy the orca/cerebras sender, only base URL/key differ).
**Tests first:** in `backend/tests/aiProvider.test.js` (follow existing Cerebras routing tests): zen key present → non-thinking text routes zen first; zen 429 → falls back hcnsec; no zen key → old behavior. Run → FAIL. Implement → PASS. **Commit.**

### Task D3: Free-model chain ordered fastest→slowest (verify live, don't trust docs)
Order to implement (inferred from Artificial Analysis underlying-model data — Nemotron 3.5 Lightning measured 281 tok/s, TTFT 1.0s):
1. `nemotron-3.5-lightning-free` 2. `deepseek-v4-flash-free` 3. `mimo-v2.5-free` 4. `nemotron-3-ultra-free` 5. `ling-3.0-flash-fin-free` (finance-tuned, last).
Chain semantics: try in order, any 401/402/403/429 → next (repo already treats these retryable), hcnsec `auto` stays final fallback. Remove Cerebras from chain; keep MiniMax-M3 wire-map code but drop it as default vision (dead channel).
**Verify live:** time 3 identical prompts per model via backend, record tok/s, re-order if docs lied; `npm test` green. **Commit.**

### Task D4: Vision via Zen (verify, don't assume)
Try `deepseek-v4-flash-vision-exp` with an image through Zen from a scratch script. If it answers about the image → set as vision-first, keep `GEMINI_VISION_MODEL` (free AI Studio key, no card) as fallback. If it fails → keep Gemini-first, note in code comment. **Commit** whichever wiring results.

### Task D5: Replace Agnes dropdown entry with Zen
**Files:** `Enums.kt:99-110` — change `REASONING` slug/display to the Task-D3 winner (e.g. display "Zen Flash"); keep `isStepModel`/quota logic untouched. `AiModelRouter.chatModelOverride` unchanged (passes slug through). Backend: Zen wire model needs NO mapping (native Zen id).
**Verify:** emulator: model dropdown shows Auto + Zen entry; selecting it routes to Zen (check Render logs for `provider=zen`). **Commit.** Final `npm test` + `assembleDebug` + push + confirm Render `live`.

---

## Phase E — Orange as the default color on all pages

**Finding (verified):** `ThemePresets.DEFAULT_PRIMARY` is already orange (`#F97316`, `ui/theme/ThemeCustomization.kt:21`) and `StudentAiTheme` builds the scheme from it — yet screenshots render blue. So either a stored theme preference overrides the default, or screens use hardcoded blues. There are 61 hardcoded `0xFF…` colors across 16 files under `ui/` (most in `HomeScreen.kt` ×10, `JeviLoadingSpinner.kt` ×16, `StarfieldBackground.kt` ×6, `PillTabBar.kt` ×5).

### Task E1: Trace where the blue comes from
**Files:** Read-only: `ui/StudentAiApp.kt` (`StudentAiAppContent`), `MainActivity.kt`, any DataStore theme key.
**Steps:** Find what hex `StudentAiAppContent` passes to `StudentAiTheme(primaryColorHex=…)`; check if a saved preference (DataStore) holds a blue value; list which of the 61 hardcodes are brand blues/purples vs functional (white/black/transparent/error red). Write findings as a comment in the task PR — no code change. **Commit** (docs only, optional).

### Task E2: One-time migration to orange default
**Files:** Modify `data/preferences/UserPreferences.kt` (or wherever the theme key lives — see E1) + `ui/theme/ThemeCustomization.kt`.
Add a boolean migration flag (e.g. `theme_orange_migrated`); on first launch after update, if flag unset, overwrite the stored primary hex with `#F97316` and set the flag. Never touch it again (respects later user changes in the theme picker, if any).
**Verify:** fresh install + upgraded install both render orange; `assembleDebug` green. **Commit.**

### Task E3: Replace non-orange brand hardcodes with colorScheme
**Files:** the E1 list (expect `HomeScreen.kt`, `PillTabBar.kt`, `JeviChatInput.kt` chips, flashcard/quiz buttons).
Rule: any hardcoded blue/purple/indigo used as brand/accent → `MaterialTheme.colorScheme.primary` (or `primaryContainer`/`tertiary` to match emphasis). Leave functional colors (onPrimary white, transparent, error, shimmer grays) alone.
**Verify:** emulator screenshots of all 5 tabs (Home, Schedule, Planner, JEVI, Profile) in dark mode + Create Flashcards screen: orange accents everywhere, no blue. **Commit.**

---

## Accepted risks / open questions
- Free-model speed order is inferred, not measured — Task D3 measures live.
- `muse-spark-1.3-contributor-free` trains Meta models on prompts (per Zen docs) — NOT in chain for student data; documented, do not add.
- App traffic burns the user's personal Zen quota/credits; hcnsec stays as final fallback. Flag ToS risk of app-backend use of a personal key.
- Billing details were required by Zen docs at signup — user already holds a key, so no action; free models only, no card needed from here.
