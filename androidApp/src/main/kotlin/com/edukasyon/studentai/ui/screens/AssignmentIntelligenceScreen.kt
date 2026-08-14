package com.edukasyon.studentai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.AssignmentBreakdown
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.core.mlkit.rememberDocumentScanLauncher
import com.edukasyon.studentai.ui.viewmodel.AssignmentInputMode
import com.edukasyon.studentai.ui.viewmodel.AssignmentIntelligenceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentIntelligenceScreen(
    onBack: () -> Unit,
    onAddedToPlanner: () -> Unit = {},
    viewModel: AssignmentIntelligenceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onPdfSelected(it) }
    }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }
    val launchDocumentScan = rememberDocumentScanLauncher(
        onResult = { bytes -> viewModel.onScannedImageBytes(bytes) },
        onError = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        },
    )

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
            onAddedToPlanner()
        }
    }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assignment Intelligence") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        AdaptiveContentContainer {
            contentModifier ->
            Box(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    state.isAnalyzing -> {
                        GeneratingLoader(
                            label = "Analyzing assignment…",
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    state.breakdown != null && state.showBreakdownReview -> {
                        BreakdownReviewContent(
                            breakdown = state.breakdown!!,
                            horizontalPadding = horizontalPadding,
                            isSaving = state.isSaving,
                            onBackToInput = viewModel::backToInput,
                            onAddToPlanner = viewModel::addToPlanner,
                        )
                    }
                    else -> {
                        InputContent(
                            state = state,
                            horizontalPadding = horizontalPadding,
                            onModeSelected = viewModel::setInputMode,
                            onTextChanged = viewModel::updateInstructionsText,
                            onAnalyzeText = viewModel::analyzeFromText,
                            onPickPdf = { pdfLauncher.launch(arrayOf("application/pdf")) },
                            onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
                            onDocumentScan = launchDocumentScan,
                            onResumeBreakdown = viewModel::resumeBreakdownReview,
                            onDiscardBreakdown = viewModel::discardBreakdown,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputContent(
    state: com.edukasyon.studentai.ui.viewmodel.AssignmentIntelligenceUiState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onModeSelected: (AssignmentInputMode) -> Unit,
    onTextChanged: (String) -> Unit,
    onAnalyzeText: () -> Unit,
    onPickPdf: () -> Unit,
    onPickImage: () -> Unit,
    onDocumentScan: () -> Unit,
    onResumeBreakdown: () -> Unit,
    onDiscardBreakdown: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard breakdown?") },
            text = {
                Text("This removes the analyzed subtasks from this session. Add to Planner first if you want to keep them.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardBreakdown()
                    },
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontalPadding)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.breakdown?.let { breakdown ->
            StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Unsaved breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "\"${breakdown.title}\" with ${breakdown.subtasks.size} subtasks is still available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BouncyButton(
                            onClick = onResumeBreakdown,
                            modifier = Modifier.weight(1f),
                            shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button,
                        ) {
                            Text("View breakdown")
                        }
                        OutlinedButton(onClick = { showDiscardDialog = true }) {
                            Text("Discard")
                        }
                    }
                }
            }
        }

        StudentAiCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Upload or paste your assignment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "Jevi extracts deadlines, requirements, rubric, and subtasks — then adds them to your Planner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.inputMode == AssignmentInputMode.TEXT,
                onClick = { onModeSelected(AssignmentInputMode.TEXT) },
                label = { Text("Paste") },
                leadingIcon = { Icon(Icons.Default.TextFields, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.inputMode == AssignmentInputMode.PDF,
                onClick = { onModeSelected(AssignmentInputMode.PDF) },
                label = { Text("PDF") },
                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = state.inputMode == AssignmentInputMode.IMAGE,
                onClick = { onModeSelected(AssignmentInputMode.IMAGE) },
                label = { Text("Image") },
                leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(18.dp)) },
            )
        }

        when (state.inputMode) {
            AssignmentInputMode.TEXT -> {
                OutlinedTextField(
                    value = state.instructionsText,
                    onValueChange = onTextChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    label = { Text("Assignment instructions") },
                    placeholder = { Text("Paste syllabus text, LMS instructions, or rubric…") },
                )
                BouncyButton(
                    onClick = onAnalyzeText,
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button,
                ) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyze")
                }
            }
            AssignmentInputMode.PDF -> {
                StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Pick a PDF assignment sheet or syllabus page.", style = MaterialTheme.typography.bodyMedium)
                        state.selectedFileName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        BouncyButton(onClick = onPickPdf, shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button) {
                            Text("Choose PDF")
                        }
                    }
                }
            }
            AssignmentInputMode.IMAGE -> {
                StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Pick a photo or screenshot of the assignment.", style = MaterialTheme.typography.bodyMedium)
                        state.selectedFileName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        BouncyButton(onClick = onPickImage, shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button) {
                            Text("Choose Image")
                        }
                        OutlinedButton(onClick = onDocumentScan) {
                            Icon(Icons.Default.DocumentScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan document")
                        }
                    }
                }
                OutlinedTextField(
                    value = state.instructionsText,
                    onValueChange = onTextChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional notes") },
                    placeholder = { Text("Add context for the AI…") },
                    minLines = 2,
                )
            }
        }
    }
}

@Composable
private fun BreakdownReviewContent(
    breakdown: AssignmentBreakdown,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    isSaving: Boolean,
    onBackToInput: () -> Unit,
    onAddToPlanner: () -> Unit,
) {
    val dueMillis = breakdown.deadline?.let { DateUtils.parseIsoDate(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(breakdown.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (dueMillis != null) {
                        Text(
                            "Deadline: ${DateUtils.formatFullDate(dueMillis)} (${DateUtils.formatCountdown(dueMillis)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            "Deadline: not detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Estimated effort: ${breakdown.estimatedEffortHours} hours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (breakdown.requirements.isNotEmpty()) {
            item { SectionHeader("Requirements") }
            item { BulletCard(breakdown.requirements) }
        }
        if (breakdown.deliverables.isNotEmpty()) {
            item { SectionHeader("Deliverables") }
            item { BulletCard(breakdown.deliverables) }
        }
        if (breakdown.rubric.isNotEmpty()) {
            item { SectionHeader("Rubric") }
            item { BulletCard(breakdown.rubric) }
        }

        item { SectionHeader("Subtasks (${breakdown.subtasks.size})") }
        itemsIndexed(breakdown.subtasks) { index, sub ->
            StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "${index + 1}",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sub.title, style = MaterialTheme.typography.bodyLarge)
                        val targetDate = dueMillis?.let { DateUtils.formatFullDate(DateUtils.subtractDays(it, sub.dueOffsetDays)) }
                        Text(
                            buildString {
                                append("~${sub.estimatedMinutes} min")
                                targetDate?.let { append(" · target $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (breakdown.notes.isNotBlank()) {
            item { SectionHeader("Notes") }
            item {
                StudentAiCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        breakdown.notes,
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onBackToInput, modifier = Modifier.weight(1f), enabled = !isSaving) {
                    Text("Back to input")
                }
                BouncyButton(
                    onClick = onAddToPlanner,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                    shape = com.edukasyon.studentai.ui.theme.StudentAiShapes.button,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add to Planner")
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletCard(items: List<String>) {
    StudentAiCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Text("• $item", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
