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
