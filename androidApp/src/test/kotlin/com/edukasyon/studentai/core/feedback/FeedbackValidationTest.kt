package com.edukasyon.studentai.core.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackValidationTest {

    object FeedbackValidator {
        fun validate(title: String, description: String): String? {
            val trimmedTitle = title.trim()
            val trimmedDesc = description.trim()
            if (trimmedTitle.length < 3) return "Title must be at least 3 characters."
            if (trimmedDesc.length < 10) return "Description must be at least 10 characters."
            return null
        }

        fun calculateCooldown(lastSubmittedMs: Long, nowMs: Long, cooldownSec: Long = 60L): Long {
            if (lastSubmittedMs <= 0L) return 0L
            val elapsed = (nowMs - lastSubmittedMs) / 1000
            val remaining = cooldownSec - elapsed
            return if (remaining > 0) remaining else 0L
        }
    }

    @Test
    fun `rejects title shorter than 3 characters`() {
        val err = FeedbackValidator.validate("ab", "Valid description here")
        assertEquals("Title must be at least 3 characters.", err)
    }

    @Test
    fun `rejects description shorter than 10 characters`() {
        val err = FeedbackValidator.validate("Valid title", "short")
        assertEquals("Description must be at least 10 characters.", err)
    }

    @Test
    fun `accepts valid title and description`() {
        val err = FeedbackValidator.validate("Fix dark mode", "The widget does not display properly in dark mode.")
        assertEquals(null, err)
    }

    @Test
    fun `calculates remaining cooldown correctly`() {
        val now = 100_000L
        // Submitted 20 seconds ago
        val lastSubmitted = now - 20_000L
        val remaining = FeedbackValidator.calculateCooldown(lastSubmitted, now, cooldownSec = 60L)
        assertEquals(40L, remaining)
    }

    @Test
    fun `returns 0 cooldown after 60 seconds have elapsed`() {
        val now = 100_000L
        // Submitted 65 seconds ago
        val lastSubmitted = now - 65_000L
        val remaining = FeedbackValidator.calculateCooldown(lastSubmitted, now, cooldownSec = 60L)
        assertEquals(0L, remaining)
    }
}
