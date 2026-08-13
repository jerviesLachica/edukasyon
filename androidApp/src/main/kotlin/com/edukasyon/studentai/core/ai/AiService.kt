package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.domain.model.AssignmentAnalysisInput
import com.edukasyon.studentai.domain.model.AssignmentBreakdown
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.FocusPlan
import com.edukasyon.studentai.domain.model.FocusPlanContext
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.StudyPlan

data class AiChatHistoryMessage(
    val role: String,
    val content: String,
)

data class AiChatRequest(
    val message: String,
    val subject: String? = null,
    val contextSummary: String? = null,
    val conversationId: String? = null,
    val historyMessages: List<AiChatHistoryMessage> = emptyList(),
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val imageBase64: String? = null,
    val attachmentText: String? = null,
    val model: String? = null,
)

data class AiChatResponse(
    val reply: String,
    val conversationId: String,
    val reasoning: String? = null,
    val model: String? = null,
)

data class ScheduleAnalysisResult(
    val classes: List<ExtractedClass>,
    val uncertainFields: List<String> = emptyList()
)

data class ExtractedClass(
    val subject: String,
    val teacher: String?,
    val room: String?,
    val day: String,
    val startTime: String,
    val endTime: String
)

data class StudyPlanContext(
    val examDate: Long,
    val availableHours: Int,
    val subjects: List<String>,
    val topics: List<String>
)

/** Schedule scan payload: image plus optional on-device ML Kit OCR text. */
data class ScheduleScanInput(
    val imageData: ByteArray,
    val extractedText: String? = null,
)

interface AiService {
    suspend fun chat(request: AiChatRequest): AiChatResponse
    suspend fun analyzeSchedule(input: ScheduleScanInput): ScheduleAnalysisResult
    suspend fun summarize(text: String): String
    suspend fun generateFlashcards(text: String): List<Flashcard>
    suspend fun generateQuiz(text: String): Quiz
    suspend fun generateStudyPlan(context: StudyPlanContext): StudyPlan
    suspend fun analyzeAssignment(input: AssignmentAnalysisInput): AssignmentBreakdown
    suspend fun generateFocusPlan(context: FocusPlanContext): FocusPlan
}
