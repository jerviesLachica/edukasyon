package com.edukasyon.studentai.core.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleReminder(
        uniqueWorkName: String,
        type: ReminderType,
        title: String,
        message: String,
        triggerAtMillis: Long,
        referenceId: String? = null,
        notificationId: Int = uniqueWorkName.hashCode()
    ) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val data: Data = workDataOf(
            ReminderWorkerKeys.TYPE to type.name,
            ReminderWorkerKeys.TITLE to title,
            ReminderWorkerKeys.MESSAGE to message,
            ReminderWorkerKeys.REFERENCE_ID to referenceId,
            ReminderWorkerKeys.NOTIFICATION_ID to notificationId
        )
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("studentai_reminder")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(uniqueWorkName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
    }
}
