package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.ui.adaptive.AdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberAdaptiveWidth
import com.edukasyon.studentai.ui.adaptive.rememberContentMaxWidth
import com.edukasyon.studentai.ui.components.GradientHeader
import com.edukasyon.studentai.ui.components.ModernCard
import com.edukasyon.studentai.ui.features.*
import com.edukasyon.studentai.ui.navigation.MainTab
import com.edukasyon.studentai.widget.WidgetPinHelper
import com.edukasyon.studentai.widget.WidgetPinResult
import com.edukasyon.studentai.widget.WidgetSize

private enum class FilterChipOption(val label: String) {
    ALL("All"),
    SCHEDULE("Schedule"),
    AI("AI"),
    STUDY("Study"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesGuideScreen(
    onBack: () -> Unit,
    onNavigateToTab: (MainTab) -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val context = LocalContext.current
    val adaptiveWidth = rememberAdaptiveWidth()
    val contentMaxWidth = rememberContentMaxWidth()
    val horizontalPadding = if (adaptiveWidth == AdaptiveWidth.Compact) 16.dp else 32.dp

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FilterChipOption.ALL) }
    var showWidgetInstructions by remember { mutableStateOf(false) }

    val filteredFeatures = remember(searchQuery, selectedFilter) {
        FeaturesCatalog.all.filter { feature ->
            val matchesSearch = searchQuery.isBlank() ||
                feature.title.contains(searchQuery, ignoreCase = true) ||
                feature.description.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                FilterChipOption.ALL -> true
                FilterChipOption.SCHEDULE -> feature.filterCategory == FeatureFilterCategory.SCHEDULE
                FilterChipOption.AI -> feature.filterCategory == FeatureFilterCategory.AI
                FilterChipOption.STUDY -> feature.filterCategory == FeatureFilterCategory.STUDY
                FilterChipOption.SETTINGS -> feature.filterCategory == FeatureFilterCategory.SETTINGS
            }
            matchesSearch && matchesFilter
        }
    }

    val groupedFeatures = remember(filteredFeatures) {
        FeaturesCatalog.sections.mapNotNull { section ->
            val items = filteredFeatures.filter { it.section == section }
            if (items.isEmpty()) null else section to items
        }
    }

    fun onFeatureClick(feature: FeatureItem) {
        when (val dest = feature.destination) {
            is FeatureDestination.Tab -> onNavigateToTab(dest.tab)
            is FeatureDestination.Route -> onNavigateToRoute(dest.route)
            FeatureDestination.WidgetInstructions -> showWidgetInstructions = true
        }
    }

    if (showWidgetInstructions) {
        WidgetInstructionsDialog(
            onDismiss = { showWidgetInstructions = false },
            onRequestPin = {
                when (WidgetPinHelper.requestPinWidget(context, WidgetSize.SMALL_2X2)) {
                    WidgetPinResult.PIN_DIALOG_REQUESTED -> Unit
                    WidgetPinResult.MANUAL_INSTRUCTIONS_NEEDED -> WidgetPinHelper.showManualInstructionsToast(context)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Features Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    GradientHeader(
                        title = "Explore Features",
                        subtitle = "${FeaturesCatalog.all.size} tools to help you study smarter"
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                        placeholder = { Text("Search features…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(FilterChipOption.entries) { option ->
                            FilterChip(
                                selected = selectedFilter == option,
                                onClick = { selectedFilter = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }

                if (groupedFeatures.isEmpty()) {
                    item {
                        Text(
                            text = "No features match your search.",
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    groupedFeatures.forEach { (section, features) ->
                        item(key = "header_${section.name}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    horizontal = horizontalPadding,
                                    vertical = 8.dp
                                )
                            )
                        }
                        items(features, key = { it.id }) { feature ->
                            FeatureGuideCard(
                                feature = feature,
                                modifier = Modifier.padding(horizontal = horizontalPadding),
                                onClick = { onFeatureClick(feature) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureGuideCard(
    feature: FeatureItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ModernCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    feature.newInVersion?.let { version ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "New in $version",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WidgetInstructionsDialog(
    onDismiss: () -> Unit,
    onRequestPin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Home Screen Widget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Follow these steps to add a SchedMate widget:")
                val steps = listOf(
                    "Long-press an empty area on your home screen",
                    "Tap \"Widgets\" from the menu",
                    "Find \"SchedMate\" in the widget list",
                    "Drag the 2×2 or 2×3 widget to your home screen",
                    "Choose tasks, schedule, or combined view when prompted"
                )
                steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRequestPin()
                onDismiss()
            }) {
                Text("Pin Widget")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}
