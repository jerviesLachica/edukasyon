package com.edukasyon.studentai.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.TaskSorter
import com.edukasyon.studentai.data.mapper.toDomain
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.ui.theme.parseHexColor
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pure deterministic builder. Converts Room entities into a widget-ready
 * [WidgetSnapshot]. No side effects, no I/O except Room reads via DAOs.
 * All logic for filtering, sorting, and composing the snapshot lives here.
 */
object WidgetSnapshotBuilder {

    /**
     * Builds a fresh widget snapshot from the local database.
     *
     * Rules:
     * - Shows today + upcoming + overdue tasks (due >= today OR no due date OR overdue).
     * - Overdue tasks sort first, most overdue on top, with an "Overdue" subtitle.
     * - Preserves recently completed tasks (checked state visible).
     * - Pins recently toggled tasks so uncheck doesn't vanish them.
     * - Never shows skeleton placeholders unless explicitly requested.
     * - Returns isLoading = false always; real data or empty lists.
     */
    suspend fun buildSnapshot(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfig
    ): WidgetSnapshot {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val taskDao = entryPoint.taskDao()
        val scheduleDao = entryPoint.scheduleDao()
        val calendarEventDao = entryPoint.calendarEventDao()
        val userPreferences = entryPoint.userPreferences()

        val widgetSize = config.widgetSize
        val displayType = config.displayType

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

        val today = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(today.time)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(today.time)
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)

        // Midnight today in milliseconds — used to filter overdue.
        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Wide candidate pool, then sort by priority + due date.
        val candidatePoolSize = 50
        val recentCompletedWindowMs = 30 * 60_000L
        val recentSinceMs = System.currentTimeMillis() - recentCompletedWindowMs

        // Run DB queries in parallel.
        val (upcomingEntities, recentEntities, scheduleEntities, monthTasks, monthEvents) = coroutineScope {
            val u = async { taskDao.getUpcoming(candidatePoolSize) }
            val r = async {
                runCatching { taskDao.getRecentlyCompleted(recentSinceMs, taskLimit) }
                    .getOrDefault(emptyList())
            }
            val s = async { scheduleDao.getByDay(DateUtils.getTodayDayOfWeek().name) }
            val mt = async {
                if (!needsCalendar) emptyList()
                else runCatching {
                    taskDao.getDueInRange(monthStartMs(today), monthEndMs(today))
                }.getOrDefault(emptyList())
            }
            val me = async {
                if (!needsCalendar) emptyList()
                else runCatching {
                    calendarEventDao.getInRange(monthStartMs(today), monthEndMs(today))
                }.getOrDefault(emptyList())
            }
            SnapshotQueries(u.await(), r.await(), s.await(), mt.await(), me.await())
        }

        // Theme/colors.
        val accentHex = config.accentHex
            ?: userPreferences.primaryColorHex.first().takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_PRIMARY_COLOR

        val designPreset = config.designPreset
        val designColors = config.designColors
        val themeColors = widgetThemeFor(designPreset, designColors)

        // ---- TASKS PIPELINE ----
        val upcomingTasks = TaskSorter.sortByPriorityAndDueDate(
            upcomingEntities.map { it.toDomain() }
        )

        // All pending tasks are visible, including overdue ones (most overdue
        // on top), then the existing priority + due-date order for the rest.
        // Overdue tasks must never vanish — a pending task with no row means
        // no checkbox to tap.
        val (overdueFirst, restUpcoming) = upcomingTasks.partition { it.dueDate != null && it.dueDate < todayStartMs }
        val upcomingVisible = overdueFirst.sortedBy { it.dueDate } + restUpcoming

        // Recently checked tasks stay visible briefly.
        val recentCompleted = recentEntities.map { it.toDomain() }
        val recentCompletedIds = recentCompleted.map { it.id }.toSet()

        // Pinned tasks (recently toggled via widget) bypass overdue filter.
        val pinnedIds = WidgetToggleTracker.recentIds(context)
            .filter { it !in recentCompletedIds }
        val pinned = if (pinnedIds.isEmpty()) {
            emptyList()
        } else {
            runCatching { taskDao.getByIds(pinnedIds).map { it.toDomain() } }
                .getOrDefault(emptyList())
                .filter { it.status != com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED }
        }
        val pinnedById = pinned.associateBy { it.id }
        val pinnedOrdered = pinnedIds.mapNotNull { pinnedById[it] }

        // Remaining upcoming (excluding recent completed + pinned).
        val upcomingUnpinned = upcomingVisible.filter { it.id !in recentCompletedIds && it.id !in pinnedById }

        // Build task items: recent completed (checked) + pinned (with real status) + upcoming.
        val taskItems = (recentCompleted.map { it to true } +
            pinnedOrdered.map { it to (it.status == com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED) } +
            upcomingUnpinned.map { it to false })
            .take(taskLimit)
            .mapIndexed { index, (task, completed) ->
                WidgetTaskItem(
                    id = task.id,
                    title = task.title,
                    subtitle = formatTaskSubtitle(
                        task.dueDate,
                        task.dueTime,
                        isOverdue = !completed && task.dueDate != null && task.dueDate < todayStartMs
                    ),
                    accentHex = accentForIndex(index, accentHex),
                    isHighlighted = index == 0 && !completed,
                    isCompleted = completed
                )
            }

        val shownUpcoming = taskItems.count { !it.isCompleted }
        val hiddenUpcoming = (upcomingVisible.size - shownUpcoming).coerceAtLeast(0)

        // ---- SCHEDULE PIPELINE ----
        val todaySchedule = scheduleEntities
            .map { it.toDomain() }
            .sortedBy { it.startTime }

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

        // ---- CALENDAR (only for TALL_2X3 COMBINED) ----
        val calendarDays = if (needsCalendar) {
            buildMonthCalendar(today, monthTasks, monthEvents, accentHex)
        } else {
            emptyList()
        }

        val moreCount = when (displayType) {
            WidgetDisplayType.TASKS -> hiddenUpcoming
            WidgetDisplayType.SCHEDULE -> (todaySchedule.size - scheduleLimit).coerceAtLeast(0)
            WidgetDisplayType.COMBINED -> hiddenUpcoming
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
            currentTaskTimeLeft = progressInfo?.second,
            isLoading = false
        )
    }

    /** Creates an empty snapshot with real theme (never "Loading..." placeholders). */
    suspend fun buildEmptySnapshot(context: Context, appWidgetId: Int, config: WidgetConfig): WidgetSnapshot {
        val today = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(today.time)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(today.time)
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)

        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val userPreferences = entryPoint.userPreferences()

        val accentHex = config.accentHex
            ?: userPreferences.primaryColorHex.first().takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_PRIMARY_COLOR

        val designPreset = config.designPreset
        val designColors = config.designColors
        val themeColors = widgetThemeFor(designPreset, designColors)

        val taskLimit = when (config.widgetSize) {
            WidgetSize.SMALL_2X2 -> 3
            WidgetSize.TALL_2X3 -> 5
        }
        val scheduleLimit = when (config.widgetSize) {
            WidgetSize.SMALL_2X2 -> 4
            WidgetSize.TALL_2X3 -> 6
        }

        return WidgetSnapshot(
            dayName = dayName,
            monthName = monthName,
            dayOfMonth = dayOfMonth,
            tasks = emptyList(),
            schedule = emptyList(),
            calendarDays = emptyList(),
            calendarWeekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S"),
            moreCount = 0,
            accentColorHex = accentHex,
            isDarkTheme = false,
            displayType = config.displayType,
            widgetSize = config.widgetSize,
            designPreset = designPreset,
            designColors = config.designColors,
            themeColors = themeColors,
            currentTaskProgress = null,
            currentTaskTimeLeft = null,
            isLoading = false
        )
    }

    /** Skeleton with themed colors but empty data (only used if explicitly requested). */
    suspend fun buildSkeletonSnapshot(context: Context, appWidgetId: Int, config: WidgetConfig): WidgetSnapshot {
        val today = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(today.time)
        val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(today.time)
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)

        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val userPreferences = entryPoint.userPreferences()

        val accentHex = config.accentHex
            ?: userPreferences.primaryColorHex.first().takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_PRIMARY_COLOR

        val designPreset = config.designPreset
        val designColors = config.designColors
        val themeColors = widgetThemeFor(designPreset, designColors)

        val taskLimit = when (config.widgetSize) {
            WidgetSize.SMALL_2X2 -> 3
            WidgetSize.TALL_2X3 -> 5
        }
        val scheduleLimit = when (config.widgetSize) {
            WidgetSize.SMALL_2X2 -> 4
            WidgetSize.TALL_2X3 -> 6
        }

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
            displayType = config.displayType,
            widgetSize = config.widgetSize,
            designPreset = designPreset,
            designColors = designColors,
            themeColors = themeColors,
            currentTaskProgress = null,
            currentTaskTimeLeft = null,
            isLoading = true
        )
    }

    // ===== Private helpers =====

    private data class SnapshotQueries(
        val upcoming: List<com.edukasyon.studentai.data.local.entity.TaskEntity>,
        val recent: List<com.edukasyon.studentai.data.local.entity.TaskEntity>,
        val schedule: List<com.edukasyon.studentai.data.local.entity.ScheduleItemEntity>,
        val monthTasks: List<com.edukasyon.studentai.data.local.entity.TaskEntity>,
        val monthEvents: List<com.edukasyon.studentai.data.local.entity.CalendarEventEntity>,
    )

    private fun monthStartMs(today: Calendar): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun monthEndMs(today: Calendar): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    private fun formatTaskSubtitle(dueDate: Long?, dueTime: String?, isOverdue: Boolean = false): String {
        if (dueDate == null) return "No due date"
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        val today = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val timeLabel = dueTime?.let { DateUtils.formatTime12h(it) }
        if (isOverdue) return "Overdue" + (timeLabel?.let { " • $it" } ?: "")
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
        val hours = remainingSec / 3600
        val mins = (remainingSec % 3600) / 60
        val secs = remainingSec % 60
        val timeLeft = when {
            hours > 0 -> "${hours}h ${mins}m left"
            mins > 0 -> "${mins}m ${secs}s left"
            else -> "$secs sec left"
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