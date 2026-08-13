package com.edukasyon.studentai.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.edukasyon.studentai.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

object ReminderWorkerKeys {
    const val TYPE = "reminder_type"
    const val TITLE = "title"
    const val MESSAGE = "message"
    const val REFERENCE_ID = "reference_id"
    const val NOTIFICATION_ID = "notification_id"
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper,
    private val preferences: UserPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferences.notificationsEnabled.first()) return Result.success()

        val typeName = inputData.getString(ReminderWorkerKeys.TYPE) ?: return Result.failure()
        val type = runCatching { ReminderType.valueOf(typeName) }.getOrElse { return Result.failure() }

        val enabled = when (type) {
            ReminderType.CLASS -> preferences.classReminders.first()
            ReminderType.TASK, ReminderType.ASSIGNMENT -> preferences.taskReminders.first()
            ReminderType.EXAM -> preferences.examReminders.first()
            ReminderType.FOCUS, ReminderType.SCHEDULE_SCAN -> preferences.notificationsEnabled.first()
        }
        if (!enabled) return Result.success()

        val title = inputData.getString(ReminderWorkerKeys.TITLE) ?: return Result.failure()
        val message = inputData.getString(ReminderWorkerKeys.MESSAGE) ?: return Result.failure()
        val referenceId = inputData.getString(ReminderWorkerKeys.REFERENCE_ID)
        val notificationId = inputData.getInt(ReminderWorkerKeys.NOTIFICATION_ID, title.hashCode())

        notificationHelper.showReminder(notificationId, type, title, message, referenceId)
        return Result.success()
    }
}
