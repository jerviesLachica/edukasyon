package com.edukasyon.studentai.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

enum class AdaptiveWidth {
    Compact,
    Medium,
    Expanded
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberAdaptiveWidth(): AdaptiveWidth {
    val activity = LocalContext.current as ComponentActivity
    val windowSizeClass = calculateWindowSizeClass(activity)
    return windowSizeClass.toAdaptiveWidth()
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun WindowSizeClass.toAdaptiveWidth(): AdaptiveWidth = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> AdaptiveWidth.Compact
    WindowWidthSizeClass.Medium -> AdaptiveWidth.Medium
    WindowWidthSizeClass.Expanded -> AdaptiveWidth.Expanded
    else -> AdaptiveWidth.Compact
}

@Composable
fun rememberContentMaxWidth(): Int {
    val adaptiveWidth = rememberAdaptiveWidth()
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when (adaptiveWidth) {
        AdaptiveWidth.Compact -> screenWidth
        AdaptiveWidth.Medium -> minOf(screenWidth, 840)
        AdaptiveWidth.Expanded -> minOf(screenWidth, 1200)
    }
}

@Composable
fun isCompactWidth(): Boolean = rememberAdaptiveWidth() == AdaptiveWidth.Compact

@Composable
fun isMediumOrExpandedWidth(): Boolean = rememberAdaptiveWidth() != AdaptiveWidth.Compact

fun AdaptiveWidth.columnCount(default: Int = 1, medium: Int = 2, expanded: Int = 3): Int = when (this) {
    AdaptiveWidth.Compact -> default
    AdaptiveWidth.Medium -> medium
    AdaptiveWidth.Expanded -> expanded
}

fun AdaptiveWidth.listPaneWeight(): Float = when (this) {
    AdaptiveWidth.Compact -> 1f
    AdaptiveWidth.Medium -> 0.42f
    AdaptiveWidth.Expanded -> 0.38f
}
