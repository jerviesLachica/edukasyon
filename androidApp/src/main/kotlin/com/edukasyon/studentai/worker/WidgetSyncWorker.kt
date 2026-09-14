package com.edukasyon.studentai.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import java.util.concurrent.TimeUnit

/**
 * V2 background worker. Refreshes all widgets for a reason, then — for time
 * boundaries — chains the next boundary. No polling: one-shot work with a
 * delay computed from the actual schedule, plus a 30-minute safety net.
 * Plain CoroutineWorker (no Hilt): everything flows through the manager.
 */
class WidgetSyncWorker(
    context: Context,
    params: androidx.work.WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reason = runCatching {
            WidgetUpdateManager.RefreshReason.valueOf(
                inputData.getString(KEY_REASON)
                    ?: WidgetUpdateManager.RefreshReason.REMOTE_SYNC.tag
            )
        }.getOrDefault(WidgetUpdateManager.RefreshReason.REMOTE_SYNC)
        return runCatching {
            Log.i(TAG, "WIDGET_RENDER_START: background worker reason=${reason.tag}")
            WidgetUpdateManager.refreshAll(applicationContext, reason)
            if (reason == WidgetUpdateManager.RefreshReason.TIME_BOUNDARY) {
                WidgetBoundaryScheduler.scheduleNext(applicationContext)
            }
            Result.success()
        }.getOrElse {
            Log.e(TAG, "WIDGET_RENDER_COMPLETE: worker failed", it)
            Result.retry()
        }
    }

    companion object {
        const val KEY_REASON = "v2_reason"
        private const val PERIODIC_NAME = "schedmate_v2_periodic"
        private const val ONE_SHOT_NAME = "schedmate_v2_oneshot"
        private const val TAG = "WidgetV2"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(30, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_REASON to WidgetUpdateManager.RefreshReason.REMOTE_SYNC.tag))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun scheduleOneShot(context: Context, delayMs: Long, reason: WidgetUpdateManager.RefreshReason) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_REASON to reason.tag))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(PERIODIC_NAME)
                cancelUniqueWork(ONE_SHOT_NAME)
                cancelUniqueWork(WidgetBoundaryScheduler.BOUNDARY_NAME)
            }
        }
    }
}
