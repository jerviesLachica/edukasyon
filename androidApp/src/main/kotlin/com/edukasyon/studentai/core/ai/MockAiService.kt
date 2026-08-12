package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAiService @Inject constructor() : AiService {
    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val reply = when {
            request.message.contains("recursion", ignoreCase = true) ->
                "Recursion is when a function calls itself to solve a problem by breaking it into smaller subproblems. Each recursive call works on a simpler version until reaching a base case."
            request.message.contains("exam", ignoreCase = true) ->
                "Based on your upcoming exams, I recommend creating a study plan with focused review sessions. Would you like me to generate one?"
            else ->
                "I'm your StudentAI tutor. Ask me about any subject, and I'll help explain concepts clearly. ${request.subject?.let { "Current subject: $it." } ?: ""}"
        }
        return AiChatResponse(reply = reply, conversationId = request.conversationId ?: UUID.randomUUID().toString())
    }

    override suspend fun analyzeSchedule(imageData: ByteArray): ScheduleAnalysisResult = ScheduleAnalysisResult(
        classes = listOf(
            ExtractedClass("Programming 2", "Juan Santos", "304", "MONDAY", "08:00", "09:30"),
            ExtractedClass("Database Management", "Maria Cruz", "201", "WEDNESDAY", "10:00", "11:30")
        ),
        uncertainFields = listOf("room for Database Management")
    )

    override suspend fun summarize(text: String): String {
        val words = text.split("\\s+".toRegex()).take(30)
        return "Summary: ${words.joinToString(" ")}${if (text.split("\\s+".toRegex()).size > 30) "..." else ""}"
    }

    override suspend fun generateFlashcards(text: String): List<Flashcard> = listOf(
        Flashcard(UUID.randomUUID().toString(), "What is the main topic?", text.take(100), null, null, "medium", 0, 0, 0, null, null),
        Flashcard(UUID.randomUUID().toString(), "Key concept?", "Review the note content for details.", null, null, "easy", 0, 0, 0, null, null)
    )

    override suspend fun generateQuiz(text: String): Quiz {
        val quizId = UUID.randomUUID().toString()
        return Quiz(
            id = quizId,
            title = "Generated Quiz",
            subjectId = null,
            sourceNoteId = null,
            questions = listOf(
                QuizQuestion(UUID.randomUUID().toString(), quizId, QuestionType.MULTIPLE_CHOICE,
                    "What is covered in this note?", listOf("Option A", "Option B", "Option C"), "Option A"),
                QuizQuestion(UUID.randomUUID().toString(), quizId, QuestionType.TRUE_FALSE,
                    "This note contains important study material.", listOf("True", "False"), "True")
            ),
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun generateStudyPlan(context: StudyPlanContext): StudyPlan {
        val planId = UUID.randomUUID().toString()
        return StudyPlan(
            id = planId,
            title = "Study Plan",
            examId = null,
            items = context.subjects.mapIndexed { index, subject ->
                StudyPlanItem(
                    UUID.randomUUID().toString(), planId,
                    DayOfWeek.entries[index % 7],
                    "18:00", "18:45", subject,
                    context.topics.getOrElse(index) { "Review" },
                    "Study session", Priority.HIGH
                )
            },
            createdAt = System.currentTimeMillis()
        )
    }
}
