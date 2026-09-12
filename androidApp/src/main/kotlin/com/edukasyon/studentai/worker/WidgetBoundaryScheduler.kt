package com.edukasyon.studentai.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.widget.WidgetEntryPoint
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Time-boundary scheduling without polling: finds the next meaningful schedule
 * moment (a class starting/ending, or midnight rollover) and enqueues one
 * one-shot worker for it. The worker chains the following boundary on completion.
 * Inexact-by-design: WorkManager may defer under battery restrictions, and the
 * 30-minute periodic safety net covers missed boundaries.
 */
object WidgetBoundaryScheduler {
    const val BOUNDARY_NAME = "schedmate_v2_boundary"
    private const val TAG = "WidgetV2"

    suspend fun scheduleNext(context: Context) {
        val appContext = context.applicationContext
        val delayMs = runCatching { nextBoundaryDelayMs(appContext) }
            .onFailure { Log.w(TAG, "WIDGET_RENDER_START: boundary calc failed", it) }
            .getOrDefault(TimeUnit.MINUTES.toMillis(30))
        val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    WidgetSyncWorker.KEY_REASON to
                        WidgetUpdateManager.RefreshReason.TIME_BOUNDARY.tag
                )
            )
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(BOUNDARY_NAME, ExistingWorkPolicy.REPLACE, request)
        Log.i(TAG, "WIDGET_RENDER_START: next boundary in ${delayMs / 60_000}min")
    }

    fun cancel(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(BOUNDARY_NAME)
        }
    }

    private suspend fun nextBoundaryDelayMs(appContext: Context): Long {
        val entryPoint = EntryPointAccessors
            .fromApplication(appContext, WidgetEntryPoint::class.java)
        val todayItems = runCatching {
            entryPoint.scheduleDao().getByDay(DateUtils.getTodayDayOfWeek().name)
        }.getOrDefault(emptyList())

        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var nextMinutes: Int? = null
        todayItems.forEach { item ->
            listOf(item.startTime, item.endTime).forEach { clock ->
                val mins = parseMinutes(clock)
                if (mins != null && mins > nowMinutes) {
                    nextMinutes = minOf(nextMinutes ?: mins, mins)
                }
            }
        }
        if (nextMinutes != null) {
            return (nextMinutes!! - nowMinutes).toLong() * 60_000L
        }
        // Nothing left today: wake just after midnight for the date rollover.
        val midnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    private fun parseMinutes(clock: String?): Int? {
        if (clock.isNullOrBlank()) return null
        return try {
            val parts = clock.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            (h * 60 + m).takeIf { it in 0..(24 * 60) }
        } catch (e: Exception) {
            null
        }
    }
}
