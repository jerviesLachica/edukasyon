package com.edukasyon.studentai.core.share

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire format for private share links ("share my schedule/deck via a 6-char code").
 *
 * Everything here is pure-JVM JSON logic — no Android, no Firestore — so it is
 * unit-testable on the desktop. [ShareRepository] wraps Firestore around these types.
 *
 * Format: a [ShareEnvelope] whose `payloadJson` holds a kind-specific inner JSON:
 *  - kind "schedule": an object with a single "classes" array of SharedClass.
 *  - kind "deck": an object with title, optional description, colorHex, and a "cards" array.
 */

@Serializable
data class SharedClass(
    val subject: String,
    val teacher: String?,
    val room: String?,
    val day: String,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class SharedCard(
    val question: String,
    val answer: String,
    val topic: String?,
)

@Serializable
data class ShareEnvelope(
    val v: Int = 1,
    val kind: String,
    val createdAt: Long,
    val payloadJson: String,
)

/** Outcome of parsing an envelope. Everything unrecoverable funnels into [Invalid]. */
sealed interface SharePayloadResult {
    data class ScheduleOk(val classes: List<SharedClass>) : SharePayloadResult
    data class DeckOk(
        val title: String,
        val description: String?,
        val colorHex: String,
        val cards: List<SharedCard>,
    ) : SharePayloadResult

    data class Invalid(val reason: String) : SharePayloadResult
}

@Serializable
private data class ScheduleInner(val classes: List<SharedClass>)

@Serializable
private data class DeckInner(
    val title: String,
    val description: String?,
    val colorHex: String,
    val cards: List<SharedCard>,
)

/**
 * What lives at Firestore `shares/{code}`. Deliberately lives with the pure
 * payload types: it only touches kotlinx-serialization and plain collections,
 * so expiry decisions and map round-trips are unit-testable without Firestore.
 */
data class ShareDocument(
    val kind: String,
    val payloadJson: String,
    val createdBy: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val views: Int = 0,
) {
    /** A share lives for 30 days from creation. Expired at exactly [expiresAt]. */
    fun isExpiredAt(instant: Long): Boolean = instant >= expiresAt

    /** Firestore-safe field map: only Strings/Longs/Ints/nulls, no Android types. */
    fun toMap(): Map<String, Any?> = mapOf(
        "kind" to kind,
        "payloadJson" to payloadJson,
        "createdBy" to createdBy,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt,
        "views" to views,
    )

    companion object {
        const val TTL_MS = 30L * 24 * 60 * 60 * 1000

        fun new(
            kind: String,
            payloadJson: String,
            createdBy: String?,
            createdAt: Long = System.currentTimeMillis(),
        ): ShareDocument = ShareDocument(
            kind = kind,
            payloadJson = payloadJson,
            createdBy = createdBy,
            createdAt = createdAt,
            expiresAt = createdAt + TTL_MS,
            views = 0,
        )

        /** Tolerant of Firestore's habit of returning every number as Long. */
        fun fromMap(map: Map<String, Any?>): ShareDocument? {
            val kind = map["kind"] as? String ?: return null
            val payloadJson = map["payloadJson"] as? String ?: return null
            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: return null
            val expiresAt = (map["expiresAt"] as? Number)?.toLong() ?: return null
            val views = (map["views"] as? Number)?.toInt() ?: 0
            return ShareDocument(
                kind = kind,
                payloadJson = payloadJson,
                createdBy = map["createdBy"] as? String,
                createdAt = createdAt,
                expiresAt = expiresAt,
                views = views,
            )
        }
    }
}

object SharePayload {
    const val KIND_SCHEDULE = "schedule"
    const val KIND_DECK = "deck"

    /** Hard caps: a share payload is student content, not a file drop. */
    const val MAX_CLASSES = 60
    const val MAX_CARDS = 500
    const val MAX_PAYLOAD_CHARS = 200_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSchedule(classes: List<SharedClass>): String =
        json.encodeToString(ScheduleInner(classes))

    fun encodeDeck(
        title: String,
        description: String?,
        colorHex: String,
        cards: List<SharedCard>,
    ): String = json.encodeToString(DeckInner(title, description, colorHex, cards))

    /** Wraps a kind-specific payload into an envelope, stamping creation time. */
    fun envelopeFor(kind: String, payloadJson: String, createdAt: Long = System.currentTimeMillis()): ShareEnvelope =
        ShareEnvelope(kind = kind, createdAt = createdAt, payloadJson = payloadJson)

    fun encodeEnvelope(envelope: ShareEnvelope): String = json.encodeToString(envelope)

    /** The envelope's `kind` when it decodes and is a kind we know, else null. */
    fun decodePayloadKind(envelopeJson: String): String? =
        decodeEnvelope(envelopeJson)?.kind?.takeIf { it == KIND_SCHEDULE || it == KIND_DECK }

    fun decodeEnvelope(envelopeJson: String): ShareEnvelope? =
        runCatching { json.decodeFromString<ShareEnvelope>(envelopeJson) }.getOrNull()

    /**
     * Full parse + guard pass over an encoded [ShareEnvelope]. Never throws:
     * malformed JSON, unknown kinds, oversize payloads, and broken inner
     * payloads all come back as [SharePayloadResult.Invalid].
     */
    fun parse(envelopeJson: String): SharePayloadResult {
        if (envelopeJson.isBlank()) return SharePayloadResult.Invalid("empty envelope")
        val envelope = decodeEnvelope(envelopeJson)
            ?: return SharePayloadResult.Invalid("malformed envelope")
        if (envelope.v != 1) return SharePayloadResult.Invalid("unsupported version ${envelope.v}")
        if (envelope.payloadJson.length > MAX_PAYLOAD_CHARS) {
            return SharePayloadResult.Invalid("payload too large (${envelope.payloadJson.length} chars)")
        }
        return when (envelope.kind) {
            KIND_SCHEDULE -> runCatching { json.decodeFromString<ScheduleInner>(envelope.payloadJson) }
                .fold(
                    onSuccess = { inner ->
                        if (inner.classes.size > MAX_CLASSES) {
                            SharePayloadResult.Invalid("too many classes (${inner.classes.size})")
                        } else {
                            SharePayloadResult.ScheduleOk(inner.classes)
                        }
                    },
                    onFailure = { SharePayloadResult.Invalid("malformed schedule payload") },
                )

            KIND_DECK -> runCatching { json.decodeFromString<DeckInner>(envelope.payloadJson) }
                .fold(
                    onSuccess = { inner ->
                        if (inner.cards.size > MAX_CARDS) {
                            SharePayloadResult.Invalid("too many cards (${inner.cards.size})")
                        } else {
                            SharePayloadResult.DeckOk(inner.title, inner.description, inner.colorHex, inner.cards)
                        }
                    },
                    onFailure = { SharePayloadResult.Invalid("malformed deck payload") },
                )

            else -> SharePayloadResult.Invalid("unknown kind '${envelope.kind}'")
        }
    }
}
