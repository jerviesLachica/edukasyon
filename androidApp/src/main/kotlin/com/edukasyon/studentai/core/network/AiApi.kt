package com.edukasyon.studentai.core.network

import com.edukasyon.studentai.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
}

@Serializable data class HealthResponseDto(val status: String, val aiConfigured: Boolean, val model: String)
@Serializable data class ChatRequest(val message: String, val subject: String? = null, val contextSummary: String? = null, val conversationId: String? = null)
@Serializable data class ChatResponseDto(val reply: String, val conversationId: String)
@Serializable data class ScheduleAnalysisRequest(val imageBase64: String)
@Serializable data class ExtractedClassDto(val subject: String, val teacher: String? = null, val room: String? = null, val day: String, val startTime: String, val endTime: String)
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
    private val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val match = fenceRegex.find(trimmed)
        return (match?.groupValues?.getOrNull(1) ?: trimmed).trim()
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
