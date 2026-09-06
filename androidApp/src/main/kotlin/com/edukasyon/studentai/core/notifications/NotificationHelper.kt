package com.edukasyon.studentai.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.edukasyon.studentai.MainActivity
import com.edukasyon.studentai.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val REFERENCE_ID_EXTRA = "reference_id"
        const val REFERENCE_TYPE_EXTRA = "reference_type"
        private const val TAG = "NotificationHelper"
    }
    init {
        createChannels()
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderType.entries.forEach { type ->
            val channel = NotificationChannel(type.channelId, type.channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "SchedMate "
                setSound(
                    Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                enableVibration(true)
                setBypassDnd(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showReminder(
        notificationId: Int,
        type: ReminderType,
        title: String,
        message: String,
        referenceId: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping reminder $notificationId")
                return
            }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            referenceId?.let { putExtra(REFERENCE_ID_EXTRA, it) }
            putExtra(REFERENCE_TYPE_EXTRA, type.name)
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
