package com.edukasyon.studentai.core.ai

/**
 * Client-side sliding window for Agnes 2.5 Flash chat usage (mirrors backend: 25 req / 10 min).
 */
object StepModelQuotaTracker {
    const val LIMIT = 25
    const val WINDOW_MS = 10 * 60 * 1000L

    data class Status(
        val remaining: Int,
        val used: Int,
        val resetInMs: Long,
        val exhausted: Boolean,
    )

    fun status(timestamps: List<Long>, nowMs: Long = System.currentTimeMillis()): Status {
        val active = prune(timestamps, nowMs)
        val used = active.size
        val remaining = (LIMIT - used).coerceAtLeast(0)
        val resetInMs = if (active.isEmpty()) 0L else (active.first() + WINDOW_MS - nowMs).coerceAtLeast(0)
        return Status(
            remaining = remaining,
            used = used,
            resetInMs = resetInMs,
            exhausted = remaining <= 0,
        )
    }

    fun canUse(timestamps: List<Long>, nowMs: Long = System.currentTimeMillis()): Boolean =
        !status(timestamps, nowMs).exhausted

    fun recordUse(timestamps: List<Long>, nowMs: Long = System.currentTimeMillis()): List<Long> {
        val active = prune(timestamps, nowMs).toMutableList()
        active.add(nowMs)
        return active
    }

    fun prune(timestamps: List<Long>, nowMs: Long = System.currentTimeMillis()): List<Long> {
        val windowStart = nowMs - WINDOW_MS
        return timestamps.filter { it > windowStart }.sorted()
    }

    fun formatRemainingLabel(status: Status): String {
        if (status.remaining >= LIMIT) return "$LIMIT/$LIMIT left"
        val resetMinutes = ((status.resetInMs + 59_999) / 60_000).toInt()
        val resetPart = if (resetMinutes <= 0) "resets soon" else "resets in ${resetMinutes}m"
        return "${status.remaining}/$LIMIT left · $resetPart"
    }
}
