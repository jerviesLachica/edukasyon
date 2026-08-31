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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetActions {
    const val START_TAB_KEY = "start_tab"

    fun openApp(context: Context, tab: String) = actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, tab)
        }
    )
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
        withContext(Dispatchers.Default) {
            WidgetDataProvider.prewarmBackground(context, snapshot)
        }

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

        // Load fresh data in background if not cached or stale
        if (cached == null || shouldRefreshCachedSnapshot(context, appWidgetId)) {
            WidgetDataProvider.loadSnapshotFresh(context, appWidgetId, widgetSize)
            update(context, id)
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