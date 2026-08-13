package com.edukasyon.studentai.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import com.edukasyon.studentai.ui.theme.parseHexColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object WidgetBackgroundGenerator {
    private const val CACHE_MAX = 12
    private val cache = LruCache<String, Bitmap>(CACHE_MAX)

    fun getBitmap(
        context: Context,
        preset: WidgetDesignPreset,
        colors: WidgetDesignColors,
        widthDp: Int = 160,
        heightDp: Int = 160
    ): Bitmap {
        if (preset == WidgetDesignPreset.MINIMAL) {
            val color = parseAndroidColor(colors.color1, Color.parseColor("#F3F4F6"))
            val key = "minimal|$color|${widthDp}x$heightDp|${context.resources.displayMetrics.density}"
            cache.get(key)?.let { return it }
            val bitmap = solidBitmap(context, color, widthDp, heightDp)
            cache.put(key, bitmap)
            return bitmap
        }

        val key = "${preset.name}|${colors.cacheKey()}|${widthDp}x$heightDp|${context.resources.displayMetrics.density}"
        cache.get(key)?.let { return it }

        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (preset) {
            WidgetDesignPreset.CORAL_CHEVRON -> drawCoralChevron(canvas, widthPx, heightPx, colors)
            WidgetDesignPreset.HEX_DARK -> drawHexDark(canvas, widthPx, heightPx, colors)
            WidgetDesignPreset.DOT_GRID -> drawDotGrid(canvas, widthPx, heightPx, colors)
            WidgetDesignPreset.LINE_GRID -> drawLineGrid(canvas, widthPx, heightPx, colors)
            WidgetDesignPreset.MINIMAL -> canvas.drawColor(parseAndroidColor(colors.color1, Color.parseColor("#F3F4F6")))
        }

        cache.put(key, bitmap)
        return bitmap
    }

    fun invalidateCache() {
        cache.evictAll()
    }

    private fun solidBitmap(context: Context, color: Int, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(color)
        }
    }

    private fun drawCoralChevron(canvas: Canvas, width: Int, height: Int, colors: WidgetDesignColors) {
        val c1 = parseAndroidColor(colors.color1, Color.parseColor("#F8B195"))
        val c2 = parseAndroidColor(colors.color2, Color.parseColor("#355C7D"))

        val radial = RadialGradient(
            width / 2f,
            height / 2f,
            min(width, height) * 0.75f,
            intArrayOf(c1, c2),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = radial }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = adjustAlpha(c1, 0.35f)
            strokeWidth = width * 0.015f
            style = Paint.Style.STROKE
        }
        val spacing = width * 0.08f
        var offset = -height.toFloat()
        while (offset < width + height) {
            canvas.drawLine(offset, 0f, offset + height, height.toFloat(), stripePaint)
            offset += spacing
        }
        offset = -height.toFloat()
        while (offset < width + height) {
            canvas.drawLine(offset + height, 0f, offset, height.toFloat(), stripePaint)
            offset += spacing
        }

        val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = adjustAlpha(c2, 0.18f)
            style = Paint.Style.FILL
        }
        val diamondSize = width * 0.12f
        var y = -diamondSize
        var row = 0
        while (y < height + diamondSize) {
            var x = if (row % 2 == 0) -diamondSize else -diamondSize / 2f
            while (x < width + diamondSize) {
                drawDiamond(canvas, x, y, diamondSize, diamondPaint)
                x += diamondSize * 1.6f
            }
            y += diamondSize * 1.2f
            row++
        }
    }

    private fun drawHexDark(canvas: Canvas, width: Int, height: Int, colors: WidgetDesignColors) {
        val palette = listOf(
            parseAndroidColor(colors.color1, Color.parseColor("#1D1D1D")),
            parseAndroidColor(colors.color2, Color.parseColor("#4E4F51")),
            parseAndroidColor(colors.color3 ?: "#3C3C3C", Color.parseColor("#3C3C3C"))
        )
        canvas.drawColor(palette[0])

        val radius = width * 0.055f
        val hexHeight = radius * 2f
        val hexWidth = sqrt(3f) * radius
        val vertStep = hexHeight * 0.75f

        var row = 0
        var y = -hexHeight
        while (y < height + hexHeight) {
            var x = if (row % 2 == 0) -hexWidth else -hexWidth / 2f
            var col = 0
            while (x < width + hexWidth) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette[(row + col) % palette.size]
                    style = Paint.Style.FILL
                }
                drawHexagon(canvas, x + hexWidth / 2f, y + radius, radius * 0.92f, paint)
                x += hexWidth
                col++
            }
            y += vertStep
            row++
        }
    }

    private fun drawDotGrid(canvas: Canvas, width: Int, height: Int, colors: WidgetDesignColors) {
        val bg = parseAndroidColor(colors.color1, Color.parseColor("#313131"))
        val dot = parseAndroidColor(colors.color2, Color.WHITE)
        canvas.drawColor(bg)

        val spacing = width * 0.075f // ~30px at common densities
        val dotRadius = spacing * 0.08f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = adjustAlpha(dot, 0.55f)
            style = Paint.Style.FILL
        }
        var y = spacing / 2f
        while (y < height) {
            var x = spacing / 2f
            while (x < width) {
                canvas.drawCircle(x, y, dotRadius, dotPaint)
                x += spacing
            }
            y += spacing
        }
    }

    private fun drawLineGrid(canvas: Canvas, width: Int, height: Int, colors: WidgetDesignColors) {
        val bg = parseAndroidColor(colors.color1, Color.parseColor("#191A1A"))
        val line = parseAndroidColor(colors.color2, Color.parseColor("#808080"))
        canvas.drawColor(bg)

        val spacing = width * 0.14f // ~55px at common densities
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = adjustAlpha(line, 0.35f)
            strokeWidth = 1f
        }
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
            x += spacing
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            y += spacing
        }
    }

    private fun drawDiamond(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(centerX, centerY - size)
            lineTo(centerX + size, centerY)
            lineTo(centerX, centerY + size)
            lineTo(centerX - size, centerY)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawHexagon(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((60.0 * i) - 30.0)
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun parseAndroidColor(hex: String, fallback: Int): Int {
        return parseHexColor(hex)?.let { color ->
            Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
        } ?: fallback
    }

    private fun adjustAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
