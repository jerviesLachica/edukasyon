package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.domain.model.TaskStatus
import org.junit.Assert.*
import org.junit.Test

class GradeCalculatorTest {
    @Test
    fun calculateWeightedGrade_withMultipleCategories() {
        val entries = listOf(
            GradeEntry("1", "s1", "Quiz 1", "Quizzes", 18.0, 20.0, 0.3, "1st"),
            GradeEntry("2", "s1", "Quiz 2", "Quizzes", 16.0, 20.0, 0.3, "1st"),
            GradeEntry("3", "s1", "Exam", "Exams", 85.0, 100.0, 0.7, "1st")
        )
        val result = GradeCalculator.calculateWeightedGrade(entries)
        assertTrue(result > 0)
        assertTrue(result <= 100)
    }

    @Test
    fun calculatePercentage_returnsCorrectValue() {
        assertEquals(85.0, GradeCalculator.calculatePercentage(85.0, 100.0), 0.01)
        assertEquals(0.0, GradeCalculator.calculatePercentage(0.0, 100.0), 0.01)
    }

    @Test
    fun calculateWeightedGrade_emptyList_returnsZero() {
        assertEquals(0.0, GradeCalculator.calculateWeightedGrade(emptyList()), 0.01)
    }
}

class ScheduleValidatorTest {
    private fun item(id: String, day: DayOfWeek, start: String, end: String) = ScheduleItem(
        id, null, "Subject", null, null, null, day, start, end, "#000", null, "", ""
    )

    @Test
    fun hasOverlap_detectsOverlappingClasses() {
        val a = item("1", DayOfWeek.MONDAY, "08:00", "09:30")
        val b = item("2", DayOfWeek.MONDAY, "09:00", "10:00")
        assertTrue(ScheduleValidator.hasOverlap(a, b))
    }

    @Test
    fun hasOverlap_differentDays_noOverlap() {
        val a = item("1", DayOfWeek.MONDAY, "08:00", "09:30")
        val b = item("2", DayOfWeek.TUESDAY, "08:00", "09:30")
        assertFalse(ScheduleValidator.hasOverlap(a, b))
    }

    @Test
    fun hasOverlap_nonOverlappingTimes() {
        val a = item("1", DayOfWeek.MONDAY, "08:00", "09:00")
        val b = item("2", DayOfWeek.MONDAY, "10:00", "11:00")
        assertFalse(ScheduleValidator.hasOverlap(a, b))
    }
}

class TaskSorterTest {
    @Test
    fun sortByPriorityAndDueDate_urgentFirst() {
        val now = System.currentTimeMillis()
        val tasks = listOf(
            Task("1", "Low", null, null, Priority.LOW, now + 1000, null, TaskStatus.PENDING, null, null, now, now, null),
            Task("2", "Urgent", null, null, Priority.URGENT, now + 5000, null, TaskStatus.PENDING, null, null, now, now, null),
            Task("3", "High", null, null, Priority.HIGH, now + 2000, null, TaskStatus.PENDING, null, null, now, now, null)
        )
        val sorted = TaskSorter.sortByPriorityAndDueDate(tasks)
        assertEquals("Urgent", sorted[0].title)
        assertEquals("High", sorted[1].title)
        assertEquals("Low", sorted[2].title)
    }
}

class DateUtilsTest {
    @Test
    fun formatTime12h_24hFormat_convertsCorrectly() {
        assertEquals("2:30 PM", DateUtils.formatTime12h("14:30"))
        assertEquals("8:05 AM", DateUtils.formatTime12h("08:05"))
        assertEquals("12:00 AM", DateUtils.formatTime12h("00:00"))
        assertEquals("12:00 PM", DateUtils.formatTime12h("12:00"))
    }

    @Test
    fun formatTime12h_already12hFormat_doesNotDuplicateAmPm() {
        assertEquals("9:30 AM", DateUtils.formatTime12h("9:30 AM"))
        assertEquals("2:15 PM", DateUtils.formatTime12h("02:15 PM"))
        assertEquals("12:00 AM", DateUtils.formatTime12h("12:00 AM"))
    }

    @Test
    fun combineDateAndTime_handlesBoth12hAnd24h() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.OCTOBER, 1, 0, 0, 0)
        val dateMillis = cal.timeInMillis

        val combined24 = DateUtils.combineDateAndTime(dateMillis, "14:30")
        val cal24 = java.util.Calendar.getInstance().apply { timeInMillis = combined24 }
        assertEquals(14, cal24.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal24.get(java.util.Calendar.MINUTE))

        val combined12 = DateUtils.combineDateAndTime(dateMillis, "2:30 PM")
        val cal12 = java.util.Calendar.getInstance().apply { timeInMillis = combined12 }
        assertEquals(14, cal12.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal12.get(java.util.Calendar.MINUTE))
    }
}
