package com.edukasyon.studentai.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueParserTest {

    @Test
    fun parse_cleanAlternatingTags() {
        val script = """
            A: Okay, first up — what even is a mitochondrion?
            B: It's the powerhouse of the cell, makes ATP through respiration.
            A: Wait — really? That simple?
            B: Simple but true. Next card.
        """.trimIndent()
        val lines = DialogueParser.parse(script)
        assertEquals(4, lines.size)
        assertEquals(listOf('A', 'B', 'A', 'B'), lines.map { it.speaker })
        assertTrue(lines[0].text.startsWith("Okay, first up"))
    }

    @Test
    fun parse_caseInsensitiveTags() {
        val lines = DialogueParser.parse("a: hello there\nB: hi back")
        assertEquals(2, lines.size)
        assertEquals('A', lines[0].speaker)
        assertEquals('B', lines[1].speaker)
    }

    @Test
    fun parse_toleratesBulletsAndBoldMarkers() {
        val lines = DialogueParser.parse("- A: **one** thing\n* b: another\n> A: quoted line")
        assertEquals(3, lines.size)
        assertEquals("one thing", lines[0].text)
        assertEquals("another", lines[1].text)
        assertEquals("quoted line", lines[2].text)
    }

    @Test
    fun parse_firstNamedSpeakerMapsA_secondMapsB() {
        val script = """
            Host: Welcome, professor — what powers a cell?
            Prof: Respiration in the mitochondria, mostly.
            Host: And where does that happen?
        """.trimIndent()
        val lines = DialogueParser.parse(script)
        assertEquals(3, lines.size)
        assertEquals(listOf('A', 'B', 'A'), lines.map { it.speaker })
        // label itself must not leak into the spoken text
        assertTrue(lines.all { !it.text.contains("Host") && !it.text.contains("Prof") })
    }

    @Test
    fun parse_moreThanTwoNamedSpeakersFoldIntoB() {
        val lines = DialogueParser.parse("Chen: x\nDiaz: y\nPark: z")
        assertEquals(listOf('A', 'B', 'B'), lines.map { it.speaker })
    }

    @Test
    fun parse_skipsBlankAndUnmarkedProse() {
        val script = "Some intro prose without a speaker.\n\nA: real line\n\nB: another"
        val lines = DialogueParser.parse(script)
        assertEquals(2, lines.size)
        assertEquals("real line", lines[0].text)
    }

    @Test
    fun parse_emptyScript_returnsEmptyList() {
        assertTrue(DialogueParser.parse("").isEmpty())
        assertTrue(DialogueParser.parse("just a blob of essay text\nwith more prose").isEmpty())
    }

    @Test
    fun parse_emptyDialogueAfterTag_dropsLine() {
        assertEquals(1, DialogueParser.parse("A: \nB: something").size)
    }
}
