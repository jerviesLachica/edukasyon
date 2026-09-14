package com.edukasyon.studentai.widget.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.edukasyon.studentai.widget.update.WidgetUpdateManager
import com.edukasyon.studentai.worker.WidgetBoundaryScheduler
import com.edukasyon.studentai.worker.WidgetSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * V2 restoration: reboot / update / clock changes repopulate widgets from
 * local state and re-arm background work. No network, no Activity.
 */
class WidgetV2BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "WIDGET_INIT_START: restore (${intent.action})")
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        WidgetUpdateManager.refreshAll(
                            context, WidgetUpdateManager.RefreshReason.RESTORED
                        )
                        WidgetSyncWorker.schedulePeriodic(context)
                        WidgetBoundaryScheduler.scheduleNext(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "WIDGET_INIT_COMPLETE: restore failed", e)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "WidgetV2"
    }
}
