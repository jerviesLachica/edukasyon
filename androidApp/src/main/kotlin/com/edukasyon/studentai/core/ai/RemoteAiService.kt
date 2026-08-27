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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            throw AiException("Jevi returned an empty reply.")
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
            val compressed = withContext(Dispatchers.Default) {
                compressScheduleImage(input.imageData)
            }
            val response = api.analyzeSchedule(
                com.edukasyon.studentai.core.network.ScheduleAnalysisRequest(
                    imageBase64 = android.util.Base64.encodeToString(compressed, android.util.Base64.NO_WRAP),
                    extractedText = input.extractedText?.takeIf { it.isNotBlank() },
                )
            )
            ScheduleAnalysisResult(
                classes = response.classes.mapNotNull { dto ->
                    // Drop entries the AI left blank instead of failing the whole scan.
                    val subject = dto.subject.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val day = dto.day.trim().uppercase().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val startTime = dto.startTime.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val endTime = dto.endTime.trim().takeIf { it.isNotEmpty() } ?: startTime
                    ExtractedClass(subject, dto.teacher, dto.room, day, startTime, endTime)
                },
                uncertainFields = response.uncertainFields
            )
        } catch (e: Exception) {
            throw AiException("Failed to analyze schedule image.", e)
        }
    }

    private companion object {
        // 1280px is the sweet spot for the schedule-scanner vision prompt:
        // text in typical class schedules stays legible at q75 while upload size
        // drops by ~40% vs the previous 1568/q85 combination. Lower latency is
        // the single biggest UX win for "scanning is too slow".
        private const val SCAN_MAX_DIMENSION = 1280
        private const val SCAN_JPEG_QUALITY = 75
    }

    /** Downscale large camera photos so the vision model processes them faster. */
    private fun compressScheduleImage(bytes: ByteArray): ByteArray {
        // Two-pass decode: first read only the JPEG header to discover
        // dimensions, then decode with inSampleSize to load a sub-resolution
        // bitmap directly. This avoids paying for a 12-megapixel camera
        // photo's full RGBA memory and decode time before throwing most of
        // it away — the biggest single-source of "scanning is slow" reports.
        val (srcW, srcH) = readImageDimensions(bytes) ?: return bytes
        if (srcW <= 0 || srcH <= 0) return bytes
        val sample = computeInSampleSize(srcW, srcH, SCAN_MAX_DIMENSION)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: return bytes
        return try {
            val ratio = min(
                SCAN_MAX_DIMENSION.toFloat() / decoded.width,
                SCAN_MAX_DIMENSION.toFloat() / decoded.height,
            )
            if (ratio >= 1f) {
                // Decoded size is already under cap — re-encode at lower quality.
                ByteArrayOutputStream().use { stream ->
                    decoded.compress(Bitmap.CompressFormat.JPEG, SCAN_JPEG_QUALITY, stream)
                    stream.toByteArray()
                }
            } else {
                val newW = max(1, (decoded.width * ratio).toInt())
                val newH = max(1, (decoded.height * ratio).toInt())
                val scaled = Bitmap.createScaledBitmap(decoded, newW, newH, true)
                ByteArrayOutputStream().use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, SCAN_JPEG_QUALITY, stream)
                    stream.toByteArray()
                }
            }
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Read JPEG dimensions without fully decoding the image. Uses a
     * BitmapFactory.Options with `inJustDecodeBounds = true`, which skips
     * bitmap allocation and only parses the header — typically <10 ms vs
     * ~100 ms for a full decode of a 12MP photo.
     */
    private fun readImageDimensions(bytes: ByteArray): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return if (opts.outWidth > 0 && opts.outHeight > 0) Pair(opts.outWidth, opts.outHeight) else null
    }

    /**
     * Returns an `inSampleSize` power-of-two that fits both dimensions
     * within [maxDim]. The logic mirrors Android's `computeSampleSize`,
     * clamping to powers of two so the decoder works reliably on all
     * hardware paths.
     */
    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        while (width / (sample * 2) > maxDim && height / (sample * 2) > maxDim) {
            sample *= 2
        }
        return sample
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
