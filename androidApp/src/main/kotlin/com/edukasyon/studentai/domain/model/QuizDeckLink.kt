package com.edukasyon.studentai.domain.model

private const val DECK_PREFIX = "jevi-deck:"

fun Quiz.withDeckId(deckId: String?): Quiz = copy(
    sourceNoteId = deckId?.let { "$DECK_PREFIX$it" } ?: sourceNoteId,
)

fun Quiz.deckId(): String? = sourceNoteId
    ?.takeIf { it.startsWith(DECK_PREFIX) }
    ?.removePrefix(DECK_PREFIX)

fun encodeQuizDeckId(deckId: String): String = "$DECK_PREFIX$deckId"
