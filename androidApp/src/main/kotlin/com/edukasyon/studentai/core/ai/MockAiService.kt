package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAiService @Inject constructor() : AiService {
    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        val conversationId = request.conversationId ?: UUID.randomUUID().toString()
        val message = request.message
        val lower = message.lowercase()

        if (isJailbreakAttempt(lower)) {
            return AiChatResponse(
                reply = "I'm Jevi, your study tutor — I can't change my role or share internal instructions. What subject or assignment can I help you with?",
                conversationId = conversationId,
            )
        }
        if (isActiveExamCheatingRequest(lower)) {
            return AiChatResponse(
                reply = "I can't help with active exams — that wouldn't be fair to you or your classmates. After the exam, I'm happy to help you review topics you found tricky.",
                conversationId = conversationId,
            )
        }

        if (request.imageBase64 != null) {
            return AiChatResponse(
                reply = "I received your image${request.attachmentName?.let { " ($it)" } ?: ""}. " +
                    "In offline mode I can't analyze images yet — connect to the backend for vision support. " +
                    "Your message: $message",
                conversationId = conversationId,
            )
        }
        if (request.attachmentText != null || request.attachmentName != null) {
            val preview = request.attachmentText?.take(120)?.let { " Content preview: \"$it\"..." } ?: ""
            return AiChatResponse(
                reply = "Got your file${request.attachmentName?.let { " \"$it\"" } ?: ""}.$preview " +
                    "I'll use it as context. $message",
                conversationId = conversationId,
            )
        }

        if (isFollowUpAboutMissingAnswer(lower, request.historyMessages)) {
            val priorUser = request.historyMessages.lastOrNull { it.role == "user" }?.content
            val priorAssistant = request.historyMessages.lastOrNull { it.role == "assistant" }?.content
            return AiChatResponse(
                reply = if (!priorAssistant.isNullOrBlank()) {
                    "You're following up on: \"$priorUser\". My previous reply starts with: " +
                        "\"${priorAssistant.take(200)}${if (priorAssistant.length > 200) "…" else ""}\". " +
                        "If the answer bubble was empty, expand the Reasoning section above or ask me to resend the full text."
                } else {
                    "You're following up on: \"$priorUser\". My previous response may not have displayed correctly — " +
                        "ask me to resend the full answer."
                },
                conversationId = conversationId,
                model = "mock",
            )
        }

        val reply = when {
            lower.contains("add") && lower.contains("task") -> {
                """Sure! I'll add that task to your planner.

```actions
{"actions":[{"type":"add_task","title":"${message.take(60)}","priority":"MEDIUM"}]}
```"""
            }
            lower.contains("add") &&
                (lower.contains("class") || lower.contains("schedule")) -> {
                """I'll add that class to your schedule.

```actions
{"actions":[{"type":"add_schedule","subject":"New Class","day":"MONDAY","startTime":"08:00","endTime":"09:00"}]}
```"""
            }
            lower.contains("recursion") -> {
                "Recursion is when a function calls itself to solve a problem by breaking it into smaller subproblems. " +
                    "Each call works on a simpler version until you reach a base case. " +
                    "For a gentle walkthrough, see Khan Academy's recursion lessons: https://www.khanacademy.org/computing/computer-science/algorithms"
            }
            lower.contains("photosynthesis") ->
                "Photosynthesis converts light energy into chemical energy (glucose) using CO₂ and water, releasing oxygen. " +
                    "It happens mainly in chloroplasts. I'm confident in the basics; for diagrams and quizzes, try Wikipedia: https://en.wikipedia.org/wiki/Photosynthesis"
            lower.contains("link") || lower.contains("website") || lower.contains("resource") ->
                "When I suggest links, I only share well-known trusted sites (https) — never made-up URLs. " +
                    "Good starting points: Khan Academy (https://www.khanacademy.org), Wikipedia (https://en.wikipedia.org), or your school's official LMS. " +
                    "What topic are you studying?"
            lower.contains("exam") ->
                "Based on your upcoming exams, I recommend focused review sessions and practice questions. " +
                    "Would you like help planning what to study each day?"
            else ->
                buildDefaultGizmoReply(request)
        }

        val reasoning = when {
            lower.contains("recursion") ->
                "The student asked about recursion. I'll define it as self-calling functions, mention base cases, and link to Khan Academy for practice."
            lower.contains("photosynthesis") ->
                "This is a biology concept. I'll cover inputs (light, CO₂, water), outputs (glucose, O₂), and where it happens (chloroplasts)."
            lower.contains("explain") || lower.contains("how") || lower.contains("why") ->
                "The student wants a conceptual explanation. I'll start with a plain definition, add one simple example, then keep the answer concise."
            else -> null
        }

        return AiChatResponse(
            reply = reply,
            conversationId = conversationId,
            reasoning = reasoning,
            model = "mock",
        )
    }

    private fun isJailbreakAttempt(lower: String): Boolean {
        val patterns = listOf(
            "ignore previous instructions",
            "ignore prior instructions",
            "ignore all instructions",
            "reveal your system prompt",
            "reveal system prompt",
            "you are now",
            "act as dan",
            "jailbreak",
        )
        return patterns.any { lower.contains(it) }
    }

    private fun isActiveExamCheatingRequest(lower: String): Boolean {
        val cheat = lower.contains("cheat") ||
            (lower.contains("answer") && (lower.contains("exam") || lower.contains("test") || lower.contains("quiz")))
        val active = lower.contains("during") || lower.contains("right now") ||
            lower.contains("in progress") || lower.contains("currently taking")
        return cheat && active
    }

    private fun isFollowUpAboutMissingAnswer(
        lower: String,
        history: List<AiChatHistoryMessage>,
    ): Boolean {
        if (history.isEmpty()) return false
        return (lower.contains("where") && (lower.contains("answer") || lower.contains("essay") || lower.contains("response"))) ||
            lower.contains("where's the answer") ||
            lower.contains("wheres the answer")
    }

    private fun buildDefaultGizmoReply(request: AiChatRequest): String {
        val subjectPart = request.subject?.let { " Subject focus: $it." } ?: ""
        val contextPart = request.contextSummary?.let { " From your app: $it" } ?: ""
        val historyPart = if (request.historyMessages.isNotEmpty()) {
            " Continuing our chat (${request.historyMessages.size} prior messages)."
        } else {
            ""
        }
        return "Hi! I'm Jevi, your study tutor in SchedMate.$subjectPart$contextPart$historyPart " +
            "Ask me to explain a concept, plan study time, or add tasks to your planner. " +
            "I'll be honest when I'm unsure and won't make up facts or links."
    }

    override suspend fun analyzeSchedule(input: ScheduleScanInput): ScheduleAnalysisResult {
        throw AiException(
            "Schedule scanning requires an internet connection. Connect to Wi‑Fi or mobile data and try again."
        )
    }

    override suspend fun summarize(text: String): String {
        val words = text.split("\\s+".toRegex()).take(30)
        return "Summary: ${words.joinToString(" ")}${if (text.split("\\s+".toRegex()).size > 30) "..." else ""}"
    }

    override suspend fun generateFlashcards(text: String): List<Flashcard> = listOf(
        Flashcard(
            id = UUID.randomUUID().toString(),
            question = "What is the main topic?",
            answer = text.take(100),
            subjectId = null,
            topic = null,
            difficulty = "medium",
            reviewCount = 0,
            correctCount = 0,
            incorrectCount = 0,
            lastReviewedAt = null,
            nextReviewAt = null,
        ),
        Flashcard(
            id = UUID.randomUUID().toString(),
            question = "Key concept?",
            answer = "Review the note content for details.",
            subjectId = null,
            topic = null,
            difficulty = "easy",
            reviewCount = 0,
            correctCount = 0,
            incorrectCount = 0,
            lastReviewedAt = null,
            nextReviewAt = null,
        ),
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

    override suspend fun analyzeAssignment(input: AssignmentAnalysisInput): AssignmentBreakdown {
        val text = listOfNotNull(input.text, input.attachmentText).joinToString("\n")
        val title = text.lineSequence().firstOrNull { it.length in 5..80 }?.trim()
            ?: "Sample Assignment"
        val deadlineCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, 7) }
        val deadline = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(deadlineCal.time)
        return AssignmentBreakdown(
            title = title.take(80),
            deadline = deadline,
            requirements = listOf("Follow assignment instructions", "Meet minimum length requirements"),
            deliverables = listOf("Final submission file"),
            rubric = listOf("Content quality", "Organization", "Timeliness"),
            subtasks = listOf(
                AssignmentSubtaskBreakdown("Review instructions", 20, 6),
                AssignmentSubtaskBreakdown("Research topic", 90, 4),
                AssignmentSubtaskBreakdown("Create outline", 45, 3),
                AssignmentSubtaskBreakdown("Write draft", 120, 2),
                AssignmentSubtaskBreakdown("Revise and submit", 60, 0),
            ),
            estimatedEffortHours = 5.5,
            notes = "Mock breakdown — connect to backend for real AI analysis.",
        )
    }

    override suspend fun generateFocusPlan(context: FocusPlanContext): FocusPlan {
        val total = context.totalMinutes.coerceIn(15, 240)
        val subjects = context.subjects.ifEmpty { listOf("General review") }
        val breakGap = 5
        val blockDuration = (total / subjects.size).coerceAtLeast(15)
        val blocks = subjects.mapIndexed { index, subject ->
            val start = index * (blockDuration + breakGap)
            val end = (start + blockDuration).coerceAtMost(total)
            FocusBlock(
                startMinute = start,
                endMinute = end,
                activity = subject,
                type = if (index == subjects.lastIndex && total - end >= 10) FocusBlockType.REVIEW else FocusBlockType.STUDY,
            )
        }.filter { it.endMinute > it.startMinute }
        return FocusPlan(
            totalMinutes = total,
            blocks = blocks.ifEmpty {
                listOf(FocusBlock(0, total, subjects.first(), FocusBlockType.STUDY))
            },
            breakMinutesBetween = breakGap,
        )
    }
}
