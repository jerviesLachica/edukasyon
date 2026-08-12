package com.edukasyon.studentai.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.edukasyon.studentai.ui.components.AdaptiveNavigationRail
import com.edukasyon.studentai.ui.components.StudentAiBottomBar
import com.edukasyon.studentai.ui.navigation.MainTab

@Composable
fun AdaptiveScaffold(
    currentRoute: String?,
    tabs: List<MainTab>,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val adaptiveWidth = rememberAdaptiveWidth()

    when (adaptiveWidth) {
        AdaptiveWidth.Compact -> {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                bottomBar = {
                    StudentAiBottomBar(
                        tabs = tabs,
                        currentRoute = currentRoute,
                        onTabSelected = onTabSelected
                    )
                }
            ) { padding ->
                content(Modifier.padding(padding))
            }
        }

        AdaptiveWidth.Medium, AdaptiveWidth.Expanded -> {
            Row(modifier = modifier.fillMaxSize()) {
                AdaptiveNavigationRail(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onTabSelected = onTabSelected,
                    expanded = adaptiveWidth == AdaptiveWidth.Expanded
                )
                Box(Modifier.fillMaxHeight().weight(1f)) {
                    content(Modifier.fillMaxSize())
                }
            }
        }
    }
}
