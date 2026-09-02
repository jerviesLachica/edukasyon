package com.edukasyon.studentai.core.sync

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Helper function to create calendar intents from schedule data
fun createCalendarIntent(context: Context, scheduleItem: ScheduleItem): Intent {
    val startMillis: Long = scheduleItem.startTime.toEpochMilli()
    val endMillis: Long = scheduleItem.endTime.toEpochMilli()

    return Intent(Intent.ACTION_INSERT).apply {
        setData(CalendarContract.Events.CONTENT_URI)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.TITLE, scheduleItem.title)
        putExtra(CalendarContract.Events.DESCRIPTION, scheduleItem.description)
        putExtra(CalendarContract.Events.EVENT_LOCATION, scheduleItem.location)
        putExtra(CalendarContract.Events.ALL_DAY, false)
    }
}

// Data class to represent a simplified schedule item for demonstration
data class ScheduleItem(
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant
)

// Dummy data for testing (next 7 days of classes)
fun getDummyScheduleItemsForNext7Days(): List<ScheduleItem> {
    val scheduleItems = mutableListOf<ScheduleItem>()
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

    for (i in 0 until 7) {
        val date = now.plus(i.toLong(), ChronoUnit.DAYS)

        // Example class 1: Math
        val mathStartTime = date.atZone(zoneId).withHour(9).withMinute(0).toInstant()
        val mathEndTime = date.atZone(zoneId).withHour(10).withMinute(0).toInstant()
        scheduleItems.add(
            ScheduleItem(
                title = "Math Class",
                description = "Algebra 101",
                location = "Room 101",
                startTime = mathStartTime,
                endTime = mathEndTime
            )
        )

        // Example class 2: Science
        val scienceStartTime = date.atZone(zoneId).withHour(11).withMinute(0).toInstant()
        val scienceEndTime = date.atZone(zoneId).withHour(12).withMinute(0).toInstant()
        scheduleItems.add(
            ScheduleItem(
                title = "Science Lab",
                description = "Chemistry practical",
                location = "Lab 203",
                startTime = scienceStartTime,
                endTime = scienceEndTime
            )
        )
    }
    return scheduleItems
}
