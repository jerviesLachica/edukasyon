package com.edukasyon.studentai.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastThemesTest {

    @Test
    fun catalog_fourUniqueThemeIds() {
        assertEquals(4, PodcastThemes.default.size)
        assertEquals(4, PodcastThemes.default.map { it.id }.distinct().size)
        assertEquals(
            listOf("study_duo", "hot_seats", "story_time", "debate_club"),
            PodcastThemes.default.map { it.id },
        )
    }

    @Test
    fun byId_nullOrUnknown_fallsBackToDefault() {
        assertEquals("study_duo", PodcastThemes.byId(null).id)
        assertEquals("study_duo", PodcastThemes.byId("nope").id)
        assertEquals("hot_seats", PodcastThemes.byId("hot_seats").id)
    }

    @Test
    fun voicePairs_matchPlanContract() {
        val byId = PodcastThemes.default.associateBy { it.id }
        // naturalness v2: Study Duo moves to the multilingual Andrew/Ava pair
        assertEquals("andrew" to "ava", pair(byId.getValue("study_duo")))
        assertEquals("jenny" to "davis", pair(byId.getValue("hot_seats")))
        assertEquals("sonia" to "guy", pair(byId.getValue("story_time")))
        assertEquals("aria" to "davis", pair(byId.getValue("debate_club")))
    }

    @Test
    fun studyDuo_neutralBaseProsody_zeroOffsetsForJitter() {
        val t = PodcastThemes.byId("study_duo")
        // naturalness v2: study_duo leans on per-line jitter for variation, so
        // its speaker A base rate/pitch are the library default (null = 0)
        // while B keeps a small offset so the two voices still differ.
        assertEquals(null, t.prosodyA.rate)
        assertEquals(null, t.prosodyA.pitch)
        assertNotEquals(t.prosodyA, t.prosodyB)
    }

    @Test
    fun everyTheme_hasPromptProsodyAndNonBlankFields() {
        for (t in PodcastThemes.default) {
            assertTrue(t.title.isNotBlank() && t.emoji.isNotBlank() && t.blurb.isNotBlank())
            assertTrue(t.scriptPrompt.length > 40)
            // at least one prosody dimension differs between the two speakers
            assertTrue(
                "${t.id}: A and B prosody identical",
                t.prosodyA != t.prosodyB,
            )
        }
    }

    private fun pair(t: PodcastTheme) = t.voiceA to t.voiceB
}
