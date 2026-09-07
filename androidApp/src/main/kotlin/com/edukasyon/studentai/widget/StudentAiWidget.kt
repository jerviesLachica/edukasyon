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
        
        // WIDGET_INIT: Determine initial snapshot
        // If cache exists, use it. If not (new widget), try eager load from DB.
        val snapshot = if (cached != null) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_LOCAL_SNAPSHOT_READ: Using cached snapshot")
            cached
        } else {
            // First render with no cache: eagerly load fresh data from DB within this suspension.
            // This avoids WorkManager latency and shows real data immediately if available.
            android.util.Log.i("WidgetLifecycle", "WIDGET_LOCAL_SNAPSHOT_READ: Cache miss, loading fresh from DB")
            try {
                val fresh = WidgetDataProvider.loadSnapshotFresh(context, appWidgetId, widgetSize)
                if (fresh.tasks.isNotEmpty() || fresh.schedule.isNotEmpty()) {
                    android.util.Log.i("WidgetLifecycle", "WIDGET_FIRST_RENDER: Fresh data loaded (${fresh.tasks.size} tasks, ${fresh.schedule.size} schedule)")
                    fresh
                } else {
                    android.util.Log.i("WidgetLifecycle", "WIDGET_FIRST_RENDER: Fresh load returned empty, showing skeleton")
                    WidgetDataProvider.createSkeletonSnapshot(context, appWidgetId, widgetSize)
                }
            } catch (e: Exception) {
                android.util.Log.e("WidgetLifecycle", "WIDGET_FIRST_RENDER: Fresh load failed, showing skeleton", e)
                WidgetDataProvider.createSkeletonSnapshot(context, appWidgetId, widgetSize)
            }
        }

        val startTab = when (snapshot.displayType) {
            WidgetDisplayType.TASKS, WidgetDisplayType.COMBINED -> "planner"
            WidgetDisplayType.SCHEDULE -> "schedule"
        }
        val openAction = WidgetActions.openApp(context, startTab)
        
        android.util.Log.i("WidgetLifecycle", "WIDGET_FIRST_RENDER: Rendering widget (isLoading=${snapshot.isLoading})")
        provideContent {
            when (widgetSize) {
                WidgetSize.SMALL_2X2 -> SmallWidgetContent(snapshot, openAction)
                WidgetSize.TALL_2X3 -> TallWidgetContent(snapshot, openAction)
            }
        }

        // AFTER first paint: prewarm design bitmap in background so next update is instant
        WidgetDataProvider.prewarmBackground(context, snapshot)

        // Detect stale cache: different day, or cache too old (>30 min), or no cache at all.
        val dateChanged = cached != null && !isSnapshotForToday(cached)
        val needsBackgroundSync = cached == null || dateChanged || shouldRefreshCachedSnapshot(context, appWidgetId)

        if (needsBackgroundSync) {
            if (dateChanged && cached != null) {
                android.util.Log.i("WidgetLifecycle", "WIDGET_REFRESH_REASON: Date changed, loading fresh")
                val fresh = WidgetDataProvider.loadSnapshotFresh(context, appWidgetId, widgetSize)
                WidgetUpdater.refreshAll(context)
            } else {
                android.util.Log.i("WidgetLifecycle", "WIDGET_BACKGROUND_SYNC_START: Enqueuing WorkManager refresh")
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