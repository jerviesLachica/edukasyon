package com.edukasyon.studentai.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.ChatAttachmentUtils
import com.edukasyon.studentai.core.util.MAX_CHAT_ATTACHMENT_BYTES
import com.edukasyon.studentai.domain.model.ChatAttachmentPayload
import com.edukasyon.studentai.domain.model.AiConversationType
import com.edukasyon.studentai.domain.model.GizmoConstants
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberChatContentMaxWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.adaptive.rememberImeVisible
import com.edukasyon.studentai.ui.viewmodel.AiTool
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.sharedAiViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onOpenHistory: (filterScope: String) -> Unit = {},
    onChatInputActive: (Boolean) -> Unit = {},
    viewModel: AiViewModel = sharedAiViewModel(),
    deckId: String? = null,
    onCloseDeck: () -> Unit = {},
    showTopBar: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val ttsController = remember { com.edukasyon.studentai.core.util.TtsSpeakController() }
    val ttsContext = LocalContext.current
    val ttsReady by ttsController.ready.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { ttsController.init(ttsContext) }
    DisposableEffect(Unit) { onDispose { ttsController.shutdown() } }

    LaunchedEffect(deckId) {
        if (deckId != null) viewModel.openDeckTutor(deckId)
        else viewModel.enterGeneralTutor()
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            if (showTopBar) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    var headerExpanded by remember { mutableStateOf(true) }
    var showSourcesSheet by remember { mutableStateOf(false) }

    val mainChatContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            contentModifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            GizmoCompanionHeader(
                gizmo = state.gizmo,
                isOnline = state.isOnline,
                expanded = headerExpanded,
                onToggleExpanded = { headerExpanded = !headerExpanded },
                thinkingLevel = state.thinkingLevel,
                onThinkingLevelSelected = { viewModel.setThinkingLevel(it) },
            )
            if (state.activeDeckId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Deck: ${state.activeDeckTitle ?: "Deck"}") },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.School,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.closeDeckTutor()
                                    onCloseDeck()
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Leave deck chat",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "Answers from deck cards only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            NotebookLmSourcesBar(
                sourcesCount = state.sources.size,
                selectedCount = state.selectedSourceIds?.size ?: state.sources.size,
                strictGrounding = state.strictGroundingMode,
                onOpenSources = { showSourcesSheet = true },
                onToggleStrictGrounding = { viewModel.toggleStrictGrounding() },
            )
            NotebookLmStudioBar(
                onAudioOverview = { viewModel.generateAudioOverviewFromSources() },
                onStudyGuide = { viewModel.generateStudyGuideFromSources() },
                onFlashcards = { viewModel.generateFlashcardsFromSources() },
                onQuiz = { viewModel.generateQuizFromSources() },
                onBriefingDoc = { viewModel.generateBriefingDocFromSources() },
            )
            if (state.audioOverviewState !is com.edukasyon.studentai.ui.viewmodel.AudioOverviewUiState.Idle) {
                NotebookLmAudioBanner(
                    audioState = state.audioOverviewState,
                    onPlayPause = { viewModel.playOrPauseAudioOverview() },
                    onSeek = { viewModel.seekAudioOverview(it) },
                    onDismiss = { viewModel.dismissAudioOverview() },
                )
            }
            AiTutorTab(
                state = state,
                input = inputText,
                onInputChange = { inputText = it },
                onSend = { attachment ->
                    viewModel.sendMessage(inputText, attachment = attachment, deckId = state.activeDeckId)
                    inputText = ""
                },
                onAcceptStudyBlocks = viewModel::acceptStudyBlocks,
                onDismissStudyProposals = viewModel::dismissStudyProposals,
                onSpeakMessage = { text -> ttsController.speak(text) },
                ttsReady = ttsReady,
                onQuickPrompt = { viewModel.sendQuickPrompt(it) },
                onCopied = {
                    snackbarHostState.showSnackbar("Copied to clipboard")
                },
                onChatInputActive = onChatInputActive,
                onHeaderExpandedChange = { headerExpanded = it },
                onModelSelected = { viewModel.setChatModel(it) },
                onThinkingLevelSelected = { viewModel.setThinkingLevel(it) },
                onCitationClick = { viewModel.openCitation(it) },
                onToggleSource = { viewModel.toggleSource(it) },
                onAddSource = { name, text -> viewModel.addSource(name, text) },
                onDeleteSource = { viewModel.deleteSource(it) },
                onViewerStep = { viewModel.stepViewer(it) },
                onViewerClose = { viewModel.closeViewer() },
                onRetryLastMessage = { viewModel.retryLastMessage() },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showTopBar) {
        Scaffold(
            snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Jevi AI") },
                    actions = {
                        IconButton(onClick = { onOpenHistory("tutor") }) {
                            Icon(Icons.Outlined.History, contentDescription = "Conversation history")
                        }
                        IconButton(onClick = {
                            viewModel.startNewConversation(AiConversationType.TUTOR)
                            inputText = ""
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = "New conversation")
                        }
                        AssistChip(
                            onClick = { showSourcesSheet = true },
                            label = {
                                Text(
                                    if (state.sources.isEmpty()) "Add sources"
                                    else "Sources (${state.sources.size})",
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(16.dp))
                            },
                        )
                    },
                )
            },
        ) { padding ->
            AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
                mainChatContent(contentModifier)
            }
        }
    } else {
        AdaptiveContentContainer(Modifier.fillMaxSize()) { contentModifier ->
            mainChatContent(contentModifier)
        }
    }

    if (showSourcesSheet) {
        SourcesBottomSheet(
            sources = state.sources,
            selectedSourceIds = state.selectedSourceIds,
            onToggleSource = { viewModel.toggleSource(it) },
            onAddSource = { name, text -> viewModel.addSource(name, text) },
            onDeleteSource = { viewModel.deleteSource(it) },
            onDismiss = { showSourcesSheet = false },
            onWebSearch = { q -> viewModel.searchWebSources(q) },
            onPickWebResult = { viewModel.addWebSource(it) },
        )
    }
}

@Composable
private fun HeaderScrollCollapseEffect(
    listState: LazyListState,
    onHeaderExpandedChange: (Boolean) -> Unit,
) {
    var wasScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8
        val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48

        if (scrolled) {
            wasScrolled = true
            onHeaderExpandedChange(false)
        } else if (atTop && wasScrolled) {
            onHeaderExpandedChange(true)
            wasScrolled = false
        }
    }
}

@Composable
private fun AiTutorTab(
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: (ChatAttachmentPayload?) -> Unit,
    onAcceptStudyBlocks: (List<com.edukasyon.studentai.core.ai.StudyBlockPayload>) -> Unit = {},
    onDismissStudyProposals: () -> Unit = {},
    onRetryLastMessage: () -> Unit = {},
    onSpeakMessage: (String) -> Unit = {},
    ttsReady: Boolean = false,
    onQuickPrompt: (String) -> Unit,
    onCopied: suspend () -> Unit,
    onChatInputActive: (Boolean) -> Unit = {},
    onHeaderExpandedChange: (Boolean) -> Unit = {},
    onModelSelected: (com.edukasyon.studentai.domain.model.AiModel) -> Unit = {},
    onThinkingLevelSelected: (com.edukasyon.studentai.domain.model.ThinkingLevel) -> Unit = {},
    onCitationClick: (com.edukasyon.studentai.domain.model.CitedChunkView) -> Unit = {},
    onToggleSource: (String) -> Unit = {},
    onAddSource: (String, String) -> Unit = { _, _ -> },
    onDeleteSource: (String) -> Unit = {},
    onViewerStep: (Int) -> Unit = {},
    onViewerClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingAttachment by remember { mutableStateOf<ChatAttachmentPayload?>(null) }
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
    HeaderScrollCollapseEffect(listState, onHeaderExpandedChange)
    val imeVisible = rememberImeVisible()
    val adaptiveWidth = rememberAdaptiveWidth()
    val chatMaxWidth = rememberChatContentMaxWidth()
    val showPromptSidePanel = adaptiveWidth == AdaptiveWidth.Expanded && !imeVisible

    LaunchedEffect(imeVisible) {
        onChatInputActive(imeVisible)
    }

    DisposableEffect(Unit) {
        onDispose { onChatInputActive(false) }
    }

    LaunchedEffect(state.messages.size, state.isLoading, imeVisible) {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (chatMaxWidth != Dp.Unspecified) {
                            Modifier.widthIn(max = chatMaxWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    if (state.messages.isEmpty()) {
                        item {
                            val welcomeMessage =
                                "Hi! I'm Jevi, your AI study buddy. Ask me anything — I'll explain concepts, help with homework, and cheer you on! 💪"
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GizmoChatBubble(
                                    message = welcomeMessage,
                                    isUser = false,
                                    sender = "Jevi",
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(welcomeMessage))
                                        scope.launch { onCopied() }
                                    },
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                "NotebookLM Studio",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            )
                                        }
                                        Text(
                                            "Add notes, PDFs, or web links to Sources. Jevi grounds answers with [1] clickable citations. Use Studio to generate:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            listOf(
                                                "🎙️  Audio Overviews — two-host podcast deep dives",
                                                "📑  Study Guides — structured chapter summaries",
                                                "🃏  Flashcards — key concept cards from sources",
                                                "📝  Practice Quizzes — test your knowledge",
                                            ).forEach { line ->
                                                Text(
                                                    line,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                )
                                            }
                                        }
                                    }
                                }
                                if (!showPromptSidePanel) {
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
                    }
                    items(state.messages, key = { "${it.timestamp}-${it.content.hashCode()}" }) { msg ->
                        GizmoChatBubble(
                            message = msg.content,
                            isUser = msg.isUser,
                            sender = msg.sender,
                            attachmentName = msg.attachmentName,
                            attachmentIsImage = msg.attachmentIsImage,
                            reasoning = msg.reasoning,
                            citations = msg.citations,
                            onCitationClick = if (!msg.isUser && msg.citations.isNotEmpty()) {
                                { onCitationClick(it) }
                            } else {
                                null
                            },
                            onCopy = if (!msg.isUser) {
                                {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    scope.launch { onCopied() }
                                }
                            } else {
                                null
                            },
                            onSpeak = if (!msg.isUser && ttsReady) {
                                { onSpeakMessage(msg.content) }
                            } else {
                                null
                            },
                        )
                    }
                    if (state.isLoading && state.loadingTool == AiTool.TUTOR) {
                        item {
                            JeviThinkingIndicator(
                                reasoning = state.streamingReasoning,
                            )
                        }
                    }
                    if (!state.isLoading && state.error != null) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Jevi couldn't respond",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = state.error!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = onRetryLastMessage,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError,
                                        ),
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                    if (!state.isLoading && state.studyProposals.isNotEmpty()) {
                        item {
                            StudyBlockProposalCard(
                                blocks = state.studyProposals,
                                onAccept = onAcceptStudyBlocks,
                                onDismiss = onDismissStudyProposals,
                            )
                        }
                    }
                    if (!state.isLoading && state.followUps.isNotEmpty()) {
                        item {
                            SuggestFollowupsRow(
                                items = state.followUps,
                                onPick = onInputChange,
                            )
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth(),
                ) {
                    attachError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    JeviChatInputBar(
                        input = input,
                        onInputChange = onInputChange,
                        selectedModel = state.selectedChatModel,
                        onModelSelected = onModelSelected,
                        thinkingLevel = state.thinkingLevel,
                        onThinkingLevelSelected = onThinkingLevelSelected,
                        stepQuotaRemaining = state.stepQuotaRemaining,
                        stepQuotaLabel = state.stepQuotaLabel,
                        stepQuotaExhausted = state.stepQuotaExhausted,
                        pendingAttachment = pendingAttachment,
                        onRemoveAttachment = { pendingAttachment = null },
                        onSend = {
                            onSend(pendingAttachment)
                            pendingAttachment = null
                        },
                        onPickImage = { imagePicker.launch("image/*") },
                        onPickFile = { filePicker.launch(arrayOf("*/*")) },
                        sources = state.sources,
                        selectedSourceIds = state.selectedSourceIds,
                        onSourceToggle = onToggleSource,
                        onAddSource = onAddSource,
                        onDeleteSource = onDeleteSource,
                        enabled = !state.isLoading,
                        isOnline = state.isOnline,
                        onFocusChanged = { focused ->
                            onChatInputActive(focused || imeVisible)
                        },
                    )
                }
            }
        }
        if (showPromptSidePanel) {
            AiQuickPromptsSidePanel(
                onQuickPrompt = onQuickPrompt,
                enabled = !state.isLoading,
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 280.dp)
                    .fillMaxHeight()
                    .padding(end = 16.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }

    // Citation passage viewer bottom sheet
    if (state.viewerChunks.isNotEmpty() && state.viewerIndex >= 0) {
        CitationPassageBottomSheet(
            chunks = state.viewerChunks,
            currentIndex = state.viewerIndex,
            highlight = state.viewerHighlight,
            onStep = onViewerStep,
            onClose = onViewerClose,
        )
    }
}

@Composable
private fun AiQuickPromptsSidePanel(
    onQuickPrompt: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Quick prompts",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            GizmoConstants.QUICK_PROMPTS.forEach { prompt ->
                SuggestionChip(
                    onClick = { onQuickPrompt(prompt) },
                    label = { Text(prompt) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookLmSourcesBar(
    sourcesCount: Int,
    selectedCount: Int,
    strictGrounding: Boolean,
    onOpenSources: () -> Unit,
    onToggleStrictGrounding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                FilledTonalButton(
                    onClick = onOpenSources,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (sourcesCount == 0) "Sources · Add"
                        else "Sources",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (sourcesCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Text("$selectedCount/$sourcesCount")
                        }
                    }
                }
                if (sourcesCount > 0) {
                    FilterChip(
                        selected = strictGrounding,
                        onClick = onToggleStrictGrounding,
                        leadingIcon = {
                            Icon(
                                if (strictGrounding) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        label = {
                            Text(
                                "Strict Grounding",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onOpenSources,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add source",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun NotebookLmStudioBar(
    onAudioOverview: () -> Unit,
    onStudyGuide: () -> Unit,
    onFlashcards: () -> Unit,
    onQuiz: () -> Unit,
    onBriefingDoc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Studio",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StudioActionCard(
                icon = Icons.Default.Podcasts,
                label = "Audio Overview",
                onClick = onAudioOverview,
            )
            StudioActionCard(
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                label = "Study Guide",
                onClick = onStudyGuide,
            )
            StudioActionCard(
                icon = Icons.Default.Style,
                label = "Flashcards",
                onClick = onFlashcards,
            )
            StudioActionCard(
                icon = Icons.Default.Quiz,
                label = "Practice Quiz",
                onClick = onQuiz,
            )
            StudioActionCard(
                icon = Icons.Default.Summarize,
                label = "Briefing Doc",
                onClick = onBriefingDoc,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NotebookLmAudioBanner(
    audioState: com.edukasyon.studentai.ui.viewmodel.AudioOverviewUiState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                        Icons.Default.Podcasts,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            "Audio Overview Deep Dive",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            "Two-host deep dive dialogue (NotebookLM style)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss audio",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (audioState) {
                is com.edukasyon.studentai.ui.viewmodel.AudioOverviewUiState.Generating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            audioState.progressNote ?: "Generating two-voice podcast episode…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is com.edukasyon.studentai.ui.viewmodel.AudioOverviewUiState.Ready -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                if (audioState.playable) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (audioState.playable) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        val progress = if (audioState.durationMs > 0)
                            audioState.positionMs.toFloat() / audioState.durationMs else 0f
                        Slider(
                            value = progress,
                            onValueChange = onSeek,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                        Text(
                            "${audioState.positionMs / 60000}:${"%02d".format(audioState.positionMs / 1000 % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is com.edukasyon.studentai.ui.viewmodel.AudioOverviewUiState.Failed -> {
                    Text(
                        "Generation failed: ${audioState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }
        }
    }
}
