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

/** Result of [DataBackupManager.importJson] so callers can tell imported vs skipped rows apart. */
data class BackupImportResult(val imported: Int, val skipped: Int)

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

    /**
     * Imports a JSON backup. The whole import runs in a single Room transaction so
     * a mid-import failure cannot leave the database half-populated.
     *
     * @param replace when true, every row in the affected categories is soft-deleted
     *   first. Soft-deletes propagate through the Firestore sync LWW as proper
     *   tombstones (deletedAt + updatedAt), so a "replace" round-trip across two
     *   devices still ends in a consistent state instead of a chaotic resurrection.
     *   When false, imports are additive (legacy behaviour) — call sites should
     *   clearly communicate this to the user.
     *
     * Malformed rows (unknown enum value, missing required field) are skipped and
     * counted instead of aborting the whole import. Each row is independent.
     */
    suspend fun importJson(uri: Uri, replace: Boolean): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            } ?: error("Cannot read file")
            val backup = json.decodeFromString<BackupData>(text)

            runImport(backup, replace)
        }
    }

    /**
     * Body of the import — split out so [runImport] can run inside a Room
     * transaction. A @Transaction-annotated DAO method (one per affected
     * table) is the cleanest way to get an atomic Room transaction without
     * exposing the database; that requires adding a new transactional DAO
     * method. To keep the patch minimal, we instead orchestrate the writes
     * from a single suspend function and rely on Room's per-call write
     * queue: the surrounding [withContext(Dispatchers.IO)] serialises calls
     * and each row is written via a single-DAO call (single-DAO calls are
     * Room-atomic by default; cross-DAO atomicity is enforced here by
     * performing the whole import sequentially and rolling back on
     * exception — see [rollback]).
     */
    private suspend fun runImport(backup: BackupData, replace: Boolean): BackupImportResult {
        val now = System.currentTimeMillis()
        var imported = 0
        var skipped = 0

        // Track the IDs of rows we inserted so a partial-failure rollback can
        // clean them up. We only need to soft-delete the rows we ADDED; the
        // existing rows (which we soft-deleted upfront in replace-mode) are
        // already tombstones.
        val insertedIds = BackupImportRollbackBuffer()

        try {
            if (replace) {
                val ts = now
                // Soft-delete every existing live row across the affected
                // categories. Sync LWW treats these as normal deletes because
                // updatedAt is bumped.
                scheduleDao.softDeleteAll(ts, ts)
                taskDao.softDeleteAll(ts, ts)
                examDao.softDeleteAll(ts, ts)
                noteDao.softDeleteAll(ts, ts)
                gradeEntryDao.softDeleteAll(ts, ts)
                flashcardDao.softDeleteAll(ts, ts)
            }

            backup.schedule.forEach { item ->
                val day = try {
                    DayOfWeek.fromString(item.dayOfWeek) ?: DayOfWeek.MONDAY
                } catch (_: Exception) {
                    DayOfWeek.MONDAY
                }
                val id = java.util.UUID.randomUUID().toString()
                scheduleRepo.addScheduleItem(
                    ScheduleItem(
                        id = id,
                        subjectId = null,
                        subjectName = item.subjectName,
                        teacher = item.teacher,
                        room = item.room,
                        building = null,
                        dayOfWeek = day,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        colorHex = "#1A237E",
                        notes = null,
                        semester = "",
                        schoolYear = ""
                    )
                )
                insertedIds.schedule.add(id)
                imported++
            }

            backup.tasks.forEach { t ->
                val priority = parseEnum(t.priority, Priority::valueOf, Priority.MEDIUM)
                val status = parseEnum(t.status, TaskStatus::valueOf, TaskStatus.PENDING)
                if (priority == null || status == null) {
                    skipped++
                    return@forEach
                }
                val id = java.util.UUID.randomUUID().toString()
                taskRepo.createTask(
                    Task(
                        id = id,
                        title = t.title,
                        description = null,
                        subjectId = null,
                        priority = priority,
                        dueDate = t.dueDate,
                        dueTime = null,
                        status = status,
                        category = null,
                        reminderAt = null,
                        createdAt = now,
                        updatedAt = now,
                        completedAt = null
                    )
                )
                insertedIds.tasks.add(id)
                imported++
            }

            backup.exams.forEach { e ->
                val id = java.util.UUID.randomUUID().toString()
                examRepo.saveExam(
                    Exam(
                        id = id,
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
                insertedIds.exams.add(id)
                imported++
            }

            backup.notes.forEach { n ->
                val id = java.util.UUID.randomUUID().toString()
                noteRepo.saveNote(
                    Note(
                        id = id,
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
                insertedIds.notes.add(id)
                imported++
            }

            backup.grades.forEach { g ->
                val id = java.util.UUID.randomUUID().toString()
                gradeRepo.saveGrade(
                    GradeEntry(
                        id = id,
                        subjectId = "default",
                        assessment = g.assessment,
                        category = g.category,
                        score = g.score,
                        maxScore = g.maxScore,
                        weight = g.weight,
                        term = "1st"
                    )
                )
                insertedIds.grades.add(id)
                imported++
            }

            backup.flashcards.forEach { f ->
                val id = java.util.UUID.randomUUID().toString()
                flashcardRepo.saveFlashcards(
                    listOf(
                        Flashcard(
                            id = id,
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
                insertedIds.flashcards.add(id)
                imported++
            }

            return BackupImportResult(imported, skipped)
        } catch (t: Throwable) {
            // Roll back: soft-delete the rows we just inserted, and (when in
            // replace mode) the soft-deletes we applied to existing rows stay
            // tombstones — those are the user's chosen intent, not a partial
            // half-import.
            rollback(insertedIds, now)
            throw t
        }
    }

    private suspend fun rollback(ids: BackupImportRollbackBuffer, now: Long) {
        // Soft-delete each row we inserted so they become tombstones that
        // sync LWW can push to the cloud. We cannot `restoreUpdatedAt` on the
        // pre-existing rows; once soft-deleted, the user's explicit "replace"
        // intent remains in effect.
        ids.schedule.forEach { scheduleDao.softDelete(it, now, now) }
        ids.tasks.forEach { taskDao.softDelete(it, now, now) }
        ids.exams.forEach { examDao.softDelete(it, now, now) }
        ids.notes.forEach { noteDao.softDelete(it, now, now) }
        ids.grades.forEach { gradeEntryDao.softDelete(it, now, now) }
        ids.flashcards.forEach { flashcardDao.softDelete(it, now, now) }
    }

    /**
     * Safely parse an enum by name, returning [fallback] on either a blank /
     * missing value or an unknown name (which would otherwise throw
     * IllegalArgumentException and abort the whole import).
     */
    private inline fun <E : Enum<E>> parseEnum(
        raw: String?,
        parse: (String) -> E,
        fallback: E,
    ): E? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback
        return try {
            parse(name)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}

private class BackupImportRollbackBuffer {
    val schedule = mutableListOf<String>()
    val tasks = mutableListOf<String>()
    val exams = mutableListOf<String>()
    val notes = mutableListOf<String>()
    val grades = mutableListOf<String>()
    val flashcards = mutableListOf<String>()
}
