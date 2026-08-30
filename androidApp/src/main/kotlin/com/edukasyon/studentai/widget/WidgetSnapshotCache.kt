package com.edukasyon.studentai.widget

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the last rendered widget snapshot so cold starts and refreshes can paint
 * immediately while fresh DB data loads.
 */
object WidgetSnapshotCache {
    private const val PREFS_NAME = "studentai_widget_snapshot_cache"
    private const val TTL_MS = 5 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context, appWidgetId: Int): WidgetSnapshot? =
        readEntry(context, appWidgetId)?.snapshot

    fun readSavedAtMs(context: Context, appWidgetId: Int): Long? =
        readEntry(context, appWidgetId)?.savedAtMs

    private fun readEntry(context: Context, appWidgetId: Int): CacheEntry? {
        val raw = prefs(context).getString(key(appWidgetId), null) ?: return null
        return runCatching {
            val cached = json.decodeFromString<CachedWidgetSnapshot>(raw)
            // Reject cross-midnight cache hits — cachedDate is epoch day, so a midnight
            // boundary changes the day and invalidates yesterday's schedule/tasks.
            val todayEpochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
            if (cached.cachedDate != todayEpochDay) return null
            CacheEntry(cached.savedAtMs, cached.toSnapshot())
        }.getOrNull()
    }

    private data class CacheEntry(val savedAtMs: Long, val snapshot: WidgetSnapshot)

    fun write(context: Context, appWidgetId: Int, snapshot: WidgetSnapshot) {
        prefs(context).edit()
            .putString(key(appWidgetId), json.encodeToString(snapshot.toCached()))
            .apply()
    }

    fun invalidate(context: Context, appWidgetId: Int? = null) {
        val editor = prefs(context).edit()
        if (appWidgetId == null) {
            prefs(context).all.keys
                .filter { it.startsWith("snapshot_") }
                .forEach { editor.remove(it) }
        } else {
            editor.remove(key(appWidgetId))
        }
        editor.apply()
    }

    fun isFresh(snapshot: WidgetSnapshot, savedAtMs: Long): Boolean {
        return System.currentTimeMillis() - savedAtMs < TTL_MS
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "snapshot_$appWidgetId"
}

@Serializable
private data class CachedWidgetSnapshot(
    val savedAtMs: Long,
    val cachedDate: Long, // epoch day — invalidates cache across midnight boundaries
    val dayName: String,
    val monthName: String,
    val dayOfMonth: Int,
    val tasks: List<CachedTaskItem>,
    val schedule: List<CachedScheduleItem>,
    val calendarDays: List<CachedCalendarDay>,
    val calendarWeekdayLabels: List<String>,
    val moreCount: Int,
    val accentColorHex: String,
    val displayType: String,
    val widgetSize: String,
    val designPreset: String,
    val designColor1: String,
    val designColor2: String,
    val designColor3: String?,
    val currentTaskProgress: Float?,
    val currentTaskTimeLeft: String?
)

@Serializable
private data class CachedTaskItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val accentHex: String,
    val isHighlighted: Boolean
)

@Serializable
private data class CachedScheduleItem(
    val id: String,
    val title: String,
    val timeRange: String,
    val accentHex: String,
    val isCurrent: Boolean
)

@Serializable
private data class CachedCalendarDay(
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isCurrentMonth: Boolean,
    val dotColorHex: String?
)

private fun WidgetSnapshot.toCached(): CachedWidgetSnapshot = CachedWidgetSnapshot(
    savedAtMs = System.currentTimeMillis(),
    cachedDate = System.currentTimeMillis() / (24 * 60 * 60 * 1000),
    dayName = dayName,
    monthName = monthName,
    dayOfMonth = dayOfMonth,
    tasks = tasks.map {
        CachedTaskItem(it.id, it.title, it.subtitle, it.accentHex, it.isHighlighted)
    },
    schedule = schedule.map {
        CachedScheduleItem(it.id, it.title, it.timeRange, it.accentHex, it.isCurrent)
    },
    calendarDays = calendarDays.map {
        CachedCalendarDay(it.dayOfMonth, it.isToday, it.isCurrentMonth, it.dotColorHex)
    },
    calendarWeekdayLabels = calendarWeekdayLabels,
    moreCount = moreCount,
    accentColorHex = accentColorHex,
    displayType = displayType.name,
    widgetSize = widgetSize.name,
    designPreset = designPreset.name,
    designColor1 = designColors.color1,
    designColor2 = designColors.color2,
    designColor3 = designColors.color3,
    currentTaskProgress = currentTaskProgress,
    currentTaskTimeLeft = currentTaskTimeLeft
)

private fun CachedWidgetSnapshot.toSnapshot(): WidgetSnapshot {
    val preset = WidgetDesignPreset.entries.find { it.name == designPreset } ?: WidgetDesignPreset.MINIMAL
    val colors = WidgetDesignColors(designColor1, designColor2, designColor3)
    return WidgetSnapshot(
        dayName = dayName,
        monthName = monthName,
        dayOfMonth = dayOfMonth,
        tasks = tasks.map {
            WidgetTaskItem(it.id, it.title, it.subtitle, it.accentHex, it.isHighlighted)
        },
        schedule = schedule.map {
            WidgetScheduleItem(it.id, it.title, it.timeRange, it.accentHex, it.isCurrent)
        },
        calendarDays = calendarDays.map {
            WidgetCalendarDay(it.dayOfMonth, it.isToday, it.isCurrentMonth, it.dotColorHex)
        },
        calendarWeekdayLabels = calendarWeekdayLabels,
        moreCount = moreCount,
        accentColorHex = accentColorHex,
        isDarkTheme = false,
        displayType = WidgetDisplayType.entries.find { it.name == displayType } ?: WidgetDisplayType.TASKS,
        widgetSize = WidgetSize.entries.find { it.name == widgetSize } ?: WidgetSize.SMALL_2X2,
        designPreset = preset,
        designColors = colors,
        themeColors = widgetThemeFor(preset, colors),
        currentTaskProgress = currentTaskProgress,
        currentTaskTimeLeft = currentTaskTimeLeft
    )
}
