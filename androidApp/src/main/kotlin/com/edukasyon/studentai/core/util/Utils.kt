package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task

object GradeCalculator {
    fun calculateWeightedGrade(entries: List<GradeEntry>): Double {
        if (entries.isEmpty()) return 0.0
        val categoryGroups = entries.groupBy { it.category }
        var totalWeight = 0.0
        var weightedSum = 0.0
        categoryGroups.forEach { (_, categoryEntries) ->
            val categoryWeight = categoryEntries.first().weight
            val avgPercentage = categoryEntries.map { (it.score / it.maxScore) * 100 }.average()
            weightedSum += avgPercentage * categoryWeight
            totalWeight += categoryWeight
        }
        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }

    fun calculatePercentage(score: Double, maxScore: Double): Double =
        if (maxScore > 0) (score / maxScore) * 100 else 0.0
}

object ScheduleValidator {
    fun hasOverlap(a: ScheduleItem, b: ScheduleItem): Boolean {
        if (a.dayOfWeek != b.dayOfWeek || a.id == b.id) return false
        val aStart = parseTime(a.startTime)
        val aEnd = parseTime(a.endTime)
        val bStart = parseTime(b.startTime)
        val bEnd = parseTime(b.endTime)
        return aStart < bEnd && bStart < aEnd
    }

    fun findOverlaps(items: List<ScheduleItem>): List<Pair<ScheduleItem, ScheduleItem>> {
        val overlaps = mutableListOf<Pair<ScheduleItem, ScheduleItem>>()
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                if (hasOverlap(items[i], items[j])) overlaps.add(items[i] to items[j])
            }
        }
        return overlaps
    }

    private fun parseTime(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts.getOrElse(1) { "0" }.toInt()
    }
}

object TaskSorter {
    private val priorityOrder = mapOf(
        Priority.URGENT to 0, Priority.HIGH to 1, Priority.MEDIUM to 2, Priority.LOW to 3
    )

    fun sortByPriorityAndDueDate(tasks: List<Task>): List<Task> =
        tasks.sortedWith(compareBy({ priorityOrder[it.priority] ?: 4 }, { it.dueDate ?: Long.MAX_VALUE }))
}

object DateUtils {
    fun getTodayDayOfWeek(): DayOfWeek {
        val cal = java.util.Calendar.getInstance()
        return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> DayOfWeek.MONDAY
            java.util.Calendar.TUESDAY -> DayOfWeek.TUESDAY
            java.util.Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
            java.util.Calendar.THURSDAY -> DayOfWeek.THURSDAY
            java.util.Calendar.FRIDAY -> DayOfWeek.FRIDAY
            java.util.Calendar.SATURDAY -> DayOfWeek.SATURDAY
            else -> DayOfWeek.SUNDAY
        }
    }

    fun greeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun daysUntil(timestamp: Long): Long {
        val diff = timestamp - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    }

    fun formatCountdown(timestamp: Long): String {
        val days = daysUntil(timestamp)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            else -> "in $days days"
        }
    }
}
