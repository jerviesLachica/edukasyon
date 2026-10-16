package com.edukasyon.studentai.ui.components.notebooklm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edukasyon.studentai.domain.model.CitedSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmSourcesTab(
    sources: List<CitedSource>,
    selectedSourceIds: Set<String>?,
    onToggleSource: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddFiles: (List<Uri>) -> Unit,
    onAddText: (name: String, text: String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDeleteSources: (Set<String>) -> Unit = {},
    onSearchWeb: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddSourceSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(false) }

    val allSelected = remember(sources, selectedSourceIds) {
        if (sources.isEmpty()) false
        else selectedSourceIds != null && selectedSourceIds.size == sources.size
    }

    val sortedSources = remember(sources, sortAscending) {
        if (sortAscending) sources.sortedBy { it.name }
        else sources.sortedByDescending { it.updatedAt }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. "+ Add sources" large action button
        item {
            Surface(
                onClick = { showAddSourceSheet = true },
                shape = RoundedCornerShape(24.dp),
                color = NotebookLmColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = NotebookLmColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Add sources",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        ),
                        color = NotebookLmColors.TextPrimary,
                    )
                }
            }
        }

        // 2. "Search the web for new sources" bar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Search the web for new sources",
                    style = MaterialTheme.typography.bodySmall,
                    color = NotebookLmColors.TextSecondary,
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = NotebookLmColors.TextSecondary,
                                )
                                Text(
                                    text = "Web",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = NotebookLmColors.TextSecondary,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = NotebookLmColors.TextSecondary,
                                )
                                Text(
                                    text = "Fast Research",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = NotebookLmColors.TextSecondary,
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) onSearchWeb(searchQuery.trim())
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Search",
                                tint = NotebookLmColors.TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // 3. Sources List Toolbar: Refresh, Sort, "Select all", Checkbox
        if (sources.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = { /* refresh */ },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = NotebookLmColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = { sortAscending = !sortAscending },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Sort,
                                contentDescription = "Sort",
                                tint = NotebookLmColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (!selectedSourceIds.isNullOrEmpty()) {
                            IconButton(
                                onClick = {
                                    onDeleteSources(selectedSourceIds)
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete selected",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clickable { onSelectAll(!allSelected) },
                    ) {
                        Text(
                            text = "Select all",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = NotebookLmColors.TextSecondary,
                        )
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { onSelectAll(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NotebookLmColors.BlueAccent,
                                uncheckedColor = NotebookLmColors.Border,
                                checkmarkColor = NotebookLmColors.Background,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // 4. Source items (Screenshot 3: minimalist rows with dot or outline checkbox)
            items(sortedSources, key = { it.id }) { source ->
                val isChecked = selectedSourceIds?.contains(source.id) == true
                SourceListItemRow(
                    source = source,
                    isChecked = isChecked,
                    onToggle = { onToggleSource(source.id) },
                    onDelete = { onDeleteSource(source.id) },
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = NotebookLmColors.TextTertiary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "No sources added yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = NotebookLmColors.TextSecondary,
                        )
                        Text(
                            text = "Add PDFs, notes, photos, or websites to start learning",
                            style = MaterialTheme.typography.bodySmall,
                            color = NotebookLmColors.TextTertiary,
                        )
                    }
                }
            }
        }
    }

    if (showAddSourceSheet) {
        NotebookLmAddSourcesSheet(
            onDismissRequest = { showAddSourceSheet = false },
            onAddFiles = { uris ->
                showAddSourceSheet = false
                onAddFiles(uris)
            },
            onAddText = { name, text ->
                showAddSourceSheet = false
                onAddText(name, text)
            },
        )
    }
}

@Composable
private fun SourceListItemRow(
    source: CitedSource,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val isImage = source.name.lowercase().endsWith(".jpg") ||
            source.name.lowercase().endsWith(".jpeg") ||
            source.name.lowercase().endsWith(".png")
    val isPdf = source.name.lowercase().endsWith(".pdf")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isChecked) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            // Red outlined image icon badge matching Screenshot 3
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, if (isImage || isPdf) NotebookLmColors.RedImageMime else NotebookLmColors.BlueAccent),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            isImage -> Icons.Outlined.Image
                            isPdf -> Icons.Outlined.PictureAsPdf
                            else -> Icons.Outlined.Description
                        },
                        contentDescription = null,
                        tint = if (isImage || isPdf) NotebookLmColors.RedImageMime else NotebookLmColors.BlueAccent,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = NotebookLmColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isChecked) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = NotebookLmColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(NotebookLmColors.SurfaceHigh),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete source", color = NotebookLmColors.WrongRed) },
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

                // Checked: Outline rounded square with theme checkmark
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            } else {
                // Unchecked: Minimalist subtle dot (Screenshot 3)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(NotebookLmColors.TextTertiary)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookLmAddSourcesSheet(
    onDismissRequest: () -> Unit,
    onAddFiles: (List<Uri>) -> Unit,
    onAddText: (name: String, text: String) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var showPasteDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = NotebookLmColors.Surface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                color = NotebookLmColors.Border,
                shape = CircleShape,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: "Create Audio and Video Overviews from websites" + close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Create Audio and Video Overviews from",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NotebookLmColors.TextPrimary,
                    )
                    Text(
                        text = "websites",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NotebookLmColors.BlueAccent,
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NotebookLmColors.TextSecondary,
                    )
                }
            }

            // Quick source action buttons row/grid (Screenshot 4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceTypeChip(
                    icon = Icons.Outlined.UploadFile,
                    label = "Upload files",
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                )
                SourceTypeChip(
                    icon = Icons.Outlined.Link,
                    secondaryIcon = Icons.Filled.PlayArrow,
                    secondaryTint = NotebookLmColors.YoutubeRed,
                    label = "Websites",
                    modifier = Modifier.weight(1.1f),
                    onClick = { showPasteDialog = true },
                )
                SourceTypeChip(
                    icon = Icons.Filled.PlayArrow,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = "Books",
                    modifier = Modifier.weight(0.9f),
                    onClick = { filePicker.launch(arrayOf("application/pdf")) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceTypeChip(
                    icon = Icons.Outlined.CloudQueue,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    label = "Drive",
                    modifier = Modifier.weight(1f),
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                )
                SourceTypeChip(
                    icon = Icons.Outlined.ContentPaste,
                    label = "Copied text",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val text = clipboardManager.getText()?.text.orEmpty()
                        if (text.isNotBlank()) {
                            onAddText("Pasted Notes", text)
                        } else {
                            showPasteDialog = true
                        }
                    },
                )
            }

            // Drag & drop dropzone dashed border card
            Surface(
                onClick = { imagePicker.launch("image/*") },
                shape = RoundedCornerShape(16.dp),
                color = NotebookLmColors.SurfaceHigh.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.Border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.CloudUpload,
                        contentDescription = null,
                        tint = NotebookLmColors.TextSecondary,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "Drag & drop your files to upload. Supported file types: pdf, images, docs, audio, and more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NotebookLmColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }

    if (showPasteDialog) {
        var pasteTitle by remember { mutableStateOf("") }
        var pasteContent by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = NotebookLmColors.SurfaceHigh,
            title = { Text("Add Copied Text / Website", color = NotebookLmColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pasteTitle,
                        onValueChange = { pasteTitle = it },
                        label = { Text("Title or URL") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NotebookLmColors.TextPrimary,
                            unfocusedTextColor = NotebookLmColors.TextPrimary,
                            focusedBorderColor = NotebookLmColors.BlueAccent,
                            unfocusedBorderColor = NotebookLmColors.Border,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pasteContent,
                        onValueChange = { pasteContent = it },
                        label = { Text("Content or Markdown") },
                        minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NotebookLmColors.TextPrimary,
                            unfocusedTextColor = NotebookLmColors.TextPrimary,
                            focusedBorderColor = NotebookLmColors.BlueAccent,
                            unfocusedBorderColor = NotebookLmColors.Border,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pasteContent.isNotBlank()) {
                            onAddText(pasteTitle.ifBlank { "Pasted Source" }, pasteContent)
                            showPasteDialog = false
                        }
                    },
                ) {
                    Text("Add", color = NotebookLmColors.BlueAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel", color = NotebookLmColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun SourceTypeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = NotebookLmColors.TextSecondary,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    secondaryTint: Color = Color.Unspecified,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = NotebookLmColors.SurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, NotebookLmColors.BorderSoft),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp),
            )
            if (secondaryIcon != null) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    secondaryIcon,
                    contentDescription = null,
                    tint = secondaryTint,
                    modifier = Modifier.size(13.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = NotebookLmColors.TextPrimary,
                maxLines = 1,
            )
        }
    }
}
