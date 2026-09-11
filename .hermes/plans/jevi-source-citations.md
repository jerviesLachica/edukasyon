# Plan: JEVI source citations (NotebookLM-style) — DRAFT, not accepted

## 1. What NotebookLM actually does (researched 2026-09-11)

NotebookLM (renamed Gemini Notebook, July 2026) is a source-grounded RAG pipeline,
not a different kind of model. Four stages per answer:

1. **Ingestion** — PDFs, Docs, URLs, YouTube transcripts, audio, text are sliced
   into overlapping chunks (typically a few hundred to a few thousand tokens each).
2. **Embedding** — each chunk → meaning vector (Gemini embeddings), stored in an index.
3. **Retrieval** — the user question is embedded the same way; top-N nearest chunks
   found by MIPS / vector similarity search.
4. **Grounded generation** — retrieved chunks + question go to the LLM (Gemini family)
   with an instruction to base the answer on the material; a citation number is
   attached per sentence/claim, and citations are clickable to jump to the original
   passage in a source viewer.

Key enablers Google has that JEVI does not: ~2M-token context (can stuff dozens of
chunks per answer), hosted embeddings, and model-issued character offsets for
exact-span highlighting. The plan below works around all three.

Sources: peekaboolabs.ai source-grounding explainer (4-stage pipeline); cubxxw.com
Gemini Notebook workflow analysis; apexneural.com NotebookLM-clone case study
(Qdrant + local embeddings + cited generation); notebooklm.hk academic tutorial.

## 2. Goal

JEVI chat answers questions **only from user-selected sources** (notes, uploaded
docs) and renders **tappable inline citations** (`[1]`, `[2]`) that open the exact
passage they came from.

## 3. Non-goals

- Audio Overviews / TTS dialogue.
- YouTube transcription, web-URL import, Drive auto-sync.
- Character-span highlighting (chunk-level citations only — see §7).
- Deep Research long-form reports, cross-notebook search.
- Changing the chat model routing (OrcaRouter → hcnsec stays as-is).

## 4. Assumptions (visible; challenge before acceptance)

- A1. Retrieval embeddings via **Google AI Studio `gemini-embedding-001`**
  (free, no card, ~1,500 req/day; verified live 2026-09-11: returns 768-dim
  vectors with RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY task types). Backend
  client: `backend/ai/Embeddings.js` (batch via :batchEmbedContents, dims
  truncated to 768 via env). Key per deployer: `GEMINI_EMBEDDING_API_KEY`
  in Render (blueprint entry added, `sync: false`).
  Per-user store: chunk vectors in Room tables keyed by Firebase uid —
  each user embeds and searches only their own sources.
  **Rejected option:** on-device MediaPipe (bigger APK, weaker quality) and
  proxying embeddings through chat providers (burns RPM for no benefit).
- A2. Top-k = 5 chunks ≈ fits free-tier context limits. Google stuffs dozens;
  we cannot.
- A3. PDF text extraction reuses the existing doc-scanner path (ML Kit); plain
  text / notes are direct. **Verify during build:** if no on-device PDF parser
  exists, add Apache PdfBox-Android (~10MB) or route PDFs through the backend.
- A4. Citation markers depend on model compliance; smaller free-tier models
  sometimes drop `[n]` tags → the "Sources used" fallback (§6, step 3) is
  mandatory, not nice-to-have.

## 5. Known facts (this repo)

- `ChatRequest` (`core/network/AiApi.kt:61`) carries `attachmentText` but no
  source/chunk structure; `ChatResponseDto` (`:73`) returns
  `{reply, conversationId, reasoning, model}` — no citation field.
- No source-document entity exists. `NoteEntity` and `FlashcardEntity`/`JeviDeckEntity`
  exist and are the natural first citation targets.
- Toggle/checkbox path is v2-only RemoteViews (`WidgetToggleReceiver` absolute
  `desiredCompleted`) — untouched by this plan.

## 6. Build steps

1. **Source + chunk store** — new `SourceEntity` (id, name, mime, createdAt) and
   `SourceChunkEntity` (sourceId, ordinal, text ~500 tokens w/ ~50 overlap,
   embedding BLOB nullable). Chunker unit-testable, pure Kotlin.
2. **Ingestion** — "Add source" from note text / uploaded file → extract → chunk →
   embed (MediaPipe, background worker) → persist. Show per-source "ready" state;
   chat works with keyword overlap until embeddings land.
3. **Retrieval** — embed query, cosine top-k=5 over selected sources; fallback to
   keyword overlap when embedder/model missing. Return `RankedChunk(chunkId,
   sourceId, ordinal, text, score)`.
4. **Grounded prompt + DTO** — `ChatRequest.sources: List<CitedChunk(id, label,
   text)>`; backend numbers them `[1..k]`, instructs "cite every factual claim";
   `ChatResponseDto.citedChunkIds: List<String>` echoes which chunks the model
   claims to use. App appends "Sources used: [1][3]" from the retrieved set when
   the reply contains no markers (A4 fallback). Backend change is confined to
   `backend/ai/AiProvider.js` + `PromptBuilder.js`.
5. **Chat UI** — parse `[n]` in replies in `JeviScreens.kt`; render tappable chips;
   tap opens bottom sheet with chunk text, source name, "passage m of n", and
   prev/next chunk navigation (this is what replaces exact-span highlighting).
6. **Source picker** — per-conversation source multi-select (mirrors NotebookLM's
   per-query source selection), persisted on the conversation.

## 7. Rejected options / tradeoffs

- Exact character-span highlight: needs model-issued offsets; free-tier models
  won't reliably emit them. Chunk viewer with prev/next is the substitute.
- Whole-document stuffing into `attachmentText`: breaks on long docs under
  free-tier limits; chunking + top-k is required, not optional.
- Hosted vector DB (Qdrant/Pinecone): needs credit card / paid tier — excluded
  by project constraints.

## 8. Acceptance criteria

- [ ] Upload/select 2+ sources, ask a question answerable from only one → reply
  contains `[n]` markers AND tapping one opens the originating passage.
- [ ] Ask something answerable from no source → model says so (no hallucinated
  citation; every `[n]` resolves to a retrieved chunk).
- [ ] Airplane mode (after embedder downloaded): retrieval + excerpt viewing work;
  only generation requires network.
- [ ] All 9 existing widget tests still pass; new unit tests cover chunker,
  cosine ranking, and marker parsing.

## 9. Verification shape

- Unit: chunker boundaries/overlap, cosine top-k ordering, `[n]` parse +
  resolve-to-chunk (JUnit, same module as existing widget tests).
- Emulator: seed 2 notes → attach as sources → ask targeted question → screenshot
  citations → tap → excerpt sheet; repeat with wifi off for the offline check.
- Smallest proof per skill rail: one emulator run showing a tapped `[1]` opening
  its passage.

## 10. Status

- DRAFT. No code touched. Needs user acceptance of scope (§2–§4) before any
  executor handoff. After acceptance, recommended follow-on: single prepared
  coding change per step (§6), starting with step 1.
