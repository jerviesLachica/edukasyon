package com.edukasyon.studentai.core.mlkit

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launches Google Play ML Kit Document Scanner (auto-crop, enhance).
 * Returns JPEG bytes from the first scanned page, or null on cancel/failure.
 */
@Composable
fun rememberDocumentScanLauncher(
    onResult: (ByteArray) -> Unit,
    onError: (String) -> Unit = {},
    pageLimit: Int = 1,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            ?: run {
                onError("Document scan returned no result")
                return@rememberLauncherForActivityResult
            }
        val pageUri = scanResult.pages?.firstOrNull()?.imageUri
            ?: run {
                onError("Document scan did not produce an image")
                return@rememberLauncherForActivityResult
            }
        scope.launch {
            val bytes = readUriBytes(context, pageUri)
            if (bytes != null && bytes.isNotEmpty()) {
                onResult(bytes)
            } else {
                onError("Could not read scanned document")
            }
        }
    }

    return {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit.coerceIn(1, 10))
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                onError(error.message ?: "Document scanner unavailable")
            }
    }
}

private suspend fun readUriBytes(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}
