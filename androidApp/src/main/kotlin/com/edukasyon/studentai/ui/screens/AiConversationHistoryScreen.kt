package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edukasyon.studentai.domain.model.AiConversation
import com.edukasyon.studentai.ui.components.EmptyState
import com.edukasyon.studentai.ui.components.StudentAiCard
import com.edukasyon.studentai.ui.viewmodel.AiConversationHistoryViewModel
import com.edukasyon.studentai.ui.viewmodel.AiViewModel
import com.edukasyon.studentai.ui.viewmodel.sharedAiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConversationHistoryScreen(
    filterScope: String,
    onBack: () -> Unit,
    onConversationSelected: () -> Unit = onBack,
    historyViewModel: AiConversationHistoryViewModel = hiltViewModel(),
    aiViewModel: AiViewModel = sharedAiViewModel(),
) {
    val conversations by historyViewModel.conversations.collectAsStateWithLifecycle()
    val deckTitles by historyViewModel.deckTitles.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var deckFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filterScope) {
        historyViewModel.setFilter(filterScope)
        deckFilter = null
    }

    val deckIdsInList = remember(conversations) {
        conversations.mapNotNull { it.deckId }.distinct()
    }

    val title = when (filterScope) {
        "tutor" -> "Tutor History"
        "tools" -> "Tools History"
        else -> "AI History"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            EmptyState(
                title = "No saved sessions",
                message = when (filterScope) {
                    "tutor" -> "Your tutor chats will appear here so you can pick up where you left off."
                    else -> "Summaries, flashcards, and quizzes you generate will be saved here."
                },
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (filterScope == "tutor" && (deckIdsInList.isNotEmpty() || deckFilter != null)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = deckFilter == null,
                            onClick = {
                                deckFilter = null
                                historyViewModel.setDeckFilter(null)
                            },
                            label = { Text("All") },
                        )
                        deckIdsInList.forEach { deckId ->
                            FilterChip(
                                selected = deckFilter == deckId,
                                onClick = {
                                    deckFilter = deckId
                                    historyViewModel.setDeckFilter(deckId)
                                },
                                label = { Text(deckTitles[deckId] ?: "Deck") },
                            )
                        }
                    }
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    AiConversationHistoryItem(
                        conversation = conversation,
                        dateFormat = dateFormat,
                        deckTitle = conversation.deckId?.let { deckTitles[it] },
                        onClick = {
                            aiViewModel.loadConversation(conversation.id)
                            onConversationSelected()
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun AiConversationHistoryItem(
    conversation: AiConversation,
    dateFormat: SimpleDateFormat,
    deckTitle: String?,
    onClick: () -> Unit,
) {
    StudentAiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(conversation.title, style = MaterialTheme.typography.titleSmall)
        if (deckTitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Deck · $deckTitle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                conversation.type.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                dateFormat.format(Date(conversation.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
