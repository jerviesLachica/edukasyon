package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.GizmoCompanionState
import com.edukasyon.studentai.domain.model.GizmoMood
import com.edukasyon.studentai.R

@Composable
fun GizmoCompanionHeader(
    gizmo: GizmoCompanionState,
    isOnline: Boolean = true,
    expanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val expandSpring = spring<IntSize>(stiffness = Spring.StiffnessMediumLow)
    val fadeSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (expanded) 0.35f else 0.28f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        tonalElevation = if (expanded) 0.dp else 2.dp,
    ) {
        Column(
            Modifier.padding(
                horizontal = 12.dp,
                vertical = if (expanded) 12.dp else 8.dp,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GizmoAvatar(
                    mood = gizmo.mood,
                    level = gizmo.level,
                    modifier = Modifier.size(if (expanded) 56.dp else 40.dp),
                )
                if (expanded) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Jarvis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            gizmo.mood.greeting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Lv.${gizmo.level} · ${gizmo.xp} XP",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { gizmo.xpProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Jarvis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Lv.${gizmo.level} · AI Tutor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GizmoCompactStats(gizmo = gizmo, isOnline = isOnline)
                }
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse companion bar" else "Expand companion bar",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = expandSpring) + fadeIn(animationSpec = fadeSpring),
                exit = shrinkVertically(animationSpec = expandSpring) + fadeOut(animationSpec = fadeSpring),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    GizmoCapabilityChips(isOnline = isOnline)
                    Spacer(Modifier.height(8.dp))
                    GizmoStreakRow(gizmo = gizmo)
                }
            }
        }
    }
}

@Composable
private fun GizmoCompactStats(
    gizmo: GizmoCompanionState,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (gizmo.streakDays > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "${gizmo.streakDays}d",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isOnline) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                ),
        )
    }
}

@Composable
private fun GizmoCapabilityChips(isOnline: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistChip(
            onClick = {},
            label = { Text("Vision") },
            leadingIcon = {
                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
            },
        )
        AssistChip(
            onClick = {},
            label = { Text("Tools") },
            leadingIcon = {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
            },
        )
        AssistChip(
            onClick = {},
            label = { Text("Files") },
            leadingIcon = {
                Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(14.dp))
            },
        )
        AssistChip(
            onClick = {},
            label = { Text(if (isOnline) "Online" else "Offline") },
            leadingIcon = {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(14.dp))
            },
        )
    }
}

@Composable
fun GizmoAvatar(
    mood: GizmoMood,
    level: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.jarvis_avatar),
            contentDescription = "Jarvis avatar",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$level",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun GizmoStreakRow(
    gizmo: GizmoCompanionState,
    modifier: Modifier = Modifier,
) {
    if (gizmo.streakDays <= 0) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.LocalFireDepartment,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${gizmo.streakDays}-day streak",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun JarvisThinkingIndicator(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp, top = 4.dp)
                .size(28.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.jarvis_avatar),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                "Jarvis",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            GeneratingLoader(
                label = "Thinking",
                style = GeneratingLoaderStyle.Compact,
            )
        }
    }
}

@Composable
fun JarvisReasoningSection(
    reasoning: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember(reasoning) { mutableStateOf(initiallyExpanded) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    Text(
                        "Reasoning",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse reasoning" else "Expand reasoning",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                MarkdownChatText(
                    markdown = reasoning,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun GizmoChatBubble(
    message: String,
    isUser: Boolean,
    sender: String,
    modifier: Modifier = Modifier,
    attachmentName: String? = null,
    attachmentIsImage: Boolean = false,
    reasoning: String? = null,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, top = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.jarvis_avatar),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            if (!isUser && !reasoning.isNullOrBlank()) {
                JarvisReasoningSection(
                    reasoning = reasoning,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (isUser || message.isNotBlank() || attachmentName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp,
                    ),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (attachmentName != null) {
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        attachmentName,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (attachmentIsImage) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        if (message.isNotBlank()) {
                            if (isUser) {
                                Text(message, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                MarkdownChatText(markdown = message)
                            }
                        }
                        if (!isUser && onCopy != null && message.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = onCopy,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy response",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
