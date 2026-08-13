package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.QuestionType
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.QuizQuestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiToolMetadataDto(
    val kind: String,
    val summary: String? = null,
    val reasoning: String? = null,
    val flashcards: List<FlashcardDto>? = null,
    val quiz: QuizDto? = null,
)

@Serializable
data class FlashcardDto(
    val id: String,
    val question: String,
    val answer: String,
    val topic: String? = null,
)

@Serializable
data class QuizDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val questions: List<QuizQuestionDto>,
)

@Serializable
data class QuizQuestionDto(
    val id: String,
    val quizId: String,
    val type: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
)

object AiConversationMetadata {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSummary(summary: String): String =
        json.encodeToString(AiToolMetadataDto(kind = "SUMMARY", summary = summary))

    fun encodeTutorReasoning(reasoning: String?): String? =
        reasoning?.trim()?.takeIf { it.isNotEmpty() }?.let {
            json.encodeToString(AiToolMetadataDto(kind = "TUTOR", reasoning = it))
        }

    fun decodeTutorReasoning(raw: String?): String? =
        decode(raw)?.takeIf { it.kind == "TUTOR" }?.reasoning?.trim()?.takeIf { it.isNotEmpty() }

    fun encodeFlashcards(cards: List<Flashcard>): String =
        json.encodeToString(
            AiToolMetadataDto(
                kind = "FLASHCARDS",
                flashcards = cards.map {
                    FlashcardDto(it.id, it.question, it.answer, it.topic)
                },
            )
        )

    fun encodeQuiz(quiz: Quiz): String =
        json.encodeToString(
            AiToolMetadataDto(
                kind = "QUIZ",
                quiz = QuizDto(
                    id = quiz.id,
                    title = quiz.title,
                    createdAt = quiz.createdAt,
                    questions = quiz.questions.map {
                        QuizQuestionDto(
                            id = it.id,
                            quizId = it.quizId,
                            type = it.type.name,
                            question = it.question,
                            options = it.options,
                            correctAnswer = it.correctAnswer,
                        )
                    },
                ),
            )
        )

    fun decode(raw: String?): AiToolMetadataDto? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString<AiToolMetadataDto>(it) }.getOrNull() }

    fun toFlashcards(dto: AiToolMetadataDto): List<Flashcard> =
        dto.flashcards.orEmpty().map {
            Flashcard(
                id = it.id,
                question = it.question,
                answer = it.answer,
                subjectId = null,
                topic = it.topic,
                difficulty = "MEDIUM",
                reviewCount = 0,
                correctCount = 0,
                incorrectCount = 0,
                lastReviewedAt = null,
                nextReviewAt = null,
            )
        }

    fun toQuiz(dto: AiToolMetadataDto): Quiz? {
        val quizDto = dto.quiz ?: return null
        return Quiz(
            id = quizDto.id,
            title = quizDto.title,
            subjectId = null,
            sourceNoteId = null,
            createdAt = quizDto.createdAt,
            questions = quizDto.questions.map {
                QuizQuestion(
                    id = it.id,
                    quizId = it.quizId,
                    type = runCatching { QuestionType.valueOf(it.type) }.getOrDefault(QuestionType.MULTIPLE_CHOICE),
                    question = it.question,
                    options = it.options,
                    correctAnswer = it.correctAnswer,
                )
            },
        )
    }
}
