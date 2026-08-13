package com.edukasyon.studentai.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.core.util.MAX_CHAT_ATTACHMENT_BYTES
import com.edukasyon.studentai.domain.model.ChatAttachmentPayload
import com.edukasyon.studentai.domain.model.AiConversationType
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.AiTool
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.QuizSessionState
import com.edukasyon.studentai.ui.viewmodel.sharedAiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onOpenScanner: () -> Unit = {},
    onOpenFlashcardStudy: () -> Unit = {},
    onOpenHistory: (filterScope: String) -> Unit = {},
    viewModel: AiViewModel = sharedAiViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFeature by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(state.restoredToolInput) {
        state.restoredToolInput?.let { restored ->
            inputText = restored
            viewModel.consumeRestoredInput()
        }
    }

    LaunchedEffect(state.activeConversationType) {
        state.activeConversationType?.let { type ->
            selectedFeature = when (type) {
                AiConversationType.TUTOR -> 0
                AiConversationType.SUMMARIZE,
                AiConversationType.FLASHCARDS,
                AiConversationType.QUIZ -> 1
            }
        }
    }

    val historyFilter = if (selectedFeature == 0) "tutor" else "tools"
    val newConversationType = if (selectedFeature == 0) {
        AiConversationType.TUTOR
    } else {
        AiConversationType.SUMMARIZE
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Gizmo AI") },
                actions = {
                    if (selectedFeature != 2) {
                        IconButton(onClick = { onOpenHistory(historyFilter) }) {
                            Icon(Icons.Outlined.History, contentDescription = "Conversation history")
                        }
                        IconButton(onClick = {
                            viewModel.startNewConversation(newConversationType)
                            inputText = ""
                        }) {
                            Icon(Icons.Outlined.NoteAdd, contentDescription = "New conversation")
                        }
                    }
                    if (state.xpEarnedThisSession > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("+${state.xpEarnedThisSession} XP") },
                            leadingIcon = {
                                Icon(Icons.Default.Star, contentDescription = null, Modifier.size(16.dp))
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            GizmoCompanionHeader(gizmo = state.gizmo, isOnline = state.isOnline)
            TabRow(selectedTabIndex = selectedFeature) {
                Tab(selected = selectedFeature == 0, onClick = { selectedFeature = 0 }, text = { Text("Tutor") })
                Tab(selected = selectedFeature == 1, onClick = { selectedFeature = 1 }, text = { Text("Tools") })
                Tab(selected = selectedFeature == 2, onClick = { selectedFeature = 2 }, text = { Text("Scanner") })
            }
            AnimatedContent(
                targetState = selectedFeature,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "aiFeatureTabs",
            ) { feature ->
                when (feature) {
                    0 -> AiTutorTab(
                        state = state,
                        input = inputText,
                        onInputChange = { inputText = it },
                        onSend = { attachment ->
                            viewModel.sendMessage(inputText, attachment = attachment)
                            inputText = ""
                        },
                        onQuickPrompt = { viewModel.sendQuickPrompt(it) },
                        onDismissError = { viewModel.clearError() },
                        onCopied = {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        },
                    )
                    1 -> AiToolsTab(
                        input = inputText,
                        onInputChange = { inputText = it },
                        state = state,
                        viewModel = viewModel,
                        onStudy = onOpenFlashcardStudy,
                        onCopied = {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        },
                    )
                    2 -> AiScannerTab(
                        state,
                        onOpenScanner,
                        onDismissError = { viewModel.clearError() },
                        onImport = { viewModel.confirmScannedClasses() },
                        onClear = { viewModel.clearScannedClasses() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTutorTab(
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (ChatAttachmentPayload?) -> Unit,
    onQuickPrompt: (String) -> Unit,
    onDismissError: () -> Unit,
    onCopied: suspend () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAttachment by remember { mutableStateOf<ChatAttachmentPayload?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var attachError by remember { mutableStateOf<String?>(null) }

    fun handlePickedUri(uri: Uri?) {
        attachError = null
        if (uri == null) return
        val attachment = readChatAttachment(context, uri)
        if (attachment == null) {
            attachError = "Could not read attachment (max 4 MB)."
        } else {
            pendingAttachment = attachment
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        handlePickedUri(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handlePickedUri(uri)
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottom > 0

    LaunchedEffect(state.messages.size, state.isLoading, imeVisible) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    val welcomeMessage =
                        "Hi! I'm Gizmo, your AI study buddy. Ask me anything — I'll explain concepts, help with homework, and cheer you on! 💪"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GizmoChatBubble(
                            message = welcomeMessage,
                            isUser = false,
                            sender = "Gizmo",
                            onCopy = {
                                clipboard.setText(AnnotatedString(welcomeMessage))
                                scope.launch { onCopied() }
                            },
                        )
                        Text(
                            "Quick prompts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GizmoConstants.QUICK_PROMPTS.forEach { prompt ->
                                SuggestionChip(
                                    onClick = { onQuickPrompt(prompt) },
                                    label = { Text(prompt, maxLines = 1) },
                                    enabled = !state.isLoading,
                                )
                            }
                        }
                    }
                }
            }
            items(state.messages, key = { "${it.timestamp}-${it.content.hashCode()}" }) { msg ->
                GizmoChatBubble(
                    message = msg.content,
                    isUser = msg.isUser,
                    sender = msg.sender,
                    attachmentName = msg.attachmentName,
                    attachmentIsImage = msg.attachmentIsImage,
                    onCopy = if (!msg.isUser) {
                        {
                            clipboard.setText(AnnotatedString(msg.content))
                            scope.launch { onCopied() }
                        }
                    } else {
                        null
                    },
                )
            }
            if (state.isLoading && state.loadingTool == AiTool.TUTOR) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AiLoadingIndicator(message = "Gizmo is thinking…")
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            state.error?.let { ErrorState(it, onDismiss = onDismissError) }
            attachError?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            pendingAttachment?.let { attachment ->
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(attachment.fileName, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                BouncyIconButton(
                                    onClick = { pendingAttachment = null },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                                }
                            },
                        )
                    }
                    if (attachment.isImage) {
                        Text(
                            text = if (state.isOnline) {
                                "Gizmo can see this image when you send."
                            } else {
                                "Image attached — connect online for Gizmo to analyze it."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    BouncyIconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !state.isLoading,
                    ) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = "Attach file or image")
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo or image") },
                            onClick = {
                                showAttachMenu = false
                                imagePicker.launch("image/*")
                            },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Document or file") },
                            onClick = {
                                showAttachMenu = false
                                filePicker.launch(arrayOf("*/*"))
                            },
                            leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, contentDescription = null) },
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Gizmo…") },
                    enabled = !state.isLoading,
                )
                BouncyIconButton(
                    onClick = {
                        onSend(pendingAttachment)
                        pendingAttachment = null
                    },
                    enabled = (input.isNotBlank() || pendingAttachment != null) && !state.isLoading,
                ) {
                    Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}

private fun readChatAttachment(context: Context, uri: Uri): ChatAttachmentPayload? {
    val reportedMime = context.contentResolver.getType(uri)
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"

    if (ChatAttachmentUtils.isPdf(reportedMime, name)) {
        val pdfImage = ChatAttachmentUtils.renderPdfFirstPageAsJpeg(context, uri) ?: return null
        return ChatAttachmentPayload(
            fileName = name,
            mimeType = "image/jpeg",
            isImage = true,
            bytes = pdfImage,
            textContent = "[PDF first page rendered for vision analysis]",
        )
    }

    val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (rawBytes.isEmpty() || rawBytes.size > MAX_CHAT_ATTACHMENT_BYTES) return null

    val isImage = ChatAttachmentUtils.isImageAttachment(rawBytes, name, reportedMime)
    val resolvedMime = if (isImage) {
        ChatAttachmentUtils.detectImageMime(rawBytes, name, reportedMime) ?: "image/jpeg"
    } else {
        reportedMime
    }

    val (bytes, mimeType) = if (isImage) {
        ChatAttachmentUtils.compressImageBytes(rawBytes, resolvedMime)
    } else {
        rawBytes to resolvedMime
    }

    val textContent = if (!isImage) ChatAttachmentUtils.readTextContent(bytes, mimeType) else null

    return ChatAttachmentPayload(
        fileName = name,
        mimeType = mimeType,
        isImage = isImage,
        bytes = bytes,
        textContent = textContent,
    )
}

@Composable
private fun AiToolsTab(
    input: String,
    onInputChange: (String) -> Unit,
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    viewModel: AiViewModel,
    onStudy: () -> Unit,
    onCopied: suspend () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun copyText(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { onCopied() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BouncyOutlinedButton(onClick = onStudy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.School, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Study Flashcards")
                }
                FilterChip(
                    selected = state.gizmo.memoriseMode,
                    onClick = { viewModel.toggleMemoriseMode() },
                    label = { Text("Memorise") },
                    leadingIcon = {
                        Icon(
                            if (state.gizmo.memoriseMode) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            Modifier.size(16.dp),
                        )
                    },
                )
            }
        }

        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AiLoadingIndicator(
                        message = when (state.loadingTool) {
                            AiTool.SUMMARIZE -> "Summarizing…"
                            AiTool.FLASHCARDS -> "Generating flashcards…"
                            AiTool.QUIZ -> "Building quiz…"
                            else -> "Working…"
                        },
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note content or topic") },
                placeholder = { Text("Paste your notes or enter a topic…") },
                minLines = 4,
                enabled = !state.isLoading,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolActionButton(
                    label = "Summarize",
                    loading = state.isLoading && state.loadingTool == AiTool.SUMMARIZE,
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.summarize(input) },
                )
                ToolActionButton(
                    label = "Flashcards",
                    loading = state.isLoading && state.loadingTool == AiTool.FLASHCARDS,
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.generateFlashcards(input) },
                )
                ToolActionButton(
                    label = "Quiz",
                    loading = state.isLoading && state.loadingTool == AiTool.QUIZ,
                    enabled = input.isNotBlank() && !state.isLoading && (!state.gizmo.memoriseMode || state.gizmo.canQuiz),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.generateQuiz(input) },
                )
            }
        }

        if (state.gizmo.memoriseMode && !state.gizmo.canQuiz) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Out of hearts! Turn off Memorise mode or wait for hearts to refill.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            item { ErrorState(error, onDismiss = { viewModel.clearError() }) }
        }

        state.lastSummary?.let { summary ->
            item {
                SummaryResultCard(
                    summary = summary,
                    onCopy = { copyText(summary) },
                )
            }
        }

        if (state.generatedFlashcards.isNotEmpty()) {
            item {
                FlashcardsResultSection(
                    cards = state.generatedFlashcards,
                    saved = state.flashcardsSaved,
                    onSave = { viewModel.saveGeneratedFlashcards() },
                    onStudy = onStudy,
                    onCopyAll = {
                        copyText(
                            state.generatedFlashcards.joinToString("\n\n") { card ->
                                buildString {
                                    append("Q: ${card.question}\nA: ${card.answer}")
                                    card.topic?.let { append("\nTopic: $it") }
                                }
                            },
                        )
                    },
                )
            }
        }

        state.quizSession?.let { session ->
            item {
                QuizSessionSection(
                    session = session,
                    gizmo = state.gizmo,
                    quizSaved = state.quizSaved,
                    onSelectAnswer = viewModel::selectQuizAnswer,
                    onReveal = viewModel::revealQuizAnswer,
                    onNext = viewModel::nextQuizQuestion,
                    onRestart = viewModel::restartQuiz,
                    onSave = viewModel::saveQuizResult,
                    onCopyResults = {
                        copyText(
                            buildString {
                                append("Quiz Complete!\n")
                                append("${session.quiz.title}\n")
                                append("Score: ${session.correctCount}/${session.totalQuestions} (${session.scorePercent}%)")
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolActionButton(
    label: String,
    loading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BouncyButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(label)
    }
}

@Composable
private fun SummaryResultCard(summary: String, onCopy: () -> Unit) {
    StudentAiCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Summary", style = MaterialTheme.typography.titleSmall)
            BouncyIconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy summary")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(summary)
    }
}

@Composable
private fun FlashcardsResultSection(
    cards: List<com.edukasyon.studentai.domain.model.Flashcard>,
    saved: Boolean,
    onSave: () -> Unit,
    onStudy: () -> Unit,
    onCopyAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Generated ${cards.size} flashcards",
                style = MaterialTheme.typography.titleSmall,
            )
            BouncyIconButton(onClick = onCopyAll) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy flashcards")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!saved) {
                BouncyButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Library")
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text("Saved") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) },
                )
            }
            BouncyOutlinedButton(onClick = onStudy, enabled = saved) {
                Text("Study Now")
            }
        }
        cards.forEach { card ->
            StudentAiCard {
                Text("Q: ${card.question}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("A: ${card.answer}", style = MaterialTheme.typography.bodySmall)
                card.topic?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuizSessionSection(
    session: QuizSessionState,
    gizmo: com.edukasyon.studentai.domain.model.GizmoCompanionState,
    quizSaved: Boolean,
    onSelectAnswer: (String) -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onSave: () -> Unit,
    onCopyResults: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(session.quiz.title, style = MaterialTheme.typography.titleMedium)
            if (gizmo.memoriseMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${gizmo.hearts} ♥", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (session.finished) {
            StudentAiCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Quiz Complete! 🎉", style = MaterialTheme.typography.titleSmall)
                    BouncyIconButton(onClick = onCopyResults) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy quiz results")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Score: ${session.correctCount}/${session.totalQuestions} (${session.scorePercent}%)")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BouncyOutlinedButton(onClick = onRestart) { Text("Retry") }
                    if (!quizSaved) {
                        BouncyButton(onClick = onSave) { Text("Save Quiz") }
                    } else {
                        AssistChip(
                            onClick = {},
                            label = { Text("Quiz saved") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            return
        }

        if (session.blockedByHearts) {
            StudentAiCard {
                Text("Out of Hearts 💔", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Wait for hearts to refill or turn off Memorise mode to keep quizzing.")
            }
            return
        }

        val question = session.currentQuestion ?: return
        StudentAiCard {
            Text(
                "Question ${session.currentIndex + 1} of ${session.totalQuestions}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(question.question, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            QuizOptions(question, session, onSelectAnswer, onReveal, onNext)
        }
    }
}

@Composable
private fun QuizOptions(
    question: QuizQuestion,
    session: QuizSessionState,
    onSelectAnswer: (String) -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
) {
    question.options.forEach { option ->
        val selected = session.selectedAnswer == option
        val isCorrect = session.revealed && option.equals(question.correctAnswer, ignoreCase = true)
        val isWrong = session.revealed && selected && !option.equals(question.correctAnswer, ignoreCase = true)
        val containerColor = when {
            isCorrect -> MaterialTheme.colorScheme.primaryContainer
            isWrong -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
        OutlinedCard(
            onClick = { if (!session.revealed) onSelectAnswer(option) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        ) {
            Text(option, Modifier.padding(12.dp))
        }
    }

    if (session.revealed) {
        val isCorrect = session.selectedAnswer?.equals(question.correctAnswer, ignoreCase = true) == true
        Text(
            if (isCorrect) "Correct! +${GizmoConstants.XP_CORRECT_ANSWER} XP 🎉" else "Wrong — lost a heart ♥",
            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!session.blockedByHearts) {
            BouncyButton(onClick = onNext, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    if (session.currentIndex + 1 >= session.totalQuestions) "See Results"
                    else "Next Question",
                )
            }
        }
    } else {
        BouncyButton(
            onClick = onReveal,
            enabled = session.selectedAnswer != null,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Check Answer")
        }
    }
}

@Composable
private fun AiScannerTab(
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    onOpenScanner: () -> Unit,
    onDismissError: () -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Schedule Scanner", style = MaterialTheme.typography.titleMedium)
        Text("Capture your class schedule and AI will extract your classes for review.")
        BouncyButton(onClick = onOpenScanner) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Camera Scanner")
        }
        if (state.scannedClasses.isNotEmpty()) {
            Text("Review extracted classes:", style = MaterialTheme.typography.titleSmall)
            state.scannedClasses.forEach { cls ->
                StudentAiCard {
                    Text(cls.subject, style = MaterialTheme.typography.titleSmall)
                    Text("${cls.day} ${cls.startTime}-${cls.endTime}")
                    cls.teacher?.let { Text("Teacher: $it") }
                    cls.room?.let { Text("Room: $it") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BouncyOutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("Clear")
                }
                BouncyButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import to Schedule")
                }
            }
        }
        if (state.isLoading && state.loadingTool == AiTool.SCANNER) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AiLoadingIndicator(message = "Scanning schedule…")
            }
        }
        state.error?.let { ErrorState(it, onDismiss = onDismissError) }
    }
}
