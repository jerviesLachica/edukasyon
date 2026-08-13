package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.network.ConnectivityMonitor
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class AiServiceProvider @Inject constructor(
    private val remote: RemoteAiService,
    private val mock: MockAiService,
    private val connectivity: ConnectivityMonitor
) : AiService {

    private suspend fun <T> execute(block: suspend (AiService) -> T): T {
        val service = if (connectivity.isOnline.first()) remote else mock
        return try {
            block(service)
        } catch (e: AiException) {
            if (service === remote && e.cause is IOException) {
                block(mock)
            } else {
                throw e
            }
        }
    }

    override suspend fun chat(request: AiChatRequest): AiChatResponse {
        if (request.imageBase64 != null) {
            if (!connectivity.isCurrentlyOnline()) {
                return mock.chat(request)
            }
            return try {
                remote.chat(request)
            } catch (e: AiException) {
                throw e
            }
        }

        if (!connectivity.isCurrentlyOnline()) {
            return mock.chat(request)
        }

        return try {
            remote.chat(request)
        } catch (first: AiException) {
            if (request.model == null && isRetryableProviderFailure(first)) {
                try {
                    return remote.chat(request.copy(model = VISION_FALLBACK_MODEL))
                } catch (retry: AiException) {
                    throw retry
                }
            }
            if (first.cause is IOException) {
                mock.chat(request)
            } else {
                throw first
            }
        }
    }

    override suspend fun analyzeSchedule(imageData: ByteArray): ScheduleAnalysisResult {
        if (!connectivity.isCurrentlyOnline()) {
            throw AiException(
                "Schedule scanning requires an internet connection. Connect to Wi‑Fi or mobile data and try again."
            )
        }
        return try {
            remote.analyzeSchedule(imageData)
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException(
                "Could not reach the AI server. Check your connection and try again.",
                e
            )
        }
    }

    override suspend fun summarize(text: String): String = execute { it.summarize(text) }
    override suspend fun generateFlashcards(text: String) = execute { it.generateFlashcards(text) }
    override suspend fun generateQuiz(text: String) = execute { it.generateQuiz(text) }
    override suspend fun generateStudyPlan(context: StudyPlanContext) = execute { it.generateStudyPlan(context) }

    private fun isRetryableProviderFailure(error: AiException): Boolean {
        val httpCode = (error.cause as? HttpException)?.code()
        if (httpCode == 502 || httpCode == 503 || httpCode == 429) return true
        val detail = buildString {
            append(error.message.orEmpty())
            error.cause?.message?.let { append(' ').append(it) }
        }
        return detail.contains("503", ignoreCase = true) ||
            detail.contains("502", ignoreCase = true) ||
            detail.contains("NO_UPSTREAM", ignoreCase = true) ||
            detail.contains("temporarily unavailable", ignoreCase = true) ||
            detail.contains("busy", ignoreCase = true)
    }

    companion object {
        private const val VISION_FALLBACK_MODEL = "claude-opus-4-8"
    }
}
