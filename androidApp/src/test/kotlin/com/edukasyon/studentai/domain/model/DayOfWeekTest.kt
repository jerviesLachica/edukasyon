package com.edukasyon.studentai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayOfWeekTest {
    @Test
    fun fromString_acceptsEnumNames() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromString("MONDAY"))
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("wednesday"))
    }

    @Test
    fun fromString_acceptsDisplayNames() {
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("Wednesday"))
        assertEquals(DayOfWeek.FRIDAY, DayOfWeek.fromString("friday"))
    }

    @Test
    fun fromString_acceptsAbbreviations() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromString("Mon"))
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("Wed"))
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("WED"))
        assertEquals(DayOfWeek.THURSDAY, DayOfWeek.fromString("R"))
        assertEquals(DayOfWeek.THURSDAY, DayOfWeek.fromString("Thur"))
    }

    @Test
    fun fromString_acceptsNumericIsoDays() {
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromString("1"))
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("3"))
        assertEquals(DayOfWeek.SUNDAY, DayOfWeek.fromString("7"))
        assertEquals(DayOfWeek.SUNDAY, DayOfWeek.fromString("0"))
    }

    @Test
    fun fromString_trimsAndUsesFirstToken() {
        assertEquals(DayOfWeek.WEDNESDAY, DayOfWeek.fromString("  Wed.  "))
        assertEquals(DayOfWeek.MONDAY, DayOfWeek.fromString("Monday/Wednesday"))
    }

    @Test
    fun fromString_unknownReturnsNull() {
        assertNull(DayOfWeek.fromString(""))
        assertNull(DayOfWeek.fromString("notaday"))
    }
}
