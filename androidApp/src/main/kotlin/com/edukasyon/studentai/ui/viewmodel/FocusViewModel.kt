package com.edukasyon.studentai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.ai.AiException
import com.edukasyon.studentai.core.notifications.NotificationHelper
import com.edukasyon.studentai.core.notifications.ReminderType
import com.edukasyon.studentai.core.util.FocusPlanValidator
import com.edukasyon.studentai.core.util.GradeCalculator
import com.edukasyon.studentai.data.preferences.FocusPreferences
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.domain.repository.ExamRepository
import com.edukasyon.studentai.domain.repository.GradeRepository
import com.edukasyon.studentai.domain.repository.SubjectRepository
import com.edukasyon.studentai.domain.usecase.GenerateFocusPlanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class FocusScreenStep {
    SETUP,
    REVIEW,
    RUNNING,
    COMPLETE,
}

data class FocusUiState(
    val step: FocusScreenStep = FocusScreenStep.SETUP,
    val mode: FocusMode = FocusMode.MANUAL,
    val preset: FocusPreset = FocusPreset.POMODORO_25_5,
    val customFocusMinutes: Int = 25,
    val customBreakMinutes: Int = 5,
    val subjectLabel: String = "",
    val subjects: List<String> = emptyList(),
    val aiTotalMinutes: Int = 90,
    val aiPrompt: String = "",
    val isGeneratingPlan: Boolean = false,
    val plan: FocusPlan? = null,
    val planError: String? = null,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalPhaseSeconds: Int = 0,
    val phase: FocusTimerPhase = FocusTimerPhase.FOCUS,
    val currentBlockIndex: Int = 0,
    val completedCycles: Int = 0,
    val totalFocusMinutesLogged: Int = 0,
    val currentActivityLabel: String = "",
    val sessionHistory: List<FocusSessionRecord> = emptyList(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val subjectRepo: SubjectRepository,
    private val examRepo: ExamRepository,
    private val gradeRepo: GradeRepository,
    private val generateFocusPlan: GenerateFocusPlanUseCase,
    private val focusPreferences: FocusPreferences,
    private val notificationHelper: NotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var tickJob: Job? = null
    private var manualOnBreak = false

    init {
        viewModelScope.launch {
            subjectRepo.observeSubjects().collect { subjects ->
                _uiState.update { it.copy(subjects = subjects.map { s -> s.name }) }
            }
        }
        viewModelScope.launch {
            focusPreferences.sessionHistory.collect { history ->
                _uiState.update { it.copy(sessionHistory = history) }
            }
        }
    }

    fun selectMode(mode: FocusMode) {
        _uiState.update { it.copy(mode = mode, planError = null) }
    }

    fun selectPreset(preset: FocusPreset) {
        _uiState.update {
            it.copy(
                preset = preset,
                customFocusMinutes = preset.focusMinutes,
                customBreakMinutes = preset.breakMinutes,
            )
        }
    }

    fun setCustomFocusMinutes(minutes: Int) {
        _uiState.update {
            it.copy(
                customFocusMinutes = minutes.coerceIn(5, 120),
                preset = FocusPreset.CUSTOM,
            )
        }
    }

    fun setCustomBreakMinutes(minutes: Int) {
        _uiState.update {
            it.copy(
                customBreakMinutes = minutes.coerceIn(1, 30),
                preset = FocusPreset.CUSTOM,
            )
        }
    }

    fun setSubjectLabel(label: String) {
        _uiState.update { it.copy(subjectLabel = label) }
    }

    fun setAiTotalMinutes(minutes: Int) {
        _uiState.update { it.copy(aiTotalMinutes = minutes.coerceIn(15, 240)) }
    }

    fun setAiPrompt(prompt: String) {
        _uiState.update { it.copy(aiPrompt = prompt) }
    }

    fun generateAiPlan() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPlan = true, planError = null) }
            try {
                val context = buildPlanContext(state)
                val plan = generateFocusPlan.execute(context)
                _uiState.update {
                    it.copy(
                        isGeneratingPlan = false,
                        plan = plan,
                        step = FocusScreenStep.REVIEW,
                    )
                }
            } catch (e: AiException) {
                _uiState.update {
                    it.copy(isGeneratingPlan = false, planError = e.message)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingPlan = false,
                        planError = e.message ?: "Could not generate focus plan.",
                    )
                }
            }
        }
    }

    fun updatePlanBlock(index: Int, activity: String) {
        val plan = _uiState.value.plan ?: return
        if (index !in plan.blocks.indices) return
        val updated = plan.blocks.toMutableList()
        updated[index] = updated[index].copy(activity = activity)
        _uiState.update { it.copy(plan = plan.copy(blocks = updated)) }
    }

    fun confirmPlanAndStart() {
        val plan = _uiState.value.plan ?: return
        FocusPlanValidator.validate(plan).onSuccess { validated ->
            _uiState.update { it.copy(plan = validated) }
            startAiSession(validated)
        }.onFailure { error ->
            _uiState.update { it.copy(planError = error.message) }
        }
    }

    fun startManualSession() {
        val state = _uiState.value
        manualOnBreak = false
        val focusSeconds = state.customFocusMinutes * 60
        _uiState.update {
            it.copy(
                step = FocusScreenStep.RUNNING,
                isRunning = true,
                isPaused = false,
                phase = FocusTimerPhase.FOCUS,
                remainingSeconds = focusSeconds,
                totalPhaseSeconds = focusSeconds,
                currentActivityLabel = state.subjectLabel.ifBlank { "Focus" },
                completedCycles = 0,
                totalFocusMinutesLogged = 0,
            )
        }
        startTicking()
    }

    private fun startAiSession(plan: FocusPlan) {
        if (plan.blocks.isEmpty()) return
        _uiState.update {
            it.copy(
                step = FocusScreenStep.RUNNING,
                isRunning = true,
                isPaused = false,
                currentBlockIndex = 0,
                completedCycles = 0,
                totalFocusMinutesLogged = 0,
            )
        }
        enterBlock(0, plan)
        startTicking()
    }

    private fun enterBlock(index: Int, plan: FocusPlan) {
        val block = plan.blocks.getOrNull(index) ?: run {
            completeSession()
            return
        }
        val seconds = block.durationMinutes * 60
        _uiState.update {
            it.copy(
                currentBlockIndex = index,
                phase = FocusTimerPhase.BLOCK,
                remainingSeconds = seconds,
                totalPhaseSeconds = seconds,
                currentActivityLabel = block.activity,
            )
        }
    }

    fun togglePause() {
        val state = _uiState.value
        if (!state.isRunning || state.step != FocusScreenStep.RUNNING) return
        if (state.isPaused) {
            _uiState.update { it.copy(isPaused = false) }
            startTicking()
        } else {
            _uiState.update { it.copy(isPaused = true) }
            tickJob?.cancel()
        }
    }

    fun skipPhase() {
        val state = _uiState.value
        if (state.step != FocusScreenStep.RUNNING) return
        tickJob?.cancel()
        onPhaseComplete()
    }

    fun endSession() {
        tickJob?.cancel()
        val state = _uiState.value
        if (state.step == FocusScreenStep.RUNNING && state.totalFocusMinutesLogged > 0) {
            persistSession(state)
        }
        resetToSetup()
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun backFromReview() {
        _uiState.update { it.copy(step = FocusScreenStep.SETUP, plan = null, planError = null) }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (_uiState.value.isRunning && !_uiState.value.isPaused) {
                delay(1000)
                val remaining = _uiState.value.remainingSeconds - 1
                if (remaining <= 0) {
                    onPhaseComplete()
                } else {
                    _uiState.update { it.copy(remainingSeconds = remaining) }
                }
            }
        }
    }

    private fun onPhaseComplete() {
        val state = _uiState.value
        notifyPhaseComplete(state)

        when (state.mode) {
            FocusMode.MANUAL -> advanceManualPhase(state)
            FocusMode.AI_PLAN -> advanceAiPhase(state)
        }
    }

    private fun advanceManualPhase(state: FocusUiState) {
        if (!manualOnBreak) {
            val focusMinutes = state.customFocusMinutes
            manualOnBreak = true
            val breakSeconds = state.customBreakMinutes * 60
            _uiState.update {
                it.copy(
                    phase = FocusTimerPhase.BREAK,
                    remainingSeconds = breakSeconds,
                    totalPhaseSeconds = breakSeconds,
                    currentActivityLabel = "Break",
                    completedCycles = it.completedCycles + 1,
                    totalFocusMinutesLogged = it.totalFocusMinutesLogged + focusMinutes,
                )
            }
        } else {
            manualOnBreak = false
            val focusSeconds = state.customFocusMinutes * 60
            _uiState.update {
                it.copy(
                    phase = FocusTimerPhase.FOCUS,
                    remainingSeconds = focusSeconds,
                    totalPhaseSeconds = focusSeconds,
                    currentActivityLabel = state.subjectLabel.ifBlank { "Focus" },
                )
            }
        }
        if (_uiState.value.isRunning && !_uiState.value.isPaused) {
            startTicking()
        }
    }

    private fun advanceAiPhase(state: FocusUiState) {
        val plan = state.plan ?: run {
            completeSession()
            return
        }
        val currentBlock = plan.blocks.getOrNull(state.currentBlockIndex)
        val loggedMinutes = currentBlock?.durationMinutes ?: 0
        val nextIndex = state.currentBlockIndex + 1
        if (nextIndex >= plan.blocks.size) {
            _uiState.update {
                it.copy(totalFocusMinutesLogged = it.totalFocusMinutesLogged + loggedMinutes)
            }
            completeSession()
            return
        }
        _uiState.update {
            it.copy(totalFocusMinutesLogged = it.totalFocusMinutesLogged + loggedMinutes)
        }
        enterBlock(nextIndex, plan)
        if (_uiState.value.isRunning && !_uiState.value.isPaused) {
            startTicking()
        }
    }

    private fun completeSession() {
        tickJob?.cancel()
        val state = _uiState.value
        persistSession(state)
        _uiState.update {
            it.copy(
                step = FocusScreenStep.COMPLETE,
                isRunning = false,
                isPaused = false,
                phase = FocusTimerPhase.COMPLETE,
                remainingSeconds = 0,
                snackbarMessage = "Focus session complete!",
            )
        }
    }

    private fun persistSession(state: FocusUiState) {
        if (state.totalFocusMinutesLogged <= 0 && state.completedCycles <= 0) return
        viewModelScope.launch {
            focusPreferences.appendSession(
                FocusSessionRecord(
                    id = UUID.randomUUID().toString(),
                    mode = state.mode,
                    subjectLabel = state.subjectLabel.takeIf { it.isNotBlank() },
                    focusMinutes = state.customFocusMinutes,
                    breakMinutes = state.customBreakMinutes,
                    completedCycles = state.completedCycles.coerceAtLeast(1),
                    totalFocusMinutes = state.totalFocusMinutesLogged.coerceAtLeast(state.customFocusMinutes),
                    completedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun resetToSetup() {
        tickJob?.cancel()
        manualOnBreak = false
        _uiState.update {
            FocusUiState(
                mode = it.mode,
                preset = it.preset,
                customFocusMinutes = it.customFocusMinutes,
                customBreakMinutes = it.customBreakMinutes,
                subjects = it.subjects,
                aiTotalMinutes = it.aiTotalMinutes,
                sessionHistory = it.sessionHistory,
            )
        }
    }

    fun restartAfterComplete() {
        resetToSetup()
    }

    private suspend fun buildPlanContext(state: FocusUiState): FocusPlanContext {
        val subjectList = subjectRepo.observeSubjects().first()
        val subjectNameById = subjectList.associate { it.id to it.name }
        val subjects = subjectList.map { it.name }
        val exams = examRepo.observeUpcoming(5).first().map { exam ->
            val subjectName = exam.subjectId?.let { subjectNameById[it] } ?: exam.title
            "${exam.title} ($subjectName)"
        }
        val grades = gradeRepo.observeGrades().first()
        val weakAreas = grades
            .groupBy { it.subjectId }
            .mapNotNull { (subjectId, entries) ->
                val avg = GradeCalculator.calculateWeightedGrade(entries)
                if (avg < 75.0) subjectNameById[subjectId] else null
            }
            .distinct()
            .take(3)
        return FocusPlanContext(
            totalMinutes = state.aiTotalMinutes,
            subjects = subjects,
            upcomingExams = exams,
            weakAreas = weakAreas,
            userPrompt = state.aiPrompt.takeIf { it.isNotBlank() },
        )
    }

    private fun notifyPhaseComplete(state: FocusUiState) {
        val title = when (state.phase) {
            FocusTimerPhase.FOCUS, FocusTimerPhase.BLOCK -> "Break time!"
            FocusTimerPhase.BREAK -> "Back to focus!"
            FocusTimerPhase.COMPLETE -> "Session complete"
        }
        val message = when (state.phase) {
            FocusTimerPhase.FOCUS, FocusTimerPhase.BLOCK ->
                "${state.currentActivityLabel} block finished."
            FocusTimerPhase.BREAK -> "Start your next focus block."
            FocusTimerPhase.COMPLETE -> "Great work staying focused."
        }
        notificationHelper.showReminder(
            notificationId = "focus_phase_${System.currentTimeMillis()}".hashCode(),
            type = ReminderType.FOCUS,
            title = title,
            message = message,
        )
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }
}
