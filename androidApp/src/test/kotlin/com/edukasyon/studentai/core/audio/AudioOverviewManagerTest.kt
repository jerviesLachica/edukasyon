package com.edukasyon.studentai.core.audio

import com.edukasyon.studentai.domain.model.JeviDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests around AudioOverviewManager's static helpers (companion) — no
 * Context/Hilt needed. AC2: switching theme/voices must land a different cache
 * file for the same deck contents.
 */
class AudioOverviewManagerTest {

    private fun deck() = JeviDeck(
        id = "deck-1",
        title = "Biology 101",
        description = null,
        subjectId = null,
        sourceNoteId = null,
        colorHex = "#FF0000",
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun cards() = listOf(
        DialogueFixtures.flashcard("c1", "What is a cell?", "The basic unit of life"),
        DialogueFixtures.flashcard("c2", "What is DNA?", "Deoxyribonucleic acid carries genes"),
    )

    @Test
    fun cacheKey_sameTheme_sameDeck_stable() {
        val a = AudioOverviewManager.cacheKeyFor(deck(), cards(), PodcastThemes.byId("study_duo"))
        val b = AudioOverviewManager.cacheKeyFor(deck(), cards(), PodcastThemes.byId("study_duo"))
        assertEquals(a, b)
    }

    @Test
    fun cacheKey_differentTheme_differentKey() {
        val duo = AudioOverviewManager.cacheKeyFor(deck(), cards(), PodcastThemes.byId("study_duo"))
        val hot = AudioOverviewManager.cacheKeyFor(deck(), cards(), PodcastThemes.byId("hot_seats"))
        assertNotEquals(duo, hot)
    }

    @Test
    fun cacheKey_voiceOrProsodyChange_differentKey() {
        val base = PodcastThemes.byId("study_duo")
        val k0 = AudioOverviewManager.cacheKeyFor(deck(), cards(), base)
        val k1 = AudioOverviewManager.cacheKeyFor(deck(), cards(), base.copy(voiceA = "jenny"))
        val k2 = AudioOverviewManager.cacheKeyFor(deck(), cards(), base.copy(prosodyA = base.prosodyA.copy(rate = "+10%")))
        assertNotEquals(k0, k1)
        assertNotEquals(k0, k2)
    }

    @Test
    fun cacheKey_deckContentChange_differentKey() {
        val t = PodcastThemes.default.first()
        assertNotEquals(
            AudioOverviewManager.cacheKeyFor(deck(), cards(), t),
            AudioOverviewManager.cacheKeyFor(deck(), cards().take(1), t),
        )
    }

    @Test
    fun scriptPrompt_containsDialogueContractFooter() {
        val msg = AudioOverviewManager.scriptRequestFor(PodcastThemes.default.first())
        assertTrue(msg.contains("Return ONLY lines prefixed A: or B:"))
        assertTrue(msg.contains("no intro"))
        assertTrue(msg.startsWith(PodcastThemes.default.first().scriptPrompt))
    }

    @Test
    fun exportedDisplayName_sanitizedAndThemed() {
        val name = AudioOverviewManager.exportedDisplayName("Bio: Cell / DNA?", "study_duo")
        assertTrue(name, name.startsWith("JEVI "))
        assertTrue(name, name.endsWith(".mp3"))
        assertTrue(name, !name.contains(':') && !name.contains('/'))
        assertTrue(name, name.contains("Study Duo"))
    }

    @Test
    fun previewRequestsFor_themeAlternatesVoicesWithProsody() {
        val theme = PodcastThemes.byId("hot_seats")
        val reqs = AudioOverviewManager.previewRequestsFor(theme)
        assertEquals(2, reqs.size)
        assertEquals(theme.voiceA, reqs[0].voice)
        assertEquals(theme.voiceB, reqs[1].voice)
        assertEquals(theme.prosodyA.rate, reqs[0].rate)
        assertEquals(theme.prosodyB.pitch, reqs[1].pitch)
    }

    @Test
    fun previewRequestsForVoice_singleNeutralLine() {
        val reqs = AudioOverviewManager.previewRequestsForVoice("jenny")
        assertEquals(1, reqs.size)
        assertEquals("jenny", reqs[0].voice)
        assertEquals(null, reqs[0].rate)
        assertEquals(null, reqs[0].pitch)
    }

    // --- naturalness v2: per-line jitter ---------------------------------------

    @Test
    fun jitterFor_sameLineIndex_isDeterministic() {
        val a = AudioOverviewManager.jitterFor(3, "+4%", "-2Hz")
        val b = AudioOverviewManager.jitterFor(3, "+4%", "-2Hz")
        assertEquals(a, b)
    }

    @Test
    fun jitterFor_acrossTenIndices_atLeastOneDiffers() {
        val outs = (0..9).map { AudioOverviewManager.jitterFor(it, "+4%", "-2Hz") }
        assertTrue("jitter never varies", outs.distinct().size > 1)
    }

    @Test
    fun jitterFor_formatsSignedRatePercentAndSignedPitchHz() {
        for (i in 0..9) {
            val (rate, pitch) = AudioOverviewManager.jitterFor(i, null, null)
            assertTrue("rate '$rate' not +N%/-N%", Regex("""^[+-]\d+%$""").matches(rate))
            assertTrue("pitch '$pitch' not +NHZ", Regex("""^[+-]\d+Hz$""").matches(pitch))
        }
    }

    @Test
    fun jitterFor_respectsBackendClamps_atBothExtremes() {
        for (i in 0..19) {
            val (rate, pitch) = AudioOverviewManager.jitterFor(i, "+100%", "+100Hz")
            assertTrue(rate, rate.removeSuffix("%").toInt() in -50..100)
            assertTrue(pitch, pitch.removeSuffix("Hz").toInt() in -100..100)
            val (r2, p2) = AudioOverviewManager.jitterFor(i, "-50%", "-100Hz")
            assertTrue(r2, r2.removeSuffix("%").toInt() in -50..100)
            assertTrue(p2, p2.removeSuffix("Hz").toInt() in -100..100)
        }
    }

    @Test
    fun jitterFor_nullBaseMeansZero_andStaysWithinJitterWindow() {
        for (i in 0..9) {
            val (rate, pitch) = AudioOverviewManager.jitterFor(i, null, null)
            assertTrue(rate, rate.removeSuffix("%").toInt() in -4..4)
            assertTrue(pitch, pitch.removeSuffix("Hz").toInt() in -15..15)
        }
    }

    @Test
    fun previewRequestsFor_haveNoJitter() {
        // Previews stay on the raw theme prosody (deterministic audition).
        val theme = PodcastThemes.byId("hot_seats")
        val reqs = AudioOverviewManager.previewRequestsFor(theme)
        assertEquals(theme.prosodyA.rate, reqs[0].rate)
        assertEquals(theme.prosodyB.rate, reqs[1].rate)
    }
}
