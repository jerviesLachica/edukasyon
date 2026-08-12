package com.edukasyon.studentai.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.edukasyon.studentai.data.local.dao.SyncMetadataDao
import com.edukasyon.studentai.data.local.entity.SyncMetadataEntity
import com.edukasyon.studentai.domain.model.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncMetadataDao: SyncMetadataDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    entityType = "all",
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingCount = 0,
                    failedCount = 0,
                    status = SyncState.SYNCED.name
                )
            )
            Result.success()
        } catch (e: Exception) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    entityType = "all",
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingCount = 0,
                    failedCount = 1,
                    status = SyncState.FAILED.name
                )
            )
            Result.retry()
        }
    }
}

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "studentai_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
