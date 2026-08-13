package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.AssignmentAnalysisInput
import com.edukasyon.studentai.domain.model.AssignmentBreakdown
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.Subtask
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.domain.model.TaskStatus
import com.edukasyon.studentai.domain.repository.TaskRepository
import java.util.UUID
import javax.inject.Inject

class AiAnalyzeAssignmentUseCase @Inject constructor(
    private val aiService: AiService,
) : UseCase<AssignmentAnalysisInput, AssignmentBreakdown> {
    override suspend fun execute(params: AssignmentAnalysisInput): AssignmentBreakdown =
        aiService.analyzeAssignment(params)
}

class SaveAssignmentBreakdownToPlannerUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) : UseCase<AssignmentBreakdown, Task> {
    override suspend fun execute(params: AssignmentBreakdown): Task {
        val now = System.currentTimeMillis()
        val taskId = UUID.randomUUID().toString()
        val dueDateMillis = params.deadline?.let { DateUtils.parseIsoDate(it) }
        val priority = inferPriority(dueDateMillis)

        val description = buildDescription(params, dueDateMillis)

        val subtasks = params.subtasks.mapIndexed { index, sub ->
            Subtask(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                title = sub.title,
                isCompleted = false,
                sortOrder = index,
            )
        }

        val task = Task(
            id = taskId,
            title = "Assignment: ${params.title}",
            description = description,
            subjectId = null,
            priority = priority,
            dueDate = dueDateMillis,
            dueTime = "23:59",
            status = TaskStatus.PENDING,
            category = "assignment",
            reminderAt = null,
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            subtasks = subtasks,
        )

        taskRepository.createTask(task)
        return task
    }

    private fun inferPriority(dueDateMillis: Long?): Priority {
        if (dueDateMillis == null) return Priority.MEDIUM
        val days = DateUtils.daysUntil(dueDateMillis)
        return when {
            days <= 1 -> Priority.URGENT
            days <= 4 -> Priority.HIGH
            days <= 10 -> Priority.MEDIUM
            else -> Priority.LOW
        }
    }

    private fun buildDescription(breakdown: AssignmentBreakdown, dueDateMillis: Long?): String {
        val sections = mutableListOf<String>()

        breakdown.requirements.takeIf { it.isNotEmpty() }?.let { req ->
            sections.add("Requirements:\n${req.joinToString("\n") { "• $it" }}")
        }
        breakdown.deliverables.takeIf { it.isNotEmpty() }?.let { del ->
            sections.add("Deliverables:\n${del.joinToString("\n") { "• $it" }}")
        }
        breakdown.rubric.takeIf { it.isNotEmpty() }?.let { rub ->
            sections.add("Rubric:\n${rub.joinToString("\n") { "• $it" }}")
        }

        if (dueDateMillis != null && breakdown.subtasks.isNotEmpty()) {
            val schedule = breakdown.subtasks.map { sub ->
                val subDue = DateUtils.subtractDays(dueDateMillis, sub.dueOffsetDays)
                "• ${sub.title} (~${sub.estimatedMinutes} min) — target ${DateUtils.formatFullDate(subDue)}"
            }
            sections.add("Suggested schedule:\n${schedule.joinToString("\n")}")
        }

        sections.add("Estimated effort: ${breakdown.estimatedEffortHours} hours")

        if (breakdown.notes.isNotBlank()) {
            sections.add("Notes: ${breakdown.notes}")
        }

        return sections.joinToString("\n\n")
    }
}
