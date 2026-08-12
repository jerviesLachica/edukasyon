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
    val viewMode: String = "daily",
    val error: String? = null
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val addScheduleItem: AddScheduleItemUseCase,
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
    }

    fun selectDay(day: DayOfWeek) { _uiState.update { it.copy(selectedDay = day) } }
    fun setViewMode(mode: String) { _uiState.update { it.copy(viewMode = mode) } }
    fun addClass(item: ScheduleItem) { viewModelScope.launch { addScheduleItem.execute(item) } }
    fun deleteClass(id: String) { viewModelScope.launch { deleteScheduleItem.execute(id) } }

    fun itemsForSelectedDay(): List<ScheduleItem> =
        _uiState.value.allItems.filter { it.dayOfWeek == _uiState.value.selectedDay }.sortedBy { it.startTime }
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
    val error: String? = null,
    val lastSummary: String? = null,
    val generatedFlashcards: List<Flashcard> = emptyList(),
    val generatedQuiz: Quiz? = null,
    val studyPlan: StudyPlan? = null,
    val scannedClasses: List<com.edukasyon.studentai.core.ai.ExtractedClass> = emptyList()
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val aiChat: AiChatUseCase,
    private val aiSummarize: AiSummarizeUseCase,
    private val aiGenerateFlashcards: AiGenerateFlashcardsUseCase,
    private val aiGenerateQuiz: AiGenerateQuizUseCase,
    private val aiAnalyzeSchedule: AiAnalyzeScheduleUseCase,
    private val saveFlashcards: SaveFlashcardsUseCase,
    private val addScheduleItem: AddScheduleItemUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    fun sendMessage(message: String, subject: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = aiChat.execute(com.edukasyon.studentai.core.ai.AiChatRequest(message, subject))
                _uiState.update { s ->
                    s.copy(isLoading = false, messages = s.messages + ("You" to message) + ("AI" to response.reply))
                }
            } catch (e: com.edukasyon.studentai.core.ai.AiException) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun summarize(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = aiSummarize.execute(text)
                _uiState.update { it.copy(isLoading = false, lastSummary = result) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateFlashcards(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val cards = aiGenerateFlashcards.execute(text)
                saveFlashcards.execute(cards)
                _uiState.update { it.copy(isLoading = false, generatedFlashcards = cards) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun generateQuiz(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val quiz = aiGenerateQuiz.execute(text)
                _uiState.update { it.copy(isLoading = false, generatedQuiz = quiz) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun analyzeScheduleImage(imageData: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = aiAnalyzeSchedule.execute(imageData)
                _uiState.update { it.copy(isLoading = false, scannedClasses = result.classes) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
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
    val notificationsEnabled: Boolean = true,
    val isOnline: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(userRepo.observeUser(), preferences.themeMode, preferences.notificationsEnabled, connectivity.isOnline) { u, t, n, o ->
                ProfileUiState(user = u, themeMode = t, notificationsEnabled = n, isOnline = o)
            }.collect { _uiState.value = it }
        }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { preferences.setThemeMode(mode) } }
    fun setNotifications(enabled: Boolean) { viewModelScope.launch { preferences.setNotificationsEnabled(enabled) } }
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
    private val saveCalendarEvent: SaveCalendarEventUseCase
) : ViewModel() {
    private val monthStart: Long
    private val monthEnd: Long

    init {
        val cal = java.util.Calendar.getInstance()
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

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM
    )

    fun markOnboardingFinished() {
        onboardingFinishedLocally.value = true
    }
}
