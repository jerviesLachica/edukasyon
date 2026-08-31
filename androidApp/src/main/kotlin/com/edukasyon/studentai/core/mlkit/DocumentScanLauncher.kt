package com.edukasyon.studentai.core.mlkit

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

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
            if (bytes != null) {
                onResult(bytes)
            } else {
                onError("Failed to read image bytes")
            }
        }
    }

    // Return a lambda that initializes and launches the document scanner
    return {
        val activity = context.findComponentActivity()
        if (activity == null) {
            onError("Document scanner requires an Activity context")
        } else {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pageLimit)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build()

            try {
                val client = GmsDocumentScanning.getClient(options)
                client.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                        scannerLauncher.launch(intentSenderRequest)
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Document scanner initialization failed")
                    }
            } catch (e: Exception) {
                onError("Could not initialize document scanner: ${e.message}")
            }
        }
    }
}

private suspend fun readUriBytes(context: Context, uri: Uri): ByteArray? {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
