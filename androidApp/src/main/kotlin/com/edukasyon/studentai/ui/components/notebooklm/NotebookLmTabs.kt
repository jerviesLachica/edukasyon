package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NotebookLmTab(val title: String) {
    SOURCES("Sources"),
    CHAT("Chat"),
    STUDIO("Studio")
}

@Composable
fun NotebookLmTabs(
    selectedTab: NotebookLmTab,
    onTabSelected: (NotebookLmTab) -> Unit,
    modifier: Modifier = Modifier,
    sourcesCount: Int = 0,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(NotebookLmColors.Surface)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NotebookLmTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) NotebookLmColors.SurfaceHighest else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tab_bg_${tab.name}",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) NotebookLmColors.TextPrimary else NotebookLmColors.TextSecondary,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tab_text_${tab.name}",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(containerColor)
                        .clickable { onTabSelected(tab) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = tab.title,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
