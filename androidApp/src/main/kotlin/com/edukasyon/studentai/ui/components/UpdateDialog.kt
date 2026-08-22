package com.edukasyon.studentai.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.edukasyon.studentai.core.update.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismissRequest: () -> Unit,
    onUpdateNow: (String) -> Unit,
    onStartDownload: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle,
        UpdateUiState.Checking,
        UpdateUiState.UpToDate -> Unit
        is UpdateUiState.UpdateAvailable -> UpdateAvailableCard(
            state = state,
            onDismissRequest = onDismissRequest,
            onStartDownload = onStartDownload,
        )
        is UpdateUiState.Downloading -> UpdateDownloadingCard(state = state)
        is UpdateUiState.ReadyToInstall -> UpdateReadyCard(
            state = state,
            onDismissRequest = onDismissRequest,
            onUpdateNow = onUpdateNow,
        )
        UpdateUiState.InstallStarted -> Unit
        is UpdateUiState.Error -> UpdateErrorCard(state = state, onDismissRequest = onDismissRequest)
    }
}

/** Shared card container matching the app's Material3 theme. */
@Composable
private fun UpdateCard(content: @Composable () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            ) { content() }
        }
    }
}

@Composable
private fun UpdateTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun UpdateMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Big pill CTA in the app's primary color. */
@Composable
private fun UpdateCta(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UpdateDismissLink(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UpdateAvailableCard(
    state: UpdateUiState.UpdateAvailable,
    onDismissRequest: () -> Unit,
    onStartDownload: () -> Unit,
) {
    UpdateCard {
        UpdateTitle("Update Available")
        val notes = state.info.releaseNotes.isNotBlank()
        UpdateMessage(
            if (notes) state.info.releaseNotes
            else "A new version of the app is ready to install.\nDo you want to update now?"
        )
        Spacer(Modifier.height(4.dp))
        UpdateCta(label = "Update Now", onClick = onStartDownload)
        if (!state.info.mandatoryUpdate) {
            UpdateDismissLink(label = "Not Now", onClick = onDismissRequest)
        }
    }
}

@Composable
private fun UpdateDownloadingCard(state: UpdateUiState.Downloading) {
    UpdateCard {
        UpdateTitle("Downloading Update")
        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        UpdateMessage("Downloading SchedMate update… ${(state.progress * 100).toInt()}%")
    }
}

@Composable
private fun UpdateReadyCard(
    state: UpdateUiState.ReadyToInstall,
    onDismissRequest: () -> Unit,
    onUpdateNow: (String) -> Unit,
) {
    UpdateCard {
        UpdateTitle("Ready to Install")
        UpdateMessage("Version downloaded successfully.\nTap below to install it now.")
        UpdateCta(label = "Install Now", onClick = { onUpdateNow(state.apkUri) })
        UpdateDismissLink(label = "Not Now", onClick = onDismissRequest)
    }
}

@Composable
private fun UpdateErrorCard(state: UpdateUiState.Error, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    UpdateCard {
        UpdateTitle("Update Unavailable")
        UpdateMessage(state.message)
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open app settings")
        }
        UpdateDismissLink(label = "Close", onClick = onDismissRequest)
    }
}
