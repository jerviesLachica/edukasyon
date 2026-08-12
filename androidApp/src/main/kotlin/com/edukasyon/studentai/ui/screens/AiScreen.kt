package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.AiTool
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.QuizSessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onOpenScanner: () -> Unit = {},
    onOpenFlashcardStudy: () -> Unit = {},
    viewModel: AiViewModel = hiltViewModel()
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("AI Assistant") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedFeature) {
                Tab(selected = selectedFeature == 0, onClick = { selectedFeature = 0 }, text = { Text("Tutor") })
                Tab(selected = selectedFeature == 1, onClick = { selectedFeature = 1 }, text = { Text("Tools") })
                Tab(selected = selectedFeature == 2, onClick = { selectedFeature = 2 }, text = { Text("Scanner") })
            }
            when (selectedFeature) {
                0 -> AiTutorTab(
                    state = state,
                    input = inputText,
                    onInputChange = { inputText = it },
                    onSend = { viewModel.sendMessage(inputText); inputText = "" },
                    onRetry = { viewModel.retryLastAction() }
                )
                1 -> AiToolsTab(
                    input = inputText,
                    onInputChange = { inputText = it },
                    state = state,
                    viewModel = viewModel,
                    onStudy = onOpenFlashcardStudy
                )
                2 -> AiScannerTab(state, onOpenScanner, onRetry = { viewModel.retryLastAction() })
            }
        }
    }
}

@Composable
private fun AiTutorTab(
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.messages.isEmpty()) {
                item { EmptyState("AI Tutor", "Ask me anything about your subjects.") }
            }
            items(state.messages) { (sender, msg) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(msg)
                    }
                }
            }
        }
        state.error?.let { ErrorState(it, onRetry = onRetry) }
        if (state.isLoading && state.loadingTool == AiTool.TUTOR) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a question...") },
                enabled = !state.isLoading
            )
            IconButton(onClick = onSend, enabled = input.isNotBlank() && !state.isLoading) {
                Icon(Icons.Default.Send, "Send")
            }
        }
    }
}

@Composable
private fun AiToolsTab(
    input: String,
    onInputChange: (String) -> Unit,
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    viewModel: AiViewModel,
    onStudy: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedButton(onClick = onStudy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.School, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Study Due Flashcards")
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
                enabled = !state.isLoading
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolActionButton(
                    label = "Summarize",
                    loading = state.isLoading && state.loadingTool == AiTool.SUMMARIZE,
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.summarize(input) }
                )
                ToolActionButton(
                    label = "Flashcards",
                    loading = state.isLoading && state.loadingTool == AiTool.FLASHCARDS,
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.generateFlashcards(input) }
                )
                ToolActionButton(
                    label = "Quiz",
                    loading = state.isLoading && state.loadingTool == AiTool.QUIZ,
                    enabled = input.isNotBlank() && !state.isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.generateQuiz(input) }
                )
            }
        }

        state.error?.let { error ->
            item { ErrorState(error, onRetry = { viewModel.retryLastAction() }) }
        }

        state.lastSummary?.let { summary ->
            item {
                SummaryResultCard(
                    summary = summary,
                    onCopy = { clipboard.setText(AnnotatedString(summary)) }
                )
            }
        }

        if (state.generatedFlashcards.isNotEmpty()) {
            item {
                FlashcardsResultSection(
                    cards = state.generatedFlashcards,
                    saved = state.flashcardsSaved,
                    onSave = { viewModel.saveGeneratedFlashcards() },
                    onStudy = onStudy
                )
            }
        }

        state.quizSession?.let { session ->
            item {
                QuizSessionSection(
                    session = session,
                    quizSaved = state.quizSaved,
                    onSelectAnswer = viewModel::selectQuizAnswer,
                    onReveal = viewModel::revealQuizAnswer,
                    onNext = viewModel::nextQuizQuestion,
                    onRestart = viewModel::restartQuiz,
                    onSave = viewModel::saveQuizResult
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
    onClick: () -> Unit
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Summary", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onCopy) {
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
    onStudy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Generated ${cards.size} flashcards",
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!saved) {
                Button(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save to Library")
                }
            } else {
                AssistChip(
                    onClick = {},
                    label = { Text("Saved") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                )
            }
            OutlinedButton(onClick = onStudy, enabled = saved) {
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
    quizSaved: Boolean,
    onSelectAnswer: (String) -> Unit,
    onReveal: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(session.quiz.title, style = MaterialTheme.typography.titleMedium)

        if (session.finished) {
            StudentAiCard {
                Text("Quiz Complete", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("Score: ${session.correctCount}/${session.totalQuestions} (${session.scorePercent}%)")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRestart) { Text("Retry") }
                    if (!quizSaved) {
                        Button(onClick = onSave) { Text("Save Quiz") }
                    } else {
                        AssistChip(
                            onClick = {},
                            label = { Text("Quiz saved") },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }
            return
        }

        val question = session.currentQuestion ?: return
        StudentAiCard {
            Text(
                "Question ${session.currentIndex + 1} of ${session.totalQuestions}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
    onNext: () -> Unit
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
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor)
        ) {
            Text(option, Modifier.padding(12.dp))
        }
    }

    if (session.revealed) {
        val isCorrect = session.selectedAnswer?.equals(question.correctAnswer, ignoreCase = true) == true
        Text(
            if (isCorrect) "Correct!" else "Correct answer: ${question.correctAnswer}",
            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(onClick = onNext, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                if (session.currentIndex + 1 >= session.totalQuestions) "See Results"
                else "Next Question"
            )
        }
    } else {
        Button(
            onClick = onReveal,
            enabled = session.selectedAnswer != null,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Check Answer")
        }
    }
}

@Composable
private fun AiScannerTab(
    state: com.edukasyon.studentai.ui.viewmodel.AiUiState,
    onOpenScanner: () -> Unit,
    onRetry: () -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Schedule Scanner", style = MaterialTheme.typography.titleMedium)
        Text("Capture your class schedule and AI will extract your classes for review.")
        Button(onClick = onOpenScanner) {
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
        }
        if (state.isLoading && state.loadingTool == AiTool.SCANNER) {
            StarPreloader(containerSize = 48.dp, showGlow = false)
        }
        state.error?.let { ErrorState(it, onRetry = onRetry) }
    }
}
