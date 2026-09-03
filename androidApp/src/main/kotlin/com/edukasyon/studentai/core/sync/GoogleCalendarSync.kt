package com.edukasyon.studentai.core.sync

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import java.util.Calendar
import java.util.TimeZone

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

// Overload for DummyScheduleItem (legacy epoch-millis based, used in settings UI)
fun createCalendarIntent(context: Context, scheduleItem: DummyScheduleItem): Intent {
    return Intent(Intent.ACTION_INSERT).apply {
        setData(CalendarContract.Events.CONTENT_URI)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, scheduleItem.startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, scheduleItem.endMillis)
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
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
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

private fun domainDayToCalendarConstant(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> Calendar.MONDAY
    DayOfWeek.TUESDAY -> Calendar.TUESDAY
    DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
    DayOfWeek.THURSDAY -> Calendar.THURSDAY
    DayOfWeek.FRIDAY -> Calendar.FRIDAY
    DayOfWeek.SATURDAY -> Calendar.SATURDAY
    DayOfWeek.SUNDAY -> Calendar.SUNDAY
}

private fun parseHhMmToMinutes(hhmm: String): Pair<Int, Int> {
    val parts = hhmm.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

/**
 * Compute the next future occurrence of a schedule item (HH:mm + dayOfWeek) as epoch millis.
 * Uses java.util.Calendar (API 1+) so it works on minSdk 24.
 */
private fun ScheduleItem.nextOccurrenceStartMillis(): Long {
    val targetDow = domainDayToCalendarConstant(dayOfWeek)
    val (hour, minute) = parseHhMmToMinutes(startTime)
    val cal = Calendar.getInstance().apply {
        // Walk forward to the target day of week
        while (get(Calendar.DAY_OF_WEEK) != targetDow) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        // If today's occurrence already passed, push to next week
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.WEEK_OF_YEAR, 1)
        }
    }
    return cal.timeInMillis
}

private fun ScheduleItem.nextOccurrenceEndMillis(): Long {
    val targetDow = domainDayToCalendarConstant(dayOfWeek)
    val (hour, minute) = parseHhMmToMinutes(endTime)
    val cal = Calendar.getInstance().apply {
        while (get(Calendar.DAY_OF_WEEK) != targetDow) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.WEEK_OF_YEAR, 1)
        }
    }
    return cal.timeInMillis
}

// Dummy data for testing (next 7 days of classes) — uses epoch millis for API 24 compat
data class DummyScheduleItem(
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long
)

fun getDummyScheduleItemsForNext7Days(): List<DummyScheduleItem> {
    val scheduleItems = mutableListOf<DummyScheduleItem>()
    val now = System.currentTimeMillis()

    for (i in 0 until 7) {
        val dayBase = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, i)
        }
        val mathStart = (dayBase.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val mathEnd = (dayBase.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        scheduleItems.add(
            DummyScheduleItem(
                title = "Math Class",
                description = "Algebra 101",
                location = "Room 101",
                startMillis = mathStart,
                endMillis = mathEnd
            )
        )

        val sciStart = (dayBase.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sciEnd = (dayBase.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        scheduleItems.add(
            DummyScheduleItem(
                title = "Science Lab",
                description = "Chemistry practical",
                location = "Lab 203",
                startMillis = sciStart,
                endMillis = sciEnd
            )
        )
    }
    return scheduleItems
}
