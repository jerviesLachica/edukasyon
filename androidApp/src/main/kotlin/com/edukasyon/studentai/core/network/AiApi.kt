package com.edukasyon.studentai.core.network

import com.edukasyon.studentai.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AiApiService {
    @GET("health")
    suspend fun health(): HealthResponseDto

    @POST("api/ai/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponseDto

    @POST("api/ai/schedule-analysis")
    suspend fun analyzeSchedule(@Body request: ScheduleAnalysisRequest): ScheduleAnalysisResponseDto

    @POST("api/ai/summarize")
    suspend fun summarize(@Body request: TextRequest): TextResponseDto

    @POST("api/ai/flashcards")
    suspend fun generateFlashcards(@Body request: TextRequest): FlashcardsResponseDto

    @POST("api/ai/quiz")
    suspend fun generateQuiz(@Body request: TextRequest): QuizResponseDto

    @POST("api/ai/study-plan")
    suspend fun generateStudyPlan(@Body request: StudyPlanRequest): StudyPlanResponseDto

    @POST("api/ai/assignment-breakdown")
    suspend fun analyzeAssignment(@Body request: AssignmentBreakdownRequest): AssignmentBreakdownResponseDto

    @POST("api/ai/focus-plan")
    suspend fun generateFocusPlan(@Body request: FocusPlanRequest): FocusPlanResponseDto
}

@Serializable data class HealthResponseDto(
    val status: String,
    val aiConfigured: Boolean,
    val model: String,
    val availableModels: List<String> = emptyList(),
    val visionModel: String? = null,
    val textModel: String? = null,
    val routingPolicy: String? = null,
)
@Serializable data class ChatHistoryMessageDto(
    val role: String,
    val content: String,
)

@Serializable data class ChatRequest(
    val message: String,
    val subject: String? = null,
    val contextSummary: String? = null,
    val conversationId: String? = null,
    val historyMessages: List<ChatHistoryMessageDto> = emptyList(),
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val imageBase64: String? = null,
    val attachmentText: String? = null,
    val model: String? = null,
)
@Serializable data class ChatResponseDto(
    val reply: String,
    val conversationId: String,
    val reasoning: String? = null,
    val model: String? = null,
)
@Serializable data class ScheduleAnalysisRequest(
    val imageBase64: String,
    val extractedText: String? = null,
)
@Serializable data class ExtractedClassDto(
    val subject: String,
    val teacher: String? = null,
    val room: String? = null,
    @JsonNames("day", "dayOfWeek", "day_of_week") val day: String = "",
    // Defaults + coerceInputValues keep one AI-returned null from failing the whole response.
    val startTime: String = "",
    val endTime: String = "",
)
@Serializable data class ScheduleAnalysisResponseDto(val classes: List<ExtractedClassDto>, val uncertainFields: List<String> = emptyList())
@Serializable data class TextRequest(val text: String)
@Serializable data class TextResponseDto(val result: String)
@Serializable data class FlashcardDto(val question: String, val answer: String, val topic: String? = null)
@Serializable data class FlashcardsResponseDto(val cards: List<FlashcardDto>)
@Serializable data class QuizQuestionDto(val type: String, val question: String, val options: List<String>, val correctAnswer: String)
@Serializable data class QuizResponseDto(val title: String, val questions: List<QuizQuestionDto>)
@Serializable data class StudyPlanRequest(
    val examDate: Long,
    val availableHours: Int,
    val subjects: List<String>,
    val topics: List<String>
)
@Serializable data class StudyPlanItemDto(val dayOfWeek: String, val startTime: String, val endTime: String, val subjectName: String, val topic: String, val activity: String)
@Serializable data class StudyPlanResponseDto(val title: String, val items: List<StudyPlanItemDto>)
@Serializable data class AssignmentBreakdownRequest(
    val text: String? = null,
    val attachmentText: String? = null,
    val imageBase64: String? = null,
)
@Serializable data class AssignmentSubtaskDto(
    val title: String,
    val estimatedMinutes: Int = 30,
    val dueOffsetDays: Int = 0,
)
@Serializable data class AssignmentBreakdownResponseDto(
    val title: String,
    val deadline: String? = null,
    val requirements: List<String> = emptyList(),
    val deliverables: List<String> = emptyList(),
    val rubric: List<String> = emptyList(),
    val subtasks: List<AssignmentSubtaskDto> = emptyList(),
    val estimatedEffortHours: Double = 1.0,
    val notes: String = "",
)
@Serializable data class FocusPlanRequest(
    val totalMinutes: Int,
    val subjects: List<String> = emptyList(),
    val upcomingExams: List<String> = emptyList(),
    val weakAreas: List<String> = emptyList(),
    val userPrompt: String? = null,
)
@Serializable data class FocusBlockDto(
    val startMinute: Int,
    val endMinute: Int,
    val activity: String,
    val type: String = "STUDY",
)
@Serializable data class FocusPlanResponseDto(
    val totalMinutes: Int,
    val blocks: List<FocusBlockDto>,
    val breakMinutesBetween: Int = 5,
)

fun AssignmentBreakdownResponseDto.toDomain() = AssignmentBreakdown(
    title = title,
    deadline = deadline,
    requirements = requirements,
    deliverables = deliverables,
    rubric = rubric,
    subtasks = subtasks.map {
        AssignmentSubtaskBreakdown(
            title = it.title,
            estimatedMinutes = it.estimatedMinutes,
            dueOffsetDays = it.dueOffsetDays,
        )
    },
    estimatedEffortHours = estimatedEffortHours,
    notes = notes,
)

fun FocusPlanResponseDto.toDomain() = FocusPlan(
    totalMinutes = totalMinutes,
    blocks = blocks.map {
        FocusBlock(
            startMinute = it.startMinute,
            endMinute = it.endMinute,
            activity = it.activity,
            type = FocusBlockType.fromString(it.type),
        )
    },
    breakMinutesBetween = breakMinutesBetween,
)

fun FlashcardDto.toDomain() = Flashcard(
    id = java.util.UUID.randomUUID().toString(), question = question, answer = answer,
    subjectId = null, topic = topic, difficulty = "medium",
    reviewCount = 0, correctCount = 0, incorrectCount = 0, lastReviewedAt = null, nextReviewAt = null
)

fun QuizResponseDto.toDomain(): Quiz {
    val quizId = java.util.UUID.randomUUID().toString()
    return Quiz(
        id = quizId, title = title, subjectId = null, sourceNoteId = null,
        questions = questions.map {
            QuizQuestion(
                java.util.UUID.randomUUID().toString(), quizId,
                AiJsonParser.normalizeQuestionType(it.type), it.question, it.options, it.correctAnswer
            )
        },
        createdAt = System.currentTimeMillis()
    )
}

fun StudyPlanResponseDto.toDomain(): StudyPlan {
    val planId = java.util.UUID.randomUUID().toString()
    return StudyPlan(
        id = planId, title = title, examId = null,
        items = items.map {
            StudyPlanItem(
                java.util.UUID.randomUUID().toString(), planId,
                DayOfWeek.fromString(it.dayOfWeek) ?: DayOfWeek.MONDAY,
                it.startTime, it.endTime, it.subjectName, it.topic, it.activity, Priority.MEDIUM
            )
        },
        createdAt = System.currentTimeMillis()
    )
}

object AiJsonParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val contentStart = if (firstNewline >= 0) firstNewline + 1 else 3
        val endFence = trimmed.lastIndexOf("```")
        if (endFence <= contentStart) return trimmed
        return trimmed.substring(contentStart, endFence).trim()
    }

    fun normalizeQuestionType(type: String): QuestionType {
        val normalized = type.trim()
            .uppercase()
            .replace('-', '_')
            .replace(' ', '_')
        return when {
            normalized.contains("TRUE") || normalized.contains("FALSE") -> QuestionType.TRUE_FALSE
            normalized.contains("SHORT") -> QuestionType.SHORT_ANSWER
            normalized.contains("MULTIPLE") || normalized.contains("CHOICE") -> QuestionType.MULTIPLE_CHOICE
            else -> runCatching { QuestionType.valueOf(normalized) }
                .getOrDefault(QuestionType.MULTIPLE_CHOICE)
        }
    }

    fun parseScheduleAnalysis(raw: String): ScheduleAnalysisResponseDto? = try {
        json.decodeFromString<ScheduleAnalysisResponseDto>(stripMarkdownFences(raw))
    } catch (_: Exception) { null }

    fun parseFlashcards(raw: String): List<FlashcardDto>? = try {
        val cleaned = stripMarkdownFences(raw)
        json.decodeFromString<FlashcardsResponseDto>(cleaned).cards.takeIf { it.isNotEmpty() }
            ?: json.decodeFromString<List<FlashcardDto>>(cleaned)
    } catch (_: Exception) {
        null
    }

    fun parseQuiz(raw: String): QuizResponseDto? = try {
        val cleaned = stripMarkdownFences(raw)
        json.decodeFromString<QuizResponseDto>(cleaned)
    } catch (_: Exception) {
        null
    }
}
