package com.edukasyon.studentai.widget.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.edukasyon.studentai.widget.TaskToggleLocks
import com.edukasyon.studentai.widget.WidgetEntryPoint
import com.edukasyon.studentai.widget.WidgetToggleTracker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * V2 checkbox toggle target. Explicit broadcast PendingIntent with a unique
 * requestCode per (widget, task) — no shared/glance-managed identity, so one
 * tap can never fire another row's action.
 *
 * Absolute semantics: the tap sets the state that was rendered
 * (☐→COMPLETED, ☑→PENDING). Duplicate deliveries are idempotent.
 */
class WidgetToggleReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.edukasyon.studentai.widget.v2.TOGGLE_TASK"
        const val EXTRA_WIDGET_ID = "v2_widget_id"
        const val EXTRA_TASK_ID = "v2_task_id"
        const val EXTRA_DESIRED_COMPLETED = "v2_desired_completed"
        private const val TAG = "WidgetV2"

        fun pendingIntent(
            context: Context,
            appWidgetId: Int,
            taskId: String,
            desiredCompleted: Boolean
        ): PendingIntent {
            val intent = Intent(context, WidgetToggleReceiver::class.java).apply {
                action = ACTION_TOGGLE
                // Unique data URI: belt-and-braces alongside the unique
                // requestCode, since extras alone never disambiguate.
                // The desired state IS part of the identity: the CHECK and
                // UNCHECK intents for one task must be distinct PendingIntents
                // so a stale/duplicated delivery can never replay the previous
                // direction over the tap the user just made (stale
                // desired=true re-checking an uncheck = "can't uncheck").
                data = Uri.parse("schedmate://v2/toggle/$appWidgetId/$taskId/$desiredCompleted")
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_DESIRED_COMPLETED, desiredCompleted)
            }
            return PendingIntent.getBroadcast(
                context,
                ("v2tog:$appWidgetId:$taskId:$desiredCompleted").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        // Absent extra (stale pre-param intents): fall back to flipping DB state.
        val desired: Boolean? = if (intent.hasExtra(EXTRA_DESIRED_COMPLETED)) {
            intent.getBooleanExtra(EXTRA_DESIRED_COMPLETED, false)
        } else null

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleToggle(context.applicationContext, appWidgetId, taskId, desired)
            } catch (e: Exception) {
                Log.e(TAG, "WIDGET_TAP_BEGIN: failed task=$taskId", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleToggle(
        appCtx: Context,
        appWidgetId: Int,
        taskId: String,
        desired: Boolean?
    ) {
        Log.e(TAG, "WIDGET_TAP_BEGIN: wid=$appWidgetId task=$taskId desired=$desired")

        // 1. Optimistic: flip stored snapshots, repaint without DB.
        var optimistic = false
        runCatching {
            WidgetUpdateManager.hostedIds(appCtx).forEach { id ->
                if (WidgetUpdateManager.flipStoredTask(appCtx, id, taskId) != null) {
                    if (WidgetUpdateManager.paintStored(appCtx, id)) optimistic = true
                }
            }
        }.onFailure { Log.w(TAG, "WIDGET_TAP_BEGIN: optimistic failed", it) }
        Log.e(TAG, "WIDGET_TAP_OPTIMISTIC: task=$taskId painted=$optimistic")

        // 2+3. Serialized absolute DB write + full reconcile.
        TaskToggleLocks.forId(taskId).withLock {
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors
                        .fromApplication(appCtx, WidgetEntryPoint::class.java)
                    val taskDao = entryPoint.taskDao()
                    val task = runCatching {
                        taskDao.getByIds(listOf(taskId)).firstOrNull()
                    }.getOrNull()
                    if (task == null) {
                        Log.w(TAG, "WIDGET_TAP_BEGIN: task gone, rebuilding id=$appWidgetId")
                        WidgetUpdateManager.refreshAll(
                            appCtx, WidgetUpdateManager.RefreshReason.TASK_CHANGED
                        )
                        return@withContext
                    }
                    val completedName =
                        com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED.name
                    val pendingName =
                        com.edukasyon.studentai.domain.model.TaskStatus.PENDING.name
                    val newStatus = when (desired) {
                        true -> completedName
                        false -> pendingName
                        null -> if (task.status == completedName) pendingName else completedName
                    }
                    taskDao.insert(
                        task.copy(
                            status = newStatus,
                            completedAt = if (newStatus == pendingName) {
                                null
                            } else {
                                System.currentTimeMillis()
                            },
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    runCatching {
                        val now = System.currentTimeMillis()
                        taskDao.clampFutureCompletedAt(now)
                        taskDao.clampFutureUpdatedAt(now)
                    }
                    WidgetToggleTracker.markToggled(appCtx, taskId)
                    Log.e(TAG, "WIDGET_TAP_RECONCILE: task=$taskId -> $newStatus")
                    WidgetUpdateManager.refreshAll(
                        appCtx, WidgetUpdateManager.RefreshReason.TASK_CHANGED
                    )
                    Log.e(TAG, "WIDGET_TAP_RECONCILED: task=$taskId")
                } catch (e: Exception) {
                    Log.e(TAG, "WIDGET_TAP_BEGIN: toggle failed task=$taskId", e)
                }
            }
        }
    }
}
