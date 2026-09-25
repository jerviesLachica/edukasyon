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
data class FlashcardItemPayload(
    val front: String? = null,
    val question: String? = null,
    val back: String? = null,
    val answer: String? = null,
    val topic: String? = null,
)

@Serializable
data class QuizQuestionPayload(
    val question: String? = null,
    val options: List<String> = emptyList(),
    val correctAnswer: String? = null,
    val explanation: String? = null,
)

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
    val blocks: List<StudyBlockPayload>? = null,
    val items: List<String>? = null,
    val cards: List<FlashcardItemPayload>? = null,
    val questions: List<QuizQuestionPayload>? = null,
    val dueDateString: String? = null,
)

/** Dated, one-off study block proposed by the tutor; accepted blocks become tasks. */
@Serializable
data class StudyBlockPayload(
    val subject: String,
    val date: String,          // ISO-8601 yyyy-MM-dd
    val startTime: String,    // HH:mm
    val endTime: String,      // HH:mm
    val reason: String? = null,
)

@Serializable
data class AiActionsEnvelope(val actions: List<AiActionPayload> = emptyList())

data class ParsedAiReply(
    val displayText: String,
    val actions: List<AiActionPayload>,
    /** Study block proposals extracted from actions (rendered as accept/dismiss cards). */
    val studyBlocks: List<StudyBlockPayload> = emptyList(),
    /** Follow-up question suggestions (max 3, deduplicated). */
    val followUps: List<String> = emptyList(),
    /** Non-proposal actions that should be auto-executed. */
    val directActions: List<AiActionPayload> = emptyList(),
    /** Interactive tool action card (e.g. save flashcards or start quiz). */
    val toolActionType: String? = null,
    val toolActionData: String? = null,
)

object AiActionParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Normalise escaped newline/carriage-return literals that some backends
     * return as literal `\n` / `\r` text instead of control characters.
     */
    fun sanitizeReply(raw: String): String =
        raw.trim().replace("\\n", "\n").replace("\\r", "\r")

    /**
     * Parse a raw AI reply string and extract the display text plus all
     * structured actions, pre-sorted into proposals (study blocks, follow-ups)
     * and direct (auto-apply) actions. This is the single entry-point that
     * replaces the old inline filter chains in the ViewModel.
     */
    fun parseAndExtract(rawReply: String): ParsedAiReply {
        val sanitized = sanitizeReply(rawReply)
        val base = parse(sanitized)

        val proposals = base.actions.filter {
            it.type.lowercase() in AiActionExecutor.PROPOSAL_TYPES
        }
        val studyBlocks = proposals
            .filter { it.type.lowercase() == "propose_study_blocks" }
            .flatMap { it.blocks.orEmpty() }
        val followUps = proposals
            .filter { it.type.lowercase() == "suggest_followups" }
            .flatMap { it.items.orEmpty() }
            .distinct()
            .take(3)
        val directActions = base.actions.filterNot {
            it.type.lowercase() in AiActionExecutor.PROPOSAL_TYPES
        }

        var extractedToolType: String? = null
        var extractedToolData: String? = null

        val flashcardAction = base.actions.firstOrNull {
            it.type.lowercase() in setOf("create_flashcard_deck", "create_deck", "flashcards")
        }
        if (flashcardAction != null && !flashcardAction.cards.isNullOrEmpty()) {
            extractedToolType = "create_flashcard_deck"
            val title = flashcardAction.title?.ifBlank { "Study Deck" } ?: "Study Deck"
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("title", kotlinx.serialization.json.JsonPrimitive(title))
                put("cards", kotlinx.serialization.json.buildJsonArray {
                    flashcardAction.cards.forEach { c ->
                        val front = c.front ?: c.question ?: ""
                        val back = c.back ?: c.answer ?: ""
                        if (front.isNotBlank() && back.isNotBlank()) {
                            add(kotlinx.serialization.json.buildJsonObject {
                                put("front", kotlinx.serialization.json.JsonPrimitive(front))
                                put("back", kotlinx.serialization.json.JsonPrimitive(back))
                                put("topic", kotlinx.serialization.json.JsonPrimitive(c.topic ?: title))
                            })
                        }
                    }
                })
            }
            extractedToolData = obj.toString()
        }

        val quizAction = base.actions.firstOrNull {
            it.type.lowercase() in setOf("launch_practice_quiz", "practice_quiz", "quiz")
        }
        if (quizAction != null && !quizAction.questions.isNullOrEmpty() && extractedToolType == null) {
            extractedToolType = "launch_practice_quiz"
            val title = quizAction.title?.ifBlank { "Practice Quiz" } ?: "Practice Quiz"
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("title", kotlinx.serialization.json.JsonPrimitive(title))
                put("questions", kotlinx.serialization.json.buildJsonArray {
                    quizAction.questions.forEach { q ->
                        val questionText = q.question.orEmpty()
                        if (questionText.isNotBlank()) {
                            add(kotlinx.serialization.json.buildJsonObject {
                                put("question", kotlinx.serialization.json.JsonPrimitive(questionText))
                                put("correctAnswer", kotlinx.serialization.json.JsonPrimitive(q.correctAnswer.orEmpty()))
                                put("options", kotlinx.serialization.json.buildJsonArray {
                                    q.options.forEach { opt ->
                                        add(kotlinx.serialization.json.JsonPrimitive(opt))
                                    }
                                })
                                q.explanation?.let { exp ->
                                    put("explanation", kotlinx.serialization.json.JsonPrimitive(exp))
                                }
                            })
                        }
                    }
                })
            }
            extractedToolData = obj.toString()
        }

        val taskAction = base.actions.firstOrNull {
            it.type.lowercase() == "create_study_task"
        }
        if (taskAction != null && extractedToolType == null) {
            extractedToolType = "create_study_task"
            val title = taskAction.title ?: "Study Task"
            val due = taskAction.dueDateString ?: taskAction.dueTime ?: ""
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("title", kotlinx.serialization.json.JsonPrimitive(title))
                put("dueDate", kotlinx.serialization.json.JsonPrimitive(due))
            }
            extractedToolData = obj.toString()
        }

        return base.copy(
            studyBlocks = studyBlocks,
            followUps = followUps,
            directActions = directActions,
            toolActionType = extractedToolType,
            toolActionData = extractedToolData,
        )
    }

    fun parse(reply: String): ParsedAiReply = try {
        extractActionsFence(reply)?.let { (display, rawActions) ->
            val finalDisplay = display.ifBlank { "I've processed your request and organized the study items." }
            ParsedAiReply(finalDisplay, decodeActions(rawActions).orEmpty())
        } ?: extractTrailingActions(reply.trim())?.let { (display, actions) ->
            val finalDisplay = display.ifBlank { "I've processed your request and organized the study items." }
            ParsedAiReply(finalDisplay, actions)
        } ?: ParsedAiReply(reply.trim().ifBlank { "Here is what I found for you." }, emptyList())
    } catch (_: Exception) {
        ParsedAiReply(reply.trim().ifBlank { "Here is what I found for you." }, emptyList())
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
            // Proposal-type actions are rendered as cards and executed on user
            // acceptance elsewhere; they are never auto-applied.
            if (action.type.lowercase() in PROPOSAL_TYPES) continue
            runCatching { executeOne(action) }
                .onSuccess { results += it }
                .onFailure { results += "Could not ${action.type}: ${it.message ?: "unknown error"}" }
        }
        return results
    }

    companion object {
        val PROPOSAL_TYPES = setOf(
            "propose_study_blocks",
            "suggest_followups",
            "create_flashcard_deck",
            "create_deck",
            "flashcards",
            "launch_practice_quiz",
            "practice_quiz",
            "quiz",
            "create_study_task",
        )
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
    private val getUser: com.edukasyon.studentai.domain.usecase.GetUserUseCase,
) {
    suspend fun buildSummary(): String {
        val today = DateUtils.getTodayDayOfWeek()
        val schedule = runCatching { getTodaySchedule.execute(Unit) }.getOrDefault(emptyList())
        val tasks = runCatching { getUpcomingTasks.execute(5) }.getOrDefault(emptyList())
        val exams = runCatching { getUpcomingExams.execute(3) }.getOrDefault(emptyList())
        val subjects = runCatching { getAllSubjects.execute(Unit) }.getOrDefault(emptyList())
        val user = runCatching { getUser.execute(Unit) }.getOrNull()

        return buildString {
            user?.let { profile ->
                append("Student profile: ")
                append("Name: ${profile.displayName}. ")
                if (profile.school.isNotBlank()) append("School: ${profile.school}. ")
                if (profile.preferredStatus.isNotBlank()) append("Status: ${profile.preferredStatus}. ")
                if (profile.bio.isNotBlank()) append("Bio: ${profile.bio}. ")
            }
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
