package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectPickerMergerTest {
    private val mathSubject = Subject(
        id = "sub-math",
        name = "Mathematics",
        code = null,
        teacher = "Mr. A",
        colorHex = "#3949AB",
        semester = "1st",
        schoolYear = "2025-2026",
    )

    private fun scheduleItem(name: String) = ScheduleItem(
        id = "sched-1",
        subjectId = null,
        subjectName = name,
        teacher = "Mr. B",
        room = "101",
        building = null,
        dayOfWeek = DayOfWeek.MONDAY,
        startTime = "08:00",
        endTime = "09:00",
        colorHex = "#1A237E",
        notes = null,
        semester = "",
        schoolYear = "",
    )

    @Test
    fun mergeSubjectsForPicker_includesScheduleOnlyNames() {
        val merged = SubjectPickerMerger.mergeSubjectsForPicker(
            subjects = emptyList(),
            scheduleItems = listOf(scheduleItem("Biology"), scheduleItem("Biology")),
        )

        assertEquals(1, merged.size)
        assertEquals("Biology", merged.single().name)
    }

    @Test
    fun mergeSubjectsForPicker_doesNotDuplicateExistingSubjectNames() {
        val merged = SubjectPickerMerger.mergeSubjectsForPicker(
            subjects = listOf(mathSubject),
            scheduleItems = listOf(scheduleItem("mathematics")),
        )

        assertEquals(1, merged.size)
        assertEquals("sub-math", merged.single().id)
    }

    @Test
    fun stableSubjectIdForScheduleName_isDeterministic() {
        val first = SubjectPickerMerger.stableSubjectIdForScheduleName("Biology")
        val second = SubjectPickerMerger.stableSubjectIdForScheduleName("biology")
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
    }
}
