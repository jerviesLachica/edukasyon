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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NoteAdd
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    var headerExpanded by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
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
                        Icon(Icons.Outlined.NoteAdd, contentDescription = "New conversation")
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
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            Column(
                contentModifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                GizmoCompanionHeader(
                    gizmo = state.gizmo,
                    isOnline = state.isOnline,
                    expanded = headerExpanded,
                    onToggleExpanded = { headerExpanded = !headerExpanded },
                )
                AiTutorTab(
                    state = state,
                    input = inputText,
                    onInputChange = { inputText = it },
                    onSend = { attachment ->
                        viewModel.sendMessage(inputText, attachment = attachment)
                        inputText = ""
                    },
                    onQuickPrompt = { viewModel.sendQuickPrompt(it) },
                    onCopied = {
                        snackbarHostState.showSnackbar("Copied to clipboard")
                    },
                    onChatInputActive = onChatInputActive,
                    onHeaderExpandedChange = { headerExpanded = it },
                    onModelSelected = { viewModel.setChatModel(it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
    onQuickPrompt: (String) -> Unit,
    onCopied: suspend () -> Unit,
    onChatInputActive: (Boolean) -> Unit = {},
    onHeaderExpandedChange: (Boolean) -> Unit = {},
    onModelSelected: (com.edukasyon.studentai.domain.model.AiModel) -> Unit = {},
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
                            JeviThinkingIndicator()
                        }
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
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
