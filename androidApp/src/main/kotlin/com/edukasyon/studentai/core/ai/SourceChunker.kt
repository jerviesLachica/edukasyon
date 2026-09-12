package com.edukasyon.studentai.core.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Citation ingestion primitives: chunking, vector packing, cosine ranking.
 * Pure Kotlin, no Android dependencies — unit-testable on JVM.
 */
object SourceChunker {
    /** ~500 tokens at ~4 chars/token. */
    const val CHUNK_CHARS = 2000
    /** Overlap so claims spanning a boundary stay retrievable. */
    const val OVERLAP_CHARS = 200

    fun chunk(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= CHUNK_CHARS) return listOf(clean)
        val out = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + CHUNK_CHARS).coerceAtMost(clean.length)
            if (end < clean.length) {
                // Prefer a sentence/word boundary near the cut.
                val window = clean.substring(start, end)
                val cut = window.lastIndexOf(". ")
                    .takeIf { it > CHUNK_CHARS / 2 }
                    ?: window.lastIndexOf(' ').takeIf { it > CHUNK_CHARS / 2 }
                    ?: (CHUNK_CHARS - 1)
                end = start + cut + 1
            }
            out.add(clean.substring(start, end).trim())
            if (end >= clean.length) break
            start = (end - OVERLAP_CHARS).coerceAtLeast(start + 1)
        }
        return out.filter { it.isNotEmpty() }
    }
}

object VectorCodecs {
    fun floatsToBytes(vec: List<Double>): ByteArray {
        val buf = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vec.forEach { buf.putFloat(it.toFloat()) }
        return buf.array()
    }

    fun bytesToFloats(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.float }
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }
}
