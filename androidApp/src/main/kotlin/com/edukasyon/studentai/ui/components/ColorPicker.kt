package com.edukasyon.studentai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.theme.ThemePresets
import com.edukasyon.studentai.ui.theme.buildColorScheme
import com.edukasyon.studentai.ui.theme.isValidHexColor
import com.edukasyon.studentai.ui.theme.parseHexColor

@Composable
fun ThemeColorPicker(
    primaryColorHex: String,
    secondaryColorHex: String?,
    themeMode: ThemeMode,
    onPrimaryColorSelected: (String) -> Unit,
    onSecondaryColorSelected: (String?) -> Unit,
    onResetColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customPrimaryHex by remember(primaryColorHex) { mutableStateOf(primaryColorHex) }
    var customSecondaryHex by remember(secondaryColorHex) { mutableStateOf(secondaryColorHex.orEmpty()) }
    var useCustomSecondary by remember(secondaryColorHex) { mutableStateOf(secondaryColorHex != null) }

    val darkPreview = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Accent color",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PresetColorGrid(
            presets = ThemePresets.presets.map { it.primaryHex },
            selectedHex = primaryColorHex,
            onColorSelected = {
                customPrimaryHex = it
                onPrimaryColorSelected(it)
            }
        )

        OutlinedTextField(
            value = customPrimaryHex,
            onValueChange = {
                customPrimaryHex = it
                if (isValidHexColor(it)) onPrimaryColorSelected(it)
            },
            label = { Text("Custom primary hex") },
            placeholder = { Text("#3949AB") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                parseHexColor(customPrimaryHex)?.let { preview ->
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Secondary color",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = useCustomSecondary,
                onCheckedChange = {
                    useCustomSecondary = it
                    if (!it) {
                        customSecondaryHex = ""
                        onSecondaryColorSelected(null)
                    }
                }
            )
        }

        if (useCustomSecondary) {
            PresetColorGrid(
                presets = ThemePresets.presets.map { it.secondaryHex ?: it.primaryHex },
                selectedHex = secondaryColorHex,
                onColorSelected = {
                    customSecondaryHex = it
                    onSecondaryColorSelected(it)
                }
            )

            OutlinedTextField(
                value = customSecondaryHex,
                onValueChange = {
                    customSecondaryHex = it
                    if (isValidHexColor(it)) onSecondaryColorSelected(it)
                },
                label = { Text("Custom secondary hex") },
                placeholder = { Text("#00897B") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ThemeColorPreview(
            primaryColorHex = primaryColorHex,
            secondaryColorHex = if (useCustomSecondary) secondaryColorHex else null,
            darkTheme = darkPreview
        )

        OutlinedButton(
            onClick = {
                customPrimaryHex = ThemePresets.DEFAULT_PRIMARY
                customSecondaryHex = ""
                useCustomSecondary = false
                onResetColors()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to default")
        }
    }
}

@Composable
private fun PresetColorGrid(
    presets: List<String>,
    selectedHex: String?,
    onColorSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { hex ->
                    val color = parseHexColor(hex) ?: Color.Gray
                    val selected = selectedHex?.equals(hex, ignoreCase = true) == true
                    ColorSwatch(
                        color = color,
                        selected = selected,
                        contentDescription = hex,
                        onClick = { onColorSelected(hex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThemeColorPreview(
    primaryColorHex: String,
    secondaryColorHex: String?,
    darkTheme: Boolean
) {
    val primary = parseHexColor(primaryColorHex) ?: parseHexColor(ThemePresets.DEFAULT_PRIMARY)!!
    val secondary = secondaryColorHex?.let { parseHexColor(it) }
    val previewScheme = remember(primary, secondary, darkTheme) {
        buildColorScheme(primary, secondary, darkTheme)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = previewScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = previewScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewScheme.primary,
                        contentColor = previewScheme.onPrimary,
                        disabledContainerColor = previewScheme.primary,
                        disabledContentColor = previewScheme.onPrimary
                    )
                ) {
                    Text("Button")
                }
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = previewScheme.primary,
                        disabledContentColor = previewScheme.primary
                    )
                ) {
                    Text("Outlined")
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = previewScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Sample card", style = MaterialTheme.typography.titleSmall, color = previewScheme.onSurface)
                    Text(
                        "Headers and navigation use your accent color.",
                        style = MaterialTheme.typography.bodySmall,
                        color = previewScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
