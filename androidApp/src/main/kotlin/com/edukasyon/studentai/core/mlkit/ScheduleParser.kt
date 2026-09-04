package com.edukasyon.studentai.core.mlkit

import android.util.Log
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleItem
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device schedule parser that extracts structured schedule data from OCR text.
 * Eliminates the need for AI vision model calls.
 */
@Singleton
class ScheduleParser @Inject constructor() {

    companion object {
        private const val TAG = "ScheduleParser"

        // Day abbreviations mapping (all values are lists for consistent multi-day expansion)
        private val DAY_MAP: Map<String, List<DayOfWeek>> = mapOf(
            "mon" to listOf(DayOfWeek.MONDAY), "monday" to listOf(DayOfWeek.MONDAY),
            "tue" to listOf(DayOfWeek.TUESDAY), "tuesday" to listOf(DayOfWeek.TUESDAY),
            "tues" to listOf(DayOfWeek.TUESDAY),
            "wed" to listOf(DayOfWeek.WEDNESDAY), "wednesday" to listOf(DayOfWeek.WEDNESDAY),
            "thu" to listOf(DayOfWeek.THURSDAY), "thurs" to listOf(DayOfWeek.THURSDAY),
            "thursday" to listOf(DayOfWeek.THURSDAY),
            "fri" to listOf(DayOfWeek.FRIDAY), "friday" to listOf(DayOfWeek.FRIDAY),
            "sat" to listOf(DayOfWeek.SATURDAY), "saturday" to listOf(DayOfWeek.SATURDAY),
            "sun" to listOf(DayOfWeek.SUNDAY), "sunday" to listOf(DayOfWeek.SUNDAY),
            // Multi-day patterns
            "mwf" to listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            "tth" to listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            "mw" to listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )

        // Time patterns: HH:MM, H:MM, HH:MM AM/PM, H:MM AM/PM
        private val TIME_PATTERN = Pattern.compile(
            """(\d{1,2}):(\d{2})\s*(?:am|pm)?""",
            Pattern.CASE_INSENSITIVE
        )

        // Time range pattern: HH:MM - HH:MM or HH:MM-HH:MM
        private val TIME_RANGE_PATTERN = Pattern.compile(
            """(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})""",
            Pattern.CASE_INSENSITIVE
        )

        // Subject + time pattern (e.g., "Math 09:00-10:00 Room 101")
        private val SUBJECT_LINE_PATTERN = Pattern.compile(
            """(.+?)\s+(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})\s*(.*)""",
            Pattern.CASE_INSENSITIVE
        )

        // Day header patterns (Mon, Tue, etc. as column headers)
        private val DAY_HEADER_PATTERN = Pattern.compile(
            """\b(MON|MONDAY|TUE|TUES|TUESDAY|WED|WEDNESDAY|THU|THURS|THURSDAY|FRI|FRIDAY|SAT|SATURDAY|SUN|SUNDAY)\b""",
            Pattern.CASE_INSENSITIVE
        )

        // Common schedule line patterns
        // "Math 09:00-10:00 Room 101"
        // "CS101 MWF 09:00-10:00 Room 101"
        private val CLASS_LINE_PATTERN = Pattern.compile(
            """(?i)^\s*(.+?)\s+(MWF|TTH|MW|MON|TUE|WED|THU|FRI|SAT|SUN)\s+(\d{1,2}:\d{2})\s*[-–]\s*(\d{1,2}:\d{2})\s*(.*)""")
    }

    /**
     * Parse OCR text and extract schedule items.
     * Returns list of ScheduleItem objects ready to be saved.
     */
    fun parseScheduleText(ocrText: String): ParseResult {
        val lines = ocrText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        val scheduleItems = mutableListOf<ScheduleItem>()
        val uncertainFields = mutableListOf<String>()

        // Try to detect table structure first
        val tableResult = parseAsTable(lines)
        if (tableResult.isNotEmpty()) {
            return ParseResult(tableResult, emptyList())
        }

        // Fallback: parse as linear text
        val linearResult = parseLinearText(lines)
        if (linearResult.isNotEmpty()) {
            return ParseResult(linearResult, listOf("Parsed from linear text, may miss some details"))
        }

        return ParseResult(emptyList(), listOf("Could not parse schedule from OCR text"))
    }

    private fun parseAsTable(lines: List<String>): List<ScheduleItem> {
        // Try to detect column headers (days of week)
        val headerLine = lines.firstOrNull { DAY_HEADER_PATTERN.matcher(it).find() }
            ?: return emptyList()

        val dayColumns = mutableListOf<String>()
        val matcher = DAY_HEADER_PATTERN.matcher(headerLine)
        while (matcher.find()) {
            dayColumns.add(matcher.group().uppercase())
        }

        if (dayColumns.isEmpty()) return emptyList()

        val items = mutableListOf<ScheduleItem>()

        // Parse subsequent lines as rows
        for (line in lines.dropWhile { it != headerLine }.drop(1)) {
            val parts = line.split(Pattern.compile("\\s{2,}")).filter { it.isNotBlank() }
            if (parts.size < 2) continue

            // First part is typically time range or subject
            val timeRange = extractTimeRange(parts.first())
            if (timeRange == null) continue

            val (startTime, endTime) = timeRange
            val subject = parts.drop(1).joinToString(" ")

            // Try to extract day info
            val days = inferDaysFromLine(parts, dayColumns)
            for (day in days) {
                items.add(createScheduleItem(day, startTime, endTime, subject))
            }
        }
        return items
    }

    private fun parseLinearText(lines: List<String>): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()

        for (line in lines) {
            // Try pattern: "Subject 09:00-10:00 Room 101" or "CS101 MWF 09:00-10:00 Room 101"
            val classMatch = CLASS_LINE_PATTERN.matcher(line)
            if (classMatch.matches()) {
                val subject = classMatch.group(1)
                val dayCode = classMatch.group(2).uppercase()
                val startTime = classMatch.group(3)
                val endTime = classMatch.group(4)
                val extra = classMatch.group(5) ?: ""

                val days = parseDayCode(dayCode)
                for (day in days) {
                    items.add(createScheduleItem(day, startTime, endTime, subject, extra))
                }
                continue
            }

            // Try simpler pattern: "Subject 09:00-10:00"
            val simpleMatch = SUBJECT_LINE_PATTERN.matcher(line)
            if (simpleMatch.matches()) {
                val subject = simpleMatch.group(1)
                val startTime = simpleMatch.group(2)
                val endTime = simpleMatch.group(3)
                val extra = simpleMatch.group(4)

                // Try to infer day from context or default to today
                val day = inferDayFromContext(lines)
                items.add(createScheduleItem(day, startTime, endTime, subject, extra))
            }
        }
        return items
    }

    private fun extractTimeRange(text: String): Pair<String, String>? {
        val rangeMatch = TIME_RANGE_PATTERN.matcher(text)
        if (rangeMatch.find()) {
            return rangeMatch.group(1) to rangeMatch.group(2)
        }
        return null
    }

    private fun inferDaysFromLine(parts: List<String>, dayColumns: List<String>): List<DayOfWeek> {
        // If first part matches a day, use that
        val firstPart = parts.first().uppercase()
        for (dayCol in dayColumns) {
            if (firstPart.contains(dayCol)) {
                return listOf(parseDayName(dayCol))
            }
        }
        return listOf(DayOfWeek.MONDAY) // fallback
    }

    private fun parseDayCode(code: String): List<DayOfWeek> {
        return DAY_MAP[code.lowercase()] ?: listOf(DayOfWeek.MONDAY)
    }

    private fun parseDayName(name: String): DayOfWeek {
        return DayOfWeek.entries.find { it.name == name.toUpperCase(Locale.ROOT) } ?: DayOfWeek.MONDAY
    }

    private fun inferDayFromContext(lines: List<String>): DayOfWeek {
        // Simple heuristic: look for day mentions in nearby lines
        for (line in lines) {
            val match = DAY_HEADER_PATTERN.matcher(line)
            if (match.find()) {
                return parseDayName(match.group(1))
            }
        }
        return DayOfWeek.MONDAY
    }

    private fun createScheduleItem(
        day: DayOfWeek,
        startTime: String,
        endTime: String,
        subject: String,
        extra: String = ""
    ): ScheduleItem = ScheduleItem(
        id = UUID.randomUUID().toString(),
        subjectId = null,
        subjectName = subject.trim(),
        teacher = null,
        room = extractRoom(extra),
        building = null,
        dayOfWeek = day,
        startTime = normalizeTime(startTime),
        endTime = normalizeTime(endTime),
        colorHex = "#1A237E",
        notes = null,
        semester = "",
        schoolYear = "",
        isRecurring = true
    )

    private fun extractRoom(extra: String): String? {
        val roomPatterns = listOf(
            Pattern.compile("(?i)Room\\s+(\\S+)"),
            Pattern.compile("(?i)Rm\\.?\\s+(\\S+)"),
            Pattern.compile("(?i)Room\\s*#?\\s*(\\S+)"),
        )
        for (pattern in roomPatterns) {
            val matcher = pattern.matcher(extra)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    private fun normalizeTime(time: String): String {
        val matcher = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(time)
        if (matcher.find()) {
            val hour = matcher.group(1).toInt()
            val minute = matcher.group(2)
            return String.format(Locale.US, "%02d:%s", hour, minute)
        }
        return time
    }
}

data class ParseResult(
    val items: List<ScheduleItem>,
    val uncertainFields: List<String>
)