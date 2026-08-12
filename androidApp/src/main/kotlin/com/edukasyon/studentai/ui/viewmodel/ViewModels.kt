package com.edukasyon.studentai.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.domain.repository.*
import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.firebase.FirestoreSyncService
import com.edukasyon.studentai.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val userName: String = "Student",
    val nextClass: ScheduleItem? = null,
    val todaySchedule: List<ScheduleItem> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val upcomingExams: List<Exam> = emptyList(),
    val aiSuggestion: String? = null,
    val isOnline: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val examRepo: ExamRepository,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadDashboard() }

    private fun loadDashboard() {
        viewModelScope.launch {
            try {
                combine(
                    userRepo.observeUser().onStart { emit(null) },
                    scheduleRepo.observeByDay(DateUtils.getTodayDayOfWeek()).onStart { emit(emptyList()) },
                    taskRepo.observeUpcoming(5).onStart { emit(emptyList()) },
                    examRepo.observeUpcoming(3).onStart { emit(emptyList()) },
                    connectivity.isOnline.onStart { emit(true) }
                ) { user, schedule, tasks, exams, online ->
                    val sorted = schedule.sortedBy { it.startTime }
                    val next = sorted.firstOrNull { isUpcoming(it.startTime) }
                    val suggestion = exams.firstOrNull()?.let { exam ->
                        "You have a ${exam.title} ${DateUtils.formatCountdown(exam.examDate)}. Would you like me to create a study plan?"
                    }
                    HomeUiState(
                        isLoading = false,
                        greeting = DateUtils.greeting(),
                        userName = user?.displayName ?: "Student",
                        nextClass = next,
                        todaySchedule = sorted,
                        upcomingTasks = tasks,
                        upcomingExams = exams,
                        aiSuggestion = suggestion,
                        isOnline = online
                    )
                }.collect { _uiState.value = it }
            } catch (_: Exception) {
                _uiState.value = HomeUiState(isLoading = false, greeting = DateUtils.greeting())
            }
        }
    }

    private fun isUpcoming(startTime: String): Boolean {
        val parts = startTime.split(":")
        val now = java.util.Calendar.getInstance()
        val classMinutes = parts[0].toInt() * 60 + parts.getOrElse(1) { "0" }.toInt()
        val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return classMinutes >= nowMinutes
    }
}

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val allItems: List<ScheduleItem> = emptyList(),
    val selectedDay: DayOfWeek = DateUtils.getTodayDayOfWeek(),
    val viewMode: String = "weekly",
    val dayTemplates: ScheduleWeekTemplates = ScheduleWeekTemplates.defaults(),
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val addScheduleItem: AddScheduleItemUseCase,
    private val updateScheduleItem: UpdateScheduleItemUseCase,
    private val deleteScheduleItem: DeleteScheduleItemUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            scheduleRepo.observeSchedule().collect { items ->
                _uiState.update { it.copy(isLoading = false, allItems = items) }
            }
        }
        viewModelScope.launch {
            preferences.scheduleDayTemplates.collect { templates ->
                _uiState.update { it.copy(dayTemplates = templates) }
            }
        }
    }

    fun selectDay(day: DayOfWeek) { _uiState.update { it.copy(selectedDay = day) } }
    fun setViewMode(mode: String) { _uiState.update { it.copy(viewMode = mode) } }
    fun addClass(item: ScheduleItem) { viewModelScope.launch { addScheduleItem.execute(item) } }
    fun updateClass(item: ScheduleItem) { viewModelScope.launch { updateScheduleItem.execute(item) } }
    fun deleteClass(id: String) { viewModelScope.launch { deleteScheduleItem.execute(id) } }

    fun itemsForSelectedDay(): List<ScheduleItem> =
        _uiState.value.allItems.filter { it.dayOfWeek == _uiState.value.selectedDay }.sortedBy { it.startTime }

    fun itemsGroupedByDay(): Map<DayOfWeek, List<ScheduleItem>> =
        DayOfWeek.entries.associateWith { day ->
            _uiState.value.allItems.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
        }

    fun setDayTemplate(day: DayOfWeek, template: ScheduleDayTemplate) {
        viewModelScope.launch { preferences.setScheduleDayTemplate(day, template) }
    }

    fun resetDayTemplates() {
        viewModelScope.launch { preferences.resetScheduleDayTemplates() }
    }
}

data class PlannerUiState(
    val selectedTab: Int = 0,
    val tasks: List<Task> = emptyList(),
    val assignments: List<Assignment> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val assignmentRepo: AssignmentRepository,
    private val examRepo: ExamRepository,
    private val createTask: CreateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val saveAssignment: SaveAssignmentUseCase,
    private val saveExam: SaveExamUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(taskRepo.observeTasks(), assignmentRepo.observeAssignments(), examRepo.observeExams()) { t, a, e ->
                PlannerUiState(isLoading = false, tasks = t, assignments = a, exams = e, selectedTab = _uiState.value.selectedTab)
            }.collect { _uiState.value = it }
        }
    }

    fun selectTab(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }
    fun addTask(task: Task) { viewModelScope.launch { createTask.execute(task) } }
    fun completeTask(id: String) { viewModelScope.launch { completeTaskUseCase.execute(id) } }
    fun deleteTask(id: String) { viewModelScope.launch { deleteTaskUseCase.execute(id) } }
    fun addAssignment(a: Assignment) { viewModelScope.launch { saveAssignment.execute(a) } }
    fun addExam(e: Exam) { viewModelScope.launch { saveExam.execute(e) } }

    fun addSubtask(taskId: String, title: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == taskId } ?: return@launch
            val subtasks = task.subtasks + Subtask(
                id = java.util.UUID.randomUUID().toString(),
                taskId = taskId,
                title = title,
                isCompleted = false,
                sortOrder = task.subtasks.size
            )
            createTask.execute(task.copy(subtasks = subtasks, updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == taskId } ?: return@launch
            val subtasks = task.subtasks.map {
                if (it.id == subtaskId) it.copy(isCompleted = !it.isCompleted) else it
            }
            createTask.execute(task.copy(subtasks = subtasks, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == taskId } ?: return@launch
            createTask.execute(task.copy(
                subtasks = task.subtasks.filter { it.id != subtaskId },
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}

data class NotesUiState(val notes: List<Note> = emptyList(), val searchQuery: String = "", val isLoading: Boolean = true)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepo.observeNotes().collect { notes -> _uiState.update { it.copy(notes = notes, isLoading = false) } }
        }
    }

    fun saveNote(note: Note) { viewModelScope.launch { saveNoteUseCase.execute(note) } }
    fun deleteNote(id: String) { viewModelScope.launch { deleteNoteUseCase.execute(id) } }
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val flow = if (query.isBlank()) noteRepo.observeNotes() else noteRepo.search(query)
            flow.collect { _uiState.update { s -> s.copy(notes = it) } }
        }
    }
}

data class AiUiState(
    val messages: List<Pair<String, String>> = emptyList(),
    val isLoading: Boolean = false,
    val loadingTool: AiTool? = null,
    val error: String? = null,
    val lastSummary: String? = null,
    val generatedFlashcards: List<Flashcard> = emptyList(),
    val flashcardsSaved: Boolean = false,
    val generatedQuiz: Quiz? = null,
    val quizSession: QuizSessionState? = null,
    val quizSaved: Boolean = false,
    val studyPlan: StudyPlan? = null,
    val scannedClasses: List<com.edukasyon.studentai.core.ai.ExtractedClass> = emptyList(),
    val statusMessage: String? = null
)

enum class AiTool { TUTOR, SUMMARIZE, FLASHCARDS, QUIZ, SCANNER }

data class QuizSessionState(
    val quiz: Quiz,
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val revealed: Boolean = false,
    val correctCount: Int = 0,
    val finished: Boolean = false
) {
    val currentQuestion: QuizQuestion? get() = quiz.questions.getOrNull(currentIndex)
    val totalQuestions: Int get() = quiz.questions.size
    val scorePercent: Int get() = if (totalQuestions == 0) 0 else (correctCount * 100) / totalQuestions
}

private data class PendingAiAction(val tool: AiTool, val text: String)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiChat: AiChatUseCase,
    private val aiSummarize: AiSummarizeUseCase,
    private val aiGenerateFlashcards: AiGenerateFlashcardsUseCase,
    private val aiGenerateQuiz: AiGenerateQuizUseCase,
    private val aiAnalyzeSchedule: AiAnalyzeScheduleUseCase,
    private val saveFlashcards: SaveFlashcardsUseCase,
    private val saveQuiz: SaveQuizUseCase,
    private val addScheduleItem: AddScheduleItemUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()
    private var pendingAction: PendingAiAction? = null
    private var lastChatMessage: String? = null

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun retryLastAction() {
        val action = pendingAction ?: return
        when (action.tool) {
            AiTool.SUMMARIZE -> summarize(action.text)
            AiTool.FLASHCARDS -> generateFlashcards(action.text)
            AiTool.QUIZ -> generateQuiz(action.text)
            AiTool.TUTOR -> lastChatMessage?.let { sendMessage(it) }
            AiTool.SCANNER -> Unit
        }
    }

    fun sendMessage(message: String, subject: String? = null) {
        if (message.isBlank()) return
        lastChatMessage = message
        pendingAction = PendingAiAction(AiTool.TUTOR, message)
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingTool = AiTool.TUTOR, error = null) }
            try {
                val response = aiChat.execute(com.edukasyon.studentai.core.ai.AiChatRequest(message, subject))
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        loadingTool = null,
                        messages = s.messages + ("You" to message) + ("AI" to response.reply)
                    )
                }
            } catch (e: com.edukasyon.studentai.core.ai.AiException) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null, error = e.message) }
            }
        }
    }

    fun summarize(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Paste some note content to summarize.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.SUMMARIZE, text)
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, loadingTool = AiTool.SUMMARIZE, error = null, lastSummary = null)
            }
            try {
                val result = aiSummarize.execute(text)
                _uiState.update {
                    it.copy(isLoading = false, loadingTool = null, lastSummary = result, statusMessage = "Summary ready")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null, error = e.message ?: "Summarize failed") }
            }
        }
    }

    fun generateFlashcards(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Paste note content or a topic to generate flashcards.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.FLASHCARDS, text)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingTool = AiTool.FLASHCARDS,
                    error = null,
                    generatedFlashcards = emptyList(),
                    flashcardsSaved = false
                )
            }
            try {
                val cards = aiGenerateFlashcards.execute(text)
                if (cards.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, loadingTool = null, error = "No flashcards were generated. Try again with more content.")
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        generatedFlashcards = cards,
                        statusMessage = "Generated ${cards.size} flashcards"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null, error = e.message ?: "Flashcard generation failed") }
            }
        }
    }

    fun saveGeneratedFlashcards() {
        val cards = _uiState.value.generatedFlashcards
        if (cards.isEmpty()) return
        viewModelScope.launch {
            try {
                saveFlashcards.execute(cards)
                _uiState.update { it.copy(flashcardsSaved = true, statusMessage = "Flashcards saved to library") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save flashcards") }
            }
        }
    }

    fun generateQuiz(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Paste note content or a topic to generate a quiz.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.QUIZ, text)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingTool = AiTool.QUIZ,
                    error = null,
                    generatedQuiz = null,
                    quizSession = null,
                    quizSaved = false
                )
            }
            try {
                val quiz = aiGenerateQuiz.execute(text)
                if (quiz.questions.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, loadingTool = null, error = "No quiz questions were generated. Try again with more content.")
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        generatedQuiz = quiz,
                        quizSession = QuizSessionState(quiz = quiz),
                        statusMessage = "Quiz ready — ${quiz.questions.size} questions"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null, error = e.message ?: "Quiz generation failed") }
            }
        }
    }

    fun selectQuizAnswer(answer: String) {
        val session = _uiState.value.quizSession ?: return
        if (session.revealed || session.finished) return
        _uiState.update { it.copy(quizSession = session.copy(selectedAnswer = answer)) }
    }

    fun revealQuizAnswer() {
        val session = _uiState.value.quizSession ?: return
        val question = session.currentQuestion ?: return
        val selected = session.selectedAnswer ?: return
        if (session.revealed) return
        val isCorrect = selected.equals(question.correctAnswer, ignoreCase = true)
        _uiState.update {
            it.copy(
                quizSession = session.copy(
                    revealed = true,
                    correctCount = session.correctCount + if (isCorrect) 1 else 0
                )
            )
        }
    }

    fun nextQuizQuestion() {
        val session = _uiState.value.quizSession ?: return
        if (!session.revealed) return
        val nextIndex = session.currentIndex + 1
        if (nextIndex >= session.totalQuestions) {
            _uiState.update {
                it.copy(
                    quizSession = session.copy(finished = true),
                    statusMessage = "Quiz complete — ${session.correctCount}/${session.totalQuestions} correct"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    quizSession = session.copy(
                        currentIndex = nextIndex,
                        selectedAnswer = null,
                        revealed = false
                    )
                )
            }
        }
    }

    fun restartQuiz() {
        val quiz = _uiState.value.generatedQuiz ?: return
        _uiState.update {
            it.copy(
                quizSession = QuizSessionState(quiz = quiz),
                quizSaved = false,
                statusMessage = null
            )
        }
    }

    fun saveQuizResult() {
        val quiz = _uiState.value.generatedQuiz ?: return
        viewModelScope.launch {
            try {
                saveQuiz.execute(quiz)
                _uiState.update { it.copy(quizSaved = true, statusMessage = "Quiz saved to library") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save quiz") }
            }
        }
    }

    fun analyzeScheduleImage(imageData: ByteArray) {
        pendingAction = PendingAiAction(AiTool.SCANNER, "")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingTool = AiTool.SCANNER, error = null) }
            try {
                val result = aiAnalyzeSchedule.execute(imageData)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        scannedClasses = result.classes,
                        statusMessage = "Found ${result.classes.size} classes"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null, error = e.message) }
            }
        }
    }

    fun confirmScannedClasses() {
        viewModelScope.launch {
            _uiState.value.scannedClasses.forEach { cls ->
                addScheduleItem.execute(ScheduleItem(
                    id = java.util.UUID.randomUUID().toString(),
                    subjectId = null, subjectName = cls.subject, teacher = cls.teacher,
                    room = cls.room, building = null,
                    dayOfWeek = DayOfWeek.fromString(cls.day) ?: DayOfWeek.MONDAY,
                    startTime = cls.startTime, endTime = cls.endTime,
                    colorHex = "#1A237E", notes = null, semester = "", schoolYear = ""
                ))
            }
            _uiState.update { it.copy(scannedClasses = emptyList()) }
        }
    }
}

data class ProfileUiState(
    val user: UserProfile? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColorHex: String = com.edukasyon.studentai.ui.theme.ThemePresets.DEFAULT_PRIMARY,
    val secondaryColorHex: String? = null,
    val notificationsEnabled: Boolean = true,
    val classReminders: Boolean = true,
    val taskReminders: Boolean = true,
    val examReminders: Boolean = true,
    val isOnline: Boolean = true,
    val backupMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor,
    private val dataBackupManager: com.edukasyon.studentai.core.backup.DataBackupManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(
                    userRepo.observeUser(),
                    preferences.themeMode,
                    preferences.notificationsEnabled
                ) { u, t, n -> Triple(u, t, n) },
                combine(
                    preferences.primaryColorHex,
                    preferences.secondaryColorHex,
                    preferences.classReminders,
                    preferences.taskReminders,
                    preferences.examReminders,
                    connectivity.isOnline
                ) { primary, secondary, c, task, exam, o ->
                    listOf(primary, secondary, c, task, exam, o)
                }
            ) { first, second ->
                ProfileUiState(
                    user = first.first,
                    themeMode = first.second,
                    notificationsEnabled = first.third,
                    primaryColorHex = second[0] as String,
                    secondaryColorHex = second[1] as String?,
                    classReminders = second[2] as Boolean,
                    taskReminders = second[3] as Boolean,
                    examReminders = second[4] as Boolean,
                    isOnline = second[5] as Boolean
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { preferences.setThemeMode(mode) } }
    fun setPrimaryColor(hex: String) { viewModelScope.launch { preferences.setPrimaryColorHex(hex) } }
    fun setSecondaryColor(hex: String?) { viewModelScope.launch { preferences.setSecondaryColorHex(hex) } }
    fun resetThemeColors() { viewModelScope.launch { preferences.resetThemeColors() } }
    fun setNotifications(enabled: Boolean) { viewModelScope.launch { preferences.setNotificationsEnabled(enabled) } }
    fun setClassReminders(enabled: Boolean) { viewModelScope.launch { preferences.setClassReminders(enabled) } }
    fun setTaskReminders(enabled: Boolean) { viewModelScope.launch { preferences.setTaskReminders(enabled) } }
    fun setExamReminders(enabled: Boolean) { viewModelScope.launch { preferences.setExamReminders(enabled) } }

    fun exportJson(uri: android.net.Uri) {
        viewModelScope.launch {
            dataBackupManager.exportJson(uri)
                .onSuccess { _uiState.update { it.copy(backupMessage = "Backup exported successfully") } }
                .onFailure { e -> _uiState.update { it.copy(backupMessage = e.message) } }
        }
    }

    fun exportScheduleCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            dataBackupManager.exportScheduleCsv(uri)
                .onSuccess { _uiState.update { it.copy(backupMessage = "Schedule CSV exported") } }
                .onFailure { e -> _uiState.update { it.copy(backupMessage = e.message) } }
        }
    }

    fun exportGradesCsv(uri: android.net.Uri) {
        viewModelScope.launch {
            dataBackupManager.exportGradesCsv(uri)
                .onSuccess { _uiState.update { it.copy(backupMessage = "Grades CSV exported") } }
                .onFailure { e -> _uiState.update { it.copy(backupMessage = e.message) } }
        }
    }

    fun importJson(uri: android.net.Uri, replace: Boolean) {
        viewModelScope.launch {
            dataBackupManager.importJson(uri, replace)
                .onSuccess { count -> _uiState.update { it.copy(backupMessage = "Imported $count items") } }
                .onFailure { e -> _uiState.update { it.copy(backupMessage = e.message) } }
        }
    }

    fun clearBackupMessage() { _uiState.update { it.copy(backupMessage = null) } }
}

data class GradesUiState(val entries: List<GradeEntry> = emptyList(), val weightedGrade: Double = 0.0)

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val gradeRepo: GradeRepository,
    private val saveGrade: SaveGradeUseCase,
    private val calculateWeightedGrade: CalculateWeightedGradeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gradeRepo.observeGrades().collect { entries ->
                _uiState.value = GradesUiState(entries, calculateWeightedGrade.execute(entries))
            }
        }
    }

    fun addGrade(entry: GradeEntry) { viewModelScope.launch { saveGrade.execute(entry) } }
}

data class OnboardingUiState(
    val step: Int = 0,
    val school: String = "",
    val gradeLevel: String = "",
    val section: String = "",
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveUser: SaveUserUseCase,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreSyncService: FirestoreSyncService
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { current ->
            current.copy(step = (current.step + 1).coerceAtMost(2))
        }
    }

    fun updateSchool(school: String) { _uiState.update { it.copy(school = school) } }
    fun updateGradeLevel(level: String) { _uiState.update { it.copy(gradeLevel = level) } }
    fun updateSection(section: String) { _uiState.update { it.copy(section = section) } }

    fun completeGuestOnboarding(name: String = "Guest Student", onFinished: () -> Unit = {}) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        onFinished()
        viewModelScope.launch {
            try {
                val firebaseUserId = firebaseAuthManager.ensureAnonymousSession()
                preferences.setOnboardingComplete(true)
                val user = UserProfile(
                    id = firebaseUserId ?: java.util.UUID.randomUUID().toString(),
                    displayName = name,
                    email = null,
                    school = _uiState.value.school,
                    gradeLevel = _uiState.value.gradeLevel,
                    section = _uiState.value.section,
                    schoolYear = "2025-2026",
                    semester = "1st",
                    isGuest = true
                )
                saveUser.execute(user)
                firestoreSyncService.syncUserProfile(user)
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Guest onboarding failed", e)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val taskRepo: TaskRepository,
    private val examRepo: ExamRepository,
    private val assignmentRepo: AssignmentRepository,
    private val saveCalendarEvent: SaveCalendarEventUseCase,
    private val holidayRepo: com.edukasyon.studentai.data.repository.HolidayRepository
) : ViewModel() {
    private val monthStart: Long
    private val monthEnd: Long
    private val visibleYear: Int

    init {
        val cal = java.util.Calendar.getInstance()
        visibleYear = cal.get(java.util.Calendar.YEAR)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        monthStart = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        monthEnd = cal.timeInMillis
        viewModelScope.launch {
            combine(taskRepo.observeTasks(), examRepo.observeExams(), assignmentRepo.observeAssignments()) { tasks, exams, assignments ->
                tasks.filter { it.dueDate != null }.forEach { task ->
                    saveCalendarEvent.execute(
                        CalendarEvent("task-${task.id}", task.title, task.description, task.dueDate!!, task.dueDate, "TASK", task.id, "#00897B")
                    )
                }
                exams.forEach { exam ->
                    saveCalendarEvent.execute(
                        CalendarEvent("exam-${exam.id}", exam.title, exam.coverage, exam.examDate, exam.examDate, "EXAM", exam.id, "#B00020")
                    )
                }
                assignments.filter { it.dueDate != null }.forEach { assignment ->
                    saveCalendarEvent.execute(
                        CalendarEvent("assignment-${assignment.id}", assignment.title, assignment.description, assignment.dueDate!!, assignment.dueDate, "ASSIGNMENT", assignment.id, "#1A237E")
                    )
                }
            }.collect { }
        }
    }

    val events: StateFlow<List<CalendarEvent>> = calendarRepo.observeEvents(monthStart, monthEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _holidays = MutableStateFlow<List<com.edukasyon.studentai.domain.model.Holiday>>(emptyList())
    val holidays: StateFlow<List<com.edukasyon.studentai.domain.model.Holiday>> = _holidays.asStateFlow()

    private val _holidaysLoading = MutableStateFlow(false)
    val holidaysLoading: StateFlow<Boolean> = _holidaysLoading.asStateFlow()

    fun refreshHolidays() {
        viewModelScope.launch {
            loadHolidaysForVisibleMonth()
        }
    }

    private suspend fun loadHolidaysForVisibleMonth() {
        val hasCache = holidayRepo.hasCachedData()
        if (!hasCache) {
            _holidaysLoading.value = true
        }

        _holidays.value = holidayRepo.getHolidays(monthStart, monthEnd)

        if (!hasCache) {
            holidayRepo.refreshYearIfStale(visibleYear, force = true)
            _holidays.value = holidayRepo.getHolidays(monthStart, monthEnd)
            _holidaysLoading.value = false
            return
        }

        _holidaysLoading.value = false
        if (holidayRepo.refreshYearIfStale(visibleYear)) {
            _holidays.value = holidayRepo.getHolidays(monthStart, monthEnd)
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences
) : ViewModel() {
    private val onboardingFinishedLocally = MutableStateFlow(false)

    val onboardingComplete: StateFlow<Boolean> = combine(
        preferences.onboardingComplete,
        onboardingFinishedLocally
    ) { stored, finishedLocally ->
        stored || finishedLocally
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val preferencesReady: StateFlow<Boolean> = combine(
        preferences.onboardingComplete,
        preferences.themeMode,
        preferences.primaryColorHex,
        preferences.secondaryColorHex,
    ) { _, _, _, _ ->
        true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM
    )

    val primaryColorHex: StateFlow<String> = preferences.primaryColorHex.stateIn(
        viewModelScope, SharingStarted.Eagerly, com.edukasyon.studentai.ui.theme.ThemePresets.DEFAULT_PRIMARY
    )

    val secondaryColorHex: StateFlow<String?> = preferences.secondaryColorHex.stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    fun markOnboardingFinished() {
        onboardingFinishedLocally.value = true
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: com.edukasyon.studentai.domain.repository.ChatRepository
) : ViewModel() {
    val conversations = chatRepo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeConversationId: String? = null

    fun messages(conversationId: String) = chatRepo.observeMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setActiveConversation(id: String) { activeConversationId = id }

    fun createConversation(title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val conv = chatRepo.createConversation(title, isGroup = true)
            onCreated(conv.id)
        }
    }

    fun sendMessage(conversationId: String, content: String) {
        viewModelScope.launch {
            chatRepo.sendMessage(conversationId, "me", content)
        }
    }
}

data class FlashcardStudyUiState(
    val dueCards: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0
) {
    val currentCard: Flashcard? get() = dueCards.getOrNull(currentIndex)
    val remaining: Int get() = (dueCards.size - currentIndex).coerceAtLeast(0)
}

@HiltViewModel
class FlashcardStudyViewModel @Inject constructor(
    private val flashcardRepo: FlashcardRepository,
    private val updateFlashcard: UpdateFlashcardUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(FlashcardStudyUiState())
    val uiState: StateFlow<FlashcardStudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            flashcardRepo.observeDueFlashcards().collect { cards ->
                _uiState.update { it.copy(dueCards = cards, currentIndex = 0) }
            }
        }
    }

    fun rate(card: Flashcard, quality: Int) {
        viewModelScope.launch {
            val updated = com.edukasyon.studentai.core.study.Sm2Algorithm.review(card, quality)
            updateFlashcard.execute(updated)
            _uiState.update { state ->
                state.copy(currentIndex = (state.currentIndex + 1).coerceAtMost(state.dueCards.size))
            }
        }
    }
}
