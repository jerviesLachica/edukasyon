package com.edukasyon.studentai.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.edukasyon.studentai.MainActivity
import androidx.glance.appwidget.action.actionStartActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetActions {
    const val START_TAB_KEY = "start_tab"
    const val TASK_ID_KEY = "task_id"

    const val ACTION_OPEN_TAB = "com.edukasyon.studentai.widget.OPEN_TAB"
    const val ACTION_OPEN_TASK = "com.edukasyon.studentai.widget.OPEN_TASK"

    fun openApp(context: Context, tab: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            // Glance's actionStartActivity(Intent) does NOT wrap the intent in a
            // trampoline, so the system conflates PendingIntents whose intents are
            // filterEquals-identical. Component + extras alone do NOT disambiguate —
            // without a unique action/data every task row collapsed onto a single
            // PendingIntent and taps delivered the wrong extras / opened the wrong
            // screen. A unique action + data URI per destination keeps each
            // clickable's PendingIntent distinct. The intent stays explicit, so no
            // <intent-filter> is needed in the manifest.
            action = ACTION_OPEN_TAB
            data = Uri.parse("schedmate://widget/tab/$tab")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, tab)
        }
    )

    fun openAppForTask(context: Context, taskId: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TASK
            // Unique per task — this is what stops the rows from sharing one
            // PendingIntent (see openApp above for why extras alone aren't enough).
            data = Uri.parse("schedmate://widget/task/$taskId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, "planner")
            putExtra(TASK_ID_KEY, taskId)
        }
    )

    fun openSchedule(context: Context) = openApp(context, "schedule")
}

abstract class BaseStudentAiWidget(
    private val widgetSize: WidgetSize
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        WidgetPreferences.setWidgetSize(context, appWidgetId, widgetSize)

        val cached = WidgetDataProvider.loadCachedSnapshot(context, appWidgetId, widgetSize)
        
        // INSTANT LOAD: Show cached or skeleton immediately, load fresh data in background
        val snapshot = cached ?: WidgetDataProvider.createSkeletonSnapshot(context, appWidgetId, widgetSize)
        
        // Render the design bitmap off the main thread BEFORE composition — otherwise
        // WidgetBackgroundLayer draws pattern bitmaps synchronously during first paint.
        WidgetDataProvider.prewarmBackground(context, snapshot)

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

        // Load fresh data in background if cached is null or stale. Re-render the widget
        // once the fresh snapshot is ready using updateAll (reliable across glance versions)
        // rather than calling update() against the original GlanceId, which can be a no-op
        // or race with the just-finished provideContent call.
        if (cached == null || shouldRefreshCachedSnapshot(context, appWidgetId)) {
            WidgetUpdater.notifyDataChanged(context)
        }
    }

    private fun shouldRefreshCachedSnapshot(context: Context, appWidgetId: Int): Boolean {
        val savedAt = WidgetSnapshotCache.readSavedAtMs(context, appWidgetId) ?: return true
        return System.currentTimeMillis() - savedAt > 30_000L
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