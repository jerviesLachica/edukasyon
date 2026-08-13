package com.edukasyon.studentai.network

import com.edukasyon.studentai.config.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
class AiApiClient(
    private val baseUrl: String = BackendConfig.AI_BACKEND_URL,
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun health(): HealthResponseDto =
        httpClient.get("${baseUrl.trimEnd('/')}/health").body()

    suspend fun chat(request: ChatRequest): ChatResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun analyzeSchedule(request: ScheduleAnalysisRequest): ScheduleAnalysisResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/schedule-analysis") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun summarize(request: TextRequest): TextResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/summarize") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun generateFlashcards(request: TextRequest): FlashcardsResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/flashcards") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun generateQuiz(request: TextRequest): QuizResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/quiz") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun generateStudyPlan(request: StudyPlanRequest): StudyPlanResponseDto =
        httpClient.post("${baseUrl.trimEnd('/')}/api/ai/study-plan") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
