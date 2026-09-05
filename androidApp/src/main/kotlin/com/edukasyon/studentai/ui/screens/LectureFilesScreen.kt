package com.edukasyon.studentai.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.LectureFile
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.LectureFilesViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureFilesScreen(
    onBack: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToPlanner: () -> Unit = {},
    showBackButton: Boolean = true,
    viewModel: LectureFilesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingMime by remember { mutableStateOf<String?>(null) }
    var fileTitle by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pendingUri = uri
            pendingMime = context.contentResolver.getType(uri)
            fileTitle = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.replace('-', ' ')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "Lecture file"
            showAddDialog = true
        }
    }

    Scaffold(
        topBar = {
            if (showBackButton) {
                TopAppBar(
                    title = { Text("Lecture Files") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            StudentAiAddFab(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                contentDescription = "Add file",
            )
        },
        bottomBar = {
            StudyMaterialsPillNav(
                selectedIndex = 1,
                onNotes = onNavigateToNotes,
                onFiles = {},
                onTasks = onNavigateToPlanner
            )
        }
    ) { padding ->
        AdaptiveContentContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!showBackButton) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Lecture Files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Keep lecture PDFs, slides, and photos organized by subject.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LocalStorageWarningBanner(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (state.isLoading) {
                LoadingState(message = "Loading files…")
            } else if (state.files.isEmpty()) {
                LectureFilesEmptyState(
                    onAddFile = { filePickerLauncher.launch(arrayOf("application/pdf", "image/*")) }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val grouped = state.files.groupBy { it.subjectId }
                    grouped.forEach { (subjectId, files) ->
                        item {
                            Text(
                                state.subjectName(subjectId),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        items(files, key = { it.id }) { file ->
                            LectureFileCard(
                                file = file,
                                subjectLabel = state.subjectName(file.subjectId),
                                onOpen = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(android.net.Uri.parse(file.fileUri), file.mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                onDelete = { viewModel.deleteFile(file.id) }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    if (showAddDialog && pendingUri != null) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                pendingUri = null
            },
            title = { Text("Add lecture file") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = fileTitle,
                        onValueChange = { fileTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = StudentAiShapes.chip
                    )
                    Text("Subject", style = MaterialTheme.typography.labelMedium)
                    FlowRowSubjectChips(
                        subjects = state.subjects,
                        selectedId = selectedSubjectId,
                        onSelected = { selectedSubjectId = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUri?.let { uri ->
                            viewModel.addFile(
                                title = fileTitle.trim(),
                                uri = uri.toString(),
                                mimeType = pendingMime ?: "application/octet-stream",
                                subjectId = selectedSubjectId
                            )
                        }
                        showAddDialog = false
                        pendingUri = null
                        fileTitle = ""
                        selectedSubjectId = null
                    },
                    enabled = fileTitle.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    pendingUri = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LocalStorageWarningBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StudentAiShapes.card,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column {
                Text("Not synced yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Lecture files stay on this device only. Deleting the app can permanently lose them — we're still working on syncing this feature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LectureFilesEmptyState(onAddFile: () -> Unit) {
    ModernEmptyState(
        title = "No lecture files yet",
        message = "Add a PDF, slide deck, or photo to get started.",
        actionLabel = "Add File",
        onAction = onAddFile,
modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LectureFileCard(
    file: LectureFile,
    subjectLabel: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val icon = when {
        file.mimeType.startsWith("image/") -> Icons.Default.Image
        file.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
        else -> Icons.Default.InsertDriveFile
    }
    val fileTypeDescription = when {
        file.mimeType.startsWith("image/") -> "Image file"
        file.mimeType.contains("pdf") -> "PDF file"
        else -> "Document file"
    }

    ModernCard(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = fileTypeDescription, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subjectLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dateFormat.format(Date(file.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSubjectChips(
    subjects: List<com.edukasyon.studentai.domain.model.Subject>,
    selectedId: String?,
    onSelected: (String?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelected(null) },
            label = {
                Text(
                    "General",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        subjects.take(4).forEach { subject ->
            FilterChip(
                selected = selectedId == subject.id,
                onClick = { onSelected(subject.id) },
                label = {
                    Text(
                        subject.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
fun StudyMaterialsPillNav(
    selectedIndex: Int,
    onNotes: () -> Unit,
    onFiles: () -> Unit,
    onTasks: () -> Unit
) {
    PillTabBar(
        tabs = listOf(
            PillTabSpec(label = "Notes", icon = Icons.Default.Note, selectedIcon = Icons.Default.Note),
            PillTabSpec(label = "Files", icon = Icons.Default.Folder, selectedIcon = Icons.Default.Folder),
            PillTabSpec(label = "Tasks", icon = Icons.Default.TaskAlt, selectedIcon = Icons.Default.TaskAlt),
        ),
        selectedIndex = selectedIndex,
        onTabSelected = { index ->
            when (index) {
                0 -> onNotes()
                1 -> onFiles()
                2 -> onTasks()
            }
        }
    )
}
