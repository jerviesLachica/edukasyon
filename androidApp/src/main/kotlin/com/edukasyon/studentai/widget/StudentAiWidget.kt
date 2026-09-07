package com.edukasyon.studentai.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.edukasyon.studentai.MainActivity
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetActions {
    const val START_TAB_KEY = "start_tab"
    const val TASK_ID_KEY = "task_id"

    fun openApp(context: Context, tab: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, tab)
        }
    )

    fun openAppForTask(context: Context, taskId: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, "planner")
            putExtra(TASK_ID_KEY, taskId)
        }
    )

    fun toggleTaskComplete(taskId: String) = androidx.glance.appwidget.action.actionRunCallback<ToggleTaskActionCallback>(
        actionParametersOf(
            ToggleTaskActionCallback.TASK_ID_KEY to taskId
        )
    )
}

abstract class BaseStudentAiWidget(
    private val widgetSize: WidgetSize
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetPreferences.setWidgetSize(context, appWidgetId, widgetSize)

        val cached = WidgetDataProvider.loadCachedSnapshot(context, appWidgetId, widgetSize)
        
        // INSTANT LOAD: Show cached or skeleton immediately — no blocking work before provideContent
        val snapshot = cached ?: WidgetDataProvider.createSkeletonSnapshot(context, appWidgetId, widgetSize)

        val startTab = when (snapshot.displayType) {
            WidgetDisplayType.TASKS, WidgetDisplayType.COMBINED -> "planner"
            WidgetDisplayType.SCHEDULE -> "schedule"
        }
        val openAction = WidgetActions.openApp(context, startTab)
        provideContent {
            when (widgetSize) {
                WidgetSize.SMALL_2X2 -> SmallWidgetContent(snapshot, openAction)
                WidgetSize.TALL_2X3 -> TallWidgetContent(snapshot, openAction)
            }
        }

        // AFTER first paint: prewarm design bitmap in background so next update is instant
        WidgetDataProvider.prewarmBackground(context, snapshot)

        // Detect stale cache: different day, or cache too old (>30 min), or no cache at all.
        // When the day changed we must reload immediately so the widget doesn't show yesterday's
        // date; the background worker is fire-and-forget and may be killed by the OS.
        val dateChanged = cached != null && !isSnapshotForToday(cached)
        val needsRefresh = cached == null || dateChanged || shouldRefreshCachedSnapshot(context, appWidgetId)

        if (needsRefresh) {
            if (dateChanged && cached != null) {
                // Day rolled over: load fresh snapshot synchronously and re-render
                // so the widget shows today's date without waiting for a background worker.
                val fresh = WidgetDataProvider.loadSnapshotFresh(context, appWidgetId, widgetSize)
                WidgetUpdater.refreshAll(context)
            } else {
                WidgetUpdater.notifyDataChanged(context)
            }
        }
    }

    private fun shouldRefreshCachedSnapshot(context: Context, appWidgetId: Int): Boolean {
            val savedAt = WidgetSnapshotCache.readSavedAtMs(context, appWidgetId) ?: return true
            return System.currentTimeMillis() - savedAt > 30 * 60_000L  // 30 minutes
        }

    /** Returns true if the cached snapshot was written today (same day/month/year). */
    private fun isSnapshotForToday(snapshot: WidgetSnapshot): Boolean {
        val now = java.util.Calendar.getInstance()
        val currentDay = now.get(java.util.Calendar.DAY_OF_MONTH)
        val currentMonth = now.get(java.util.Calendar.MONTH) // 0-based, matches SimpleDateFormat "MMM" intent
        val currentYear = now.get(java.util.Calendar.YEAR)
        val dayName = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(now.time)
        return snapshot.dayOfMonth == currentDay && snapshot.monthName == java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(now.time)
    }
}

class StudentAiWidget2x2 : BaseStudentAiWidget(WidgetSize.SMALL_2X2)

class StudentAiWidget2x3 : BaseStudentAiWidget(WidgetSize.TALL_2X3)

class StudentAiWidget2x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudentAiWidget2x2()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            WidgetPreferences.remove(context, it)
            WidgetSnapshotCache.invalidate(context, it)
        }
    }
}

class StudentAiWidget2x3Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StudentAiWidget2x3()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            WidgetPreferences.remove(context, it)
            WidgetSnapshotCache.invalidate(context, it)
        }
    }
}