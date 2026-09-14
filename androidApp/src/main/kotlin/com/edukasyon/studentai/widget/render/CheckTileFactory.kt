package com.edukasyon.studentai.widget.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache

/**
 * Draws checkbox tiles (rounded square + optional check stroke) as Bitmaps.
 * No font dependency: the check is two stroked path segments, deterministic
 * on every device and density. Results are LRU-cached per state/color/size.
 */
object CheckTileFactory {
    private const val TILE_DP = 28
    private const val CORNER_DP = 7f
    private val cache = LruCache<String, Bitmap>(24)

    fun tile(
        context: Context,
        checked: Boolean,
        fillColor: Int,
        checkColor: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val px = (TILE_DP * density).toInt().coerceAtLeast(1)
        val key = "$checked|$fillColor|$checkColor|$px"
        cache.get(key)?.let { return it }

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = CORNER_DP * density

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), radius, radius, fill)

        if (checked) {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = checkColor
                style = Paint.Style.STROKE
                strokeWidth = (2.6f * density).coerceAtLeast(2f)
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            // Check mark in tile-local coordinates.
            val path = Path().apply {
                moveTo(px * 0.28f, px * 0.54f)
                lineTo(px * 0.45f, px * 0.70f)
                lineTo(px * 0.73f, px * 0.32f)
            }
            canvas.drawPath(path, stroke)
        }

        cache.put(key, bitmap)
        return bitmap
    }

    fun invalidate() = cache.evictAll()
}
