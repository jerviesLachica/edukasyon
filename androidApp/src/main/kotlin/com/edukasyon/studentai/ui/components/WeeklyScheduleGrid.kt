package com.edukasyon.studentai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.domain.model.DayOfWeek
import com.edukasyon.studentai.domain.model.ScheduleDayTemplate
import com.edukasyon.studentai.domain.model.ScheduleItem
import com.edukasyon.studentai.domain.model.ScheduleWeekTemplates
import com.edukasyon.studentai.ui.adaptive.isMediumOrExpandedWidth
import com.edukasyon.studentai.ui.theme.isValidHexColor
import com.edukasyon.studentai.ui.theme.parseHexColor
import kotlin.math.roundToInt

private val DayColumnShape = RoundedCornerShape(20.dp)
private val DayPillShape = RoundedCornerShape(50)
private val ClassCardShape = RoundedCornerShape(12.dp)

@Composable
fun WeeklyScheduleGrid(
    itemsByDay: Map<DayOfWeek, List<ScheduleItem>>,
    dayTemplates: ScheduleWeekTemplates,
    selectedDay: DayOfWeek,
    onItemClick: (ScheduleItem) -> Unit,
    onDayEmptyClick: (DayOfWeek) -> Unit,
    onCustomizeTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "My Schedule",
    onItemMove: (ScheduleItem, DayOfWeek) -> Unit = { _, _ -> },
) {
    val expanded = isMediumOrExpandedWidth()
    val today = DateUtils.getTodayDayOfWeek()
    val hapticFeedback = LocalHapticFeedback.current

    var draggedItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragGrabOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetDay by remember { mutableStateOf<DayOfWeek?>(null) }
    val columnBounds = remember { mutableStateMapOf<DayOfWeek, Rect>() }

    fun resolveDropTarget(position: Offset): DayOfWeek? =
        columnBounds.entries.firstOrNull { (_, bounds) -> bounds.contains(position) }?.key

    fun finishDrag(performMove: Boolean) {
        val item = draggedItem
        val targetDay = dropTargetDay
        if (performMove && item != null && targetDay != null && item.dayOfWeek != targetDay) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onItemMove(item, targetDay)
        }
        draggedItem = null
        dragGrabOffset = Offset.Zero
        dropTargetDay = null
    }

    val draggedAccentColor = draggedItem?.let { item ->
        parseHexColor(item.colorHex)
            ?: dayTemplates.templateFor(item.dayOfWeek).accentColorHex?.let { parseHexColor(it) }
            ?: MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ScheduleDecorativeHeader(
                title = title,
                onCustomize = onCustomizeTemplates
            )

            if (expanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        DayColumn(
                            day = day,
                            items = itemsByDay[day].orEmpty(),
                            template = dayTemplates.templateFor(day),
                            isToday = day == today,
                            isSelected = day == selectedDay,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coordinates ->
                                    columnBounds[day] = coordinates.boundsInRoot()
                                },
                            onItemClick = onItemClick,
                            onEmptyClick = { onDayEmptyClick(day) },
                            draggedItemId = draggedItem?.id,
                            isDropTarget = dropTargetDay == day && draggedItem != null,
                            enableDrag = true,
                            onDragStart = { item, fingerPosition, grabOffset ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedItem = item
                                dragPosition = fingerPosition
                                dragGrabOffset = grabOffset
                                dropTargetDay = resolveDropTarget(fingerPosition)
                            },
                            onDrag = { position ->
                                dragPosition = position
                                dropTargetDay = resolveDropTarget(position)
                            },
                            onDragEnd = { finishDrag(performMove = true) },
                            onDragCancel = { finishDrag(performMove = false) },
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(DayOfWeek.entries) { day ->
                        DayColumn(
                            day = day,
                            items = itemsByDay[day].orEmpty(),
                            template = dayTemplates.templateFor(day),
                            isToday = day == today,
                            isSelected = day == selectedDay,
                            modifier = Modifier
                                .width(108.dp)
                                .height(340.dp)
                                .onGloballyPositioned { coordinates ->
                                    columnBounds[day] = coordinates.boundsInRoot()
                                },
                            onItemClick = onItemClick,
                            onEmptyClick = { onDayEmptyClick(day) },
                            draggedItemId = draggedItem?.id,
                            isDropTarget = dropTargetDay == day && draggedItem != null,
                            enableDrag = true,
                            onDragStart = { item, fingerPosition, grabOffset ->
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggedItem = item
                                dragPosition = fingerPosition
                                dragGrabOffset = grabOffset
                                dropTargetDay = resolveDropTarget(fingerPosition)
                            },
                            onDrag = { position ->
                                dragPosition = position
                                dropTargetDay = resolveDropTarget(position)
                            },
                            onDragEnd = { finishDrag(performMove = true) },
                            onDragCancel = { finishDrag(performMove = false) },
                        )
                    }
                }
            }
        }

        draggedItem?.let { item ->
            val accent = draggedAccentColor ?: MaterialTheme.colorScheme.primary
            ScheduleClassCard(
                item = item,
                accentColor = accent,
                onClick = {},
                modifier = Modifier
                    .width(100.dp)
                    .offset {
                        IntOffset(
                            (dragPosition.x - dragGrabOffset.x).roundToInt(),
                            (dragPosition.y - dragGrabOffset.y).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                        shadowElevation = 12f
                        alpha = 0.95f
                    },
                isDragging = true,
            )
        }
    }
}

@Composable
private fun ScheduleDecorativeHeader(
    title: String,
    onCustomize: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }
        IconButton(
            onClick = onCustomize,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = "Customize week template",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DayColumn(
    day: DayOfWeek,
    items: List<ScheduleItem>,
    template: ScheduleDayTemplate,
    isToday: Boolean,
    isSelected: Boolean,
    onItemClick: (ScheduleItem) -> Unit,
    onEmptyClick: () -> Unit,
    modifier: Modifier = Modifier,
    draggedItemId: String? = null,
    isDropTarget: Boolean = false,
    enableDrag: Boolean = false,
    onDragStart: ((ScheduleItem, Offset, Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val backgroundColor = parseHexColor(template.backgroundColorHex)
        ?: MaterialTheme.colorScheme.surfaceContainerLow
    val accentColor = template.accentColorHex?.let { parseHexColor(it) }
        ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = DayPillShape,
            color = accentColor,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = day.displayName.take(3).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        val dropHighlightAlpha by animateFloatAsState(
            targetValue = if (isDropTarget) 1f else 0f,
            animationSpec = spring(stiffness = 400f),
            label = "dropHighlight",
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .then(
                    if (isToday) Modifier.background(
                        accentColor.copy(alpha = 0.12f),
                        DayColumnShape
                    ) else Modifier
                )
                .then(
                    if (dropHighlightAlpha > 0f) {
                        Modifier.border(
                            width = 2.dp,
                            color = accentColor.copy(alpha = 0.5f + dropHighlightAlpha * 0.5f),
                            shape = DayColumnShape,
                        )
                    } else {
                        Modifier
                    }
                ),
            shape = DayColumnShape,
            color = if (isDropTarget) {
                accentColor.copy(alpha = 0.08f + dropHighlightAlpha * 0.12f)
            } else {
                backgroundColor
            },
            tonalElevation = if (isSelected) 2.dp else 0.dp,
            shadowElevation = if (isToday) 2.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                            .clip(ClassCardShape)
                            .clickable(onClick = onEmptyClick)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add class",
                            tint = accentColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    items.forEach { item ->
                        ScheduleClassCard(
                            item = item,
                            accentColor = accentColor,
                            onClick = { onItemClick(item) },
                            isDragging = draggedItemId == item.id,
                            enableDrag = enableDrag,
                            onDragStart = onDragStart?.let { callback ->
                                { fingerPosition, grabOffset ->
                                    callback(item, fingerPosition, grabOffset)
                                }
                            },
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ClassCardShape)
                            .clickable(onClick = onEmptyClick)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add class",
                            tint = accentColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleClassCard(
    item: ScheduleItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    enableDrag: Boolean = false,
    onDragStart: ((Offset, Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val itemColor = parseHexColor(item.colorHex) ?: accentColor
    val cardAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.35f else 1f,
        label = "cardAlpha",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 0.92f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "cardScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(onClick = onClick)
            .then(
                if (enableDrag && onDragStart != null) {
                    Modifier.scheduleCardDragSource(
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                } else {
                    Modifier
                }
            ),
        shape = ClassCardShape,
        color = Color.White.copy(alpha = 0.85f),
        tonalElevation = if (isDragging) 0.dp else 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(itemColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = DateUtils.formatTime12h(item.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.room?.let { room ->
                    Text(
                        text = room,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Modifier.scheduleCardDragSource(
    onDragStart: (Offset, Offset) -> Unit,
    onDrag: ((Offset) -> Unit)?,
    onDragEnd: (() -> Unit)?,
    onDragCancel: (() -> Unit)?,
): Modifier = composed {
    var cardPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { coordinates ->
            cardPositionInRoot = coordinates.positionInRoot()
        }
        .pointerInput(onDragStart, onDrag, onDragEnd, onDragCancel) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset ->
                    onDragStart(cardPositionInRoot + localOffset, localOffset)
                },
                onDragEnd = { onDragEnd?.invoke() },
                onDragCancel = { onDragCancel?.invoke() },
            ) { change, _ ->
                change.consume()
                onDrag?.invoke(cardPositionInRoot + change.position)
            }
        }
}

private val DayTemplatePresets = listOf(
    "#FCE4EC", "#F8BBD0", "#F48FB1", "#FFCDD2", "#FCE4EC", "#E1BEE7", "#BBDEFB",
    "#E3F2FD", "#C8E6C9", "#FFF9C4", "#FFE0B2", "#D1C4E9", "#B2DFDB", "#F0F4C3"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayTemplateCustomizationSheet(
    dayTemplates: ScheduleWeekTemplates,
    onDismiss: () -> Unit,
    onDayColorSelected: (DayOfWeek, ScheduleDayTemplate) -> Unit,
    onReset: () -> Unit
) {
    var selectedDay by remember { mutableStateOf(DayOfWeek.MONDAY) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Customize week template",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Pick a background color for each day column.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DayOfWeek.entries) { day ->
                    val template = dayTemplates.templateFor(day)
                    val accent = template.accentColorHex?.let { parseHexColor(it) }
                        ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = selectedDay == day,
                        onClick = { selectedDay = day },
                        label = { Text(day.displayName.take(3)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.25f)
                        )
                    )
                }
            }

            val currentTemplate = dayTemplates.templateFor(selectedDay)
            Text(
                "${selectedDay.displayName} background",
                style = MaterialTheme.typography.labelLarge
            )

            DayTemplatePresets.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { hex ->
                        val color = parseHexColor(hex) ?: Color.Gray
                        val selected = currentTemplate.backgroundColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(color)
                                .clickable {
                                    onDayColorSelected(
                                        selectedDay,
                                        currentTemplate.copy(backgroundColorHex = hex)
                                    )
                                }
                                .then(
                                    if (selected) Modifier.background(
                                        Color.Black.copy(alpha = 0.15f),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = currentTemplate.backgroundColorHex,
                onValueChange = { hex ->
                    if (isValidHexColor(hex)) {
                        onDayColorSelected(selectedDay, currentTemplate.copy(backgroundColorHex = hex))
                    }
                },
                label = { Text("Custom hex") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DayColumn(
                    day = selectedDay,
                    items = emptyList(),
                    template = currentTemplate,
                    isToday = false,
                    isSelected = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    onItemClick = {},
                    onEmptyClick = {}
                )
            }

            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to default palette")
            }
        }
    }
}
