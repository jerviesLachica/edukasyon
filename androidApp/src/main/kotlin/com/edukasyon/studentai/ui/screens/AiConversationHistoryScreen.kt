package com.edukasyon.studentai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    LaunchedEffect(filterScope) {
        historyViewModel.setFilter(filterScope)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    AiConversationHistoryItem(
                        conversation = conversation,
                        dateFormat = dateFormat,
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

@Composable
private fun AiConversationHistoryItem(
    conversation: AiConversation,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
) {
    StudentAiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(conversation.title, style = MaterialTheme.typography.titleSmall)
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
