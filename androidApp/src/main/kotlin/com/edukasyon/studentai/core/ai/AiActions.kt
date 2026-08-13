package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.network.AiJsonParser
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.*
import com.edukasyon.studentai.domain.usecase.AddScheduleItemUseCase
import com.edukasyon.studentai.domain.usecase.CreateTaskUseCase
import com.edukasyon.studentai.domain.usecase.SaveExamUseCase
import com.edukasyon.studentai.domain.usecase.SaveNoteUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AiActionPayload(
    val type: String,
    val subject: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    val day: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val title: String? = null,
    val description: String? = null,
    val dueDate: Long? = null,
    val dueTime: String? = null,
    val priority: String? = null,
    val examDate: Long? = null,
    val examTime: String? = null,
    val location: String? = null,
    val content: String? = null,
)

@Serializable
data class AiActionsEnvelope(val actions: List<AiActionPayload> = emptyList())

data class ParsedAiReply(
    val displayText: String,
    val actions: List<AiActionPayload>,
)

object AiActionParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(reply: String): ParsedAiReply = try {
        extractActionsFence(reply)?.let { (display, rawActions) ->
            ParsedAiReply(display, decodeActions(rawActions).orEmpty())
        } ?: extractTrailingActions(reply.trim())?.let { (display, actions) ->
            ParsedAiReply(display, actions)
        } ?: ParsedAiReply(reply.trim(), emptyList())
    } catch (_: Exception) {
        ParsedAiReply(reply.trim(), emptyList())
    }

    /** String-based extraction avoids Regex init crashes on some Android/Huawei engines. */
    private fun extractActionsFence(reply: String): Pair<String, String>? {
        val marker = "```actions"
        val start = reply.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val contentStart = reply.indexOf('\n', start).let { if (it < 0) start + marker.length else it + 1 }
        val end = reply.indexOf("```", contentStart)
        if (end < 0) return null
        val display = (reply.substring(0, start) + reply.substring(end + 3)).trim()
        return display to reply.substring(contentStart, end).trim()
    }

    private fun extractTrailingActions(trimmed: String): Pair<String, List<AiActionPayload>>? {
        val actionsKey = "\"actions\""
        val keyIndex = trimmed.lastIndexOf(actionsKey)
        if (keyIndex < 0) return null
        val braceStart = trimmed.lastIndexOf('{', keyIndex)
        if (braceStart < 0) return null
        val jsonPart = trimmed.substring(braceStart)
        val actions = decodeActions(jsonPart) ?: return null
        return trimmed.substring(0, braceStart).trim() to actions
    }

    private fun decodeActions(raw: String): List<AiActionPayload>? = try {
        json.decodeFromString<AiActionsEnvelope>(AiJsonParser.stripMarkdownFences(raw)).actions
    } catch (_: Exception) {
        null
    }
}

@Singleton
class AiActionExecutor @Inject constructor(
    private val addScheduleItem: AddScheduleItemUseCase,
    private val createTask: CreateTaskUseCase,
    private val saveExam: SaveExamUseCase,
    private val saveNote: SaveNoteUseCase,
) {
    suspend fun execute(actions: List<AiActionPayload>): List<String> {
        val results = mutableListOf<String>()
        for (action in actions) {
            runCatching { executeOne(action) }
                .onSuccess { results += it }
                .onFailure { results += "Could not ${action.type}: ${it.message ?: "unknown error"}" }
        }
        return results
    }

    private suspend fun executeOne(action: AiActionPayload): String = when (action.type.lowercase()) {
        "add_schedule", "schedule" -> {
            val subject = action.subject ?: action.title
                ?: throw IllegalArgumentException("subject required")
            addScheduleItem.execute(
                ScheduleItem(
                    id = UUID.randomUUID().toString(),
                    subjectId = null,
                    subjectName = subject,
                    teacher = action.teacher,
                    room = action.room,
                    building = null,
                    dayOfWeek = DayOfWeek.fromString(action.day ?: "MONDAY") ?: DayOfWeek.MONDAY,
                    startTime = action.startTime ?: "08:00",
                    endTime = action.endTime ?: "09:00",
                    colorHex = "#1A237E",
                    notes = action.description,
                    semester = "",
                    schoolYear = "",
                )
            )
            "Added $subject to schedule (${action.day ?: "MONDAY"} ${action.startTime ?: "08:00"})"
        }
        "add_task", "task" -> {
            val title = action.title ?: throw IllegalArgumentException("title required")
            val now = System.currentTimeMillis()
            createTask.execute(
                Task(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = action.description,
                    subjectId = null,
                    priority = parsePriority(action.priority),
                    dueDate = action.dueDate,
                    dueTime = action.dueTime,
                    status = TaskStatus.PENDING,
                    category = null,
                    reminderAt = null,
                    createdAt = now,
                    updatedAt = now,
                    completedAt = null,
                )
            )
            "Added task: $title"
        }
        "add_exam", "exam" -> {
            val title = action.title ?: action.subject
                ?: throw IllegalArgumentException("title required")
            saveExam.execute(
                Exam(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    subjectId = null,
                    examDate = action.examDate ?: (System.currentTimeMillis() + 7 * 86_400_000L),
                    examTime = action.examTime,
                    location = action.location,
                    coverage = action.description,
                    notes = null,
                    reminderAt = null,
                )
            )
            "Added exam: $title"
        }
        "add_note", "note" -> {
            val title = action.title ?: "AI Note"
            val now = System.currentTimeMillis()
            saveNote.execute(
                Note(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    content = action.content ?: action.description ?: "",
                    subjectId = null,
                    tags = listOf("ai"),
                    createdAt = now,
                    updatedAt = now,
                    isPinned = false,
                    isFavorite = false,
                )
            )
            "Added note: $title"
        }
        else -> throw IllegalArgumentException("unknown action type: ${action.type}")
    }

    private fun parsePriority(raw: String?): Priority = when (raw?.uppercase()) {
        "LOW" -> Priority.LOW
        "HIGH" -> Priority.HIGH
        "URGENT" -> Priority.URGENT
        else -> Priority.MEDIUM
    }
}

@Singleton
class AppContextBuilder @Inject constructor(
    private val getTodaySchedule: com.edukasyon.studentai.domain.usecase.GetTodayScheduleUseCase,
    private val getUpcomingTasks: com.edukasyon.studentai.domain.usecase.GetUpcomingTasksUseCase,
    private val getUpcomingExams: com.edukasyon.studentai.domain.usecase.GetUpcomingExamsUseCase,
    private val getAllSubjects: com.edukasyon.studentai.domain.usecase.GetAllSubjectsUseCase,
) {
    suspend fun buildSummary(): String {
        val today = DateUtils.getTodayDayOfWeek()
        val schedule = runCatching { getTodaySchedule.execute(Unit) }.getOrDefault(emptyList())
        val tasks = runCatching { getUpcomingTasks.execute(5) }.getOrDefault(emptyList())
        val exams = runCatching { getUpcomingExams.execute(3) }.getOrDefault(emptyList())
        val subjects = runCatching { getAllSubjects.execute(Unit) }.getOrDefault(emptyList())

        return buildString {
            append("Today is ${today.displayName}. ")
            if (subjects.isNotEmpty()) {
                append("Subjects: ${subjects.joinToString { it.name }}. ")
            }
            if (schedule.isNotEmpty()) {
                append("Today's classes: ")
                append(schedule.joinToString { "${it.subjectName} ${it.startTime}-${it.endTime}" })
                append(". ")
            } else {
                append("No classes scheduled today. ")
            }
            if (tasks.isNotEmpty()) {
                append("Pending tasks: ")
                append(tasks.joinToString { it.title })
                append(". ")
            }
            if (exams.isNotEmpty()) {
                append("Upcoming exams: ")
                append(exams.joinToString { "${it.title} ${DateUtils.formatCountdown(it.examDate)}" })
                append(". ")
            }
        }.trim()
    }
}
