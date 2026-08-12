package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.AiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(viewModel: AiViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFeature by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("AI Assistant") })
        TabRow(selectedTabIndex = selectedFeature) {
            Tab(selected = selectedFeature == 0, onClick = { selectedFeature = 0 }, text = { Text("Tutor") })
            Tab(selected = selectedFeature == 1, onClick = { selectedFeature = 1 }, text = { Text("Tools") })
            Tab(selected = selectedFeature == 2, onClick = { selectedFeature = 2 }, text = { Text("Scanner") })
        }
        when (selectedFeature) {
            0 -> AiTutorTab(state, inputText, { inputText = it }, { viewModel.sendMessage(inputText); inputText = "" })
            1 -> AiToolsTab(inputText, { inputText = it }, state, viewModel)
            2 -> AiScannerTab(state, viewModel)
        }
    }
}

@Composable
private fun AiTutorTab(state: com.edukasyon.studentai.ui.viewmodel.AiUiState, input: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
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
        state.error?.let { ErrorState(it) }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(input, onInputChange, Modifier.weight(1f), placeholder = { Text("Ask a question...") })
            IconButton(onClick = onSend, enabled = input.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
        }
    }
}

@Composable
private fun AiToolsTab(input: String, onInputChange: (String) -> Unit, state: com.edukasyon.studentai.ui.viewmodel.AiUiState, viewModel: AiViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(input, onInputChange, Modifier.fillMaxWidth(), label = { Text("Note content") }, minLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.summarize(input) }) { Text("Summarize") }
            Button(onClick = { viewModel.generateFlashcards(input) }) { Text("Flashcards") }
            Button(onClick = { viewModel.generateQuiz(input) }) { Text("Quiz") }
        }
        state.lastSummary?.let { StudentAiCard { Text("Summary", style = MaterialTheme.typography.titleSmall); Text(it) } }
        if (state.generatedFlashcards.isNotEmpty()) {
            Text("Generated ${state.generatedFlashcards.size} flashcards", style = MaterialTheme.typography.titleSmall)
            state.generatedFlashcards.forEach { card ->
                StudentAiCard { Text("Q: ${card.question}"); Text("A: ${card.answer}") }
            }
        }
        state.generatedQuiz?.let { quiz ->
            StudentAiCard {
                Text(quiz.title, style = MaterialTheme.typography.titleSmall)
                quiz.questions.forEach { q -> Text("• ${q.question}") }
            }
        }
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AiScannerTab(state: com.edukasyon.studentai.ui.viewmodel.AiUiState, viewModel: AiViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Schedule Scanner", style = MaterialTheme.typography.titleMedium)
        Text("Capture your class schedule and AI will extract your classes for review.")
        Button(onClick = { viewModel.analyzeScheduleImage(ByteArray(0)) }) {
            Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Scan Schedule (Demo)")
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
            Button(onClick = { viewModel.confirmScannedClasses() }) { Text("Confirm & Save") }
        }
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
