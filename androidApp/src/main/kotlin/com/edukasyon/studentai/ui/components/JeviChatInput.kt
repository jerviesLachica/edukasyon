package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.ai.StepModelQuotaTracker
import com.edukasyon.studentai.domain.model.AiModel
import com.edukasyon.studentai.domain.model.CitedSource
import com.edukasyon.studentai.domain.model.ThinkingLevel
import com.edukasyon.studentai.domain.model.ChatAttachmentPayload

/**
 * Capability tags for the Jevi model selector.
 *
 * Vision on **Auto**: backend `AiProvider.js` lists `auto` in `VISION_CAPABLE_MODELS` and
 * `resolveChatModel()` keeps user-selected `auto` for image attachments (no forced upgrade to step).
 */
private object JeviModelCapabilities {
    fun tagsFor(model: AiModel): List<String> = when (model) {
        AiModel.AUTO -> listOf("Fast", "Chat", "Files", "Vision")
        AiModel.REASONING -> listOf("Reasoning", "Vision", "Stronger", "25/10 min")
    }
}

@Composable
fun JeviChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    selectedModel: AiModel,
    onModelSelected: (AiModel) -> Unit,
    thinkingLevel: ThinkingLevel,
    onThinkingLevelSelected: (ThinkingLevel) -> Unit,
    stepQuotaRemaining: Int,
    stepQuotaLabel: String,
    stepQuotaExhausted: Boolean,
    pendingAttachment: ChatAttachmentPayload?,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    sources: List<CitedSource> = emptyList(),
    selectedSourceIds: Set<String>? = null,
    onSourceToggle: ((String) -> Unit)? = null,
    onAddSource: ((String, String) -> Unit)? = null,
    onDeleteSource: ((String) -> Unit)? = null,
    enabled: Boolean,
    isOnline: Boolean,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }

    if (showAddSourceDialog && onAddSource != null) {
        AddSourceDialog(
            onDismiss = { showAddSourceDialog = false },
            onConfirm = { name, text ->
                showAddSourceDialog = false
                onAddSource(name, text)
            },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        pendingAttachment?.let { attachment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InputChip(
                    selected = true,
                    onClick = {},
                    label = { Text(attachment.fileName, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onRemoveAttachment,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                        }
                    },
                )
            }
            if (attachment.isImage) {
                Text(
                    text = if (isOnline) {
                        "Jevi can see this image when you send."
                    } else {
                        "Image attached — connect online for Jevi to analyze it."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Source chips — horizontal scroll inside a compact row above input.
        // Always visible when adding is supported so the first source can be added.
        if (sources.isNotEmpty() || onAddSource != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Add Source chip
                    if (onAddSource != null) {
                        FilterChipSmall(
                            onClick = { showAddSourceDialog = true },
                            label = { Text("Add Source") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = "Add Source", modifier = Modifier.size(14.dp)) },
                            enabled = enabled,
                        )
                    }
                    sources.forEach { source ->
                        val isSelected = selectedSourceIds == null || selectedSourceIds.contains(source.id)
                        FilterChipSmall(
                            selected = isSelected,
                            onClick = { onSourceToggle?.invoke(source.id) },
                            label = { Text(source.name) },
                            leadingIcon = {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(14.dp))
                                } else {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            trailingIcon = {
                                if (onDeleteSource != null) {
                                    IconButton(onClick = { onDeleteSource.invoke(source.id) }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Filled.Close, contentDescription = "Delete source", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                    }
                                }
                            },
                            enabled = enabled,
                        )
                    }
                }
            }
        }

        // Thinking level — compact 3-level selector inside the composer bar
        // (part of the bottom input area, never blocking chat messages)
        JeviThinkingLevelRow(
            selectedLevel = thinkingLevel,
            onLevelSelected = onThinkingLevelSelected,
            enabled = enabled,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = enabled,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Attach file or image",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo or image") },
                            onClick = {
                                showAttachMenu = false
                                onPickImage()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text("Document or file") },
                            onClick = {
                                showAttachMenu = false
                                onPickFile()
                            },
                            leadingIcon = { Icon(Icons.Outlined.InsertDriveFile, contentDescription = null) },
                        )
                    }
                }

                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    singleLine = false,
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (input.isEmpty()) {
                                Text(
                                    "Ask Jevi anything…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                // Model selector dropdown (compact, next to send)
                Box {
                    IconButton(
                        onClick = { showModelDropdown = !showModelDropdown },
                        enabled = enabled,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Select model",
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                    ) {
                        AiModel.entries.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    showModelDropdown = false
                                    onModelSelected(model)
                                },
                            )
                        }
                    }
                }

                val canSend = enabled && (input.isNotBlank() || pendingAttachment != null)
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) scheme.primary
                            else scheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (canSend) scheme.onPrimary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipSmall(
    selected: Boolean = false,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
    }
}

@Composable
private fun JeviThinkingLevelRow(
    selectedLevel: ThinkingLevel,
    onLevelSelected: (ThinkingLevel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThinkingLevel.entries.forEach { level ->
            InputChip(
                selected = selectedLevel == level,
                onClick = { if (enabled) onLevelSelected(level) },
                enabled = enabled,
                label = { Text(level.displayName) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

fun stepQuotaLabelFromStatus(status: StepModelQuotaTracker.Status): String =
    StepModelQuotaTracker.formatRemainingLabel(status)
