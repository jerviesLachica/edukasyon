package com.edukasyon.studentai.widget.store

import android.content.Context
import com.edukasyon.studentai.widget.WidgetConfig
import com.edukasyon.studentai.widget.WidgetDesignPreset
import com.edukasyon.studentai.widget.WidgetDisplayType
import com.edukasyon.studentai.widget.WidgetSize
import java.util.concurrent.ConcurrentHashMap

/**
 * V2 per-instance configuration store. AppWidgetId → WidgetConfig, persisted
 * in SharedPreferences (commit on write) with a memory mirror for speed.
 *
 * Disk is truth: memory is only a cache and is never required for correct
 * rendering after process death, reboot, or launcher recreation.
 */
object WidgetConfigStore {
    private const val PREFS_NAME = "schedmate_widget_v2_config"

    private val memory = ConcurrentHashMap<Int, WidgetConfig>()

    /**
     * Synchronous commit — a refresh issued immediately after this call is
     * guaranteed to observe the saved values, even across process death.
     */
    fun save(context: Context, config: WidgetConfig) {
        memory[config.appWidgetId] = config
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(config.appWidgetId), encode(config))
            .commit()
    }

    fun load(context: Context, appWidgetId: Int): WidgetConfig? {
        memory[appWidgetId]?.let { return it }
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(appWidgetId), null) ?: return null
        return decode(appWidgetId, raw)?.also { memory[appWidgetId] = it }
    }

    fun hasConfig(context: Context, appWidgetId: Int): Boolean =
        memory.containsKey(appWidgetId) || load(context, appWidgetId) != null

    fun remove(context: Context, appWidgetId: Int) {
        memory.remove(appWidgetId)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key(appWidgetId))
            .apply()
    }

    private fun key(appWidgetId: Int) = "config_$appWidgetId"

    private fun encode(config: WidgetConfig): String = buildString {
        append(config.widgetSize.name).append('|')
        append(config.displayType.name).append('|')
        append(config.accentHex ?: "").append('|')
        append(config.designPreset.name).append('|')
        append(config.designColor1 ?: "").append('|')
        append(config.designColor2 ?: "").append('|')
        append(config.designColor3 ?: "")
    }

    private fun decode(appWidgetId: Int, raw: String): WidgetConfig? {
        return try {
            val parts = raw.split('|')
            if (parts.size < 7) return null
            WidgetConfig(
                appWidgetId = appWidgetId,
                widgetSize = WidgetSize.valueOf(parts[0]),
                displayType = WidgetDisplayType.valueOf(parts[1]),
                accentHex = parts[2].ifBlank { null },
                designPreset = WidgetDesignPreset.valueOf(parts[3]),
                designColor1 = parts[4].ifBlank { null },
                designColor2 = parts[5].ifBlank { null },
                designColor3 = parts[6].ifBlank { null }
            )
        } catch (e: Exception) {
            null
        }
    }
}
