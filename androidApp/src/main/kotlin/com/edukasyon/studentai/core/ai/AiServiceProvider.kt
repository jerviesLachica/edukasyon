package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.network.ConnectivityMonitor
import com.edukasyon.studentai.domain.model.AssignmentAnalysisInput
import com.edukasyon.studentai.domain.model.FocusPlanContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

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

    override suspend fun chat(request: AiChatRequest): AiChatResponse = execute { it.chat(request) }

    override suspend fun analyzeSchedule(input: ScheduleScanInput): ScheduleAnalysisResult {
        if (!connectivity.isCurrentlyOnline()) {
            throw AiException(
                "Schedule scanning requires an internet connection. Connect to Wi‑Fi or mobile data and try again."
            )
        }
        return try {
            remote.analyzeSchedule(input)
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
    override suspend fun analyzeAssignment(input: AssignmentAnalysisInput) = execute { it.analyzeAssignment(input) }
    override suspend fun generateFocusPlan(context: FocusPlanContext) = execute { it.generateFocusPlan(context) }
}
