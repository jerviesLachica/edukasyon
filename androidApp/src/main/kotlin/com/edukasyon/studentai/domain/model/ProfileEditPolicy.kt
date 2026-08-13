package com.edukasyon.studentai.domain.model

object ProfileEditPolicy {
    const val BIO_MAX_LENGTH = 500
    const val COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    fun canEditProfile(now: Long, lastEditAt: Long?): Boolean {
        if (lastEditAt == null) return true
        return now - lastEditAt >= COOLDOWN_MS
    }

    fun millisUntilNextEdit(now: Long, lastEditAt: Long?): Long {
        if (lastEditAt == null) return 0L
        val elapsed = now - lastEditAt
        if (elapsed >= COOLDOWN_MS) return 0L
        return COOLDOWN_MS - elapsed
    }

    fun daysUntilNextEdit(now: Long, lastEditAt: Long?): Int {
        val remaining = millisUntilNextEdit(now, lastEditAt)
        if (remaining <= 0L) return 0
        return ((remaining + 86_399_999) / 86_400_000).toInt()
    }
}
