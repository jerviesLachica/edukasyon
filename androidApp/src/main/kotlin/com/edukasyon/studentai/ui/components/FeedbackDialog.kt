package com.edukasyon.studentai.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.edukasyon.studentai.BuildConfig
import kotlinx.coroutines.delay

enum class FeedbackCategory(val id: String, val label: String, val icon: ImageVector) {
    BUG("bug", "Bug Report", Icons.Outlined.BugReport),
    FEATURE("feature", "Idea / Feature", Icons.Outlined.Lightbulb),
    GENERAL("general", "Feedback", Icons.Outlined.RateReview),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(
    initialCooldownSeconds: Long = 0L,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (category: String, title: String, description: String, contact: String?, honeypot: String) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.FEATURE) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var honeypotField by remember { mutableStateOf("") } // Anti-bot guardrail

    var cooldownRemaining by remember { mutableStateOf(initialCooldownSeconds) }

    // Cooldown countdown timer
    LaunchedEffect(cooldownRemaining) {
        if (cooldownRemaining > 0) {
            delay(1000L)
            cooldownRemaining -= 1
        }
    }

    val isTitleValid = title.trim().length >= 3
    val isDescValid = description.trim().length >= 10
    val canSubmit = isTitleValid && isDescValid && !isSubmitting && cooldownRemaining <= 0

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Feedback & Reports",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Suggest a feature or report an issue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Category chips
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else null,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = cat.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 140) title = it },
                    label = { Text("Title") },
                    placeholder = {
                        Text(
                            when (selectedCategory) {
                                FeedbackCategory.BUG -> "e.g., Alarm notification keeps repeating"
                                FeedbackCategory.FEATURE -> "e.g., Add Google Calendar 2-way sync"
                                FeedbackCategory.GENERAL -> "e.g., Loving the study timer!"
                            }
                        )
                    },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (title.isNotBlank() && !isTitleValid) "Min 3 characters"
                                else "",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text("${title.length} / 140")
                        }
                    },
                    isError = title.isNotBlank() && !isTitleValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 3000) description = it },
                    label = { Text("Details & Description") },
                    placeholder = {
                        Text(
                            when (selectedCategory) {
                                FeedbackCategory.BUG -> "What went wrong? Steps to reproduce the problem..."
                                FeedbackCategory.FEATURE -> "Describe your idea and how it will help students..."
                                FeedbackCategory.GENERAL -> "Tell us how we can make SchedMate better..."
                            }
                        )
                    },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (description.isNotBlank() && !isDescValid) "Min 10 characters"
                                else "",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text("${description.length} / 3000")
                        }
                    },
                    isError = description.isNotBlank() && !isDescValid,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )

                // Contact input (optional)
                OutlinedTextField(
                    value = contact,
                    onValueChange = { if (it.length <= 100) contact = it },
                    label = { Text("Contact Email (Optional)") },
                    placeholder = { Text("For follow-ups regarding your report") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )

                // Anti-Bot Honeypot Guardrail (hidden from human users, traps spam bots)
                Box(
                    modifier = Modifier
                        .size(0.dp)
                        .alpha(0f)
                ) {
                    TextField(
                        value = honeypotField,
                        onValueChange = { honeypotField = it },
                        label = { Text("BotTrap") },
                    )
                }

                // Error message banner if any
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Diagnostic Telemetry Summary Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Device Diagnostics Attached",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · App v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSubmit(
                                selectedCategory.id,
                                title.trim(),
                                description.trim(),
                                contact.trim().ifBlank { null },
                                honeypotField
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Submitting...")
                        } else if (cooldownRemaining > 0) {
                            Text("Wait ${cooldownRemaining}s")
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Send Report")
                        }
                    }
                }
            }
        }
    }
}
