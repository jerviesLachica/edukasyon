package com.edukasyon.studentai.data.repository

import android.content.Context
import android.util.Log
import com.edukasyon.studentai.core.network.ConnectivityMonitor
import com.edukasyon.studentai.core.network.HolidayApi
import com.edukasyon.studentai.core.network.NagerHolidayDto
import com.edukasyon.studentai.data.local.dao.CachedHolidayDao
import com.edukasyon.studentai.data.local.entity.CachedHolidayEntity
import com.edukasyon.studentai.domain.model.Holiday
import com.edukasyon.studentai.domain.model.HolidayType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BundledHolidayDto(val name: String, val date: String, val type: String)

@Singleton
class HolidayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holidayApi: HolidayApi,
    private val cachedHolidayDao: CachedHolidayDao,
    private val connectivity: ConnectivityMonitor
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var bundledCache: List<Holiday>? = null

    suspend fun getHolidays(fromMillis: Long, toMillis: Long): List<Holiday> = withContext(Dispatchers.IO) {
        val fromDate = dateFormat.format(fromMillis)
        val toDate = dateFormat.format(toMillis)
        val cached = cachedHolidayDao.getByDateRange(fromDate, toDate).map { it.toDomain() }
        if (cached.isNotEmpty()) return@withContext cached

        loadBundled().filter { it.dateMillis in fromMillis until toMillis }
    }

    suspend fun getAllHolidays(): List<Holiday> = withContext(Dispatchers.IO) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val cached = (currentYear - 1..currentYear + 1).flatMap { year ->
            cachedHolidayDao.getByYear(year)
        }.map { it.toDomain() }
        if (cached.isNotEmpty()) return@withContext cached.distinctBy { it.dateMillis }

        loadBundled()
    }

    suspend fun hasCachedData(): Boolean = withContext(Dispatchers.IO) {
        cachedHolidayDao.getLatestFetchedAtForYear(Calendar.getInstance().get(Calendar.YEAR)) != null
    }

    suspend fun refreshOnAppStart() = withContext(Dispatchers.IO) {
        if (!connectivity.isCurrentlyOnline()) return@withContext
        val year = Calendar.getInstance().get(Calendar.YEAR)
        refreshYearIfStale(year)
        refreshYearIfStale(year + 1)
    }

    suspend fun refreshYearIfStale(year: Int, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!connectivity.isCurrentlyOnline()) return@withContext false

        val lastFetched = cachedHolidayDao.getLatestFetchedAtForYear(year)
        val isStale = lastFetched == null || System.currentTimeMillis() - lastFetched > STALE_MS
        if (!force && !isStale) return@withContext false

        syncYear(year)
    }

    private suspend fun syncYear(year: Int): Boolean {
        return try {
            val response = holidayApi.getPublicHolidays(year)
            val fetchedAt = System.currentTimeMillis()
            val entities = response.map { it.toEntity(year, fetchedAt) }
            cachedHolidayDao.deleteByYear(year)
            cachedHolidayDao.upsertAll(entities)
            Log.i(TAG, "Synced $year PH holidays (${entities.size} entries)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync PH holidays for $year", e)
            false
        }
    }

    private fun loadBundled(): List<Holiday> {
        bundledCache?.let { return it }
        val raw = context.assets.open("ph_holidays.json").bufferedReader().use { it.readText() }
        val dtos = json.decodeFromString<List<BundledHolidayDto>>(raw)
        return dtos.mapNotNull { dto ->
            val date = runCatching { dateFormat.parse(dto.date)?.time }.getOrNull() ?: return@mapNotNull null
            Holiday(
                name = dto.name,
                dateMillis = date,
                type = if (dto.type.equals("regular", ignoreCase = true)) HolidayType.REGULAR else HolidayType.SPECIAL
            )
        }.also { bundledCache = it }
    }

    private fun CachedHolidayEntity.toDomain(): Holiday = Holiday(
        name = name,
        localName = localName.takeIf { it.isNotBlank() },
        dateMillis = requireNotNull(dateFormat.parse(date)?.time),
        type = if (type == HolidayType.REGULAR.name) HolidayType.REGULAR else HolidayType.SPECIAL
    )

    private fun NagerHolidayDto.toEntity(year: Int, fetchedAt: Long): CachedHolidayEntity = CachedHolidayEntity(
        date = date,
        name = name,
        localName = localName,
        type = mapNagerType(types).name,
        year = year,
        fetchedAt = fetchedAt
    )

    private fun mapNagerType(types: List<String>): HolidayType {
        val normalized = types.map { it.lowercase(Locale.US) }
        return if (normalized.any { it == "public" }) HolidayType.REGULAR else HolidayType.SPECIAL
    }

    companion object {
        private const val TAG = "HolidayRepository"
        private const val STALE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
