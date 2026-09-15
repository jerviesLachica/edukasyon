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
}
