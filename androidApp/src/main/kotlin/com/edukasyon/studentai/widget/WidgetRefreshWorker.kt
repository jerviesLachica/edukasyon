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
            WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
