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
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
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

    fun createChannels(soundUri: String? = null, soundName: String? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        
        // Resolve the sound URI with fallback to system default
        val baseUri = soundUri?.takeIf { it.isNotBlank() }?.let {
            try { Uri.parse(it) } catch (_: Exception) { Settings.System.DEFAULT_ALARM_ALERT_URI }
        } ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        ReminderType.entries.forEach { type ->
            // Create versioned channel ID based on sound URI hash
            val versionSuffix = soundUri?.hashCode()?.let { hash ->
                if (hash != 0) "_v${abs(hash)}" else ""
            } ?: ""
            val channelId = "${type.channelId}${versionSuffix}"
            val displayName = if (soundName != null) "${type.channelName} - $soundName" else type.channelName
            
            val channel = NotificationChannel(channelId, displayName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "SchedMate"
                setSound(
                    baseUri,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                enableVibration(true)
                setBypassDnd(true)
            }
            try {
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create channel with sound $baseUri, falling back to default", e)
                try {
                    val fallbackChannel = NotificationChannel(channelId, displayName, NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "SchedMate"
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
                    manager.createNotificationChannel(fallbackChannel)
                } catch (_: Exception) {}
            }
            
            // Clean up old unversioned channel if using versioned
            if (versionSuffix.isNotEmpty()) {
                try { manager.deleteNotificationChannel(type.channelId) } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showReminder(
        notificationId: Int,
        type: ReminderType,
        title: String,
        message: String,
        referenceId: String? = null,
        soundUri: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping reminder $notificationId")
                return
            }
        }
        
        // Compute the versioned channel ID matching createChannels()
        val versionSuffix = soundUri?.hashCode()?.let { hash ->
            if (hash != 0) "_v${abs(hash)}" else ""
        } ?: ""
        val effectiveChannelId = "${type.channelId}${versionSuffix}"
        
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
        val notification = NotificationCompat.Builder(context, effectiveChannelId)
            .setSmallIcon(R.drawable.ic_stat_schedmate)
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
