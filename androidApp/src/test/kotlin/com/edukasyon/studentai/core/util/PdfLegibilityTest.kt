package com.edukasyon.studentai.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLegibilityTest {

    @Test
    fun legibleEnglishSentence_returnsTrue() {
        assertTrue(
            ChatAttachmentUtils.isLegibleText(
                "Photosynthesis converts light energy in chloroplasts",
            ),
        )
    }

    @Test
    fun fontEncodedMojibake_returnsFalse() {
        assertFalse(
            ChatAttachmentUtils.isLegibleText(
                "ÏÎ c âÄklIBI ÜiDÕ¥ÖN&Ý < UþI _ÈkŸ-WN",
            ),
        )
    }
}
