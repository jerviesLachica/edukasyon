package com.edukasyon.studentai.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.DocQuizDifficulty
import com.edukasyon.studentai.ui.viewmodel.DocStudyTarget
import com.edukasyon.studentai.ui.viewmodel.DocToStudyViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocToStudyStudioSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    initialUri: Uri? = null,
    initialText: Pair<String, String>? = null,
    viewModel: DocToStudyViewModel = hiltViewModel(),
    onOpenQuizArena: ((Quiz) -> Unit)? = null,
    onFlashcardsSaved: ((deckId: String, count: Int) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(initialUri) {
        if (initialUri != null && state.selectedUris.isEmpty() && state.extractedMarkdown == null) {
            viewModel.selectDocuments(context, listOf(initialUri))
        }
    }

    LaunchedEffect(initialText) {
        if (initialText != null && state.extractedMarkdown == null) {
            viewModel.loadDirectText(initialText.first, initialText.second)
        }
    }

    // Launchers for picking documents
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.selectDocuments(context, listOf(it)) }
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.selectDocuments(context, uris)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Header Banner
            item {
                DocStudyHeader(
                    onDismiss = onDismissRequest,
                )
            }

            // 2. Pickers (Camera / Gallery Multi / PDF)
            item {
                DocSourcePickersRow(
                    hasDocuments = state.selectedUris.isNotEmpty(),
                    isExtracting = state.isExtracting,
                    onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onPickImages = { multiImagePicker.launch("image/*") },
                )
            }

            // 3. Document Extraction Status Card
            if (state.selectedUris.isNotEmpty()) {
                item {
                    ExtractedDocStatusCard(
                        fileNames = state.fileNames,
                        isExtracting = state.isExtracting,
                        progressText = state.extractionProgressText,
                        extractedPageCount = state.extractedPageCount,
                        extractedWordCount = state.extractedWordCount,
                        isFastOcr = state.isFastOcr,
                        onClear = viewModel::clearDocuments,
                    )
                }
            }

            // Error Banner
            state.error?.let { err ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Info Banner
            state.infoMessage?.let { info ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // 4. Study Material Target Configuration
            if (!state.extractedMarkdown.isNullOrBlank()) {
                item {
                    DocStudyConfigSection(
                        target = state.target,
                        onTargetSelect = viewModel::setTarget,
                        quizCount = state.quizCount,
                        onQuizCountChange = viewModel::setQuizCount,
                        difficulty = state.quizDifficulty,
                        onDifficultyChange = viewModel::setDifficulty,
                        decks = state.decks,
                        selectedDeckId = state.selectedDeckId,
                        onDeckSelect = viewModel::selectDeck,
                        forceVision = state.forceVision,
                        onToggleForceVision = viewModel::setForceVision,
                    )
                }

                // 5. Generate Action Button
                item {
                    BouncyButton(
                        onClick = viewModel::generate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !state.isGenerating && !state.isExtracting,
                    ) {
                        if (state.isGenerating) {
                            StudentAiLoader(
                                label = null,
                                style = StudentAiLoaderStyle.Compact,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(state.generatingProgressText ?: "Generating study pack…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (state.target) {
                                    DocStudyTarget.FLASHCARDS -> "Generate Flashcards ⚡"
                                    DocStudyTarget.QUIZ -> "Generate Practice Quiz 📝"
                                    DocStudyTarget.BOTH -> "Generate Complete Study Pack ⚡"
                                },
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // 6. Interactive Flashcards Results
            if (state.generatedCards.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    GeneratedFlashcardsPreview(
                        cards = state.generatedCards,
                        isSaved = state.cardsSaved,
                        onSave = {
                            viewModel.saveFlashcards()
                            onFlashcardsSaved?.invoke(state.selectedDeckId, state.generatedCards.size)
                        },
                    )
                }
            }

            // 7. Interactive Quiz Results
            state.generatedQuiz?.let { quiz ->
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    GeneratedQuizPreview(
                        quiz = quiz,
                        isSaved = state.quizSaved,
                        onSave = viewModel::saveQuizDirectly,
                        onOpenQuizArena = {
                            if (!state.quizSaved) {
                                viewModel.saveQuizDirectly()
                            }
                            onOpenQuizArena?.invoke(quiz)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocStudyHeader(
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Doc-to-Study Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "⚡ Fast",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    "Turn notes, slides, or PDFs into quizzes & flashcards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun DocSourcePickersRow(
    hasDocuments: Boolean,
    isExtracting: Boolean,
    onPickPdf: () -> Unit,
    onPickImages: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (hasDocuments) "Change Source Document" else "Choose Source Document",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Multi Image Picker (Camera / Gallery photos)
            BouncyOutlinedButton(
                onClick = onPickImages,
                modifier = Modifier.weight(1f),
                enabled = !isExtracting,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Snap / Images", style = MaterialTheme.typography.labelMedium)
                }
            }

            // PDF Document Picker
            BouncyOutlinedButton(
                onClick = onPickPdf,
                modifier = Modifier.weight(1f),
                enabled = !isExtracting,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("PDF Slides", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ExtractedDocStatusCard(
    fileNames: List<String>,
    isExtracting: Boolean,
    progressText: String?,
    extractedPageCount: Int,
    extractedWordCount: Int,
    isFastOcr: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (fileNames.any { it.endsWith(".pdf", ignoreCase = true) }) Icons.Default.Description else Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (fileNames.size == 1) fileNames.first() else "${fileNames.size} document files attached",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }

            if (isExtracting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                )
                Text(
                    text = progressText ?: "⚡ Extracting text instantly…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = if (isFastOcr) "⚡ Instant OCR" else "🧠 Vision AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = "$extractedPageCount pages · $extractedWordCount words ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DocStudyConfigSection(
    target: DocStudyTarget,
    onTargetSelect: (DocStudyTarget) -> Unit,
    quizCount: Int,
    onQuizCountChange: (Int) -> Unit,
    difficulty: DocQuizDifficulty,
    onDifficultyChange: (DocQuizDifficulty) -> Unit,
    decks: List<com.edukasyon.studentai.domain.model.JeviDeck>,
    selectedDeckId: String,
    onDeckSelect: (String) -> Unit,
    forceVision: Boolean,
    onToggleForceVision: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Target Mode
        Text(
            "What would you like to create?",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetChip(
                label = "🗂️ Flashcards",
                selected = target == DocStudyTarget.FLASHCARDS,
                onClick = { onTargetSelect(DocStudyTarget.FLASHCARDS) },
                modifier = Modifier.weight(1f),
            )
            TargetChip(
                label = "📝 Quiz",
                selected = target == DocStudyTarget.QUIZ,
                onClick = { onTargetSelect(DocStudyTarget.QUIZ) },
                modifier = Modifier.weight(1f),
            )
            TargetChip(
                label = "⚡ Both",
                selected = target == DocStudyTarget.BOTH,
                onClick = { onTargetSelect(DocStudyTarget.BOTH) },
                modifier = Modifier.weight(1f),
            )
        }

        // Deck Picker (if flashcards or both)
        if (target == DocStudyTarget.FLASHCARDS || target == DocStudyTarget.BOTH) {
            if (decks.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                val selectedDeck = decks.find { it.id == selectedDeckId }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedDeck?.title ?: "Select Deck",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Save Flashcards to Deck") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        decks.forEach { deck ->
                            DropdownMenuItem(
                                text = { Text(deck.title) },
                                onClick = {
                                    onDeckSelect(deck.id)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Quiz Options (if quiz or both)
        if (target == DocStudyTarget.QUIZ || target == DocStudyTarget.BOTH) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Number of Quiz Questions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5 to "5 Quick", 10 to "10 Standard", 15 to "15 Deep").forEach { (count, label) ->
                        FilterChip(
                            selected = quizCount == count,
                            onClick = { onQuizCountChange(count) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Quiz Rigor & Difficulty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocQuizDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { onDifficultyChange(diff) },
                            label = { Text(diff.label) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "chipContainer",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "chipContent",
    )
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Surface(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GeneratedFlashcardsPreview(
    cards: List<Flashcard>,
    isSaved: Boolean,
    onSave: () -> Unit,
) {
    var flippedIndex by remember { mutableIntStateOf(-1) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Generated Flashcards (${cards.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tap card to flip front/back",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!isSaved) {
                BouncyButton(onClick = onSave) {
                    Text("Save to Deck")
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Saved", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        ) {
            items(cards.indices.toList()) { index ->
                val card = cards[index]
                val isFlipped = flippedIndex == index
                FlashcardFlipPreviewItem(
                    card = card,
                    isFlipped = isFlipped,
                    index = index + 1,
                    total = cards.size,
                    onClick = {
                        flippedIndex = if (isFlipped) -1 else index
                    },
                )
            }
        }
    }
}

@Composable
private fun FlashcardFlipPreviewItem(
    card: Flashcard,
    isFlipped: Boolean,
    index: Int,
    total: Int,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400),
        label = "flip",
    )

    Card(
        modifier = Modifier
            .size(width = 240.dp, height = 150.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .graphicsLayer {
                    if (rotation > 90f) rotationY = 180f
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isFlipped) "Answer" else "Question",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$index/$total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = if (isFlipped) card.answer else card.question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFlipped) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center,
            )

            Icon(
                Icons.Default.Flip,
                contentDescription = "Flip",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneratedQuizPreview(
    quiz: Quiz,
    isSaved: Boolean,
    onSave: () -> Unit,
    onOpenQuizArena: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Practice Quiz (${quiz.questions.size} Questions)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    quiz.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BouncyButton(onClick = onOpenQuizArena) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Take Quiz")
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Preview questions
        quiz.questions.take(3).forEachIndexed { i, q ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Q${i + 1}: ${q.question}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        q.options.forEach { opt ->
                            val isCorrect = q.isAnswerCorrect(opt)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCorrect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCorrect) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (quiz.questions.size > 3) {
            Text(
                "+ ${quiz.questions.size - 3} more questions ready in Quiz Arena",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
