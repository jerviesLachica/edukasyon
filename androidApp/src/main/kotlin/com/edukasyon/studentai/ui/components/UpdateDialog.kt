package com.edukasyon.studentai.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edukasyon.studentai.core.update.UpdateUiState

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismissRequest: () -> Unit,
    onUpdateNow: (String) -> Unit,
) {
    val context = LocalContext.current

    when (state) {
        UpdateUiState.Idle,
        UpdateUiState.Checking,
        UpdateUiState.UpToDate -> Unit
        is UpdateUiState.Downloading -> UpdateDownloadingDialog(state = state)
        is UpdateUiState.ReadyToInstall -> UpdateReadyDialog(
            state = state,
            onDismissRequest = onDismissRequest,
            onUpdateNow = onUpdateNow
        )
        UpdateUiState.InstallStarted -> Unit
        is UpdateUiState.Error -> UpdateErrorDialog(state = state, onDismissRequest = onDismissRequest)
    }
}

@Composable
private fun UpdateDownloadingDialog(state: UpdateUiState.Downloading) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Downloading update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Downloading SchedMate update...")
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun UpdateReadyDialog(
    state: UpdateUiState.ReadyToInstall,
    onDismissRequest: () -> Unit,
    onUpdateNow: (String) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Update ready") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A new version is ready to install. Tap Update to start the installer.")
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdateNow(state.apkUri)
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text("Later")
            }
        }
    )
}

@Composable
private fun UpdateErrorDialog(state: UpdateUiState.Error, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Update unavailable") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message)
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open app settings")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("Close")
            }
        },
        dismissButton = {}
    )
}
