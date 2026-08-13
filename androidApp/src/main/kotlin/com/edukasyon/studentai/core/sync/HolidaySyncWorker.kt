package com.edukasyon.studentai.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.edukasyon.studentai.data.repository.HolidayRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class HolidaySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val holidayRepository: HolidayRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            holidayRepository.refreshOnAppStart()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

@Singleton
class HolidaySyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<HolidaySyncWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val WORK_NAME = "ph_holiday_sync"
    }
}
