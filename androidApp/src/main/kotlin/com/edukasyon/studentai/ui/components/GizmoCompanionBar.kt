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
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.GizmoCompanionState
import com.edukasyon.studentai.domain.model.GizmoMood
import kotlin.math.ceil

@Composable
fun GizmoCompanionHeader(
    gizmo: GizmoCompanionState,
    isOnline: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GizmoAvatar(mood = gizmo.mood, level = gizmo.level)
                Column(Modifier.weight(1f)) {
                    Text(
                        "Gizmo",
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
            }
            Spacer(Modifier.height(10.dp))
            GizmoCapabilityChips(isOnline = isOnline)
            Spacer(Modifier.height(8.dp))
            GizmoHeartsRow(gizmo = gizmo)
        }
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
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(mood.emoji, style = MaterialTheme.typography.headlineMedium)
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
fun GizmoHeartsRow(
    gizmo: GizmoCompanionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(gizmo.maxHearts.coerceAtMost(15)) { index ->
                val filled = index < gizmo.hearts
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(if (index < 5) 16.dp else 12.dp),
                    tint = if (filled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    },
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "${gizmo.hearts}/${gizmo.maxHearts}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (gizmo.superHearts > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${gizmo.superHearts}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (gizmo.streakDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${gizmo.streakDays}d",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    gizmo.heartsCooldownRemainingMs?.let { remaining ->
        Spacer(Modifier.height(6.dp))
        Text(
            "Hearts refilling in ${formatCooldown(remaining)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
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
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Text("🤖", modifier = Modifier.padding(end = 6.dp, top = 4.dp))
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
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!isUser && onCopy != null) {
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

private fun formatCooldown(ms: Long): String {
    val totalSeconds = ceil(ms / 1000.0).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
