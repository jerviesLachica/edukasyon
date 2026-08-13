package com.edukasyon.studentai.core.backup

import android.content.Context
import android.net.Uri
import com.edukasyon.studentai.data.local.dao.*
import com.edukasyon.studentai.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val schedule: List<BackupScheduleItem> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val exams: List<BackupExam> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    val grades: List<BackupGrade> = emptyList(),
    val flashcards: List<BackupFlashcard> = emptyList()
)

@Serializable data class BackupScheduleItem(val subjectName: String, val dayOfWeek: String, val startTime: String, val endTime: String, val teacher: String?, val room: String?)
@Serializable data class BackupTask(val title: String, val dueDate: Long?, val status: String, val priority: String)
@Serializable data class BackupExam(val title: String, val examDate: Long, val location: String?)
@Serializable data class BackupNote(val title: String, val content: String)
@Serializable data class BackupGrade(val assessment: String, val score: Double, val maxScore: Double, val category: String, val weight: Double)
@Serializable data class BackupFlashcard(val question: String, val answer: String, val topic: String?)

@Singleton
class DataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDao: ScheduleDao,
    private val taskDao: TaskDao,
    private val examDao: ExamDao,
    private val noteDao: NoteDao,
    private val gradeEntryDao: GradeEntryDao,
    private val flashcardDao: FlashcardDao,
    private val scheduleRepo: com.edukasyon.studentai.domain.repository.ScheduleRepository,
    private val taskRepo: com.edukasyon.studentai.domain.repository.TaskRepository,
    private val examRepo: com.edukasyon.studentai.domain.repository.ExamRepository,
    private val noteRepo: com.edukasyon.studentai.domain.repository.NoteRepository,
    private val gradeRepo: com.edukasyon.studentai.domain.repository.GradeRepository,
    private val flashcardRepo: com.edukasyon.studentai.domain.repository.FlashcardRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportJson(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val schedule = scheduleDao.observeAll().first().map {
                BackupScheduleItem(it.subjectName, it.dayOfWeek, it.startTime, it.endTime, it.teacher, it.room)
            }
            val tasks = taskDao.observeAll().first().map {
                BackupTask(it.title, it.dueDate, it.status, it.priority)
            }
            val exams = examDao.observeAll().first().map {
                BackupExam(it.title, it.examDate, it.location)
            }
            val notes = noteDao.observeAll().first().map {
                BackupNote(it.title, it.content)
            }
            val grades = gradeEntryDao.observeAll().first().map {
                BackupGrade(it.assessment, it.score, it.maxScore, it.category, it.weight)
            }
            val flashcards = flashcardDao.observeAll().first().map {
                BackupFlashcard(it.question, it.answer, it.topic)
            }
            val backup = BackupData(
                schedule = schedule,
                tasks = tasks,
                exams = exams,
                notes = notes,
                grades = grades,
                flashcards = flashcards
            )
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.encodeToString(backup).toByteArray())
            } ?: error("Cannot write to selected file")
        }
    }

    suspend fun exportScheduleCsv(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val items = scheduleDao.observeAll().first()
            val csv = buildString {
                appendLine("Subject,Day,Start,End,Teacher,Room")
                items.forEach {
                    appendLine("${csvEscape(it.subjectName)},${it.dayOfWeek},${it.startTime},${it.endTime},${csvEscape(it.teacher ?: "")},${csvEscape(it.room ?: "")}")
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                ?: error("Cannot write CSV")
        }
    }

    suspend fun exportGradesCsv(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = gradeEntryDao.observeAll().first()
            val csv = buildString {
                appendLine("Assessment,Category,Score,MaxScore,Weight,Term")
                entries.forEach {
                    appendLine("${csvEscape(it.assessment)},${csvEscape(it.category)},${it.score},${it.maxScore},${it.weight},${csvEscape(it.term)}")
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                ?: error("Cannot write CSV")
        }
    }

    suspend fun importJson(uri: Uri, replace: Boolean): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: error("Cannot read file")
            val backup = json.decodeFromString<BackupData>(text)
            var count = 0
            val now = System.currentTimeMillis()
            backup.schedule.forEach { item ->
                scheduleRepo.addScheduleItem(
                    ScheduleItem(
                        id = java.util.UUID.randomUUID().toString(),
                        subjectId = null,
                        subjectName = item.subjectName,
                        teacher = item.teacher,
                        room = item.room,
                        building = null,
                        dayOfWeek = DayOfWeek.fromString(item.dayOfWeek) ?: DayOfWeek.MONDAY,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        colorHex = "#1A237E",
                        notes = null,
                        semester = "",
                        schoolYear = ""
                    )
                )
                count++
            }
            backup.tasks.forEach { t ->
                taskRepo.createTask(
                    Task(
                        id = java.util.UUID.randomUUID().toString(),
                        title = t.title,
                        description = null,
                        subjectId = null,
                        priority = Priority.valueOf(t.priority),
                        dueDate = t.dueDate,
                        dueTime = null,
                        status = TaskStatus.valueOf(t.status),
                        category = null,
                        reminderAt = null,
                        createdAt = now,
                        updatedAt = now,
                        completedAt = null
                    )
                )
                count++
            }
            backup.exams.forEach { e ->
                examRepo.saveExam(
                    Exam(
                        id = java.util.UUID.randomUUID().toString(),
                        title = e.title,
                        subjectId = null,
                        examDate = e.examDate,
                        examTime = null,
                        location = e.location,
                        coverage = null,
                        notes = null,
                        reminderAt = null
                    )
                )
                count++
            }
            backup.notes.forEach { n ->
                noteRepo.saveNote(
                    Note(
                        id = java.util.UUID.randomUUID().toString(),
                        title = n.title,
                        content = n.content,
                        subjectId = null,
                        tags = emptyList(),
                        createdAt = now,
                        updatedAt = now,
                        isPinned = false,
                        isFavorite = false
                    )
                )
                count++
            }
            backup.grades.forEach { g ->
                gradeRepo.saveGrade(
                    GradeEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        subjectId = "default",
                        assessment = g.assessment,
                        category = g.category,
                        score = g.score,
                        maxScore = g.maxScore,
                        weight = g.weight,
                        term = "1st"
                    )
                )
                count++
            }
            backup.flashcards.forEach { f ->
                flashcardRepo.saveFlashcards(
                    listOf(
                        Flashcard(
                            id = java.util.UUID.randomUUID().toString(),
                            question = f.question,
                            answer = f.answer,
                            subjectId = null,
                            topic = f.topic,
                            difficulty = "medium",
                            reviewCount = 0,
                            correctCount = 0,
                            incorrectCount = 0,
                            lastReviewedAt = null,
                            nextReviewAt = null,
                            easeFactor = 2.5,
                            intervalDays = 1
                        )
                    )
                )
                count++
            }
            count
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
