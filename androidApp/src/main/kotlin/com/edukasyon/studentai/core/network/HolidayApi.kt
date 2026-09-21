package com.edukasyon.studentai.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface HolidayApi {
    @GET("api/v3/PublicHolidays/{year}/{countryCode}")
    suspend fun getPublicHolidays(
        @Path("year") year: Int,
        @Path("countryCode") countryCode: String = "PH"
    ): List<NagerHolidayDto>

    @GET("api/v3/NextPublicHolidays/{countryCode}")
    suspend fun getNextPublicHolidays(
        @Path("countryCode") countryCode: String = "PH"
    ): List<NagerHolidayDto>

    @GET("api/v3/LongWeekend/{year}/{countryCode}")
    suspend fun getLongWeekends(
        @Path("year") year: Int,
        @Path("countryCode") countryCode: String = "PH"
    ): List<NagerLongWeekendDto>

    @GET("api/v3/IsTodayPublicHoliday/{countryCode}")
    suspend fun isTodayPublicHoliday(
        @Path("countryCode") countryCode: String = "PH"
    ): retrofit2.Response<Unit>
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

@Serializable
data class NagerLongWeekendDto(
    val startDate: String,
    val endDate: String,
    val dayCount: Int,
    val needBridgeDay: Boolean = false,
    val bridgeDays: List<String> = emptyList()
)
