package com.edukasyon.studentai.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptAuditorTest {

    private fun card(id: String, q: String, a: String) =
        DialogueFixtures.flashcard(id, q, a)

    private fun twoCards() = listOf(
        card("c1", "What is the powerhouse of the cell?", "The mitochondria generate ATP through cellular respiration"),
        card("c2", "Who wrote the Philippine national anthem?", "Jose Palma composed it, with lyrics by Jose Palma published in 1899"),
    )

    private fun fullCoverageScript() = listOf(
        DialogueParser.Line('A', "Okay — what is the powerhouse of the cell?"),
        DialogueParser.Line('B', "That's the mitochondria generate ATP through cellular respiration, easy."),
        DialogueParser.Line('A', "Nice. And who wrote the Philippine national anthem?"),
        DialogueParser.Line('B', "Jose Palma composed it, with lyrics by Jose Palma published in 1899."),
        DialogueParser.Line('A', "Both of them, actually. Funny fact."),
        DialogueParser.Line('B', "Tell me more about respiration next time."),
        DialogueParser.Line('A', "Sure, we will circle back to that."),
        DialogueParser.Line('B', "Meanwhile remember the year, 1899."),
        DialogueParser.Line('A', "Got it, powerhouses and anthems."),
        DialogueParser.Line('B', "Exactly, that is the whole deck."),
    )

    @Test
    fun audit_fullCoverageCleanScript_isOk() {
        val report = ScriptAuditor.audit(fullCoverageScript(), twoCards())
        assertTrue("expected ok, got missing=${report.missingCards} slop=${report.slopHits}", report.ok)
        assertEquals(listOf(1, 2), report.coveredCards)
        assertTrue(report.missingCards.isEmpty())
        assertNull(report.feedbackForRegen)
    }

    @Test
    fun audit_skippedCard_detectedAndNamedInFeedback() {
        val lines = fullCoverageScript().filterNot {
            it.text.contains("Philippine national anthem") || it.text.contains("Jose Palma") || it.text.contains("1899")
        }
        val report = ScriptAuditor.audit(lines, twoCards())
        assertEquals(listOf(2), report.missingCards)
        assertEquals(listOf(1), report.coveredCards)
        assertTrue(!report.ok)
        assertNotNull(report.feedbackForRegen)
        // feedback must name the skipped card by its 1-based number
        assertTrue(report.feedbackForRegen!!.contains("card(s) 2"))
    }

    @Test
    fun audit_coverageViaDistinctiveLongWord() {
        // script uses only the distinctive token, not a 6-word sequence
        val lines = listOf(
            DialogueParser.Line('A', "mitochondria question time"),
            DialogueParser.Line('B', "yes the mitochondria again"),
        )
        val report = ScriptAuditor.audit(lines, listOf(card("c1", "Explain mitochondria briefly", "Mitochondria matter")))
        assertTrue(1 in report.coveredCards)
    }

    @Test
    fun audit_cannedSlopSample_tripsBannedPhrases() {
        val slop = listOf(
            DialogueParser.Line('A', "In this episode we dive into photosynthesis!"),
            DialogueParser.Line('B', "Great question — let's break it down, absolutely! Fantastic stuff."),
            DialogueParser.Line('A', "Photosynthesis converts light into glucose inside chloroplasts."),
            DialogueParser.Line('B', "Now let's move on, welcome back to another topic."),
        )
        val report = ScriptAuditor.audit(slop, listOf(card("c1", "What is photosynthesis?", "Photosynthesis converts light into glucose inside chloroplasts")))
        val bannedHits = report.slopHits.filter { it.startsWith("banned phrase") }
        assertTrue("banned phrases missed: ${report.slopHits}", bannedHits.size >= 5)
        assertTrue(!report.ok)
        assertNotNull(report.feedbackForRegen)
    }

    @Test
    fun audit_sameOpenerTooManyTimes_flagged() {
        val lines = listOf(
            DialogueParser.Line('A', "Next we have mitochondria details"),
            DialogueParser.Line('B', "Next thing, ATP is the energy currency"),
            DialogueParser.Line('A', "Next up, the nucleus stores DNA"),
        )
        val report = ScriptAuditor.audit(lines, emptyList())
        assertTrue(report.slopHits.any { it.contains("\"next\"") })
    }

    @Test
    fun audit_adjacentSameSpeaker_flaggedAsStructureHit() {
        val lines = listOf(
            DialogueParser.Line('A', "mitochondria are powerhouses"),
            DialogueParser.Line('A', "and they make ATP mostly"),
        )
        val report = ScriptAuditor.audit(lines, listOf(card("c1", "Explain mitochondria please now", "Mitochondria make ATP")))
        assertTrue(report.slopHits.any { it.contains("alternating") || it.contains("repeats speaker") })
    }

    @Test
    fun audit_lengthGate_enforced() {
        // 4 cards -> min lines = max(10, 4*3) = 12
        assertEquals(12, ScriptAuditor.minLineGate(4))
        assertEquals(10, ScriptAuditor.minLineGate(2))
        val few = fullCoverageScript().take(6).toMutableList()
        // pad so both cards are still covered via the distinctive-word rule
        few[0] = DialogueParser.Line('A', "mitochondria and jose palma appear here")
        val report = ScriptAuditor.audit(few, twoCards())
        assertTrue(!report.ok)
        assertTrue(report.slopHits.any { it.contains("lines — need at least") })
    }

    @Test
    fun audit_emptyScript_notOk() {
        val report = ScriptAuditor.audit(emptyList(), twoCards())
        assertTrue(!report.ok)
        assertEquals(listOf(1, 2), report.missingCards)
        assertEquals(0, report.lineCount)
    }
}

/** Shared fixtures — plain-ctor Flashcard without needing Room/Android. */
object DialogueFixtures {
    fun flashcard(id: String, q: String, a: String) = com.edukasyon.studentai.domain.model.Flashcard(
        id = id,
        question = q,
        answer = a,
        subjectId = null,
        deckId = null,
        topic = null,
        difficulty = "medium",
        reviewCount = 0,
        correctCount = 0,
        incorrectCount = 0,
        lastReviewedAt = null,
        nextReviewAt = null,
    )
}
