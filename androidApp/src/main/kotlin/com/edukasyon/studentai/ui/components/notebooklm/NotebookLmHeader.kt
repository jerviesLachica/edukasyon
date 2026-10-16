package com.edukasyon.studentai.ui.components.notebooklm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmHeader(
    notebookTitle: String,
    onTitleChange: (String) -> Unit = {},
    onNewNotebook: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onShare: () -> Unit = {},
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onNavigateHome: () -> Unit = {},
    onNavigateSchedule: () -> Unit = {},
    onNavigatePlanner: () -> Unit = {},
    onNavigateProfile: () -> Unit = {},
    onOpenDecks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showAppsMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var tempTitle by remember(notebookTitle) { mutableStateOf(notebookTitle) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left side: Swirl logo + Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable { showRenameDialog = true },
        ) {
            NotebookLmSwirlLogo(modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = notebookTitle.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
                color = NotebookLmColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Right side action icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // New notebook / chat button
            IconButton(
                onClick = onNewNotebook,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Notebook",
                    tint = NotebookLmColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // More menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = NotebookLmColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(NotebookLmColors.SurfaceHigh),
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = NotebookLmColors.TextSecondary)
                        },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Share, contentDescription = null, tint = NotebookLmColors.TextSecondary)
                        },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export notes", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = NotebookLmColors.TextSecondary)
                        },
                        onClick = {
                            showMenu = false
                            onExport()
                        },
                    )
                    HorizontalDivider(color = NotebookLmColors.BorderSoft)
                    DropdownMenuItem(
                        text = { Text("Delete", color = NotebookLmColors.WrongRed) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = NotebookLmColors.WrongRed)
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }

            // Settings gear
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = NotebookLmColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // PRO Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NotebookLmColors.SurfaceHighest,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = "PRO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NotebookLmColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }

            // Google 9-dots apps icon
            Box {
                IconButton(
                    onClick = { showAppsMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Apps,
                        contentDescription = "Apps",
                        tint = NotebookLmColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                DropdownMenu(
                    expanded = showAppsMenu,
                    onDismissRequest = { showAppsMenu = false },
                    modifier = Modifier
                        .background(NotebookLmColors.SurfaceHigh)
                        .width(220.dp),
                ) {
                    Text(
                        text = "SchedMate Apps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NotebookLmColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    DropdownMenuItem(
                        text = { Text("Home", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Home, contentDescription = null, tint = NotebookLmColors.BlueAccent)
                        },
                        onClick = {
                            showAppsMenu = false
                            onNavigateHome()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Schedule", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = NotebookLmColors.BlueAccent)
                        },
                        onClick = {
                            showAppsMenu = false
                            onNavigateSchedule()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Planner", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = NotebookLmColors.BlueAccent)
                        },
                        onClick = {
                            showAppsMenu = false
                            onNavigatePlanner()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Study Decks", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Style, contentDescription = null, tint = NotebookLmColors.StudioFlashcards)
                        },
                        onClick = {
                            showAppsMenu = false
                            onOpenDecks()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Profile", color = NotebookLmColors.TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = NotebookLmColors.TextSecondary)
                        },
                        onClick = {
                            showAppsMenu = false
                            onNavigateProfile()
                        },
                    )
                }
            }

            // Avatar circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(NotebookLmColors.BlueDark)
                    .border(1.dp, NotebookLmColors.BlueAccent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "J",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NotebookLmColors.BlueAccent,
                )
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = NotebookLmColors.SurfaceHigh,
            title = {
                Text(
                    "Rename Notebook",
                    color = NotebookLmColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NotebookLmColors.TextPrimary,
                        unfocusedTextColor = NotebookLmColors.TextPrimary,
                        focusedBorderColor = NotebookLmColors.BlueAccent,
                        unfocusedBorderColor = NotebookLmColors.Border,
                        focusedContainerColor = NotebookLmColors.Surface,
                        unfocusedContainerColor = NotebookLmColors.Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempTitle.isNotBlank()) {
                            onTitleChange(tempTitle.trim())
                        }
                        showRenameDialog = false
                    },
                ) {
                    Text("Save", color = NotebookLmColors.BlueAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = NotebookLmColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
fun NotebookLmSwirlLogo(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val strokeWidth = size.width * 0.16f

            // Outer primary arch - concentric arc using app theme primary
            drawArc(
                color = primary,
                startAngle = 145f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth * 0.5f, strokeWidth * 0.5f),
                size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Inner arch - nested concentric arc using app theme secondary
            val innerInset = strokeWidth * 1.35f
            drawArc(
                color = secondary,
                startAngle = 145f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(innerInset, innerInset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * innerInset, size.height - 2 * innerInset),
                style = Stroke(width = strokeWidth * 0.9f, cap = StrokeCap.Round),
            )
        }
    }
}
