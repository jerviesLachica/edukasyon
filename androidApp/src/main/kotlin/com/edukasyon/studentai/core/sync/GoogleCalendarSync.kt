package com.edukasyon.studentai.core.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.edukasyon.studentai.domain.model.ScheduleItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Helper function to create calendar intents from schedule data (manual approval path)
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

// Automatic calendar sync: directly insert event via ContentResolver without user confirmation
fun insertCalendarEventAutomatically(context: Context, scheduleItem: ScheduleItem): Boolean {
    return try {
        val calendarId = getPrimaryCalendarId(context) ?: return false
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, scheduleItem.title)
            put(CalendarContract.Events.DESCRIPTION, scheduleItem.description)
            put(CalendarContract.Events.EVENT_LOCATION, scheduleItem.location)
            put(CalendarContract.Events.DTSTART, scheduleItem.startTime.toEpochMilli())
            put(CalendarContract.Events.DTEND, scheduleItem.endTime.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        uri != null
    } catch (e: SecurityException) {
        // Permission not granted — caller should request WRITE_CALENDAR
        false
    } catch (e: Exception) {
        false
    }
}

// Get the primary visible calendar ID
fun getPrimaryCalendarId(context: Context): Long? {
    return try {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        context.contentResolver.query(uri, projection, "${CalendarContract.Calendars.VISIBLE}=1", null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else null
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

// Data class to represent a simplified schedule item for demonstration (deprecated, see domain model)
data class ScheduleItemOld(
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant
)

// Dummy data for testing (next 7 days of classes)
fun getDummyScheduleItemsForNext7Days(): List<ScheduleItemOld> {
    val scheduleItems = mutableListOf<ScheduleItemOld>()
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)

    for (i in 0 until 7) {
        val date = now.plus(i.toLong(), ChronoUnit.DAYS)

        // Example class 1: Math
        val mathStartTime = date.atZone(zoneId).withHour(9).withMinute(0).toInstant()
        val mathEndTime = date.atZone(zoneId).withHour(10).withMinute(0).toInstant()
        scheduleItems.add(
            ScheduleItemOld(
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
            ScheduleItemOld(
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
