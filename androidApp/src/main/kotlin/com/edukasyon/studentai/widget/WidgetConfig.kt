package com.edukasyon.studentai.widget

import android.content.Context

/**
 * Per-AppWidgetId configuration. Every hosted widget has one of these.
 */
data class WidgetConfig(
    val appWidgetId: Int,
    val widgetSize: WidgetSize,
    val displayType: WidgetDisplayType,
    val accentHex: String?,
    val designPreset: WidgetDesignPreset,
    val designColor1: String?,
    val designColor2: String?,
    val designColor3: String?
) {
    val designColors: WidgetDesignColors
        get() = designPreset.defaultColors().resolved(designColor1, designColor2, designColor3)

    companion object {
        fun defaultsForSize(widgetSize: WidgetSize): WidgetConfig = WidgetConfig(
            appWidgetId = -1,
            widgetSize = widgetSize,
            displayType = when (widgetSize) {
                WidgetSize.SMALL_2X2 -> WidgetDisplayType.TASKS
                WidgetSize.TALL_2X3 -> WidgetDisplayType.COMBINED
            },
            accentHex = null,
            designPreset = WidgetDesignPreset.MINIMAL,
            designColor1 = null,
            designColor2 = null,
            designColor3 = null
        )
    }
}
