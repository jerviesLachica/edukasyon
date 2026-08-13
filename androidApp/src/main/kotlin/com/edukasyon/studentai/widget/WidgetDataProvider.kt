package com.edukasyon.studentai.widget

import android.content.Context
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.TaskSorter
import com.edukasyon.studentai.data.mapper.toDomain
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.theme.parseHexColor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetDataProvider {

    suspend fun loadSnapshot(
        context: Context,
        appWidgetId: Int,
        widgetSize: WidgetSize
    ): WidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val taskDao = entryPoint.taskDao()
        val scheduleDao = entryPoint.scheduleDao()
        val calendarEventDao = entryPoint.calendarEventDao()
        val userPreferences = entryPoint.userPreferences()

        val defaultType = when (widgetSize) {
            WidgetSize.SMALL_2X2 -> WidgetDisplayType.TASKS
            WidgetSize.TALL_2X3 -> WidgetDisplayType.COMBINED
        }
        val displayType = WidgetPreferences.getDisplayType(context, appWidgetId, defaultType)
        val themeMode = userPreferences.themeMode.first()
        val isDarkTheme = when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> {
                (context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }

        val accentHex = WidgetPreferences.getAccentColorHex(context, appWidgetId)
            ?: userPreferences.primaryColorHex.first().takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_PRIMARY_COLOR

        val designPreset = WidgetPreferences.getDesignPreset(context, appWidgetId)
        val designColors = WidgetPreferences.getResolvedDesignColors(context, appWidgetId)
        val themeColors = widgetThemeFor(designPreset, designColors)

        val today = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(today.time)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(today.time)
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)

        val taskLimit = when (widgetSize) {
            WidgetSize.SMALL_2X2 -> 3
            WidgetSize.TALL_2X3 -> 5
        }
        val scheduleLimit = when (widgetSize) {
            WidgetSize.SMALL_2X2 -> 4
            WidgetSize.TALL_2X3 -> 6
        }

        val todayDow = DateUtils.getTodayDayOfWeek()
        val upcomingTasks = TaskSorter.sortByPriorityAndDueDate(
            taskDao.getUpcoming(taskLimit + 4).map { it.toDomain() }
        )
        val todaySchedule = scheduleDao.getByDay(todayDow.name)
            .map { it.toDomain() }
            .sortedBy { it.startTime }

        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthEnd = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val tasksInMonth = taskDao.getDueInRange(monthStart.timeInMillis, monthEnd.timeInMillis)
        val eventsInMonth = calendarEventDao.getInRange(monthStart.timeInMillis, monthEnd.timeInMillis)

        val taskItems = upcomingTasks.take(taskLimit).mapIndexed { index, task ->
            WidgetTaskItem(
                id = task.id,
                title = task.title,
                subtitle = formatTaskSubtitle(task.dueDate, task.dueTime),
                accentHex = accentForIndex(index, accentHex),
                isHighlighted = index == 0
            )
        }

        val currentScheduleId = findCurrentScheduleId(todaySchedule, today)
        val scheduleItems = todaySchedule.take(scheduleLimit).mapIndexed { index, item ->
            WidgetScheduleItem(
                id = item.id,
                title = item.subjectName,
                timeRange = DateUtils.formatTimeRange(item.startTime, item.endTime),
                accentHex = item.colorHex.takeIf { parseHexColor(it) != null } ?: accentForIndex(index, accentHex),
                isCurrent = item.id == currentScheduleId
            )
        }

        val calendarDays = buildMonthCalendar(today, tasksInMonth, eventsInMonth, accentHex)
        val moreCount = when (displayType) {
            WidgetDisplayType.TASKS -> (upcomingTasks.size - taskLimit).coerceAtLeast(0)
            WidgetDisplayType.SCHEDULE -> (todaySchedule.size - scheduleLimit).coerceAtLeast(0)
            WidgetDisplayType.COMBINED -> (upcomingTasks.size - 3).coerceAtLeast(0)
        }

        val currentSchedule = todaySchedule.find { it.id == currentScheduleId }
        val progressInfo = currentSchedule?.let { computeProgress(it, today) }

        return WidgetSnapshot(
            dayName = dayName,
            monthName = monthName,
            dayOfMonth = dayOfMonth,
            tasks = taskItems,
            schedule = scheduleItems,
            calendarDays = calendarDays,
            calendarWeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
            moreCount = moreCount,
            accentColorHex = accentHex,
            isDarkTheme = isDarkTheme,
            displayType = displayType,
            widgetSize = widgetSize,
            designPreset = designPreset,
            designColors = designColors,
            themeColors = themeColors,
            currentTaskProgress = progressInfo?.first,
            currentTaskTimeLeft = progressInfo?.second
        )
    }

    private fun formatTaskSubtitle(dueDate: Long?, dueTime: String?): String {
        if (dueDate == null) return "No due date"
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val timeLabel = dueTime?.let { DateUtils.formatTime12h(it) }
        return when {
            isToday && timeLabel != null -> "Until $timeLabel"
            isToday -> "Due today"
            timeLabel != null -> "At $timeLabel"
            else -> DateUtils.formatCountdown(dueDate)
        }
    }

    private fun findCurrentScheduleId(
        schedule: List<com.edukasyon.studentai.domain.model.ScheduleItem>,
        now: Calendar
    ): String? {
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return schedule.firstOrNull { item ->
            val start = parseMinutes(item.startTime)
            val end = parseMinutes(item.endTime)
            nowMinutes in start until end
        }?.id
    }

    private fun computeProgress(
        item: com.edukasyon.studentai.domain.model.ScheduleItem,
        now: Calendar
    ): Pair<Float, String>? {
        val start = parseMinutes(item.startTime)
        val end = parseMinutes(item.endTime)
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (nowMinutes !in start until end) return null
        val total = (end - start).coerceAtLeast(1)
        val elapsed = nowMinutes - start
        val remainingSec = (end - nowMinutes) * 60
        val progress = elapsed.toFloat() / total.toFloat()
        val timeLeft = when {
            remainingSec >= 3600 -> "${remainingSec / 3600} hr left"
            remainingSec >= 60 -> "${remainingSec / 60} min left"
            else -> "$remainingSec sec left"
        }
        return progress to timeLeft
    }

    private fun parseMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun accentForIndex(index: Int, primary: String): String {
        val palette = WidgetAccentPresets.presets.map { it.first }
        return palette.getOrElse(index % palette.size) { primary }
    }

    private fun buildMonthCalendar(
        today: Calendar,
        tasks: List<com.edukasyon.studentai.data.local.entity.TaskEntity>,
        events: List<com.edukasyon.studentai.data.local.entity.CalendarEventEntity>,
        accentHex: String
    ): List<WidgetCalendarDay> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val taskDays = tasks.mapNotNull { task ->
            task.dueDate?.let { due ->
                Calendar.getInstance().apply { timeInMillis = due }.get(Calendar.DAY_OF_MONTH)
            }
        }.toSet()
        val eventDays = events.map {
            Calendar.getInstance().apply { timeInMillis = it.startAt }.get(Calendar.DAY_OF_MONTH)
        }.toSet()

        val days = mutableListOf<WidgetCalendarDay>()
        repeat(firstDayOfWeek) {
            days.add(WidgetCalendarDay(0, false, false, null))
        }
        for (day in 1..daysInMonth) {
            val isToday = day == today.get(Calendar.DAY_OF_MONTH)
            val dot = when {
                isToday -> accentHex
                day in taskDays -> WidgetAccentPresets.presets[0].first
                day in eventDays -> WidgetAccentPresets.presets[2].first
                else -> null
            }
            days.add(WidgetCalendarDay(day, isToday, true, dot))
        }
        return days
    }
}
