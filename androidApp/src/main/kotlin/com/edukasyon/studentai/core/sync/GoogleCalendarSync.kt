package com.edukasyon.studentai.core.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import java.time.DayOfWeek as JdkDayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Helper function to create calendar intents from schedule data (manual approval path)
fun createCalendarIntent(context: Context, scheduleItem: ScheduleItem): Intent {
    val startMillis = scheduleItem.nextOccurrenceStartMillis()
    val endMillis = scheduleItem.nextOccurrenceEndMillis()

    return Intent(Intent.ACTION_INSERT).apply {
        setData(CalendarContract.Events.CONTENT_URI)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.TITLE, scheduleItem.subjectName)
        putExtra(CalendarContract.Events.DESCRIPTION, scheduleItem.notes)
        putExtra(CalendarContract.Events.EVENT_LOCATION, scheduleItem.room)
        putExtra(CalendarContract.Events.ALL_DAY, false)
    }
}

// Overload for DummyScheduleItem (legacy Instant-based, used in settings UI)
fun createCalendarIntent(context: Context, scheduleItem: DummyScheduleItem): Intent {
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

// Automatic calendar sync: directly insert or update event via ContentResolver
// Uses scheduleItem.id as UID to prevent duplicates and enable updates
fun insertCalendarEventAutomatically(context: Context, scheduleItem: ScheduleItem): Long? {
    return try {
        val calendarId = getPrimaryCalendarId(context) ?: return null
        val startMillis = scheduleItem.nextOccurrenceStartMillis()
        val endMillis = scheduleItem.nextOccurrenceEndMillis()
        val uid = "schedmate-${scheduleItem.id}@studentai.local"

        // Check if event already exists
        val existingId = findEventByUid(context, calendarId, uid)

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, scheduleItem.subjectName)
            put(CalendarContract.Events.DESCRIPTION, scheduleItem.notes)
            put(CalendarContract.Events.EVENT_LOCATION, scheduleItem.room)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.RRULE, buildRRule(scheduleItem))
            put(CalendarContract.Events.UID_2445, uid) // Stable ID for deduplication (RFC 2445)
        }

        if (existingId != null) {
            // Update existing event
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId)
            context.contentResolver.update(uri, values, null, null)
            existingId
        } else {
            // Insert new event
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) }
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

// Find existing event by UID to prevent duplicates
private fun findEventByUid(context: Context, calendarId: Long, uid: String): Long? {
    val uri = CalendarContract.Events.CONTENT_URI
    val projection = arrayOf(CalendarContract.Events._ID)
    val selection = "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.UID_2445}=?"
    val selectionArgs = arrayOf(calendarId.toString(), uid)

    return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
}

// Build a recurrence rule so the event repeats every week on the same day
private fun buildRRule(scheduleItem: ScheduleItem): String {
    val dayMap = mapOf(
        DayOfWeek.MONDAY to "MO",
        DayOfWeek.TUESDAY to "TU",
        DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR",
        DayOfWeek.SATURDAY to "SA",
        DayOfWeek.SUNDAY to "SU",
    )
    val day = dayMap[scheduleItem.dayOfWeek] ?: "MO"
    return "FREQ=WEEKLY;BYDAY=$day"
}

// Get the primary visible calendar ID
fun getPrimaryCalendarId(context: Context): Long? {
    return try {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        context.contentResolver.query(uri, projection, "${CalendarContract.Calendars.VISIBLE}=1", null, null)?.use { cursor ->
            // Prefer Google's primary calendar; otherwise first visible.
            var fallbackId: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val isPrimary = cursor.getInt(1) == 1
                val accountType = cursor.getString(2) ?: ""
                if (isPrimary && accountType == "com.google") return id
                if (fallbackId == null) fallbackId = id
            }
            fallbackId
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Compute the next future occurrence of a schedule item (HH:mm + dayOfWeek) as epoch millis.
 * If today is the day and the time has passed, returns next week's instance.
 */
private fun ScheduleItem.nextOccurrenceStartMillis(): Long {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val jdkDow = toJdkDayOfWeek(dayOfWeek)
    val candidate = if (today.dayOfWeek == jdkDow) {
        today
    } else {
        today.with(TemporalAdjusters.next(jdkDow))
    }
    val time = LocalTime.parse(this.startTime, DateTimeFormatter.ofPattern("HH:mm"))
    return candidate.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

private fun ScheduleItem.nextOccurrenceEndMillis(): Long {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val jdkDow = toJdkDayOfWeek(dayOfWeek)
    val candidate = if (today.dayOfWeek == jdkDow) {
        today
    } else {
        today.with(TemporalAdjusters.next(jdkDow))
    }
    val time = LocalTime.parse(this.endTime, DateTimeFormatter.ofPattern("HH:mm"))
    return candidate.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

private fun toJdkDayOfWeek(day: DayOfWeek): JdkDayOfWeek = when (day) {
    DayOfWeek.MONDAY -> JdkDayOfWeek.MONDAY
    DayOfWeek.TUESDAY -> JdkDayOfWeek.TUESDAY
    DayOfWeek.WEDNESDAY -> JdkDayOfWeek.WEDNESDAY
    DayOfWeek.THURSDAY -> JdkDayOfWeek.THURSDAY
    DayOfWeek.FRIDAY -> JdkDayOfWeek.FRIDAY
    DayOfWeek.SATURDAY -> JdkDayOfWeek.SATURDAY
    DayOfWeek.SUNDAY -> JdkDayOfWeek.SUNDAY
}

// Dummy data for testing (next 7 days of classes) — uses Instant for legacy callers
data class DummyScheduleItem(
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant
)

fun getDummyScheduleItemsForNext7Days(): List<DummyScheduleItem> {
    val scheduleItems = mutableListOf<DummyScheduleItem>()
    val now = Instant.now()
    val zoneId = ZoneId.systemDefault()

    for (i in 0 until 7) {
        val date = now.plus(i.toLong(), ChronoUnit.DAYS)

        val mathStartTime = date.atZone(zoneId).withHour(9).withMinute(0).toInstant()
        val mathEndTime = date.atZone(zoneId).withHour(10).withMinute(0).toInstant()
        scheduleItems.add(
            DummyScheduleItem(
                title = "Math Class",
                description = "Algebra 101",
                location = "Room 101",
                startTime = mathStartTime,
                endTime = mathEndTime
            )
        )

        val scienceStartTime = date.atZone(zoneId).withHour(11).withMinute(0).toInstant()
        val scienceEndTime = date.atZone(zoneId).withHour(12).withMinute(0).toInstant()
        scheduleItems.add(
            DummyScheduleItem(
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
