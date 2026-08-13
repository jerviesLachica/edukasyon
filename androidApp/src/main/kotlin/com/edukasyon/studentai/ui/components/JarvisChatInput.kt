package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import com.edukasyon.studentai.domain.model.ChatAttachmentPayload

/**
 * Capability tags for the Jarvis model selector.
 *
 * Vision on **Auto**: backend `AiProvider.js` lists `auto` in `VISION_CAPABLE_MODELS` and
 * `resolveChatModel()` keeps user-selected `auto` for image attachments (no forced upgrade to step).
 */
private object JarvisModelCapabilities {
    fun tagsFor(model: AiModel): List<String> = when (model) {
        AiModel.AUTO -> listOf("Fast", "Chat", "Files", "Vision")
        AiModel.REASONING -> listOf("Reasoning", "Vision", "Stronger", "5/10 min")
    }
}

@Composable
fun JarvisChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    selectedModel: AiModel,
    onModelSelected: (AiModel) -> Unit,
    stepQuotaRemaining: Int,
    stepQuotaLabel: String,
    stepQuotaExhausted: Boolean,
    pendingAttachment: ChatAttachmentPayload?,
    onRemoveAttachment: () -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    enabled: Boolean,
    isOnline: Boolean,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JarvisModelSelectorRow(
            selectedModel = selectedModel,
            onModelSelected = onModelSelected,
            stepQuotaLabel = stepQuotaLabel,
            stepQuotaExhausted = stepQuotaExhausted,
            enabled = enabled,
        )

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
                        BouncyIconButton(
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
                        "Jarvis can see this image when you send."
                    } else {
                        "Image attached — connect online for Jarvis to analyze it."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.35f)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    BouncyIconButton(
                        onClick = { showAttachMenu = true },
                        enabled = enabled,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Attach file or image",
                            tint = scheme.onSurface,
                            modifier = Modifier.size(22.dp),
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
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (input.isEmpty()) {
                                Text(
                                    "Ask anything",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                val canSend = enabled && (input.isNotBlank() || pendingAttachment != null)
                BouncyIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) scheme.primary
                            else scheme.surfaceVariant.copy(alpha = 0.65f),
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
private fun JarvisModelSelectorRow(
    selectedModel: AiModel,
    onModelSelected: (AiModel) -> Unit,
    stepQuotaLabel: String,
    stepQuotaExhausted: Boolean,
    enabled: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AiModel.entries.forEach { model ->
            val isStep = model.isStepModel
            val chipEnabled = enabled && (!isStep || !stepQuotaExhausted || selectedModel == model)
            JarvisModelChip(
                model = model,
                selected = selectedModel == model,
                enabled = chipEnabled,
                subtitle = if (isStep) stepQuotaLabel else model.chatDescription,
                onClick = { if (chipEnabled) onModelSelected(model) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JarvisModelChip(
    model: AiModel,
    selected: Boolean,
    enabled: Boolean,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> scheme.surfaceVariant.copy(alpha = 0.45f)
        selected -> scheme.primaryContainer
        else -> scheme.surfaceContainerHigh
    }
    val borderColor = when {
        selected -> scheme.primary.copy(alpha = 0.65f)
        else -> scheme.outline.copy(alpha = 0.35f)
    }
    val titleColor = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.45f)
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val subtitleColor = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
        selected -> scheme.onPrimaryContainer.copy(alpha = 0.8f)
        else -> scheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .widthIn(min = 148.dp, max = 220.dp)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick)
                else Modifier,
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (model.isStepModel) {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = titleColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor,
                maxLines = 1,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                JarvisModelCapabilities.tagsFor(model).forEach { tag ->
                    JarvisCapabilityTagPill(
                        label = tag,
                        selected = selected,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun JarvisCapabilityTagPill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        !enabled -> scheme.surfaceVariant.copy(alpha = 0.35f)
        selected -> scheme.primary.copy(alpha = 0.14f)
        else -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val textColor = when {
        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = background,
        border = BorderStroke(0.5.dp, scheme.outline.copy(alpha = 0.3f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
        )
    }
}

fun stepQuotaLabelFromStatus(status: StepModelQuotaTracker.Status): String =
    StepModelQuotaTracker.formatRemainingLabel(status)
