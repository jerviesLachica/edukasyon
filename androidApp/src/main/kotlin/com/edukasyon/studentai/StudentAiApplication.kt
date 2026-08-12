package com.edukasyon.studentai

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.sync.SyncScheduler
import com.edukasyon.studentai.data.local.StudentAiDatabase
import com.edukasyon.studentai.data.preferences.UserPreferences
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
    @Inject lateinit var preferences: UserPreferences
    @Inject lateinit var aiApi: AiApiService

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
