package com.edukasyon.studentai.core.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 urgent exam nudge decision coverage for
 * [ReviewReminderCoordinator.isUrgentExamNudge].
 *
 * Rule: urgent when the exam is within 3 days AND the linked deck shows more
 * than 40% of cards due — or the deck is empty/unlinked (totalCards == 0).
 */
class ReviewReminderCoordinatorTest {

    @Test
    fun examBeyondThreeDays_isNeverUrgent() {
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(4, dueCards = 10, totalCards = 10))
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(30, dueCards = 9, totalCards = 10))
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(7, dueCards = 0, totalCards = 0))
    }

    @Test
    fun examWithinThreeDays_highDueRatio_isUrgent() {
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(3, dueCards = 5, totalCards = 10))
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(1, dueCards = 8, totalCards = 9))
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(0, dueCards = 3, totalCards = 3))
    }

    @Test
    fun examWithinThreeDays_lowDueRatio_isNotUrgent() {
        // Exactly 40% is not strictly greater than 40% → not urgent.
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(2, dueCards = 4, totalCards = 10))
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(1, dueCards = 1, totalCards = 100))
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(3, dueCards = 0, totalCards = 10))
    }

    @Test
    fun examWithinThreeDays_emptyDeck_isUrgent() {
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(3, dueCards = 0, totalCards = 0))
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(0, dueCards = 0, totalCards = 0))
    }

    @Test
    fun invalidDueOverTotal_isUrgentOnlyInsideWindow() {
        // Degenerate input (due > total, e.g. deck changed mid-cycle): ratio > 1
        // still passes inside the window and stays blocked outside it.
        assertTrue(ReviewReminderCoordinator.isUrgentExamNudge(2, dueCards = 12, totalCards = 10))
        assertFalse(ReviewReminderCoordinator.isUrgentExamNudge(4, dueCards = 12, totalCards = 10))
    }
}
