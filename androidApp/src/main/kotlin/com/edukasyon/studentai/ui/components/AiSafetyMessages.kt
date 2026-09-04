/**
 * User-friendly messages for AI safety error codes from the backend gateway.
 * Used with StudentAiSnackbar / statusMessage patterns.
 */

package com.edukasyon.studentai.ui.components

object AiSafetyMessages {

    const val CODE_RATE_LIMIT = "RATE_LIMIT_EXCEEDED"
    const val CODE_QUOTA = "QUOTA_EXCEEDED"
    const val CODE_CONTENT_BLOCKED = "CONTENT_BLOCKED"
    const val CODE_INJECTION_BLOCKED = "PROMPT_INJECTION_BLOCKED"
    const val CODE_ABUSE_DETECTED = "ABUSE_DETECTED"
    const val CODE_OUTPUT_BLOCKED = "OUTPUT_BLOCKED"

    fun rateLimitMessage(retryAfterMs: Long? = null): String {
        val seconds = retryAfterMs?.let { ((it + 999) / 1000).toInt() }
        return when {
            seconds == null || seconds <= 0 ->
                "You're sending requests too quickly. Please wait a moment before trying again."
            seconds <= 60 ->
                "Please wait $seconds seconds before using Jevi again."
            else -> {
                val minutes = (seconds + 59) / 60
                "Please wait about $minutes minute${if (minutes == 1) "" else "s"} before trying again."
            }
        }
    }

    fun quotaMessage(): String =
        "You've reached your AI usage limit for now. Try again in an hour or tomorrow."

    fun contentBlockedMessage(): String =
        "That message couldn't be processed. Please keep questions study-related and try again."

    fun injectionBlockedMessage(): String =
        "That message couldn't be processed. Ask a straightforward study question instead."

    fun abuseDetectedMessage(): String =
        "AI access is temporarily restricted. Please try again later."

    fun outputBlockedMessage(): String =
        "Jevi couldn't deliver a safe response. Please rephrase your question."

    fun providerBusyMessage(): String =
        "AI service is busy. Try again in a moment."

    fun providerUnavailableMessage(): String =
        "AI provider is temporarily unavailable."

    /**
     * Maps backend error JSON (code + optional retryAfterMs) to a user-facing string.
     */
    fun fromErrorCode(code: String?, retryAfterMs: Long? = null): String? = when (code) {
        CODE_RATE_LIMIT -> rateLimitMessage(retryAfterMs)
        CODE_QUOTA -> quotaMessage()
        CODE_CONTENT_BLOCKED -> contentBlockedMessage()
        CODE_INJECTION_BLOCKED -> injectionBlockedMessage()
        CODE_ABUSE_DETECTED -> abuseDetectedMessage()
        CODE_OUTPUT_BLOCKED -> outputBlockedMessage()
        "PROVIDER_BUSY" -> providerBusyMessage()
        "PROVIDER_ERROR", "AI_ERROR" -> providerUnavailableMessage()
        else -> null
    }

    /** Client-side Jevi tutor cooldown (complements server rate limit). */
    fun tutorClientCooldown(remainingMs: Long): String {
        val seconds = ((remainingMs + 999) / 1000).toInt()
        return if (seconds <= 1) {
            "Please wait a moment before asking Jevi again."
        } else {
            "Please wait $seconds seconds before asking Jevi again."
        }
    }

    fun stepModelQuotaMessage(retryAfterMs: Long? = null): String {
        val minutes = retryAfterMs?.let { ((it + 59_999) / 60_000).toInt() }
        return when {
            minutes == null || minutes <= 0 ->
                "Agnes 2.5 Flash limit reached — switched to Auto."
            else ->
                "Agnes 2.5 Flash limit reached — switched to Auto. Try again in ${minutes}m."
        }
    }
}
