package com.edukasyon.studentai.core.util

import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Priority
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.Task
import java.util.Calendar

object GradeCalculator {
    fun calculateWeightedGrade(entries: List<GradeEntry>): Double {
        if (entries.isEmpty()) return 0.0
        val categoryGroups = entries.groupBy { it.category }
        var totalWeight = 0.0
        var weightedSum = 0.0
        categoryGroups.forEach { (_, categoryEntries) ->
            val categoryWeight = categoryEntries.first().weight
            val validPercentages = categoryEntries
                .filter { it.maxScore > 0 }
                .map { (it.score / it.maxScore) * 100 }
            val avgPercentage = if (validPercentages.isNotEmpty()) validPercentages.average() else 0.0
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
        val cal = Calendar.getInstance()
        cal.timeInMillis = System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayMidnight = cal.timeInMillis

        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dueMidnight = cal.timeInMillis

        val diff = dueMidnight - todayMidnight
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

    fun formatTime12h(time: String): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
        val minute = parts.getOrElse(1) { "00" }.padStart(2, '0')
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$displayHour:$minute $amPm"
    }

    fun formatTimeRange(startTime: String, endTime: String): String =
        "${formatTime12h(startTime)} - ${formatTime12h(endTime)}"

    fun startOfDay(timestamp: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun combineDateAndTime(dateMillis: Long, time: String): Long {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 23
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 59
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun effectiveDueMillis(dueDate: Long?, dueTime: String?): Long? {
        if (dueDate == null) return null
        return if (dueTime != null) combineDateAndTime(dueDate, dueTime) else dueDate
    }

    fun toTimeString(hour: Int, minute: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    fun toTimeString(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return toTimeString(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    fun formatFullDate(timestamp: Long): String {
        val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val formatter = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }

    fun formatDueDateTime(dueDate: Long?, dueTime: String?): String? {
        if (dueDate == null) return null
        val datePart = formatFullDate(dueDate)
        val timePart = dueTime?.let { formatTime12h(it) }
        return if (timePart != null) "$datePart at $timePart" else datePart
    }

    fun formatReminderAt(timestamp: Long): String =
        "${formatFullDate(timestamp)} at ${formatTime12h(toTimeString(timestamp))}"

    /** Default reminder: one day before due at 9:00 AM, or same-day morning / 1 hour before if sooner. */
    fun defaultReminderAt(dueMillis: Long): Long {
        val dayBeforeMorning = java.util.Calendar.getInstance().apply {
            timeInMillis = dueMillis
            add(java.util.Calendar.DAY_OF_MONTH, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (dayBeforeMorning.timeInMillis > System.currentTimeMillis()) {
            return dayBeforeMorning.timeInMillis
        }
        val sameDayMorning = java.util.Calendar.getInstance().apply {
            timeInMillis = dueMillis
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (sameDayMorning.timeInMillis > System.currentTimeMillis()) {
            return sameDayMorning.timeInMillis
        }
        return (dueMillis - 60 * 60 * 1000L).coerceAtLeast(System.currentTimeMillis() + 1000)
    }

    fun tomorrowStartOfDay(): Long = startOfDay(System.currentTimeMillis() + 86_400_000L)

    /** Parse YYYY-MM-DD to start-of-day millis, or null if invalid. */
    fun parseIsoDate(isoDate: String): Long? {
        val match = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(isoDate.trim()) ?: return null
        val (year, month, day) = match.destructured
        return runCatching {
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year.toInt())
                set(java.util.Calendar.MONTH, month.toInt() - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day.toInt())
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }.getOrNull()
    }

    fun subtractDays(timestamp: Long, days: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = timestamp
            add(java.util.Calendar.DAY_OF_MONTH, -days.coerceAtLeast(0))
        }
        return cal.timeInMillis
    }
}
