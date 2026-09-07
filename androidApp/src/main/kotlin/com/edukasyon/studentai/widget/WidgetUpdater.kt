package com.edukasyon.studentai.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit

object WidgetUpdater {
    private const val PERIODIC_WORK_NAME = "studentai_widget_refresh"
    private const val ON_DEMAND_WORK_NAME = "studentai_widget_refresh_once"

    fun schedulePeriodicRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        // Also schedule robust daily alarm manager refresh to guarantee everyday refresh at midnight
        WidgetAlarmReceiver.scheduleDailyAlarm(context)
    }

    suspend fun refreshAll(context: Context) {
        android.util.Log.i("WidgetLifecycle", "WIDGET_REFRESH: refreshAll called")
        StudentAiWidget2x2().updateAll(context)
        StudentAiWidget2x3().updateAll(context)
    }

    suspend fun updateAppWidget(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        val widgetSize = WidgetPreferences.getWidgetSize(appContext, appWidgetId)
        when (widgetSize) {
            WidgetSize.SMALL_2X2 -> updateGlanceWidget(
                appContext = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                widgetClass = StudentAiWidget2x2::class.java,
                widget = StudentAiWidget2x2()
            )
            WidgetSize.TALL_2X3 -> updateGlanceWidget(
                appContext = appContext,
                manager = manager,
                appWidgetId = appWidgetId,
                widgetClass = StudentAiWidget2x3::class.java,
                widget = StudentAiWidget2x3()
            )
            null -> refreshAll(appContext)
        }
    }

    private suspend fun updateGlanceWidget(
        appContext: Context,
        manager: GlanceAppWidgetManager,
        appWidgetId: Int,
        widgetClass: Class<out androidx.glance.appwidget.GlanceAppWidget>,
        widget: androidx.glance.appwidget.GlanceAppWidget
    ) {
        manager.getGlanceIds(widgetClass)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }
            ?.let { glanceId -> widget.update(appContext, glanceId) }
            ?: refreshAll(appContext)
    }

    fun notifyDataChanged(context: Context) {
            android.util.Log.i("WidgetLifecycle", "WIDGET_REFRESH_REASON: notifyDataChanged called — invalidating cache and refreshing immediately")
            WidgetSnapshotCache.invalidate(context)
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    ON_DEMAND_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
                )
            // Immediately refresh widgets so they pick up the new data without waiting for WorkManager.
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    android.util.Log.i("WidgetLifecycle", "WIDGET_REFRESH: Immediate refresh after notifyDataChanged")
                    refreshAll(context)
                } catch (e: Exception) {
                    android.util.Log.e("WidgetLifecycle", "WIDGET_REFRESH: Immediate refresh failed", e)
                }
            }
        }

    suspend fun widgetIds(context: Context): List<Int> {
        val manager = GlanceAppWidgetManager(context)
        val small = manager.getGlanceIds(StudentAiWidget2x2::class.java).map {
            manager.getAppWidgetId(it)
        }
        val tall = manager.getGlanceIds(StudentAiWidget2x3::class.java).map {
            manager.getAppWidgetId(it)
        }
        return small + tall
    }
}
