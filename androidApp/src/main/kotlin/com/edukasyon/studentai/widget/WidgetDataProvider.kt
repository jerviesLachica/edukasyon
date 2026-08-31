package com.edukasyon.studentai.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.TaskSorter
import com.edukasyon.studentai.data.mapper.toDomain
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.ui.theme.parseHexColor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetDataProvider {

    /** Loads from Room, writes cache, and pre-warms the design bitmap off the UI path. */
    suspend fun loadSnapshotFresh(
        context: Context,
        appWidgetId: Int,
        widgetSize: WidgetSize
    ): WidgetSnapshot {
        val snapshot = buildSnapshot(context, appWidgetId, widgetSize)
        WidgetSnapshotCache.write(context, appWidgetId, snapshot)
        prewarmBackground(context, snapshot)
        return snapshot
    }

    /** Fast path for first paint after process death — no DB or DataStore I/O. */
    fun loadCachedSnapshot(context: Context, appWidgetId: Int, widgetSize: WidgetSize): WidgetSnapshot? {
        return WidgetSnapshotCache.read(context, appWidgetId)?.takeIf { cached ->
            cached.widgetSize == widgetSize
        }
    }

    /**
     * Creates a skeleton/placeholder snapshot that loads instantly.
     * Shows empty state while fresh data loads in background.
     * Preserves user design preferences without blocking on DB queries.
     */
    fun createSkeletonSnapshot(
        context: Context,
        appWidgetId: Int,
        widgetSize: WidgetSize
    ): WidgetSnapshot {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            val userPreferences = entryPoint.userPreferences()

            val defaultType = when (widgetSize) {
                WidgetSize.SMALL_2X2 -> WidgetDisplayType.TASKS
                WidgetSize.TALL_2X3 -> WidgetDisplayType.COMBINED
            }
            val displayType = WidgetPreferences.getDisplayType(context, appWidgetId, defaultType)

            val widgetAccent = WidgetPreferences.getAccentColorHex(context, appWidgetId)
            val accentHex = widgetAccent
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

            // Empty placeholders — UI shows loading shimmer
            val taskItems = (0 until taskLimit).map { index ->
                WidgetTaskItem(
                    id = "skeleton_task_$index",
                    title = "Loading...",
                    subtitle = "",
                    accentHex = accentForIndex(index, accentHex),
                    isHighlighted = index == 0
                )
            }

            val scheduleItems = (0 until scheduleLimit).map { index ->
                WidgetScheduleItem(
                    id = "skeleton_schedule_$index",
                    title = "Loading...",
                    timeRange = "",
                    accentHex = accentForIndex(index, accentHex),
                    isCurrent = false
                )
            }

            return WidgetSnapshot(
                dayName = dayName,
                monthName = monthName,
                dayOfMonth = dayOfMonth,
                tasks = taskItems,
                schedule = scheduleItems,
                calendarDays = emptyList(),
                calendarWeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
                moreCount = 0,
                accentColorHex = accentHex,
                isDarkTheme = false,
                displayType = displayType,
                widgetSize = widgetSize,
                designPreset = designPreset,
                designColors = designColors,
                themeColors = themeColors,
                currentTaskProgress = null,
                currentTaskTimeLeft = null
            )
        } catch (e: Exception) {
            // Fallback if preferences fail to load
            return createEmptySnapshot(widgetSize)
        }
    }

    private fun createEmptySnapshot(widgetSize: WidgetSize): WidgetSnapshot {
        val today = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(today.time)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(today.time)
        return WidgetSnapshot(
            dayName = dayName,
            monthName = monthName,
            dayOfMonth = today.get(Calendar.DAY_OF_MONTH),
            tasks = emptyList(),
            schedule = emptyList(),
            calendarDays = emptyList(),
            calendarWeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
            moreCount = 0,
            accentColorHex = UserPreferences.DEFAULT_PRIMARY_COLOR,
            isDarkTheme = false,
            displayType = when (widgetSize) {
                WidgetSize.SMALL_2X2 -> WidgetDisplayType.TASKS
                WidgetSize.TALL_2X3 -> WidgetDisplayType.COMBINED
            },
            widgetSize = widgetSize,
            designPreset = WidgetDesignPreset.MINIMAL,
            designColors = WidgetDesignPreset.MINIMAL.defaultColors(),
            themeColors = WidgetThemeColors(
                onSurface = Color(0xFF1A1A1A),
                muted = Color(0xFF6B7280),
                card = Color.White,
                isLightBackground = true
            ),
            currentTaskProgress = null,
            currentTaskTimeLeft = null
        )
    }

    private suspend fun buildSnapshot(
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

        val widgetAccent = WidgetPreferences.getAccentColorHex(context, appWidgetId)
        val accentHex = widgetAccent
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
        val needsCalendar = widgetSize == WidgetSize.TALL_2X3 &&
            displayType == WidgetDisplayType.COMBINED

        val todayDow = DateUtils.getTodayDayOfWeek()
        val fetchExtra = if (needsCalendar) 1 else 2
        val upcomingTasks = TaskSorter.sortByPriorityAndDueDate(
            taskDao.getUpcoming(taskLimit + fetchExtra).map { it.toDomain() }
        )
        val todaySchedule = scheduleDao.getByDay(todayDow.name)
            .map { it.toDomain() }
            .sortedBy { it.startTime }

        val calendarDays = if (needsCalendar) {
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
            buildMonthCalendar(today, tasksInMonth, eventsInMonth, accentHex)
        } else {
            emptyList()
        }

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
            isDarkTheme = false,
            displayType = displayType,
            widgetSize = widgetSize,
            designPreset = designPreset,
            designColors = designColors,
            themeColors = themeColors,
            currentTaskProgress = progressInfo?.first,
            currentTaskTimeLeft = progressInfo?.second
        )
    }

    /** Renders the design bitmap ahead of Glance composition so first paint never blocks on drawing. */
    internal fun prewarmBackground(context: Context, snapshot: WidgetSnapshot) {
        if (snapshot.designPreset == WidgetDesignPreset.MINIMAL) return
        val (widthDp, heightDp) = backgroundSizeDp(snapshot.widgetSize)
        WidgetBackgroundGenerator.getBitmap(
            context = context,
            preset = snapshot.designPreset,
            colors = snapshot.designColors,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }

    internal fun backgroundSizeDp(widgetSize: WidgetSize): Pair<Int, Int> = when (widgetSize) {
        WidgetSize.SMALL_2X2 -> 160 to 160
        WidgetSize.TALL_2X3 -> 160 to 240
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
