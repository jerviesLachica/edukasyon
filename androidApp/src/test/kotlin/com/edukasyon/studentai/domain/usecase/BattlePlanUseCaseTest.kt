package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.domain.model.ExamReadinessStatus
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.SuggestedAction
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BattlePlanUseCaseTest {

    private fun task(id: String, dueDate: Long?): Task = Task(
        id = id,
        title = id,
        description = null,
        subjectId = null,
        priority = Priority.MEDIUM,
        dueDate = dueDate,
        dueTime = null,
        status = TaskStatus.PENDING,
        category = null,
        reminderAt = null,
        createdAt = 0L,
        updatedAt = 0L,
        completedAt = null,
    )

    private fun classAt(start: String): ScheduleItem =
        com.edukasyon.studentai.domain.model.ScheduleItem(
            id = start,
            subjectId = null,
            subjectName = "Math",
            teacher = null,
            room = null,
            building = null,
            dayOfWeek = com.edukasyon.studentai.domain.model.DayOfWeek.MONDAY,
            startTime = start,
            endTime = "10:00",
            colorHex = "#3949AB",
            notes = null,
            semester = "",
            schoolYear = "",
            isRecurring = true,
        )

    @Test
    fun emptyInputs_plansDay() {
        val plan = GetTodaysBattlePlanUseCase.decide(
            todaySchedule = emptyList(),
            tasks = emptyList(),
            exams = emptyList(),
            readinessMap = emptyMap(),
            nowMillis = com.edukasyon.studentai.core.util.DateUtils.startOfDay(System.currentTimeMillis()) + 8 * 3_600_000L,
        )
        assertEquals(SuggestedAction.PLAN_DAY, plan.suggestedAction)
    }

    @Test
    fun overdueBeatsDueCards() {
        val now = com.edukasyon.studentai.core.util.DateUtils.startOfDay(System.currentTimeMillis()) + 8 * 3_600_000L
        val overdue = task("old", com.edukasyon.studentai.core.util.DateUtils.startOfDay(now) - 86_400_000L)
        val readiness = mapOf(
            "e1" to com.edukasyon.studentai.domain.model.ExamReadiness(
                examId = "e1",
                status = ExamReadinessStatus.READY,
                mode = com.edukasyon.studentai.domain.model.ExamReadinessMode.AUTO,
                readinessPercent = 40,
                dueCards = 12,
            ),
        )
        val plan = GetTodaysBattlePlanUseCase.decide(
            todaySchedule = emptyList(),
            tasks = listOf(overdue),
            exams = emptyList(),
            readinessMap = readiness,
            nowMillis = now,
        )
        assertEquals(SuggestedAction.CLEAR_OVERDUE, plan.suggestedAction)
        assertEquals(12, plan.dueFlashcardsCount)
    }

    @Test
    fun classWithin30Min_wins() {
        val base = com.edukasyon.studentai.core.util.DateUtils.startOfDay(System.currentTimeMillis())
        // 08:20 local on the same day; class at 08:30 is 10 min away.
        val now = base + (8 * 60 + 20) * 60_000L
        val plan = GetTodaysBattlePlanUseCase.decide(
            todaySchedule = listOf(classAt("08:30")),
            tasks = emptyList(),
            exams = emptyList(),
            readinessMap = emptyMap(),
            nowMillis = now,
        )
        assertEquals(SuggestedAction.ATTEND_NEXT_CLASS, plan.suggestedAction)
    }
}
