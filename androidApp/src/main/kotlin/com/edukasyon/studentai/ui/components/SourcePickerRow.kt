package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Checkbox
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.CitedSource
import com.edukasyon.studentai.domain.model.RankedChunk
import com.edukasyon.studentai.domain.model.WebSearchResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerRow(
    sources: List<CitedSource>,
    selectedIds: Set<String>?,
    onToggle: (String) -> Unit,
    onAdd: (name: String, text: String) -> Unit,
    onDelete: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Add Source Button
            FilterChip(
                selected = false,
                onClick = { showAddDialog = true },
                enabled = enabled,
                label = { Text("Add Source") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Source",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            // Source Chips
            sources.forEach { source ->
                val isSelected = selectedIds == null || selectedIds.contains(source.id)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(source.id) },
                    enabled = enabled,
                    label = { Text(source.name) },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDelete(source.id) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete source",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    },
                )
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, text ->
                showAddDialog = false
                onAdd(name, text)
            },
        )
    }
}

@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, text: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Source Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Title (e.g. Bio Chapter 4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Content / Notes") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && text.isNotBlank()) {
                        onConfirm(name.trim(), text.trim())
                    }
                },
                enabled = name.isNotBlank() && text.isNotBlank(),
            ) {
                Text("Ingest & Index")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesBottomSheet(
    sources: List<CitedSource>,
    selectedSourceIds: Set<String>?,
    onToggleSource: (String) -> Unit,
    onAddSource: (String, String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDismiss: () -> Unit,
    onWebSearch: suspend (String) -> List<WebSearchResult> = { emptyList() },
    onPickWebResult: (WebSearchResult) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessingPhotos by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            isProcessingPhotos = true
            try {
                val mlKit = com.edukasyon.studentai.di.HiltEntryPoint.mlKitTextRecognizer(context)
                val textParts = mutableListOf<String>()
                uris.forEachIndexed { index, uri ->
                    val ocr = runCatching { mlKit.recognizeFromUri(context, uri) }.getOrNull()
                    if (ocr != null && ocr.hasUsableText) {
                        textParts.add("--- Page ${index + 1} ---\n${ocr.text}")
                    }
                }
                if (textParts.isNotEmpty()) {
                    val combined = textParts.joinToString("\n\n")
                    val title = if (uris.size == 1) "Photo Notes" else "${uris.size} Photo Notes"
                    onAddSource(title, combined)
                }
            } finally {
                isProcessingPhotos = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.LibraryBooks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Sources (${sources.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close sources sheet")
                    }
                }

                Spacer(Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Saved (${sources.size})") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Search the web") },
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (selectedTab == 0) {
                // Action buttons row: Add Source & From Photos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ Add text")
                    }
                    TextButton(
                        onClick = { photoPicker.launch("image/*") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessingPhotos,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isProcessingPhotos) "Reading…" else "+ From photos")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Source list or Empty state
                if (sources.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                "No sources added yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Add course notes, textbook chapters, or PDFs. Jevi will automatically ground answers and cite statements from your sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(sources, key = { it.id }) { source ->
                            val isSelected = selectedSourceIds == null || selectedSourceIds.contains(source.id)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                onClick = { onToggleSource(source.id) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { onToggleSource(source.id) },
                                        )
                                        Column {
                                            Text(
                                                text = source.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = "${source.chunkCount} passages",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteSource(source.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete source",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                } else {
                    WebSearchTab(
                        onWebSearch = onWebSearch,
                        onPick = onPickWebResult,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, text ->
                showAddDialog = false
                onAddSource(name, text)
            },
        )
    }
}

@Composable
fun WebSearchTab(
    onWebSearch: suspend (String) -> List<WebSearchResult>,
    onPick: (WebSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<WebSearchResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        error = null
        scope.launch {
            try {
                results = onWebSearch(q)
                searched = true
            } catch (e: Exception) {
                error = e.message ?: "Search failed. Check your connection and try again."
            } finally {
                searching = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search the web (e.g. photosynthesis steps)") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { runSearch() }, enabled = query.isNotBlank() && !searching) {
                    Icon(Icons.Default.Search, contentDescription = "Search the web")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            modifier = Modifier.fillMaxWidth(),
            enabled = !searching,
        )
        Text(
            "Pick a result (+) to save it as a source Jevi will cite.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            searching -> {
                    StudentAiLoader(
                        label = "Searching",
                        style = StudentAiLoaderStyle.Compact,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
            }
            error != null -> {
                Text(
                    error ?: "Search failed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            !searched -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Type a topic and hit search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            results.isEmpty() -> {
                Text(
                    "No results. Try different keywords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.url }) { result ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.title.ifBlank { result.url },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = result.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (result.content.isNotBlank()) {
                                        Text(
                                            text = result.content,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(onClick = { onPick(result) }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add as source",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationPassageBottomSheet(
    chunks: List<RankedChunk>,
    currentIndex: Int,
    highlight: String? = null,
    onStep: (dir: Int) -> Unit,
    onClose: () -> Unit,
) {
    if (chunks.isEmpty() || currentIndex !in chunks.indices) return

    val currentChunk = chunks[currentIndex]
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = currentChunk.sourceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Passage ${currentIndex + 1} of ${chunks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close passage viewer")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Chunk Text Excerpt
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = highlightMatch(currentChunk.text, highlight),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Navigation Prev / Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onStep(-1) },
                        enabled = currentIndex > 0,
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Previous")
                    }

                    TextButton(
                        onClick = { onStep(1) },
                        enabled = currentIndex < chunks.lastIndex,
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** Bolds the first case-insensitive occurrence of [query] inside [text]. */
private fun highlightMatch(text: String, query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return AnnotatedString(text)
    val needle = query.trim()
    val idx = text.indexOf(needle, ignoreCase = true)
    if (idx < 0) return AnnotatedString(text)
    val end = (idx + needle.length).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(idx, end))
        }
        append(text.substring(end))
    }
}
