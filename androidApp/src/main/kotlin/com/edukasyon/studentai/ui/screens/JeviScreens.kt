package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.ui.viewmodel.JeviCreateViewModel
import com.edukasyon.studentai.ui.viewmodel.JeviDeckDetailViewModel
import com.edukasyon.studentai.ui.viewmodel.JeviDecksViewModel
import com.edukasyon.studentai.ui.viewmodel.JeviHomeViewModel
import com.edukasyon.studentai.ui.viewmodel.JeviQuizPhase
import com.edukasyon.studentai.ui.viewmodel.JeviQuizSource
import com.edukasyon.studentai.ui.viewmodel.JeviQuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviHubScreen(
    onOpenDecks: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenTutor: () -> Unit,
    onOpenQuiz: () -> Unit,
    viewModel: JeviHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboard = state.dashboard
    val horizontalPadding = rememberAdaptiveHorizontalPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("JEVI")
                        Text(
                            "Intelligent Revision & Virtual Instruction",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            if (state.isLoading && dashboard == null) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    contentModifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        JeviStatsRow(
                            dueCount = dashboard?.dueCount ?: 0,
                            streakDays = dashboard?.streakDays ?: 0,
                            level = dashboard?.level ?: 1,
                            xp = dashboard?.xp ?: 0,
                            xpProgress = dashboard?.xpProgress ?: 0f,
                        )
                    }

                    item {
                        val dueCount = dashboard?.dueCount ?: 0
                        BouncyButton(
                            onClick = onOpenReview,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = dueCount > 0,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (dueCount > 0) "Continue Studying ($dueCount due)" else "All caught up!",
                            )
                        }
                    }

                    item {
                        Text(
                            "Study Tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            JeviQuickAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Style,
                                label = "Decks",
                                subtitle = "${dashboard?.deckCount ?: 0} decks",
                                onClick = onOpenDecks,
                            )
                            JeviQuickAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.AutoAwesome,
                                label = "Create",
                                subtitle = "AI flashcards",
                                onClick = onOpenCreate,
                            )
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            JeviQuickAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Psychology,
                                label = "AI Tutor",
                                subtitle = "Ask JEVI",
                                onClick = onOpenTutor,
                            )
                            JeviQuickAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Outlined.Replay,
                                label = "Review",
                                subtitle = when (val due = dashboard?.dueCount ?: 0) {
                                    0 -> {
                                        val total = dashboard?.totalCards ?: 0
                                        if (total > 0) "0 due · $total cards" else "No cards yet"
                                    }
                                    else -> "$due due"
                                },
                                onClick = onOpenReview,
                            )
                        }
                    }

                    item {
                        JeviQuickAction(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Quiz,
                            label = "Quiz",
                            subtitle = when (val count = dashboard?.quizCount ?: 0) {
                                0 -> "AI quiz arena"
                                1 -> "1 saved quiz"
                                else -> "$count saved quizzes"
                            },
                            onClick = onOpenQuiz,
                        )
                    }

                    if (!dashboard?.decks.isNullOrEmpty()) {
                        item {
                            Text(
                                "Your Decks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(dashboard!!.decks.take(5), key = { it.id }) { deck ->
                            JeviDeckCard(deck = deck, onClick = onOpenDecks)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JeviStatsRow(
    dueCount: Int,
    streakDays: Int,
    level: Int,
    xp: Int,
    xpProgress: Float,
) {
    StudentAiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                JeviStatItem(Icons.Default.Schedule, dueCount.toString(), "Due")
                JeviStatItem(Icons.Default.LocalFireDepartment, "${streakDays}d", "Streak")
                JeviStatItem(Icons.Default.Star, "Lv.$level", "$xp XP")
            }
            LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun JeviStatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JeviQuickAction(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StudentAiCard(
        modifier = modifier.animatedClickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun JeviDeckCard(deck: JeviDeck, onClick: () -> Unit) {
    val deckColor = runCatching { Color(android.graphics.Color.parseColor(deck.colorHex)) }
        .getOrDefault(MaterialTheme.colorScheme.primary)

    StudentAiCard(modifier = Modifier.animatedClickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(deckColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Style, contentDescription = null, tint = deckColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(deck.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${deck.cardCount} cards · ${deck.dueCount} due",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (deck.dueCount > 0) {
                Badge { Text("${deck.dueCount}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviDecksScreen(
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit = {},
    viewModel: JeviDecksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newDeckTitle by remember { mutableStateOf("") }
    val horizontalPadding = rememberAdaptiveHorizontalPadding()

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Deck") },
            text = {
                OutlinedTextField(
                    value = newDeckTitle,
                    onValueChange = { newDeckTitle = it },
                    label = { Text("Deck name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createDeck(newDeckTitle)
                    newDeckTitle = ""
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, "New deck")
                    }
                },
            )
        },
        floatingActionButton = {
            StudentAiAddFab(
                onClick = { showCreateDialog = true },
                contentDescription = "New deck",
            )
        },
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            if (state.decks.isEmpty() && !state.isLoading) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "No decks yet",
                        message = "Create a deck or generate flashcards with JEVI AI.",
                    )
                }
            } else {
                LazyColumn(
                    contentModifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    items(state.decks, key = { it.id }) { deck ->
                        JeviDeckCard(deck = deck, onClick = { onOpenDeck(deck.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviDeckDetailScreen(
    onBack: () -> Unit,
    onReviewDue: (String) -> Unit,
    onStudyAll: (String) -> Unit,
    viewModel: JeviDeckDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deck = state.deck
    val horizontalPadding = rememberAdaptiveHorizontalPadding()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.title ?: "Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            if (state.isLoading && deck == null) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (deck == null) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(title = "Deck not found", message = "This deck may have been deleted.")
                }
            } else {
                LazyColumn(
                    contentModifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        StudentAiCard {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "${deck.cardCount} cards · ${deck.dueCount} due · ${deck.masteredCount} mastered",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BouncyButton(
                                        onClick = { onReviewDue(deck.id) },
                                        modifier = Modifier.weight(1f),
                                        enabled = deck.dueCount > 0,
                                    ) {
                                        Text(
                                            if (deck.dueCount > 0) "Review due (${deck.dueCount})"
                                            else "Review due",
                                        )
                                    }
                                    BouncyOutlinedButton(
                                        onClick = { onStudyAll(deck.id) },
                                        modifier = Modifier.weight(1f),
                                        enabled = deck.cardCount > 0,
                                    ) {
                                        Text("Study all")
                                    }
                                }
                            }
                        }
                    }

                    if (state.cards.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No cards in this deck",
                                message = "Generate flashcards with JEVI AI or move cards into this deck.",
                            )
                        }
                    } else {
                        item {
                            Text(
                                "Cards",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(state.cards, key = { it.id }) { card ->
                            JeviGeneratedCardPreview(card)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviCreateScreen(
    onBack: () -> Unit,
    viewModel: JeviCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create Flashcards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            LazyColumn(
                contentModifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                item {
                    Text(
                        "JEVI will generate flashcards from your topic or notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.topic,
                        onValueChange = viewModel::updateTopic,
                        label = { Text("Topic or note content") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isGenerating,
                    )
                }
                item {
                    if (state.decks.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedDeck = state.decks.find { it.id == state.selectedDeckId }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = selectedDeck?.title ?: "Select deck",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Save to deck") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                state.decks.forEach { deck ->
                                    DropdownMenuItem(
                                        text = { Text(deck.title) },
                                        onClick = {
                                            viewModel.selectDeck(deck.id)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    BouncyButton(
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isGenerating && state.topic.isNotBlank(),
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate with JEVI")
                        }
                    }
                }
                if (state.generatedCards.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${state.generatedCards.size} cards generated",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!state.saved) {
                                BouncyOutlinedButton(onClick = viewModel::saveToSelectedDeck) {
                                    Text("Save to deck")
                                }
                            } else {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Saved") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                    }
                    items(state.generatedCards, key = { it.id }) { card ->
                        JeviGeneratedCardPreview(card)
                    }
                }
            }
        }
    }
}

@Composable
private fun JeviGeneratedCardPreview(card: Flashcard) {
    StudentAiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Q: ${card.question}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "A: ${card.answer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            card.topic?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviQuizArenaScreen(
    onBack: () -> Unit,
    viewModel: JeviQuizViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quiz Arena")
                        Text(
                            "Generate and take practice quizzes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.phase == JeviQuizPhase.PLAYING) {
                            viewModel.backToSetup()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer(Modifier.padding(padding)) { contentModifier ->
            if (state.isGenerating) {
                Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        JeviLoadingSpinner()
                        Spacer(Modifier.height(12.dp))
                        Text("Building your quiz…")
                    }
                }
            } else if (state.phase == JeviQuizPhase.PLAYING) {
                val session = state.quizSession
                if (session != null) {
                    LazyColumn(
                        contentModifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        item {
                            QuizSessionContent(
                                session = session,
                                quizSaved = state.quizSaved,
                                onSelectAnswer = viewModel::selectQuizAnswer,
                                onReveal = viewModel::revealQuizAnswer,
                                onNext = viewModel::nextQuizQuestion,
                                onRestart = viewModel::restartQuiz,
                                onSave = viewModel::saveQuizResult,
                                onReviewMistakes = viewModel::reviewMistakes,
                                onCopyResults = {},
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentModifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.source == JeviQuizSource.DECK,
                                onClick = { viewModel.selectSource(JeviQuizSource.DECK) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("From Deck") }
                            SegmentedButton(
                                selected = state.source == JeviQuizSource.TOPIC,
                                onClick = { viewModel.selectSource(JeviQuizSource.TOPIC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("From Topic") }
                        }
                    }

                    if (state.decks.isNotEmpty()) {
                        item {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedDeck = state.decks.find { it.id == state.selectedDeckId }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(
                                    value = selectedDeck?.title ?: "Select deck",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Deck") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    state.decks.forEach { deck ->
                                        DropdownMenuItem(
                                            text = { Text(deck.title) },
                                            onClick = {
                                                viewModel.selectDeck(deck.id)
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.source == JeviQuizSource.TOPIC) {
                        item {
                            OutlinedTextField(
                                value = state.topic,
                                onValueChange = viewModel::updateTopic,
                                label = { Text("Topic or study content") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        item {
                            Text(
                                "JEVI will generate a quiz from the flashcards in your selected deck.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        BouncyButton(
                            onClick = viewModel::generate,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isGenerating,
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.source == JeviQuizSource.DECK) "Generate from Deck"
                                else "Generate Quiz",
                            )
                        }
                    }

                    if (state.savedQuizzes.isNotEmpty()) {
                        item {
                            Text(
                                "Saved Quizzes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(state.savedQuizzes, key = { it.id }) { quiz ->
                            JeviSavedQuizCard(
                                quiz = quiz,
                                onClick = { viewModel.startSavedQuiz(quiz.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JeviSavedQuizCard(quiz: Quiz, onClick: () -> Unit) {
    StudentAiCard(modifier = Modifier.animatedClickable(onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(quiz.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (quiz.questions.isNotEmpty()) "${quiz.questions.size} questions" else "Tap to play",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Start quiz")
        }
    }
}
