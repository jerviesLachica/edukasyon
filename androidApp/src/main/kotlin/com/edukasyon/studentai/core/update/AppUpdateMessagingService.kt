package com.edukasyon.studentai.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.edukasyon.studentai.MainActivity
import com.edukasyon.studentai.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM topic broadcasts about new app versions and surfaces a system
 * notification with an "Install update" action. Tapping either opens
 * [MainActivity] with [EXTRA_TRIGGER_UPDATE], which auto-starts the APK
 * download and shows the in-app update flow.
 */
class AppUpdateMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data[TYPE_KEY] != TYPE_APP_UPDATE) return
        val versionName = data[VERSION_KEY] ?: return
        val notes = data[NOTES_KEY]?.takeIf { it.isNotBlank() }
            ?: "A new version of SchedMate is available."
        showUpdateNotification(versionName, notes)
    }

    private fun showUpdateNotification(versionName: String, notes: String) {
        val manager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.areNotificationsEnabled()) {
            Log.i(TAG, "Notifications disabled — skipping update broadcast")
            return
        }
        createChannel(manager)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_TRIGGER_UPDATE, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPi = PendingIntent.getActivity(this, REQUEST_CONTENT, openIntent, flags)
        val installPi = PendingIntent.getActivity(this, REQUEST_INSTALL, openIntent, flags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SchedMate $versionName is available")
            .setContentText(notes)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, "Install update", installPi)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Failed to post update notification", it) }
    }

    private fun createChannel(manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Notifies you when a new version of SchedMate is available" }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AppUpdateMsgService"
        const val CHANNEL_ID = "app_updates"
        const val EXTRA_TRIGGER_UPDATE = "extra_trigger_update"
        const val UPDATE_TOPIC = "app_updates"
        private const val TYPE_KEY = "type"
        private const val TYPE_APP_UPDATE = "app_update"
        private const val VERSION_KEY = "versionName"
        private const val NOTES_KEY = "releaseNotes"
        private const val NOTIFICATION_ID = 42001
        private const val REQUEST_CONTENT = 1
        private const val REQUEST_INSTALL = 2
    }
}
