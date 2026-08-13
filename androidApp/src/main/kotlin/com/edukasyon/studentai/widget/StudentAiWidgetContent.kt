package com.edukasyon.studentai.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
fun SmallWidgetContent(snapshot: WidgetSnapshot, openAction: Action) {
    WidgetRoot(snapshot, openAction = openAction) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            when (snapshot.displayType) {
                WidgetDisplayType.TASKS -> {
                    DateHeader(snapshot)
                    Spacer(GlanceModifier.height(8.dp))
                    if (snapshot.tasks.isEmpty()) {
                        EmptyLabel(snapshot, "No upcoming tasks")
                    } else {
                        snapshot.tasks.forEach { TaskRow(it, snapshot) }
                    }
                }
                WidgetDisplayType.SCHEDULE -> {
                    DateHeader(snapshot)
                    Spacer(GlanceModifier.height(8.dp))
                    if (snapshot.schedule.isEmpty()) {
                        EmptyLabel(snapshot, "No classes today")
                    } else {
                        snapshot.schedule.forEach { ScheduleRow(it, snapshot) }
                    }
                    snapshot.currentTaskProgress?.let {
                        Spacer(GlanceModifier.height(6.dp))
                        ProgressSection(snapshot)
                    }
                }
                WidgetDisplayType.COMBINED -> {
                    DateHeader(snapshot)
                    Spacer(GlanceModifier.height(8.dp))
                    snapshot.tasks.take(2).forEach { TaskRow(it, snapshot, compact = true) }
                }
            }
        }
    }
}

@Composable
fun TallWidgetContent(snapshot: WidgetSnapshot, openAction: Action) {
    WidgetRoot(snapshot, openAction = openAction) {
        when (snapshot.displayType) {
            WidgetDisplayType.COMBINED -> CombinedTallContent(snapshot)
            WidgetDisplayType.SCHEDULE -> ScheduleTallContent(snapshot)
            WidgetDisplayType.TASKS -> TasksTallContent(snapshot)
        }
    }
}

@Composable
private fun CombinedTallContent(snapshot: WidgetSnapshot) {
    val theme = snapshot.themeColors
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "Today",
                style = TextStyle(
                    color = ColorProvider(WidgetColors.onSurface(theme)),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            if (snapshot.tasks.isEmpty() && snapshot.schedule.isEmpty()) {
                EmptyLabel(snapshot, "Nothing scheduled")
            } else {
                snapshot.tasks.take(3).forEach { TaskRow(it, snapshot, compact = true) }
                snapshot.schedule.take(2).forEach { ScheduleRow(it, snapshot, compact = true) }
            }
            Spacer(GlanceModifier.height(4.dp))
            MoreLabel(snapshot)
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            MiniCalendar(snapshot)
        }
    }
}

@Composable
private fun ScheduleTallContent(snapshot: WidgetSnapshot) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        DateHeader(snapshot)
        Spacer(GlanceModifier.height(8.dp))
        if (snapshot.schedule.isEmpty()) {
            EmptyLabel(snapshot, "No classes today")
        } else {
            snapshot.schedule.forEach { ScheduleRow(it, snapshot) }
        }
        snapshot.currentTaskProgress?.let {
            Spacer(GlanceModifier.height(8.dp))
            ProgressSection(snapshot)
        }
        Spacer(GlanceModifier.height(4.dp))
        MoreLabel(snapshot)
    }
}

@Composable
private fun TasksTallContent(snapshot: WidgetSnapshot) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        DateHeader(snapshot)
        Spacer(GlanceModifier.height(8.dp))
        if (snapshot.tasks.isEmpty()) {
            EmptyLabel(snapshot, "No upcoming tasks")
        } else {
            snapshot.tasks.forEach { TaskRow(it, snapshot) }
        }
        Spacer(GlanceModifier.height(4.dp))
        MoreLabel(snapshot)
    }
}

@Composable
private fun EmptyLabel(snapshot: WidgetSnapshot, message: String) {
    Text(
        text = message,
        style = TextStyle(
            color = ColorProvider(WidgetColors.muted(snapshot.themeColors)),
            fontSize = 12.sp
        )
    )
}
