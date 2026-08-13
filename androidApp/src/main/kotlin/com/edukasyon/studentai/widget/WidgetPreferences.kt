package com.edukasyon.studentai.widget

import android.content.Context

object WidgetPreferences {
    private const val PREFS_NAME = "studentai_widget_prefs"
    private const val KEY_TYPE = "display_type"
    private const val KEY_ACCENT = "accent_color_hex"
    private const val KEY_SIZE = "widget_size"
    private const val KEY_DESIGN = "design_preset"
    private const val KEY_DESIGN_C1 = "design_color_1"
    private const val KEY_DESIGN_C2 = "design_color_2"
    private const val KEY_DESIGN_C3 = "design_color_3"

    fun getDisplayType(context: Context, appWidgetId: Int, default: WidgetDisplayType): WidgetDisplayType {
        val raw = prefs(context).getString(key(appWidgetId, KEY_TYPE), default.name)
        return WidgetDisplayType.entries.find { it.name == raw } ?: default
    }

    fun setDisplayType(context: Context, appWidgetId: Int, type: WidgetDisplayType) {
        prefs(context).edit().putString(key(appWidgetId, KEY_TYPE), type.name).apply()
    }

    fun getAccentColorHex(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(key(appWidgetId, KEY_ACCENT), null)

    fun setAccentColorHex(context: Context, appWidgetId: Int, hex: String?) {
        prefs(context).edit().apply {
            if (hex.isNullOrBlank()) remove(key(appWidgetId, KEY_ACCENT))
            else putString(key(appWidgetId, KEY_ACCENT), hex.trim())
        }.apply()
    }

    fun getWidgetSize(context: Context, appWidgetId: Int): WidgetSize? {
        val raw = prefs(context).getString(key(appWidgetId, KEY_SIZE), null) ?: return null
        return WidgetSize.entries.find { it.name == raw }
    }

    fun setWidgetSize(context: Context, appWidgetId: Int, size: WidgetSize) {
        prefs(context).edit().putString(key(appWidgetId, KEY_SIZE), size.name).apply()
    }

    fun getDesignPreset(context: Context, appWidgetId: Int): WidgetDesignPreset {
        val raw = prefs(context).getString(key(appWidgetId, KEY_DESIGN), WidgetDesignPreset.MINIMAL.name)
        return WidgetDesignPreset.entries.find { it.name == raw } ?: WidgetDesignPreset.MINIMAL
    }

    fun setDesignPreset(context: Context, appWidgetId: Int, preset: WidgetDesignPreset) {
        prefs(context).edit().putString(key(appWidgetId, KEY_DESIGN), preset.name).commit()
    }

    /**
     * Persists all widget configuration in one synchronous write so a refresh immediately
     * after configure reads the chosen design preset instead of falling back to MINIMAL.
     */
    fun saveConfiguration(
        context: Context,
        appWidgetId: Int,
        widgetSize: WidgetSize,
        displayType: WidgetDisplayType,
        accentHex: String?,
        designPreset: WidgetDesignPreset,
        designColor1: String?,
        designColor2: String?,
        designColor3: String?
    ): Boolean {
        return prefs(context).edit().apply {
            putString(key(appWidgetId, KEY_SIZE), widgetSize.name)
            putString(key(appWidgetId, KEY_TYPE), displayType.name)
            if (accentHex.isNullOrBlank()) {
                remove(key(appWidgetId, KEY_ACCENT))
            } else {
                putString(key(appWidgetId, KEY_ACCENT), accentHex.trim())
            }
            putString(key(appWidgetId, KEY_DESIGN), designPreset.name)
            putNullableString(key(appWidgetId, KEY_DESIGN_C1), designColor1)
            putNullableString(key(appWidgetId, KEY_DESIGN_C2), designColor2)
            putNullableString(key(appWidgetId, KEY_DESIGN_C3), designColor3)
        }.commit()
    }

    fun getDesignColorOverrides(context: Context, appWidgetId: Int): Triple<String?, String?, String?> {
        val p = prefs(context)
        return Triple(
            p.getString(key(appWidgetId, KEY_DESIGN_C1), null),
            p.getString(key(appWidgetId, KEY_DESIGN_C2), null),
            p.getString(key(appWidgetId, KEY_DESIGN_C3), null)
        )
    }

    fun setDesignColorOverrides(
        context: Context,
        appWidgetId: Int,
        color1: String?,
        color2: String?,
        color3: String?
    ) {
        prefs(context).edit().apply {
            putNullableString(key(appWidgetId, KEY_DESIGN_C1), color1)
            putNullableString(key(appWidgetId, KEY_DESIGN_C2), color2)
            putNullableString(key(appWidgetId, KEY_DESIGN_C3), color3)
        }.apply()
    }

    fun getResolvedDesignColors(context: Context, appWidgetId: Int): WidgetDesignColors {
        val preset = getDesignPreset(context, appWidgetId)
        val (c1, c2, c3) = getDesignColorOverrides(context, appWidgetId)
        return preset.defaultColors().resolved(c1, c2, c3)
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().apply {
            remove(key(appWidgetId, KEY_TYPE))
            remove(key(appWidgetId, KEY_ACCENT))
            remove(key(appWidgetId, KEY_SIZE))
            remove(key(appWidgetId, KEY_DESIGN))
            remove(key(appWidgetId, KEY_DESIGN_C1))
            remove(key(appWidgetId, KEY_DESIGN_C2))
            remove(key(appWidgetId, KEY_DESIGN_C3))
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun android.content.SharedPreferences.Editor.putNullableString(key: String, value: String?) {
        if (value.isNullOrBlank()) remove(key) else putString(key, value.trim())
    }

    private fun key(appWidgetId: Int, suffix: String) = "widget_${appWidgetId}_$suffix"
}
