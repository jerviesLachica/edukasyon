package com.edukasyon.studentai.widget.lifecycle

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import com.edukasyon.studentai.widget.store.WidgetConfigStore
import com.edukasyon.studentai.widget.store.WidgetSnapshotStore
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import com.edukasyon.studentai.worker.WidgetSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * V2 provider: classic AppWidgetProvider, RemoteViews pushed explicitly by
 * [WidgetUpdateManager]. No Glance, no Compose runtime, no ViewModel.
 */
class ScheduleWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    // First sight of this ID (no persisted config) is a creation;
                    // anything else is a system-driven update (reboot, rebind).
                    val firstSeen = !WidgetConfigStore.hasConfig(context, id)
                    WidgetUpdateManager.refresh(
                        context, id,
                        if (firstSeen) {
                            WidgetUpdateManager.RefreshReason.INITIAL_CREATION
                        } else {
                            WidgetUpdateManager.RefreshReason.SYSTEM_UPDATE
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_INIT_START: onUpdate failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Resize changes compact/tall budgets — rebuild for the new dimensions.
        WidgetUpdateManager.refreshAsync(
            context, appWidgetId,
            WidgetUpdateManager.RefreshReason.SYSTEM_UPDATE
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { id ->
            // Drop per-instance state only. Never touch tasks/schedules.
            WidgetConfigStore.remove(context, id)
            WidgetSnapshotStore.remove(context, id)
            Log.i(TAG, "WIDGET_INIT_START: forgotten deleted id=$id")
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                newWidgetIds.forEach { id ->
                    WidgetUpdateManager.refresh(
                        context, id, WidgetUpdateManager.RefreshReason.RESTORED
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_INIT_START: onRestored failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSyncWorker.schedulePeriodic(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                com.edukasyon.studentai.worker.WidgetBoundaryScheduler.scheduleNext(context)
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_INIT_START: boundary schedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSyncWorker.cancelAll(context)
        com.edukasyon.studentai.worker.WidgetBoundaryScheduler.cancel(context)
    }

    private companion object {
        const val TAG = "WidgetV2"
    }
}
