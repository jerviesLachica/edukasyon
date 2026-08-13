package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.ui.components.AiSafetyMessages
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses structured safety error responses from the backend gateway.
 */
object AiSafetyErrorParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class ErrorBody(
        val error: String? = null,
        val code: String? = null,
        @SerialName("retryAfterMs") val retryAfterMs: Long? = null,
    )

    private fun parseErrorBody(raw: String?): ErrorBody? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<ErrorBody>(raw) }.getOrNull()
    }

    fun userMessage(httpCode: Int, rawErrorBody: String?): String {
        val body = parseErrorBody(rawErrorBody)
        val fromCode = AiSafetyMessages.fromErrorCode(body?.code, body?.retryAfterMs)
        if (fromCode != null) return fromCode

        return when (httpCode) {
            429 -> AiSafetyMessages.rateLimitMessage(body?.retryAfterMs)
            402 -> AiSafetyMessages.quotaMessage()
            403 -> AiSafetyMessages.contentBlockedMessage()
            422 -> body?.error?.takeIf { it.isNotBlank() && !it.contains("sk-") }
                ?: AiSafetyMessages.outputBlockedMessage()
            502, 503 -> AiSafetyMessages.providerUnavailableMessage()
            504 -> "AI request timed out. Please try again."
            else -> body?.error?.takeIf { it.isNotBlank() && !it.contains("sk-") }
                ?: "AI service returned an error ($httpCode)."
        }
    }
}
