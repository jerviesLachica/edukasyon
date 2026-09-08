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
        android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_START: provideGlance called")
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetPreferences.setWidgetSize(context, appWidgetId, widgetSize)

        android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_DATA_READ: Reading cached snapshot for widget $appWidgetId")
        val cached = WidgetDataProvider.loadCachedSnapshot(context, appWidgetId, widgetSize)

        // WIDGET_INIT: Determine initial snapshot
        // If cache exists AND its design matches current preferences, use it (fast path).
        // If design changed (user just reconfigured), ignore cache and load fresh.
        // If no cache (new widget or first render after cache invalidation),
        // eagerly try to load fresh data from DB. If DB returns empty/skeleton, still render the
        // skeleton now — then enqueue a background refresh so the widget never stays empty.
        val snapshot = if (cached != null && isSnapshotDesignCurrent(context, appWidgetId, cached)) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_LOCAL_SNAPSHOT_READ: Using cached snapshot (design current)")
            cached
        } else {
            if (cached != null) {
                android.util.Log.i("WidgetLifecycle", "WIDGET_LOCAL_SNAPSHOT_READ: Cache stale (design changed), loading fresh")
            } else {
                android.util.Log.i("WidgetLifecycle", "WIDGET_LOCAL_SNAPSHOT_READ: Cache miss, loading fresh from DB")
            }
            try {
                WidgetDataProvider.loadSnapshotFresh(context, appWidgetId, widgetSize)
                    .also {
                        android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_DATA_READ: Fresh load (${it.tasks.size} tasks, ${it.schedule.size} schedule)")
                    }
            } catch (e: Exception) {
                android.util.Log.e("WidgetLifecycle", "WIDGET_INIT_DATA_READ: Fresh load failed, showing skeleton", e)
                WidgetDataProvider.createSkeletonSnapshot(context, appWidgetId, widgetSize)
            }
        }

        val startTab = when (snapshot.displayType) {
            WidgetDisplayType.TASKS, WidgetDisplayType.COMBINED -> "planner"
            WidgetDisplayType.SCHEDULE -> "schedule"
        }
        val openAction = WidgetActions.openApp(context, startTab)

        android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_RENDER: Rendering widget (isLoading=${snapshot.isLoading}, tasks=${snapshot.tasks.size}, schedule=${snapshot.schedule.size})")
        provideContent {
            when (widgetSize) {
                WidgetSize.SMALL_2X2 -> SmallWidgetContent(snapshot, openAction)
                WidgetSize.TALL_2X3 -> TallWidgetContent(snapshot, openAction)
            }
        }

        // AFTER first paint: prewarm design bitmap in background so next update is instant
        WidgetDataProvider.prewarmBackground(context, snapshot)

        // Trigger one deterministic background refresh.
        //
        // - INITIAL_CREATION on a brand-new widget or any cache miss: a fresh load into the
        //   cache may have raced empty, or the cached snapshot could be stale. Re-running
        //   through the coordinator guarantees a real-data update without requiring a user
        //   action (no checkbox toggle, no app open, no delay).
        // - TIME_BOUNDARY if the cached snapshot is from a previous day.
        //
        // We always route through WidgetUpdater.refresh so the WIDGET_REFRESH_REASON log
        // matches PATH B (task toggle) exactly. The coordinator handles cache invalidation
        // for data-changing reasons; INITIAL_CREATION is read-only and skips the invalidate.
        if (cached == null) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_COMPLETE: Cache miss — enqueuing INITIAL_CREATION refresh")
            WidgetUpdater.refresh(context, WidgetUpdater.Reason.INITIAL_CREATION)
        } else if (!isSnapshotForToday(cached)) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_COMPLETE: Stale day — enqueuing TIME_BOUNDARY refresh")
            WidgetUpdater.refresh(context, WidgetUpdater.Reason.TIME_BOUNDARY)
        } else if (shouldRefreshCachedSnapshot(context, appWidgetId)) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_COMPLETE: TTL expired — enqueuing REMOTE_SYNC refresh")
            WidgetUpdater.refresh(context, WidgetUpdater.Reason.REMOTE_SYNC)
        } else {
            android.util.Log.i("WidgetLifecycle", "WIDGET_INIT_COMPLETE: Cache fresh — no refresh needed")
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

    /** Checks if the snapshot's design preset & colors match current preferences. */
    private fun isSnapshotDesignCurrent(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshot
    ): Boolean {
        val currentPreset = WidgetPreferences.getDesignPreset(context, appWidgetId)
        val currentColors = WidgetPreferences.getResolvedDesignColors(context, appWidgetId)
        return snapshot.designPreset == currentPreset &&
            snapshot.designColors.color1 == currentColors.color1 &&
            snapshot.designColors.color2 == currentColors.color2 &&
            snapshot.designColors.color3 == currentColors.color3
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