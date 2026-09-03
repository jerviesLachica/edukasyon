package com.edukasyon.studentai.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // Load fresh snapshots into cache for ALL active widgets before refreshing,
            // so the widget shows real data instead of stuck skeleton "Loading..." rows.
            val ids = WidgetUpdater.widgetIds(applicationContext)
            if (ids.isNotEmpty()) {
                ids.forEach { appWidgetId ->
                    val widgetSize = WidgetPreferences.getWidgetSize(applicationContext, appWidgetId)
                    if (widgetSize != null) {
                        WidgetDataProvider.loadSnapshotFresh(applicationContext, appWidgetId, widgetSize)
                    }
                }
            }
            WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
