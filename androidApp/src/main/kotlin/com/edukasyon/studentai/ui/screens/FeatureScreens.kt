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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.adaptive.rememberFlashcardMaxWidth
import com.edukasyon.studentai.ui.components.BouncyButton
import com.edukasyon.studentai.ui.components.BouncyOutlinedButton
import com.edukasyon.studentai.ui.components.EmptyState
import com.edukasyon.studentai.ui.components.animatedClickable
import com.edukasyon.studentai.ui.viewmodel.FlashcardStudyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardStudyScreen(
    onBack: () -> Unit,
    deckId: String? = null,
    studyAll: Boolean = false,
    onStudyAll: ((String) -> Unit)? = null,
    viewModel: FlashcardStudyViewModel = hiltViewModel(
        key = "${deckId ?: "jevi_review_all"}_$studyAll",
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val flashcardMaxWidth = rememberFlashcardMaxWidth()
    var flipped by remember { mutableStateOf(false) }
    val card = state.currentCard
    val totalCards = state.studyCards.size
    val currentNumber = (state.currentIndex + 1).coerceAtMost(totalCards)
    val deckTitle = state.deck?.title
    val deckCardCount = state.deck?.cardCount ?: 0
    val deckDueCount = state.deck?.dueCount ?: 0

    LaunchedEffect(card?.id) {
        flipped = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            deckTitle != null && state.studyAll -> "$deckTitle · Study all"
                            deckTitle != null -> "$deckTitle · Review"
                            state.studyAll -> "JEVI Study All"
                            else -> "JEVI Review"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            Column(
                contentModifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (flashcardMaxWidth != Dp.Unspecified) {
                                Modifier.widthIn(max = flashcardMaxWidth)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FlashcardStudyProgressHeader(
                            remaining = state.remaining,
                            currentNumber = currentNumber,
                            totalCards = totalCards,
                            studyAll = state.studyAll,
                        )

                        Spacer(Modifier.height(24.dp))

                        if (card == null) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    when {
                                        !state.studyAll && deckId != null && deckCardCount > 0 && deckDueCount == 0 -> {
                                            EmptyState(
                                                title = "No cards due",
                                                message = "This deck has $deckCardCount cards, but none are due for review right now.",
                                            )
                                            if (onStudyAll != null) {
                                                BouncyButton(onClick = { onStudyAll(deckId) }) {
                                                    Text("Study all $deckCardCount cards")
                                                }
                                            }
                                        }
                                        deckCardCount == 0 && totalCards == 0 -> {
                                            EmptyState(
                                                title = "No cards yet",
                                                message = "Generate flashcards with JEVI AI or add cards to this deck.",
                                            )
                                        }
                                        else -> {
                                            EmptyState(
                                                title = if (state.studyAll) "Session complete" else "All caught up!",
                                                message = if (state.studyAll) {
                                                    "You've reviewed every card in this session."
                                                } else {
                                                    "No flashcards are due for review right now."
                                                },
                                            )
                                        }
                                    }
                                }
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
        }
    }
}

@Composable
private fun FlashcardStudyProgressHeader(
    remaining: Int,
    currentNumber: Int,
    totalCards: Int,
    studyAll: Boolean = false,
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
                if (studyAll) "$remaining cards left" else "$remaining cards due",
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
