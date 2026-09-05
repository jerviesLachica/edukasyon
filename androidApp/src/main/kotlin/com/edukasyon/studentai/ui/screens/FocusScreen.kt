package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.FocusPlanValidator
import com.edukasyon.studentai.domain.model.FocusBlockType
import com.edukasyon.studentai.domain.model.FocusMode
import com.edukasyon.studentai.domain.model.FocusPreset
import com.edukasyon.studentai.domain.model.FocusTimerPhase
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveHorizontalPadding
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.viewmodel.FocusScreenStep
import com.edukasyon.studentai.ui.viewmodel.FocusViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onBack: () -> Unit,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val horizontalPadding = rememberAdaptiveHorizontalPadding()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { StudentAiSnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { padding ->
        AdaptiveContentContainer { contentModifier ->
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                FocusTopBar(
                    step = state.step,
                    onBack = {
                        when (state.step) {
                            FocusScreenStep.REVIEW -> viewModel.backFromReview()
                            FocusScreenStep.RUNNING -> viewModel.endSession()
                            else -> onBack()
                        }
                    },
                )

                when (state.step) {
                    FocusScreenStep.SETUP -> FocusSetupContent(
                        state = state,
                        isDark = isDark,
                        horizontalPadding = horizontalPadding,
                        onSelectMode = viewModel::selectMode,
                        onSelectPreset = viewModel::selectPreset,
                        onCustomFocus = viewModel::setCustomFocusMinutes,
                        onCustomBreak = viewModel::setCustomBreakMinutes,
                        onSubjectLabel = viewModel::setSubjectLabel,
                        onAiMinutes = viewModel::setAiTotalMinutes,
                        onAiPrompt = viewModel::setAiPrompt,
                        onStartManual = viewModel::startManualSession,
                        onGeneratePlan = viewModel::generateAiPlan,
                    )
                    FocusScreenStep.REVIEW -> FocusReviewContent(
                        state = state,
                        horizontalPadding = horizontalPadding,
                        onUpdateBlock = viewModel::updatePlanBlock,
                        onConfirm = viewModel::confirmPlanAndStart,
                        onBack = viewModel::backFromReview,
                    )
                    FocusScreenStep.RUNNING -> FocusTimerContent(
                        state = state,
                        horizontalPadding = horizontalPadding,
                        onTogglePause = viewModel::togglePause,
                        onSkip = viewModel::skipPhase,
                        onEnd = viewModel::endSession,
                    )
                    FocusScreenStep.COMPLETE -> FocusCompleteContent(
                        state = state,
                        horizontalPadding = horizontalPadding,
                        onDone = onBack,
                        onAgain = viewModel::restartAfterComplete,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusTopBar(step: FocusScreenStep, onBack: () -> Unit) {
    GradientHeader(
        title = "Focus",
        subtitle = when (step) {
            FocusScreenStep.SETUP -> "Pomodoro & AI planner"
            FocusScreenStep.REVIEW -> "Review your plan"
            FocusScreenStep.RUNNING -> "Stay focused"
            FocusScreenStep.COMPLETE -> "Well done!"
        },
        inlineSubtitle = true,
        trailing = {
            BouncyIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    )
}

@Composable
private fun FocusSetupContent(
    state: com.edukasyon.studentai.ui.viewmodel.FocusUiState,
    isDark: Boolean,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onSelectMode: (FocusMode) -> Unit,
    onSelectPreset: (FocusPreset) -> Unit,
    onCustomFocus: (Int) -> Unit,
    onCustomBreak: (Int) -> Unit,
    onSubjectLabel: (String) -> Unit,
    onAiMinutes: (Int) -> Unit,
    onAiPrompt: (String) -> Unit,
    onStartManual: () -> Unit,
    onGeneratePlan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusModeChip(
                label = "Manual",
                selected = state.mode == FocusMode.MANUAL,
                onClick = { onSelectMode(FocusMode.MANUAL) },
                modifier = Modifier.weight(1f),
            )
            FocusModeChip(
                label = "AI Planner",
                selected = state.mode == FocusMode.AI_PLAN,
                onClick = { onSelectMode(FocusMode.AI_PLAN) },
                modifier = Modifier.weight(1f),
            )
        }

        when (state.mode) {
            FocusMode.MANUAL -> {
                Text("Preset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusPreset.entries.filter { it != FocusPreset.CUSTOM }.forEach { preset ->
                        FilterChip(
                            selected = state.preset == preset,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.label) },
                        )
                    }
                    FilterChip(
                        selected = state.preset == FocusPreset.CUSTOM,
                        onClick = { onSelectPreset(FocusPreset.CUSTOM) },
                        label = { Text("Custom") },
                    )
                }

                if (state.preset == FocusPreset.CUSTOM) {
                    ModernCard(containerColor = focusPastelCard(isDark)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.customFocusMinutes.toString(),
                                onValueChange = { onCustomFocus(it.toIntOrNull() ?: 25) },
                                label = { Text("Focus (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.customBreakMinutes.toString(),
                                onValueChange = { onCustomBreak(it.toIntOrNull() ?: 5) },
                                label = { Text("Break (min)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }
                } else {
                    ModernCard(containerColor = focusPastelCard(isDark)) {
                        Text(
                            "${state.customFocusMinutes} min focus · ${state.customBreakMinutes} min break",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                SubjectLabelField(
                    label = state.subjectLabel,
                    subjects = state.subjects,
                    onLabelChange = onSubjectLabel,
                    isDark = isDark,
                )

                Button(
                    onClick = onStartManual,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start focus session")
                }
            }

            FocusMode.AI_PLAN -> {
                ModernCard(containerColor = focusPastelCard(isDark)) {
                    Text(
                        "Jevi builds timed study blocks using your subjects, exams, and weak areas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                OutlinedTextField(
                    value = state.aiTotalMinutes.toString(),
                    onValueChange = { onAiMinutes(it.toIntOrNull() ?: 90) },
                    label = { Text("Session length (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                )

                OutlinedTextField(
                    value = state.aiPrompt,
                    onValueChange = onAiPrompt,
                    label = { Text("Optional: what do you want to study?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("e.g. Review for midterms, focus on Database") },
                )

                state.planError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = onGeneratePlan,
                    enabled = !state.isGeneratingPlan,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isGeneratingPlan) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generating…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Generate plan with Jevi")
                    }
                }

                if (state.isGeneratingPlan) {
                    GeneratingLoader(
                        label = "Planning",
                        style = GeneratingLoaderStyle.Compact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (state.sessionHistory.isNotEmpty()) {
            Text("Recent sessions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            state.sessionHistory.take(5).forEach { record ->
                ModernCard(containerColor = focusPastelCard(isDark)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                record.subjectLabel ?: if (record.mode == FocusMode.AI_PLAN) "AI session" else "Focus",
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${record.totalFocusMinutes} min · ${record.completedCycles} cycles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            DateUtils.formatDateTime(record.completedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectLabelField(
    label: String,
    subjects: List<String>,
    onLabelChange: (String) -> Unit,
    isDark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subject (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text("Subject or topic") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (subjects.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                subjects.take(4).forEach { subject ->
                    SuggestionChip(
                        onClick = { onLabelChange(subject) },
                        label = { Text(subject, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusReviewContent(
    state: com.edukasyon.studentai.ui.viewmodel.FocusUiState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onUpdateBlock: (Int, String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val plan = state.plan ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModernCard {
            Text(
                "${plan.totalMinutes} minute session · ${plan.breakMinutesBetween} min breaks",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(plan.blocks) { index, block ->
                ModernCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                formatBlockRange(block.startMinute, block.endMinute),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedTextField(
                                value = block.activity,
                                onValueChange = { onUpdateBlock(index, it) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Activity") },
                            )
                            Text(
                                FocusPlanValidator.blockTypeLabel(block.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        state.planError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start")
            }
        }
    }
}

@Composable
private fun FocusTimerContent(
    state: com.edukasyon.studentai.ui.viewmodel.FocusUiState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onEnd: () -> Unit,
) {
    val progress = if (state.totalPhaseSeconds > 0) {
        1f - (state.remainingSeconds.toFloat() / state.totalPhaseSeconds)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val phaseLabel = when (state.phase) {
            FocusTimerPhase.FOCUS -> "Focus"
            FocusTimerPhase.BREAK -> "Break"
            FocusTimerPhase.BLOCK -> FocusPlanValidator.blockTypeLabel(
                state.plan?.blocks?.getOrNull(state.currentBlockIndex)?.type ?: FocusBlockType.STUDY
            )
            FocusTimerPhase.COMPLETE -> "Done"
        }
        Text(
            phaseLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            state.currentActivityLabel,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )

        // Ring shrinks on short screens (landscape) instead of pushing the
        // controls off-screen.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .heightIn(max = 240.dp),
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                formatCountdown(state.remainingSeconds),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                ),
            )
        }

        if (state.mode == FocusMode.MANUAL) {
            Text(
                "Cycle ${state.completedCycles + 1} · ${state.totalFocusMinutesLogged} min logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val plan = state.plan
            if (plan != null) {
                Text(
                    "Block ${state.currentBlockIndex + 1} of ${plan.blocks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f)) {
                Text("End")
            }
            FilledTonalButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                Text("Skip")
            }
            Button(onClick = onTogglePause, modifier = Modifier.weight(1f)) {
                Icon(
                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.isPaused) "Resume" else "Pause")
            }
        }
    }
}

@Composable
private fun FocusCompleteContent(
    state: com.edukasyon.studentai.ui.viewmodel.FocusUiState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onDone: () -> Unit,
    onAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Session complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "You logged ${state.totalFocusMinutesLogged} minutes of focused study.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Start another session")
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun FocusModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        modifier = modifier,
    )
}

private fun focusPastelCard(isDark: Boolean): Color =
    if (isDark) Color(0xFF2A3140) else Color(0xFFEEF2FF)

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatBlockRange(startMinute: Int, endMinute: Int): String {
    fun fmt(m: Int) = "%d:%02d".format(m / 60, m % 60)
    return "${fmt(startMinute)}–${fmt(endMinute)}"
}
