package com.edukasyon.studentai.widget.render

/**
 * Framework-free hex color parsing to packed ARGB ints for RemoteViews.
 * Accepts #RGB, #RRGGBB, #AARRGGBB (with or without '#'). Never throws.
 */
object ColorInts {
    fun parse(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return try {
            var h = hex.trim().removePrefix("#")
            if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
            if (h.length == 6) h = "FF$h"
            if (h.length != 8) return fallback
            h.toLong(16).toInt()
        } catch (e: Exception) {
            fallback
        }
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
