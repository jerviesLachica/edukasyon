package com.edukasyon.studentai

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.sync.SyncScheduler
import com.edukasyon.studentai.core.sync.HolidaySyncScheduler
import com.edukasyon.studentai.data.local.StudentAiDatabase
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.data.repository.HolidayRepository
import com.edukasyon.studentai.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StudentAiApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var database: StudentAiDatabase
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var holidaySyncScheduler: HolidaySyncScheduler
    @Inject lateinit var holidayRepository: HolidayRepository
    @Inject lateinit var preferences: UserPreferences
    @Inject lateinit var aiApi: AiApiService
    @Inject lateinit var reminderSyncService: com.edukasyon.studentai.core.notifications.ReminderSyncService
    @Inject lateinit var notificationHelper: com.edukasyon.studentai.core.notifications.NotificationHelper
    @Inject lateinit var firestoreSyncService: com.edukasyon.studentai.core.firebase.FirestoreSyncService
    @Inject lateinit var firebaseAuthManager: com.edukasyon.studentai.core.firebase.FirebaseAuthManager
    @Inject lateinit var connectivityMonitor: com.edukasyon.studentai.core.network.ConnectivityMonitor

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            Log.d("StudentAiApp", "Firebase ready: ${FirebaseApp.getInstance().options.projectId}")
        }.onFailure { Log.w("StudentAiApp", "Firebase init skipped", it) }
        appScope.launch {
            runCatching {
                preferences.ensureRemoteAiEnabled()
                database.openHelper.writableDatabase
                syncScheduler.schedulePeriodicSync()
                holidaySyncScheduler.schedulePeriodicSync()
                holidayRepository.refreshOnAppStart()
                notificationHelper.createChannels()
                runCatching {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance()
                        .subscribeToTopic(
                            com.edukasyon.studentai.core.update.AppUpdateMessagingService.UPDATE_TOPIC
                        )
                }.onFailure { Log.w("StudentAiApp", "FCM topic subscribe failed", it) }
                reminderSyncService.rescheduleAll()
                WidgetUpdater.schedulePeriodicRefresh(this@StudentAiApplication)
                WidgetUpdater.refreshAll(this@StudentAiApplication)
                if (firebaseAuthManager.isGoogleSignedIn && connectivityMonitor.isCurrentlyOnline()) {
                    when (firestoreSyncService.syncAll()) {
                        is com.edukasyon.studentai.domain.model.SyncResult.Success ->
                            Log.i("StudentAiApp", "Launch sync completed")
                        is com.edukasyon.studentai.domain.model.SyncResult.Error ->
                            Log.w("StudentAiApp", "Launch sync failed")
                        else -> Unit
                    }
                }
            }.onFailure { Log.e("StudentAiApp", "Background init failed", it) }
            runCatching {
                val health = aiApi.health()
                Log.i("StudentAiApp", "AI backend reachable (aiConfigured=${health.aiConfigured})")
            }.onFailure {
                Log.w("StudentAiApp", "AI backend health check failed — features may be unavailable until online", it)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
