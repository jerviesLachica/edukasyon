package com.edukasyon.studentai.core.document

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * SHA256-based page cache keyed by (pdfUri, pageIndex).
 * On disk in Room — re-import of identical bytes triggers zero AI calls.
 */
object DocumentPageCache {

    /**
     * Compute a SHA256 digest of the JPEG bytes for a rendered page.
     * Used as the cache key: identical bytes → cache hit.
     */
    fun sha256(jpegBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(jpegBytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Encode JPEG bytes as base64 string for API upload.
     */
    fun toBase64(jpegBytes: ByteArray): String {
        return Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
    }
}
