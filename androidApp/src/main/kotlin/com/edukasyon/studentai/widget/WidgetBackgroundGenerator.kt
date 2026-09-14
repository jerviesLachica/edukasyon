package com.edukasyon.studentai.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Generates widget background bitmaps.
 *
 * Two caching layers keep the background snappy after process death:
 *   1. Memory (LruCache) — instant hit for recent widgets.
 *   2. Disk (`widget-bg/` app dir) — survives process kills; decoded from file
 *      on cold start instead of redrawing every pattern from scratch.
 *
 * Paint objects are reused per design+color signature so each render
 * avoids allocating objects inside hot drawing loops.
 */
object WidgetBackgroundGenerator {
    private const val CACHE_MAX = 16
    private val memoryCache = LruCache<String, Bitmap>(CACHE_MAX)

    @Volatile private var cacheDir: File? = null

    // Reusable paint set keyed by the color signature.
    private val paintSets = mutableMapOf<String, PaintSet>()

    private data class PaintSet(
        val color1: Int,
        val color2: Int,
        val color3: Int,
        val dot: Paint,
        val line: Paint,
        val hexPalette: List<Paint>,
    )

    fun getBitmap(
        context: Context,
        preset: WidgetDesignPreset,
        colors: WidgetDesignColors,
        widthDp: Int = 160,
        heightDp: Int = 160,
    ): Bitmap {
        if (preset == WidgetDesignPreset.MINIMAL) {
            val color = parseAndroidColor(colors.color1, Color.parseColor("#F3F4F6"))
            val key = "minimal|$color|${widthDp}x${heightDp}|${context.resources.displayMetrics.density}"
            memoryCache.get(key)?.let { return it }
            val bitmap = solidBitmap(context, color, widthDp, heightDp)
            memoryCache.put(key, bitmap)
            return bitmap
        }

        val density = context.resources.displayMetrics.density
        val key = "${preset.name}|${colors.cacheKey()}|${widthDp}x${heightDp}|${density}"
        memoryCache.get(key)?.let { return it }

        // 1. Try disk cache.
        val file = File(cacheDir(context), "$key.png")
        if (file.exists()) {
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            if (decoded != null) {
                memoryCache.put(key, decoded)
                return decoded
            }
        }

        // 2. Generate.
        // RemoteViews.setImageViewBitmap() is limited by Binder transaction size (~1 MB).
        // 160px cap keeps even the largest widget (160x240dp @ 3x density = 480x720px
        // uncapped) well under 1 MB ARGB_8888 (160x240 = 154KB). This eliminates
        // silent paint drops on high-DPI devices (Huawei 480dpi).
        val maxPx = 160
        val widthPx = (widthDp * density).toInt().coerceAtMost(maxPx).coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtMost(maxPx).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paintSet = paintSet(colors, preset)
        when (preset) {
            WidgetDesignPreset.CORAL_CHEVRON -> drawCoralChevron(canvas, widthPx, heightPx, colors, paintSet)
            WidgetDesignPreset.HEX_DARK -> drawHexDark(canvas, widthPx, heightPx, colors, paintSet)
            WidgetDesignPreset.DOT_GRID -> drawDotGrid(canvas, widthPx, heightPx, colors, paintSet)
            WidgetDesignPreset.LINE_GRID -> drawLineGrid(canvas, widthPx, heightPx, colors, paintSet)
            WidgetDesignPreset.MINIMAL -> Unit // handled above
        }

        // 3. Persist to disk.
        persistBitmap(file, bitmap)

        memoryCache.put(key, bitmap)
        return bitmap
    }

    fun invalidateCache() {
        memoryCache.evictAll()
    }

    // ── Bitmap helpers ─────────────────────────────────────────────

    private fun solidBitmap(context: Context, color: Int, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        // Same 160px cap as pattern bitmaps — a minimal solid at full density
        // is fine, but keeping the cap guarantees the same Binder safety.
        val maxPx = 160
        val widthPx = (widthDp * density).toInt().coerceAtMost(maxPx).coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtMost(maxPx).coerceAtLeast(1)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawColor(color)
        }
    }

    // ── Reusable paint set ─────────────────────────────────────────

    private fun paintSet(colors: WidgetDesignColors, preset: WidgetDesignPreset): PaintSet {
        paintSets[colors.cacheKey()]?.let { return it }
        val c1 = parseAndroidColor(colors.color1, Color.parseColor("#F8B195"))
        val c2 = parseAndroidColor(colors.color2, Color.parseColor("#355C7D"))
        val c3 = parseAndroidColor(colors.color3 ?: "#3C3C3C", Color.parseColor("#3C3C3C"))
        val set = PaintSet(
            color1 = c1,
            color2 = c2,
            color3 = c3,
            dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = adjustAlpha(Color.WHITE, 0.55f)
                style = Paint.Style.FILL
            },
            line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = adjustAlpha(Color.parseColor("#808080"), 0.35f)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            },
            hexPalette = listOf(
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = parseAndroidColor(colors.color1, Color.parseColor("#1D1D1D")); style = Paint.Style.FILL },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = parseAndroidColor(colors.color2, Color.parseColor("#4E4F51")); style = Paint.Style.FILL },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = parseAndroidColor(colors.color3 ?: "#3C3C3C", Color.parseColor("#3C3C3C")); style = Paint.Style.FILL },
            ),
        )
        paintSets[colors.cacheKey()] = set
        return set
    }

    // ── Drawing functions (receive pre-built paints + raw colors) ────

    private fun drawCoralChevron(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: WidgetDesignColors,
        p: PaintSet,
    ) {
        val c1 = p.color1
        val c2 = p.color2

        val radial = RadialGradient(
            width / 2f, height / 2f,
            min(width, height) * 0.75f,
            intArrayOf(c1, c2),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = radial }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)

        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = adjustAlpha(c1, 0.35f)
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
            this.color = adjustAlpha(c2, 0.18f)
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

    private fun drawHexDark(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: WidgetDesignColors,
        p: PaintSet,
    ) {
        canvas.drawColor(p.color1)

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
                drawHexagon(
                    canvas,
                    x + hexWidth / 2f,
                    y + radius,
                    radius * 0.92f,
                    p.hexPalette[(row + col) % p.hexPalette.size],
                )
                x += hexWidth
                col++
            }
            y += vertStep
            row++
        }
    }

    private fun drawDotGrid(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: WidgetDesignColors,
        p: PaintSet,
    ) {
        canvas.drawColor(p.color1)

        val spacing = width * 0.075f
        val dotRadius = spacing * 0.08f
        val dotPaint = p.dot
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

    private fun drawLineGrid(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: WidgetDesignColors,
        p: PaintSet,
    ) {
        canvas.drawColor(p.color1)

        val spacing = width * 0.14f
        val linePaint = p.line
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

    // ── Primitives ─────────────────────────────────────────────────

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
        val path = Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i) - 30.0)
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ── Disk persistence ────────────────────────────────────────────

    private fun cacheDir(context: Context): File = cacheDir ?: synchronized(this) {
        cacheDir ?: context.getDir("widget-bg", Context.MODE_PRIVATE).also { cacheDir = it }
    }

    private fun persistBitmap(file: File, bitmap: Bitmap) {
        // Write to temp file first, then rename atomically to avoid partial reads.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            tmp.renameTo(file)
        } catch (_: IOException) {
            tmp.delete()
        }
    }

    // ── Color helpers ────────────────────────────────────────────────

    private fun parseAndroidColor(hex: String, fallback: Int): Int {
        return parseHexColor(hex)?.let { color ->
            Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            )
        } ?: fallback
    }

    private fun adjustAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (Color.alpha(color) * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
