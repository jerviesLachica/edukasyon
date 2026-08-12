package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.toDomain
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.StudyPlan
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAiService @Inject constructor(
    private val api: AiApiService
) : AiService {
    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        return try {
            val response = api.chat(
                com.edukasyon.studentai.core.network.ChatRequest(
                    message = request.message,
                    subject = request.subject,
                    contextSummary = request.contextSummary,
                    conversationId = request.conversationId
                )
            )
            AiChatResponse(reply = response.reply, conversationId = response.conversationId)
        } catch (e: IOException) {
            throw AiException("No internet connection. AI features require an online connection.", e)
        } catch (e: Exception) {
            throw AiException("AI service is temporarily unavailable.", e)
        }
    }

    override suspend fun analyzeSchedule(imageData: ByteArray): ScheduleAnalysisResult {
        return try {
            val response = api.analyzeSchedule(
                com.edukasyon.studentai.core.network.ScheduleAnalysisRequest(
                    imageBase64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
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

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: IOException) {
            throw AiException("Internet connection required for this AI feature.", e)
        } catch (e: Exception) {
            throw AiException("AI service error. Your offline data is still available.", e)
        }
    }
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
