# Android Citation DTO/UI Changes for Unified Local & Web Citations

**Status:** Read-only analysis. No files modified.  
**Date:** 2026-09-11  
**Context:** Jevi AI: users can optionally add local sources; backend auto-searches web. Need unified citation representation.

---

## Current Architecture

### Android Side

**DTOs:**
- `CitedChunkView(id: String, sourceId: String, label: String, text: String)` — UI model for citation chips
- `RankedChunk(chunkId: Long, sourceId: String, sourceName: String, ordinal: Int, text: String, score: Double)` — local source chunks
- `CitedChunkMeta(id: String, sourceId: String, label: String, text: String)` — stored in conversation metadata JSON
- `AiChatResponse.citedChunkIds: List<String>` — opaque IDs from backend

**Flow:**
1. User toggles local sources via `SourcePickerRow`
2. Selected `RankedChunk` list passed to backend as `sources` parameter in `ChatRequest`
3. Backend returns `citedChunkIds: List<String>` (currently only local source IDs)
4. ViewModel converts backend IDs to `CitedChunkView` by filtering `groundedSources` (line 1379–1385 in ViewModels.kt)
5. UI calls `openCitation(CitedChunkView)` → queries DB via `sourceRepository.chunksForSource(sourceId)` → displays in `CitationPassageBottomSheet`

**Issue:** All citations assume local DB lookup. Web results have no persistent ID.

---

### Backend Side

**In `handleChat()` (server.js:294–396):**
- Accepts `sources: CitedChunkDto[]` (client-provided local chunks)
- Builds numbered prompt: `[1] label: text`, `[2] label: text`, etc.
- Optionally appends web search results to prompt (line 361–364) **without tracking as numbered sources**
- Extracts citations from reply by regex: `/\[(\\d+)\]/g` (line 381–383)
- Maps citation numbers to `sources[n-1].id` (line 384–386)
- **Problem:** Web results are appended as free text, not numbered. If AI cites them, the citation number doesn't map to a tracked source.

---

## Problem Statement

1. **No ID collision scheme:** How do we distinguish `web:url123` from `local:sourceId`?
2. **No web metadata in response:** Backend only returns opaque string IDs; no way to know if a citation is local or web.
3. **UI can't open URLs:** `CitationPassageBottomSheet` assumes local DB; no branch for "open in browser."
4. **Ephemeral web results:** Web search results aren't persisted; only the citation reference exists.
5. **Backend prompt ambiguity:** If web results are appended as text (not numbered), the AI's `[3]` might refer to a local source or web result depending on order.

---

## Minimal Solution Design

### 1. ID Prefix Scheme (No DTO changes, just encoding)

Use **prefixed string IDs** to encode source type:

```
Local citation:  "local:<sourceId>:<chunkId>"
                 e.g., "local:550e8400-e29b-41d4-a716-446655440000:12"

Web citation:    "web:<url_hash>:<ordinal>"
                 e.g., "web:7f8c5e7e:0"
```

**Why:**
- Single `citedChunkIds: List<String>` field unchanged in `AiChatResponse`
- Android parses prefix to determine behavior
- No DTO schema change needed (backward compatible)
- URL hash prevents collision; ordinal handles same URL cited multiple times

---

### 2. Backend Changes (Minimal)

**File:** `backend/server.js` `handleChat()`

**Change 1: Track web results as numbered sources**
```javascript
// After web search (line 361–364), append to sources array with marker:
const sources = Array.isArray(clientSources) ? clientSources.slice(0, 8) : [];
let webResults = [];

if (webSearchRequest.requested) {
  const results = await searchService.search(webSearchRequest.query, signal);
  webResults = results.slice(0, 4); // Limit web results
  // Append to numbered prompt WITHOUT adding to sources array yet
  // (web results are reference-only, not cited like local sources)
}

// Build prompt with both local sources AND web results as separate section:
let userContent = buildChatUserContent({...});

if (sources.length) {
  const numbered = sources
    .map((c, i) => `[${i + 1}] ${c.label}: ${String(c.text).slice(0, 1500)}`)
    .join('\n\n');
  userContent += `\n\nAnswer using ONLY the numbered sources below. Cite with [1], [2], etc.\n${numbered}`;
}

if (webSearchRequest.requested && webResults.length) {
  // Web results as separate, unnumbered reference (or with offset numbering)
  const webFormatted = webResults
    .map((r, i) => `Web result ${sources.length + i + 1}: "${r.title}" - ${r.url}\n${r.snippet}`)
    .join('\n\n');
  userContent += `\n\nCurrent web search results (cite as [${sources.length + 1}], [${sources.length + 2}], etc. if relevant):\n${webFormatted}`;
}

// After AI reply, extract citations:
const citedFromReply = Array.from(
  new Set(Array.from(String(reply || '').matchAll(/\[(\d+)\]/g)).map((m) => m[1]))
).filter((n) => Number(n) >= 1 && Number(n) <= sources.length + webResults.length);

const citedChunkIds = citedFromReply.map((n) => {
  const idx = Number(n) - 1;
  if (idx < sources.length) {
    return `local:${sources[idx].id}`; // Or extract sourceId if embedded in id
  } else {
    const webIdx = idx - sources.length;
    const result = webResults[webIdx];
    const urlHash = require('crypto')
      .createHash('sha256')
      .update(result.url)
      .digest('hex')
      .slice(0, 8);
    return `web:${urlHash}:${webIdx}`;
  }
});

return {
  reply,
  ...(reasoning ? { reasoning } : {}),
  conversationId: conversationId || crypto.randomUUID(),
  model: usedModel || model,
  effort,
  citedChunkIds,
  citedWebResults: citedFromReply
    .map(n => Number(n) - 1)
    .filter(i => i >= sources.length)
    .map(i => webResults[i - sources.length])
    .map(r => ({ url: r.url, title: r.title, snippet: r.snippet })),
};
```

**Change 2 (Alternative, simpler):** Return web results alongside citations
```javascript
// Instead of encoding, return structured response:
return {
  reply,
  conversationId,
  model,
  effort,
  citedChunkIds, // Local source IDs only
  citedWebUrls: citedFromReply
    .filter(n => Number(n) > sources.length)
    .map(n => webResults[Number(n) - sources.length - 1].url),
};
```

---

### 3. Android DTO Changes (Minimal)

**Option A: Add fields to `AiChatResponse` (non-breaking)**

```kotlin
@Serializable data class ChatResponseDto(
    val reply: String,
    val conversationId: String,
    val reasoning: String? = null,
    val model: String? = null,
    val citedChunkIds: List<String> = emptyList(),
    // NEW: Web citations (parallel to citedChunkIds)
    val citedWebResults: List<CitedWebResultDto> = emptyList(),
)

@Serializable data class CitedWebResultDto(
    val url: String,
    val title: String = "",
    val snippet: String = "",
)
```

**Option B: Prefix-based (no DTO change)**
- Keep `citedChunkIds` only
- Backend returns `"local:..."` and `"web:..."` prefixed IDs
- Android parses prefix at display time

→ **Recommend Option B** (smaller footprint, backward compatible)

---

### 4. Android UI/ViewModel Changes (Minimal)

**File:** `androidApp/.../ui/viewmodel/ViewModels.kt` `openCitation()`

```kotlin
fun openCitation(cite: CitedChunkView) {
    viewModelScope.launch {
        when {
            cite.id.startsWith("web:") -> {
                // Extract URL from web citation ID
                val parts = cite.id.split(":")
                val urlHash = parts.getOrNull(1) ?: return@launch
                val ordinal = parts.getOrNull(2)?.toIntOrNull() ?: 0
                
                // Option 1: Reconstruct URL (need to store in CitedChunkView.url or text)
                val url = cite.text // Assume text holds URL for web results
                
                // Open URL in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
            cite.id.startsWith("local:") -> {
                // Existing logic: query local DB
                val chunks = runCatching { sourceRepository.chunksForSource(cite.sourceId) }
                    .getOrDefault(emptyList())
                val idx = chunks.indexOfFirst { it.chunkId.toString() == cite.id }.takeIf { it >= 0 } ?: 0
                val shown = chunks.ifEmpty {
                    listOf(RankedChunk(-1, cite.sourceId, cite.label, 0, cite.text, 1.0))
                }
                _uiState.update { it.copy(viewerChunks = shown, viewerIndex = idx.coerceIn(shown.indices)) }
            }
            else -> {
                // Fallback: treat as local (backward compat)
                val chunks = runCatching { sourceRepository.chunksForSource(cite.sourceId) }
                    .getOrDefault(emptyList())
                val idx = chunks.indexOfFirst { it.chunkId.toString() == cite.id }.takeIf { it >= 0 } ?: 0
                val shown = chunks.ifEmpty {
                    listOf(RankedChunk(-1, cite.sourceId, cite.label, 0, cite.text, 1.0))
                }
                _uiState.update { it.copy(viewerChunks = shown, viewerIndex = idx.coerceIn(shown.indices)) }
            }
        }
    }
}
```

**File:** `androidApp/.../ui/components/SourcePickerRow.kt` (or citation chip component)

```kotlin
// Update citation chip label to show source type:
@Composable
fun CitationChip(cite: CitedChunkView) {
    val (icon, label) = when {
        cite.id.startsWith("web:") -> {
            Icons.Default.OpenInBrowser to "Web: ${cite.label}"
        }
        else -> {
            Icons.Default.BookmarkBorder to cite.label
        }
    }
    
    InputChip(
        selected = false,
        onClick = { /* openCitation(cite) */ },
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        // ... rest of styling
    )
}
```

---

### 5. Citation Display Changes (Minimal)

**File:** `androidApp/.../ui/components/GizmoChatBubble.kt` (approx line 341+)

```kotlin
// In the citation chips row, detect type and show appropriate action:
Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    msg.citations.forEach { cite ->
        when {
            cite.id.startsWith("web:") -> {
                // Show URL chip with "open" action
                AssistChip(
                    onClick = { /* openCitation(cite) */ },
                    label = { Text(cite.label.take(30)) },
                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                )
            }
            else -> {
                // Existing passage viewer chip
                InputChip(
                    selected = false,
                    onClick = { /* openCitation(cite) */ },
                    label = { Text(cite.label) },
                    leadingIcon = { Icon(Icons.Default.BookmarkBorder, null) },
                )
            }
        }
    }
}
```

---

## Implementation Path

### Phase 1: Backend (1 change)
1. **`backend/server.js` `handleChat()`**: Prefix web citations, return prefixed `citedChunkIds`
   - Encode web result URL as hash; append to citation IDs
   - Keep local source IDs as-is (or also prefix for consistency)
   - **Cost:** ~40 lines, no new endpoints

### Phase 2: Android DTO (optional, 0 changes if prefix-only)
1. If using Option A (structured response), add `CitedWebResultDto` to `ChatResponseDto`
2. Update `RemoteAiService.kt` to pass-through new field

### Phase 3: Android UI (~100 lines)
1. Update `ViewModels.kt` `openCitation()` to detect prefix and branch
2. Update citation chip components to show URL icon for web results
3. Add `Intent(ACTION_VIEW)` for web URLs

### Phase 4: Testing
1. **Local sources only** → existing behavior unchanged (backward compat)
2. **Web results only** → citations open URLs
3. **Mixed** → local citations show passages, web citations open browser
4. **Edge cases:** same URL cited twice, deleted source, web search disabled

---

## Edge Cases & Tests

| Case | Current Behavior | New Behavior | Test |
|------|------------------|--------------|------|
| User deletes a local source, then views old conversation | Passage viewer crashes or shows empty | Fall back to displaying citation text only | Load old convo, delete source, reopen message |
| Same web URL cited twice in one reply | N/A | Show as two separate citation chips (same URL, different ordinal) | Craft prompt so AI cites same URL twice |
| Web search disabled | N/A | Local sources work, no web results appended | Disable web search service, send message |
| Mixed local+web citations | N/A | UI shows both types with appropriate icons | Enable web search, add local source, trigger both |
| Backend returns old format (no prefix) | N/A | Treat as local (backward compat) | Test with old backend response |
| Citation number out of range | N/A | Ignore it (skip in extraction) | Manually craft malformed reply |

---

## Files Requiring Changes

### Backend
- **`backend/server.js`** (handleChat): Add web result tracking & prefixed IDs (~50 lines diff)
- **`backend/AiProvider.js`** (optional): Refactor web search formatting if extracting method

### Android
- **`androidApp/.../core/network/AiApi.kt`** (optional): Add `citedWebResults` field if using Option A
- **`androidApp/.../core/ai/RemoteAiService.kt`** (optional): Decode new field if using Option A
- **`androidApp/.../ui/viewmodel/ViewModels.kt`** `openCitation()`: Branch on ID prefix (~20 lines)
- **`androidApp/.../ui/components/GizmoChatBubble.kt`** or citation chip component: Show URL icon (~15 lines)
- **`androidApp/.../domain/model/GizmoCompanion.kt`**: CitedChunkView already has `id`, no change needed

### Tests (new)
- **`backend/test/citations.test.js`**: Verify prefix encoding, web+local mixed
- **`androidApp/src/test/.../CitationDecodingTest.kt`**: Parse prefixed IDs, verify fallback

---

## Backward Compatibility

- ✅ Existing `CitedChunkView` schema unchanged (can add optional `sourceType` field later)
- ✅ Existing `citedChunkIds` field still exists; new `citedWebResults` is optional
- ✅ Local-only flow (no web search) works unchanged
- ✅ Old backend responses (non-prefixed IDs) treated as local citations
- ✅ Old Android app ignores `citedWebResults` field if present

---

## Summary of Minimal Changes

| Component | Type | LOC | Complexity |
|-----------|------|-----|------------|
| Backend: prefix web IDs | Logic | ~50 | Medium |
| Android: parse prefix in openCitation | Logic | ~20 | Low |
| Android: URL icon in chip | UI | ~15 | Low |
| Android: open URL intent | Logic | ~5 | Low |
| **Total** | — | ~90 | **Low** |

No DTO schema breaking changes required if using prefix scheme (Option B).
