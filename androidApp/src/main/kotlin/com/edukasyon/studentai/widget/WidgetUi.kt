package com.edukasyon.studentai.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.appwidget.cornerRadius
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.edukasyon.studentai.ui.theme.parseHexColor

internal object WidgetColors {
    fun onSurface(theme: WidgetThemeColors) = theme.onSurface
    fun muted(theme: WidgetThemeColors) = theme.muted
    fun card(theme: WidgetThemeColors) = theme.card
    fun highlightBg(accent: Color, theme: WidgetThemeColors) =
        if (theme.isLightBackground) accent.copy(alpha = 0.12f) else accent.copy(alpha = 0.22f)
}

internal fun accentColor(hex: String): Color =
    parseHexColor(hex) ?: Color(0xFF3949AB)

@Composable
internal fun WidgetRoot(
    snapshot: WidgetSnapshot,
    openAction: Action,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .clickable(openAction),
        contentAlignment = Alignment.TopStart
    ) {
        WidgetBackgroundLayer(context = context, snapshot = snapshot)
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.TopStart
        ) {
            content()
        }
    }
}

@Composable
private fun WidgetBackgroundLayer(context: Context, snapshot: WidgetSnapshot) {
    when (snapshot.designPreset) {
        WidgetDesignPreset.MINIMAL -> {
            val bg = parseHexColor(snapshot.designColors.color1) ?: Color(0xFFF3F4F6)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(bg))
            ) {}
        }
        else -> {
            val (widthDp, heightDp) = WidgetDataProvider.backgroundSizeDp(snapshot.widgetSize)
            val bitmap = WidgetBackgroundGenerator.getBitmap(
                context = context,
                preset = snapshot.designPreset,
                colors = snapshot.designColors,
                widthDp = widthDp,
                heightDp = heightDp
            )
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun DateHeader(snapshot: WidgetSnapshot) {
    val theme = snapshot.themeColors
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = snapshot.dayName,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.onSurface(theme)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = snapshot.monthName,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.muted(theme)),
                    fontSize = 12.sp
                )
            )
        }
        Text(
            text = snapshot.dayOfMonth.toString(),
            style = TextStyle(
                color = ColorProvider(WidgetColors.onSurface(theme)),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
internal fun TaskRow(
    item: WidgetTaskItem,
    snapshot: WidgetSnapshot,
    compact: Boolean = false
) {
    val theme = snapshot.themeColors
    val accent = accentColor(item.accentHex)
    val bg = if (item.isHighlighted) {
        WidgetColors.highlightBg(accent, theme)
    } else {
        Color.Transparent
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bg)
            .cornerRadius(8.dp)
            .padding(vertical = if (compact) 4.dp else 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(if (compact) 24.dp else 32.dp)
                .background(accent)
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.title,
                maxLines = if (compact) 1 else 2,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.onSurface(theme)),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = if (item.isHighlighted) FontWeight.Medium else FontWeight.Normal
                )
            )
            Text(
                text = item.subtitle,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (item.isHighlighted) accent else WidgetColors.muted(theme)
                    ),
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
internal fun ScheduleRow(
    item: WidgetScheduleItem,
    snapshot: WidgetSnapshot,
    compact: Boolean = false
) {
    val theme = snapshot.themeColors
    val accent = accentColor(item.accentHex)
    val bg = if (item.isCurrent) {
        WidgetColors.highlightBg(accent, theme)
    } else {
        Color.Transparent
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(bg)
            .cornerRadius(8.dp)
            .padding(vertical = if (compact) 4.dp else 6.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(if (compact) 24.dp else 32.dp)
                .background(accent)
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                            text = item.title,
                            maxLines = if (compact) 1 else 2,
                            style = TextStyle(
                                color = ColorProvider(WidgetColors.onSurface(theme)),
                                fontSize = if (compact) 12.sp else 13.sp,
                                fontWeight = if (item.isCurrent) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                        Text(
                            text = item.timeRange,
                            maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(
                        if (item.isCurrent) accent else WidgetColors.muted(theme)
                    ),
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
internal fun MiniCalendar(snapshot: WidgetSnapshot) {
    val theme = snapshot.themeColors
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            snapshot.calendarWeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(WidgetColors.muted(theme)),
                        fontSize = 9.sp
                    )
                )
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        snapshot.calendarDays.chunked(7).forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = GlanceModifier.defaultWeight().padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day.isCurrentMonth && day.dayOfMonth > 0) {
                            val dayBg = if (day.isToday) {
                                WidgetColors.onSurface(theme)
                            } else {
                                Color.Transparent
                            }
                            val dayColor = if (day.isToday) {
                                if (theme.isLightBackground) {
                                    parseHexColor(snapshot.designColors.color1) ?: Color(0xFFF3F4F6)
                                } else {
                                    parseHexColor(snapshot.designColors.color1) ?: Color(0xFF1A1A1A)
                                }
                            } else {
                                WidgetColors.onSurface(theme)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(18.dp)
                                        .background(dayBg)
                                        .cornerRadius(9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        style = TextStyle(
                                            color = ColorProvider(dayColor),
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                                day.dotColorHex?.let { hex ->
                                    if (!day.isToday) {
                                        Box(
                                            modifier = GlanceModifier
                                                .size(4.dp)
                                                .background(accentColor(hex))
                                                .cornerRadius(2.dp)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
internal fun ProgressSection(snapshot: WidgetSnapshot) {
    val theme = snapshot.themeColors
    val progress = snapshot.currentTaskProgress ?: return
    val timeLeft = snapshot.currentTaskTimeLeft ?: return
    val accent = accentColor(snapshot.accentColorHex)
    val filled = progress.coerceIn(0.05f, 1f)
    val remaining = (1f - progress).coerceAtLeast(0.05f)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = timeLeft,
            style = TextStyle(color = ColorProvider(accent), fontSize = 11.sp)
        )
        Spacer(GlanceModifier.height(4.dp))
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackWidth = 160f
            Box(
                modifier = GlanceModifier
                    .width((trackWidth * filled).dp)
                    .height(4.dp)
                    .background(accent)
                    .cornerRadius(2.dp)
            ) {}
            Box(
                modifier = GlanceModifier
                    .width((trackWidth * remaining).dp)
                    .height(4.dp)
                    .background(WidgetColors.muted(theme).copy(alpha = 0.3f))
                    .cornerRadius(2.dp)
            ) {}
        }
    }
}

@Composable
internal fun MoreLabel(snapshot: WidgetSnapshot) {
    if (snapshot.moreCount <= 0) return
    Text(
        text = "+${snapshot.moreCount} more",
        style = TextStyle(
            color = ColorProvider(WidgetColors.muted(snapshot.themeColors)),
            fontSize = 11.sp
        )
    )
}
