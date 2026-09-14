package com.edukasyon.studentai.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the share payload encode/decode guards and the
 * [ShareDocument] expiry + Firestore-map round-trip. No Android, no Firestore.
 */
class SharePayloadTest {

    private val day = "Monday"

    private fun classAt(i: Int) = SharedClass(
        subject = "Subject$i",
        teacher = "T$i",
        room = "R$i",
        day = day,
        startTime = "08:0$i",
        endTime = "09:0$i",
    )

    private fun cardAt(i: Int) = SharedCard(
        question = "Q$i?",
        answer = "A$i",
        topic = if (i % 2 == 0) "Topic${i / 10}" else null,
    )

    // ---- schedule round-trip ----

    @Test
    fun `schedule round-trips through envelope`() {
        val classes = (1..5).map { classAt(it) }
        val payload = SharePayload.encodeSchedule(classes)
        assertTrue("payload is JSON of {classes}", payload.contains("\"classes\""))

        val env = SharePayload.envelopeFor(SharePayload.KIND_SCHEDULE, payload, createdAt = 123L)
        val encoded = SharePayload.encodeEnvelope(env)

        assertEquals("schedule", SharePayload.decodePayloadKind(encoded))
        val result = SharePayload.parse(encoded)
        assertTrue("expected ScheduleOk, got $result", result is SharePayloadResult.ScheduleOk)
        assertEquals(classes, (result as SharePayloadResult.ScheduleOk).classes)
    }

    @Test
    fun `schedule round-trip preserves nullable fields`() {
        val classes = listOf(SharedClass("Math", null, null, "Tuesday", "10:00", "11:00"))
        val json = SharePayload.encodeEnvelope(
            SharePayload.envelopeFor(
                SharePayload.KIND_SCHEDULE,
                SharePayload.encodeSchedule(classes),
            ),
        )
        val result = SharePayload.parse(json)
        assertTrue(result is SharePayloadResult.ScheduleOk)
        val restored = (result as SharePayloadResult.ScheduleOk).classes.single()
        assertNull(restored.teacher)
        assertNull(restored.room)
        assertEquals(classes.single(), restored)
    }

    // ---- deck round-trip ----

    @Test
    fun `deck round-trips through envelope`() {
        val cards = (1..12).map { cardAt(it) }
        val payload = SharePayload.encodeDeck("Algebra 101", description = "midterms", colorHex = "#FF8800", cards = cards)
        assertTrue("payload is JSON of cards", payload.contains("\"cards\""))

        val env = SharePayload.envelopeFor(SharePayload.KIND_DECK, payload, createdAt = 456L)
        val encoded = SharePayload.encodeEnvelope(env)

        assertEquals("deck", SharePayload.decodePayloadKind(encoded))
        val result = SharePayload.parse(encoded)
        assertTrue("expected DeckOk, got $result", result is SharePayloadResult.DeckOk)
        val ok = result as SharePayloadResult.DeckOk
        assertEquals("Algebra 101", ok.title)
        assertEquals("midterms", ok.description)
        assertEquals("#FF8800", ok.colorHex)
        assertEquals(cards, ok.cards)
    }

    @Test
    fun `deck round-trip preserves null description and null topics`() {
        val cards = listOf(SharedCard("Q", "A", null))
        val json = SharePayload.encodeEnvelope(
            SharePayload.envelopeFor(
                SharePayload.KIND_DECK,
                SharePayload.encodeDeck("T", description = null, colorHex = "#000000", cards = cards),
            ),
        )
        val result = SharePayload.parse(json)
        assertTrue(result is SharePayloadResult.DeckOk)
        val ok = result as SharePayloadResult.DeckOk
        assertNull(ok.description)
        assertNull(ok.cards.single().topic)
    }

    // ---- guards ----

    @Test
    fun `61 classes rejected, 60 accepted`() {
        val tooMany = SharePayload.encodeSchedule((1..61).map { classAt(it) })
        val manyResult = SharePayload.parse(
            SharePayload.encodeEnvelope(SharePayload.envelopeFor(SharePayload.KIND_SCHEDULE, tooMany)),
        )
        assertTrue("expected Invalid, got $manyResult", manyResult is SharePayloadResult.Invalid)

        val justRight = SharePayload.encodeSchedule((1..60).map { classAt(it) })
        val okResult = SharePayload.parse(
            SharePayload.encodeEnvelope(SharePayload.envelopeFor(SharePayload.KIND_SCHEDULE, justRight)),
        )
        assertTrue("60 classes must be accepted, got $okResult", okResult is SharePayloadResult.ScheduleOk)
    }

    @Test
    fun `501 cards rejected, 500 accepted`() {
        val tooMany = SharePayload.encodeDeck("T", null, "#FFFFFF", (1..501).map { cardAt(it) })
        val manyResult = SharePayload.parse(
            SharePayload.encodeEnvelope(SharePayload.envelopeFor(SharePayload.KIND_DECK, tooMany)),
        )
        assertTrue("expected Invalid, got $manyResult", manyResult is SharePayloadResult.Invalid)

        val justRight = SharePayload.encodeDeck("T", null, "#FFFFFF", (1..500).map { cardAt(it) })
        val okResult = SharePayload.parse(
            SharePayload.encodeEnvelope(SharePayload.envelopeFor(SharePayload.KIND_DECK, justRight)),
        )
        assertTrue("500 cards must be accepted, got $okResult", okResult is SharePayloadResult.DeckOk)
    }

    @Test
    fun `payloadJson over 200000 chars rejected`() {
        // Single class keeps the count guard out of the way; the giant subject trips the size guard.
        val huge = SharePayload.encodeSchedule(listOf(classAt(1).copy(subject = "X".repeat(250_000))))
        assertTrue("payload really is oversized", huge.length > 200_000)
        val result = SharePayload.parse(
            SharePayload.encodeEnvelope(SharePayload.envelopeFor(SharePayload.KIND_SCHEDULE, huge)),
        )
        assertTrue("expected Invalid, got $result", result is SharePayloadResult.Invalid)
    }

    @Test
    fun `unknown kind and malformed json are Invalid, not exceptions`() {
        val bogus = """{"v":1,"kind":"homework","createdAt":1,"payloadJson":"{}"}"""
        assertTrue(SharePayload.parse(bogus) is SharePayloadResult.Invalid)

        assertTrue(SharePayload.parse("this is not json") is SharePayloadResult.Invalid)
        assertTrue(SharePayload.parse("") is SharePayloadResult.Invalid)
        assertNull(SharePayload.decodePayloadKind("not json"))
        assertNull(SharePayload.decodePayloadKind("""{"kind":"nope"}"""))

        // Valid envelope, known kind, broken inner payload.
        val brokenInner = SharePayload.encodeEnvelope(
            SharePayload.envelopeFor(SharePayload.KIND_SCHEDULE, "{oops", createdAt = 1L),
        )
        assertTrue(SharePayload.parse(brokenInner) is SharePayloadResult.Invalid)

        // Deck payload with a card missing a required field must not parse.
        val badCard = """{"title":"t","colorHex":"#FFF","cards":[{"question":"q"}]}"""
        val badCardEnv = SharePayload.encodeEnvelope(
            SharePayload.envelopeFor(SharePayload.KIND_DECK, badCard, createdAt = 1L),
        )
        assertTrue(SharePayload.parse(badCardEnv) is SharePayloadResult.Invalid)
    }

    @Test
    fun `envelope json keeps version kind createdAt`() {
        val env = ShareEnvelope(kind = SharePayload.KIND_DECK, createdAt = 99L, payloadJson = "{}")
        assertEquals(1, env.v) // default version
        val encoded = SharePayload.encodeEnvelope(env)
        val decoded = SharePayload.decodeEnvelope(encoded)
        assertNotNull(decoded)
        assertEquals(env, decoded)
    }

    // ---- ShareDocument expiry boundary ----

    private val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000

    @Test
    fun `new document expires exactly 30 days after creation`() {
        val doc = ShareDocument.new(
            kind = SharePayload.KIND_SCHEDULE,
            payloadJson = "{}",
            createdBy = "uid-1",
            createdAt = 1_000L,
        )
        assertEquals(1_000L + thirtyDaysMs, doc.expiresAt)
        assertEquals(thirtyDaysMs, doc.expiresAt - doc.createdAt)
    }

    @Test
    fun `expiry decision at boundary`() {
        val doc = ShareDocument.new(kind = "deck", payloadJson = "{}", createdBy = null, createdAt = 0L)
        assertFalse("never expired at creation", doc.isExpiredAt(0L))
        assertFalse("one ms before expiry is still alive", doc.isExpiredAt(doc.expiresAt - 1))
        assertTrue("expired exactly at expiresAt", doc.isExpiredAt(doc.expiresAt))
        assertTrue("expired after expiresAt", doc.isExpiredAt(doc.expiresAt + 1))
    }

    // ---- ShareDocument toMap / fromMap ----

    @Test
    fun `toMap fromMap round-trip`() {
        val doc = ShareDocument(
            kind = SharePayload.KIND_DECK,
            payloadJson = """{"title":"x"}""",
            createdBy = "uid-9",
            createdAt = 5_000L,
            expiresAt = 6_000L,
            views = 3,
        )
        val map = doc.toMap()
        assertEquals("deck", map["kind"])
        assertEquals(3, (map["views"] as Number).toInt())
        val restored = ShareDocument.fromMap(map)
        assertEquals(doc, restored)
    }

    @Test
    fun `fromMap tolerates firestore long numbers and null creator`() {
        // Firestore hands back every number as Long, and a missing createdBy as null.
        val map = mapOf(
            "kind" to "schedule",
            "payloadJson" to "[]",
            "createdBy" to null,
            "createdAt" to 10L,
            "expiresAt" to 20L,
            "views" to 7L,
        )
        val doc = ShareDocument.fromMap(map)
        assertNotNull(doc)
        assertEquals(
            ShareDocument("schedule", "[]", null, 10L, 20L, 7),
            doc,
        )
    }

    @Test
    fun `fromMap returns null for missing or wrong-typed fields`() {
        assertNull(ShareDocument.fromMap(emptyMap()))
        assertNull(ShareDocument.fromMap(mapOf("kind" to "deck")))
        assertNull(
            ShareDocument.fromMap(
                mapOf(
                    "kind" to "deck",
                    "payloadJson" to 42, // wrong type
                    "createdAt" to 1L,
                    "expiresAt" to 2L,
                    "views" to 0L,
                ),
            ),
        )
    }
}
