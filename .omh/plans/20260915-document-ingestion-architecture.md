# Ralplan: Vision-First Document Reading — flashcards & quizzes from PDFs/images

Date: 2026-09-15 (rev 2 — user directive: "OCR doesn't get the full context, I need a fast AI to do the task") · Lane: Intent → plan (ralplan) · Repo: edukasyon (master @ 36090aa)
Acceptance = user says go. No code changed from this document.

## 0. What changed in rev 2
Rev 1 made device ML Kit OCR the primary reader with vision only as fallback. **User rejected that**: OCR loses structure (math, tables, figure labels, layout) — the exact context cards/quizzes need. Revised decision: **a fast vision AI reads every page that isn't born-digital text**; device OCR is demoted to an optional hint channel (the proven schedule-scanner pattern: "OCR text (hint only — image is source of truth)").

## 1. Observed current pipeline (verified in-tree 2026-09-15)
- `PdfOcrHelper`: embedded-text fast path (keep — lossless & instant); else PdfRenderer ≤5 pages → serial ML Kit Latin OCR → silent null. Pages 6+ and non-text content are lost *before* the AI runs.
- `JeviViewModels.generateFromDocument`: `text.take(4000)` (L598/L809) — 90%+ of a lecture deck deleted pre-AI.
- Backend `/api/ai/flashcards`: 10k chunks ×6–8 cards, concurrency 3, cap 60. `/api/ai/quiz`: one shot, no chunking.
- Fast-vision lanes ALREADY EXIST and are in production use: hcnsec `nemotron-3.5-lightning-free` (default vision model, AiProvider.js:29), NVIDIA NIM scan lane w/ job+poll (`resolveScanProvider`, proven by schedule-analysis), Orca `glm-5.3-flash-free` as override lane. Vision cost observed: schedule scan 45–75s was MiniMax-M3 (slow lane) — lightning/NIM lanes are the fast family.
- OCR-hint pattern proven: schedule-analysis sends image + ≤900 chars OCR hint, image authoritative (server.js:499-508).
- Two disjoint ingestion pipelines (cards text vs chat `ingestSource` chunks+embeddings).

## 2. Options (rev 2)
**A. OCR-first, vision fallback — REJECTED by user.** Threshold-tuning hell; the failure mode (garbled math/tables) is precisely what cards need.
**B. Whole-document single vision call — REJECTED.** Multi-image-per-request support unverified on free lanes; one long request is the slowest serial path and worst quota shape.
**C. Chosen: Vision-first page fan-out.**
1. **Text PDF path (free, instant):** embedded extraction when PDF has real text layers (most syllabi). Unchanged.
2. **Vision path (fast AI):** every non-text page rendered → parallel `fast-vision` calls (concurrency 3, backoff to 2 on 429) that transcribe **structured page notes** — markdown preserving headings, tables, formulas (LaTeX), and figure captions — NOT plain OCR. Per-page provenance is built in.
3. **Merge pass (cheap text model):** page notes concatenated → the improved existing map-reduce for cards; quiz gets the same map-reduce + dedupe/coverage merge. Cards/quizzes cite `page` provenance.
4. ML Kit OCR: optional hint only (off by default; flag to enable) — no longer the primary reader.
5. Results **stream per page**: progress is "3/12 pages read" and preview fills as pages land (perceived speed > raw speed).
Same page-notes artifact feeds `ingestSource` → JEVI chat cites the same document (one extraction, all features).

## 3. Speed budget (why this is fast, concretely)
- Text PDF: seconds (unchanged path).
- 12-page scanned deck: 12 parallel-3 vision calls ≈ 4 waves × 6–15s (lightning-class) ≈ **30–60s total**, streaming from wave 1. Rev-1 OCR-fallback worst case was worse *and* wrong.
- Second import of identical bytes: sha256 cache → 0 AI calls.
- Ceiling: MAX_VISION_PAGES configurable (default 12, hard 20) with explicit user warning instead of silent truncation.

## 4. Risks (vision-first)
| Risk | Mitigation |
|---|---|
| Free-lane 429 under parallel vision | concurrency 3→2 backoff, jittered retry ×2, then serial; queue job+poll lane (proven) for spillover |
| nemotron/NIM per-page latency variance | measure in spike-0 (first task: 3 real pages, time p50/p95) BEFORE UI work; NIM scan lane is the known-fast fallback |
| Multi-image-in-one-request support unknown | not load-bearing — design is one-page-per-request; multi-image is a later optimization |
| Page-local context (term defined p.3, quizzed from p.9) | merge pass sees ALL page notes; cards may cite multiple pages; dedupe in merge |
| Cost: N vision calls per doc | capped pages, cache, text-path first, no re-read on edit-then-regenerate (regenerate reads cached notes only) |
| Big render memory | downscale ≤1600px JPEG q80 immediately, recycle, semaphore 2 concurrent renders |
| Regression to JEVI chat sources | page notes → existing `ingestSource` contract, additive |
| Scanned-page hallucination by small models | "transcribe only what is visible, mark unreadable as [?]" prompt discipline + provenance chips so users can spot-check page refs |

## 5. Implementation slices
1. **Spike-0 (half day):** live-time nemotron-3.5-lightning + NIM lane on 3 fixture pages (math slide, table, diagram) — pick primary; record p50/p95/429 behavior. Gates 2–3.
2. **Backend `handlePageNotes` + improved cards/quiz:** new `/api/ai/page-notes` (image → structured markdown, no OCR-hint needed), quiz map-reduce+merge, per-item validators, page provenance fields; node tests incl. fixture transcripts (checked-in) so parsing is tested without live AI.
3. **Android `DocumentPipeline`:** render→upload fan-out (coroutines, semaphore, backoff), per-page cache (Room), progress Flow, Hilt-injected (kills the two manual `remember{PdfOcrHelper(...)}` sites), remove `take(4000)`, page chips in card list; JVM tests for cache/router/dedupe; schedule-analysis untouched.
4. **QA + changelog:** fixture trio E2E on emulator + Huawei (12p text, 12p mixed, 6p scanned), timing capture, cache second-run assert, TTS-404 ops note stays separate.

## 6. Acceptance criteria (testable)
1. 12-page scanned fixture → 12 page-note blocks, **zero pages silently dropped** (cap warns explicitly).
2. Page notes for formula/table fixtures preserve LaTeX + table structure (golden-file test vs spike transcripts).
3. Cards from fixture cite ≥3 distinct pages; regenerated cards re-read 0 images (cache hit; log assert).
4. Quiz over 20k-char notes returns ≥5 questions from ≥2 chunks' coverage; malformed-item injection degrades gracefully (backend test).
5. E2E scanned-deck import: first cards visible ≤20s, complete ≤90s on emulator; text PDFs ≤5s; second identical import <1s.
6. `take(4000)` gone; payload to generation = full text ≤60k (asserted unit test).
7. All suites green: `testDebugUnitTest`, backend `node --test`.

## 7. Verification commands
- `cd backend && node --test "tests/*.test.js"`
- `./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest`
- Spike report file `.omh/runs/20260915_vision-spike.md` (timings pasted, observed not claimed)
- Emulator E2E: logcat TAG=DocumentPipeline (wave timings, cache hits)

## 8. Evidence gaps (open, recorded)
- nemotron-3.5-lightning per-page p50/p95 + 429 behavior on free account → spike-0 closes this before any build.
- NIM scan lane model name/quotas currently configured on Render (resolveScanProvider) — read at spike time.
- Whether per-page markdown survives PDF pages with running headers (dedupe in merge handles it, measure in fixtures).

## 9. Handoff (prepared_not_observed)
On "go": ultrawork coordinated lanes — spike-0 first (blocks 2–3 selection), then backend | android in parallel post-decision; fan-in build+tests; device QA; commit per slice; no push/deploy without approval.
