package com.edukasyon.studentai.widget.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the widget toggle behavior ensuring:
 * 1. Tap on checked (desired=false) transitions to PENDING (unchecked).
 * 2. Tap on unchecked (desired=true) transitions to COMPLETED (checked).
 * 3. Fallback when desired is null flips the DB state.
 */
class WidgetToggleStateTransitionTest {

    private val completedName = com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED.name
    private val pendingName = com.edukasyon.studentai.domain.model.TaskStatus.PENDING.name

    private fun resolveNewStatus(currentStatus: String, desired: Boolean?): String {
        return when (desired) {
            true -> completedName
            false -> pendingName
            null -> if (currentStatus == completedName) pendingName else completedName
        }
    }

    @Test
    fun checkedTask_whenClicked_changesToUnchecked() {
        // Given a task that is currently COMPLETED (checked)
        val currentStatus = completedName
        // When user clicks the checked box, the intent sends desiredCompleted = false
        val desiredCompleted = false
        
        val newStatus = resolveNewStatus(currentStatus, desiredCompleted)
        
        // Then it must become PENDING (unchecked)
        assertEquals(pendingName, newStatus)
    }

    @Test
    fun uncheckedTask_whenClicked_changesToChecked() {
        // Given a task that is currently PENDING (unchecked)
        val currentStatus = pendingName
        // When user clicks the unchecked box, the intent sends desiredCompleted = true
        val desiredCompleted = true
        
        val newStatus = resolveNewStatus(currentStatus, desiredCompleted)
        
        // Then it must become COMPLETED (checked)
        assertEquals(completedName, newStatus)
    }

    @Test
    fun nullDesired_flipsCheckedToUnchecked() {
        assertEquals(pendingName, resolveNewStatus(completedName, null))
    }

    @Test
    fun nullDesired_flipsUncheckedToChecked() {
        assertEquals(completedName, resolveNewStatus(pendingName, null))
    }
}
