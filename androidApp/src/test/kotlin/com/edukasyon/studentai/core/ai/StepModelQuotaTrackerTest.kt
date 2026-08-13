package com.edukasyon.studentai.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepModelQuotaTrackerTest {

    @Test
    fun `allows up to five uses in window`() {
        val base = 1_000_000L
        var timestamps = emptyList<Long>()
        repeat(StepModelQuotaTracker.LIMIT) {
            assertTrue(StepModelQuotaTracker.canUse(timestamps, base + it))
            timestamps = StepModelQuotaTracker.recordUse(timestamps, base + it)
        }
        assertFalse(StepModelQuotaTracker.canUse(timestamps, base + StepModelQuotaTracker.LIMIT))
        assertEquals(0, StepModelQuotaTracker.status(timestamps, base + StepModelQuotaTracker.LIMIT).remaining)
    }

    @Test
    fun `formats remaining label with reset time`() {
        val base = 1_000_000L
        val timestamps = listOf(base)
        val status = StepModelQuotaTracker.status(timestamps, base + 60_000)
        val label = StepModelQuotaTracker.formatRemainingLabel(status)
        assertTrue(label.contains("4/5 left"))
        assertTrue(label.contains("resets in"))
    }
}
