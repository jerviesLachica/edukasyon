package com.edukasyon.studentai.network

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.QuestionType
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion
import com.edukasyon.studentai.domain.model.StudyPlan
import com.edukasyon.studentai.domain.model.StudyPlanItem
import com.edukasyon.studentai.util.IdGenerator
import com.edukasyon.studentai.util.currentTimeMillis
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

@Serializable
data class HealthResponseDto(
    val status: String,
    val aiConfigured: Boolean,
    val model: String,
    val availableModels: List<String> = emptyList(),
    val visionModel: String? = null,
    val textModel: String? = null,
    val routingPolicy: String? = null,
)

@Serializable
data class ChatHistoryMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
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

@Serializable
data class ChatResponseDto(
    val reply: String,
    val conversationId: String,
    val reasoning: String? = null,
    val model: String? = null,
)

@Serializable
data class ScheduleAnalysisRequest(
    val imageBase64: String,
    val extractedText: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExtractedClassDto(
    val subject: String,
    val teacher: String? = null,
    val room: String? = null,
    @JsonNames("day", "dayOfWeek", "day_of_week") val day: String,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class ScheduleAnalysisResponseDto(
    val classes: List<ExtractedClassDto>,
    val uncertainFields: List<String> = emptyList(),
)

@Serializable
data class TextRequest(val text: String)

@Serializable
data class TextResponseDto(val result: String)

@Serializable
data class FlashcardDto(val question: String, val answer: String, val topic: String? = null)

@Serializable
data class FlashcardsResponseDto(val cards: List<FlashcardDto>)

@Serializable
data class QuizQuestionDto(
    val type: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
)

@Serializable
data class QuizResponseDto(val title: String, val questions: List<QuizQuestionDto>)

@Serializable
data class StudyPlanRequest(
    val examDate: Long,
    val availableHours: Int,
    val subjects: List<String>,
    val topics: List<String>,
)

@Serializable
data class StudyPlanItemDto(
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val subjectName: String,
    val topic: String,
    val activity: String,
)

@Serializable
data class StudyPlanResponseDto(val title: String, val items: List<StudyPlanItemDto>)

fun FlashcardDto.toDomain(): Flashcard = Flashcard(
    id = IdGenerator.newId(),
    question = question,
    answer = answer,
    subjectId = null,
    topic = topic,
    difficulty = "medium",
    reviewCount = 0,
    correctCount = 0,
    incorrectCount = 0,
    lastReviewedAt = null,
    nextReviewAt = null,
)

fun QuizResponseDto.toDomain(): Quiz {
    val quizId = IdGenerator.newId()
    return Quiz(
        id = quizId,
        title = title,
        subjectId = null,
        sourceNoteId = null,
        questions = questions.map {
            QuizQuestion(
                id = IdGenerator.newId(),
                quizId = quizId,
                type = AiJsonParser.normalizeQuestionType(it.type),
                question = it.question,
                options = it.options,
                correctAnswer = it.correctAnswer,
            )
        },
        createdAt = currentTimeMillis(),
    )
}

fun StudyPlanResponseDto.toDomain(): StudyPlan {
    val planId = IdGenerator.newId()
    return StudyPlan(
        id = planId,
        title = title,
        examId = null,
        items = items.map {
            StudyPlanItem(
                id = IdGenerator.newId(),
                planId = planId,
                dayOfWeek = DayOfWeek.fromString(it.dayOfWeek) ?: DayOfWeek.MONDAY,
                startTime = it.startTime,
                endTime = it.endTime,
                subjectName = it.subjectName,
                topic = it.topic,
                activity = it.activity,
                priority = Priority.MEDIUM,
            )
        },
        createdAt = currentTimeMillis(),
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
    } catch (_: Exception) {
        null
    }

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
