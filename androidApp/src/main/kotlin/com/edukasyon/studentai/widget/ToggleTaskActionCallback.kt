package com.edukasyon.studentai.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Toggles a task's completion status when the widget checkbox is tapped.
 * Uses EntryPointAccessors since Hilt @AndroidEntryPoint doesn't support Glance ActionCallback.
 */
class ToggleTaskActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[TASK_ID_KEY] ?: return run {
            Log.w("ToggleTaskActionCallback", "No task_id parameter provided")
        }

        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                val taskDao = entryPoint.taskDao()

                val tasks = taskDao.getAllForSync()
                val task = tasks.find { it.id == taskId }

                if (task == null) {
                    Log.w("ToggleTaskActionCallback", "Task $taskId not found")
                    return@withContext
                }

                val isCompleted = task.status == com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED.name
                val newStatus = if (isCompleted) {
                    com.edukasyon.studentai.domain.model.TaskStatus.PENDING.name
                } else {
                    com.edukasyon.studentai.domain.model.TaskStatus.COMPLETED.name
                }

                val updatedTask = task.copy(
                    status = newStatus,
                    completedAt = if (isCompleted) null else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                taskDao.insert(updatedTask)
                Log.i("WidgetLifecycle", "WIDGET_REFRESH_REASON: Task $taskId toggled to $newStatus, triggering refresh")
                
                // Refresh widget
                WidgetUpdater.notifyDataChanged(context)
            } catch (e: Exception) {
                Log.e("ToggleTaskActionCallback", "Failed to toggle task $taskId", e)
            }
        }
    }

    companion object {
        val TASK_ID_KEY = ActionParameters.Key<String>("task_id")
    }
}
