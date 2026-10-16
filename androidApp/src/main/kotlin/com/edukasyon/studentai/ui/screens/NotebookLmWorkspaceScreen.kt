package com.edukasyon.studentai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.AiConversationType
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.domain.model.QuestionType
import com.edukasyon.studentai.ui.components.notebooklm.*
import com.edukasyon.studentai.ui.viewmodel.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmWorkspaceScreen(
    onOpenDecks: () -> Unit = {},
    onOpenReview: () -> Unit = {},
    onOpenCreate: () -> Unit = {},
    onOpenTutor: () -> Unit = {},
    onOpenQuiz: () -> Unit = {},
    onOpenDeckDetail: (String) -> Unit = {},
    onOpenHistory: (String) -> Unit = {},
    onChatInputActive: (Boolean) -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateSchedule: () -> Unit = {},
    onNavigatePlanner: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    aiViewModel: AiViewModel = sharedAiViewModel(),
    quizViewModel: JeviQuizViewModel = hiltViewModel(),
    docToStudyViewModel: DocToStudyViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val quizState by quizViewModel.uiState.collectAsStateWithLifecycle()
    val docState by docToStudyViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notebookTitle by remember { mutableStateOf("Untitled") }
    var selectedTab by remember { mutableStateOf(NotebookLmTab.CHAT) }
    var chatInputText by remember { mutableStateOf("") }

    var showAudioCustomizer by remember { mutableStateOf(false) }
    var showQuizCustomizer by remember { mutableStateOf(false) }
    var activeRunningQuiz by remember { mutableStateOf<Quiz?>(null) }
    var activeMindMap by remember { mutableStateOf<MindMapNode?>(null) }
    var activeSlideDeck by remember { mutableStateOf<List<SlideItem>?>(null) }
    var activeReport by remember { mutableStateOf<String?>(null) }
    var activeFlashcards by remember { mutableStateOf<List<Flashcard>?>(null) }
    var isAudioPlayerVisible by remember { mutableStateOf(true) }

    // Synchronize extracted documents into active sources
    LaunchedEffect(docState.extractedMarkdown) {
        val markdown = docState.extractedMarkdown
        if (!markdown.isNullOrBlank()) {
            val names = docState.fileNames.joinToString(", ").ifBlank { "Imported Study Source" }
            aiViewModel.addSource(names, markdown)
        }
    }

    // 1. Interactive Quiz Runner
    val currentQuiz = activeRunningQuiz ?: aiState.generatedQuiz
    if (currentQuiz != null) {
        NotebookLmInteractiveQuizRunner(
            quiz = currentQuiz,
            onBack = {
                activeRunningQuiz = null
                aiViewModel.clearStatusMessage()
            },
            sourcesCount = aiState.sources.size,
        )
        return
    }

    // 2. Interactive Mind Map Viewer
    if (activeMindMap != null) {
        NotebookLmMindMapViewer(
            title = "$notebookTitle Mind Map",
            rootNode = activeMindMap!!,
            onBack = { activeMindMap = null },
        )
        return
    }

    // 3. Interactive Slide Deck Viewer
    if (activeSlideDeck != null) {
        NotebookLmSlideDeckViewer(
            deckTitle = "$notebookTitle Slide Deck",
            slides = activeSlideDeck!!,
            onBack = { activeSlideDeck = null },
        )
        return
    }

    // 4. Interactive Report Viewer
    if (activeReport != null) {
        NotebookLmReportViewer(
            title = "$notebookTitle Briefing Report",
            reportMarkdown = activeReport!!,
            sourcesCount = aiState.sources.size,
            onBack = { activeReport = null },
        )
        return
    }

    // 5. Interactive Flashcards Player
    if (activeFlashcards != null) {
        NotebookLmFlashcardsPlayer(
            deckTitle = "$notebookTitle Flashcards",
            cards = activeFlashcards!!,
            onBack = { activeFlashcards = null },
        )
        return
    }

    // Prepare Studio output items
    val studioOutputs = remember(aiState.sources, quizState.savedQuizzes, aiState.audioOverviewState) {
        val list = mutableListOf<StudioOutputItem>()
        val srcCount = aiState.sources.size

        val ready = aiState.audioOverviewState as? AudioOverviewUiState.Ready
        if (ready != null) {
            val durMin = ready.durationMs / 60000
            val durSec = (ready.durationMs % 60000) / 1000
            list.add(
                StudioOutputItem(
                    id = "audio_latest",
                    title = "Audio Overview",
                    type = StudioArtifactType.AUDIO_OVERVIEW,
                    metadataText = String.format("%02d:%02d · $srcCount sources", durMin, durSec),
                    isPlaying = ready.playable,
                )
            )
        }

        quizState.savedQuizzes.forEach { q ->
            list.add(
                StudioOutputItem(
                    id = q.id,
                    title = q.title.ifBlank { "Quiz" },
                    type = StudioArtifactType.QUIZ,
                    metadataText = "$srcCount sources",
                )
            )
        }

        list
    }

    Scaffold(
        containerColor = NotebookLmColors.Background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NotebookLmColors.Background)
            ) {
                // Top App Bar (Swirl Logo, Title, Actions)
                NotebookLmHeader(
                    notebookTitle = notebookTitle,
                    onTitleChange = { notebookTitle = it },
                    onNewNotebook = {
                        aiViewModel.startNewConversation(AiConversationType.TUTOR)
                        notebookTitle = "Untitled"
                    },
                    onOpenSettings = {},
                    onShare = {},
                    onExport = {},
                    onDelete = {
                        aiViewModel.startNewConversation(AiConversationType.TUTOR)
                        notebookTitle = "Untitled"
                    },
                    onNavigateHome = onNavigateHome,
                    onNavigateSchedule = onNavigateSchedule,
                    onNavigatePlanner = onNavigatePlanner,
                    onNavigateProfile = onNavigateProfile,
                    onOpenDecks = onOpenDecks,
                )

                // 3 Segmented Tabs: Sources | Chat | Studio
                NotebookLmTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    sourcesCount = aiState.sources.size,
                )
            }
        },
        bottomBar = {
            // Persistent Docked Audio Player (only visible when audio is ready)
            val ready = aiState.audioOverviewState as? AudioOverviewUiState.Ready
            if (isAudioPlayerVisible && ready != null) {
                NotebookLmDockedAudioPlayer(
                    title = "Audio Overview",
                    isPlaying = ready.playable,
                    positionMs = ready.positionMs,
                    durationMs = ready.durationMs,
                    onPlayPause = { aiViewModel.playOrPauseAudioOverview() },
                    onSeek = { aiViewModel.seekAudioOverview(it) },
                    onRewind10 = {
                        val current = ready.positionMs
                        val dur = ready.durationMs
                        val newPos = (current - 10000).coerceAtLeast(0)
                        val ratio = if (dur > 0) newPos.toFloat() / dur else 0f
                        aiViewModel.seekAudioOverview(ratio)
                    },
                    onForward10 = {
                        val current = ready.positionMs
                        val dur = ready.durationMs
                        val newPos = (current + 10000).coerceAtMost(dur)
                        val ratio = if (dur > 0) newPos.toFloat() / dur else 0f
                        aiViewModel.seekAudioOverview(ratio)
                    },
                    onSpeedChange = { /* speed updated */ },
                    onDismiss = { isAudioPlayerVisible = false },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                NotebookLmTab.SOURCES -> {
                    NotebookLmSourcesTab(
                        sources = aiState.sources,
                        selectedSourceIds = aiState.selectedSourceIds,
                        onToggleSource = { aiViewModel.toggleSource(it) },
                        onSelectAll = { aiViewModel.selectAllSources(it) },
                        onDeleteSource = { aiViewModel.deleteSource(it) },
                        onDeleteSources = { aiViewModel.deleteSources(it) },
                        onAddFiles = { uris ->
                            docToStudyViewModel.selectDocuments(context, uris)
                            // Also register as sources
                            uris.forEachIndexed { i, uri ->
                                val name = uri.lastPathSegment ?: "document_${i + 1}"
                                aiViewModel.addSource(name, "Document extracted from $name")
                            }
                        },
                        onAddText = { name, text ->
                            aiViewModel.addSource(name, text)
                        },
                        onSearchWeb = { query ->
                            scope.launch {
                                val results = aiViewModel.searchWebSources(query)
                                results.forEach { aiViewModel.addWebSource(it) }
                            }
                        },
                    )
                }

                NotebookLmTab.CHAT -> {
                    NotebookLmChatTab(
                        messages = aiState.messages,
                        inputText = chatInputText,
                        onInputChange = {
                            chatInputText = it
                            onChatInputActive(it.isNotBlank())
                        },
                        onSend = {
                            if (chatInputText.isNotBlank()) {
                                aiViewModel.sendMessage(chatInputText)
                                chatInputText = ""
                                onChatInputActive(false)
                            }
                        },
                        sourcesCount = aiState.sources.size,
                        onOpenSources = { selectedTab = NotebookLmTab.SOURCES },
                        onQuickPrompt = { prompt ->
                            aiViewModel.sendMessage(prompt)
                        },
                        isGenerating = aiState.isLoading,
                        streamingReasoning = aiState.streamingReasoning,
                    )
                }

                NotebookLmTab.STUDIO -> {
                    val isGeneratingAudio = aiState.audioOverviewState is AudioOverviewUiState.Generating
                    val audioProgressNote = (aiState.audioOverviewState as? AudioOverviewUiState.Generating)?.progressNote

                    val handleSelectTile: (StudioArtifactType) -> Unit = { tileType ->
                        when (tileType) {
                            StudioArtifactType.AUDIO_OVERVIEW -> showAudioCustomizer = true
                            StudioArtifactType.QUIZ -> showQuizCustomizer = true
                            StudioArtifactType.FLASHCARDS -> {
                                if (aiState.sources.isEmpty()) {
                                    selectedTab = NotebookLmTab.SOURCES
                                } else if (aiState.generatedFlashcards.isNotEmpty()) {
                                    activeFlashcards = aiState.generatedFlashcards
                                } else {
                                    aiViewModel.generateFlashcardsFromSources()
                                    selectedTab = NotebookLmTab.CHAT
                                }
                            }
                            StudioArtifactType.REPORT -> {
                                if (aiState.sources.isEmpty()) {
                                    selectedTab = NotebookLmTab.SOURCES
                                } else {
                                    aiViewModel.generateBriefingDocFromSources()
                                    selectedTab = NotebookLmTab.CHAT
                                }
                            }
                            StudioArtifactType.MIND_MAP -> {
                                if (aiState.sources.isEmpty()) {
                                    selectedTab = NotebookLmTab.SOURCES
                                } else {
                                    aiViewModel.sendMessage("Create a structured Mind Map outline summarizing all core concepts and connections from my active sources.")
                                    selectedTab = NotebookLmTab.CHAT
                                }
                            }
                            StudioArtifactType.SLIDE_DECK -> {
                                if (aiState.sources.isEmpty()) {
                                    selectedTab = NotebookLmTab.SOURCES
                                } else {
                                    aiViewModel.sendMessage("Create a comprehensive 5-slide study presentation outline from my active sources, including speaker notes for each slide.")
                                    selectedTab = NotebookLmTab.CHAT
                                }
                            }
                            StudioArtifactType.NOTE -> {
                                selectedTab = NotebookLmTab.CHAT
                                aiViewModel.sendMessage("Summarize key study points from my sources into a structured note.")
                            }
                        }
                    }

                    NotebookLmStudioTab(
                        sourcesCount = aiState.sources.size,
                        isGeneratingAudio = isGeneratingAudio,
                        audioProgressNote = audioProgressNote,
                        isGeneratingQuiz = quizState.isGenerating,
                        outputs = studioOutputs,
                        onSelectTile = handleSelectTile,
                        onOpenOutput = { item ->
                            when (item.type) {
                                StudioArtifactType.AUDIO_OVERVIEW -> {
                                    isAudioPlayerVisible = true
                                    aiViewModel.playOrPauseAudioOverview()
                                }
                                StudioArtifactType.QUIZ -> {
                                    val found = quizState.savedQuizzes.find { it.id == item.id }
                                    if (found != null) {
                                        activeRunningQuiz = found
                                    } else {
                                        showQuizCustomizer = true
                                    }
                                }
                                StudioArtifactType.REPORT -> {
                                    handleSelectTile(StudioArtifactType.REPORT)
                                }
                                StudioArtifactType.MIND_MAP -> {
                                    handleSelectTile(StudioArtifactType.MIND_MAP)
                                }
                                StudioArtifactType.SLIDE_DECK -> {
                                    handleSelectTile(StudioArtifactType.SLIDE_DECK)
                                }
                                StudioArtifactType.FLASHCARDS -> {
                                    handleSelectTile(StudioArtifactType.FLASHCARDS)
                                }
                                else -> {}
                            }
                        },
                        onDeleteOutput = { item ->
                            if (item.type == StudioArtifactType.AUDIO_OVERVIEW) {
                                aiViewModel.dismissAudioOverview()
                            }
                        },
                        onAddNote = {
                            selectedTab = NotebookLmTab.CHAT
                        },
                    )
                }
            }
        }
    }

    // Modal Sheet 1: Customize Audio Overview (Screenshots 6, 7)
    if (showAudioCustomizer) {
        NotebookLmAudioOverviewCustomizerSheet(
            onDismissRequest = { showAudioCustomizer = false },
            sourcesCount = aiState.sources.size,
            onGenerateNow = { format, length, prompt ->
                showAudioCustomizer = false
                aiViewModel.generateAudioOverviewFromSources()
                isAudioPlayerVisible = true
            },
        )
    }

    // Modal Sheet 2: Customize Quiz (Screenshots 6, 7)
    if (showQuizCustomizer) {
        NotebookLmQuizCustomizerSheet(
            onDismissRequest = { showQuizCustomizer = false },
            sourcesCount = aiState.sources.size,
            onGenerateNow = { count, difficulty, types, topic ->
                showQuizCustomizer = false
                val promptText = if (topic.isNotBlank()) topic else "Create $difficulty practice quiz from sources"
                quizViewModel.updateTopic(promptText)
                quizViewModel.generate()
            },
        )
    }
}

