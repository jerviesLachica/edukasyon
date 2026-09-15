package com.edukasyon.studentai.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Tests for per-page PDF text-layer extraction, including Flate-compressed
 * content streams (the common case for real-world PDFs).
 */
class ChatAttachmentPdfTextLayerTest {

    /** Deflate (zlib header) a content stream, PDF-style. */
    private fun flate(content: String): ByteArray {
        val deflater = Deflater()
        val bytes = content.toByteArray(Charsets.ISO_8859_1)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun streamBody(content: String): String {
        val compressed = flate(content)
        return StringBuilder()
            .append("<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n")
            .append(String(compressed, Charsets.ISO_8859_1))
            .append("\nendstream")
            .toString()
    }

    /**
     * Hand-assembles a 2-page PDF whose content streams are Flate-compressed:
     * page 1 shows TEXT1 via `Tj`, page 2 shows TEXT2 via a `TJ` array.
     * Object numbers are assigned dynamically so `/Contents` refs resolve.
     */
    private fun twoPageCompressedPdf(text1: String, text2: String): ByteArray {
        val c1 = streamBody("BT /F1 12 Tf 72 720 Td ($text1) Tj ET")
        val c2 = streamBody("BT /F1 12 Tf 72 720 Td [($text2) -2 (x)] TJ ET")
        val sb = StringBuilder("%PDF-1.4\n")
        val objStart = mutableMapOf<Int, Int>()
        fun emit(num: Int, body: String) {
            objStart[num] = sb.length
            sb.append("$num 0 obj\n").append(body).append("\nendobj\n")
        }
        emit(1, "<< /Type /Catalog /Pages 2 0 R >>")
        emit(2, "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>")
        emit(3, "<< /Type /Page /Parent 2 0 R /Contents 5 0 R >>")
        emit(4, "<< /Type /Page /Parent 2 0 R /Contents 6 0 R >>")
        emit(5, c1)
        emit(6, c2)
        sb.append("trailer\n<< /Size 7 /Root 1 0 R >>\n%%EOF\n")
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private val words26 =
        "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november " +
            "oscar papa quebec romeo sierra tango uniform victor whiskey xray yankee zulu apricot basket"

    @Test
    fun perPage_extractsFromFlateCompressedStreams() {
        val pdf = twoPageCompressedPdf(words26, "second page text")
        val pages = ChatAttachmentUtils.extractEmbeddedPdfTextPerPage(pdf)
        assertEquals(2, pages.size)
        assertNotNull(pages[0])
        assertTrue(pages[0]!!.contains("alpha"))
        assertTrue(pages[0]!!.contains("basket"))
        assertTrue(pages[1]!!.contains("second"))
        // text from page 1 must not bleed into page 2
        assertEquals(false, pages[1]!!.contains("alpha"))
    }

    @Test
    fun wholeDoc_joinsPerPageAndPassesLegibilityGate() {
        val pdf = twoPageCompressedPdf(words26, "second page text")
        val whole = ChatAttachmentUtils.extractEmbeddedPdfText(pdf)
        assertNotNull(whole)
        assertTrue(whole!!.contains("alpha"))
        assertTrue(whole.contains("second"))
        assertTrue(whole.indexOf("alpha") < whole.indexOf("second"))
    }

    @Test
    fun uncompressedLiteralStreams_stillExtracted() {
        // Regression guard: pre-existing literal-scan path keeps working.
        val long = List(30) { "word$it" }.joinToString(" ")
        val content = "BT /F1 12 Tf ($long) Tj ET"
        val sb = StringBuilder("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n")
        sb.append("4 0 obj\n<< /Length ${content.length} >>\nstream\n").append(content).append("\nendstream\nendobj\n")
        sb.append("trailer\n<< /Size 5 /Root 1 0 R >>\n%%EOF\n")
        val pdf = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val pages = ChatAttachmentUtils.extractEmbeddedPdfTextPerPage(pdf)
        assertEquals(1, pages.size)
        assertTrue(pages[0]!!.contains("word29"))
    }

    @Test
    fun missingPage_withNoContent_isNull() {
        // A page obj with no /Contents resolves to null.
        val sb = StringBuilder("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R >>\nendobj\n")
        sb.append("trailer\n<< /Size 4 /Root 1 0 R >>\n%%EOF\n")
        val pdf = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val pages = ChatAttachmentUtils.extractEmbeddedPdfTextPerPage(pdf)
        assertEquals(1, pages.size)
        assertNull(pages[0])
    }

    @Test
    fun corruptedFlateStream_isSkippedGracefully() {
        val bogus = ByteArray(64) { (it * 7).toByte() }
        val c = StringBuilder()
            .append("<< /Length ${bogus.size} /Filter /FlateDecode >>\nstream\n")
            .append(String(bogus, Charsets.ISO_8859_1))
            .append("\nendstream")
            .toString()
        val sb = StringBuilder("%PDF-1.4\n")
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        sb.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        sb.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>\nendobj\n")
        sb.append("4 0 obj\n").append(c).append("\nendobj\n")
        sb.append("trailer\n<< /Size 5 /Root 1 0 R >>\n%%EOF\n")
        val pdf = sb.toString().toByteArray(Charsets.ISO_8859_1)
        val pages = ChatAttachmentUtils.extractEmbeddedPdfTextPerPage(pdf)
        assertEquals(1, pages.size)
        assertNull(pages[0])
    }

    @Test
    fun usableWordCount_splitsOnWhitespace() {
        assertEquals(0, ChatAttachmentUtils.usableWordCount(""))
        assertEquals(2, ChatAttachmentUtils.usableWordCount("  hello   world \n"))
        assertEquals(3, ChatAttachmentUtils.usableWordCount("a b c"))
    }
}
