package com.edukasyon.studentai.core.notifications

import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.Assignment
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.domain.model.TaskStatus
import com.edukasyon.studentai.domain.repository.AssignmentRepository
import com.edukasyon.studentai.domain.repository.ExamRepository
import com.edukasyon.studentai.domain.repository.ScheduleRepository
import com.edukasyon.studentai.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderSyncService @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val taskRepo: TaskRepository,
    private val assignmentRepo: AssignmentRepository,
    private val examRepo: ExamRepository,
    private val reminderScheduler: ReminderScheduler,
    private val preferences: com.edukasyon.studentai.data.preferences.UserPreferences
) {
    private val reminderLeadMs = 15 * 60 * 1000L

    suspend fun rescheduleAll() {
        scheduleRepo.observeSchedule().first().forEach { scheduleClassReminder(it) }
        taskRepo.observeTasks().first()
            .filter { it.status != TaskStatus.COMPLETED && it.dueDate != null }
            .forEach { scheduleTaskReminder(it) }
        assignmentRepo.observeAssignments().first()
            .filter { it.status != TaskStatus.COMPLETED && it.dueDate != null }
            .forEach { scheduleAssignmentReminder(it) }
        examRepo.observeExams().first().forEach { scheduleExamReminder(it) }
    }

    suspend fun scheduleClassReminder(item: ScheduleItem) {
        val atTime = preferences.classReminderAtTime.first()
        val fifteenMin = preferences.classReminder15MinBefore.first()
        if (!atTime && !fifteenMin) return

        val classStart = nextOccurrenceMillis(item.dayOfWeek, item.startTime)
        val triggers = buildList {
            if (fifteenMin) {
                val lead = classStart - reminderLeadMs
                if (lead > System.currentTimeMillis()) add(lead to "Class starting soon")
            }
            if (atTime) {
                if (classStart > System.currentTimeMillis()) add(classStart to "Class starting now")
            }
        }
        triggers.forEachIndexed { index, (trigger, title) ->
            reminderScheduler.scheduleReminder(
                uniqueWorkName = "${workName(ReminderType.CLASS, item.id)}_$index",
                type = ReminderType.CLASS,
                title = title,
                message = "${item.subjectName} at ${item.startTime}${item.room?.let { " • Room $it" } ?: ""}",
                triggerAtMillis = trigger,
                referenceId = item.id
            )
        }
    }

    fun scheduleTaskReminder(task: Task) {
        if (task.status == TaskStatus.COMPLETED) {
            cancelTaskReminder(task.id)
            return
        }
        val due = DateUtils.effectiveDueMillis(task.dueDate, task.dueTime) ?: run {
            cancelTaskReminder(task.id)
            return
        }
        scheduleDueReminder(
            type = ReminderType.TASK,
            id = task.id,
            reminderAt = task.reminderAt,
            dueMillis = due,
            title = "Task due soon",
            message = task.title
        )
    }

    fun scheduleAssignmentReminder(assignment: Assignment) {
        if (assignment.status == TaskStatus.COMPLETED) {
            cancelAssignmentReminder(assignment.id)
            return
        }
        val due = DateUtils.effectiveDueMillis(assignment.dueDate, assignment.dueTime) ?: run {
            cancelAssignmentReminder(assignment.id)
            return
        }
        scheduleDueReminder(
            type = ReminderType.ASSIGNMENT,
            id = assignment.id,
            reminderAt = assignment.reminderAt,
            dueMillis = due,
            title = "Assignment due soon",
            message = assignment.title
        )
    }

    fun scheduleExamReminder(exam: Exam) {
        val trigger = (exam.reminderAt ?: DateUtils.defaultReminderAt(exam.examDate))
            .coerceAtLeast(System.currentTimeMillis() + 1000)
        reminderScheduler.scheduleReminder(
            uniqueWorkName = workName(ReminderType.EXAM, exam.id),
            type = ReminderType.EXAM,
            title = "Upcoming exam",
            message = "${exam.title} is coming up",
            triggerAtMillis = trigger,
            referenceId = exam.id
        )
    }

    fun cancelTaskReminder(taskId: String) {
        reminderScheduler.cancelReminder(workName(ReminderType.TASK, taskId))
    }

    fun cancelAssignmentReminder(assignmentId: String) {
        reminderScheduler.cancelReminder(workName(ReminderType.ASSIGNMENT, assignmentId))
    }

    fun cancelExamReminder(examId: String) {
        reminderScheduler.cancelReminder(workName(ReminderType.EXAM, examId))
    }

    private fun scheduleDueReminder(
        type: ReminderType,
        id: String,
        reminderAt: Long?,
        dueMillis: Long,
        title: String,
        message: String
    ) {
        if (reminderAt == 0L) {
            reminderScheduler.cancelReminder(workName(type, id))
            return
        }
        val trigger = (reminderAt ?: DateUtils.defaultReminderAt(dueMillis))
            .coerceAtLeast(System.currentTimeMillis() + 1000)
        reminderScheduler.scheduleReminder(
            uniqueWorkName = workName(type, id),
            type = type,
            title = title,
            message = message,
            triggerAtMillis = trigger,
            referenceId = id
        )
    }

    private fun workName(type: ReminderType, id: String): String = when (type) {
        ReminderType.CLASS -> "class_$id"
        ReminderType.TASK -> "task_$id"
        ReminderType.ASSIGNMENT -> "assignment_$id"
        ReminderType.EXAM -> "exam_$id"
    }

    private fun nextOccurrenceMillis(day: DayOfWeek, startTime: String): Long {
        val parts = startTime.split(":")
        val hour = parts.getOrElse(0) { "8" }.toInt()
        val minute = parts.getOrElse(1) { "0" }.toInt()
        val cal = Calendar.getInstance()
        val targetDow = when (day) {
            DayOfWeek.SUNDAY -> Calendar.SUNDAY
            DayOfWeek.MONDAY -> Calendar.MONDAY
            DayOfWeek.TUESDAY -> Calendar.TUESDAY
            DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
            DayOfWeek.THURSDAY -> Calendar.THURSDAY
            DayOfWeek.FRIDAY -> Calendar.FRIDAY
            DayOfWeek.SATURDAY -> Calendar.SATURDAY
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (cal.get(Calendar.DAY_OF_WEEK) != targetDow || cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}
