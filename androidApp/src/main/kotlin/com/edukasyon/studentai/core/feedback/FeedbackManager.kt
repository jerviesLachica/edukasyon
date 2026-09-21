package com.edukasyon.studentai.core.feedback

import android.content.Context
import android.os.Build
import android.util.Log
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.FeedbackDeviceDto
import com.edukasyon.studentai.core.network.FeedbackRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FeedbackResult {
    data class Success(val message: String) : FeedbackResult
    data class Error(val error: String) : FeedbackResult
}

@Singleton
class FeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiApi: AiApiService,
) {
    companion object {
        private const val TAG = "FeedbackManager"
        private const val PREFS_NAME = "schedmate_feedback"
        private const val KEY_LAST_SUBMITTED = "last_feedback_time"
        const val COOLDOWN_SECONDS = 60
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getRemainingCooldownSeconds(): Long {
        val lastSubmitted = prefs.getLong(KEY_LAST_SUBMITTED, 0L)
        val elapsed = (System.currentTimeMillis() - lastSubmitted) / 1000
        val remaining = COOLDOWN_SECONDS - elapsed
        return if (remaining > 0) remaining else 0L
    }

    suspend fun submitFeedback(
        category: String,
        title: String,
        description: String,
        contact: String? = null,
        honeypot: String = "", // Anti-bot trap
    ): FeedbackResult = withContext(Dispatchers.IO) {
        val remaining = getRemainingCooldownSeconds()
        if (remaining > 0) {
            return@withContext FeedbackResult.Error(
                "Please wait $remaining seconds before submitting another report."
            )
        }

        val trimmedTitle = title.trim()
        val trimmedDesc = description.trim()

        if (trimmedTitle.length < 3) {
            return@withContext FeedbackResult.Error("Title must be at least 3 characters.")
        }
        if (trimmedDesc.length < 10) {
            return@withContext FeedbackResult.Error("Description must be at least 10 characters.")
        }

        val deviceDto = FeedbackDeviceDto(
            appVersion = BuildConfig.VERSION_NAME,
            buildNumber = BuildConfig.VERSION_CODE.toString(),
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        )

        val request = FeedbackRequestDto(
            category = category.lowercase(),
            title = trimmedTitle,
            description = trimmedDesc,
            device = deviceDto,
            contact = contact?.trim()?.ifBlank { null },
            hp = honeypot,
        )

        try {
            val response = aiApi.submitFeedback(request)
            if (response.ok) {
                // Record timestamp for client-side cooldown guardrail
                prefs.edit().putLong(KEY_LAST_SUBMITTED, System.currentTimeMillis()).apply()
                FeedbackResult.Success(response.message ?: "Thank you! Your feedback has been received.")
            } else {
                FeedbackResult.Error(response.error ?: "Submission failed. Please try again.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting feedback", e)
            val msg = e.message ?: "Network error"
            if (msg.contains("429")) {
                FeedbackResult.Error("Submission limit reached. Please wait a few minutes before trying again.")
            } else {
                FeedbackResult.Error("Failed to send feedback: $msg")
            }
        }
    }
}
