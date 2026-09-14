package com.edukasyon.studentai.core.share

import java.security.SecureRandom

/**
 * Private share codes: 6 characters from a Crockford-style alphabet that drops
 * the glyphs users confuse when typing or reading aloud (I, O, L, U, 0, 1).
 * Codes are hand-entered by recipients, so anything rude-shaped is re-rolled.
 */
object ShareCode {
    /** 32 unambiguous characters — no I, O, L, U, 0, 1. */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
    const val LENGTH = 6

    /**
     * Substrings that must never surface in a user-visible code. Full rude
     * words plus a few lookalike fragments; uppercase-only since codes are
     * uppercase by construction.
     */
    private val DENYLIST = listOf(
        "FUCK", "SHIT", "CUNT", "TWAT", "ARSE", "PISS", "DICK", "PENI", "ANAL",
        "RAPE", "CUMQ", "FCK", "SHT", "KKE", "BICH", "DAMN", "WANK",
    )

    private val random = SecureRandom()

    /** Generate a fresh, format-valid, denylist-clean 6-char code (re-rolls on hits). */
    fun generate(): String {
        while (true) {
            val code = buildString(LENGTH) {
                repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
            }
            if (!isBlocked(code)) return code
        }
    }

    /** Canonical form check: exactly 6 chars, alphabet only (uppercase is required). */
    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /** True when the code contains a denylisted rude substring. */
    fun isBlocked(code: String): Boolean =
        DENYLIST.any { bad -> code.contains(bad) }
}
