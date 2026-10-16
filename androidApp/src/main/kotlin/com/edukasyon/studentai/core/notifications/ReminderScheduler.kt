package com.edukasyon.studentai.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminder notifications on two layers:
 *  1. **Exact alarm** ([ReminderAlarmReceiver]) — fires at the precise trigger
 *     instant, even in doze; fixes WorkManager's late `setInitialDelay`
 *     delivery. Skipped gracefully when the exact-alarm permission (API 31+)
 *     is not granted.
 *  2. **WorkManager task** — durable fallback that survives reboots and OEM
 *     process limits; also the sole path when exact alarms are unavailable.
 *
 * When the alarm wins the race it replaces the delayed task (same unique
 * work name) so a reminder is delivered once, at the best time available.
 */
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
        notificationId: Int = uniqueWorkName.hashCode(),
        alarmSoundUri: String? = null,
        alarmSoundName: String? = null
    ) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val dataBuilder = Data.Builder()
            .putString(ReminderWorkerKeys.TYPE, type.name)
            .putString(ReminderWorkerKeys.TITLE, title)
            .putString(ReminderWorkerKeys.MESSAGE, message)
            .putString(ReminderWorkerKeys.REFERENCE_ID, referenceId)
            .putInt(ReminderWorkerKeys.NOTIFICATION_ID, notificationId)
        alarmSoundUri?.let { dataBuilder.putString(ReminderWorkerKeys.ALARM_SOUND_URI, it) }
        alarmSoundName?.let { dataBuilder.putString(ReminderWorkerKeys.ALARM_SOUND_NAME, it) }
        val data: Data = dataBuilder.build()
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
        scheduleExactAlarm(
            uniqueWorkName = uniqueWorkName,
            type = type,
            title = title,
            message = message,
            triggerAtMillis = triggerAtMillis,
            referenceId = referenceId,
            notificationId = notificationId,
            alarmSoundUri = alarmSoundUri,
            alarmSoundName = alarmSoundName
        )
    }

    private fun scheduleExactAlarm(
        uniqueWorkName: String,
        type: ReminderType,
        title: String,
        message: String,
        triggerAtMillis: Long,
        referenceId: String?,
        notificationId: Int,
        alarmSoundUri: String?,
        alarmSoundName: String?
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // API 31+: exact alarms require user-granted permission. Without it,
        // the WorkManager task above remains the (inexact) delivery path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_EXACT
            putExtra(ReminderAlarmReceiver.EXTRA_UNIQUE_WORK_NAME, uniqueWorkName)
            putExtra(ReminderWorkerKeys.TYPE, type.name)
            putExtra(ReminderWorkerKeys.TITLE, title)
            putExtra(ReminderWorkerKeys.MESSAGE, message)
            putExtra(ReminderWorkerKeys.REFERENCE_ID, referenceId)
            putExtra(ReminderWorkerKeys.NOTIFICATION_ID, notificationId)
            alarmSoundUri?.let { putExtra(ReminderWorkerKeys.ALARM_SOUND_URI, it) }
            alarmSoundName?.let { putExtra(ReminderWorkerKeys.ALARM_SOUND_NAME, it) }
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueWorkName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call — WorkManager
            // fallback stays enqueued.
        }
    }

    fun cancelReminder(uniqueWorkName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        cancelExactAlarm(uniqueWorkName)
    }

    private fun cancelExactAlarm(uniqueWorkName: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER_EXACT)
        // PendingIntent equality ignores extras: same request code + component
        // + action resolves the one created in scheduleExactAlarm.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueWorkName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        private const val ACTION_REMINDER_EXACT = "com.edukasyon.studentai.action.REMINDER_EXACT"
    }
}
