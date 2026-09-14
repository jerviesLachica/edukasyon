package com.edukasyon.studentai.widget

import android.content.Context
import android.util.Log
import com.edukasyon.studentai.data.local.StudentAiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes the widget follow the database in realtime. While the app process is
 * alive, every task/schedule write (in-app check, add, edit, delete, sync
 * pull) flows here and pushes a widget repaint within ~half a second —
 * no reopen, no polling, no waiting for WorkManager.
 *
 * Bursts are coalesced with debounce; repaints are idempotent and never
 * write the DB, so this cannot loop.
 */
object WidgetRealtimeObserver {
    private const val TAG = "WidgetLifecycle"
    private const val DEBOUNCE_MS = 400L

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, database: StudentAiDatabase) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            launch {
                runCatching {
                    database.taskDao().observeAll()
                        .debounce(DEBOUNCE_MS)
                        .collect {
                            Log.i(TAG, "WIDGET_REFRESH_REASON=REALTIME: realtime task change, refreshing")
                            com.edukasyon.studentai.widget.update.WidgetUpdateManager.refreshAllAsync(
                                appContext,
                                com.edukasyon.studentai.widget.update.WidgetUpdateManager.RefreshReason.TASK_CHANGED
                            )
                        }
                }.onFailure { Log.e(TAG, "WIDGET_REFRESH: realtime task observer died", it) }
            }
            launch {
                runCatching {
                    database.scheduleDao().observeAll()
                        .debounce(DEBOUNCE_MS)
                        .collect {
                            Log.i(TAG, "WIDGET_REFRESH_REASON=REALTIME: realtime schedule change, refreshing")
                            com.edukasyon.studentai.widget.update.WidgetUpdateManager.refreshAllAsync(
                                appContext,
                                com.edukasyon.studentai.widget.update.WidgetUpdateManager.RefreshReason.SCHEDULE_CHANGED
                            )
                        }
                }.onFailure { Log.e(TAG, "WIDGET_REFRESH: realtime schedule observer died", it) }
            }
        }
    }
}
