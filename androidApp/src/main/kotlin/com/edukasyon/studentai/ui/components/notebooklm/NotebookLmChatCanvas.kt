package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.domain.model.GizmoChatMessage
import com.edukasyon.studentai.ui.components.MarkdownChatText
import kotlinx.coroutines.launch

@Composable
fun NotebookLmChatTab(
    messages: List<GizmoChatMessage>,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sourcesCount: Int,
    onOpenSources: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    isGenerating: Boolean = false,
    streamingReasoning: String? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, streamingReasoning) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                // Empty canvas state (Screenshot 1)
                NotebookLmEmptyCanvas(
                    onQuickPrompt = onQuickPrompt,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Active chat stream (Screenshot 5)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(messages) { msg ->
                        NotebookLmMessageItem(
                            message = msg,
                            streamingReasoning = if (msg == messages.lastOrNull() && isGenerating) streamingReasoning else null,
                        )
                    }
                }
            }

            // Bottom pill input bar (Screenshots 1, 5)
            NotebookLmChatInputBar(
                inputText = inputText,
                onInputChange = onInputChange,
                onSend = onSend,
                sourcesCount = sourcesCount,
                onOpenSources = onOpenSources,
                isGenerating = isGenerating,
            )
        }

        // Floating down chevron button above input bar (Screenshots 5, 6)
        if (messages.isNotEmpty()) {
            val canScrollDown by remember {
                derivedStateOf { listState.canScrollForward }
            }
            if (canScrollDown) {
                Surface(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    shape = CircleShape,
                    color = NotebookLmColors.SurfaceHighest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp)
                        .size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Jump to bottom",
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotebookLmEmptyCanvas(
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        // Waving hand emoji
        Text(
            text = "👋",
            fontSize = 44.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        Text(
            text = "Let's start your\nnotebook...",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
            color = NotebookLmColors.TextPrimary,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "This is your blank canvas to understand, create, or make progress on something new. I can help you get started or you can go ahead and add your own sources.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            color = NotebookLmColors.TextSecondary,
        )

        Spacer(Modifier.height(26.dp))

        Text(
            text = "What would you like this notebook to help you do?",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
            color = NotebookLmColors.TextPrimary,
        )

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NotebookLmPromptPill(
                text = "Learn about a new topic",
                onClick = { onQuickPrompt("Help me learn about a new topic based on my sources") },
            )
            NotebookLmPromptPill(
                text = "Create something new",
                onClick = { onQuickPrompt("Create something new using the content from my sources") },
            )
            NotebookLmPromptPill(
                text = "Make progress on a project",
                onClick = { onQuickPrompt("Help me make progress on my study goals and project") },
            )
        }
    }
}

@Composable
private fun NotebookLmPromptPill(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = NotebookLmColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
    ) {
        Text(
            text = text,
            color = NotebookLmColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun NotebookLmMessageItem(
    message: GizmoChatMessage,
    streamingReasoning: String? = null,
) {
    val clipboardManager = LocalClipboardManager.current

    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Message actions top row: Pin, Copy, Thumbs
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = NotebookLmColors.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = NotebookLmColors.TextTertiary,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { clipboardManager.setText(AnnotatedString(message.content)) },
                )
                Icon(
                    Icons.Outlined.ThumbUp,
                    contentDescription = "Thumbs up",
                    tint = NotebookLmColors.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
                Icon(
                    Icons.Outlined.ThumbDown,
                    contentDescription = "Thumbs down",
                    tint = NotebookLmColors.TextTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }

            // Reasoning / Thoughts expandable section
            val reasoningText = message.reasoning ?: streamingReasoning
            if (!reasoningText.isNullOrBlank()) {
                NotebookLmThoughtsAccordion(reasoningText = reasoningText)
            }

            // AI Response Body
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
            ) {
                MarkdownChatText(
                    markdown = message.content,
                    contentColor = NotebookLmColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun NotebookLmThoughtsAccordion(
    reasoningText: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Standalone Thoughts capsule pill (Screenshots 5, 6)
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(20.dp),
            color = NotebookLmColors.SurfaceHighest,
            border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Circle-dot icon glyph
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, NotebookLmColors.TextSecondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(NotebookLmColors.TextSecondary)
                    )
                }
                Text(
                    text = "Thoughts",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = NotebookLmColors.TextPrimary,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NotebookLmColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Expanded Thoughts Timeline directly on canvas (Screenshots 5, 6)
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val steps = remember(reasoningText) { parseThoughtsSteps(reasoningText) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 2.dp),
            ) {
                steps.forEachIndexed { index, step ->
                    val isLast = index == steps.size - 1

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Left timeline line and icon node (Screenshot 5: continuous vertical line)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(),
                        ) {
                            Icon(
                                imageVector = if (step.isSearch) Icons.Outlined.FindInPage else Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = NotebookLmColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .weight(1f)
                                        .background(NotebookLmColors.Border)
                                )
                            }
                        }

                        // Right step content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (isLast) 4.dp else 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                ),
                                color = NotebookLmColors.TextPrimary,
                            )
                            if (step.description.isNotBlank()) {
                                Text(
                                    text = step.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                    ),
                                    color = NotebookLmColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ThoughtStep(val title: String, val description: String, val isSearch: Boolean = false)

private fun parseThoughtsSteps(text: String): List<ThoughtStep> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return listOf(ThoughtStep("Thinking process", text))

    val steps = mutableListOf<ThoughtStep>()
    var currentTitle = "Analyzing Sources"
    val currentBody = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        val isHeader = trimmed.startsWith("#") ||
                (trimmed.startsWith("**") && trimmed.endsWith("**")) ||
                trimmed.startsWith("- Analyzing") ||
                trimmed.startsWith("- Searched") ||
                trimmed.startsWith("- Exploring") ||
                trimmed.startsWith("- Defining") ||
                trimmed.startsWith("- Summarizing") ||
                trimmed.equals("Searched your sources", ignoreCase = true)

        if (isHeader) {
            if (currentBody.isNotEmpty()) {
                steps.add(ThoughtStep(currentTitle, currentBody.toString().trim(), currentTitle.contains("Search", ignoreCase = true)))
                currentBody.clear()
            }
            currentTitle = trimmed.removePrefix("#").removePrefix("-").replace("**", "").trim()
        } else {
            currentBody.appendLine(trimmed)
        }
    }

    if (currentBody.isNotEmpty() || currentTitle.isNotBlank()) {
        steps.add(ThoughtStep(currentTitle, currentBody.toString().trim(), currentTitle.contains("Search", ignoreCase = true)))
    }

    return steps.ifEmpty {
        listOf(ThoughtStep("Analyzing Sources", text.take(300)))
    }
}

@Composable
fun NotebookLmChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    sourcesCount: Int,
    onOpenSources: () -> Unit,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NotebookLmColors.Background)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = NotebookLmColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Text input
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Ask a question or create something",
                            color = NotebookLmColors.TextTertiary,
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        textStyle = TextStyle(
                            color = NotebookLmColors.TextPrimary,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(NotebookLmColors.BlueAccent),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Sources count pill: 📄 (13)
                Surface(
                    onClick = onOpenSources,
                    shape = RoundedCornerShape(16.dp),
                    color = NotebookLmColors.SurfaceHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = "Sources",
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "($sourcesCount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = NotebookLmColors.TextSecondary,
                        )
                    }
                }

                // Send button ↑
                val canSend = inputText.isNotBlank() && !isGenerating
                IconButton(
                    onClick = { if (canSend) onSend() },
                    enabled = canSend,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (canSend) MaterialTheme.colorScheme.primary else NotebookLmColors.SurfaceHighest),
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else NotebookLmColors.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Disclaimer
        Text(
            text = "Gemini Notebook can make mistakes, so double-check it.",
            fontSize = 11.sp,
            color = NotebookLmColors.TextTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
