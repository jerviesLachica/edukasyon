package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.toDomain
import com.edukasyon.studentai.domain.model.AssignmentAnalysisInput
import com.edukasyon.studentai.domain.model.AssignmentBreakdown
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.FocusPlan
import com.edukasyon.studentai.domain.model.FocusPlanContext
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.StudyPlan
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class RemoteAiService @Inject constructor(
    private val api: AiApiService
) : AiService {
    override suspend fun chat(request: AiChatRequest): AiChatResponse = apiCall {
        val response = api.chat(
            com.edukasyon.studentai.core.network.ChatRequest(
                message = request.message,
                subject = request.subject,
                contextSummary = request.contextSummary,
                conversationId = request.conversationId,
                historyMessages = request.historyMessages.map {
                    com.edukasyon.studentai.core.network.ChatHistoryMessageDto(
                        role = it.role,
                        content = it.content,
                    )
                },
                attachmentName = request.attachmentName,
                attachmentMimeType = request.attachmentMimeType,
                imageBase64 = request.imageBase64,
                attachmentText = request.attachmentText,
                model = request.model,
            )
        )
        val split = ReasoningContentSplitter.split(
            raw = response.reply,
            existingReasoning = response.reasoning?.trim()?.takeIf { it.isNotEmpty() },
        )
        val recovered = ReasoningContentSplitter.recoverEmptyReply(split)
        if (recovered.reply.isEmpty() && recovered.reasoning.isNullOrBlank()) {
            throw AiException("Jarvis returned an empty reply.")
        }
        AiChatResponse(
            reply = recovered.reply,
            conversationId = response.conversationId,
            reasoning = recovered.reasoning,
            model = response.model,
        )
    }

    override suspend fun analyzeSchedule(input: ScheduleScanInput): ScheduleAnalysisResult {
        return try {
            val response = api.analyzeSchedule(
                com.edukasyon.studentai.core.network.ScheduleAnalysisRequest(
                    imageBase64 = android.util.Base64.encodeToString(input.imageData, android.util.Base64.NO_WRAP),
                    extractedText = input.extractedText?.takeIf { it.isNotBlank() },
                )
            )
            ScheduleAnalysisResult(
                classes = response.classes.map {
                    ExtractedClass(it.subject, it.teacher, it.room, it.day, it.startTime, it.endTime)
                },
                uncertainFields = response.uncertainFields
            )
        } catch (e: Exception) {
            throw AiException("Failed to analyze schedule image.", e)
        }
    }

    override suspend fun summarize(text: String): String = apiCall { api.summarize(com.edukasyon.studentai.core.network.TextRequest(text)).result }
    override suspend fun generateFlashcards(text: String): List<Flashcard> =
        apiCall { api.generateFlashcards(com.edukasyon.studentai.core.network.TextRequest(text)).cards.map { it.toDomain() } }
    override suspend fun generateQuiz(text: String): Quiz =
        apiCall { api.generateQuiz(com.edukasyon.studentai.core.network.TextRequest(text)).toDomain() }
    override suspend fun generateStudyPlan(context: StudyPlanContext): StudyPlan =
        apiCall {
            api.generateStudyPlan(
                com.edukasyon.studentai.core.network.StudyPlanRequest(
                    examDate = context.examDate,
                    availableHours = context.availableHours,
                    subjects = context.subjects,
                    topics = context.topics
                )
            ).toDomain()
        }

    override suspend fun analyzeAssignment(input: AssignmentAnalysisInput): AssignmentBreakdown =
        apiCall {
            api.analyzeAssignment(
                com.edukasyon.studentai.core.network.AssignmentBreakdownRequest(
                    text = input.text,
                    attachmentText = input.attachmentText,
                    imageBase64 = input.imageBase64,
                )
            ).toDomain()
        }

    override suspend fun generateFocusPlan(context: FocusPlanContext): FocusPlan =
        apiCall {
            api.generateFocusPlan(
                com.edukasyon.studentai.core.network.FocusPlanRequest(
                    totalMinutes = context.totalMinutes,
                    subjects = context.subjects,
                    upcomingExams = context.upcomingExams,
                    weakAreas = context.weakAreas,
                    userPrompt = context.userPrompt,
                )
            ).toDomain()
        }

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            throw AiException("Internet connection required for this AI feature.", e)
        } catch (e: HttpException) {
            val rawBody = e.response()?.errorBody()?.string()
            val message = AiSafetyErrorParser.userMessage(e.code(), rawBody)
            throw AiException(message, e)
        } catch (e: Exception) {
            throw AiException("AI service error. Your offline data is still available.", e)
        }
    }
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
