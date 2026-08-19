package com.edukasyon.studentai.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class WidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                Log.i("WidgetAlarmReceiver", "Daily widget alarm triggered, refreshing all widgets")
                WidgetSnapshotCache.invalidate(context)
                WidgetUpdater.refreshAll(context)
            } catch (e: Exception) {
                Log.e("WidgetAlarmReceiver", "Error refreshing widgets on alarm", e)
            } finally {
                scheduleDailyAlarm(context)
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_DAILY_REFRESH = "com.edukasyon.studentai.widget.ACTION_DAILY_REFRESH"
        private const val REQUEST_CODE = 2026

        fun scheduleDailyAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
                action = ACTION_DAILY_REFRESH
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

            // Calculate next midnight or exact 24 hour interval
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val triggerTime = calendar.timeInMillis

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
                Log.i("WidgetAlarmReceiver", "Successfully scheduled next daily widget alarm for \")
            } catch (e: SecurityException) {
                Log.w("WidgetAlarmReceiver", "SecurityException scheduling exact alarm, falling back to inexact", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }
}
