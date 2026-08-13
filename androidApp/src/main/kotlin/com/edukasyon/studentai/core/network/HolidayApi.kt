package com.edukasyon.studentai.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface HolidayApi {
    @GET("api/v4/PublicHolidays/{year}/PH")
    suspend fun getPublicHolidays(@Path("year") year: Int): List<NagerHolidayDto>
}

@Serializable
data class NagerHolidayDto(
    val date: String,
    val localName: String,
    val name: String,
    val countryCode: String,
    val fixed: Boolean = false,
    val global: Boolean = true,
    val counties: List<String>? = null,
    val launchYear: Int? = null,
    val types: List<String> = emptyList()
)
