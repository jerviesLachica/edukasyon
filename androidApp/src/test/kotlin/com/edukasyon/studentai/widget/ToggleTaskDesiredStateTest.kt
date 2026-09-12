package com.edukasyon.studentai.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 widget checkbox tap decision ([WidgetToggleReceiver][com.edukasyon.studentai.widget.update.WidgetToggleReceiver]):
 * absolute desired state wins (true -> COMPLETED, false -> PENDING);
 * a missing extra (stale pre-param intents) falls back to flipping DB state.
 */
class ToggleTaskDesiredStateTest {

    private fun resolveV2(desired: Boolean?, dbCompleted: Boolean): Boolean =
        desired ?: !dbCompleted

    @Test
    fun desiredTrue_checks_regardlessOfDb() {
        assertTrue(resolveV2(desired = true, dbCompleted = true))
        assertTrue(resolveV2(desired = true, dbCompleted = false))
    }

    @Test
    fun desiredFalse_unchecks_regardlessOfDb() {
        assertFalse(resolveV2(desired = false, dbCompleted = true))
        assertFalse(resolveV2(desired = false, dbCompleted = false))
    }

    @Test
    fun nullDesired_flipsDbState() {
        assertTrue(resolveV2(desired = null, dbCompleted = false))
        assertFalse(resolveV2(desired = null, dbCompleted = true))
    }
}
