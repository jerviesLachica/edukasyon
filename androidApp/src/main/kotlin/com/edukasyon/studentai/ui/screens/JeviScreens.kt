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
import com.edukasyon.studentai.core.audio.PodcastThemes
import com.edukasyon.studentai.core.audio.TtsVoices
import com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer
import com.edukasyon.studentai.core.mlkit.PdfOcrHelper
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.JeviConstants
import com.edukasyon.studentai.domain.model.JeviDeck
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.share.ShareSheet
import com.edukasyon.studentai.ui.share.ShareTarget
import com.edukasyon.studentai.ui.share.ShareViewModel
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
                    StudentAiLoader(
                        label = "Loading",
                        style = StudentAiLoaderStyle.Full,
                    )
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
    onOpenDeckTutor: (String) -> Unit = {},
    viewModel: JeviDeckDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deckDeleted by viewModel.deckDeleted.collectAsStateWithLifecycle()
    val deck = state.deck
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    val shareViewModel: ShareViewModel = hiltViewModel()
    val shareState by shareViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(showShareSheet) {
        if (showShareSheet && deck != null) shareViewModel.publish(ShareTarget.Deck(deck.id))
    }

    LaunchedEffect(deckDeleted) {
        if (deckDeleted) onBack()
    }

    // Podcast snackbar (partial coverage warning, export result) + SAF "Save as…".
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.audioMessage) {
        state.audioMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAudioMessage()
        }
    }
    val saveEpisodeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri -> viewModel.exportEpisodeToUri(uri) }
    }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
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
                        IconButton(onClick = { showShareSheet = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Share deck")
                        }
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
                    StudentAiLoader(
                        label = "Loading",
                        style = StudentAiLoaderStyle.Full,
                    )
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
                                BouncyOutlinedButton(
                                    onClick = { onOpenDeckTutor(deck.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = deck.cardCount > 0,
                                ) {
                                    Text("Ask tutor about this deck")
                                }
                            }
                        }
                    }

                    if (deck.cardCount > 0) {
                        item {
                            DeckAudioOverviewSection(
                                audioState = state.audioState,
                                themeId = state.podcastThemeId,
                                voiceAOverride = state.voiceAOverride,
                                voiceBOverride = state.voiceBOverride,
                                previewPlayingKey = state.previewPlayingKey,
                                onSelectTheme = viewModel::selectPodcastTheme,
                                onSelectVoiceA = viewModel::setPodcastVoiceA,
                                onSelectVoiceB = viewModel::setPodcastVoiceB,
                                onAuditionTheme = viewModel::auditionTheme,
                                onAuditionVoice = viewModel::auditionVoice,
                                onGenerate = viewModel::generateAudioOverview,
                                onPlayPause = viewModel::playOrPauseAudio,
                                onSeek = viewModel::seekAudio,
                                onSaveToDownloads = viewModel::saveEpisodeToDownloads,
                                onSaveAs = { saveEpisodeLauncher.launch(viewModel.episodeExportIntent()) },
                            )
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

    if (showShareSheet) {
        ShareSheet(
            onDismiss = { showShareSheet = false },
            kindLabel = "deck",
            code = shareState.code,
            envelopeOrPayloadJson = shareState.envelopeJson.orEmpty(),
            generating = shareState.generating,
            error = shareState.error,
            onRetry = shareViewModel::retry,
            confirmationLine = deck?.let { "Your \"${it.title}\" deck (${it.cardCount} cards) will be shared." },
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
    val documentPipeline = remember {
        com.edukasyon.studentai.core.document.DocumentPipeline(
            aiApiService = com.edukasyon.studentai.di.HiltEntryPoint.aiApiService(context),
            pageNoteCacheDao = com.edukasyon.studentai.di.HiltEntryPoint.pageNoteCacheDao(context),
        )
    }
    val pdfOcrHelper = remember {
        com.edukasyon.studentai.core.mlkit.PdfOcrHelper(
            com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer(),
        )
    }

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
                                StudentAiLoader(
                                    label = null,
                                    style = StudentAiLoaderStyle.Compact,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generating…")
                            }
                            state.isExtracting -> {
                                StudentAiLoader(
                                    label = null,
                                    style = StudentAiLoaderStyle.Compact,
                                    modifier = Modifier.size(18.dp),
                                )
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeckAudioOverviewSection(
    audioState: com.edukasyon.studentai.ui.viewmodel.DeckAudioState,
    themeId: String?,
    voiceAOverride: String?,
    voiceBOverride: String?,
    previewPlayingKey: String?,
    onSelectTheme: (String) -> Unit,
    onSelectVoiceA: (String?) -> Unit,
    onSelectVoiceB: (String?) -> Unit,
    onAuditionTheme: (com.edukasyon.studentai.core.audio.PodcastTheme) -> Unit,
    onAuditionVoice: (String) -> Unit,
    onGenerate: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSaveToDownloads: () -> Unit,
    onSaveAs: () -> Unit,
) {
    val theme = PodcastThemes.byId(themeId)
    val generating = audioState is com.edukasyon.studentai.ui.viewmodel.DeckAudioState.Generating
    StudentAiCard {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Podcast Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
            }
            // Theme chips: each carries its own embedded script prompt + default voices;
            // a compact ▶ sits inside the label (separate tap target from select) with
            // roomy wrap gaps.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PodcastThemes.default.forEach { t ->
                    FilterChip(
                        selected = t.id == theme.id,
                        onClick = { if (!generating) onSelectTheme(t.id) },
                        enabled = !generating,
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuditionButton(
                                    playing = previewPlayingKey?.startsWith("th_${t.id}") == true,
                                    onClick = { onAuditionTheme(t) },
                                    compact = true,
                                )
                                Text("${t.emoji} ${t.title}")
                            }
                        },
                    )
                }
            }
            Text(
                theme.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A/B voice pickers; selection overrides the theme defaults per deck+theme.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PodcastVoicePickerDropdown(
                    label = "Host voice",
                    selectedId = voiceAOverride ?: theme.voiceA,
                    defaultId = theme.voiceA,
                    enabled = !generating,
                    previewPlayingKey = previewPlayingKey,
                    onSelect = onSelectVoiceA,
                    onAudition = onAuditionVoice,
                    modifier = Modifier.weight(1f),
                )
                PodcastVoicePickerDropdown(
                    label = "Co-host voice",
                    selectedId = voiceBOverride ?: theme.voiceB,
                    defaultId = theme.voiceB,
                    enabled = !generating,
                    previewPlayingKey = previewPlayingKey,
                    onSelect = onSelectVoiceB,
                    onAudition = onAuditionVoice,
                    modifier = Modifier.weight(1f),
                )
            }
            when (audioState) {
                is com.edukasyon.studentai.ui.viewmodel.DeckAudioState.Idle -> {
                    Text(
                        "Turn this deck into a two-voice podcast episode that covers every card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BouncyButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate episode")
                    }
                }
                is com.edukasyon.studentai.ui.viewmodel.DeckAudioState.Generating -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudentAiLoader(style = StudentAiLoaderStyle.Compact, label = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            audioState.progressNote ?: "Writing and voicing the script…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is com.edukasyon.studentai.ui.viewmodel.DeckAudioState.Failed -> {
                    Text(
                        audioState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    BouncyOutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                        Text("Try again")
                    }
                }
                is com.edukasyon.studentai.ui.viewmodel.DeckAudioState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPlayPause) {
                            Icon(
                                if (audioState.playable) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (audioState.playable) "Pause" else "Play",
                            )
                        }
                        val progress = if (audioState.durationMs > 0)
                            audioState.positionMs.toFloat() / audioState.durationMs else 0f
                        Slider(
                            value = progress,
                            onValueChange = onSeek,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${audioState.positionMs / 60000}:${"%02d".format(audioState.positionMs / 1000 % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Downloadable: keep the episode in Downloads or save it anywhere.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BouncyOutlinedButton(
                            onClick = onSaveToDownloads,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save to Downloads")
                        }
                        BouncyOutlinedButton(
                            onClick = onSaveAs,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Save as…")
                        }
                    }
                }
            }
        }
    }
}

/** Host/Co-host voice dropdown over the bundled TtsVoices catalog; null selection = theme default. */
@Composable
private fun PodcastVoicePickerDropdown(
    label: String,
    selectedId: String,
    defaultId: String,
    enabled: Boolean,
    previewPlayingKey: String?,
    onSelect: (String?) -> Unit,
    onAudition: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = TtsVoices.catalog.firstOrNull { it.id == selectedId }?.label ?: selectedId
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            // Short value (just the name) — the field is half-width, full details
            // (gender · locale) live in the menu rows where there's room.
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            supportingText = {
                Text(if (selectedId == defaultId) "Theme default" else "Custom", style = MaterialTheme.typography.labelSmall)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuditionButton(
                        playing = previewPlayingKey == "v_$selectedId",
                        onClick = { onAudition(selectedId) },
                        compact = true,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Theme default · " + TtsVoices.display(defaultId)) },
                onClick = { onSelect(null); expanded = false },
            )
            TtsVoices.catalog.forEach { v ->
                DropdownMenuItem(
                    text = { Text(TtsVoices.display(v.id)) },
                    trailingIcon = {
                        AuditionButton(
                            playing = previewPlayingKey == "v_${v.id}",
                            onClick = { onAudition(v.id) },
                            compact = true,
                        )
                    },
                    onClick = { onSelect(v.id); expanded = false },
                )
            }
        }
    }
}

/** Play/stop icon that auditions one preview clip; stop state while [playing]. compact = tight slot (chips/fields). */
@Composable
private fun AuditionButton(
    playing: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val box = if (compact) 30.dp else 36.dp
    val iconSize = if (compact) 18.dp else 20.dp
    IconButton(onClick = onClick, modifier = Modifier.size(box)) {
        Icon(
            imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Stop preview" else "Preview",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}
