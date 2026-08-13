package com.edukasyon.studentai.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
    forceHideBottomBar: Boolean = false,
    content: @Composable (Modifier) -> Unit
) {
    val adaptiveWidth = rememberAdaptiveWidth()

    when (adaptiveWidth) {
        AdaptiveWidth.Compact -> {
            val imeVisible = rememberImeVisible()
            val showBottomBar = !imeVisible && !forceHideBottomBar

            Scaffold(
                modifier = modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                    ) {
                        StudentAiBottomBar(
                            tabs = tabs,
                            currentRoute = currentRoute,
                            onTabSelected = onTabSelected,
                        )
                    }
                },
            ) { padding ->
                content(
                    Modifier
                        .padding(padding)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                            ),
                        ),
                )
            }
        }

        AdaptiveWidth.Medium, AdaptiveWidth.Expanded -> {
            Row(modifier = modifier.fillMaxSize()) {
                AdaptiveNavigationRail(
                    tabs = tabs,
                    currentRoute = currentRoute,
                    onTabSelected = onTabSelected,
                    expanded = adaptiveWidth == AdaptiveWidth.Expanded,
                )
                Box(Modifier.fillMaxHeight().weight(1f)) {
                    content(
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top +
                                        WindowInsetsSides.Horizontal +
                                        WindowInsetsSides.Bottom,
                                ),
                            ),
                    )
                }
            }
        }
    }
}
