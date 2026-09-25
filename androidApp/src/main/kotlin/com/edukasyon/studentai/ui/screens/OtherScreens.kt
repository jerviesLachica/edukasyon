package com.edukasyon.studentai.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.*
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
import com.edukasyon.studentai.domain.model.AiModel
import com.edukasyon.studentai.domain.model.PreferredStudentStatus
import com.edukasyon.studentai.domain.model.ProfileEditPolicy
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.R
import com.edukasyon.studentai.ui.adaptive.AdaptiveContentContainer
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.components.*
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.components.mascot.SchedMateMascot
import com.edukasyon.studentai.ui.components.mascot.MascotMood
import com.edukasyon.studentai.ui.components.DocToStudyStudioSheet
import com.edukasyon.studentai.domain.model.Note
import com.edukasyon.studentai.ui.viewmodel.CalendarViewModel
import com.edukasyon.studentai.ui.viewmodel.CalendarTab
import com.edukasyon.studentai.ui.viewmodel.NotesViewModel
import com.edukasyon.studentai.ui.viewmodel.NotesFilter
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

    state.calendarSyncMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    if (state.feedbackSuccess) {
        state.feedbackMessage?.let { msg ->
            LaunchedEffect(msg) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                viewModel.clearFeedbackMessage()
            }
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

    if (state.showFeedbackDialog) {
        FeedbackDialog(
            initialCooldownSeconds = viewModel.getFeedbackRemainingCooldown(),
            isSubmitting = state.isSubmittingFeedback,
            errorMessage = if (!state.feedbackSuccess) state.feedbackMessage else null,
            onDismiss = viewModel::dismissFeedbackDialog,
            onSubmit = { cat, title, desc, contact, hp ->
                viewModel.submitFeedback(cat, title, desc, contact, hp)
            }
        )
    }

    AdaptiveContentContainer {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    label = "Help",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFeaturesGuide,
                )
                QuickActionCard(
                    icon = Icons.Default.Feedback,
                    label = "Feedback",
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::openFeedbackDialog,
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

        // ===== FEEDBACK & SUPPORT SECTION =====
        item {
            SettingsGroup(title = "Feedback & Bug Reports") {
                SettingsRow(
                    title = "Suggest feature or report bug",
                    subtitle = "Submit suggestions or issues directly to our admin team",
                    modifier = Modifier.clickable(onClick = viewModel::openFeedbackDialog),
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onNavigateToFeaturesGuide: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateChangelog: () -> Unit = {},
    onNavigateRedeemShare: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp
    val context = androidx.compose.ui.platform.LocalContext.current
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }
    var showUnsyncConfirm by remember { mutableStateOf(false) }

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

    val calendarPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.onCalendarPermissionsGranted(context)
        } else {
            android.widget.Toast.makeText(
                context,
                "Calendar permissions required to sync schedule",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showUnsyncConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsyncConfirm = false },
            title = { Text("Unsync from Google Calendar") },
            text = { Text("This will remove all SchedMate-added events from your Google Calendar. Your SchedMate data is not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsyncConfirm = false
                    if (com.edukasyon.studentai.core.sync.hasCalendarPermissions(context)) {
                        viewModel.unsyncFromGoogleCalendar(context)
                    } else {
                        calendarPermissionLauncher.launch(com.edukasyon.studentai.core.sync.CALENDAR_PERMISSIONS)
                    }
                }) {
                    Text("Remove events", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsyncConfirm = false }) { Text("Cancel") }
            }
        )
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

    state.calendarSyncMessage?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    if (state.feedbackSuccess) {
        state.feedbackMessage?.let { msg ->
            LaunchedEffect(msg) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                viewModel.clearFeedbackMessage()
            }
        }
    }

    if (state.showFeedbackDialog) {
        FeedbackDialog(
            initialCooldownSeconds = viewModel.getFeedbackRemainingCooldown(),
            isSubmitting = state.isSubmittingFeedback,
            errorMessage = if (!state.feedbackSuccess) state.feedbackMessage else null,
            onDismiss = viewModel::dismissFeedbackDialog,
            onSubmit = { cat, title, desc, contact, hp ->
                viewModel.submitFeedback(cat, title, desc, contact, hp)
            }
        )
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

    AdaptiveContentContainer {
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
            SettingsGroup(title = "Share") {
                SettingsRow(
                    title = "Import shared schedule or deck",
                    subtitle = "Enter a 6-character code from a friend or scan their QR",
                    trailing = {
                        TextButton(onClick = onNavigateRedeemShare) {
                            Text("Open")
                        }
                    }
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
                    lastSyncedAt != null -> "Last synced ${formatSyncTime(lastSyncedAt)}"
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
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        if (state.isSyncingCalendar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    if (com.edukasyon.studentai.core.sync.hasCalendarPermissions(context)) {
                                        viewModel.onCalendarPermissionsGranted(context)
                                    } else {
                                        calendarPermissionLauncher.launch(com.edukasyon.studentai.core.sync.CALENDAR_PERMISSIONS)
                                    }
                                },
                            ) {
                                Text(if (state.calendarSyncedAt != null) "Re-sync" else "Sync")
                            }
                        }
                    }
                )
                SettingsRow(
                    title = "Unsync from Google Calendar",
                    subtitle = if (state.calendarSyncedAt != null)
                        "Last synced — remove all SchedMate events"
                    else
                        "Removes only SchedMate-added events, leaves other entries alone",
                    trailing = {
                        TextButton(
                            onClick = { showUnsyncConfirm = true },
                            enabled = !state.isSyncingCalendar,
                        ) {
                            Text("Unsync")
                        }
                    }
                )
            }
        }

        item {
            SettingsGroup(title = "Data") {
                // FlowRow: buttons wrap instead of clipping on narrow screens.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { exportJsonLauncher.launch("studentai_backup.json") }) { Text("Export JSON") }
                    OutlinedButton(onClick = { exportScheduleLauncher.launch("schedule.csv") }) { Text("Schedule CSV") }
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
                    "Selected on the Jevi AI screen. Auto is fast and unlimited; Agnes 2.5 Flash is stronger and allows 25 requests every 10 minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state.aiModel) {
                        AiModel.AUTO -> "Current: Auto - fast general answers"
                        AiModel.REASONING -> "Current: Agnes 2.5 Flash - stronger reasoning (limited)"
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
            SettingsGroup(title = "Feedback & Bug Reports") {
                SettingsRow(
                    title = "Suggest feature or report bug",
                    subtitle = "Submit suggestions or issues directly to our admin team",
                    trailing = {
                        TextButton(onClick = viewModel::openFeedbackDialog) {
                            Text("Submit")
                        }
                    }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenEditor: (noteId: String) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToPlanner: () -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
    var noteForStudy by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var menuExpandedForNoteId by remember { mutableStateOf<String?>(null) }

    val totalNotes = state.notes.size
    val pinnedCount = state.notes.count { it.isPinned }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Notes", fontWeight = FontWeight.Bold)
                        if (totalNotes > 0) {
                            Text(
                                "$totalNotes ${if (totalNotes == 1) "note" else "notes"}" +
                                    (if (pinnedCount > 0) " · $pinnedCount pinned" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            StudentAiAddFab(
                onClick = onCreateNote,
                contentDescription = "Add note",
            )
        },
        bottomBar = {
            StudyMaterialsPillNav(
                selectedIndex = 0,
                onNotes = {},
                onFiles = onNavigateToFiles,
                onTasks = onNavigateToPlanner
            )
        }
    ) { padding ->
        AdaptiveContentContainer {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search notes...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    shape = StudentAiShapes.chip,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.filter == NotesFilter.ALL,
                        onClick = { viewModel.setFilter(NotesFilter.ALL) },
                        label = { Text("All") },
                        leadingIcon = if (state.filter == NotesFilter.ALL) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                    FilterChip(
                        selected = state.filter == NotesFilter.PINNED,
                        onClick = { viewModel.setFilter(NotesFilter.PINNED) },
                        label = { Text("Pinned") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (state.filter == NotesFilter.PINNED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    FilterChip(
                        selected = state.filter == NotesFilter.FAVORITES,
                        onClick = { viewModel.setFilter(NotesFilter.FAVORITES) },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (state.filter == NotesFilter.FAVORITES) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
                if (state.isLoading) {
                    LoadingState()
                } else if (state.notes.isEmpty()) {
                    val isSearching = state.searchQuery.isNotBlank()
                    ModernEmptyState(
                        title = if (isSearching) "No notes match '${state.searchQuery}'" else "No notes yet",
                        message = if (isSearching) "Try a different search term or clear the filter." else "Create your first note to start organizing your study material.",
                        actionLabel = if (isSearching) "Clear search" else "Add Note",
                        onAction = if (isSearching) { { viewModel.search("") } } else onCreateNote,
                        illustration = {
                            SchedMateMascot(
                                mood = MascotMood.Planning,
                                size = 115.dp,
                                interactive = true,
                                customSpeechText = if (!isSearching) "Jot down class notes and I'll generate study decks from them!" else null,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.notes, key = { it.id }) { note ->
                            ModernCard(
                                onClick = { onOpenEditor(note.id) },
                                modifier = if (note.isPinned) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                } else Modifier,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        if (note.isPinned) Icons.Default.PushPin else Icons.AutoMirrored.Filled.Note,
                                        contentDescription = null,
                                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                note.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (note.isFavorite) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Favorite,
                                                    contentDescription = "Favorite",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                        if (note.content.isNotBlank()) {
                                            Text(
                                                note.content,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                dateFormat.format(java.util.Date(note.updatedAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                            if (note.tags.isNotEmpty()) {
                                                note.tags.take(2).forEach { tag ->
                                                    Surface(
                                                        shape = StudentAiShapes.chip,
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                    ) {
                                                        Text(
                                                            tag,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Box {
                                        IconButton(onClick = { menuExpandedForNoteId = note.id }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Note options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpandedForNoteId == note.id,
                                            onDismissRequest = { menuExpandedForNoteId = null },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if (note.isPinned) "Unpin note" else "Pin to top") },
                                                leadingIcon = {
                                                    Icon(Icons.Default.PushPin, contentDescription = null)
                                                },
                                                onClick = {
                                                    menuExpandedForNoteId = null
                                                    viewModel.togglePin(note)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (note.isFavorite) "Remove favorite" else "Add to favorites") },
                                                leadingIcon = {
                                                    Icon(
                                                        if (note.isFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    menuExpandedForNoteId = null
                                                    viewModel.toggleFavorite(note)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("AI Study Tools") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                },
                                                onClick = {
                                                    menuExpandedForNoteId = null
                                                    noteForStudy = note
                                                },
                                            )
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = {
                                                    Text("Delete note", color = MaterialTheme.colorScheme.error)
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {
                                                    menuExpandedForNoteId = null
                                                    noteToDelete = note
                                                },
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
    }

    noteForStudy?.let { note ->
        DocToStudyStudioSheet(
            initialText = Pair(note.title, note.content),
            onDismissRequest = { noteForStudy = null },
        )
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = { Text("Are you sure you want to delete \"${note.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        noteToDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}


@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val selectedDateMillis by viewModel.selectedDateMillis.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val holidays by viewModel.holidays.collectAsStateWithLifecycle()
    val upcomingHolidays by viewModel.upcomingHolidays.collectAsStateWithLifecycle()
    val longWeekends by viewModel.longWeekends.collectAsStateWithLifecycle()
    val holidaysLoading by viewModel.holidaysLoading.collectAsStateWithLifecycle()
    val isLiveApiOnline by viewModel.isLiveApiOnline.collectAsStateWithLifecycle()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
    val timeFormat = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val dayHeaderFormat = remember { java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()) }

    val todayCalendar = remember { java.util.Calendar.getInstance() }
    val todayYear = remember(todayCalendar) { todayCalendar.get(java.util.Calendar.YEAR) }
    val todayMonth = remember(todayCalendar) { todayCalendar.get(java.util.Calendar.MONTH) }
    val todayDay = remember(todayCalendar) { todayCalendar.get(java.util.Calendar.DAY_OF_MONTH) }

    val dayEventTypes = remember(events, holidays, visibleMonth) {
        val map = mutableMapOf<Int, MutableSet<String>>()
        val cal = java.util.Calendar.getInstance()
        events.forEach { ev ->
            cal.timeInMillis = ev.startAt
            if (cal.get(java.util.Calendar.YEAR) == visibleMonth.year && cal.get(java.util.Calendar.MONTH) == visibleMonth.month) {
                val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
                map.getOrPut(d) { mutableSetOf() }.add(ev.type.uppercase())
            }
        }
        holidays.forEach { h ->
            cal.timeInMillis = h.dateMillis
            if (cal.get(java.util.Calendar.YEAR) == visibleMonth.year && cal.get(java.util.Calendar.MONTH) == visibleMonth.month) {
                val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
                map.getOrPut(d) { mutableSetOf() }.add("HOLIDAY")
            }
        }
        map
    }

    val displayedEvents = remember(events, selectedDateMillis) {
        if (selectedDateMillis == null) events
        else {
            val start = selectedDateMillis!!
            val end = start + (24L * 60 * 60 * 1000)
            events.filter { it.startAt in start until end }
        }
    }

    val displayedHolidays = remember(holidays, selectedDateMillis) {
        if (selectedDateMillis == null) holidays
        else {
            val start = selectedDateMillis!!
            val end = start + (24L * 60 * 60 * 1000)
            holidays.filter { it.dateMillis in start until end }
        }
    }

    AdaptiveContentContainer {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Calendar",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Dynamic academic & holiday schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isLiveApiOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isLiveApiOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                )
                                Text(
                                    if (isLiveApiOnline) "Live API" else "Offline Cache",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLiveApiOnline) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                viewModel.refreshHolidays()
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Tab Selector Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = activeTab == CalendarTab.AGENDA,
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.setActiveTab(CalendarTab.AGENDA)
                        },
                        label = { Text("Agenda & Grid") },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = activeTab == CalendarTab.UPCOMING_HOLIDAYS,
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.setActiveTab(CalendarTab.UPCOMING_HOLIDAYS)
                        },
                        label = { Text("Holidays (${upcomingHolidays.size})") },
                        leadingIcon = {
                            Icon(Icons.Default.Celebration, contentDescription = null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = activeTab == CalendarTab.LONG_WEEKENDS,
                        onClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            viewModel.setActiveTab(CalendarTab.LONG_WEEKENDS)
                        },
                        label = { Text("Long Weekends (${longWeekends.size})") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            if (holidaysLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Syncing dynamic calendar data…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Tab 1: Agenda & Calendar Grid
            if (activeTab == CalendarTab.AGENDA) {
                // Month Navigation Card & Calendar Grid
                item {
                    StudentAiCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Month Header with Prev / Next & Today button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.previousMonth()
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        visibleMonth.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Tap date to filter agenda",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            viewModel.goToToday()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(30.dp),
                                    ) {
                                        Text("Today", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            viewModel.nextMonth()
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Day of Week Names Row
                            val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                            Row(modifier = Modifier.fillMaxWidth()) {
                                weekdays.forEach { name ->
                                    Text(
                                        text = name,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )
                                }
                            }

                            // 7-Column Days Grid
                            val leadingBlanks = (visibleMonth.firstDayOfWeek - 1).coerceAtLeast(0)
                            val totalDays = visibleMonth.daysInMonth
                            val totalSlots = leadingBlanks + totalDays
                            val rowsCount = (totalSlots + 6) / 7

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                for (row in 0 until rowsCount) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        for (col in 0 until 7) {
                                            val slotIndex = row * 7 + col
                                            val dayNumber = slotIndex - leadingBlanks + 1
                                            if (dayNumber in 1..totalDays) {
                                                val dayStartMillis = remember(visibleMonth, dayNumber) {
                                                    val c = java.util.Calendar.getInstance().apply {
                                                        set(java.util.Calendar.YEAR, visibleMonth.year)
                                                        set(java.util.Calendar.MONTH, visibleMonth.month)
                                                        set(java.util.Calendar.DAY_OF_MONTH, dayNumber)
                                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                        set(java.util.Calendar.MINUTE, 0)
                                                        set(java.util.Calendar.SECOND, 0)
                                                        set(java.util.Calendar.MILLISECOND, 0)
                                                    }
                                                    c.timeInMillis
                                                }
                                                val isToday = visibleMonth.year == todayYear &&
                                                        visibleMonth.month == todayMonth &&
                                                        dayNumber == todayDay
                                                val isSelected = selectedDateMillis == dayStartMillis
                                                val types = dayEventTypes[dayNumber] ?: emptySet()

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                isSelected -> MaterialTheme.colorScheme.primary
                                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                                else -> androidx.compose.ui.graphics.Color.Transparent
                                                            }
                                                        )
                                                        .clickable {
                                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                            viewModel.selectDate(dayStartMillis)
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                    ) {
                                                        Text(
                                                            dayNumber.toString(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                            color = when {
                                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                                else -> MaterialTheme.colorScheme.onSurface
                                                            },
                                                        )
                                                        if (types.isNotEmpty()) {
                                                            Row(
                                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                modifier = Modifier.padding(top = 1.dp),
                                                            ) {
                                                                if ("EXAM" in types) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .clip(CircleShape)
                                                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error)
                                                                    )
                                                                }
                                                                if ("ASSIGNMENT" in types || "TASK" in types || "CLASS" in types) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .clip(CircleShape)
                                                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                                                    )
                                                                }
                                                                if ("HOLIDAY" in types) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(4.dp)
                                                                            .clip(CircleShape)
                                                                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color(0xFFFFB300))
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Date Filter Banner (if a date is tapped)
                if (selectedDateMillis != null) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        dayHeaderFormat.format(java.util.Date(selectedDateMillis!!)),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.selectDate(null)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Text("Show Month", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Holidays Section
                if (displayedHolidays.isNotEmpty()) {
                    item {
                        Text(
                            if (selectedDateMillis != null) "Holidays on this day" else "Philippine Holidays (${visibleMonth.title})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(displayedHolidays) { holiday ->
                        StudentAiCard {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(holiday.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    if (!holiday.localName.isNullOrBlank()) {
                                        Text(
                                            holiday.localName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        dateFormat.format(java.util.Date(holiday.dateMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    color = if (holiday.type.name == "REGULAR") MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        holiday.type.label,
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (holiday.type.name == "REGULAR") MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                // Events Section
                item {
                    Text(
                        if (selectedDateMillis != null) "Events on this day" else "Events in ${visibleMonth.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (displayedEvents.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (selectedDateMillis != null) "No events on this day" else "No events in ${visibleMonth.title}",
                            message = if (selectedDateMillis != null) "Tap another date or add tasks and exams in Planner to see them here."
                            else "Add tasks, exams, or assignments in Planner to see them on your calendar.",
                            illustration = {
                                SchedMateMascot(
                                    mood = MascotMood.Planning,
                                    size = 115.dp,
                                    interactive = true,
                                    customSpeechText = "Your schedule looks open! Keep up the momentum or schedule a study block.",
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(displayedEvents, key = { it.id }) { event ->
                        val accentColor = remember(event.colorHex) {
                            try {
                                val cleanHex = if (event.colorHex.startsWith("#")) event.colorHex else "#${event.colorHex}"
                                androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(cleanHex))
                            } catch (_: Exception) {
                                null
                            }
                        }
                        StudentAiCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accentColor ?: MaterialTheme.colorScheme.primary),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            event.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (event.type.uppercase()) {
                                                "EXAM" -> MaterialTheme.colorScheme.errorContainer
                                                "ASSIGNMENT", "TASK" -> MaterialTheme.colorScheme.primaryContainer
                                                "CLASS" -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        ) {
                                            Text(
                                                event.type.uppercase(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when (event.type.uppercase()) {
                                                    "EXAM" -> MaterialTheme.colorScheme.onErrorContainer
                                                    "ASSIGNMENT", "TASK" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    "CLASS" -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                    }
                                    val formattedDate = dateFormat.format(java.util.Date(event.startAt))
                                    val formattedTime = timeFormat.format(java.util.Date(event.startAt))
                                    Text(
                                        "$formattedDate · $formattedTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!event.description.isNullOrBlank()) {
                                        Text(
                                            event.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 2: Upcoming Holidays (Live Dynamic Feed)
            if (activeTab == CalendarTab.UPCOMING_HOLIDAYS) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Celebration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    "Dynamic Live Philippine Holidays Feed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "Synchronized in real-time from the Nager.Date v3 Public Holiday API with automated offline caching.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                if (upcomingHolidays.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No upcoming holidays found",
                            message = "Holidays will refresh automatically as new proclamations are published.",
                            illustration = {
                                SchedMateMascot(
                                    mood = MascotMood.Learning,
                                    size = 110.dp,
                                    interactive = true,
                                )
                            },
                        )
                    }
                } else {
                    items(upcomingHolidays) { holiday ->
                        val now = System.currentTimeMillis()
                        val diffDays = ((holiday.dateMillis - now) / (24L * 60 * 60 * 1000)).toInt()

                        StudentAiCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        holiday.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (!holiday.localName.isNullOrBlank()) {
                                        Text(
                                            holiday.localName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        dateFormat.format(java.util.Date(holiday.dateMillis)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Surface(
                                        color = when {
                                            diffDays <= 0 -> MaterialTheme.colorScheme.errorContainer
                                            diffDays <= 3 -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            when {
                                                diffDays < 0 -> "Passed"
                                                diffDays == 0 -> "Today!"
                                                diffDays == 1 -> "Tomorrow"
                                                else -> "In $diffDays days"
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                diffDays <= 0 -> MaterialTheme.colorScheme.onErrorContainer
                                                diffDays <= 3 -> MaterialTheme.colorScheme.onPrimaryContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }

                                    Text(
                                        holiday.type.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab 3: Long Weekends (Dynamic Student Planning)
            if (activeTab == CalendarTab.LONG_WEEKENDS) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    "Long Weekend & Study Break Planner",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "Dynamically calculated 3-day and 4-day long weekends. Perfect for study sprints, revision blocks, and recharge breaks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                if (longWeekends.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No long weekends found for this year",
                            message = "Connect online to sync long weekends for ${visibleMonth.year}.",
                            illustration = {
                                SchedMateMascot(
                                    mood = MascotMood.Motivated,
                                    size = 110.dp,
                                    interactive = true,
                                )
                            },
                        )
                    }
                } else {
                    items(longWeekends) { weekend ->
                        val startFormatted = dateFormat.format(java.util.Date(weekend.startDateMillis))
                        val endFormatted = dateFormat.format(java.util.Date(weekend.endDateMillis))

                        StudentAiCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(
                                            "$startFormatted – $endFormatted",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Year ${visibleMonth.year}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text(
                                            "${weekend.dayCount} Days",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }

                                if (weekend.needBridgeDay && weekend.bridgeDays.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                "Bridge day needed: ${weekend.bridgeDays.joinToString(", ")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingAppearanceStep(
    state: com.edukasyon.studentai.ui.viewmodel.OnboardingUiState,
    viewModel: com.edukasyon.studentai.ui.viewmodel.OnboardingViewModel
) {
    Text("Appearance", style = MaterialTheme.typography.headlineMedium)
    Text("Choose a theme — you can change this anytime in Profile.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
            val schoolInvalid = draft.school.isNotBlank() && (
                draft.school.trim().length < 2 || draft.school.trim().none { it.isLetter() }
                )
            OutlinedTextField(
                value = draft.school,
                onValueChange = onSchoolChange,
                label = { Text("School") },
                modifier = Modifier.fillMaxWidth(),
                shape = StudentAiShapes.chip,
                enabled = state.canEditProfile && !state.isSavingProfile,
                singleLine = true,
                isError = schoolInvalid,
                supportingText = if (schoolInvalid) {
                    { Text("School name must be at least 2 characters and contain a letter.") }
                } else null,
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
