package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.CalendarContract
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.core.sync.createCalendarIntent
import com.edukasyon.studentai.core.sync.getDummyScheduleItemsForNext7Days
import com.edukasyon.studentai.domain.model.AiModel
import com.edukasyon.studentai.domain.model.PreferredStudentStatus
import com.edukasyon.studentai.domain.model.ProfileEditPolicy
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.R
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.CalendarViewModel
import com.edukasyon.studentai.ui.viewmodel.NotesViewModel
import com.edukasyon.studentai.ui.viewmodel.ProfileViewModel
import com.edukasyon.studentai.ui.viewmodel.ProfileUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateFeaturesGuide: () -> Unit = {},
    onNavigateNotificationSettings: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onNavigateChangelog: () -> Unit = {},
    onNavigateSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp
    val context = androidx.compose.ui.platform.LocalContext.current
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJson(it) } }

    val exportScheduleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportScheduleCsv(it) } }

    val exportGradesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportGradesCsv(it) } }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotifications(true)
    }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    val calendarSyncLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            android.widget.Toast.makeText(context, "Event added to calendar", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.notificationsEnabled) {
        if (state.notificationsEnabled && android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    state.backupMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearBackupMessage()
        }
    }

    if (showImportConfirm != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("Import backup") },
            text = { Text("Merge imported data with existing records?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm?.let { viewModel.importJson(it, replace = false) }
                    showImportConfirm = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showImportConfirm?.let { viewModel.importJson(it, replace = true) }
                        showImportConfirm = null
                    }) { Text("Replace") }
                    TextButton(onClick = { showImportConfirm = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (state.showEditSheet) {
        ProfileEditSheet(
            state = state,
            onDismiss = viewModel::dismissEditSheet,
            onSave = viewModel::saveProfile,
            onDisplayNameChange = viewModel::updateEditDisplayName,
            onSchoolChange = viewModel::updateEditSchool,
            onPreferredStatusChange = viewModel::updateEditPreferredStatus,
            onBioChange = viewModel::updateEditBio,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== HERO GRADIENT CARD =====
        item {
            val profileSubtitle = buildList {
                state.user?.school?.takeIf { it.isNotBlank() }?.let { add(it) }
                state.user?.preferredStatus?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ").ifBlank { "SchedMate profile" }
            val displayName = state.user?.displayName ?: "Guest Student"
            val initial = displayName.firstOrNull()?.uppercase() ?: "S"

            GradientHeader(
                modifier = Modifier.clickable(onClick = viewModel::openEditSheet),
                title = displayName,
                subtitle = profileSubtitle,
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Version pill
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f),
                        ) {
                            Text(
                                text = "v${com.edukasyon.studentai.BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        IconButton(onClick = viewModel::openEditSheet) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit profile",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                },
                bottomContent = {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar circle with initial
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            state.user?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                                Text(
                                    text = bio,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!state.canEditProfile) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Next edit in ${state.daysUntilNextEdit}d",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
            )
        }

        // ===== QUICK ACTIONS ROW =====
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateSettings,
                )
                QuickActionCard(
                    icon = Icons.Default.Notifications,
                    label = "Alerts",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateNotificationSettings,
                )
                QuickActionCard(
                    icon = Icons.Default.HelpOutline,
                    label = "Help",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFeaturesGuide,
                )
            }
        }

        // ===== ACCOUNT SECTION =====
        item {
            SettingsGroup(title = "Account") {
                SettingsRow(
                    title = "Sync to Google Calendar",
                    subtitle = "Push your schedule to your default calendar",
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                )
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SettingsRow(
                    title = "Backup & restore",
                    subtitle = "Export or import your data (JSON)",
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                )
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SettingsRow(
                    title = if (state.isGoogleSignedIn) "Google account linked" else "Sign in with Google",
                    subtitle = state.firebaseEmail ?: "Sync across devices",
                    trailing = {
                        Icon(
                            if (state.isGoogleSignedIn) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (state.isGoogleSignedIn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                )
            }
        }

        // ===== FOOTER =====
        item {
            Text(
                text = "SchedMate v${com.edukasyon.studentai.BuildConfig.VERSION_NAME} · made for students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToFeaturesGuide: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateChangelog: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp
    val context = androidx.compose.ui.platform.LocalContext.current
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportJsonLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJson(it) } }

    val exportScheduleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportScheduleCsv(it) } }

    val exportGradesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportGradesCsv(it) } }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportConfirm = it } }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotifications(true)
    }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    val calendarSyncLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { /* no result handling needed for calendar insert intent */ }

    LaunchedEffect(state.notificationsEnabled) {
        if (state.notificationsEnabled && android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    state.backupMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearBackupMessage()
        }
    }

    if (showImportConfirm != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text("Import backup") },
            text = { Text("Merge imported data with existing records?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm?.let { viewModel.importJson(it, replace = false) }
                    showImportConfirm = null
                }) { Text("Merge") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showImportConfirm?.let { viewModel.importJson(it, replace = true) }
                        showImportConfirm = null
                    }) { Text("Replace") }
                    TextButton(onClick = { showImportConfirm = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (state.showEditSheet) {
        ProfileEditSheet(
            state = state,
            onDismiss = viewModel::dismissEditSheet,
            onSave = viewModel::saveProfile,
            onDisplayNameChange = viewModel::updateEditDisplayName,
            onSchoolChange = viewModel::updateEditSchool,
            onPreferredStatusChange = viewModel::updateEditPreferredStatus,
            onBioChange = viewModel::updateEditBio,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item {
            SettingsGroup(title = "Help") {
                SettingsRow(
                    title = "Features Guide",
                    subtitle = "Explore all app features and where to find them",
                    trailing = {
                        TextButton(onClick = onNavigateToFeaturesGuide) {
                            Text("Open")
                        }
                    }
                )
                SettingsRow(
                    title = "What's New",
                    subtitle = "See what changed in recent updates",
                    trailing = {
                        TextButton(onClick = onNavigateChangelog) {
                            Text("Open")
                        }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            leadingIcon = if (state.themeMode == mode) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                ThemeColorPicker(
                    primaryColorHex = state.primaryColorHex,
                    secondaryColorHex = state.secondaryColorHex,
                    themeMode = state.themeMode,
                    onPrimaryColorSelected = viewModel::setPrimaryColor,
                    onSecondaryColorSelected = viewModel::setSecondaryColor,
                    onResetColors = viewModel::resetThemeColors
                )
            }
        }

        item {
            SettingsGroup(title = "Notifications") {
                SettingsRow(
                    title = "Notification settings",
                    subtitle = "Class alerts, DND bypass, battery optimization",
                    trailing = {
                        TextButton(onClick = onNavigateToNotificationSettings) {
                            Text("Open")
                        }
                    }
                )
                SettingsRow(
                    title = "Enable notifications",
                    subtitle = "Master toggle for all reminders",
                    trailing = {
                        Switch(
                            checked = state.notificationsEnabled,
                            onCheckedChange = {
                                viewModel.setNotifications(it)
                                if (it && android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                )
                SettingsRow(
                    title = "Class reminders",
                    trailing = { Switch(checked = state.classReminders, onCheckedChange = viewModel::setClassReminders) }
                )
                SettingsRow(
                    title = "Task reminders",
                    trailing = { Switch(checked = state.taskReminders, onCheckedChange = viewModel::setTaskReminders) }
                )
                SettingsRow(
                    title = "Exam reminders",
                    trailing = { Switch(checked = state.examReminders, onCheckedChange = viewModel::setExamReminders) }
                )
            }
        }

        item {
            SettingsGroup(title = "Home Screen Widget") {
                WidgetSetupCard(
                    modifier = Modifier.padding(0.dp),
                    variant = WidgetSetupCardVariant.Profile,
                )
            }
        }

        item {
            SettingsGroup(title = "Cloud Sync") {
                val lastSyncedAt = state.lastSyncedAt
                val syncSubtitle = when {
                    state.isSigningInWithGoogle -> "Signing in with Google..."
                    !state.isGoogleSignedIn -> "Use the same Google account on phone and tablet to sync decks, notes, and planner"
                    state.isSyncing -> "Syncing your study data..."
                    !state.isOnline -> "Offline - local data available, sync when online"
                    state.syncStatus == SyncState.FAILED -> "Last sync failed - tap Sync now to retry"
                    lastSyncedAt != null -> "Last synced ${'$'}{formatSyncTime(lastSyncedAt)}"
                    else -> "Keeps decks, notes, planner, and grades in sync"
                }
                if (!state.isGoogleSignedIn) {
                    Text(
                        text = syncSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.getGoogleSignInIntent()?.let { googleSignInLauncher.launch(it) }
                        },
                        enabled = !state.isSigningInWithGoogle && state.isOnline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        if (state.isSigningInWithGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google")
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    SettingsRow(
                        title = "Signed in",
                        subtitle = state.firebaseEmail ?: "Google account linked",
                        trailing = {
                            TextButton(onClick = viewModel::signOutGoogle) {
                                Text("Sign out")
                            }
                        }
                    )
                }
                SettingsRow(
                    title = "Multi-device sync",
                    subtitle = if (state.isGoogleSignedIn) syncSubtitle else "Sign in above to enable sync",
                    trailing = {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(
                                onClick = viewModel::syncNow,
                                enabled = state.isGoogleSignedIn && state.isOnline,
                            ) {
                                Text("Sync now")
                            }
                        }
                    }
                )
                SettingsRow(
                    title = "Sync to Google Calendar",
                    subtitle = "Add upcoming classes to your device calendar",
                    trailing = {
                        TextButton(
                            onClick = {
                                getDummyScheduleItemsForNext7Days().forEach { item ->
                                    calendarSyncLauncher.launch(createCalendarIntent(context, item))
                                }
                            },
                        ) {
                            Text("Sync")
                        }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Data") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportJsonLauncher.launch("studentai_backup.json") }) { Text("Export JSON") }
                    OutlinedButton(onClick = { exportScheduleLauncher.launch("schedule.csv") }) { Text("Schedule CSV") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportGradesLauncher.launch("grades.csv") }) { Text("Grades CSV") }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import JSON") }
                }
            }
        }

        item {
            SettingsGroup(title = "AI Settings") {
                SettingsRow(
                    title = "Connection status",
                    subtitle = if (state.isOnline) "Connected to AI backend" else "Offline - local AI fallback active",
                    trailing = {
                        Text(
                            text = if (state.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isOnline) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Jevi chat model",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Selected on the Jevi AI screen. Auto is fast and unlimited; Step 3.7 Flash is stronger and allows 25 requests every 10 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state.aiModel) {
                        AiModel.AUTO -> "Current: Auto - fast general answers"
                        AiModel.REASONING -> "Current: Step 3.7 Flash - stronger reasoning (limited)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            SettingsGroup(title = "Privacy") {
                Text(
                    text = "Data is stored locally on your device. AI features send only the content you select to the backend. No API keys are stored in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsGroup(title = "About") {
                SettingsRow(
                    title = "SchedMate",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    trailing = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenEditor: (noteId: String) -> Unit = {},
    onCreateNote: () -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Notes") })
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search notes...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        if (state.isLoading) {
            LoadingState()
        } else if (state.notes.isEmpty()) {
            EmptyState(
                "No notes",
                "Create your first note.",
                actionLabel = "Add Note",
                onAction = onCreateNote,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(state.notes, key = { it.id }) { note ->
                    StudentAiCard(onClick = { onOpenEditor(note.id) }) {
                        Text(note.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            note.content,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete note")
                            }
                        }
                    }
                }
            }
        }
        StudentAiAddFab(
            onClick = onCreateNote,
            contentDescription = "Add note",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val holidaysLoading by viewModel.holidaysLoading.collectAsStateWithLifecycle()
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }

    LaunchedEffect(Unit) {
        viewModel.refreshHolidays()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Calendar", style = MaterialTheme.typography.headlineSmall) }
        if (holidaysLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Loading holidays…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (holidays.isNotEmpty()) {
            item { Text("Philippine Holidays", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            items(holidays) { holiday ->
                StudentAiCard {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(holiday.name, style = MaterialTheme.typography.titleSmall)
                            Text(dateFormat.format(java.util.Date(holiday.dateMillis)))
                        }
                        Surface(
                            color = if (holiday.type.name == "REGULAR") MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                holiday.type.label,
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
        item { Text("Your Events", style = MaterialTheme.typography.titleSmall) }
        if (events.isEmpty()) {
            item { EmptyState("No events", "Add tasks, exams, or assignments to see them on your calendar.") }
        } else {
            items(events) { event ->
                StudentAiCard {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text("${event.type} • ${dateFormat.format(java.util.Date(event.startAt))}")
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val adaptiveWidth = rememberAdaptiveWidth()

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> viewModel.refreshPermissionState() }

    LaunchedEffect(state.step) {
        viewModel.refreshPermissionState()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.step > 0) {
                IconButton(onClick = viewModel::previousStep) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(StudentAiShapes.chip)
            )
        }

        Spacer(Modifier.height(24.dp))
        OnboardingIllustration(step = state.step)
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = if (adaptiveWidth == AdaptiveWidth.Expanded) Modifier.width(560.dp) else Modifier.fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally { it / 4 }) togetherWith
                        (fadeOut(tween(200)) + slideOutHorizontally { -it / 4 })
                },
                label = "onboardingStep"
            ) { step ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (step) {
                        0 -> OnboardingWelcomeStep(state, viewModel)
                        1 -> OnboardingSchoolStep(state, viewModel)
                        2 -> OnboardingAppearanceStep(state, viewModel)
                        3 -> OnboardingPermissionsStep(
                            state = state,
                            onRequestNotifications = {
                                if (android.os.Build.VERSION.SDK_INT >= 33) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onContinue = viewModel::nextStep
                        )
                        4 -> OnboardingNotifyStep(state, viewModel)
                        5 -> OnboardingWidgetsStep(
                            onExplore = {
                                viewModel.markWidgetsExplored()
                                viewModel.nextStep()
                            },
                            onSkip = viewModel::skipWidgets
                        )
                        else -> OnboardingFinishStep(state, onComplete, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Welcome to SchedMate", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(
        "Let's get to know you so we can personalize your study companion.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.displayName,
        onValueChange = viewModel::updateDisplayName,
        label = { Text("Your name") },
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.chip,
        enabled = !state.isSaving
    )
    Button(
        onClick = viewModel::nextStep,
        enabled = state.displayName.isNotBlank() && !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.button
    ) { Text("Continue") }
}

@Composable
private fun OnboardingSchoolStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("School details", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Tell us about your school so we can personalize your experience.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(state.school, viewModel::updateSchool, label = { Text("School") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    OutlinedTextField(state.gradeLevel, viewModel::updateGradeLevel, label = { Text("Grade level") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    OutlinedTextField(state.section, viewModel::updateSection, label = { Text("Section") }, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.chip)
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingAppearanceStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Appearance", style = MaterialTheme.typography.headlineMedium)
    Text("Choose a theme — you can change this anytime in Profile.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = state.themeMode == mode,
                onClick = { viewModel.setTheme(mode) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                leadingIcon = if (state.themeMode == mode) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }
    }
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingPermissionsStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    onRequestNotifications: () -> Unit,
    onContinue: () -> Unit
) {
    Text("Stay on track", style = MaterialTheme.typography.headlineMedium)
    Text(
        "SchedMate uses notifications for class reminders, tasks, and exams. Grant permission so alerts arrive on time.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (android.os.Build.VERSION.SDK_INT >= 33 && !state.notificationPermissionGranted) {
        Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
            Text("Allow notifications")
        }
    } else {
        Text("Notifications enabled ✓", color = MaterialTheme.colorScheme.secondary)
    }
    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
        Text("Continue")
    }
}

@Composable
private fun OnboardingNotifyStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Notify me", style = MaterialTheme.typography.headlineMedium)
    Text("When should we remind you about classes?", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsRow(
        title = "Class reminders",
        trailing = { Switch(checked = state.classReminders, onCheckedChange = viewModel::setClassReminders) }
    )
    SettingsRow(
        title = "At class time",
        trailing = { Switch(checked = state.classReminderAtTime, onCheckedChange = viewModel::setClassReminderAtTime, enabled = state.classReminders) }
    )
    SettingsRow(
        title = "15 minutes before",
        trailing = { Switch(checked = state.classReminder15MinBefore, onCheckedChange = viewModel::setClassReminder15MinBefore, enabled = state.classReminders) }
    )
    SettingsRow(
        title = "Task reminders",
        trailing = { Switch(checked = state.taskReminders, onCheckedChange = viewModel::setTaskReminders) }
    )
    SettingsRow(
        title = "Exam reminders",
        trailing = { Switch(checked = state.examReminders, onCheckedChange = viewModel::setExamReminders) }
    )
    Button(onClick = viewModel::nextStep, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) { Text("Continue") }
}

@Composable
private fun OnboardingWidgetsStep(onExplore: () -> Unit, onSkip: () -> Unit) {
    Text("Home screen widgets", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Pin a widget to see today's schedule or upcoming tasks without opening the app.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    WidgetSetupCard(modifier = Modifier.fillMaxWidth())
    Button(onClick = onExplore, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
        Text("Add to Home Screen")
    }
    TextButton(onClick = onSkip) { Text("Skip for now") }
}

@Composable
private fun OnboardingFinishStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    onComplete: () -> Unit,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("🎓", style = MaterialTheme.typography.headlineMedium)
            }
        }
        Surface(shape = StudentAiShapes.chip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                "You're all set!",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
    Text(
        "Here's what we set up, ${state.displayName.ifBlank { "Student" }}.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OnboardingSummaryRow("Appearance", state.appearanceLabel)
        OnboardingSummaryRow("Permissions", "${state.permissionsGrantedCount}/3 granted")
        OnboardingSummaryRow("Notify me", state.notifyMeSummary)
        OnboardingSummaryRow("Widgets", state.widgetsLabel)
        OnboardingSummaryRow("Profile", state.displayName.ifBlank { "Not set" })
    }
    Button(
        onClick = { viewModel.completeOnboarding(onFinished = onComplete) },
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.button
    ) {
        Text(if (state.isSaving) "Saving…" else "Finish")
    }
}

@Composable
private fun OnboardingSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnboardingIllustration(step: Int) {
    if (step == 0) {
        Image(
            painter = painterResource(R.drawable.wala),
            contentDescription = "SchedMate logo",
            modifier = Modifier.size(120.dp),
        )
        return
    }
    val icon = when (step) {
        1 -> Icons.Default.Edit
        else -> Icons.Default.CheckCircle
    }
    Surface(
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditSheet(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSchoolChange: (String) -> Unit,
    onPreferredStatusChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val draft = state.editDraft
    val canSave = state.canEditProfile &&
        draft.displayName.isNotBlank() &&
        !state.isSavingProfile

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Name, school, status, and bio can be updated once per week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.canEditProfile) {
                Surface(
                    shape = StudentAiShapes.chip,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "You can update your profile again in ${state.daysUntilNextEdit} day(s).",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            OutlinedTextField(
                value = draft.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                shape = StudentAiShapes.chip,
                enabled = state.canEditProfile && !state.isSavingProfile,
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.school,
                onValueChange = onSchoolChange,
                label = { Text("School") },
                modifier = Modifier.fillMaxWidth(),
                shape = StudentAiShapes.chip,
                enabled = state.canEditProfile && !state.isSavingProfile,
                singleLine = true,
            )

            Text(
                "Preferred status",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PreferredStudentStatus.options.forEach { status ->
                    FilterChip(
                        selected = draft.preferredStatus == status.displayName,
                        onClick = {
                            if (state.canEditProfile && !state.isSavingProfile) {
                                onPreferredStatusChange(status.displayName)
                            }
                        },
                        label = { Text(status.displayName) },
                        enabled = state.canEditProfile && !state.isSavingProfile,
                    )
                }
            }

            OutlinedTextField(
                value = draft.bio,
                onValueChange = onBioChange,
                label = { Text("Bio") },
                placeholder = { Text("Tell Jevi about your goals, interests, or study style…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                shape = StudentAiShapes.chip,
                enabled = state.canEditProfile && !state.isSavingProfile,
                minLines = 4,
                maxLines = 8,
            )
            Text(
                text = "${draft.bio.length}/${ProfileEditPolicy.BIO_MAX_LENGTH}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )

            state.profileSaveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSavingProfile,
                    shape = StudentAiShapes.button,
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = canSave,
                    shape = StudentAiShapes.button,
                ) {
                    if (state.isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestamp: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(timestamp))
}
