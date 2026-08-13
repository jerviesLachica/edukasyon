package com.edukasyon.studentai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.config.BackendConfig
import com.edukasyon.studentai.network.AiApiClient
import com.edukasyon.studentai.network.HealthResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MainTab(val label: String) {
    Home("Home"),
    Schedule("Schedule"),
    Planner("Planner"),
    Jevi("JEVI"),
    Profile("Profile"),
}

@Composable
fun StudentAiApp() {
    MaterialTheme {
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = MainTab.entries

        Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        MainTab.Home -> Icons.Default.Home
                                        MainTab.Schedule -> Icons.Default.CalendarMonth
                                        MainTab.Planner -> Icons.Default.Checklist
                                        MainTab.Jevi -> Icons.Default.Style
                                        MainTab.Profile -> Icons.Default.Person
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (tabs[selectedTab]) {
                    MainTab.Home -> HomeTabContent()
                    else -> PlaceholderTabContent(
                        title = tabs[selectedTab].label,
                        subtitle = "Coming soon on iOS — Phase 2 migration",
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTabContent() {
    var loading by remember { mutableStateOf(true) }
    var health by remember { mutableStateOf<HealthResponseDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val apiClient = remember { AiApiClient() }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            health = withContext(Dispatchers.Default) { apiClient.health() }
        } catch (e: Exception) {
            error = e.message ?: "Unable to reach backend"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "StudentAI",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "iOS · ${getPlatform().name}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Backend connection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = BackendConfig.AI_BACKEND_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )

                when {
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(
                            text = "Connecting to backend…",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }

                    error != null -> {
                        Text(
                            text = "Offline or unreachable",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(text = error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }

                    health != null -> {
                        val response = health!!
                        Text(text = "Status: ${response.status}")
                        Text(text = "AI configured: ${response.aiConfigured}")
                        Text(text = "Model: ${response.model}")
                        if (response.availableModels.isNotEmpty()) {
                            Text(
                                text = "Available: ${response.availableModels.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "Phase 1 foundation — shared Kotlin code with Android. Feature screens migrate in Phase 2.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaceholderTabContent(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
