package com.edukasyon.studentai.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val referenceId = intent.getStringExtra(EXTRA_REFERENCE_ID)
        val typeName = intent.getStringExtra(EXTRA_REMINDER_TYPE)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        Log.d(TAG, "Notification dismissed: type=$typeName, refId=$referenceId, notifId=$notificationId")

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (referenceId != null && typeName != null) {
            val type = runCatching { ReminderType.valueOf(typeName) }.getOrNull()
            if (type != null) {
                val dismissManager = ReminderDismissManager(context.applicationContext)
                dismissManager.markDismissed(type, referenceId)

                val scheduler = ReminderScheduler(context.applicationContext)
                val uniqueWorkName = when (type) {
                    ReminderType.CLASS -> "class_$referenceId"
                    ReminderType.TASK -> "task_$referenceId"
                    ReminderType.ASSIGNMENT -> "assignment_$referenceId"
                    ReminderType.EXAM -> "exam_$referenceId"
                    ReminderType.FOCUS -> "focus_$referenceId"
                    ReminderType.SCHEDULE_SCAN -> "schedule_scan_$referenceId"
                    ReminderType.REVIEW -> "review_$referenceId"
                }
                scheduler.cancelReminder(uniqueWorkName)
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.edukasyon.studentai.ACTION_DISMISS_NOTIFICATION"
        const val EXTRA_REFERENCE_ID = "extra_reference_id"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        private const val TAG = "NotifDismissReceiver"
    }
}
