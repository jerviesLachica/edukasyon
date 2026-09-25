package com.edukasyon.studentai.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.GradeCalculator
import com.edukasyon.studentai.core.util.SubjectPickerMerger
import com.edukasyon.studentai.core.ai.AiModelRouter
import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.ai.StepModelQuotaTracker
import com.edukasyon.studentai.core.mlkit.ScheduleParser
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.domain.repository.*
import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.firebase.FirestoreSyncService
import com.edukasyon.studentai.domain.usecase.*
import com.edukasyon.studentai.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.edukasyon.studentai.core.notifications.ReminderScheduler
import com.edukasyon.studentai.core.notifications.ReminderType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class HomeWeekDay(
    val dayOfWeek: DayOfWeek,
    val dateOfMonth: Int,
    val shortLabel: String,
    val isToday: Boolean,
)

data class AcademicOverview(
    val subjectsCount: Int = 0,
    val currentGpa: Double? = null,
    val tasksCount: Int = 0,
    val upcomingExamsCount: Int = 0,
    val weekProgressPercent: Int = 0,
    val strongestSubject: String? = null,
    val needsAttentionSubject: String? = null,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "",
    val userName: String = "Student",
    val avatarUri: String? = null,
    val selectedDay: DayOfWeek = DateUtils.getTodayDayOfWeek(),
    val weekDays: List<HomeWeekDay> = emptyList(),
    val classesTodayCount: Int = 0,
    val assignmentCount: Int = 0,
    val nextClass: ScheduleItem? = null,
    val todaySchedule: List<ScheduleItem> = emptyList(),
    val selectedDaySchedule: List<ScheduleItem> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val upcomingExams: List<Exam> = emptyList(),
    val examReadiness: Map<String, ExamReadiness> = emptyMap(),
    val subjectNames: Map<String, String> = emptyMap(),
    val aiSuggestion: String? = null,
    val isOnline: Boolean = true,
    val showNotificationDot: Boolean = false,
    val academicOverview: AcademicOverview = AcademicOverview(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val examRepo: ExamRepository,
    private val subjectRepo: SubjectRepository,
    private val gradeRepo: GradeRepository,
    private val observeExamReadinessMap: ObserveExamReadinessMapUseCase,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _selectedDay = MutableStateFlow(DateUtils.getTodayDayOfWeek())

    init { loadDashboard() }

    fun selectDay(day: DayOfWeek) {
        _selectedDay.value = day
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            try {
                val dashboardFlow = combine(
                    combine(
                        userRepo.observeUser().onStart { emit(null) },
                        scheduleRepo.observeSchedule().onStart { emit(emptyList()) },
                        taskRepo.observeUpcoming(5).onStart { emit(emptyList()) },
                    ) { user, allSchedule, tasks ->
                        Triple(user, allSchedule, tasks)
                    },
                    combine(
                        examRepo.observeUpcoming(3).onStart { emit(emptyList()) },
                        connectivity.isOnline.onStart { emit(true) },
                        subjectRepo.observeSubjects().onStart { emit(emptyList()) },
                    ) { exams, online, subjects ->
                        Triple(exams, online, subjects)
                    },
                    combine(
                        gradeRepo.observeGrades().onStart { emit(emptyList()) },
                        taskRepo.observeTasks().onStart { emit(emptyList()) },
                        examRepo.observeExams().onStart { emit(emptyList()) },
                    ) { grades, allTasks, allExams ->
                        Triple(grades, allTasks, allExams)
                    },
                ) { scheduleData, metaData, academicData ->
                    DashboardSnapshot(
                        user = scheduleData.first,
                        allSchedule = scheduleData.second,
                        tasks = scheduleData.third,
                        exams = metaData.first,
                        online = metaData.second,
                        subjects = metaData.third,
                        grades = academicData.first,
                        allTasks = academicData.second,
                        allExams = academicData.third,
                    )
                }
                combine(dashboardFlow, _selectedDay, observeExamReadinessMap()) { snapshot, selectedDay, readinessMap ->
                    val today = DateUtils.getTodayDayOfWeek()
                    val todaySchedule = snapshot.allSchedule
                        .filter { it.dayOfWeek == today }
                        .sortedBy { it.startTime }
                    val selectedSchedule = snapshot.allSchedule
                        .filter { it.dayOfWeek == selectedDay }
                        .sortedBy { it.startTime }
                    val subjectNames = snapshot.subjects.associate { it.id to it.name }
                    val next = todaySchedule.firstOrNull { isUpcoming(it.startTime) }
                    val suggestion = snapshot.exams.firstOrNull()?.let { exam ->
                        "You have a ${exam.title} ${DateUtils.formatCountdown(exam.examDate)}. Would you like me to create a study plan?"
                    }
                    val academicOverview = buildAcademicOverview(
                        subjects = snapshot.subjects,
                        grades = snapshot.grades,
                        allTasks = snapshot.allTasks,
                        allExams = snapshot.allExams,
                    )
                    HomeUiState(
                        isLoading = false,
                        greeting = DateUtils.greeting(),
                        userName = snapshot.user?.displayName ?: "Student",
                        avatarUri = snapshot.user?.avatarUri,
                        selectedDay = selectedDay,
                        weekDays = buildCurrentWeekDays(selectedDay),
                        classesTodayCount = todaySchedule.size,
                        assignmentCount = snapshot.tasks.size,
                        nextClass = next,
                        todaySchedule = todaySchedule,
                        selectedDaySchedule = selectedSchedule,
                        upcomingTasks = snapshot.tasks,
                        upcomingExams = snapshot.exams,
                        examReadiness = readinessMap,
                        subjectNames = subjectNames,
                        aiSuggestion = suggestion,
                        isOnline = snapshot.online,
                        showNotificationDot = snapshot.tasks.isNotEmpty() || snapshot.exams.isNotEmpty(),
                        academicOverview = academicOverview,
                    )
                }.collect { _uiState.value = it }
            } catch (_: Exception) {
                _uiState.value = HomeUiState(
                    isLoading = false,
                    greeting = DateUtils.greeting(),
                    weekDays = buildCurrentWeekDays(DateUtils.getTodayDayOfWeek()),
                )
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

    private data class DashboardSnapshot(
        val user: UserProfile?,
        val allSchedule: List<ScheduleItem>,
        val tasks: List<Task>,
        val exams: List<Exam>,
        val online: Boolean,
        val subjects: List<Subject>,
        val grades: List<GradeEntry>,
        val allTasks: List<Task>,
        val allExams: List<Exam>,
    )

    companion object {
        fun buildAcademicOverview(
            subjects: List<Subject>,
            grades: List<GradeEntry>,
            allTasks: List<Task>,
            allExams: List<Exam>,
        ): AcademicOverview {
            val now = System.currentTimeMillis()
            val subjectById = subjects.associateBy { it.id }
            val pendingTasks = allTasks.count {
                it.status == TaskStatus.PENDING || it.status == TaskStatus.IN_PROGRESS
            }
            val upcomingExamsCount = allExams.count { it.examDate >= now }
            val currentGpa = grades.takeIf { it.isNotEmpty() }
                ?.let { GradeCalculator.calculateWeightedGrade(it) }

            val subjectAverages = grades
                .groupBy { it.subjectId }
                .map { (subjectId, entries) ->
                    val average = entries
                        .map { GradeCalculator.calculatePercentage(it.score, it.maxScore) }
                        .average()
                    (subjectById[subjectId]?.name ?: "General") to average
                }
                .sortedByDescending { it.second }

            val strongestSubject = subjectAverages.firstOrNull()?.first
            val needsAttentionSubject = subjectAverages
                .takeIf { it.size > 1 }
                ?.last()
                ?.first

            return AcademicOverview(
                subjectsCount = subjects.size,
                currentGpa = currentGpa,
                tasksCount = pendingTasks,
                upcomingExamsCount = upcomingExamsCount,
                weekProgressPercent = computeWeekProgressPercent(allTasks),
                strongestSubject = strongestSubject,
                needsAttentionSubject = needsAttentionSubject,
            )
        }

        private fun computeWeekProgressPercent(allTasks: List<Task>): Int {
            val (weekStart, weekEnd) = currentWeekRangeMillis()
            val weekTasks = allTasks.filter { task ->
                task.status != TaskStatus.ARCHIVED && isTaskRelevantThisWeek(task, weekStart, weekEnd)
            }
            val pool = weekTasks.ifEmpty {
                allTasks.filter { it.status != TaskStatus.ARCHIVED }
            }
            if (pool.isEmpty()) return 0
            val completed = pool.count { it.status == TaskStatus.COMPLETED }
            return ((completed.toDouble() / pool.size) * 100).toInt().coerceIn(0, 100)
        }

        private fun isTaskRelevantThisWeek(task: Task, weekStart: Long, weekEnd: Long): Boolean {
            val dueInWeek = task.dueDate?.let { it in weekStart until weekEnd } == true
            val completedInWeek = task.completedAt?.let { it in weekStart until weekEnd } == true
            return dueInWeek || completedInWeek
        }

        private fun currentWeekRangeMillis(): Pair<Long, Long> {
            val startCal = java.util.Calendar.getInstance().apply {
                firstDayOfWeek = java.util.Calendar.SUNDAY
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val weekStart = startCal.timeInMillis
            val weekEnd = (startCal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_MONTH, 7)
            }.timeInMillis
            return weekStart to weekEnd
        }

        fun buildCurrentWeekDays(selectedDay: DayOfWeek): List<HomeWeekDay> {
            val today = DateUtils.getTodayDayOfWeek()
            val cal = java.util.Calendar.getInstance().apply {
                firstDayOfWeek = java.util.Calendar.SUNDAY
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
            }
            val labelFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            return (0..6).map { offset ->
                val dayCal = (cal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_MONTH, offset)
                }
                val dayOfWeek = calendarDayToDomain(dayCal.get(java.util.Calendar.DAY_OF_WEEK))
                HomeWeekDay(
                    dayOfWeek = dayOfWeek,
                    dateOfMonth = dayCal.get(java.util.Calendar.DAY_OF_MONTH),
                    shortLabel = labelFormat.format(dayCal.time),
                    isToday = dayOfWeek == today,
                )
            }
        }

        private fun calendarDayToDomain(calendarDay: Int): DayOfWeek = when (calendarDay) {
            java.util.Calendar.MONDAY -> DayOfWeek.MONDAY
            java.util.Calendar.TUESDAY -> DayOfWeek.TUESDAY
            java.util.Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            java.util.Calendar.THURSDAY -> DayOfWeek.THURSDAY
            java.util.Calendar.FRIDAY -> DayOfWeek.FRIDAY
            java.util.Calendar.SATURDAY -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }

        fun isClassActive(item: ScheduleItem, selectedDay: DayOfWeek): Boolean {
            if (selectedDay != DateUtils.getTodayDayOfWeek()) return false
            val nowMinutes = currentMinutesOfDay()
            val start = timeToMinutes(item.startTime) ?: return false
            val end = timeToMinutes(item.endTime) ?: return false
            return nowMinutes in start until end
        }

        fun timeToMinutes(time: String): Int? {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return hour * 60 + minute
        }

        private fun currentMinutesOfDay(): Int {
            val now = java.util.Calendar.getInstance()
            return now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        }
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
    private val syncScheduler: com.edukasyon.studentai.core.sync.SyncScheduler,
    private val scheduleParser: ScheduleParser,
    private val mlKitTextRecognizer: com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer,
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
    fun addClass(item: ScheduleItem) {
        viewModelScope.launch { addScheduleItem.execute(item) }
        syncScheduler.scheduleImmediateSync()
    }
    fun updateClass(item: ScheduleItem) {
        viewModelScope.launch { updateScheduleItem.execute(item) }
        syncScheduler.scheduleImmediateSync()
    }
    fun deleteClass(id: String) {
        viewModelScope.launch { deleteScheduleItem.execute(id) }
        syncScheduler.scheduleImmediateSync()
    }

    fun moveClassToDay(item: ScheduleItem, targetDay: DayOfWeek): String? {
        if (item.dayOfWeek == targetDay) return null
        viewModelScope.launch { updateScheduleItem.execute(item.copy(dayOfWeek = targetDay)) }
        syncScheduler.scheduleImmediateSync()
        return "Moved to ${targetDay.displayName}"
    }

    fun duplicateClass(item: ScheduleItem, targetDay: DayOfWeek): String {
        val copy = item.copy(
            id = java.util.UUID.randomUUID().toString(),
            dayOfWeek = targetDay,
        )
        viewModelScope.launch { addScheduleItem.execute(copy) }
        syncScheduler.scheduleImmediateSync()
        return if (targetDay == item.dayOfWeek) {
            "Class duplicated — drag to move"
        } else {
            "Duplicated to ${targetDay.displayName}"
        }
    }

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
    val examReadiness: Map<String, ExamReadiness> = emptyMap(),
    val subjects: List<Subject> = emptyList(),
    val jeviDecks: List<JeviDeck> = emptyList(),
    val expandedExamId: String? = null,
    val isLoading: Boolean = true,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val taskRepo: TaskRepository,
    private val assignmentRepo: AssignmentRepository,
    private val examRepo: ExamRepository,
    private val createTask: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val uncompleteTaskUseCase: UncompleteTaskUseCase,
    private val insertSubtaskUseCase: InsertSubtaskUseCase,
    private val updateSubtaskUseCase: UpdateSubtaskUseCase,
    private val deleteSubtaskUseCase: DeleteSubtaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val saveAssignment: SaveAssignmentUseCase,
    private val deleteAssignment: DeleteAssignmentUseCase,
    private val saveExam: SaveExamUseCase,
    private val deleteExamUseCase: DeleteExamUseCase,
    private val duplicateExamUseCase: DuplicateExamUseCase,
    private val subjectRepo: SubjectRepository,
    private val scheduleRepo: ScheduleRepository,
    private val getJeviDecks: GetJeviDecksUseCase,
    private val observeExamReadinessMap: ObserveExamReadinessMapUseCase,
    private val linkExamStudy: LinkExamStudyUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                combine(
                    taskRepo.observeTasks(),
                    assignmentRepo.observeAssignments(),
                    examRepo.observeExams(),
                ) { tasks, assignments, exams -> Triple(tasks, assignments, exams) },
                combine(
                    subjectRepo.observeSubjects(),
                    scheduleRepo.observeSchedule(),
                    getJeviDecks(),
                    observeExamReadinessMap(),
                ) { subjects, schedule, decks, readiness ->
                    Triple(
                        SubjectPickerMerger.mergeSubjectsForPicker(subjects, schedule),
                        decks,
                        readiness,
                    )
                },
            ) { (tasks, assignments, exams), (subjects, decks, readiness) ->
                PlannerUiState(
                    isLoading = false,
                    tasks = tasks,
                    assignments = assignments,
                    exams = exams,
                    subjects = subjects,
                    jeviDecks = decks,
                    examReadiness = readiness,
                    selectedTab = _uiState.value.selectedTab,
                    expandedExamId = _uiState.value.expandedExamId,
                    snackbarMessage = _uiState.value.snackbarMessage,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun selectTab(tab: Int) { _uiState.update { it.copy(selectedTab = tab) } }
    fun toggleExamExpanded(examId: String) {
        _uiState.update { state ->
            state.copy(expandedExamId = if (state.expandedExamId == examId) null else examId)
        }
    }

    fun linkExamStudy(exam: Exam, subjectId: String, deckId: String?, newDeckTitle: String?) {
        viewModelScope.launch {
            linkExamStudy.linkSubjectAndDeck(exam, subjectId, deckId, newDeckTitle)
        }
    }
    fun addTask(task: Task) { viewModelScope.launch { createTask.execute(task) } }
    fun updateTask(task: Task) { viewModelScope.launch { updateTaskUseCase.execute(task) } }
    fun completeTask(id: String) { viewModelScope.launch { completeTaskUseCase.execute(id) } }
    fun toggleTask(id: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == id } ?: return@launch
            if (task.status == TaskStatus.COMPLETED) {
                uncompleteTaskUseCase.execute(id)
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
    fun addExam(e: Exam) {
        viewModelScope.launch {
            saveExam.execute(e)
            showSnackbar("Exam added")
        }
    }

    fun updateExam(e: Exam) {
        viewModelScope.launch {
            saveExam.execute(e)
            showSnackbar("Exam updated")
        }
    }

    fun deleteExam(id: String) {
        viewModelScope.launch {
            deleteExamUseCase.execute(id)
            _uiState.update { state ->
                state.copy(
                    expandedExamId = if (state.expandedExamId == id) null else state.expandedExamId,
                    snackbarMessage = "Exam deleted",
                )
            }
        }
    }

    fun duplicateExam(exam: Exam) {
        viewModelScope.launch {
            duplicateExamUseCase.execute(exam)
            showSnackbar("Exam duplicated")
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun addSubtask(taskId: String, title: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == taskId } ?: return@launch
            insertSubtaskUseCase.execute(
                Subtask(
                    id = java.util.UUID.randomUUID().toString(),
                    taskId = taskId,
                    title = title,
                    isCompleted = false,
                    sortOrder = task.subtasks.size
                )
            )
        }
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            val task = _uiState.value.tasks.find { it.id == taskId } ?: return@launch
            val subtask = task.subtasks.find { it.id == subtaskId } ?: return@launch
            updateSubtaskUseCase.execute(subtask.copy(isCompleted = !subtask.isCompleted))
        }
    }

    fun deleteSubtask(taskId: String, subtaskId: String) {
        viewModelScope.launch {
            deleteSubtaskUseCase.execute(DeleteSubtaskParams(taskId, subtaskId))
        }
    }
}

enum class NotesFilter { ALL, PINNED, FAVORITES }

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val filter: NotesFilter = NotesFilter.ALL,
    val isLoading: Boolean = true,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val noteRepo: NoteRepository,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private var rawNotes: List<Note> = emptyList()
    private var searchJob: Job? = null

    private fun applyFilterAndSort(notes: List<Note>, filter: NotesFilter): List<Note> {
        val filtered = when (filter) {
            NotesFilter.ALL -> notes
            NotesFilter.PINNED -> notes.filter { it.isPinned }
            NotesFilter.FAVORITES -> notes.filter { it.isFavorite }
        }
        return filtered.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    init {
        observeNotesStream()
    }

    private fun observeNotesStream() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val flow = if (_uiState.value.searchQuery.isBlank()) {
                noteRepo.observeNotes()
            } else {
                noteRepo.search(_uiState.value.searchQuery)
            }
            flow.collect { notes ->
                rawNotes = notes
                _uiState.update { s ->
                    s.copy(notes = applyFilterAndSort(notes, s.filter), isLoading = false)
                }
            }
        }
    }

    fun setFilter(filter: NotesFilter) {
        _uiState.update { s ->
            s.copy(filter = filter, notes = applyFilterAndSort(rawNotes, filter))
        }
    }

    fun saveNote(note: Note) { viewModelScope.launch { saveNoteUseCase.execute(note) } }
    fun deleteNote(id: String) { viewModelScope.launch { deleteNoteUseCase.execute(id) } }
    fun togglePin(note: Note) {
        saveNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
    }
    fun toggleFavorite(note: Note) {
        saveNote(note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis()))
    }
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        observeNotesStream()
    }
}

data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val lastSavedAt: Long? = null,
    val canDelete: Boolean = false,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteRepo: NoteRepository,
    private val saveNoteUseCase: SaveNoteUseCase,
    private val deleteNoteUseCase: DeleteNoteUseCase,
) : ViewModel() {
    private val routeNoteId: String = savedStateHandle.get<String>("noteId") ?: Routes.NEW_NOTE_ID
    private val isNewNote = routeNoteId == Routes.NEW_NOTE_ID
    val noteId: String = if (isNewNote) java.util.UUID.randomUUID().toString() else routeNoteId

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var originalNote: Note? = null
    private var autoSaveJob: Job? = null
    private var hasPersisted = false

    init {
        if (isNewNote) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val note = noteRepo.getNoteById(noteId)
                originalNote = note
                hasPersisted = note != null
                _uiState.update {
                    it.copy(
                        title = note?.title.orEmpty(),
                        content = note?.content.orEmpty(),
                        isPinned = note?.isPinned ?: false,
                        isFavorite = note?.isFavorite ?: false,
                        isLoading = false,
                        canDelete = note != null,
                    )
                }
            }
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, isDirty = true) }
        scheduleAutoSave()
    }

    fun onContentChange(content: String) {
        _uiState.update { it.copy(content = content, isDirty = true) }
        scheduleAutoSave()
    }

    fun togglePin() {
        _uiState.update { it.copy(isPinned = !it.isPinned, isDirty = true) }
        scheduleAutoSave()
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite, isDirty = true) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            saveNow()
        }
    }

    fun saveNow() {
        viewModelScope.launch { performSave() }
    }

    fun flushSave(onComplete: () -> Unit = {}) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            performSave()
            onComplete()
        }
    }

    fun deleteNote(onComplete: () -> Unit) {
        viewModelScope.launch {
            if (hasPersisted) {
                deleteNoteUseCase.execute(noteId)
            }
            onComplete()
        }
    }

    private suspend fun performSave() {
        val state = _uiState.value
        if (!state.isDirty) return
        if (state.title.isBlank() && state.content.isBlank()) {
            _uiState.update { it.copy(isDirty = false) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        val now = System.currentTimeMillis()
        val existing = originalNote
        val displayTitle = state.title.trim().ifBlank {
            state.content.lineSequence().firstOrNull()?.trim()?.take(80) ?: "Untitled"
        }
        val note = Note(
            id = noteId,
            title = displayTitle,
            content = state.content,
            subjectId = existing?.subjectId,
            tags = existing?.tags ?: emptyList(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isPinned = state.isPinned,
            isFavorite = state.isFavorite,
        )
        saveNoteUseCase.execute(note)
        originalNote = note
        hasPersisted = true
        _uiState.update {
            it.copy(
                isSaving = false,
                isDirty = false,
                lastSavedAt = now,
                canDelete = true,
            )
        }
    }

    companion object {
        private const val AUTO_SAVE_DELAY_MS = 1_500L
    }
}

data class ToolsPdfState(
    val fileName: String,
    val extractedText: String? = null,
    val isExtracting: Boolean = false,
    val extractionError: String? = null,
)

data class AiUiState(
    val messages: List<GizmoChatMessage> = emptyList(),
    val gizmo: GizmoCompanionState = GizmoCompanionState(),
    val isOnline: Boolean = true,
    val isLoading: Boolean = false,
    // Live-streamed reasoning text for the in-flight tutor reply (null until the
    // AI chain exposes partial responses; per-message reasoning renders on the
    // completed bubble via JeviReasoningSection).
    val streamingReasoning: String? = null,
    // Pending tutor proposals from the latest reply; rendered as cards and only
    // executed on explicit user acceptance (never auto-applied).
    val studyProposals: List<com.edukasyon.studentai.core.ai.StudyBlockPayload> = emptyList(),
    val followUps: List<String> = emptyList(),
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
    val classesBeingImported: List<com.edukasyon.studentai.core.ai.ExtractedClass> = emptyList(),
    val statusMessage: String? = null,
    val xpEarnedThisSession: Int = 0,
    val activeLocalConversationId: String? = null,
    val activeConversationType: AiConversationType? = null,
    val activeDeckId: String? = null,
    val activeDeckTitle: String? = null,
    val restoredToolInput: String? = null,
    val toolsPdf: ToolsPdfState? = null,
    val selectedChatModel: AiModel = AiModel.AUTO,
    val thinkingLevel: ThinkingLevel = ThinkingLevel.LOW,
    val sources: List<CitedSource> = emptyList(),
    val selectedSourceIds: Set<String>? = null,
    val viewerChunks: List<RankedChunk> = emptyList(),
    val viewerIndex: Int = -1,
    val viewerHighlight: String? = null,
    val stepQuotaRemaining: Int = StepModelQuotaTracker.LIMIT,
    val stepQuotaLabel: String = "${StepModelQuotaTracker.LIMIT}/${StepModelQuotaTracker.LIMIT} left",
    val stepQuotaExhausted: Boolean = false,
    val scheduleScanStatus: ScheduleScanStatus = ScheduleScanStatus.IDLE,
    val scheduleScanRetryCount: Int = 0,
    val scheduleScanRetryAfterMillis: Long? = null,
    val scheduleScanExtractedText: String? = null,
    val strictGroundingMode: Boolean = false,
    val audioOverviewState: AudioOverviewUiState = AudioOverviewUiState.Idle,
)

sealed interface AudioOverviewUiState {
    data object Idle : AudioOverviewUiState
    data class Generating(val progressNote: String? = null) : AudioOverviewUiState
    data class Ready(val file: java.io.File, val playable: Boolean = false, val positionMs: Int = 0, val durationMs: Int = 0) : AudioOverviewUiState
    data class Failed(val message: String) : AudioOverviewUiState
}

enum class ScheduleScanStatus {
    IDLE,
    SCANNING,
    CONFIRMING,
    UNREADABLE,
    RETRY_LATER,
}

enum class AiTool { TUTOR, SUMMARIZE, FLASHCARDS, QUIZ, SCANNER, PDF_EXTRACT }

private data class PendingAiAction(val tool: AiTool, val text: String)

@HiltViewModel
class AiViewModel @Inject constructor(
    @ApplicationContext private val appContext: android.content.Context,
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
    private val studyBlockApplier: com.edukasyon.studentai.core.ai.StudyBlockApplier,
    private val gizmoManager: com.edukasyon.studentai.core.gamification.GizmoGamificationManager,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val aiConversationRepo: AiConversationRepository,
    private val reminderScheduler: ReminderScheduler,
    private val mlKitTextRecognizer: com.edukasyon.studentai.core.mlkit.MlKitTextRecognizer,
    private val scheduleRepository: ScheduleRepository,
    private val sourceRepository: com.edukasyon.studentai.domain.repository.SourceRepository,
    private val aiService: AiService,
    private val jeviRepository: com.edukasyon.studentai.domain.repository.JeviRepository,
    private val audioOverviewManager: com.edukasyon.studentai.core.audio.AudioOverviewManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()
    private var pendingAction: PendingAiAction? = null
    private var lastChatMessage: String? = null
    private var backendConversationId: String? = null
    private var lastScannedImageBytes: ByteArray? = null
    private var lastScannedExtractedText: String? = null
    private var scheduleScanJob: Job? = null
    private var scheduleScanAttemptCounter: Long = 0L
    private var audioPlayer: android.media.MediaPlayer? = null
    private var audioProgressTicker: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                gizmoManager.state.collect { gizmo ->
                    _uiState.update { it.copy(gizmo = gizmo) }
                }
            }
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            combine(preferences.aiModel, preferences.stepModelUsageTimestamps) { model, timestamps ->
                model to timestamps
            }.collect { (model, timestamps) ->
                applyChatModelAndQuota(model, timestamps)
            }
        }
        viewModelScope.launch {
            preferences.thinkingLevel.collect { level ->
                _uiState.update { it.copy(thinkingLevel = level) }
            }
        }
        viewModelScope.launch {
            sourceRepository.observeSources().collect { list ->
                _uiState.update { st ->
                    val valid = st.selectedSourceIds?.intersect(list.map { it.id }.toSet())
                    st.copy(sources = list, selectedSourceIds = valid)
                }
            }
        }
    }

    private fun applyChatModelAndQuota(model: AiModel, timestamps: List<Long>) {
        val status = StepModelQuotaTracker.status(timestamps)
        var resolvedModel = model
        var statusMessage: String? = null
        if (model.isStepModel && status.exhausted) {
            resolvedModel = AiModel.AUTO
            statusMessage = STEP_QUOTA_SWITCH_MESSAGE
            viewModelScope.launch { preferences.setAiModel(AiModel.AUTO) }
        }
        _uiState.update {
            it.copy(
                selectedChatModel = resolvedModel,
                stepQuotaRemaining = status.remaining,
                stepQuotaLabel = com.edukasyon.studentai.ui.components.stepQuotaLabelFromStatus(status),
                stepQuotaExhausted = status.exhausted,
                statusMessage = statusMessage ?: it.statusMessage,
            )
        }
    }

    fun setChatModel(model: AiModel) {
        viewModelScope.launch {
            val timestamps = preferences.stepModelUsageTimestamps.first()
            val status = StepModelQuotaTracker.status(timestamps)
            if (model.isStepModel && status.exhausted) {
                _uiState.update {
                    it.copy(
                        statusMessage = STEP_QUOTA_SWITCH_MESSAGE,
                        selectedChatModel = AiModel.AUTO,
                    )
                }
                preferences.setAiModel(AiModel.AUTO)
                return@launch
            }
            preferences.setAiModel(model)
        }
    }

    fun setThinkingLevel(level: ThinkingLevel) {
        viewModelScope.launch {
            preferences.setThinkingLevel(level)
        }
    }

    fun toggleSource(id: String) {
        val current = _uiState.value.selectedSourceIds
        val all = _uiState.value.sources.map { it.id }.toSet()
        val next = when {
            current == null -> all - id
            current.contains(id) -> (current - id).ifEmpty { emptySet() }
            else -> current + id
        }
        _uiState.update { it.copy(selectedSourceIds = next) }
    }

    fun addSource(name: String, text: String, mime: String = "text/plain") {
        viewModelScope.launch {
            runCatching { sourceRepository.ingestSource(name, mime, text) }
                .onFailure { e -> _uiState.update { it.copy(statusMessage = e.message) } }
        }
    }

    fun deleteSource(id: String) {
        viewModelScope.launch {
            runCatching { sourceRepository.deleteSource(id) }
        }
    }

    suspend fun searchWebSources(query: String): List<WebSearchResult> =
        aiService.searchSources(query)

    fun addWebSource(result: WebSearchResult) {
        val name = result.title.ifBlank { result.url }
        addSource(name, "${result.url}\n\n${result.content}")
    }

    fun openCitation(cite: CitedChunkView) {
        viewModelScope.launch {
            // Web citations: open in browser
            if (cite.id.startsWith("web:") && cite.url.isNotEmpty()) {
                runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cite.url))
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                }
                return@launch
            }

            // Local citations: show passage viewer
            val chunks = runCatching { sourceRepository.chunksForSource(cite.sourceId) }
                .getOrDefault(emptyList())
            val idx = chunks.indexOfFirst { it.chunkId.toString() == cite.id || it.chunkId.toString().removePrefix("local:") == cite.id.removePrefix("local:") }.takeIf { it >= 0 } ?: 0
            val shown = chunks.ifEmpty {
                listOf(RankedChunk(-1, cite.sourceId, cite.label, 0, cite.text, 1.0))
            }
            _uiState.update {
                it.copy(
                    viewerChunks = shown,
                    viewerIndex = idx.coerceIn(shown.indices),
                    viewerHighlight = cite.text.take(200),
                )
            }
        }
    }

    fun stepViewer(dir: Int) {
        val st = _uiState.value
        if (st.viewerChunks.isEmpty()) return
        val next = (st.viewerIndex + dir).coerceIn(st.viewerChunks.indices)
        _uiState.update { it.copy(viewerIndex = next) }
    }

    fun closeViewer() {
        _uiState.update { it.copy(viewerChunks = emptyList(), viewerIndex = -1, viewerHighlight = null) }
    }

    /** Accepts the user-checked study blocks from the proposal card; creates tasks, reports outcome. */
    fun acceptStudyBlocks(blocks: List<com.edukasyon.studentai.core.ai.StudyBlockPayload>) {
        if (blocks.isEmpty()) return
        viewModelScope.launch {
            val result = runCatching { studyBlockApplier.apply(blocks) }
            _uiState.update { s ->
                s.copy(
                    studyProposals = emptyList(),
                    statusMessage = result.getOrNull()?.let { r ->
                        val created = "Added ${r.createdCount} study block${if (r.createdCount == 1) "" else "s"} to your planner"
                        if (r.failures.isEmpty()) created else "$created · ${r.failures.joinToString(" · ")}"
                    } ?: "Could not add the study blocks",
                )
            }
        }
    }

    fun dismissStudyProposals() {
        _uiState.update { it.copy(studyProposals = emptyList()) }
    }

    private suspend fun resolveModelForSend(): AiModel {
        val timestamps = preferences.stepModelUsageTimestamps.first()
        val status = StepModelQuotaTracker.status(timestamps)
        val preferred = preferences.aiModel.first()
        if (preferred.isStepModel && status.exhausted) {
            preferences.setAiModel(AiModel.AUTO)
            _uiState.update {
                it.copy(
                    selectedChatModel = AiModel.AUTO,
                    stepQuotaRemaining = status.remaining,
                    stepQuotaLabel = com.edukasyon.studentai.ui.components.stepQuotaLabelFromStatus(status),
                    stepQuotaExhausted = true,
                    statusMessage = STEP_QUOTA_SWITCH_MESSAGE,
                )
            }
            return AiModel.AUTO
        }
        return preferred
    }

    private suspend fun recordStepModelUseIfNeeded(model: AiModel) {
        if (!model.isStepModel) return
        val updated = StepModelQuotaTracker.recordUse(preferences.stepModelUsageTimestamps.first())
        preferences.setStepModelUsageTimestamps(updated)
        val status = StepModelQuotaTracker.status(updated)
        _uiState.update {
            it.copy(
                stepQuotaRemaining = status.remaining,
                stepQuotaLabel = com.edukasyon.studentai.ui.components.stepQuotaLabelFromStatus(status),
                stepQuotaExhausted = status.exhausted,
            )
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

    fun retryLastMessage() {
        val lastUser = _uiState.value.messages.lastOrNull { it.isUser } ?: return
        clearError()
        _uiState.update { s ->
            val index = s.messages.lastIndexOf(lastUser)
            if (index >= 0) {
                s.copy(messages = s.messages.take(index))
            } else s
        }
        sendMessage(
            message = lastUser.content,
            attachment = null,
            deckId = _uiState.value.activeDeckId,
        )
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
            it.copy(
                messages = emptyList(),
                activeLocalConversationId = null,
                activeConversationType = type,
                activeDeckId = null,
                activeDeckTitle = null,
                streamingReasoning = null,
                studyProposals = emptyList(),
                followUps = emptyList(),
                error = null,
                lastSummary = null,
                viewerChunks = emptyList(),
                viewerIndex = -1,
                viewerHighlight = null,
                statusMessage = null,
                restoredToolInput = null,
                generatedFlashcards = emptyList(),
                generatedQuiz = null,
                quizSession = null,
            )
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch { loadConversationNow(conversationId) }
    }

    private suspend fun loadConversationNow(conversationId: String) {
            val conversation = aiConversationRepo.getConversation(conversationId) ?: return
            val storedMessages = aiConversationRepo.getMessages(conversationId)
            backendConversationId = conversation.backendConversationId

            when (conversation.type) {
                AiConversationType.TUTOR -> {
                    val messages = storedMessages.map { msg ->
                        val toolAction = if (!msg.isUser) {
                            com.edukasyon.studentai.core.ai.AiConversationMetadata
                                .decodeToolAction(msg.metadataJson)
                        } else null
                        GizmoChatMessage(
                            sender = if (msg.isUser) "You" else "Jevi",
                            content = msg.content,
                            isUser = msg.isUser,
                            timestamp = msg.sentAt,
                            attachmentName = msg.attachmentName,
                            attachmentIsImage = msg.attachmentIsImage,
                            reasoning = if (!msg.isUser) {
                                com.edukasyon.studentai.core.ai.AiConversationMetadata
                                    .decodeTutorReasoning(msg.metadataJson)
                            } else {
                                null
                            },
                            citations = if (!msg.isUser) {
                                com.edukasyon.studentai.core.ai.AiConversationMetadata
                                    .decodeCitations(msg.metadataJson)
                                    .map {
                                        com.edukasyon.studentai.domain.model.CitedChunkView(
                                            id = it.id, sourceId = it.sourceId, label = it.label, text = it.text
                                        )
                                    }
                            } else {
                                emptyList()
                            },
                            toolActionType = toolAction?.type,
                            toolActionData = toolAction?.data,
                        )
                    }
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            activeLocalConversationId = conversation.id,
                            activeConversationType = conversation.type,
                            activeDeckId = conversation.deckId,
                            activeDeckTitle = conversation.deckId?.let { deckId ->
                                runCatching {
                                    jeviRepository.observeDeck(deckId).first()?.title
                                }.getOrNull()
                            },
                            error = null,
                            lastSummary = null,
                            generatedFlashcards = emptyList(),
                            generatedQuiz = null,
                            quizSession = null,
                            restoredToolInput = null,
                        )
                    }
                    if (conversation.deckId == null) {
                        lastGeneralTutorId = conversation.id
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

    private var lastGeneralTutorId: String? = null

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

    /** Deck-local tutor: resume this deck's latest thread (or start one), deck-grounded. */
    fun openDeckTutor(deckId: String) {
        viewModelScope.launch {
            val deckTitle = runCatching {
                jeviRepository.observeDeck(deckId).first()?.title
            }.getOrNull()
            val existing = runCatching {
                aiConversationRepo.observeConversations(listOf(AiConversationType.TUTOR)).first()
                    .filter { it.deckId == deckId }.maxByOrNull { it.updatedAt }?.id
            }.getOrNull()
            if (existing != null) {
                loadConversation(existing)
                return@launch
            }
            val created = aiConversationRepo.createConversation(
                type = AiConversationType.TUTOR,
                title = deckTitle ?: "Deck tutor",
                deckId = deckId,
            )
            backendConversationId = null
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    activeLocalConversationId = created.id,
                    activeConversationType = AiConversationType.TUTOR,
                    activeDeckId = deckId,
                    activeDeckTitle = deckTitle,
                    error = null,
                )
            }
        }
    }

    /** Leave deck mode; the shared general tutor takes over unchanged. */
    fun closeDeckTutor() {
        _uiState.update { it.copy(activeDeckId = null, activeDeckTitle = null) }
    }

    /** Deck cards as grounding pseudo-chunks (negative ids never collide with Room rows). */
    private suspend fun deckGroundingChunks(deckId: String, deckTitle: String?): List<RankedChunk> {
        val cards = jeviRepository.observeDeckFlashcards(deckId).first()
            .take(30)
        return cards.mapIndexed { index, card ->
            val qa = "Q: ${card.question}\nA: ${card.answer}".take(400)
            RankedChunk(
                chunkId = -(index + 1).toLong(),
                sourceId = "deck:$deckId",
                sourceName = deckTitle ?: "Deck",
                ordinal = index,
                text = qa,
                score = 1.0,
            )
        }
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
            error.message ?: "Could not reach Jevi. Check your connection and try again."
        else -> error.message ?: "Could not reach Jevi. Check your connection and try again."
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
            AiTool.TUTOR -> lastChatMessage?.let { sendMessage(it, deckId = _uiState.value.activeDeckId) }
            AiTool.SCANNER -> lastScannedImageBytes?.let {
                analyzeScheduleImage(it, extractedText = lastScannedExtractedText, isRetry = true)
            }
            AiTool.PDF_EXTRACT -> Unit
        }
    }

    /** General tutor surface entry: never continue a deck-bound thread here. */
    fun enterGeneralTutor() {
        val state = _uiState.value
        if (state.activeConversationType == AiConversationType.TUTOR && state.activeDeckId != null) {
            val generalId = lastGeneralTutorId
            if (generalId != null) {
                loadConversation(generalId)
            } else {
                startNewConversation(AiConversationType.TUTOR)
            }
        }
    }

    private suspend fun ensureDeckConversation(deckId: String, deckTitle: String?): String {
        val state = _uiState.value
        if (
            state.activeConversationType == AiConversationType.TUTOR &&
            state.activeDeckId == deckId &&
            state.activeLocalConversationId != null
        ) {
            return state.activeLocalConversationId
        }
        val existing = runCatching {
            aiConversationRepo.observeConversations(listOf(AiConversationType.TUTOR)).first()
                .filter { it.deckId == deckId }.maxByOrNull { it.updatedAt }?.id
        }.getOrNull()
        if (existing != null) {
            loadConversationNow(existing)
            return existing
        }
        val created = aiConversationRepo.createConversation(
            type = AiConversationType.TUTOR,
            title = deckTitle ?: "Deck tutor",
            deckId = deckId,
        )
        backendConversationId = null
        _uiState.update {
            it.copy(
                messages = emptyList(),
                activeLocalConversationId = created.id,
                activeConversationType = AiConversationType.TUTOR,
                activeDeckId = deckId,
                activeDeckTitle = deckTitle,
                error = null,
            )
        }
        return created.id
    }

    fun sendMessage(
        message: String,
        subject: String? = null,
        attachment: ChatAttachmentPayload? = null,
        deckId: String? = null,
    ) {
        if (message.isBlank() && attachment == null) return
        val displayMessage = message.ifBlank { "Please help me with this attachment." }
        lastChatMessage = displayMessage
        pendingAction = PendingAiAction(AiTool.TUTOR, displayMessage)
        val userTimestamp = System.currentTimeMillis()
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
                streamingReasoning = null,
                studyProposals = emptyList(),
                followUps = emptyList(),
                error = null,
                messages = it.messages + userMessage,
            )
        }
        viewModelScope.launch {
            try {
                // Deck-local tutor: stay on this deck's thread, ground on deck
                // cards only (no library sources, no web). General chat keeps
                // the shared thread + library grounding unchanged.
                val localId: String
                val groundedSources: List<com.edukasyon.studentai.domain.model.RankedChunk>
                val deckMode: Boolean
                if (deckId != null) {
                    val deckTitle = _uiState.value.activeDeckTitle
                        ?: runCatching {
                            jeviRepository.observeDeck(deckId).first()?.title
                        }.getOrNull()
                    localId = ensureDeckConversation(deckId, deckTitle)
                    groundedSources = try {
                        deckGroundingChunks(deckId, deckTitle)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    deckMode = true
                } else {
                    if (_uiState.value.activeDeckId != null) {
                        // General surface must never continue a deck thread.
                        startNewConversation(AiConversationType.TUTOR)
                    }
                    localId = ensureTutorConversation(displayMessage)
                    val chunkCount = if (_uiState.value.strictGroundingMode) 8 else 5
                    groundedSources = try {
                        sourceRepository.retrieve(displayMessage, _uiState.value.selectedSourceIds, chunkCount)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    deckMode = false
                }
                val historyMessages = com.edukasyon.studentai.core.ai.ChatHistoryBuilder
                    .fromConversationMessages(aiConversationRepo.getMessages(localId))
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
                val attachmentText = when {
                    attachment == null -> null
                    !attachment.isImage -> attachment.textContent
                    else -> {
                        val ocr = mlKitTextRecognizer.recognizeFromBytes(attachment.bytes)
                        ocr.text.takeIf { ocr.hasUsableText } ?: attachment.textContent
                    }
                }
                val selectedModel = resolveModelForSend()
                val modelOverride = AiModelRouter.chatModelOverride(selectedModel)
                val effort = AiModelRouter.effortParam(preferences.thinkingLevel.first())
                if (selectedModel.isStepModel) {
                    recordStepModelUseIfNeeded(selectedModel)
                }
                val effectivePrompt = if (_uiState.value.strictGroundingMode && groundedSources.isNotEmpty()) {
                    "[STRICT SOURCE GROUNDING: Only use facts explicitly verified in the provided sources. Cite statements with bracket numbers [1], [2]. If the answer cannot be found in the sources, clearly declare that.]\n\n$displayMessage"
                } else {
                    displayMessage
                }
                val response = aiChat.execute(
                    com.edukasyon.studentai.core.ai.AiChatRequest(
                        message = effectivePrompt,
                        subject = subject,
                        contextSummary = contextSummary,
                        conversationId = backendConversationId,
                        historyMessages = historyMessages,
                        attachmentName = attachment?.fileName,
                        attachmentMimeType = attachmentMimeOverride ?: attachment?.mimeType,
                        imageBase64 = imageBase64,
                        attachmentText = attachmentText,
                        model = modelOverride,
                        effort = effort,
                        sources = groundedSources,
                        deckMode = deckMode,
                    )
                )
                val reply = response.reply.trim().replace("\\n", "\n").replace("\\r", "\r")
                val reasoning = response.reasoning?.let {
                    com.edukasyon.studentai.core.ai.AiActionParser.sanitizeReply(it)
                }?.takeIf { it.isNotEmpty() }
                if (reply.isEmpty() && reasoning.isNullOrBlank()) {
                    throw com.edukasyon.studentai.core.ai.AiException(
                        "Jevi returned an empty reply."
                    )
                }
                safePersistBackendConversationId(localId, response.conversationId)
                val parsed = com.edukasyon.studentai.core.ai.AiActionParser.parseAndExtract(response.reply)
                val studyBlocks = parsed.studyBlocks
                val followUps = parsed.followUps
                val appliedActions = runCatching {
                    if (parsed.directActions.isNotEmpty()) aiActionExecutor.execute(parsed.directActions) else emptyList()
                }.getOrElse { emptyList() }
                val assistantTimestamp = System.currentTimeMillis()
                val localCites = groundedSources
                    .filter { g -> response.citedChunkIds.contains(g.chunkId.toString()) || response.citedChunkIds.contains("local:${g.chunkId}") }
                    .map {
                        com.edukasyon.studentai.domain.model.CitedChunkView(
                            id = "local:${it.chunkId}", sourceId = it.sourceId, label = it.sourceName, text = it.text
                        )
                    }
                val webCites = response.citedWebResults.mapIndexed { idx, web ->
                    com.edukasyon.studentai.domain.model.CitedChunkView(
                        id = "web:$idx",
                        sourceId = "web",
                        label = web.title.ifBlank { "Web Source" },
                        text = web.snippet,
                        url = web.url,
                    )
                }
                val citedViews = localCites + webCites
                // Auto-add cited web pages to Sources so they persist in the sheet
                // (dedupe by URL/title against already-saved sources).
                val knownNames = _uiState.value.sources.map { it.name }.toSet()
                val freshWeb = webCites
                    .filter { it.url.isNotBlank() }
                    .distinctBy { it.url }
                    .filter { w -> knownNames.none { it == w.label } }
                if (freshWeb.isNotEmpty()) {
                    viewModelScope.launch {
                        val addedIds = freshWeb.mapNotNull { w ->
                            runCatching {
                                sourceRepository.ingestSource(
                                    w.label.ifBlank { w.url },
                                    "text/plain",
                                    "${w.url}\n\n${w.text}",
                                )
                            }.getOrNull()
                        }
                        if (addedIds.isNotEmpty()) {
                            _uiState.update { st ->
                                val base = st.selectedSourceIds
                                    ?: st.sources.map { s -> s.id }.toSet()
                                st.copy(selectedSourceIds = base + addedIds)
                            }
                        }
                    }
                }
                val finalContent = parsed.displayText.ifBlank { reply.ifBlank { "I've processed your request." } }
                val finalToolActionType = response.toolAction?.type ?: parsed.toolActionType
                val finalToolActionData = response.toolAction?.data ?: parsed.toolActionData
                val toolActionMeta = if (!finalToolActionType.isNullOrBlank() && !finalToolActionData.isNullOrBlank()) {
                    com.edukasyon.studentai.core.ai.ToolActionMeta(finalToolActionType, finalToolActionData)
                } else null
                safePersistMessage(
                    AiConversationMessage(
                        id = aiMessageId(),
                        conversationId = localId,
                        isUser = false,
                        content = finalContent,
                        sentAt = assistantTimestamp,
                        metadataJson = com.edukasyon.studentai.core.ai.AiConversationMetadata
                            .encodeTutorReasoning(
                                reasoning,
                                citedViews.map {
                                    com.edukasyon.studentai.core.ai.CitedChunkMeta(it.id, it.sourceId, it.label, it.text)
                                },
                                toolActionMeta,
                            ),
                    )
                )
                awardXp(GizmoConstants.XP_CHAT)
                _uiState.update { s ->
                    s.copy(
                        isLoading = false,
                        loadingTool = null,
                        streamingReasoning = null,
                        error = null,
                        messages = s.messages + GizmoChatMessage(
                            sender = "Jevi",
                            content = finalContent,
                            isUser = false,
                            timestamp = assistantTimestamp,
                            reasoning = reasoning,
                            citations = citedViews,
                            toolActionType = finalToolActionType,
                            toolActionData = finalToolActionData,
                        ),
                        studyProposals = studyBlocks,
                        followUps = followUps,
                        statusMessage = appliedActions.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isLoading = false, loadingTool = null) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gizmo chat failed", e)
                val wasStepModel = _uiState.value.selectedChatModel.isStepModel
                if (wasStepModel && e.message?.contains("wait", ignoreCase = true) == true) {
                    preferences.setAiModel(AiModel.AUTO)
                    _uiState.update {
                        it.copy(
                            selectedChatModel = AiModel.AUTO,
                            statusMessage = STEP_QUOTA_SWITCH_MESSAGE,
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingTool = null,
                        streamingReasoning = null,
                        error = aiErrorMessage(e),
                    )
                }
            }
        }
    }

    fun sendQuickPrompt(prompt: String) = sendMessage(prompt, deckId = _uiState.value.activeDeckId)

    fun attachToolsPdf(uri: android.net.Uri) {
        viewModelScope.launch {
            val reportedMime = appContext.contentResolver.getType(uri)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            if (!com.edukasyon.studentai.core.util.ChatAttachmentUtils.isPdf(reportedMime, name)) {
                _uiState.update { it.copy(error = "Please select a PDF file.") }
                return@launch
            }

            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                _uiState.update { it.copy(error = "Could not read PDF file.") }
                return@launch
            }
            if (bytes.size > com.edukasyon.studentai.core.util.MAX_TOOLS_PDF_BYTES) {
                _uiState.update {
                    it.copy(error = "PDF is too large (max ${com.edukasyon.studentai.core.util.MAX_TOOLS_PDF_BYTES / (1024 * 1024)} MB).")
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    toolsPdf = ToolsPdfState(fileName = name, isExtracting = true),
                    isLoading = true,
                    loadingTool = AiTool.PDF_EXTRACT,
                    error = null,
                )
            }

            try {
                val embedded = com.edukasyon.studentai.core.util.ChatAttachmentUtils.extractEmbeddedPdfText(bytes)
                val extracted = if (embedded != null) {
                    embedded
                } else {
                    extractToolsPdfViaMlKit(uri)
                }.trim()

                if (extracted.isBlank()) {
                    throw IllegalStateException("No text could be extracted from this PDF.")
                }

                _uiState.update {
                    it.copy(
                        toolsPdf = ToolsPdfState(
                            fileName = name,
                            extractedText = extracted.take(110_000),
                        ),
                        isLoading = false,
                        loadingTool = null,
                        statusMessage = "Extracted text from $name",
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update {
                    it.copy(
                        toolsPdf = ToolsPdfState(fileName = name, extractionError = "Cancelled"),
                        isLoading = false,
                        loadingTool = null,
                    )
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF text extraction failed", e)
                _uiState.update {
                    it.copy(
                        toolsPdf = ToolsPdfState(
                            fileName = name,
                            extractionError = aiErrorMessage(e),
                        ),
                        isLoading = false,
                        loadingTool = null,
                        error = aiErrorMessage(e),
                    )
                }
            }
        }
    }

    fun clearToolsPdf() {
        _uiState.update { it.copy(toolsPdf = null) }
    }

    private suspend fun extractToolsPdfViaMlKit(uri: android.net.Uri): String {
        val pages = com.edukasyon.studentai.core.util.ChatAttachmentUtils.renderPdfPagesAsJpeg(
            appContext,
            uri,
            com.edukasyon.studentai.core.util.MAX_PDF_OCR_PAGES,
        )
        if (pages.isEmpty()) {
            throw IllegalStateException("Could not render PDF pages for text extraction.")
        }
        val ocrResult = mlKitTextRecognizer.recognizeFromPageImages(pages)
        if (ocrResult.hasUsableText) {
            return ocrResult.text
        }
        return extractToolsPdfViaVision(uri, "document.pdf")
    }

    private suspend fun extractToolsPdfViaVision(uri: android.net.Uri, fileName: String): String {
        if (!_uiState.value.isOnline) {
            throw com.edukasyon.studentai.core.ai.AiException(
                "This PDF looks scanned. Connect online so Jevi can read it with vision."
            )
        }
        val pages = com.edukasyon.studentai.core.util.ChatAttachmentUtils.renderPdfPagesAsJpeg(
            appContext,
            uri,
            com.edukasyon.studentai.core.util.MAX_PDF_VISION_PAGES,
        )
        if (pages.isEmpty()) {
            throw IllegalStateException("Could not render PDF pages for text extraction.")
        }

        val parts = mutableListOf<String>()
        for ((index, pageBytes) in pages.withIndex()) {
            val response = aiChat.execute(
                com.edukasyon.studentai.core.ai.AiChatRequest(
                    message = buildPdfExtractPrompt(fileName, index + 1, pages.size),
                    attachmentName = "${fileName.substringBeforeLast('.')}-p${index + 1}.jpg",
                    attachmentMimeType = "image/jpeg",
                    imageBase64 = android.util.Base64.encodeToString(pageBytes, android.util.Base64.NO_WRAP),
                )
            )
            val pageText = response.reply.trim()
            if (pageText.isNotBlank()) {
                parts.add(pageText)
            }
        }
        return parts.joinToString("\n\n")
    }

    private fun buildPdfExtractPrompt(fileName: String, page: Int, total: Int): String =
        "Extract ALL readable text from page $page of $total of the PDF document \"$fileName\". " +
            "Return ONLY the extracted text with original paragraph breaks. No commentary, labels, or markdown."

    private fun resolveToolsContent(manualInput: String): String {
        val pdf = _uiState.value.toolsPdf
        val manual = manualInput.trim()
        val pdfText = pdf?.extractedText?.trim().orEmpty()
        return when {
            manual.isNotBlank() && pdfText.isNotBlank() ->
                "$manual\n\n--- From PDF: ${pdf?.fileName} ---\n$pdfText"
            manual.isNotBlank() -> manual
            pdfText.isNotBlank() -> pdfText
            else -> ""
        }
    }

    fun summarize(text: String) {
        val content = resolveToolsContent(text)
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "Paste note content, enter a topic, or upload a PDF.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.SUMMARIZE, content)
        viewModelScope.launch {
            val localId = createToolConversation(AiConversationType.SUMMARIZE, content)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = content,
                    sentAt = userTimestamp,
                )
            )
            _uiState.update {
                it.copy(isLoading = true, loadingTool = AiTool.SUMMARIZE, error = null, lastSummary = null)
            }
            try {
                val result = aiSummarize.execute(content)
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
        val content = resolveToolsContent(text)
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "Paste note content, enter a topic, or upload a PDF.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.FLASHCARDS, content)
        viewModelScope.launch {
            val localId = createToolConversation(AiConversationType.FLASHCARDS, content)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = content,
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
                val cards = aiGenerateFlashcards.execute(content)
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
        val content = resolveToolsContent(text)
        if (content.isBlank()) {
            _uiState.update { it.copy(error = "Paste note content, enter a topic, or upload a PDF.") }
            return
        }
        pendingAction = PendingAiAction(AiTool.QUIZ, content)
        viewModelScope.launch {
            val localId = createToolConversation(AiConversationType.QUIZ, content)
            val userTimestamp = System.currentTimeMillis()
            safePersistMessage(
                AiConversationMessage(
                    id = aiMessageId(),
                    conversationId = localId,
                    isUser = true,
                    content = content,
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
                val quiz = aiGenerateQuiz.execute(content)
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
        val isCorrect = question.isAnswerCorrect(selected)
        viewModelScope.launch {
            var gizmo = _uiState.value.gizmo
            if (isCorrect) {
                awardXp(GizmoConstants.XP_CORRECT_ANSWER)
                gizmo = _uiState.value.gizmo
            }
            val wrongAnswers = if (!isCorrect) {
                session.wrongAnswers + QuizWrongAnswer(question, selected)
            } else session.wrongAnswers
            _uiState.update {
                it.copy(
                    gizmo = gizmo,
                    quizSession = session.copy(
                        revealed = true,
                        correctCount = session.correctCount + if (isCorrect) 1 else 0,
                        wrongAnswers = wrongAnswers,
                    ),
                )
            }
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
                        revealed = false,
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

    fun saveToolDeck(title: String, cardsJson: String) {
        viewModelScope.launch {
            try {
                val parsedCards = mutableListOf<Flashcard>()
                runCatching {
                    val root = kotlinx.serialization.json.Json.parseToJsonElement(cardsJson)
                    val cardsArray = (root as? kotlinx.serialization.json.JsonObject)?.get("cards") as? kotlinx.serialization.json.JsonArray
                    cardsArray?.forEach { el ->
                        val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val front = (obj["front"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: (obj["question"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val back = (obj["back"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: (obj["answer"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val topic = (obj["topic"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        if (front.isNotBlank() && back.isNotBlank()) {
                            parsedCards += Flashcard(
                                id = java.util.UUID.randomUUID().toString(),
                                question = front,
                                answer = back,
                                subjectId = null,
                                topic = topic ?: title,
                                difficulty = "MEDIUM",
                                reviewCount = 0,
                                correctCount = 0,
                                incorrectCount = 0,
                                lastReviewedAt = null,
                                nextReviewAt = null,
                            )
                        }
                    }
                }
                if (parsedCards.isEmpty()) {
                    _uiState.update { it.copy(statusMessage = "Could not parse flashcard deck") }
                    return@launch
                }
                val deckId = java.util.UUID.randomUUID().toString()
                val deck = com.edukasyon.studentai.domain.model.JeviDeck(
                    id = deckId,
                    title = title.ifBlank { "Study Deck" },
                    description = "Created from Jevi AI conversation",
                    subjectId = null,
                    sourceNoteId = null,
                    colorHex = "#2563EB",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    cardCount = parsedCards.size,
                )
                jeviRepository.ensureDefaultDeck()
                jeviRepository.createDeck(deck)
                saveFlashcards.execute(parsedCards)
                jeviRepository.saveFlashcardsToDeck(deckId, parsedCards)
                awardXp(GizmoConstants.XP_SAVE_FLASHCARDS)
                _uiState.update {
                    it.copy(
                        statusMessage = "Deck '${deck.title}' saved with ${parsedCards.size} cards! (+${GizmoConstants.XP_SAVE_FLASHCARDS} XP)"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save tool deck", e)
                _uiState.update { it.copy(error = "Failed to save deck: ${e.message}") }
            }
        }
    }

    fun scheduleToolTask(title: String, dueDate: String) {
        viewModelScope.launch {
            try {
                val payload = com.edukasyon.studentai.core.ai.AiActionPayload(
                    type = "add_task",
                    title = title,
                    description = if (dueDate.isNotBlank()) "Due: $dueDate · Created by Jevi AI Tutor" else "Created by Jevi AI Tutor",
                    priority = "MEDIUM",
                )
                aiActionExecutor.execute(listOf(payload))
                awardXp(GizmoConstants.XP_CHAT)
                _uiState.update {
                    it.copy(statusMessage = "Task '$title' added to your study schedule!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule tool task", e)
                _uiState.update { it.copy(error = "Failed to add task: ${e.message}") }
            }
        }
    }

    fun launchToolQuiz(title: String, questionsJson: String) {
        viewModelScope.launch {
            try {
                val questions = mutableListOf<com.edukasyon.studentai.domain.model.QuizQuestion>()
                val quizId = java.util.UUID.randomUUID().toString()
                runCatching {
                    val root = kotlinx.serialization.json.Json.parseToJsonElement(questionsJson)
                    val array = (root as? kotlinx.serialization.json.JsonObject)?.get("questions") as? kotlinx.serialization.json.JsonArray
                    array?.forEachIndexed { idx, el ->
                        val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
                        val qText = (obj["question"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val correct = (obj["correctAnswer"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                        val optsArray = obj["options"] as? kotlinx.serialization.json.JsonArray
                        val options = optsArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
                        if (qText.isNotBlank()) {
                            questions += com.edukasyon.studentai.domain.model.QuizQuestion(
                                id = java.util.UUID.randomUUID().toString(),
                                quizId = quizId,
                                type = com.edukasyon.studentai.domain.model.QuestionType.MULTIPLE_CHOICE,
                                question = qText,
                                options = if (options.isNotEmpty()) options else listOf("True", "False"),
                                correctAnswer = correct.ifBlank { options.firstOrNull().orEmpty() },
                            )
                        }
                    }
                }
                if (questions.isEmpty()) {
                    _uiState.update { it.copy(statusMessage = "Could not load quiz questions") }
                    return@launch
                }
                val quiz = com.edukasyon.studentai.domain.model.Quiz(
                    id = quizId,
                    title = title.ifBlank { "Practice Quiz" },
                    subjectId = null,
                    sourceNoteId = null,
                    createdAt = System.currentTimeMillis(),
                    questions = questions,
                )
                saveQuiz.execute(quiz)
                awardXp(GizmoConstants.XP_GENERATE_QUIZ)
                _uiState.update {
                    it.copy(
                        generatedQuiz = quiz,
                        quizSession = QuizSessionState(quiz = quiz),
                        statusMessage = "Quiz '${quiz.title}' saved and ready in Quizzes!"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch tool quiz", e)
                _uiState.update { it.copy(error = "Failed to launch quiz: ${e.message}") }
            }
        }
    }

    fun analyzeScheduleImage(
        imageData: ByteArray,
        extractedText: String? = null,
        isRetry: Boolean = false,
    ) {
        val current = _uiState.value
        current.scheduleScanRetryAfterMillis?.let { retryAfter ->
            if (System.currentTimeMillis() < retryAfter) return
            _uiState.update {
                it.copy(
                    scheduleScanRetryCount = 0,
                    scheduleScanRetryAfterMillis = null,
                    scheduleScanStatus = ScheduleScanStatus.IDLE,
                )
            }
            reminderScheduler.cancelReminder(SCHEDULE_SCAN_RETRY_WORK)
        }

        // OPTIMIZATION: Compress image before upload (80% quality) to reduce network time
        val compressedImageData = compressScheduleImage(imageData)
        lastScannedImageBytes = compressedImageData.copyOf()
        pendingAction = PendingAiAction(AiTool.SCANNER, "")
        val attemptId = ++scheduleScanAttemptCounter
        scheduleScanJob?.cancel()
        scheduleScanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingTool = AiTool.SCANNER,
                    error = null,
                    scheduleScanStatus = ScheduleScanStatus.SCANNING,
                    scheduleScanRetryCount = if (isRetry) it.scheduleScanRetryCount else 0,
                    scheduleScanExtractedText = null,
                )
            }
            try {
                // OCR text is a useful hint on every attempt (it caught days the
                // vision model missed); keep it on retries instead of dropping it.
                val ocrText: String? = extractedText?.trim()?.takeIf { it.isNotEmpty() }
                    ?: run {
                        // Run ML Kit on-device OCR (free, no AI needed).
                        // Cap at 1.5s to keep the local fast-path snappy.
                        val recognized = withTimeoutOrNull(SCHEDULE_SCAN_OCR_DEADLINE_MS) {
                            mlKitTextRecognizer.recognizeFromBytes(compressedImageData).text
                        }
                        recognized?.trim()?.takeIf { it.isNotEmpty() }
                    }
                lastScannedExtractedText = ocrText
                _uiState.update {
                    it.copy(
                        scheduleScanExtractedText = ocrText,
                        statusMessage = if (isRetry) "Retrying…" else null,
                    )
                }

                if (attemptId != scheduleScanAttemptCounter) return@launch

                val result = withTimeoutOrNull(SCHEDULE_SCAN_TIMEOUT_MS) {
                    aiAnalyzeSchedule.execute(
                        com.edukasyon.studentai.core.ai.ScheduleScanInput(
                            imageData = compressedImageData,
                            extractedText = ocrText,
                        )
                    )
                }
                if (attemptId != scheduleScanAttemptCounter) return@launch

                when {
                    result == null ->
                        // The scan was aborted locally before the backend answered
                        // (only possible on a network stall now that the timeout
                        // covers the backend's worst-case processing time).
                        registerScheduleScanError(
                            attemptId,
                            "The scan didn't finish — check your connection and try again.",
                        )
                    result.classes.isEmpty() ->
                        // The backend answered successfully but found nothing
                        // readable. Guidance, not an error.
                        registerScheduleScanUnreadable(attemptId)
                    else -> _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingTool = null,
                            scheduleScanStatus = ScheduleScanStatus.IDLE,
                            scheduleScanRetryCount = 0,
                            scheduleScanRetryAfterMillis = null,
                            scannedClasses = result.classes,
                            statusMessage = "Found ${result.classes.size} classes — review and import",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attemptId != scheduleScanAttemptCounter) return@launch
                val msg = (e as? com.edukasyon.studentai.core.ai.AiException)?.message
                    ?: e.message
                    ?: "Scan failed"
                registerScheduleScanError(attemptId, msg)
            }
        }
    }

    fun retryScheduleScan() {
        val bytes = lastScannedImageBytes ?: return
        analyzeScheduleImage(bytes, extractedText = lastScannedExtractedText, isRetry = true)
    }

    /**
     * Compresses schedule images (JPEG 80% quality) before upload.
     * Reduces network time and bandwidth while maintaining OCR/vision quality.
     * Target: 50-80% size reduction (e.g., 4MB → 800KB-1.2MB).
     */
    private fun compressScheduleImage(imageData: ByteArray): ByteArray {
        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            if (bitmap == null) imageData else {
                val compressed = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, compressed)
                bitmap.recycle()
                compressed.toByteArray()
            }
        } catch (e: Exception) {
            // If compression fails, use original (e.g., already compressed, not an image)
            imageData
        }
    }

    fun onScheduleScannerOpened() {
        scheduleScanJob?.cancel()
        val now = System.currentTimeMillis()
        val retryAfter = _uiState.value.scheduleScanRetryAfterMillis
        if (retryAfter != null && now < retryAfter) {
            _uiState.update {
                it.copy(
                    scannedClasses = emptyList(),
                    error = null,
                    isLoading = false,
                    loadingTool = null,
                    scheduleScanStatus = ScheduleScanStatus.RETRY_LATER,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    scannedClasses = emptyList(),
                    error = null,
                    isLoading = false,
                    loadingTool = null,
                    scheduleScanStatus = ScheduleScanStatus.IDLE,
                    scheduleScanRetryCount = 0,
                    scheduleScanRetryAfterMillis = null,
                )
            }
        }
    }

    fun dismissScheduleScanFailure() {
        _uiState.update {
            it.copy(
                scheduleScanStatus = ScheduleScanStatus.IDLE,
                isLoading = false,
                loadingTool = null,
            )
        }
    }

    /**
     * Backend answered but couldn't find classes in the image. Guidance panel
     * only — deliberately NOT an error snackbar, and no automatic retries: the
     * user waits for the single awaited attempt, then decides (Retry / enter
     * manually / back to camera).
     */
    private fun registerScheduleScanUnreadable(attemptId: Long) {
        if (attemptId != scheduleScanAttemptCounter) return
        _uiState.update {
            it.copy(
                isLoading = false,
                loadingTool = null,
                scheduleScanStatus = ScheduleScanStatus.UNREADABLE,
                scheduleScanRetryCount = 0,
                scheduleScanRetryAfterMillis = null,
                error = null,
            )
        }
    }

    /**
     * Real failure (network / backend error / local timeout). Surfaces the
     * backend's own message on the error banner and re-enables the capture
     * button so the user can retry immediately.
     */
    private fun registerScheduleScanError(attemptId: Long, reason: String) {
        if (attemptId != scheduleScanAttemptCounter) return
        reminderScheduler.cancelReminder(SCHEDULE_SCAN_RETRY_WORK)
        _uiState.update {
            it.copy(
                isLoading = false,
                loadingTool = null,
                scheduleScanStatus = ScheduleScanStatus.IDLE,
                scheduleScanRetryCount = 0,
                scheduleScanRetryAfterMillis = null,
                error = reason,
            )
        }
    }

    fun clearScannedClasses() {
        if (_uiState.value.scheduleScanRetryAfterMillis != null) {
            reminderScheduler.cancelReminder(SCHEDULE_SCAN_RETRY_WORK)
        }
        _uiState.update {
            it.copy(
                scannedClasses = emptyList(),
                classesBeingImported = emptyList(),
                error = null,
                scheduleScanStatus = ScheduleScanStatus.IDLE,
                scheduleScanRetryCount = 0,
                scheduleScanRetryAfterMillis = null,
            )
        }
    }

    /** Called by the ScheduleScannerScreen after the timetable-populate animation finishes. */
    fun dismissPopulateAnimation() {
        _uiState.update {
            it.copy(
                classesBeingImported = emptyList(),
                scheduleScanStatus = ScheduleScanStatus.IDLE,
            )
        }
    }

    fun confirmScannedClasses() {
        val classes = _uiState.value.scannedClasses
        if (classes.isEmpty()) return
        // Emit CONFIRMING first so the UI can show the timetable-populate animation
        // while classes are being persisted in the background.
        _uiState.update {
            it.copy(
                scheduleScanStatus = ScheduleScanStatus.CONFIRMING,
                classesBeingImported = classes,
            )
        }
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
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to import classes",
                        scheduleScanStatus = ScheduleScanStatus.IDLE,
                    )
                }
            }
        }
    }

    /**
     * Import classes that arrived from a redeemed share code. Exact duplicates
     * (same subject, day, start and end time already in the schedule) are
     * dropped first; the remainder replays the same populate-animation path as
     * a camera scan (CONFIRMING + classesBeingImported). Deliberately awards no
     * XP — shared content is a copy, not fresh work.
     * Returns false when everything was already present.
     */
    fun importSharedClasses(classes: List<com.edukasyon.studentai.core.ai.ExtractedClass>): Boolean {
        if (classes.isEmpty()) return false
        scheduleScanJob?.cancel()
        scheduleScanJob = viewModelScope.launch {
            val existingKeys: MutableSet<Triple<String, String, String>> = runCatching {
                scheduleRepository.observeSchedule().first().mapTo(mutableSetOf()) { item ->
                    Triple(
                        item.subjectName.trim().lowercase(),
                        item.dayOfWeek.name,
                        normalizeShareTimePair(item.startTime, item.endTime),
                    )
                }
            }.getOrElse { mutableSetOf() }
            val newClasses = classes.filter { cls ->
                val key = Triple(
                    cls.subject.trim().lowercase(),
                    com.edukasyon.studentai.domain.model.DayOfWeek.fromString(cls.day)?.name ?: cls.day,
                    normalizeShareTimePair(cls.startTime, cls.endTime),
                )
                // Set.add returns true only for genuinely-new elements, so
                // intra-batch duplicates are dropped too.
                existingKeys.add(key)
            }
            if (newClasses.isEmpty()) {
                _uiState.update {
                    it.copy(statusMessage = "Already in your schedule")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    scheduleScanStatus = ScheduleScanStatus.CONFIRMING,
                    scannedClasses = newClasses,
                    classesBeingImported = newClasses,
                )
            }
            try {
                newClasses.forEach { cls ->
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
                _uiState.update { it.copy(scannedClasses = emptyList()) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to import classes",
                        scheduleScanStatus = ScheduleScanStatus.IDLE,
                        classesBeingImported = emptyList(),
                    )
                }
            }
        }
        return true
    }

    private fun normalizeShareTimePair(start: String, end: String): String {
        fun norm(raw: String): String {
            val parts = raw.trim().split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()
            return if (hour != null && minute != null) {
                "%02d:%02d".format(hour, minute)
            } else {
                raw.trim()
            }
        }
        return "${norm(start)}-${norm(end)}"
    }

    fun toggleStrictGrounding() {
        _uiState.update { it.copy(strictGroundingMode = !it.strictGroundingMode) }
    }

    suspend fun getActiveSourcesText(): String {
        val st = _uiState.value
        val activeSources = if (st.selectedSourceIds != null) {
            st.sources.filter { st.selectedSourceIds.contains(it.id) }
        } else {
            st.sources
        }
        if (activeSources.isEmpty()) return ""
        val chunks = activeSources.flatMap { s ->
            runCatching { sourceRepository.chunksForSource(s.id) }.getOrDefault(emptyList())
        }
        return chunks.joinToString("\n\n") { "From ${it.sourceName}:\n${it.text}" }
    }

    fun generateStudyGuideFromSources() {
        viewModelScope.launch {
            val text = getActiveSourcesText()
            if (text.isBlank() && _uiState.value.activeDeckId == null) {
                _uiState.update { it.copy(statusMessage = "Add at least one source (notes, PDF, web) to generate a Study Guide.") }
                return@launch
            }
            val prompt = "Create a comprehensive, beautifully structured Study Guide from my active sources. Include:\n" +
                "1. 📌 Core Topics & Key Concepts\n" +
                "2. 📖 Essential Summaries & Glossary Definitions\n" +
                "3. 🧠 Key Takeaways & Mind Map Outline\n" +
                "4. ❓ Review & Self-Check Questions with detailed answers.\n" +
                "Ground all facts in the active sources and cite them throughout using bracketed numbers [1], [2]."
            sendMessage(prompt, deckId = _uiState.value.activeDeckId)
        }
    }

    fun generateBriefingDocFromSources() {
        viewModelScope.launch {
            val text = getActiveSourcesText()
            if (text.isBlank() && _uiState.value.activeDeckId == null) {
                _uiState.update { it.copy(statusMessage = "Add at least one source to generate a Briefing Document.") }
                return@launch
            }
            val prompt = "Generate an executive Briefing Document and FAQ from my active sources. Structure it into:\n" +
                "1. 🎯 Executive Summary\n" +
                "2. ⚡ Important Facts & Crucial Details\n" +
                "3. ❓ Frequently Asked Questions (FAQ) with in-depth answers.\n" +
                "Ground every point strictly in the sources and cite them using [1], [2]."
            sendMessage(prompt, deckId = _uiState.value.activeDeckId)
        }
    }

    fun generateFlashcardsFromSources() {
        viewModelScope.launch {
            val text = getActiveSourcesText()
            if (text.isBlank() && _uiState.value.activeDeckId == null) {
                _uiState.update { it.copy(statusMessage = "Add at least one source to create flashcards.") }
                return@launch
            }
            val content = if (text.isNotBlank()) text.take(15000) else "Create study flashcards from current deck"
            generateFlashcards(content)
        }
    }

    fun generateQuizFromSources() {
        viewModelScope.launch {
            val text = getActiveSourcesText()
            if (text.isBlank() && _uiState.value.activeDeckId == null) {
                _uiState.update { it.copy(statusMessage = "Add at least one source to generate a practice quiz.") }
                return@launch
            }
            val content = if (text.isNotBlank()) text.take(15000) else "Create practice quiz from current deck"
            generateQuiz(content)
        }
    }

    fun generateAudioOverviewFromSources() {
        val st = _uiState.value
        if (st.audioOverviewState is AudioOverviewUiState.Generating) return
        viewModelScope.launch {
            val text = getActiveSourcesText()
            if (text.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Add at least one source to generate an Audio Overview episode.") }
                return@launch
            }
            _uiState.update { it.copy(audioOverviewState = AudioOverviewUiState.Generating("Writing podcast script…")) }
            val title = st.sources.firstOrNull()?.name ?: "Sources Deep Dive"
            val result = audioOverviewManager.overviewForSources(
                title = title,
                sourcesText = text,
                onProgress = { stage, done, total ->
                    val note = when (stage) {
                        com.edukasyon.studentai.core.audio.PodcastStatusEvent.Scripting -> "Drafting dialogue script…"
                        com.edukasyon.studentai.core.audio.PodcastStatusEvent.Auditing -> "Auditing source coverage…"
                        com.edukasyon.studentai.core.audio.PodcastStatusEvent.Synthesizing ->
                            if (total > 0) "Voicing episode ($done/$total lines)…" else "Voicing podcast episode…"
                    }
                    _uiState.update { s ->
                        if (s.audioOverviewState is AudioOverviewUiState.Generating) {
                            s.copy(audioOverviewState = AudioOverviewUiState.Generating(note))
                        } else s
                    }
                }
            )
            when (result) {
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.Result.Ready -> {
                    _uiState.update { it.copy(audioOverviewState = AudioOverviewUiState.Ready(file = result.file)) }
                }
                is com.edukasyon.studentai.core.audio.AudioOverviewManager.Result.Failed -> {
                    _uiState.update { it.copy(audioOverviewState = AudioOverviewUiState.Failed(result.message)) }
                }
            }
        }
    }

    fun playOrPauseAudioOverview() {
        val ready = _uiState.value.audioOverviewState as? AudioOverviewUiState.Ready ?: return
        val current = audioPlayer
        if (current != null && ready.playable) {
            current.pause()
            _uiState.update { it.copy(audioOverviewState = ready.copy(playable = false)) }
            return
        }
        if (current != null && current.isPlaying) return
        runCatching {
            val mp = current ?: android.media.MediaPlayer().apply {
                setDataSource(ready.file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _uiState.update { s ->
                        (s.audioOverviewState as? AudioOverviewUiState.Ready)?.let {
                            s.copy(audioOverviewState = it.copy(playable = false, positionMs = 0))
                        } ?: s
                    }
                }
            }
            mp.seekTo(ready.positionMs)
            mp.start()
            audioPlayer = mp
            _uiState.update { s ->
                val r = (s.audioOverviewState as? AudioOverviewUiState.Ready)
                    ?.copy(playable = true, durationMs = mp.duration)
                    ?: AudioOverviewUiState.Ready(file = ready.file, playable = true, durationMs = mp.duration)
                s.copy(audioOverviewState = r)
            }
            startAudioProgressTicker()
        }.onFailure { err ->
            _uiState.update { it.copy(audioOverviewState = AudioOverviewUiState.Failed("Playback error: ${err.message}")) }
        }
    }

    fun seekAudioOverview(fraction: Float) {
        val mp = audioPlayer ?: return
        runCatching {
            val target = (mp.duration * fraction.coerceIn(0f, 1f)).toInt()
            mp.seekTo(target)
            _uiState.update { s ->
                (s.audioOverviewState as? AudioOverviewUiState.Ready)?.let {
                    s.copy(audioOverviewState = it.copy(positionMs = mp.currentPosition))
                } ?: s
            }
        }
    }

    fun dismissAudioOverview() {
        audioProgressTicker?.cancel()
        runCatching {
            audioPlayer?.stop()
            audioPlayer?.release()
        }
        audioPlayer = null
        _uiState.update { it.copy(audioOverviewState = AudioOverviewUiState.Idle) }
    }

    private fun startAudioProgressTicker() {
        audioProgressTicker?.cancel()
        audioProgressTicker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(500)
                val mp = audioPlayer ?: break
                val pos = runCatching { mp.currentPosition }.getOrNull() ?: break
                _uiState.update { s ->
                    (s.audioOverviewState as? AudioOverviewUiState.Ready)?.let {
                        s.copy(audioOverviewState = it.copy(positionMs = pos))
                    } ?: s
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioProgressTicker?.cancel()
        runCatching { audioPlayer?.release() }
        audioPlayer = null
    }

    private companion object {
        const val TAG = "AiViewModel"
        // The vision model upstream takes 45-75s on real schedule photos, and
        // when the first pass finds 0 classes the backend retries through auto
        // routing — worst case ~144s. The gateway allows schedule-analysis 300s
        // 300s covers the backend's worst-case processing time for large images
        // on MiniMax-M3 while failing cleanly before the 10-minute server job timeout.
        const val SCHEDULE_SCAN_TIMEOUT_MS = 300_000L
        // Skip ML Kit OCR if it would delay the request — vision model doesn't need text hint
        const val SCHEDULE_SCAN_OCR_DEADLINE_MS = 1_500L
        const val SCHEDULE_SCAN_RETRY_WORK = "schedule_scan_retry_later"
        const val STEP_QUOTA_SWITCH_MESSAGE =
            "Agnes 2.5 Flash limit reached — switched to Auto. Try again in a few minutes."
    }
}

data class ProfileEditDraft(
    val displayName: String = "",
    val school: String = "",
    val preferredStatus: String = "",
    val bio: String = "",
)

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
    val aiModel: AiModel = AiModel.AUTO,
    val backupMessage: String? = null,
    val showEditSheet: Boolean = false,
    val editDraft: ProfileEditDraft = ProfileEditDraft(),
    val canEditProfile: Boolean = true,
    val daysUntilNextEdit: Int = 0,
    val profileSaveMessage: String? = null,
    val isSavingProfile: Boolean = false,
    val isFirebaseAuthenticated: Boolean = false,
    val isGoogleSignedIn: Boolean = false,
    val firebaseEmail: String? = null,
    val isSigningInWithGoogle: Boolean = false,
    val isSyncing: Boolean = false,
        val lastSyncedAt: Long? = null,
        val syncStatus: SyncState = SyncState.LOCAL_ONLY,
        val isSyncingCalendar: Boolean = false,
        val calendarSyncMessage: String? = null,
        val calendarSyncedAt: Long? = null,
        val showFeedbackDialog: Boolean = false,
        val isSubmittingFeedback: Boolean = false,
        val feedbackMessage: String? = null,
        val feedbackSuccess: Boolean = false,
    )

    @HiltViewModel
    class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val updateProfile: com.edukasyon.studentai.domain.usecase.UpdateProfileUseCase,
    private val saveUser: com.edukasyon.studentai.domain.usecase.SaveUserUseCase,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val connectivity: com.edukasyon.studentai.core.network.ConnectivityMonitor,
    private val dataBackupManager: com.edukasyon.studentai.core.backup.DataBackupManager,
    private val firestoreSyncService: com.edukasyon.studentai.core.firebase.FirestoreSyncService,
    private val firebaseAuthManager: com.edukasyon.studentai.core.firebase.FirebaseAuthManager,
    private val googleSignInHelper: com.edukasyon.studentai.core.firebase.GoogleSignInHelper,
    private val syncMetadataDao: com.edukasyon.studentai.data.local.dao.SyncMetadataDao,
    private val scheduleRepository: ScheduleRepository,
    private val feedbackManager: com.edukasyon.studentai.core.feedback.FeedbackManager,
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
                combine(
                    connectivity.isOnline,
                    preferences.lastSyncedAt,
                    syncMetadataDao.observeByType(com.edukasyon.studentai.core.firebase.FirestoreSyncService.ENTITY_TYPE_ALL),
                ) { online, lastSynced, metadata ->
                    Triple(online, lastSynced, metadata)
                },
                combine(
                    preferences.firebaseAuthEmail,
                    preferences.googleAccountLinked,
                ) { email, linked -> email to linked },
            ) { userInfo, prefs, syncInfo, authPrefs ->
                val (colors, reminders) = prefs
                val (online, lastSynced, metadata) = syncInfo
                val (storedEmail, storedGoogleLinked) = authPrefs
                val user = userInfo.first
                val now = System.currentTimeMillis()
                val canEdit = com.edukasyon.studentai.domain.model.ProfileEditPolicy
                    .canEditProfile(now, user?.lastProfileEditAt)
                val daysRemaining = com.edukasyon.studentai.domain.model.ProfileEditPolicy
                    .daysUntilNextEdit(now, user?.lastProfileEditAt)
                val syncStatus = metadata?.status?.let {
                    runCatching { SyncState.valueOf(it) }.getOrNull()
                } ?: SyncState.LOCAL_ONLY
                val isGoogleSignedIn = firebaseAuthManager.isGoogleSignedIn || storedGoogleLinked
                ProfileUiState(
                    user = user,
                    themeMode = userInfo.second,
                    notificationsEnabled = userInfo.third,
                    primaryColorHex = colors.first,
                    secondaryColorHex = colors.second,
                    classReminders = colors.third,
                    taskReminders = reminders.first,
                    examReminders = reminders.second,
                    aiModel = reminders.third,
                    isOnline = online,
                    canEditProfile = canEdit,
                    daysUntilNextEdit = daysRemaining,
                    showEditSheet = _uiState.value.showEditSheet,
                    editDraft = _uiState.value.editDraft,
                    profileSaveMessage = _uiState.value.profileSaveMessage,
                    isSavingProfile = _uiState.value.isSavingProfile,
                    isFirebaseAuthenticated = isGoogleSignedIn,
                    isGoogleSignedIn = isGoogleSignedIn,
                    firebaseEmail = firebaseAuthManager.userEmail ?: storedEmail ?: user?.email,
                    isSigningInWithGoogle = _uiState.value.isSigningInWithGoogle,
                    isSyncing = _uiState.value.isSyncing,
                    lastSyncedAt = lastSynced ?: metadata?.lastSyncedAt,
                    syncStatus = syncStatus,
                    backupMessage = _uiState.value.backupMessage,
                )
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            firebaseAuthManager.refreshPersistedAuthState()
        }
    }

    fun getGoogleSignInIntent(): android.content.Intent? {
        googleSignInHelper.configurationIssue()?.let { issue ->
            _uiState.update { it.copy(backupMessage = issue) }
            return null
        }
        return googleSignInHelper.getSignInIntent()
    }

    fun handleGoogleSignInResult(data: android.content.Intent?) {
        if (_uiState.value.isSigningInWithGoogle) return
        _uiState.update { it.copy(isSigningInWithGoogle = true) }
        viewModelScope.launch {
            val tokenResult = googleSignInHelper.getIdTokenFromResult(data)
            tokenResult.onFailure { error ->
                val friendly = googleSignInHelper.describeSignInError(error)
                    ?: return@launch // user cancelled — stay silent, just reset
                _uiState.update { it.copy(isSigningInWithGoogle = false, backupMessage = friendly) }
                return@launch
            }
            val idToken = tokenResult.getOrThrow()
            firebaseAuthManager.signInWithGoogle(idToken)
                .onSuccess { outcome -> onGoogleSignInSuccess(outcome) }
                .onFailure { error ->
                    val friendly = googleSignInHelper.describeSignInError(error)
                        ?: "Google Sign-In failed"
                    _uiState.update {
                        it.copy(isSigningInWithGoogle = false, backupMessage = friendly)
                    }
                }
        }
    }

    private suspend fun onGoogleSignInSuccess(outcome: com.edukasyon.studentai.core.firebase.GoogleSignInOutcome) {
        val existing = userRepo.observeUser().first()
        if (existing != null) {
            val updated = existing.copy(
                id = outcome.uid,
                email = outcome.email ?: existing.email,
                displayName = outcome.displayName?.takeIf { it.isNotBlank() } ?: existing.displayName,
                isGuest = false,
            )
            saveUser.execute(updated)
        }
        _uiState.update { it.copy(isSigningInWithGoogle = false) }
        when (val result = firestoreSyncService.syncAll()) {
            is com.edukasyon.studentai.domain.model.SyncResult.Success -> {
                _uiState.update {
                    it.copy(
                        backupMessage = if (outcome.linkedFromAnonymous) {
                            "Google account linked — sync complete"
                        } else if (outcome.mergedExistingAccount) {
                            "Signed in — cloud data merged"
                        } else {
                            "Signed in with Google — sync complete"
                        },
                        lastSyncedAt = result.summary.syncedAt,
                        syncStatus = SyncState.SYNCED,
                    )
                }
            }
            is com.edukasyon.studentai.domain.model.SyncResult.Offline -> {
                _uiState.update {
                    it.copy(backupMessage = "Signed in — sync when back online")
                }
            }
            else -> {
                _uiState.update {
                    it.copy(backupMessage = "Signed in with Google")
                }
            }
        }
    }

    fun signOutGoogle() {
        viewModelScope.launch {
            firebaseAuthManager.signOut()
            _uiState.update {
                it.copy(
                    backupMessage = "Signed out — local data kept, cloud sync paused",
                    isFirebaseAuthenticated = false,
                    isGoogleSignedIn = false,
                    firebaseEmail = null,
                )
            }
        }
    }

    fun syncNow() {
        if (_uiState.value.isSyncing) return
        if (!_uiState.value.isGoogleSignedIn) {
            _uiState.update { it.copy(backupMessage = "Sign in with Google to sync across devices") }
            return
        }
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(backupMessage = "Offline — sync when back online") }
            return
        }
        _uiState.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            when (val result = firestoreSyncService.syncAll()) {
                is com.edukasyon.studentai.domain.model.SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            backupMessage = "Sync successful",
                            lastSyncedAt = result.summary.syncedAt,
                            syncStatus = SyncState.SYNCED,
                        )
                    }
                }
                is com.edukasyon.studentai.domain.model.SyncResult.Offline -> {
                    _uiState.update {
                        it.copy(isSyncing = false, backupMessage = "Offline — sync when back online")
                    }
                }
                is com.edukasyon.studentai.domain.model.SyncResult.NotAuthenticated -> {
                    _uiState.update {
                        it.copy(isSyncing = false, backupMessage = "Sign in to sync across devices")
                    }
                }
                is com.edukasyon.studentai.domain.model.SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            backupMessage = "Sync failed: ${result.message}",
                            syncStatus = SyncState.FAILED,
                        )
                    }
                }
            }
        }
    }

    /**
     * Called from the UI after the user grants calendar permissions.
     * Reads real schedule items from the database and batch-inserts them
     * into the device's Google Calendar via ContentResolver — unlike the legacy
     * intent-loop which dropped all but the first event.
     */
    fun onCalendarPermissionsGranted(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingCalendar = true) }
            val items = scheduleRepository.observeSchedule().first()
            val result = com.edukasyon.studentai.core.sync.syncAllToGoogleCalendar(context, items)
            val message = when (result) {
                is com.edukasyon.studentai.core.sync.CalendarSyncResult.Success ->
                    "Calendar synced: ${result.inserted} added, ${result.updated} updated"
                is com.edukasyon.studentai.core.sync.CalendarSyncResult.PartialFailure ->
                    "Calendar partially synced: ${result.inserted} added, ${result.updated} updated, ${result.failed} failed"
                is com.edukasyon.studentai.core.sync.CalendarSyncResult.MissingPermissions ->
                    "Calendar permission required — please grant and try again"
                is com.edukasyon.studentai.core.sync.CalendarSyncResult.NoCalendarAccount ->
                    "No Google Calendar found on this device"
                is com.edukasyon.studentai.core.sync.CalendarSyncResult.NoScheduleData ->
                    "No schedule items found — add classes first"
            }
            _uiState.update { it.copy(isSyncingCalendar = false, calendarSyncMessage = message, calendarSyncedAt = System.currentTimeMillis()) }
        }
    }

    fun unsyncFromGoogleCalendar(context: android.content.Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingCalendar = true) }
            val result = com.edukasyon.studentai.core.sync.unsyncAllFromGoogleCalendar(context)
            val message = when (result) {
                is com.edukasyon.studentai.core.sync.CalendarUnsyncResult.Success ->
                    "Removed ${result.deleted} schedule events from calendar"
                is com.edukasyon.studentai.core.sync.CalendarUnsyncResult.Failed ->
                    "Unsync failed: ${result.reason ?: "unknown error"}"
                is com.edukasyon.studentai.core.sync.CalendarUnsyncResult.MissingPermissions ->
                    "Calendar permission required — please grant and try again"
                is com.edukasyon.studentai.core.sync.CalendarUnsyncResult.NoCalendarAccount ->
                    "No Google Calendar found on this device"
                is com.edukasyon.studentai.core.sync.CalendarUnsyncResult.NoEventsFound ->
                    "No SchedMate events found in calendar"
            }
            _uiState.update { it.copy(isSyncingCalendar = false, calendarSyncMessage = message, calendarSyncedAt = null) }
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
                .onSuccess { result ->
                    val msg = if (result.skipped > 0) {
                        "Imported ${result.imported} items (${result.skipped} skipped)"
                    } else {
                        "Imported ${result.imported} items"
                    }
                    _uiState.update { it.copy(backupMessage = msg) }
                }
                .onFailure { e -> _uiState.update { it.copy(backupMessage = e.message) } }
        }
    }

    fun clearBackupMessage() { _uiState.update { it.copy(backupMessage = null) } }

    fun openEditSheet() {
        val user = _uiState.value.user ?: return
        _uiState.update {
            it.copy(
                showEditSheet = true,
                profileSaveMessage = null,
                editDraft = ProfileEditDraft(
                    displayName = user.displayName,
                    school = user.school,
                    preferredStatus = user.preferredStatus,
                    bio = user.bio,
                ),
            )
        }
    }

    fun dismissEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, profileSaveMessage = null) }
    }

    fun updateEditDisplayName(name: String) {
        _uiState.update { it.copy(editDraft = it.editDraft.copy(displayName = name)) }
    }

    fun updateEditSchool(school: String) {
        _uiState.update { it.copy(editDraft = it.editDraft.copy(school = school)) }
    }

    fun updateEditPreferredStatus(status: String) {
        _uiState.update { it.copy(editDraft = it.editDraft.copy(preferredStatus = status)) }
    }

    fun updateEditBio(bio: String) {
        val trimmed = bio.take(com.edukasyon.studentai.domain.model.ProfileEditPolicy.BIO_MAX_LENGTH)
        _uiState.update { it.copy(editDraft = it.editDraft.copy(bio = trimmed)) }
    }

    fun saveProfile() {
        if (_uiState.value.isSavingProfile) return
        val draft = _uiState.value.editDraft
        if (draft.displayName.isBlank()) {
            _uiState.update { it.copy(profileSaveMessage = "Display name is required.") }
            return
        }
        // Bug #5 fix: Validate school field
        val trimmedSchool = draft.school.trim()
        if (trimmedSchool.isNotEmpty() && (trimmedSchool.length < 2 || !trimmedSchool.any { it.isLetter() })) {
            _uiState.update { it.copy(profileSaveMessage = "School name must be at least 2 characters and contain at least one letter.") }
            return
        }
        _uiState.update { it.copy(isSavingProfile = true, profileSaveMessage = null) }
        viewModelScope.launch {
            when (
                val result = updateProfile.execute(
                    com.edukasyon.studentai.domain.usecase.UpdateProfileParams(
                        displayName = draft.displayName,
                        school = draft.school,
                        preferredStatus = draft.preferredStatus,
                        bio = draft.bio,
                    )
                )
            ) {
                is com.edukasyon.studentai.domain.usecase.ProfileUpdateResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            showEditSheet = false,
                            backupMessage = "Profile updated",
                        )
                    }
                }
                is com.edukasyon.studentai.domain.usecase.ProfileUpdateResult.RateLimited -> {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profileSaveMessage = "You can update your profile again in ${result.daysRemaining} day(s).",
                        )
                    }
                }
                is com.edukasyon.studentai.domain.usecase.ProfileUpdateResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSavingProfile = false,
                            profileSaveMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun clearProfileSaveMessage() {
        _uiState.update { it.copy(profileSaveMessage = null) }
    }

    fun openFeedbackDialog() {
        _uiState.update { it.copy(showFeedbackDialog = true, feedbackMessage = null, feedbackSuccess = false) }
    }

    fun dismissFeedbackDialog() {
        _uiState.update { it.copy(showFeedbackDialog = false, feedbackMessage = null, feedbackSuccess = false) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun getFeedbackRemainingCooldown(): Long {
        return feedbackManager.getRemainingCooldownSeconds()
    }

    fun submitFeedback(
        category: String,
        title: String,
        description: String,
        contact: String? = null,
        honeypot: String = "",
        onCompleted: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingFeedback = true, feedbackMessage = null) }
            val result = feedbackManager.submitFeedback(
                category = category,
                title = title,
                description = description,
                contact = contact,
                honeypot = honeypot,
            )
            when (result) {
                is com.edukasyon.studentai.core.feedback.FeedbackResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            feedbackMessage = result.message,
                            feedbackSuccess = true,
                            showFeedbackDialog = false,
                        )
                    }
                    onCompleted(true, result.message)
                }
                is com.edukasyon.studentai.core.feedback.FeedbackResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSubmittingFeedback = false,
                            feedbackMessage = result.error,
                            feedbackSuccess = false,
                        )
                    }
                    onCompleted(false, result.error)
                }
            }
        }
    }
}

data class NotificationSettingsUiState(
    val notificationsEnabled: Boolean = true,
    val classReminders: Boolean = true,
    val taskReminders: Boolean = true,
    val examReminders: Boolean = true,
    val classReminderAtTime: Boolean = true,
    val classReminder15MinBefore: Boolean = true,
    val notificationSoundEnabled: Boolean = true,
    val alarmSoundName: String = "System Default",
    val alarmSoundUri: String? = null,
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
                combine(
                    preferences.notificationsEnabled,
                    preferences.classReminders,
                    preferences.taskReminders,
                    preferences.examReminders,
                    preferences.classReminderAtTime,
                ) { a, b, c, d, e -> listOf(a, b, c, d, e) },
                combine(
                    preferences.classReminder15MinBefore,
                    preferences.notificationSoundEnabled,
                ) { a, b -> listOf(a, b) },
                preferences.alarmSoundName,
                preferences.alarmSoundUri,
            ) { flags, extra, soundName, soundUri ->
                NotificationSettingsUiState(
                    notificationsEnabled = flags[0],
                    classReminders = flags[1],
                    taskReminders = flags[2],
                    examReminders = flags[3],
                    classReminderAtTime = flags[4],
                    classReminder15MinBefore = extra[0],
                    notificationSoundEnabled = extra[1],
                    alarmSoundName = soundName,
                    alarmSoundUri = soundUri,
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
    fun setAlarmSoundName(name: String) { viewModelScope.launch { preferences.setAlarmSoundName(name) } }
    fun setAlarmSoundUri(uri: String?) {
        viewModelScope.launch {
            preferences.setAlarmSoundUri(uri)
            // Re-create channels with updated sound URI and name
            runCatching {
                com.edukasyon.studentai.core.notifications.NotificationHelper(appContext)
                    .createChannels(uri, _uiState.value.alarmSoundName)
            }
        }
    }
    fun selectAlarmSound(name: String, uri: String?) {
        viewModelScope.launch {
            preferences.setAlarmSoundName(name)
            preferences.setAlarmSoundUri(uri)
            runCatching {
                com.edukasyon.studentai.core.notifications.NotificationHelper(appContext)
                    .createChannels(uri, name)
            }
        }
    }
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
            // Returning user (reinstall) who signed in with Google: prefill their account
            // name so the wizard feels familiar instead of starting from scratch.
            if (firebaseAuthManager.isGoogleSignedIn) {
                val accountName = firebaseAuthManager.currentUser?.displayName
                if (!accountName.isNullOrBlank()) {
                    _uiState.update { it.copy(displayName = it.displayName.ifBlank { accountName }) }
                }
            }
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

                // Returning user (reinstall): their Google account already has a cloud profile.
                // Restore it instead of overwriting it with onboarding defaults.
                val existingProfile = firestoreSyncService.fetchCloudProfile()

                val user = existingProfile ?: UserProfile(
                    id = firebaseUserId ?: java.util.UUID.randomUUID().toString(),
                    displayName = state.displayName.ifBlank { "Student" },
                    email = firebaseAuthManager.userEmail?.takeIf { firebaseAuthManager.isGoogleSignedIn },
                    school = state.school,
                    gradeLevel = state.gradeLevel,
                    section = state.section,
                    schoolYear = "2025-2026",
                    semester = "1st",
                    isGuest = !firebaseAuthManager.isGoogleSignedIn,
                )
                saveUser.execute(user)
                firestoreSyncService.syncUserProfile(user)

                // Pull the rest of their cloud data down right away (reinstall restore) and
                // push anything captured during onboarding up. No-ops for guests offline.
                firestoreSyncService.syncAll()
            } catch (e: Exception) {
                Log.e("OnboardingViewModel", "Onboarding failed", e)
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}

data class CalendarMonthState(
    val year: Int,
    val month: Int, // 0 = Jan .. 11 = Dec
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val daysInMonth: Int,
    val firstDayOfWeek: Int, // 1 = Sunday .. 7 = Saturday
)

enum class CalendarTab(val label: String) {
    AGENDA("Agenda"),
    UPCOMING_HOLIDAYS("Holidays"),
    LONG_WEEKENDS("Long Weekends")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val taskRepo: TaskRepository,
    private val examRepo: ExamRepository,
    private val assignmentRepo: AssignmentRepository,
    private val saveCalendarEvent: SaveCalendarEventUseCase,
    private val holidayRepo: com.edukasyon.studentai.data.repository.HolidayRepository
) : ViewModel() {

    private val _visibleMonth = MutableStateFlow(
        computeMonthState(
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
            java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        )
    )
    val visibleMonth: StateFlow<CalendarMonthState> = _visibleMonth.asStateFlow()

    private val _selectedDateMillis = MutableStateFlow<Long?>(null)
    val selectedDateMillis: StateFlow<Long?> = _selectedDateMillis.asStateFlow()

    private val _activeTab = MutableStateFlow(CalendarTab.AGENDA)
    val activeTab: StateFlow<CalendarTab> = _activeTab.asStateFlow()

    val events: StateFlow<List<CalendarEvent>> = _visibleMonth
        .flatMapLatest { mState ->
            calendarRepo.observeEvents(mState.startMillis, mState.endMillis)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _holidays = MutableStateFlow<List<com.edukasyon.studentai.domain.model.Holiday>>(emptyList())
    val holidays: StateFlow<List<com.edukasyon.studentai.domain.model.Holiday>> = _holidays.asStateFlow()

    private val _upcomingHolidays = MutableStateFlow<List<com.edukasyon.studentai.domain.model.Holiday>>(emptyList())
    val upcomingHolidays: StateFlow<List<com.edukasyon.studentai.domain.model.Holiday>> = _upcomingHolidays.asStateFlow()

    private val _longWeekends = MutableStateFlow<List<com.edukasyon.studentai.domain.model.LongWeekend>>(emptyList())
    val longWeekends: StateFlow<List<com.edukasyon.studentai.domain.model.LongWeekend>> = _longWeekends.asStateFlow()

    private val _holidaysLoading = MutableStateFlow(false)
    val holidaysLoading: StateFlow<Boolean> = _holidaysLoading.asStateFlow()

    private val _isLiveApiOnline = MutableStateFlow(holidayRepo.isOnline())
    val isLiveApiOnline: StateFlow<Boolean> = _isLiveApiOnline.asStateFlow()

    init {
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

        refreshHolidays()
    }

    fun nextMonth() {
        val current = _visibleMonth.value
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, current.year)
            set(java.util.Calendar.MONTH, current.month)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, 1)
        }
        _visibleMonth.value = computeMonthState(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
        _selectedDateMillis.value = null
        refreshHolidays()
    }

    fun previousMonth() {
        val current = _visibleMonth.value
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, current.year)
            set(java.util.Calendar.MONTH, current.month)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, -1)
        }
        _visibleMonth.value = computeMonthState(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
        _selectedDateMillis.value = null
        refreshHolidays()
    }

    fun goToToday() {
        val today = java.util.Calendar.getInstance()
        _visibleMonth.value = computeMonthState(today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH))
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        _selectedDateMillis.value = todayStart
        refreshHolidays()
    }

    fun selectDate(millis: Long?) {
        _selectedDateMillis.value = if (_selectedDateMillis.value == millis) null else millis
    }

    fun setActiveTab(tab: CalendarTab) {
        _activeTab.value = tab
    }

    fun refreshHolidays() {
        viewModelScope.launch {
            _isLiveApiOnline.value = holidayRepo.isOnline()
            loadHolidaysForVisibleMonth()
            loadUpcomingAndLongWeekends()
        }
    }

    private suspend fun loadHolidaysForVisibleMonth() {
        val mState = _visibleMonth.value
        val hasCache = holidayRepo.hasCachedData()
        if (!hasCache) {
            _holidaysLoading.value = true
        }

        _holidays.value = holidayRepo.getHolidays(mState.startMillis, mState.endMillis)
        if (_holidays.value.isNotEmpty()) {
            _holidaysLoading.value = false
        }

        val refreshed = holidayRepo.refreshYearIfStale(mState.year, force = !hasCache)
        if (refreshed || !hasCache) {
            _holidays.value = holidayRepo.getHolidays(mState.startMillis, mState.endMillis)
        }
        _holidaysLoading.value = false
    }

    private suspend fun loadUpcomingAndLongWeekends() {
        val mState = _visibleMonth.value
        try {
            val upcoming = holidayRepo.getUpcomingHolidays()
            if (upcoming.isNotEmpty()) {
                _upcomingHolidays.value = upcoming
            }
        } catch (_: Exception) {}

        try {
            val weekends = holidayRepo.getLongWeekends(mState.year)
            _longWeekends.value = weekends
        } catch (_: Exception) {}
    }

    companion object {
        fun computeMonthState(year: Int, month: Int): CalendarMonthState {
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val startMillis = cal.timeInMillis
            val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1 = Sunday

            val endCal = cal.clone() as java.util.Calendar
            endCal.add(java.util.Calendar.MONTH, 1)
            val endMillis = endCal.timeInMillis

            val monthNames = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val title = "${monthNames[month.coerceIn(0, 11)]} $year"
            return CalendarMonthState(
                year = year,
                month = month,
                title = title,
                startMillis = startMillis,
                endMillis = endMillis,
                daysInMonth = daysInMonth,
                firstDayOfWeek = firstDayOfWeek
            )
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences,
    private val authManager: com.edukasyon.studentai.core.firebase.FirebaseAuthManager,
    private val googleSignInHelper: com.edukasyon.studentai.core.firebase.GoogleSignInHelper,
    private val saveUser: com.edukasyon.studentai.domain.usecase.SaveUserUseCase,
    private val firestoreSyncService: com.edukasyon.studentai.core.firebase.FirestoreSyncService,
) : ViewModel() {

    private val _welcomeBackMessage = MutableStateFlow<String?>(null)
    val welcomeBackMessage: StateFlow<String?> = _welcomeBackMessage.asStateFlow()

    fun dismissWelcomeBack() {
        _welcomeBackMessage.value = null
    }

    private val onboardingFinishedLocally = MutableStateFlow(false)

    val onboardingComplete: StateFlow<Boolean> = combine(
        preferences.onboardingComplete,
        onboardingFinishedLocally
    ) { stored, finishedLocally ->
        stored || finishedLocally
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val authStrategy: StateFlow<com.edukasyon.studentai.data.preferences.UserPreferences.AuthStrategy> =
        preferences.authStrategy.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.edukasyon.studentai.data.preferences.UserPreferences.AuthStrategy.UNSELECTED,
        )

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

    /** Persist the user's first-launch authentication choice. */
    fun chooseAuthStrategy(strategy: com.edukasyon.studentai.data.preferences.UserPreferences.AuthStrategy) {
        viewModelScope.launch {
            preferences.setAuthStrategy(strategy)
            onboardingFinishedLocally.value = false
        }
    }

    /** Build a Google sign-in intent launcher payload. */
    suspend fun buildGoogleSignInIntent(): Intent? = googleSignInHelper.getSignInIntent()

    /** User-facing reason Google Sign-In cannot start, or null when ready. */
    fun googleSignInIssue(): String? = googleSignInHelper.configurationIssue()

    /** Complete Google sign-in after the system returns the activity result. */
    fun completeGoogleSignIn(
        data: android.content.Intent?,
        onSuccess: (String?) -> Unit = {},
        onFailure: (String) -> Unit = {},
        onCancelled: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val tokenResult = googleSignInHelper.getIdTokenFromResult(data)
            val idToken = tokenResult.getOrNull()
            if (idToken.isNullOrBlank()) {
                val exception = tokenResult.exceptionOrNull()
                if (exception != null) {
                    val friendly = googleSignInHelper.describeSignInError(exception)
                    if (friendly != null) onFailure(friendly) else onCancelled()
                } else {
                    onFailure("Could not read Google sign-in result. Please try again.")
                }
                return@launch
            }
            val outcome = authManager.signInWithGoogle(idToken)
            outcome
                .onSuccess {
                    preferences.setAuthStrategy(
                        com.edukasyon.studentai.data.preferences.UserPreferences.AuthStrategy.GOOGLE,
                    )
                    // Check if a cloud profile already exists for this returning user.
                    // If so, bypass onboarding entirely and sync their data.
                    try {
                        val existingProfile = firestoreSyncService.fetchCloudProfile()
                        if (existingProfile != null) {
                            preferences.setOnboardingComplete(true)
                            saveUser.execute(existingProfile)
                            firestoreSyncService.syncAll()
                            _welcomeBackMessage.value = "Welcome back, ${existingProfile.displayName}! Synced your data."
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to check existing cloud profile on sign-in", e)
                    }
                    onSuccess(it.email)
                }
                .onFailure { e -> onFailure(e.message ?: "Google sign-in failed.") }
        }
    }

    /**
     * Confirm the user picked "Continue as Guest": persist the choice immediately so setup can
     * proceed even offline, then create the anonymous Firebase session in the background.
     * [completeOnboarding] retries session creation, so a transient failure is not fatal.
     */
    fun continueAsGuest(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            preferences.setAuthStrategy(
                com.edukasyon.studentai.data.preferences.UserPreferences.AuthStrategy.GUEST,
            )
            val uid = authManager.ensureAnonymousSession()
            if (uid == null) {
                Log.w(TAG, "Guest anonymous session not created yet — will retry on onboarding completion")
                onError("Couldn't reach the cloud just now — your data stays on this device until you're back online.")
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiConversationHistoryViewModel @Inject constructor(
    private val aiConversationRepo: AiConversationRepository,
    private val jeviRepository: com.edukasyon.studentai.domain.repository.JeviRepository,
) : ViewModel() {
    private val filterTypes = MutableStateFlow<List<AiConversationType>>(AiConversationType.entries)
    private val deckFilter = MutableStateFlow<String?>(null)

    val conversations: StateFlow<List<AiConversation>> = combine(
        filterTypes,
        deckFilter,
    ) { types, deckId ->
        aiConversationRepo.observeConversations(types).map { list ->
            if (deckId == null) list else list.filter { it.deckId == deckId }
        }
    }.flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deckTitles: StateFlow<Map<String, String>> =
        jeviRepository.observeDecks()
            .map { decks -> decks.associate { it.id to it.title } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFilter(scope: String) {
        deckFilter.value = null
        filterTypes.value = when (scope) {
            "tutor" -> listOf(AiConversationType.TUTOR)
            "tools" -> AiConversationType.TOOL_TYPES
            else -> AiConversationType.entries
        }
    }

    fun setDeckFilter(deckId: String?) {
        deckFilter.value = deckId
    }
}

data class FlashcardStudyUiState(
    val studyCards: List<Flashcard> = emptyList(),
    val currentIndex: Int = 0,
    val studyAll: Boolean = false,
    val deck: JeviDeck? = null,
) {
    val currentCard: Flashcard? get() = studyCards.getOrNull(currentIndex)
    val remaining: Int get() = (studyCards.size - currentIndex).coerceAtLeast(0)
}

@HiltViewModel
class FlashcardStudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDueFlashcards: com.edukasyon.studentai.domain.usecase.GetDueFlashcardsUseCase,
    private val getDeckFlashcards: com.edukasyon.studentai.domain.usecase.GetDeckFlashcardsUseCase,
    private val getDeck: com.edukasyon.studentai.domain.usecase.GetJeviDeckUseCase,
    private val updateFlashcard: UpdateFlashcardUseCase,
    private val recordReview: com.edukasyon.studentai.domain.usecase.RecordJeviReviewUseCase,
    private val gizmoManager: com.edukasyon.studentai.core.gamification.GizmoGamificationManager,
) : ViewModel() {
    private val deckId: String? = savedStateHandle.get<String>("deckId")
    private val studyAll: Boolean = savedStateHandle.get<Boolean>("studyAll") ?: false
    private val _uiState = MutableStateFlow(FlashcardStudyUiState(studyAll = studyAll))
    val uiState: StateFlow<FlashcardStudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cardsFlow = if (studyAll && deckId != null) {
                getDeckFlashcards(deckId)
            } else {
                getDueFlashcards(deckId)
            }
            val deckFlow = deckId?.let { getDeck(it) } ?: kotlinx.coroutines.flow.flowOf(null)
            kotlinx.coroutines.flow.combine(cardsFlow, deckFlow) { cards, deck ->
                cards to deck
            }.collect { (cards, deck) ->
                _uiState.update {
                    it.copy(
                        studyCards = cards,
                        currentIndex = 0,
                        deck = deck,
                        studyAll = studyAll,
                    )
                }
            }
        }
    }

    fun rate(card: Flashcard, quality: Int) {
        viewModelScope.launch {
            val intervalBefore = card.intervalDays
            val updated = com.edukasyon.studentai.core.study.Sm2Algorithm.review(card, quality)
            updateFlashcard.execute(updated)
            recordReview(
                com.edukasyon.studentai.domain.model.JeviReviewRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    flashcardId = card.id,
                    deckId = card.deckId,
                    quality = quality,
                    reviewedAt = System.currentTimeMillis(),
                    intervalBefore = intervalBefore,
                    intervalAfter = updated.intervalDays,
                    easeFactorAfter = updated.easeFactor,
                )
            )
            gizmoManager.addXp(com.edukasyon.studentai.domain.model.JeviConstants.xpForRating(quality))
            gizmoManager.recordActivity()
            _uiState.update { state ->
                val nextIndex = state.currentIndex + 1
                if (nextIndex >= state.studyCards.size && state.studyCards.isNotEmpty()) {
                    gizmoManager.addXp(com.edukasyon.studentai.domain.model.JeviConstants.XP_COMPLETE_SESSION)
                }
                state.copy(currentIndex = nextIndex.coerceAtMost(state.studyCards.size))
            }
        }
    }

    fun retryCards(cards: List<Flashcard>) {
        _uiState.update {
            it.copy(
                studyCards = cards,
                currentIndex = 0,
            )
        }
    }
}
