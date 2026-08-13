package com.edukasyon.studentai.data.repository

import android.content.Context
import com.edukasyon.studentai.core.util.GradeCalculator
import com.edukasyon.studentai.core.util.TaskSorter
import com.edukasyon.studentai.data.local.dao.*
import com.edukasyon.studentai.data.mapper.*
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.domain.repository.*
import com.edukasyon.studentai.widget.WidgetUpdater
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(private val userDao: UserDao) : UserRepository {
    override fun observeUser(): Flow<UserProfile?> = userDao.observeUser().map { it?.toDomain() }
    override suspend fun saveUser(user: UserProfile) = userDao.insert(user.toEntity())
}

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao,
    @ApplicationContext private val context: Context
) : ScheduleRepository {
    override fun observeSchedule(): Flow<List<ScheduleItem>> =
        scheduleDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeByDay(day: DayOfWeek): Flow<List<ScheduleItem>> =
        scheduleDao.observeByDay(day.name).map { it.map { e -> e.toDomain() } }

    override suspend fun addScheduleItem(item: ScheduleItem) {
        scheduleDao.insert(item.toEntity())
        WidgetUpdater.notifyDataChanged(context)
    }

    override suspend fun updateScheduleItem(item: ScheduleItem) {
        scheduleDao.insert(item.toEntity())
        WidgetUpdater.notifyDataChanged(context)
    }

    override suspend fun deleteScheduleItem(id: String) {
        val now = System.currentTimeMillis()
        scheduleDao.softDelete(id, now, now)
        WidgetUpdater.notifyDataChanged(context)
    }

    override fun search(query: String): Flow<List<ScheduleItem>> =
        scheduleDao.search(query).map { it.map { e -> e.toDomain() } }
}

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val subtaskDao: SubtaskDao,
    private val reminderSyncService: Lazy<com.edukasyon.studentai.core.notifications.ReminderSyncService>,
    @ApplicationContext private val context: Context
) : TaskRepository {
    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observeAll().mapLatest { tasks ->
            tasks.map { entity ->
                val subtasks = subtaskDao.observeByTask(entity.id).first().map { it.toDomain() }
                entity.toDomain(subtasks)
            }
        }

    override fun observeUpcoming(limit: Int): Flow<List<Task>> =
        taskDao.observeUpcoming(limit).map { TaskSorter.sortByPriorityAndDueDate(it.map { t -> t.toDomain() }) }

    override suspend fun createTask(task: Task) {
        taskDao.insert(task.toEntity())
        subtaskDao.deleteByTask(task.id)
        task.subtasks.forEach { subtaskDao.insert(it.toEntity()) }
        reminderSyncService.get().scheduleTaskReminder(task)
        WidgetUpdater.notifyDataChanged(context)
    }

    override suspend fun updateTask(task: Task) = createTask(task)

    override suspend fun completeTask(id: String) {
        val tasks = taskDao.observeAll().first()
        tasks.find { it.id == id }?.let { entity ->
            taskDao.insert(entity.copy(status = TaskStatus.COMPLETED.name, completedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            reminderSyncService.get().cancelTaskReminder(id)
        }
        WidgetUpdater.notifyDataChanged(context)
    }

    override suspend fun deleteTask(id: String) {
        val now = System.currentTimeMillis()
        taskDao.softDelete(id, now, now)
        reminderSyncService.get().cancelTaskReminder(id)
        WidgetUpdater.notifyDataChanged(context)
    }

    override fun search(query: String): Flow<List<Task>> =
        taskDao.search(query).map { it.map { e -> e.toDomain() } }
}

private fun Subtask.toEntity() = com.edukasyon.studentai.data.local.entity.SubtaskEntity(
    id = id, taskId = taskId, title = title, isCompleted = isCompleted, sortOrder = sortOrder
)

@Singleton
class AssignmentRepositoryImpl @Inject constructor(
    private val assignmentDao: AssignmentDao,
    private val reminderSyncService: Lazy<com.edukasyon.studentai.core.notifications.ReminderSyncService>
) : AssignmentRepository {
    override fun observeAssignments(): Flow<List<Assignment>> =
        assignmentDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun saveAssignment(assignment: Assignment) {
        assignmentDao.insert(assignment.toEntity())
        reminderSyncService.get().scheduleAssignmentReminder(assignment)
    }

    override suspend fun deleteAssignment(id: String) {
        val now = System.currentTimeMillis()
        assignmentDao.softDelete(id, now, now)
        reminderSyncService.get().cancelAssignmentReminder(id)
    }
}

@Singleton
class ExamRepositoryImpl @Inject constructor(
    private val examDao: ExamDao,
    private val reminderSyncService: Lazy<com.edukasyon.studentai.core.notifications.ReminderSyncService>
) : ExamRepository {
    override fun observeExams(): Flow<List<Exam>> =
        examDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeUpcoming(limit: Int): Flow<List<Exam>> =
        examDao.observeUpcoming(System.currentTimeMillis(), limit).map { it.map { e -> e.toDomain() } }

    override suspend fun saveExam(exam: Exam) {
        examDao.insert(exam.toEntity())
        reminderSyncService.get().scheduleExamReminder(exam)
    }

    override suspend fun deleteExam(id: String) {
        val now = System.currentTimeMillis()
        examDao.softDelete(id, now, now)
        reminderSyncService.get().cancelExamReminder(id)
    }
}

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao
) : NoteRepository {
    override fun observeNotes(): Flow<List<Note>> =
        noteDao.observeAll().mapLatest { notes ->
            notes.map { note ->
                val tags = noteTagDao.observeByNote(note.id).first().map { it.tag }
                note.toDomain(tags)
            }
        }

    override suspend fun saveNote(note: Note) {
        noteDao.insert(note.toEntity())
        noteTagDao.deleteByNote(note.id)
        note.tags.forEach { noteTagDao.insert(com.edukasyon.studentai.data.local.entity.NoteTagEntity(noteId = note.id, tag = it)) }
    }

    override suspend fun deleteNote(id: String) {
        val now = System.currentTimeMillis()
        noteDao.softDelete(id, now, now)
    }

    override fun search(query: String): Flow<List<Note>> =
        noteDao.search(query).mapLatest { notes ->
            notes.map { note ->
                val tags = noteTagDao.observeByNote(note.id).first().map { it.tag }
                note.toDomain(tags)
            }
        }
}

@Singleton
class GradeRepositoryImpl @Inject constructor(private val gradeEntryDao: GradeEntryDao) : GradeRepository {
    override fun observeGrades(subjectId: String?): Flow<List<GradeEntry>> {
        val source = if (subjectId.isNullOrBlank()) gradeEntryDao.observeAll() else gradeEntryDao.observeBySubject(subjectId)
        return source.map { it.map { e -> e.toDomain() } }
    }

    override suspend fun saveGrade(entry: GradeEntry) = gradeEntryDao.insert(entry.toEntity())
    override suspend fun deleteGrade(id: String) {
        val now = System.currentTimeMillis()
        gradeEntryDao.softDelete(id, now, now)
    }

    override fun calculateWeightedGrade(entries: List<GradeEntry>): Double =
        GradeCalculator.calculateWeightedGrade(entries)
}

@Singleton
class SubjectRepositoryImpl @Inject constructor(private val subjectDao: SubjectDao) : SubjectRepository {
    override fun observeSubjects(): Flow<List<Subject>> =
        subjectDao.observeAll().map { it.map { e -> e.toDomain() } }

    override suspend fun saveSubject(subject: Subject) {
        val now = System.currentTimeMillis()
        subjectDao.insert(
            com.edukasyon.studentai.data.local.entity.SubjectEntity(
                id = subject.id, name = subject.name, code = subject.code, teacher = subject.teacher,
                colorHex = subject.colorHex, semester = subject.semester, schoolYear = subject.schoolYear,
                createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
            )
        )
    }
}

@Singleton
class CalendarRepositoryImpl @Inject constructor(private val calendarEventDao: CalendarEventDao) : CalendarRepository {
    override fun observeEvents(from: Long, to: Long): Flow<List<CalendarEvent>> =
        calendarEventDao.observeInRange(from, to).map { it.map { e -> e.toDomain() } }

    override suspend fun saveEvent(event: CalendarEvent) = calendarEventDao.insert(event.toEntity())
}

@Singleton
class FlashcardRepositoryImpl @Inject constructor(private val flashcardDao: FlashcardDao) : FlashcardRepository {
    override fun observeFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeDueFlashcards(): Flow<List<Flashcard>> =
        flashcardDao.observeDue(System.currentTimeMillis()).map { it.map { e -> e.toDomain() } }

    override suspend fun saveFlashcards(cards: List<Flashcard>) {
        cards.forEach { insertCard(it) }
    }

    override suspend fun updateFlashcard(card: Flashcard) {
        insertCard(card)
    }

    private suspend fun insertCard(card: Flashcard) {
        val now = System.currentTimeMillis()
        flashcardDao.insert(
            com.edukasyon.studentai.data.local.entity.FlashcardEntity(
                id = card.id, question = card.question, answer = card.answer,
                subjectId = card.subjectId, topic = card.topic, difficulty = card.difficulty,
                reviewCount = card.reviewCount, correctCount = card.correctCount,
                incorrectCount = card.incorrectCount, lastReviewedAt = card.lastReviewedAt,
                nextReviewAt = card.nextReviewAt, easeFactor = card.easeFactor,
                intervalDays = card.intervalDays,
                createdAt = now, updatedAt = now,
                deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
            )
        )
    }
}

@Singleton
class QuizRepositoryImpl @Inject constructor(
    private val quizDao: QuizDao,
    private val quizQuestionDao: QuizQuestionDao
) : QuizRepository {
    override suspend fun saveQuiz(quiz: Quiz) {
        quizDao.insert(quiz.toEntity())
        quiz.questions.forEach { quizQuestionDao.insert(it.toEntity()) }
    }
}

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val subjectDao: SubjectDao,
    private val scheduleDao: ScheduleDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val examDao: ExamDao,
    private val flashcardDao: FlashcardDao
) : SearchRepository {
    override fun globalSearch(query: String): Flow<Map<String, List<String>>> {
        if (query.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyMap())
        return combine(
            combine(
                subjectDao.search(query).map { list -> list.map { it.name } },
                scheduleDao.search(query).map { list -> list.map { it.subjectName } },
                taskDao.search(query).map { list -> list.map { it.title } }
            ) { subjects, schedules, tasks -> Triple(subjects, schedules, tasks) },
            combine(
                noteDao.search(query).map { list -> list.map { it.title } },
                examDao.search(query).map { list -> list.map { it.title } },
                flashcardDao.search(query).map { list -> list.map { it.question } }
            ) { notes, exams, flashcards -> Triple(notes, exams, flashcards) }
        ) { first, second ->
            val (subjects, schedules, tasks) = first
            val (notes, exams, flashcards) = second
            buildMap {
                if (subjects.isNotEmpty()) put("Subjects", subjects)
                if (schedules.isNotEmpty()) put("Schedule", schedules)
                if (tasks.isNotEmpty()) put("Tasks", tasks)
                if (notes.isNotEmpty()) put("Notes", notes)
                if (exams.isNotEmpty()) put("Exams", exams)
                if (flashcards.isNotEmpty()) put("Flashcards", flashcards)
            }
        }
    }
}

@Singleton
class LectureFileRepositoryImpl @Inject constructor(
    private val lectureFileDao: LectureFileDao
) : LectureFileRepository {
    override fun observeFiles(): Flow<List<LectureFile>> =
        lectureFileDao.observeAll().map { files -> files.map { it.toDomain() } }

    override fun observeFilesBySubject(subjectId: String): Flow<List<LectureFile>> =
        lectureFileDao.observeBySubject(subjectId).map { files -> files.map { it.toDomain() } }

    override suspend fun saveFile(file: LectureFile) {
        lectureFileDao.insert(file.toEntity())
    }

    override suspend fun deleteFile(id: String) {
        lectureFileDao.deleteById(id)
    }
}
