package com.edukasyon.studentai.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.edukasyon.studentai.ui.components.StarfieldScaffold
import com.edukasyon.studentai.ui.theme.StudentAiShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.theme.StudentAiTheme
import com.edukasyon.studentai.domain.model.ThemeMode
import com.edukasyon.studentai.ui.theme.parseHexColor
import kotlinx.coroutines.launch

data class WidgetConfigureResult(
    val displayType: WidgetDisplayType,
    val accentHex: String?,
    val designPreset: WidgetDesignPreset,
    val designColor1: String?,
    val designColor2: String?,
    val designColor3: String?
)

open class WidgetConfigureActivity(
    private val widgetSize: WidgetSize
) : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val defaultType = when (widgetSize) {
            WidgetSize.SMALL_2X2 -> WidgetDisplayType.TASKS
            WidgetSize.TALL_2X3 -> WidgetDisplayType.COMBINED
        }
        val (initialC1, initialC2, initialC3) = WidgetPreferences.getDesignColorOverrides(this, appWidgetId)

        setContent {
            val scope = rememberCoroutineScope()
            StudentAiTheme(
                themeMode = ThemeMode.LIGHT,
                primaryColorHex = "#F97316",
                secondaryColorHex = "#F8B195",
            ) {
                WidgetConfigureScreen(
                    widgetSize = widgetSize,
                    initialType = WidgetPreferences.getDisplayType(this, appWidgetId, defaultType),
                    initialAccent = WidgetPreferences.getAccentColorHex(this, appWidgetId),
                    initialDesign = WidgetPreferences.getDesignPreset(this, appWidgetId),
                    initialDesignColor1 = initialC1,
                    initialDesignColor2 = initialC2,
                    initialDesignColor3 = initialC3,
                    onSave = { result ->
                        WidgetPreferences.saveConfiguration(
                            context = this,
                            appWidgetId = appWidgetId,
                            widgetSize = widgetSize,
                            displayType = result.displayType,
                            accentHex = result.accentHex,
                            designPreset = result.designPreset,
                            designColor1 = result.designColor1,
                            designColor2 = result.designColor2,
                            designColor3 = result.designColor3
                        )
                        WidgetBackgroundGenerator.invalidateCache()
                        WidgetSnapshotCache.invalidate(this, appWidgetId)
                        // Pre-seed snapshot cache synchronously so Glance's cache-first
                        // provideGlance hits instantly on first composition.
                        kotlinx.coroutines.runBlocking {
                            WidgetDataProvider.loadSnapshotFresh(
                                this@WidgetConfigureActivity,
                                appWidgetId,
                                widgetSize
                            )
                        }
                        scope.launch {
                            WidgetUpdater.updateAppWidget(this@WidgetConfigureActivity, appWidgetId)
                            val saveResult = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(RESULT_OK, saveResult)
                            finish()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

class WidgetConfigureActivity2x2 : WidgetConfigureActivity(WidgetSize.SMALL_2X2)

class WidgetConfigureActivity2x3 : WidgetConfigureActivity(WidgetSize.TALL_2X3)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigureScreen(
    widgetSize: WidgetSize,
    initialType: WidgetDisplayType,
    initialAccent: String?,
    initialDesign: WidgetDesignPreset,
    initialDesignColor1: String?,
    initialDesignColor2: String?,
    initialDesignColor3: String?,
    onSave: (WidgetConfigureResult) -> Unit,
    onCancel: () -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var selectedAccent by remember { mutableStateOf(initialAccent) }
    var selectedDesign by remember { mutableStateOf(initialDesign) }
    var designColor1 by remember { mutableStateOf(initialDesignColor1) }
    var designColor2 by remember { mutableStateOf(initialDesignColor2) }
    var designColor3 by remember { mutableStateOf(initialDesignColor3) }

    val typeOptions = when (widgetSize) {
        WidgetSize.SMALL_2X2 -> listOf(WidgetDisplayType.TASKS, WidgetDisplayType.SCHEDULE)
        WidgetSize.TALL_2X3 -> listOf(WidgetDisplayType.COMBINED, WidgetDisplayType.SCHEDULE, WidgetDisplayType.TASKS)
    }

    val resolvedDesignColors = remember(selectedDesign, designColor1, designColor2, designColor3) {
        selectedDesign.defaultColors().resolved(designColor1, designColor2, designColor3)
    }

    StarfieldScaffold {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Configure Widget") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ConfigureSectionCard {
                    Text(
                        text = when (widgetSize) {
                            WidgetSize.SMALL_2X2 -> "2×2 widget — pick content, design, and accent"
                            WidgetSize.TALL_2X3 -> "2×3 widget — combined view, design, and accent"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ConfigureSectionCard(title = "Design") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WidgetDesignPreset.entries.forEach { design ->
                            DesignPresetCard(
                                design = design,
                                colors = if (selectedDesign == design) {
                                    resolvedDesignColors
                                } else {
                                    design.defaultColors()
                                },
                                selected = selectedDesign == design,
                                onClick = {
                                    selectedDesign = design
                                    if (design == WidgetDesignPreset.MINIMAL) {
                                        designColor1 = null
                                        designColor2 = null
                                        designColor3 = null
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedDesign != WidgetDesignPreset.MINIMAL) {
                    ConfigureSectionCard(title = "Design colors") {
                        DesignColorOverrides(
                            design = selectedDesign,
                            color1 = designColor1,
                            color2 = designColor2,
                            color3 = designColor3,
                            onColor1Selected = { designColor1 = it },
                            onColor2Selected = { designColor2 = it },
                            onColor3Selected = { designColor3 = it },
                            onReset = {
                                designColor1 = null
                                designColor2 = null
                                designColor3 = null
                            }
                        )
                    }
                }

                ConfigureSectionCard(title = "Content") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        typeOptions.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = {
                                    Text(
                                        when (type) {
                                            WidgetDisplayType.TASKS -> "Tasks"
                                            WidgetDisplayType.SCHEDULE -> "Schedule"
                                            WidgetDisplayType.COMBINED -> "Combined"
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }

                ConfigureSectionCard(title = "Accent color") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WidgetAccentPresets.presets.forEach { (hex, label) ->
                            val color = parseHexColor(hex) ?: Color.Gray
                            val selected = selectedAccent == hex
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAccent = hex },
                                    contentAlignment = Alignment.Center
                                ) {}
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Minimal uses a light card layout. Pattern designs use light text on dark backgrounds for readability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        onSave(
                            WidgetConfigureResult(
                                displayType = selectedType,
                                accentHex = selectedAccent,
                                designPreset = selectedDesign,
                                designColor1 = designColor1,
                                designColor2 = designColor2,
                                designColor3 = designColor3
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = StudentAiShapes.button,
                ) {
                    Text("Save widget")
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = StudentAiShapes.button,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ConfigureSectionCard(
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = StudentAiShapes.dashboard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
private fun DesignPresetCard(
    design: WidgetDesignPreset,
    colors: WidgetDesignColors,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val previewBitmap = remember(design, colors.cacheKey()) {
        WidgetBackgroundGenerator.getBitmap(
            context = context,
            preset = design,
            colors = colors,
            widthDp = 96,
            heightDp = 96
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        DesignPreviewImage(previewBitmap)
        Spacer(Modifier.height(6.dp))
        Text(design.displayName, style = MaterialTheme.typography.labelMedium)
        Text(
            design.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun DesignPreviewImage(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(10.dp))
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignColorOverrides(
    design: WidgetDesignPreset,
    color1: String?,
    color2: String?,
    color3: String?,
    onColor1Selected: (String?) -> Unit,
    onColor2Selected: (String?) -> Unit,
    onColor3Selected: (String?) -> Unit,
    onReset: () -> Unit
) {
    val defaults = design.defaultColors()
    val resolved = defaults.resolved(color1, color2, color3)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Tap a swatch to customize. Defaults are restored when you pick Minimal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DesignColorRow(
            label = colorLabelForDesign(design, 1),
            selectedHex = resolved.color1,
            presets = listOf(defaults.color1, "#FFFFFF", "#000000", "#F8B195", "#355C7D", "#313131"),
            onSelected = onColor1Selected
        )
        DesignColorRow(
            label = colorLabelForDesign(design, 2),
            selectedHex = resolved.color2,
            presets = listOfNotNull(
                defaults.color2,
                "#FFFFFF",
                "#808080",
                "#4E4F51",
                "#355C7D",
                "#F3F4F6"
            ),
            onSelected = onColor2Selected
        )
        if (design == WidgetDesignPreset.HEX_DARK) {
            DesignColorRow(
                label = colorLabelForDesign(design, 3),
                selectedHex = resolved.color3 ?: defaults.color3.orEmpty(),
                presets = listOfNotNull(defaults.color3, "#3C3C3C", "#1D1D1D", "#4E4F51"),
                onSelected = onColor3Selected
            )
        }

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset design colors")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignColorRow(
    label: String,
    selectedHex: String,
    presets: List<String>,
    onSelected: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.distinct().forEach { hex ->
                val color = parseHexColor(hex) ?: return@forEach
                val selected = selectedHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelected(hex) }
                )
            }
        }
    }
}

private fun colorLabelForDesign(design: WidgetDesignPreset, index: Int): String = when (design) {
    WidgetDesignPreset.CORAL_CHEVRON -> when (index) {
        1 -> "Primary (coral)"
        else -> "Secondary (navy)"
    }
    WidgetDesignPreset.HEX_DARK -> when (index) {
        1 -> "Hex tone 1"
        2 -> "Hex tone 2"
        else -> "Hex tone 3"
    }
    WidgetDesignPreset.DOT_GRID -> when (index) {
        1 -> "Background"
        else -> "Dot color"
    }
    WidgetDesignPreset.LINE_GRID -> when (index) {
        1 -> "Background"
        else -> "Grid line color"
    }
    WidgetDesignPreset.MINIMAL -> "Background"
}
