package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.FocusBlock
import com.edukasyon.studentai.domain.model.FocusBlockType
import com.edukasyon.studentai.domain.model.FocusPlan

object FocusPlanValidator {
    private const val MIN_TOTAL = 15
    private const val MAX_TOTAL = 240
    private const val MAX_BLOCKS = 12

    fun validate(plan: FocusPlan): Result<FocusPlan> {
        if (plan.totalMinutes !in MIN_TOTAL..MAX_TOTAL) {
            return Result.failure(IllegalArgumentException("Total session must be between $MIN_TOTAL and $MAX_TOTAL minutes."))
        }
        if (plan.blocks.isEmpty()) {
            return Result.failure(IllegalArgumentException("Focus plan must include at least one block."))
        }
        if (plan.blocks.size > MAX_BLOCKS) {
            return Result.failure(IllegalArgumentException("Too many blocks in the plan."))
        }

        val normalized = plan.blocks.mapNotNull { normalizeBlock(it, plan.totalMinutes) }
        if (normalized.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid focus blocks in the plan."))
        }

        val sorted = normalized.sortedBy { it.startMinute }
        for (block in sorted) {
            if (block.endMinute > plan.totalMinutes) {
                return Result.failure(IllegalArgumentException("Block \"${block.activity}\" exceeds session length."))
            }
        }

        return Result.success(plan.copy(blocks = sorted))
    }

    private fun normalizeBlock(block: FocusBlock, totalMinutes: Int): FocusBlock? {
        val activity = block.activity.trim()
        if (activity.isEmpty()) return null
        val start = block.startMinute.coerceIn(0, totalMinutes - 1)
        val end = block.endMinute.coerceIn(start + 1, totalMinutes)
        if (end <= start) return null
        return block.copy(
            startMinute = start,
            endMinute = end,
            activity = activity.take(120),
            type = block.type,
        )
    }

    fun blockTypeLabel(type: FocusBlockType): String = when (type) {
        FocusBlockType.STUDY -> "Study"
        FocusBlockType.BREAK -> "Break"
        FocusBlockType.REVIEW -> "Review"
    }
}
