package com.edukasyon.studentai.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fires when an exact alarm scheduled by [ReminderScheduler] reaches its
 * trigger time. WorkManager's delayed task is kept as a durable fallback
 * (survives reboots and OEM process limits); when the exact alarm wins the
 * race, the delayed task is replaced so the reminder cannot fire twice — and
 * even a simultaneous run posts the same notification ID, which the system
 * deduplicates into a single visible notification.
 *
 * Posting is delegated to [ReminderWorker] so both paths share one pipeline
 * (opt-in preference gates, dismiss dedupe, sound selection).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uniqueWorkName = intent.getStringExtra(EXTRA_UNIQUE_WORK_NAME) ?: return
        val type = intent.getStringExtra(ReminderWorkerKeys.TYPE) ?: return
        val title = intent.getStringExtra(ReminderWorkerKeys.TITLE) ?: return
        val message = intent.getStringExtra(ReminderWorkerKeys.MESSAGE) ?: return

        val dataBuilder = Data.Builder()
            .putString(ReminderWorkerKeys.TYPE, type)
            .putString(ReminderWorkerKeys.TITLE, title)
            .putString(ReminderWorkerKeys.MESSAGE, message)
            .putString(
                ReminderWorkerKeys.REFERENCE_ID,
                intent.getStringExtra(ReminderWorkerKeys.REFERENCE_ID)
            )
            .putInt(
                ReminderWorkerKeys.NOTIFICATION_ID,
                intent.getIntExtra(ReminderWorkerKeys.NOTIFICATION_ID, title.hashCode())
            )
        intent.getStringExtra(ReminderWorkerKeys.ALARM_SOUND_URI)
            ?.let { dataBuilder.putString(ReminderWorkerKeys.ALARM_SOUND_URI, it) }
        intent.getStringExtra(ReminderWorkerKeys.ALARM_SOUND_NAME)
            ?.let { dataBuilder.putString(ReminderWorkerKeys.ALARM_SOUND_NAME, it) }

        val immediate = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(dataBuilder.build())
            .addTag("studentai_reminder")
            .build()
        // REPLACE drops the still-delayed WorkManager task for this name, so
        // the exact alarm (already fired) becomes the single delivery path.
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            androidx.work.ExistingWorkPolicy.REPLACE,
            immediate
        )
    }

    companion object {
        const val EXTRA_UNIQUE_WORK_NAME = "studentai_reminder_unique_name"
    }
}
