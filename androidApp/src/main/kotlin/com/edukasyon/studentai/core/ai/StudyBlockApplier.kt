package com.edukasyon.studentai.core.ai

import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.Task
import com.edukasyon.studentai.domain.model.TaskStatus
import com.edukasyon.studentai.domain.usecase.CreateTaskUseCase
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of applying accepted study blocks: tasks created plus per-block failure messages. */
data class StudyBlockApplyResult(
    val createdCount: Int,
    val failures: List<String>,
)

/** Converts accepted [StudyBlockPayload]s into planner tasks; malformed or failing blocks are reported, never thrown. */
@Singleton
class StudyBlockApplier @Inject constructor(
    private val createTask: CreateTaskUseCase,
) {
    suspend fun apply(blocks: List<StudyBlockPayload>): StudyBlockApplyResult =
        apply(blocks, createTask)

    companion object {
        /** Maps accepted blocks to tasks; malformed or failing blocks are reported, never thrown. */
        suspend fun apply(
            blocks: List<StudyBlockPayload>,
            createTask: CreateTaskUseCase,
        ): StudyBlockApplyResult {
            var created = 0
            val failures = mutableListOf<String>()
            blocks.forEachIndexed { index, block ->
                val subject = block.subject.trim()
                val time = parseTime(block.startTime)
                val dayMillis = DateUtils.parseIsoDate(block.date)
                if (subject.isEmpty() || time == null || dayMillis == null) {
                    failures += "Skipped block ${index + 1}: invalid subject, date, or start time"
                    return@forEachIndexed
                }
                val normalizedTime = DateUtils.toTimeString(time.first, time.second)
                runCatching {
                    val now = System.currentTimeMillis()
                    createTask.execute(
                        Task(
                            id = UUID.randomUUID().toString(),
                            title = "$subject study block",
                            description = block.reason?.takeIf { it.isNotBlank() },
                            subjectId = null,
                            priority = Priority.MEDIUM,
                            dueDate = DateUtils.combineDateAndTime(dayMillis, normalizedTime),
                            dueTime = normalizedTime,
                            status = TaskStatus.PENDING,
                            category = null,
                            reminderAt = null,
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null,
                        )
                    )
                }.onSuccess { created += 1 }
                    .onFailure { failures += "Could not add $subject: ${it.message ?: "unknown error"}" }
            }
            return StudyBlockApplyResult(created, failures)
        }

        private fun parseTime(raw: String?): Pair<Int, Int>? {
            val parts = raw?.trim()?.split(":") ?: return null
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }
    }
}
