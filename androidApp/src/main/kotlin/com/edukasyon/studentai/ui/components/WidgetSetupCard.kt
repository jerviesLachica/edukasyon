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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.R
import com.edukasyon.studentai.ui.theme.StudentAiGradients
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import com.edukasyon.studentai.widget.WidgetPinHelper
import com.edukasyon.studentai.widget.WidgetPinResult
import com.edukasyon.studentai.widget.WidgetSize

enum class WidgetSetupCardVariant {
    Profile,
    Home,
}

@Composable
fun WidgetSetupCard(
    modifier: Modifier = Modifier,
    variant: WidgetSetupCardVariant = WidgetSetupCardVariant.Profile,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showSizePicker by remember { mutableStateOf(false) }
    var showManualSteps by remember { mutableStateOf(false) }

    if (showSizePicker) {
        WidgetSizePickerDialog(
            onDismiss = { showSizePicker = false },
            onSizeSelected = { size ->
                showSizePicker = false
                when (WidgetPinHelper.requestPinWidget(context, size)) {
                    WidgetPinResult.PIN_DIALOG_REQUESTED -> Unit
                    WidgetPinResult.MANUAL_INSTRUCTIONS_NEEDED -> showManualSteps = true
                }
            },
        )
    }

    if (showManualSteps) {
        WidgetManualStepsDialog(
            onDismiss = { showManualSteps = false },
            onOpenSettings = {
                WidgetPinHelper.openHomeScreenSettings(context)
                showManualSteps = false
            },
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = StudentAiShapes.dashboard,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudentAiGradients.accentChipBrush(2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Widgets,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.widget_add_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.widget_add_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.widget_dismiss),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (variant == WidgetSetupCardVariant.Home) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WidgetSizeChip(label = "2×2", subtitle = "Tasks or schedule", modifier = Modifier.weight(1f))
                    WidgetSizeChip(label = "2×3", subtitle = "Combined view", modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSizePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = StudentAiShapes.button,
            ) {
                Text(stringResource(R.string.widget_add_button))
            }
            TextButton(
                onClick = { WidgetPinHelper.openCustomize(context) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.widget_customize_button))
            }
            Text(
                text = stringResource(R.string.widget_manual_hint_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WidgetSizeChip(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = StudentAiShapes.chip,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WidgetSizePickerDialog(
    onDismiss: () -> Unit,
    onSizeSelected: (WidgetSize) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_pick_size_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.widget_pick_size_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onSizeSelected(WidgetSize.SMALL_2X2) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = StudentAiShapes.button,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.widget_2x2_label))
                        Text(
                            stringResource(R.string.widget_2x2_description),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { onSizeSelected(WidgetSize.TALL_2X3) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = StudentAiShapes.button,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.widget_2x3_label))
                        Text(
                            stringResource(R.string.widget_2x3_description),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun WidgetManualStepsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.widget_manual_intro))
                val steps = listOf(
                    stringResource(R.string.widget_manual_step_1),
                    stringResource(R.string.widget_manual_step_2),
                    stringResource(R.string.widget_manual_step_3),
                    stringResource(R.string.widget_manual_step_4),
                )
                steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.widget_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.widget_got_it))
            }
        },
    )
}
