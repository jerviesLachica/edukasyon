package com.edukasyon.studentai.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.components.DocToStudyStudioSheet
import com.edukasyon.studentai.ui.components.LoadingState
import com.edukasyon.studentai.ui.components.MarkdownChatText
import com.edukasyon.studentai.ui.viewmodel.NoteEditorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAiStudySheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var bodyField by remember { mutableStateOf(TextFieldValue()) }
    var bodyInitialized by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.saveNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isLoading, state.content) {
        if (!state.isLoading && !bodyInitialized) {
            bodyField = TextFieldValue(
                text = state.content,
                selection = TextRange(state.content.length),
            )
            bodyInitialized = true
        }
    }

    val insertFormatting: (String, String) -> Unit = { prefix, suffix ->
        val current = bodyField.text
        val selection = bodyField.selection
        val start = selection.min.coerceIn(0, current.length)
        val end = selection.max.coerceIn(0, current.length)

        val newText = if (start != end) {
            val selected = current.substring(start, end)
            current.replaceRange(start, end, "$prefix$selected$suffix")
        } else {
            current.replaceRange(start, start, "$prefix$suffix")
        }

        val newCursor = if (start != end) {
            start + prefix.length + (end - start) + suffix.length
        } else {
            start + prefix.length
        }

        val updated = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor.coerceIn(0, newText.length)),
        )
        bodyField = updated
        viewModel.onContentChange(newText)
    }

    if (showAiStudySheet) {
        DocToStudyStudioSheet(
            initialText = Pair(state.title.ifBlank { "Note" }, state.content),
            onDismissRequest = { showAiStudySheet = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete note?") },
            text = { Text("This note will be removed permanently.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteNote(onBack)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Column {
                        Text(
                            text = if (state.title.isBlank()) "New note" else state.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when {
                                state.isSaving -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Saving…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                state.lastSavedAt != null -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        "Saved ${formatSavedTime(state.lastSavedAt!!)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.flushSave(onComplete = onBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAiStudySheet = true },
                        enabled = state.content.isNotBlank() || state.title.isNotBlank(),
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Study Tools",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Icon(
                            if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (isPreviewMode) "Edit Note" else "Preview Markdown",
                            tint = if (isPreviewMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            if (state.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (state.isPinned) "Unpin" else "Pin",
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Save now") },
                                onClick = {
                                    showMenu = false
                                    viewModel.saveNow()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.isFavorite) "Remove from favorites" else "Add to favorites") },
                                onClick = {
                                    showMenu = false
                                    viewModel.toggleFavorite()
                                },
                                leadingIcon = {
                                    Icon(if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share note") },
                                onClick = {
                                    showMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, state.title.ifBlank { "Note" })
                                        val body = if (state.title.isNotBlank()) "${state.title}\n\n${state.content}" else state.content
                                        putExtra(Intent.EXTRA_TEXT, body)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Note"))
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                enabled = state.content.isNotBlank() || state.title.isNotBlank(),
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                enabled = state.canDelete,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
        ) { contentModifier ->
            if (state.isLoading) {
                LoadingState(modifier = contentModifier)
            } else {
                Column(
                    modifier = contentModifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                ) {
                    TextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title (optional)") },
                        textStyle = MaterialTheme.typography.titleLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next,
                        ),
                        colors = fieldColors,
                    )
                    val wordCount = remember(state.content) {
                        state.content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                    }
                    val charCount = state.content.length
                    val readMinutes = maxOf(1, (wordCount / 180))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (wordCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = "$wordCount ${if (wordCount == 1) "word" else "words"} · $charCount chars · $readMinutes min read",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        if (state.isPinned) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = "Pinned",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    if (isPreviewMode) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "Markdown Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            if (state.content.isBlank()) {
                                Text(
                                    "No content yet. Tap the edit icon in the top bar to start writing notes.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            } else {
                                MarkdownChatText(
                                    markdown = state.content,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    } else {
                        // Markdown quick formatting toolbar
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            item {
                                FormatChip(
                                    icon = Icons.Default.FormatBold,
                                    label = "Bold",
                                    onClick = { insertFormatting("**", "**") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.FormatItalic,
                                    label = "Italic",
                                    onClick = { insertFormatting("*", "*") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.FormatStrikethrough,
                                    label = "Strike",
                                    onClick = { insertFormatting("~~", "~~") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.Title,
                                    label = "H1",
                                    onClick = { insertFormatting("# ", "") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.Title,
                                    label = "H2",
                                    onClick = { insertFormatting("## ", "") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                    label = "List",
                                    onClick = { insertFormatting("• ", "") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Outlined.CheckBox,
                                    label = "Task",
                                    onClick = { insertFormatting("- [ ] ", "") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.FormatQuote,
                                    label = "Quote",
                                    onClick = { insertFormatting("> ", "") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.Code,
                                    label = "Code",
                                    onClick = { insertFormatting("```\n", "\n```") },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.AccessTime,
                                    label = "Timestamp",
                                    onClick = {
                                        val stamp = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
                                        insertFormatting("[$stamp] ", "")
                                    },
                                )
                            }
                            item {
                                FormatChip(
                                    icon = Icons.Default.AutoAwesome,
                                    label = "AI Tools",
                                    isHighlighted = true,
                                    onClick = { showAiStudySheet = true },
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )

                        // Note body text field: handles multiline scrolling smoothly without nested conflicts
                        TextField(
                            value = bodyField,
                            onValueChange = { updated ->
                                bodyField = updated
                                viewModel.onContentChange(updated.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            placeholder = {
                                Text(
                                    "Start writing…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = fieldColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(10.dp),
        color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (isHighlighted) 2.dp else 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatSavedTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
