package com.edukasyon.studentai.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            Log.i("WidgetBootReceiver", "Rescheduling daily widget alarm on action: \")
            WidgetAlarmReceiver.scheduleDailyAlarm(context)
        }
    }
}
