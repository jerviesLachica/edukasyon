package com.edukasyon.studentai.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [ShareCode]: alphabet, length, format validation, and the
 * denylist re-roll guarantee.
 */
class ShareCodeTest {

    // ---- generate() ----

    @Test
    fun `generated codes are 6 chars from the alphabet and never blocked`() {
        repeat(200) {
            val code = ShareCode.generate()
            assertEquals("code length", 6, code.length)
            assertTrue("chars in alphabet: $code", code.all { it in ShareCode.ALPHABET })
            assertTrue("format-valid: $code", ShareCode.isValid(code))
            assertFalse("denylist hit in 200 draws: $code", isBlockedByTestList(code))
            assertFalse("ShareCode agrees it is clean: $code", ShareCode.isBlocked(code))
        }
    }

    @Test
    fun `alphabet excludes ambiguous glyphs`() {
        assertEquals("ABCDEFGHJKMNPQRSTVWXYZ23456789", ShareCode.ALPHABET)
        assertEquals(30, ShareCode.ALPHABET.length)
        for (bad in listOf('I', 'O', 'L', 'U', '0', '1')) {
            assertFalse("alphabet must not contain $bad", ShareCode.ALPHABET.contains(bad))
        }
    }

    // ---- isValid() ----

    @Test
    fun `valid codes accepted`() {
        for (code in listOf("AB23CD", "765432", "ABCDEFGH".take(6), "MNPD23", "AAAAAA", "999999")) {
            assertTrue("should accept $code", ShareCode.isValid(code))
        }
    }

    @Test
    fun `invalid codes rejected - wrong length`() {
        assertFalse(ShareCode.isValid(""))
        assertFalse(ShareCode.isValid("A"))
        assertFalse(ShareCode.isValid("ABCDE"))
        assertFalse(ShareCode.isValid("ABCDEFG"))
        assertFalse(ShareCode.isValid("ABCDEFGH"))
    }

    @Test
    fun `invalid codes rejected - disallowed characters`() {
        // Lowercase is NOT accepted (canonical form is uppercase-only).
        assertFalse(ShareCode.isValid("abcdef"))
        // Ambiguous glyphs deliberately excluded from the alphabet.
        assertFalse(ShareCode.isValid("AB0CDE"))
        assertFalse(ShareCode.isValid("AB1CDE"))
        assertFalse(ShareCode.isValid("ABICDE"))
        assertFalse(ShareCode.isValid("ABOCDE"))
        assertFalse(ShareCode.isValid("ABLCDE"))
        // Symbols and whitespace.
        assertFalse(ShareCode.isValid("AB-CDE"))
        assertFalse(ShareCode.isValid("AB CDE"))
        assertFalse(ShareCode.isValid("AB#\$%CD".take(6)))
    }

    // ---- denylist semantics ----

    @Test
    fun `isBlocked flags known rude substrings`() {
        assertTrue(ShareCode.isBlocked("FUCKER"))
        assertTrue(ShareCode.isBlocked("SHITTY"))
        assertTrue(ShareCode.isBlocked("TWAT12"))
        // Innocuous codes are not blocked.
        assertFalse(ShareCode.isBlocked("STUDY1"))
        assertFalse(ShareCode.isBlocked("MATH23"))
    }

    /** Independent copy of the rude-substring list so the test does not trust the implementation's own list. */
    private fun isBlockedByTestList(code: String): Boolean {
        val rude = listOf("FUCK", "SHIT", "CUNT", "TWAT", "ARSE", "PISS", "DICK", "PENI", "ANAL", "RAPE", "CUMQ")
        return rude.any { code.contains(it) }
    }
}
