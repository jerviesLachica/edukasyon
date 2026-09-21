package com.edukasyon.studentai.domain.model

data class Holiday(
    val name: String,
    val localName: String? = null,
    val dateMillis: Long,
    val type: HolidayType
)

enum class HolidayType(val label: String) {
    REGULAR("Regular"),
    SPECIAL("Special Non-Working")
}

data class LongWeekend(
    val startDateMillis: Long,
    val endDateMillis: Long,
    val dayCount: Int,
    val needBridgeDay: Boolean = false,
    val bridgeDays: List<String> = emptyList()
)
