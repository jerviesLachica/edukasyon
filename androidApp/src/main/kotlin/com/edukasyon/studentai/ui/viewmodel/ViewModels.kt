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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val holidays: List<com.edukasyon.studentai.domain.model.Holiday> = emptyList(),
    val calendarYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val addScheduleItem: AddScheduleItemUseCase,
    private val updateScheduleItem: UpdateScheduleItemUseCase,
    private val deleteScheduleItem: DeleteScheduleItemUseCase,
    private val holidayRepo: com.edukasyon.studentai.data.repository.HolidayRepository,
    private val saveCalendarEvent: SaveCalendarEventUseCase,
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
        viewModelScope.launch {
            loadHolidaysForYear(_uiState.value.calendarYear)
        }
    }

    fun loadHolidaysForYear(year: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(calendarYear = year) }
            val cal = java.util.Calendar.getInstance()
            cal.set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(year, java.util.Calendar.DECEMBER, 31, 23, 59, 59)
            cal.set(java.util.Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            holidayRepo.refreshYearIfStale(year, force = false)
            val holidays = holidayRepo.getHolidays(start, end)
            _uiState.update { it.copy(holidays = holidays) }
        }
    }

    fun createCalendarEvent(title: String, description: String?, dateMillis: Long) {
        viewModelScope.launch {
            val endOfDay = dateMillis + (24 * 60 * 60 * 1000) - 1
            saveCalendarEvent.execute(
                CalendarEvent(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    description = description?.ifBlank { null },
                    startAt = dateMillis,
                    endAt = endOfDay,
                    type = "EVENT",
                    referenceId = null,
                    colorHex = "#3949AB"
                )
            )
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
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val saveAssignment: SaveAssignmentUseCase,
    private val deleteAssignment: DeleteAssignmentUseCase,
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
    fun updateTask(task: Task) { viewModelScope.launch { updateTaskUseCase.execute(task) } }
    fun completeTask(id: String) { viewModelScope.launch { completeTaskUseCase.execute(id) } }
    fun toggleTask(id: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == id } ?: return@launch
            if (task.status == TaskStatus.COMPLETED) {
                updateTaskUseCase.execute(
                    task.copy(
                        status = TaskStatus.PENDING,
                        completedAt = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                completeTaskUseCase.execute(id)
            }
        }
    }
    fun deleteTask(id: String) { viewModelScope.launch { deleteTaskUseCase.execute(id) } }
    fun addAssignment(a: Assignment) { viewModelScope.launch { saveAssignment.execute(a) } }
    fun updateAssignment(a: Assignment) { viewModelScope.launch { saveAssignment.execute(a) } }
    fun deleteAssignment(id: String) { viewModelScope.launch { deleteAssignment.execute(id) } }
    fun completeAssignment(id: String) {
        viewModelScope.launch {
            val assignment = _uiState.value.assignments.find { it.id == id } ?: return@launch
            if (assignment.status != TaskStatus.COMPLETED) {
                saveAssignment.execute(assignment.copy(status = TaskStatus.COMPLETED))
            }
        }
    }
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
    val messages: List<GizmoChatMessage> = emptyList(),
    val gizmo: GizmoCompanionState = GizmoCompanionState(),
    val isOnline: Boolean = true,
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
    val statusMessage: String? = null,
    val heartsLostThisSession: Int = 0,
    val xpEarnedThisSession: Int = 0,
    val activeLocalConversationId: String? = null,
    val activeConversationType: AiConversationType? = null,
    val restoredToolInput: String? = null,
)

enum class AiTool { TUTOR, SUMMARIZE, FLASHCARDS, QUIZ, SCANNER }

data class QuizSessionState(
    val quiz: Quiz,
    val currentIndex: Int = 0,
    val selectedAnswer: String? = null,
    val revealed: Boolean = false,
    val correctCount: Int = 0,
    val finished: Boolean = false,
    val blockedByHearts: Boolean = false,
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
    private val addScheduleItem: AddScheduleItemUseCase,
    private val appContextBuilder: com.edukasyon.studentai.core.ai.AppContextBuilder,
    private val aiActionExecutor: com.edukasyon.studentai.core.ai.AiActionExecutor,
    private val gizmoManager: com.edukasyon.studentai.core.gamification.GizmoGamificationManager,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val aiConversationRepo: AiConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()
    private var pendingAction: PendingAiAction? = null
    private var lastChatMessage: String? = null
    private var backendConversationId: String? = null
    private var lastScannedImageBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            runCatching {
                gizmoManager.state.collect { gizmo ->
                    _uiState.update { it.copy(gizmo = gizmo) }
                }
            }
        }
        viewModelScope.launch {
            runCatching { gizmoManager.refreshHearts() }
                .onSuccess { refreshed -> _uiState.update { it.copy(gizmo = refreshed) } }
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    private suspend fun awardXp(amount: Int, message: String? = null) {
        runCatching {
            val updated = gizmoManager.addXp(amount)
            gizmoManager.recordActivity()
            updated
        }.onSuccess { updated ->
            _uiState.update {
                it.copy(
                    gizmo = updated,
                    xpEarnedThisSession = it.xpEarnedThisSession + amount,
                    statusMessage = message ?: "+$amount XP",
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun consumeRestoredInput() {
        _uiState.update { it.copy(restoredToolInput = null) }
    }

    fun startNewConversation(type: AiConversationType) {
        backendConversationId = null
        _uiState.update {
            AiUiState(
                gizmo = it.gizmo,
                isOnline = it.isOnline,
                xpEarnedThisSession = it.xpEarnedThisSession,
                activeConversationType = type,
            )
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            val conversation = aiConversationRepo.getConversation(conversationId) ?: return@launch
            val storedMessages = aiConversationRepo.getMessages(conversationId)
            backendConversationId = conversation.backendConversationId

            when (conversation.type) {
                AiConversationType.TUTOR -> {
                    val messages = storedMessages.map { msg ->
                        GizmoChatMessage(
                            sender = if (msg.isUser) "You" else "Gizmo",
                            content = msg.content,
                            isUser = msg.isUser,
                            timestamp = msg.sentAt,
                            attachmentName = msg.attachmentName,
                            attachmentIsImage = msg.attachmentIsImage,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            activeLocalConversationId = conversation.id,
                            activeConversationType = conversation.type,
                            error = null,
                            lastSummary = null,
                            generatedFlashcards = emptyList(),
                            generatedQuiz = null,
                            quizSession = null,
                            restoredToolInput = null,
                        )
                    }
                }
                AiConversationType.SUMMARIZE -> {
                    val userInput = storedMessages.firstOrNull { it.isUser }?.content.orEmpty()
                    val summary = storedMessages
                        .firstOrNull { !it.isUser }
                        ?.let { msg ->
                            com.edukasyon.studentai.core.ai.AiConversationMetadata
                                .decode(msg.metadataJson)?.summary ?: msg.content
                        }
                    _uiState.update {
                        it.copy(
                            messages = emptyList(),
                            activeLocalConversationId = conversation.id,
                            activeConversationType = conversation.type,
                            lastSummary = summary,
                            restoredToolInput = userInput,
                            generatedFlashcards = emptyList(),
                            generatedQuiz = null,
                            quizSession = null,
                            error = null,
                        )
                    }
                }
                AiConversationType.FLASHCARDS -> {
                    val userInput = storedMessages.firstOrNull { it.isUser }?.content.orEmpty()
                    val cards = storedMessages
                        .firstOrNull { !it.isUser }
                        ?.let { msg ->
                            com.edukasyon.studentai.core.ai.AiConversationMetadata
                                .decode(msg.metadataJson)
                                ?.let(com.edukasyon.studentai.core.ai.AiConversationMetadata::toFlashcards)
                        }.orEmpty()
                    _uiState.update {
                        it.copy(
                            messages = emptyList(),
                            activeLocalConversationId = conversation.id,
                            activeConversationType = conversation.type,
                            generatedFlashcards = cards,
                            flashcardsSaved = false,
                            restoredToolInput = userInput,
                            lastSummary = null,
                            generatedQuiz = null,
                            quizSession = null,
                            error = null,
                        )
                    }
                }
                AiConversationType.QUIZ -> {
                    val userInput = storedMessages.firstOrNull { it.isUser }?.content.orEmpty()
                    val quiz = storedMessages
                        .firstOrNull { !it.isUser }
                        ?.let { msg ->
                            com.edukasyon.studentai.core.ai.AiConversationMetadata
                                .decode(msg.metadataJson)
                                ?.let(com.edukasyon.studentai.core.ai.AiConversationMetadata::toQuiz)
                        }
                    _uiState.update {
                        it.copy(
                            messages = emptyList(),
                            activeLocalConversationId = conversation.id,
                            activeConversationType = conversation.type,
                            generatedQuiz = quiz,
                            quizSession = quiz?.let { q -> QuizSessionState(quiz = q) },
                            quizSaved = false,
                            restoredToolInput = userInput,
                            lastSummary = null,
                            generatedFlashcards = emptyList(),
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun ensureTutorConversation(title: String): String {
        val existing = _uiState.value.activeLocalConversationId
        if (existing != null && _uiState.value.activeConversationType == AiConversationType.TUTOR) {
            return existing
        }
        val conversation = aiConversationRepo.createConversation(
            type = AiConversationType.TUTOR,
            title = titleFromText(title),
        )
        _uiState.update {
            it.copy(
                activeLocalConversationId = conversation.id,
                activeConversationType = AiConversationType.TUTOR,
            )
        }
        return conversation.id
    }

    private suspend fun createToolConversation(type: AiConversationType, title: String): String {
        val conversation = aiConversationRepo.createConversation(type, titleFromText(title))
        backendConversationId = null
        _uiState.update {
            it.copy(
                activeLocalConversationId = conversation.id,
                activeConversationType = type,
            )
        }
        return conversation.id
    }

    private suspend fun safePersistMessage(message: AiConversationMessage) {
        runCatching { aiConversationRepo.saveMessage(message) }
            .onFailure { Log.w(TAG, "Failed to persist AI message ${message.id}", it) }
    }

    private suspend fun safePersistBackendConversationId(localId: String, backendId: String) {
        runCatching {
            backendConversationId = backendId
            aiConversationRepo.updateBackendConversationId(localId, backendId)
        }.onFailure { Log.w(TAG, "Failed to persist backend conversation id", it) }
    }

    private fun aiErrorMessage(error: Throwable): String = when (error) {
        is com.edukasyon.studentai.core.ai.AiException ->
            error.message ?: "Could not reach Gizmo. Check your connection and try again."
        else -> error.message ?: "Could not reach Gizmo. Check your connection and try again."
    }

    private fun titleFromText(text: String): String {
        val trimmed = text.trim().replace("\n", " ")
        return if (trimmed.length <= 48) trimmed.ifBlank { "AI session" } else trimmed.take(45) + "…"
    }

    private fun aiMessageId(): String = java.util.UUID.randomUUID().toString()

    fun retryLastAction() {
        val action = pendingAction ?: return
        when (action.tool) {
            AiTool.SUMMARIZE -> summarize(action.text)
            AiTool.FLASHCARDS -> generateFlashcards(action.text)
            AiTool.QUIZ -> generateQuiz(action.text)
            AiTool.TUTOR -> lastChatMessage?.let { sendMessage(it) }
            AiTool.SCANNER -> lastScannedImageBytes?.let { analyzeScheduleImage(it) }
        }
    }

    fun sendMessage(message: String, subject: String? = null, attachment: ChatAttachmentPayload? = null) {
        if (message.isBlank() && attachment == null) return
        val displayMessage = message.ifBlank { "Please help me with this attachment." }
        lastChatMessage = displayMessage
        pendingAction = PendingAiAction(AiTool.TUTOR, displayMessage)
        val userTimestamp = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val localId = ensureTutorConversation(displayMessage)
                val userMessage = GizmoChatMessage(
                    sender = "You",
                    content = displayMessage,
                    isUser = true,
                    timestamp = userTimestamp,
                    attachmentName = attachment?.fileName,
                    attachmentIsImage = attachment?.isImage == true,
                )
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        loadingTool = AiTool.TUTOR,
                        error = null,
                        messages = it.messages + userMessage,
                    )
                }
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = true,
                        content = displayMessage,
                        sentAt = userTimestamp,
                        attachmentName = attachment?.fileName,
                        attachmentIsImage = attachment?.isImage == true,
                    )
                )
                val contextSummary = runCatching { appContextBuilder.buildSummary() }.getOrNull()
                var attachmentMimeOverride: String? = null
                val imageBase64 = attachment?.takeIf { it.isImage }?.let { img ->
                    val (bytes, mime) = com.edukasyon.studentai.core.util.ChatAttachmentUtils
                        .compressImageBytes(img.bytes, img.mimeType)
                    attachmentMimeOverride = mime
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }
                val attachmentText = attachment?.takeIf { !it.isImage }?.textContent
                val visionModel = imageBase64?.let {
                    preferences.aiModel.first().slug
                }
                val response = aiChat.execute(
                    com.edukasyon.studentai.core.ai.AiChatRequest(
                        message = displayMessage,
                        subject = subject,
                        contextSummary = contextSummary,
                        conversationId = backendConversationId,
                        attachmentName = attachment?.fileName,
                        attachmentMimeType = attachmentMimeOverride ?: attachment?.mimeType,
                        imageBase64 = imageBase64,
                        attachmentText = attachmentText,
                        model = visionModel,
                    )
                )
                val reply = response.reply.trim()
                if (reply.isEmpty()) {
                    throw com.edukasyon.studentai.core.ai.AiException(
                        "Gizmo returned an empty reply. Please try again."
                    )
                }
                safePersistBackendConversationId(localId, response.conversationId)
                val parsed = com.edukasyon.studentai.core.ai.AiActionParser.parse(reply)
                val appliedActions = runCatching {
                    if (parsed.actions.isNotEmpty()) aiActionExecutor.execute(parsed.actions) else emptyList()
                }.getOrElse { emptyList() }
                val assistantTimestamp = System.currentTimeMillis()
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = false,
                        content = parsed.displayText,
                        sentAt = assistantTimestamp,
                    )
                )
                awardXp(GizmoConstants.XP_CHAT)
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        loadingTool = null,
                        messages = s.messages + GizmoChatMessage(
                            "Gizmo",
                            parsed.displayText,
                            isUser = false,
                            timestamp = assistantTimestamp,
                        ),
                        statusMessage = appliedActions.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gizmo chat failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        error = aiErrorMessage(e),
                    )
                }
            }
        }
    }

    fun sendQuickPrompt(prompt: String) = sendMessage(prompt)

    fun toggleMemoriseMode() {
        viewModelScope.launch {
            val updated = gizmoManager.setMemoriseMode(!_uiState.value.gizmo.memoriseMode)
            _uiState.update { it.copy(gizmo = updated) }
        }
    }

    fun summarize(text: String) {
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Paste some note content to summarize.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.SUMMARIZE, text)
        viewModelScope.launch {
            val localId = createToolConversation(AiConversationType.SUMMARIZE, text)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = text,
                    sentAt = userTimestamp,
                )
            )
            _uiState.update {
                it.copy(isLoading = true, loadingTool = AiTool.SUMMARIZE, error = null, lastSummary = null)
            }
            try {
                val result = aiSummarize.execute(text)
                val metadata = com.edukasyon.studentai.core.ai.AiConversationMetadata.encodeSummary(result)
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = false,
                        content = result,
                        sentAt = System.currentTimeMillis(),
                        metadataJson = metadata,
                    )
                )
                awardXp(GizmoConstants.XP_CHAT, "Summary ready · +${GizmoConstants.XP_CHAT} XP")
                _uiState.update {
                    it.copy(isLoading = false, loadingTool = null, lastSummary = result)
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
            val localId = createToolConversation(AiConversationType.FLASHCARDS, text)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = text,
                    sentAt = userTimestamp,
                )
            )
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
                val metadata = com.edukasyon.studentai.core.ai.AiConversationMetadata.encodeFlashcards(cards)
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = false,
                        content = "Generated ${cards.size} flashcards",
                        sentAt = System.currentTimeMillis(),
                        metadataJson = metadata,
                    )
                )
                awardXp(GizmoConstants.XP_GENERATE_FLASHCARDS, "Generated ${cards.size} flashcards · +${GizmoConstants.XP_GENERATE_FLASHCARDS} XP")
                _uiState.update {
                    it.copy(isLoading = false, loadingTool = null, generatedFlashcards = cards)
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
                awardXp(GizmoConstants.XP_SAVE_FLASHCARDS, "Flashcards saved · +${GizmoConstants.XP_SAVE_FLASHCARDS} XP")
                _uiState.update { it.copy(flashcardsSaved = true) }
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
        val gizmo = _uiState.value.gizmo
        if (gizmo.memoriseMode && !gizmo.canQuiz) {
            _uiState.update {
                it.copy(error = "Out of hearts! Wait for them to refill or turn off Memorise mode.")
            }
            return
        }
        pendingAction = PendingAiAction(AiTool.QUIZ, text)
        viewModelScope.launch {
            val localId = createToolConversation(AiConversationType.QUIZ, text)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = text,
                    sentAt = userTimestamp,
                )
            )
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
                val metadata = com.edukasyon.studentai.core.ai.AiConversationMetadata.encodeQuiz(quiz)
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = false,
                        content = quiz.title,
                        sentAt = System.currentTimeMillis(),
                        metadataJson = metadata,
                    )
                )
                awardXp(GizmoConstants.XP_GENERATE_QUIZ, "Quiz ready — ${quiz.questions.size} questions · +${GizmoConstants.XP_GENERATE_QUIZ} XP")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        generatedQuiz = quiz,
                        quizSession = QuizSessionState(quiz = quiz),
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
        viewModelScope.launch {
            var gizmo = _uiState.value.gizmo
            var heartsLost = _uiState.value.heartsLostThisSession
            if (!isCorrect && gizmo.memoriseMode) {
                gizmo = gizmoManager.loseHeart()
                heartsLost += 1
                if (!gizmo.canQuiz) {
                    _uiState.update {
                        it.copy(
                            gizmo = gizmo,
                            heartsLostThisSession = heartsLost,
                            quizSession = session.copy(revealed = true, blockedByHearts = true),
                            error = "Out of hearts! Wait ${formatCooldown(gizmo.heartsCooldownRemainingMs ?: 0)} to quiz again.",
                        )
                    }
                    return@launch
                }
            } else if (isCorrect) {
                awardXp(GizmoConstants.XP_CORRECT_ANSWER)
                gizmo = _uiState.value.gizmo
            }
            _uiState.update {
                it.copy(
                    gizmo = gizmo,
                    heartsLostThisSession = heartsLost,
                    quizSession = session.copy(
                        revealed = true,
                        correctCount = session.correctCount + if (isCorrect) 1 else 0,
                    ),
                )
            }
        }
    }

    private fun formatCooldown(ms: Long): String {
        val totalSeconds = kotlin.math.ceil(ms / 1000.0).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
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
        lastScannedImageBytes = imageData.copyOf()
        pendingAction = PendingAiAction(AiTool.SCANNER, "")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingTool = AiTool.SCANNER, error = null) }
            try {
                val result = aiAnalyzeSchedule.execute(imageData)
                if (result.classes.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingTool = null,
                            error = "No classes found. Try a clearer photo or different angle.",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        scannedClasses = result.classes,
                        statusMessage = "Found ${result.classes.size} classes — review and import",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        error = e.message ?: "Failed to analyze schedule image",
                    )
                }
            }
        }
    }

    fun clearScannedClasses() {
        _uiState.update { it.copy(scannedClasses = emptyList(), error = null) }
    }

    fun confirmScannedClasses() {
        val classes = _uiState.value.scannedClasses
        if (classes.isEmpty()) return
        viewModelScope.launch {
            try {
                classes.forEach { cls ->
                    addScheduleItem.execute(
                        ScheduleItem(
                            id = java.util.UUID.randomUUID().toString(),
                            subjectId = null,
                            subjectName = cls.subject,
                            teacher = cls.teacher,
                            room = cls.room,
                            building = null,
                            dayOfWeek = DayOfWeek.fromString(cls.day) ?: DayOfWeek.MONDAY,
                            startTime = cls.startTime,
                            endTime = cls.endTime,
                            colorHex = "#1A237E",
                            notes = null,
                            semester = "",
                            schoolYear = "",
                        )
                    )
                }
                awardXp(GizmoConstants.XP_CHAT, "Imported ${classes.size} classes to schedule")
                _uiState.update { it.copy(scannedClasses = emptyList()) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to import classes") }
            }
        }
    }

    private companion object {
        const val TAG = "AiViewModel"
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
    val aiModel: AiModel = AiModel.STANDARD,
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
                    combine(
                        preferences.primaryColorHex,
                        preferences.secondaryColorHex,
                        preferences.classReminders
                    ) { primary, secondary, classR -> Triple(primary, secondary, classR) },
                    combine(
                        preferences.taskReminders,
                        preferences.examReminders,
                        preferences.aiModel
                    ) { task, exam, aiModel -> Triple(task, exam, aiModel) }
                ) { colors, reminders -> colors to reminders },
                connectivity.isOnline
            ) { userInfo, prefs, online ->
                val (colors, reminders) = prefs
                ProfileUiState(
                    user = userInfo.first,
                    themeMode = userInfo.second,
                    notificationsEnabled = userInfo.third,
                    primaryColorHex = colors.first,
                    secondaryColorHex = colors.second,
                    classReminders = colors.third,
                    taskReminders = reminders.first,
                    examReminders = reminders.second,
                    aiModel = reminders.third,
                    isOnline = online
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
    fun setAiModel(model: AiModel) { viewModelScope.launch { preferences.setAiModel(model) } }

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

data class NotificationSettingsUiState(
    val notificationsEnabled: Boolean = true,
    val classReminders: Boolean = true,
    val taskReminders: Boolean = true,
    val examReminders: Boolean = true,
    val classReminderAtTime: Boolean = true,
    val classReminder15MinBefore: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val notificationPermissionGranted: Boolean = true,
    val dndAccessGranted: Boolean = false,
    val batteryOptimizationDisabled: Boolean = false
) {
    val notificationsOn: Boolean
        get() = notificationsEnabled && notificationPermissionGranted
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.notificationsEnabled,
                preferences.classReminders,
                preferences.taskReminders,
                preferences.examReminders,
                preferences.classReminderAtTime,
                preferences.classReminder15MinBefore,
                preferences.notificationSoundEnabled
            ) { values ->
                NotificationSettingsUiState(
                    notificationsEnabled = values[0],
                    classReminders = values[1],
                    taskReminders = values[2],
                    examReminders = values[3],
                    classReminderAtTime = values[4],
                    classReminder15MinBefore = values[5],
                    notificationSoundEnabled = values[6],
                    notificationPermissionGranted = _uiState.value.notificationPermissionGranted,
                    dndAccessGranted = _uiState.value.dndAccessGranted,
                    batteryOptimizationDisabled = _uiState.value.batteryOptimizationDisabled
                )
            }.collect { prefsState ->
                _uiState.update { current ->
                    prefsState.copy(
                        notificationPermissionGranted = current.notificationPermissionGranted,
                        dndAccessGranted = current.dndAccessGranted,
                        batteryOptimizationDisabled = current.batteryOptimizationDisabled
                    )
                }
            }
        }
    }

    fun refreshSystemState() {
        val nm = androidx.core.app.NotificationManagerCompat.from(appContext)
        val notificationGranted = nm.areNotificationsEnabled()
        val dndGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
            manager.isNotificationPolicyAccessGranted
        } else true
        val powerManager = appContext.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val batteryOptDisabled = powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        _uiState.update {
            it.copy(
                notificationPermissionGranted = notificationGranted,
                dndAccessGranted = dndGranted,
                batteryOptimizationDisabled = batteryOptDisabled
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { preferences.setNotificationsEnabled(enabled) } }
    fun setClassReminders(enabled: Boolean) { viewModelScope.launch { preferences.setClassReminders(enabled) } }
    fun setTaskReminders(enabled: Boolean) { viewModelScope.launch { preferences.setTaskReminders(enabled) } }
    fun setExamReminders(enabled: Boolean) { viewModelScope.launch { preferences.setExamReminders(enabled) } }
    fun setClassReminderAtTime(enabled: Boolean) { viewModelScope.launch { preferences.setClassReminderAtTime(enabled) } }
    fun setClassReminder15MinBefore(enabled: Boolean) { viewModelScope.launch { preferences.setClassReminder15MinBefore(enabled) } }
    fun setNotificationSoundEnabled(enabled: Boolean) { viewModelScope.launch { preferences.setNotificationSoundEnabled(enabled) } }
}

data class LectureFilesUiState(
    val files: List<LectureFile> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val isLoading: Boolean = true
) {
    fun subjectName(subjectId: String?): String =
        subjects.find { it.id == subjectId }?.name ?: "General"
}

@HiltViewModel
class LectureFilesViewModel @Inject constructor(
    private val lectureFileRepo: LectureFileRepository,
    private val subjectRepo: SubjectRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LectureFilesUiState())
    val uiState: StateFlow<LectureFilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                lectureFileRepo.observeFiles(),
                subjectRepo.observeSubjects()
            ) { files, subjects ->
                LectureFilesUiState(files = files, subjects = subjects, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }

    fun addFile(title: String, uri: String, mimeType: String, subjectId: String?) {
        viewModelScope.launch {
            lectureFileRepo.saveFile(
                LectureFile(
                    id = java.util.UUID.randomUUID().toString(),
                    subjectId = subjectId,
                    title = title,
                    fileUri = uri,
                    mimeType = mimeType,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch { lectureFileRepo.deleteFile(id) }
    }
}

data class GradesUiState(
    val entries: List<GradeEntry> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val weightedGrade: Double = 0.0,
    val selectedTerm: String? = null
)

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val gradeRepo: GradeRepository,
    private val subjectRepo: SubjectRepository,
    private val saveGrade: SaveGradeUseCase,
    private val deleteGrade: DeleteGradeUseCase,
    private val calculateWeightedGrade: CalculateWeightedGradeUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                gradeRepo.observeGrades(),
                subjectRepo.observeSubjects()
            ) { entries, subjects ->
                Triple(entries, subjects, calculateWeightedGrade.execute(entries))
            }.collect { (entries, subjects, weighted) ->
                _uiState.update { current ->
                    current.copy(
                        entries = entries,
                        subjects = subjects,
                        weightedGrade = weighted
                    )
                }
            }
        }
    }

    fun setSelectedTerm(term: String?) {
        _uiState.update { it.copy(selectedTerm = term) }
    }

    fun addGrade(entry: GradeEntry) { viewModelScope.launch { saveGrade.execute(entry) } }

    fun removeGrade(id: String) { viewModelScope.launch { deleteGrade.execute(id) } }
}

data class OnboardingUiState(
    val step: Int = 0,
    val displayName: String = "",
    val school: String = "",
    val gradeLevel: String = "",
    val section: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val classReminders: Boolean = true,
    val classReminderAtTime: Boolean = true,
    val classReminder15MinBefore: Boolean = true,
    val taskReminders: Boolean = true,
    val examReminders: Boolean = true,
    val widgetsExplored: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val batteryOptimizationDisabled: Boolean = false,
    val isSaving: Boolean = false
) {
    val totalSteps: Int = 7
    val progress: Float get() = (step + 1) / totalSteps.toFloat()

    val notifyMeSummary: String
        get() = buildList {
            if (classReminderAtTime) add("At class time")
            if (classReminder15MinBefore) add("15m before")
        }.joinToString(" + ").ifBlank { "Off" }

    val permissionsGrantedCount: Int
        get() = listOf(
            notificationPermissionGranted,
            exactAlarmsAllowed,
            batteryOptimizationDisabled
        ).count { it }

    val appearanceLabel: String
        get() = themeMode.name.lowercase().replaceFirstChar { it.uppercase() }

    val widgetsLabel: String
        get() = if (widgetsExplored) "Explored" else "None"
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveUser: SaveUserUseCase,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreSyncService: FirestoreSyncService,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val theme = preferences.themeMode.first()
            _uiState.update { it.copy(themeMode = theme) }
        }
    }

    fun refreshPermissionState() {
        val nm = androidx.core.app.NotificationManagerCompat.from(appContext)
        val notificationGranted = nm.areNotificationsEnabled()
        val alarmManager = appContext.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val exactAlarms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        val powerManager = appContext.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val batteryOptDisabled = powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
        _uiState.update {
            it.copy(
                notificationPermissionGranted = notificationGranted,
                exactAlarmsAllowed = exactAlarms,
                batteryOptimizationDisabled = batteryOptDisabled
            )
        }
    }

    fun nextStep() {
        _uiState.update { current ->
            current.copy(step = (current.step + 1).coerceAtMost(current.totalSteps - 1))
        }
    }

    fun previousStep() {
        _uiState.update { current ->
            current.copy(step = (current.step - 1).coerceAtLeast(0))
        }
    }

    fun updateDisplayName(name: String) { _uiState.update { it.copy(displayName = name) } }
    fun updateSchool(school: String) { _uiState.update { it.copy(school = school) } }
    fun updateGradeLevel(level: String) { _uiState.update { it.copy(gradeLevel = level) } }
    fun updateSection(section: String) { _uiState.update { it.copy(section = section) } }

    fun setTheme(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun setClassReminders(enabled: Boolean) {
        _uiState.update { it.copy(classReminders = enabled) }
        viewModelScope.launch { preferences.setClassReminders(enabled) }
    }

    fun setClassReminderAtTime(enabled: Boolean) {
        _uiState.update { it.copy(classReminderAtTime = enabled) }
        viewModelScope.launch { preferences.setClassReminderAtTime(enabled) }
    }

    fun setClassReminder15MinBefore(enabled: Boolean) {
        _uiState.update { it.copy(classReminder15MinBefore = enabled) }
        viewModelScope.launch { preferences.setClassReminder15MinBefore(enabled) }
    }

    fun setTaskReminders(enabled: Boolean) {
        _uiState.update { it.copy(taskReminders = enabled) }
        viewModelScope.launch { preferences.setTaskReminders(enabled) }
    }

    fun setExamReminders(enabled: Boolean) {
        _uiState.update { it.copy(examReminders = enabled) }
        viewModelScope.launch { preferences.setExamReminders(enabled) }
    }

    fun markWidgetsExplored() {
        _uiState.update { it.copy(widgetsExplored = true) }
        viewModelScope.launch { preferences.setOnboardingWidgetsExplored(true) }
    }

    fun skipWidgets() {
        nextStep()
    }

    fun completeOnboarding(onFinished: () -> Unit = {}) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        onFinished()
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val firebaseUserId = firebaseAuthManager.ensureAnonymousSession()
                preferences.setOnboardingComplete(true)
                val user = UserProfile(
                    id = firebaseUserId ?: java.util.UUID.randomUUID().toString(),
                    displayName = state.displayName.ifBlank { "Student" },
                    email = null,
                    school = state.school,
                    gradeLevel = state.gradeLevel,
                    section = state.section,
                    schoolYear = "2025-2026",
                    semester = "1st",
                    isGuest = true
                )
                saveUser.execute(user)
                firestoreSyncService.syncUserProfile(user)
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Onboarding failed", e)
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
        if (_holidays.value.isNotEmpty()) {
            _holidaysLoading.value = false
        }

        val refreshed = holidayRepo.refreshYearIfStale(visibleYear, force = !hasCache)
        if (refreshed || !hasCache) {
            _holidays.value = holidayRepo.getHolidays(monthStart, monthEnd)
        }
        _holidaysLoading.value = false
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiConversationHistoryViewModel @Inject constructor(
    private val aiConversationRepo: AiConversationRepository,
) : ViewModel() {
    private val filterTypes = MutableStateFlow<List<AiConversationType>>(AiConversationType.entries)

    val conversations: StateFlow<List<AiConversation>> = filterTypes
        .flatMapLatest { types -> aiConversationRepo.observeConversations(types) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(scope: String) {
        filterTypes.value = when (scope) {
            "tutor" -> listOf(AiConversationType.TUTOR)
            "tools" -> AiConversationType.TOOL_TYPES
            else -> AiConversationType.entries
        }
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
