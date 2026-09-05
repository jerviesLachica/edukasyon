package com.edukasyon.studentai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer
import com.edukasyon.studentai.core.mlkit.PdfOcrHelper
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviConstants
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.domain.model.QuestionType
import androidx.compose.ui.platform.LocalContext
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
                        Text("JEVI", maxLines = 1)
                        Text(
                            "Intelligent Revision & Virtual Instruction",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                                subtitle = "${dashboard?.deckCount ?: 0} ${if ((dashboard?.deckCount ?: 0) == 1) "deck" else "decks"}",
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
                progress = { xpProgress.coerceIn(0f, 1f) },
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
        Spacer(Modifier.height(2.dp))
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
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
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
    val deckDeleted by viewModel.deckDeleted.collectAsStateWithLifecycle()
    val deck = state.deck
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deckDeleted) {
        if (deckDeleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.title ?: "Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (deck != null && deck.id != JeviConstants.DEFAULT_DECK_ID) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete deck")
                        }
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

    if (showDeleteDialog && deck != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete deck") },
            text = { Text("Are you sure you want to delete \"${deck.title}\"? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteCurrentDeck()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JeviCreateScreen(
    onBack: () -> Unit,
    viewModel: JeviCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val snackbarHostState = remember { SnackbarHostState() }
    val pdfOcrHelper = remember { PdfOcrHelper(MlKitTextRecognizer()) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            } ?: "document.pdf"
            val text = pdfOcrHelper.extractTextFromPdf(context, uri, fileName)
            text?.let { viewModel.generateFromDocument(it) }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = pdfOcrHelper.recognizeImage(context, uri)
            text?.let { viewModel.generateFromDocument(it) }
        }
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
                        "Type a topic, paste content, or import a PDF/image scan.",
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
                        enabled = !state.isGenerating && !state.isExtracting,
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BouncyOutlinedButton(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGenerating && !state.isExtracting,
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Pick PDF")
                        }
                        BouncyOutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGenerating && !state.isExtracting,
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Pick Image")
                        }
                    }
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
                        enabled = !state.isGenerating && !state.isExtracting && state.topic.isNotBlank(),
                    ) {
                        when {
                            state.isGenerating -> {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Generating…")
                            }
                            state.isExtracting -> {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Reading document…")
                            }
                            else -> {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate with JEVI")
                            }
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                    items(state.generatedCards, key = { it.id }) { card ->
                        EditableFlashcardPreview(
                            card = card,
                            onUpdate = { q, a -> viewModel.updateCard(card.id, q, a) },
                            onRemove = { viewModel.removeCard(card.id) },
                        )
                    }
                    item {
                        var showAdd by remember { mutableStateOf(false) }
                        var questionText by remember { mutableStateOf("") }
                        var answerText by remember { mutableStateOf("") }

                        if (showAdd) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Add card", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = questionText,
                                        onValueChange = { questionText = it },
                                        label = { Text("Question") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                    )
                                    OutlinedTextField(
                                        value = answerText,
                                        onValueChange = { answerText = it },
                                        label = { Text("Answer") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BouncyButton(onClick = {
                                            viewModel.addCard(questionText, answerText)
                                            questionText = ""
                                            answerText = ""
                                            showAdd = false
                                        }) { Text("Add") }
                                        TextButton(onClick = { showAdd = false }) { Text("Cancel") }
                                    }
                                }
                            }
                        }
                        BouncyOutlinedButton(
                            onClick = { showAdd = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add card manually")
                        }
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

@Composable
private fun EditableFlashcardPreview(
    card: Flashcard,
    onUpdate: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var qText by remember { mutableStateOf(card.question) }
    var aText by remember { mutableStateOf(card.answer) }

    StudentAiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = aText,
                    onValueChange = { aText = it },
                    label = { Text("Answer") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BouncyButton(onClick = {
                        onUpdate(qText, aText)
                        isEditing = false
                    }, modifier = Modifier.weight(1f)) { Text("Save") }
                    TextButton(onClick = {
                        qText = card.question
                        aText = card.answer
                        isEditing = false
                    }) { Text("Cancel") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Q: ${card.question}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "A: ${card.answer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableQuizQuestionPreview(
    question: QuizQuestion,
    onUpdate: (question: String, options: List<String>, correctAnswer: String) -> Unit,
    onRemove: () -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var qText by remember { mutableStateOf(question.question) }
    var options by remember { mutableStateOf(question.options.toMutableList()) }
    var correctAnswer by remember { mutableStateOf(question.correctAnswer) }
    var correctAnswerExpanded by remember { mutableStateOf(false) }

    StudentAiCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text("Question") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Options editor
                for (index in options.indices) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${('A' + index).toChar()}) ",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(20.dp),
                        )
                        OutlinedTextField(
                            value = options[index],
                            onValueChange = { options[index] = it },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (index > 0) {
                            IconButton(onClick = {
                                options.removeAt(index)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove option")
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = {
                        options.add("")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add option")
                    }
                }

                // Correct answer selector (stores the actual answer text)
                Text(
                    "Correct answer:",
                    style = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenuBox(
                    expanded = correctAnswerExpanded,
                    onExpandedChange = { correctAnswerExpanded = it },
                ) {
                    OutlinedTextField(
                        value = correctAnswer,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Correct answer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(correctAnswerExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = correctAnswerExpanded,
                        onDismissRequest = { correctAnswerExpanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    correctAnswer = option
                                    correctAnswerExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = {
                        isEditing = false
                        if (options.all { it.isNotBlank() } && correctAnswer.isNotBlank()) {
                            onUpdate(qText, options.toList(), correctAnswer)
                        }
                    }) {
                        Text("Save")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        qText = question.question
                        options = question.options.toMutableList()
                        correctAnswer = question.correctAnswer
                        isEditing = false
                    }) {
                        Text("Cancel")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        question.question,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    question.options.forEachIndexed { index, option ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${('A' + index).toChar()}) ",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(20.dp),
                            )
                            Text(option)
                            if (option == question.correctAnswer) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Correct answer",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddQuizQuestionDialog(
    onDismiss: () -> Unit,
    onConfirm: (question: String, options: List<String>, correctAnswer: String) -> Unit,
) {
    var questionText by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(mutableListOf("", "")) }
    var correctAnswer by remember { mutableStateOf("") }
    var isCorrectExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Question") },
        text = {
            // Scrollable: the option list can grow and the dialog clips on
            // small screens / landscape / with the keyboard open otherwise.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                for (index in options.indices) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${('A' + index).toChar()}) ",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(20.dp),
                        )
                        OutlinedTextField(
                            value = options[index],
                            onValueChange = { newVal ->
                                options[index] = newVal
                            },
                            label = { Text("Option ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        if (index > 0) {
                            IconButton(onClick = {
                                options.removeAt(index)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove option")
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = {
                        options.add("")
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add option")
                    }
                }

                Text(
                    "Correct answer:",
                    style = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenuBox(
                    expanded = isCorrectExpanded,
                    onExpandedChange = { isCorrectExpanded = it },
                ) {
                    OutlinedTextField(
                        value = correctAnswer,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Correct answer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isCorrectExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = isCorrectExpanded,
                        onDismissRequest = { isCorrectExpanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    correctAnswer = option
                                    isCorrectExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (questionText.isNotBlank() && options.all { it.isNotBlank() }) {
                        onConfirm(questionText, options.toList(), correctAnswer)
                    }
                },
            ) {
                Text("Add")
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfOcrHelper = remember { PdfOcrHelper(MlKitTextRecognizer()) }

    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            } ?: "document.pdf"
            val text = pdfOcrHelper.extractTextFromPdf(context, uri, fileName)
            text?.let { viewModel.generateFromDocument(it) }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = pdfOcrHelper.recognizeImage(context, uri)
            text?.let { viewModel.generateFromDocument(it) }
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
            } else if (state.phase == JeviQuizPhase.REVIEW) {
                LazyColumn(
                    contentModifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item {
                        Text(
                            "Review & edit your quiz before starting.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Remove questions you don't need or edit them. Add more if you want.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    items(
                        state.generatedQuiz!!.questions,
                        key = { it.id },
                    ) { question ->
                        EditableQuizQuestionPreview(
                            question = question,
                            onUpdate = { q, opts, correct ->
                                viewModel.updateQuizQuestion(question.id, q, opts, correct)
                            },
                            onRemove = { viewModel.removeQuizQuestion(question.id) },
                        )
                    }

                    item {
                        var showAddDialog by remember { mutableStateOf(false) }
                        BouncyOutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Question")
                        }

                        if (showAddDialog) {
                            AddQuizQuestionDialog(
                                onDismiss = { showAddDialog = false },
                                onConfirm = { q, opts, correct ->
                                    viewModel.addQuizQuestion(q, opts, correct)
                                    showAddDialog = false
                                },
                            )
                        }
                    }

                    item {
                        BouncyButton(
                            onClick = viewModel::startQuizFromReview,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.generatedQuiz!!.questions.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Quiz")
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BouncyOutlinedButton(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isGenerating && !state.isExtracting,
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Pick PDF")
                            }
                            BouncyOutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                enabled = !state.isGenerating && !state.isExtracting,
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Pick Image")
                            }
                        }
                    }

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
                            enabled = !state.isGenerating && !state.isExtracting,
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
