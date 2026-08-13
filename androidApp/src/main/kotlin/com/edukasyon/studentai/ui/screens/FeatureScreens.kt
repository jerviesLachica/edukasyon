package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.ui.components.BouncyButton
import com.edukasyon.studentai.ui.components.BouncyOutlinedButton
import com.edukasyon.studentai.ui.components.EmptyState
import com.edukasyon.studentai.ui.components.StudentAiCard
import com.edukasyon.studentai.ui.components.animatedClickable
import com.edukasyon.studentai.ui.viewmodel.ChatViewModel
import com.edukasyon.studentai.ui.viewmodel.FlashcardStudyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Groups") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Default.Add, "New chat")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyState(
                title = "No conversations",
                message = "Start a study group chat to collaborate with classmates.",
                actionLabel = "New Chat",
                onAction = { showNew = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                items(conversations) { conv ->
                    StudentAiCard(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenChat(conv.id) }
                    ) {
                        Text(conv.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (conv.isGroup) "Group" else "Direct",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New Study Group") },
            text = {
                OutlinedTextField(title, { title = it }, label = { Text("Group name") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        viewModel.createConversation(title) { id -> onOpenChat(id) }
                        showNew = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages(conversationId).collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(conversationId) { viewModel.setActiveConversation(conversationId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.reversed()) { msg ->
                    val isMe = msg.senderId == "me"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMe) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(msg.content)
                                Text(
                                    timeFormat.format(Date(msg.sentAt)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    input,
                    { input = it },
                    Modifier.weight(1f),
                    placeholder = { Text("Message...") }
                )
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.sendMessage(conversationId, input)
                            input = ""
                        }
                    }
                ) { Icon(Icons.Default.Send, "Send") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardStudyScreen(
    onBack: () -> Unit,
    viewModel: FlashcardStudyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var flipped by remember { mutableStateOf(false) }
    val card = state.currentCard
    val totalCards = state.dueCards.size
    val currentNumber = (state.currentIndex + 1).coerceAtMost(totalCards)

    LaunchedEffect(card?.id) {
        flipped = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlashcardStudyProgressHeader(
                remaining = state.remaining,
                currentNumber = currentNumber,
                totalCards = totalCards,
            )

            Spacer(Modifier.height(24.dp))

            if (card == null) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState("All caught up!", "No flashcards due for review right now.")
                }
            } else {
                AnimatedContent(
                    targetState = card.id,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(200)))
                    },
                    label = "cardTransition",
                ) { _ ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FlashcardFlipCard(
                            question = card.question,
                            answer = card.answer,
                            topic = card.topic,
                            isFlipped = flipped,
                            onFlip = { flipped = !flipped },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp, max = 360.dp),
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (flipped) "How well did you know this?" else "Tap card to reveal answer",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(
                    visible = flipped,
                    enter = fadeIn(tween(250)) + slideInHorizontally { it / 4 },
                    exit = fadeOut(tween(150)),
                ) {
                    FlashcardRatingBar(
                        onAgain = { flipped = false; viewModel.rate(card, 0) },
                        onHard = { flipped = false; viewModel.rate(card, 1) },
                        onGood = { flipped = false; viewModel.rate(card, 3) },
                        onEasy = { flipped = false; viewModel.rate(card, 5) },
                    )
                }

                if (!flipped) {
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun FlashcardStudyProgressHeader(
    remaining: Int,
    currentNumber: Int,
    totalCards: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$remaining cards due",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (totalCards > 0) {
                Text(
                    "$currentNumber / $totalCards",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (totalCards > 0) {
            LinearProgressIndicator(
                progress = { currentNumber.toFloat() / totalCards.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun FlashcardFlipCard(
    question: String,
    answer: String,
    topic: String?,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "cardFlipRotation",
    )
    val scale by animateFloatAsState(
        targetValue = if (rotation in 75f..105f) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardFlipScale",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        FlashcardStackLayer(offsetY = 12.dp, alpha = 0.18f)
        FlashcardStackLayer(offsetY = 6.dp, alpha = 0.32f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    scaleX = scale
                    scaleY = scale
                    cameraDistance = 12f * density
                }
                .animatedClickable(onClick = onFlip, haptic = true),
        ) {
            FlashcardFace(
                label = "Question",
                text = question,
                topic = topic,
                visible = rotation <= 90f,
                modifier = Modifier.matchParentSize(),
            )
            FlashcardFace(
                label = "Answer",
                text = answer,
                topic = topic,
                visible = rotation > 90f,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
private fun FlashcardStackLayer(offsetY: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 360.dp)
            .offset(y = offsetY)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
private fun FlashcardFace(
    label: String,
    text: String,
    topic: String?,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    )

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = if (visible) 1f else 0f }
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ) {
                    Text(
                        text = label.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (!topic.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun FlashcardRatingBar(
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    onEasy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BouncyOutlinedButton(
                onClick = onAgain,
                modifier = Modifier.weight(1f),
            ) {
                Text("Again", style = MaterialTheme.typography.labelMedium)
            }
            BouncyOutlinedButton(
                onClick = onHard,
                modifier = Modifier.weight(1f),
            ) {
                Text("Hard", style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BouncyButton(
                onClick = onGood,
                modifier = Modifier.weight(1f),
            ) {
                Text("Good", style = MaterialTheme.typography.labelMedium)
            }
            BouncyButton(
                onClick = onEasy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Easy", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
