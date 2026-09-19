package com.edukasyon.studentai.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.components.DocToStudyStudioSheet
import com.edukasyon.studentai.ui.components.LoadingState
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.title.isBlank()) "New note" else state.title,
                            maxLines = 1,
                        )
                        when {
                            state.isSaving -> Text(
                                "Saving…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.lastSavedAt != null -> Text(
                                "Saved ${formatSavedTime(state.lastSavedAt!!)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            if (state.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (state.isPinned) "Unpin" else "Pin",
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "Unfavorite" else "Favorite",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.saveNow() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("AI Study Tools") },
                                onClick = {
                                    showMenu = false
                                    showAiStudySheet = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                enabled = state.content.isNotBlank() || state.title.isNotBlank(),
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.isPinned) "Unpin note" else "Pin note") },
                                onClick = {
                                    showMenu = false
                                    viewModel.togglePin()
                                },
                                leadingIcon = {
                                    Icon(if (state.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, contentDescription = null)
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
                                text = { Text("Delete") },
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
                    val readMinutes = maxOf(1, (wordCount / 180))
                    if (wordCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    text = "$wordCount ${if (wordCount == 1) "word" else "words"} · $readMinutes min read",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
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
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    TextField(
                        value = bodyField,
                        onValueChange = { updated ->
                            bodyField = updated
                            viewModel.onContentChange(updated.text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
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

private fun formatSavedTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
