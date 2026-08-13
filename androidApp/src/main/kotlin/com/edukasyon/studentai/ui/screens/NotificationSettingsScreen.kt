package com.edukasyon.studentai.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.components.ModernCard
import com.edukasyon.studentai.ui.components.SettingsGroup
import com.edukasyon.studentai.ui.components.SettingsRow
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.ui.viewmodel.NotificationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adaptiveWidth = rememberAdaptiveWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemState()
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    LaunchedEffect(Unit) { viewModel.refreshSystemState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "NOTIFICATIONS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = StudentAiShapes.chip,
                        color = if (state.notificationsOn) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (state.notificationsOn) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                if (state.notificationsOn) "Notifications On" else "Notifications Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                ModernCard(onClick = onOpenDetail) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = StudentAiShapes.chip,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Notification Settings", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Class alerts, sound & timing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                PermissionInfoCard(
                    title = "Reminders muted by Do Not Disturb?",
                    body = "Grant Do Not Disturb access so class, task, and repeating reminders aren't silenced.",
                    buttonLabel = "Allow StudentAI to bypass Do Not Disturb",
                    granted = state.dndAccessGranted,
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                    }
                )
            }

            item {
                PermissionInfoCard(
                    title = "Alarm timing not matching?",
                    subtitle = "Reminders arriving later than expected? Battery optimization can delay them.",
                    body = "This is essential for accurate class alarms and for the home-screen widget to update on its own (e.g. rolling over to the next day at midnight) — without it, Android can delay or skip these in the background.",
                    buttonLabel = "Disable battery optimization for StudentAI",
                    granted = state.batteryOptimizationDisabled,
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= 33 && !state.notificationPermissionGranted) {
                item {
                    PermissionInfoCard(
                        title = "Notification permission required",
                        body = "Allow notifications so class and task reminders can reach you on time.",
                        buttonLabel = "Allow notifications",
                        granted = false,
                        onAction = {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsDetailScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroup(title = "Master") {
                    SettingsRow(
                        title = "Enable notifications",
                        subtitle = "Master toggle for all reminders",
                        trailing = {
                            Switch(
                                checked = state.notificationsEnabled,
                                onCheckedChange = viewModel::setNotificationsEnabled
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Class alerts") {
                    SettingsRow(
                        title = "Class reminders",
                        trailing = {
                            Switch(
                                checked = state.classReminders,
                                onCheckedChange = viewModel::setClassReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "At class time",
                        subtitle = "Notify when class starts",
                        trailing = {
                            Switch(
                                checked = state.classReminderAtTime,
                                onCheckedChange = viewModel::setClassReminderAtTime,
                                enabled = state.classReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "15 minutes before",
                        subtitle = "Early heads-up before class",
                        trailing = {
                            Switch(
                                checked = state.classReminder15MinBefore,
                                onCheckedChange = viewModel::setClassReminder15MinBefore,
                                enabled = state.classReminders
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Other reminders") {
                    SettingsRow(
                        title = "Task reminders",
                        trailing = {
                            Switch(
                                checked = state.taskReminders,
                                onCheckedChange = viewModel::setTaskReminders
                            )
                        }
                    )
                    SettingsRow(
                        title = "Exam reminders",
                        trailing = {
                            Switch(
                                checked = state.examReminders,
                                onCheckedChange = viewModel::setExamReminders
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup(title = "Sound") {
                    SettingsRow(
                        title = "Notification sound",
                        subtitle = "Play sound with reminders",
                        trailing = {
                            Switch(
                                checked = state.notificationSoundEnabled,
                                onCheckedChange = viewModel::setNotificationSoundEnabled
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionInfoCard(
    title: String,
    body: String,
    buttonLabel: String,
    granted: Boolean,
    onAction: () -> Unit,
    subtitle: String? = null
) {
    ModernCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (granted) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!granted) {
                    Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = StudentAiShapes.button) {
                        Text(buttonLabel)
                    }
                } else {
                    Text(
                        "Already configured",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
