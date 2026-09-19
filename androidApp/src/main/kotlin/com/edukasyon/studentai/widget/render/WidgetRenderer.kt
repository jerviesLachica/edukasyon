package com.edukasyon.studentai.widget.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.edukasyon.studentai.MainActivity
import com.edukasyon.studentai.R
import com.edukasyon.studentai.widget.WidgetAccentPresets
import com.edukasyon.studentai.widget.WidgetBackgroundGenerator
import com.edukasyon.studentai.widget.WidgetConfig
import com.edukasyon.studentai.widget.WidgetDesignPreset
import com.edukasyon.studentai.widget.WidgetDisplayType
import com.edukasyon.studentai.widget.WidgetSnapshot
import com.edukasyon.studentai.widget.update.WidgetToggleReceiver

/**
 * V2 renderer: (config, snapshot) -> RemoteViews. Deterministic, synchronous,
 * local-data-only. No database, no network, no Firebase, no AI, no Hilt.
 * All data must already be inside [snapshot]; all behavior inside [config].
 */
object WidgetRenderer {

    // Must match what MainActivity's deep-link handling reads.
    const val START_TAB_KEY = "start_tab"
    const val TASK_ID_KEY = "task_id"
    const val ACTION_OPEN_TAB = "com.edukasyon.studentai.widget.v2.OPEN_TAB"
    const val ACTION_OPEN_TASK = "com.edukasyon.studentai.widget.v2.OPEN_TASK"

    private data class ThemeInts(
        val onSurface: Int,
        val muted: Int,
        val lightBackground: Boolean
    )

    fun render(
        context: Context,
        appWidgetId: Int,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        tall: Boolean
    ): RemoteViews {
        val pkg = context.packageName
        val views = RemoteViews(pkg, R.layout.widget_v2_root)
        val theme = themeInts(config)
        val limit = if (tall) 5 else 3

        paintBackground(context, views, config)
        paintHeader(views, snapshot, theme)
        views.removeAllViews(R.id.v2_rows)

        var added = 0
        when (snapshot.displayType) {
            WidgetDisplayType.TASKS -> {
                snapshot.tasks.take(limit).forEachIndexed { index, item ->
                    views.addView(
                        R.id.v2_rows,
                        taskRow(context, appWidgetId, item, theme, index)
                    )
                    added++
                }
            }
            WidgetDisplayType.SCHEDULE -> {
                snapshot.schedule.take(limit).forEach { item ->
                    views.addView(R.id.v2_rows, scheduleRow(context, appWidgetId, item, theme))
                    added++
                }
            }
            WidgetDisplayType.COMBINED -> {
                val taskBudget = 2
                snapshot.tasks.take(taskBudget).forEachIndexed { index, item ->
                    views.addView(
                        R.id.v2_rows,
                        taskRow(context, appWidgetId, item, theme, index)
                    )
                    added++
                }
                snapshot.schedule.take((limit - taskBudget).coerceAtLeast(0)).forEach { item ->
                    views.addView(R.id.v2_rows, scheduleRow(context, appWidgetId, item, theme))
                    added++
                }
            }
        }

        if (added == 0) {
            views.setViewVisibility(R.id.v2_empty, View.VISIBLE)
            views.setTextViewText(
                R.id.v2_empty,
                when (snapshot.displayType) {
                    WidgetDisplayType.SCHEDULE -> "No classes today"
                    WidgetDisplayType.COMBINED -> "Nothing scheduled"
                    else -> "No upcoming tasks"
                }
            )
            views.setTextColor(R.id.v2_empty, theme.muted)
        } else {
            views.setViewVisibility(R.id.v2_empty, View.GONE)
        }

        if (snapshot.moreCount > 0) {
            views.setViewVisibility(R.id.v2_more, View.VISIBLE)
            views.setTextViewText(R.id.v2_more, "+${snapshot.moreCount} more")
            views.setTextColor(R.id.v2_more, theme.muted)
        } else {
            views.setViewVisibility(R.id.v2_more, View.GONE)
        }

        return views
    }

    // ===== Pieces =====

    private const val TAG = "WidgetV2"

    private fun paintBackground(context: Context, views: RemoteViews, config: WidgetConfig) {
        val (w, h) = widgetSizeDp(context, config)
        val capPx = (maxOf(w, h) * context.resources.displayMetrics.density)
            .toInt().coerceAtMost(384).coerceAtLeast(120)
        val photoPath = config.backgroundImagePath
        android.util.Log.i(TAG, "paintBg: id=${config.appWidgetId} photoPath=${photoPath?.take(50)} w=$w h=$h capPx=$capPx")
        if (!photoPath.isNullOrBlank()) {
            val file = java.io.File(photoPath)
            android.util.Log.i(TAG, "paintBg: file exists=${file.exists()} readable=${file.canRead()} size=${if(file.exists()) file.length() else 0}")
            val decoded = decodeCappedBitmap(photoPath, w, h, capPx)
            if (decoded != null) {
                android.util.Log.i(TAG, "paintBg: decoded OK ${decoded.width}x${decoded.height}")
                runCatching {
                    views.setImageViewBitmap(R.id.v2_bg, decoded)
                    android.util.Log.i(TAG, "paintBg: setImageViewBitmap OK")
                }.onFailure {
                    android.util.Log.e(TAG, "paintBg: setImageViewBitmap FAILED", it)
                    fallbackBackground(context, views, config, w, h, capPx)
                }
                return
            } else {
                android.util.Log.w(TAG, "paintBg: decodeCappedBitmap returned NULL")
            }
        }
        android.util.Log.i(TAG, "paintBg: using DESIGN preset")
        fallbackBackground(context, views, config, w, h, capPx)
    }

    /**
     * Real widget size in dp from AppWidgetManager options (max of min/max
     * bounds — the closer cell estimate), clamped to a sane 80..300dp range.
     * Falls back to the legacy per-size constants when options are missing
     * or zero (fresh placement, some launchers) — never throws.
     */
    private fun widgetSizeDp(context: Context, config: WidgetConfig): Pair<Int, Int> {
        val fallback = when (config.widgetSize) {
            com.edukasyon.studentai.widget.WidgetSize.SMALL_2X2 -> 160 to 160
            com.edukasyon.studentai.widget.WidgetSize.TALL_2X3 -> 160 to 240
        }
        return runCatching {
            val options = AppWidgetManager.getInstance(context)
                .getAppWidgetOptions(config.appWidgetId)
            val w = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            ).takeIf { it > 0 }?.coerceIn(80, 300)
            val h = maxOf(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ).takeIf { it > 0 }?.coerceIn(80, 300)
            if (w == null || h == null) fallback else w to h
        }.getOrDefault(fallback)
    }

    private fun fallbackBackground(
        context: Context,
        views: RemoteViews,
        config: WidgetConfig,
        w: Int,
        h: Int,
        capPx: Int
    ) {
        runCatching {
            val bitmap = WidgetBackgroundGenerator.getBitmap(
                context, config.designPreset, config.designColors, w, h, capPx
            )
            views.setImageViewBitmap(R.id.v2_bg, bitmap)
        }.onFailure {
            // Never white: solid fallback in the design's primary color on v2_bg, preserving root rounded outline.
            views.setInt(
                R.id.v2_bg, "setBackgroundColor",
                ColorInts.parse(config.designColors.color1, 0xFF1F2A44.toInt())
            )
        }
    }

    /**
     * Decodes a photo file to a Bitmap capped to the same pixel budget as
     * generated backgrounds. Oversized images crash RemoteViews (Binder
     * transaction limit ~1 MB); this bounds memory and falls back to null
     * (preset) on any failure — never throws.
     */
    private fun decodeCappedBitmap(
        path: String,
        widthDp: Int,
        heightDp: Int,
        maxPx: Int,
    ): android.graphics.Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // Cap the long edge to the pixel budget (sample-size decode).
            val sample = computeSampleSize(bounds.outWidth, bounds.outHeight, maxPx)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
            // Preserve aspect ratio: the ImageView is centerCrop, so exact
            // target fitting here would STRETCH the photo (square into a
            // 2x3 cell). Only trim the long edge to the cap; no distortion.
            val longest = maxOf(decoded.width, decoded.height)
            if (longest > maxPx) {
                val ratio = maxPx.toFloat() / longest
                val w = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val h = (decoded.height * ratio).toInt().coerceAtLeast(1)
                val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
                if (scaled != decoded) decoded.recycle()
                return scaled
            }
            decoded
        } catch (_: Exception) {
            null
        }
    }

    private fun computeSampleSize(srcW: Int, srcH: Int, maxPx: Int): Int {
        var sample = 1
        val longest = maxOf(srcW, srcH)
        while (longest / (sample * 2) > maxPx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun paintHeader(views: RemoteViews, snapshot: WidgetSnapshot, theme: ThemeInts) {
        views.setTextViewText(R.id.v2_day_name, snapshot.dayName)
        views.setTextColor(R.id.v2_day_name, theme.onSurface)
        views.setTextViewText(R.id.v2_month_name, snapshot.monthName)
        views.setTextColor(R.id.v2_month_name, theme.muted)
        views.setTextViewText(R.id.v2_day_number, snapshot.dayOfMonth.toString())
        views.setTextColor(R.id.v2_day_number, theme.onSurface)
    }

    private fun taskRow(
        context: Context,
        appWidgetId: Int,
        item: com.edukasyon.studentai.widget.WidgetTaskItem,
        theme: ThemeInts,
        index: Int
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_v2_row_task)
        val accent = accentForIndex(index, item.accentHex)

        row.setTextViewText(R.id.v2_row_title, item.title)
        row.setTextColor(
            R.id.v2_row_title,
            if (item.isHighlighted) accent else theme.onSurface
        )
        row.setTextViewText(R.id.v2_row_subtitle, item.subtitle)
        row.setTextColor(
            R.id.v2_row_subtitle,
            if (item.isHighlighted) accent else theme.muted
        )

        val fill = if (item.isCompleted) {
            accent
        } else {
            ColorInts.withAlpha(theme.muted, 51)
        }
        val check = if (theme.lightBackground) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        row.setImageViewBitmap(
            R.id.v2_check_tile,
            CheckTileFactory.tile(context, item.isCompleted, fill, check)
        )

        // Absolute toggle: the tap means what was rendered.
        row.setOnClickPendingIntent(
            R.id.v2_check_hit,
            WidgetToggleReceiver.pendingIntent(
                context, appWidgetId, item.id, !item.isCompleted
            )
        )
        row.setOnClickPendingIntent(
            R.id.v2_row_text,
            openTaskIntent(context, appWidgetId, item.id)
        )
        return row
    }

    private fun scheduleRow(
        context: Context,
        appWidgetId: Int,
        item: com.edukasyon.studentai.widget.WidgetScheduleItem,
        theme: ThemeInts
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_v2_row_schedule)
        val accent = ColorInts.parse(item.accentHex, 0xFF3949AB.toInt())
        row.setInt(R.id.v2_sched_bar, "setBackgroundColor", accent)
        row.setTextViewText(R.id.v2_sched_title, item.title)
        row.setTextColor(
            R.id.v2_sched_title,
            if (item.isCurrent) accent else theme.onSurface
        )
        row.setTextViewText(R.id.v2_sched_time, item.timeRange)
        row.setTextColor(
            R.id.v2_sched_time,
            if (item.isCurrent) accent else theme.muted
        )
        row.setOnClickPendingIntent(
            R.id.v2_sched_hit,
            openTabIntent(context, appWidgetId, "schedule")
        )
        return row
    }

    // ===== Intents (unique identity per destination — extras alone do NOT
    // disambiguate PendingIntents, so action + data URI + requestCode vary) =====

    fun openTaskIntent(context: Context, appWidgetId: Int, taskId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TASK
            data = Uri.parse("schedmate://v2/task/$appWidgetId/$taskId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, "planner")
            putExtra(TASK_ID_KEY, taskId)
        }
        return PendingIntent.getActivity(
            context, ("v2open:$appWidgetId:$taskId").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openTabIntent(context: Context, appWidgetId: Int, tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TAB
            data = Uri.parse("schedmate://v2/tab/$appWidgetId/$tab")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(START_TAB_KEY, tab)
        }
        return PendingIntent.getActivity(
            context, ("v2tab:$appWidgetId:$tab").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ===== Theme =====

    private fun themeInts(config: WidgetConfig): ThemeInts {
        return when (config.designPreset) {
            WidgetDesignPreset.MINIMAL -> ThemeInts(
                onSurface = 0xFF1A1A1A.toInt(),
                muted = 0xFF6B7280.toInt(),
                lightBackground = true
            )
            WidgetDesignPreset.CORAL_CHEVRON -> ThemeInts(
                onSurface = 0xFFF8FAFC.toInt(),
                muted = 0xFFE2E8F0.toInt(),
                lightBackground = false
            )
            WidgetDesignPreset.HEX_DARK,
            WidgetDesignPreset.DOT_GRID,
            WidgetDesignPreset.LINE_GRID -> ThemeInts(
                onSurface = 0xFFF5F5F5.toInt(),
                muted = 0xFF9CA3AF.toInt(),
                lightBackground = false
            )
        }
    }

    private fun accentForIndex(index: Int, fallbackHex: String): Int {
        val palette = WidgetAccentPresets.presets.map { it.first }
        val hex = palette.getOrElse(index % palette.size) { fallbackHex }
        return ColorInts.parse(hex, ColorInts.parse(fallbackHex, 0xFF3949AB.toInt()))
    }
}
