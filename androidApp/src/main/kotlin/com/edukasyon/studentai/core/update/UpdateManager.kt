package com.edukasyon.studentai.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
) : ViewModel() {
    companion object {
        private const val TAG = "UpdateManager"
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState

    private var downloadId: Long = -1L
    private var downloadReceiver: BroadcastReceiver? = null
    private var pendingInfo: UpdateInfo? = null

    suspend fun checkForUpdate(): UpdateResult {
        _uiState.value = UpdateUiState.Checking
        return updateChecker.checkForUpdate()
    }

    /** Surfaces an available update in the UI (in-app prompt with install button). */
    fun showAvailable(info: UpdateInfo) {
        pendingInfo = info
        _uiState.value = UpdateUiState.UpdateAvailable(info)
    }

    /** Starts downloading the last [showAvailable] payload (used by the update dialog). */
    fun startPendingDownload() {
        val info = pendingInfo ?: return
        startDownload(info)
    }

    fun startDownload(updateInfo: UpdateInfo) {
        _uiState.value = UpdateUiState.Downloading(0f)

        val url = updateInfo.apkUrl

        // Hardening: only allow HTTPS downloads for the self-update path. The URL
        // comes from a developer-controlled version.json over HTTPS
        // (UpdateChecker) — but defence-in-depth against an attacker who
        // somehow controls that document (or any future FCM-injected URL)
        // must not let them ship a cleartext APK. Android's same-signature
        // update gate mitigates pure RCE, but cleartext lets an on-path
        // attacker substitute a malformed/older APK to grief the user.
        if (!isAllowedDownloadUrl(url)) {
            _uiState.value = UpdateUiState.Error("Update download URL must use HTTPS")
            return
        }

        val fileName = "schedmate-${updateInfo.versionName}.apk"

        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Updating SchedMate")
                .setDescription("Downloading version ${updateInfo.versionName}…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            // Register for download completion
            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
                    if (id == downloadId) {
                        handleDownloadComplete(downloadManager)
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(downloadReceiver, filter)
            }

            // Monitor download progress
            CoroutineScope(Dispatchers.IO).launch {
                monitorDownloadProgress(downloadManager)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start download", e)
            _uiState.value = UpdateUiState.Error("Failed to start download: ${e.message}")
        }
    }

    private suspend fun monitorDownloadProgress(downloadManager: DownloadManager) {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        _uiState.value = UpdateUiState.Downloading(progress)
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        cursor.close()
                        handleDownloadComplete(downloadManager)
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        cursor.close()
                        _uiState.value = UpdateUiState.Error("Download failed")
                        return
                    }
                }
            }
            cursor.close()
            delay(500)
        }
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase() ?: return false
        // Only allow hosts that the developer controls. This prevents an
        // attacker who manages to inject a foreign URL into the update
        // document (or future FCM payload) from having a stolen code-signing
        // cert signed APK on a hostile domain installed as a "legitimate"
        // update. Add hosts here when expanding the distribution surface.
        val allowedHosts = setOf(
            "edukasyon-studentai.web.app",
            "edukasyon-studentai.firebaseapp.com",
            "studentai-backend-ha0z.onrender.com",
        )
        // Exact match or any subdomain of an allowed parent domain.
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun handleDownloadComplete(downloadManager: DownloadManager) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId)
        if (uri != null) {
            _uiState.value = UpdateUiState.ReadyToInstall(uri.toString())
        } else {
            // Try to get the file from Downloads folder
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close()
                if (localUri != null) {
                    _uiState.value = UpdateUiState.ReadyToInstall(localUri)
                } else {
                    _uiState.value = UpdateUiState.Error("Download completed but file not found")
                }
            } else {
                cursor.close()
                _uiState.value = UpdateUiState.Error("Download failed")
            }
        }

        // Unregister receiver
        try {
            downloadReceiver?.let { context.unregisterReceiver(it) }
            downloadReceiver = null
        } catch (_: Exception) { }
    }

    fun installApk(apkUriString: String) {
        try {
            val uri = Uri.parse(apkUriString)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                _uiState.value = UpdateUiState.InstallStarted
            } else {
                // Fallback: try via file path
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        val fileProviderUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileProviderUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                        _uiState.value = UpdateUiState.InstallStarted
                    } else {
                        _uiState.value = UpdateUiState.Error("APK file not found at $path")
                    }
                } else {
                    _uiState.value = UpdateUiState.Error("Could not determine APK path")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch install", e)
            _uiState.value = UpdateUiState.Error("Could not open installer: ${e.message}")
        }
    }

    fun reset() {
        _uiState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(val progress: Float) : UpdateUiState()
    data class ReadyToInstall(val apkUri: String) : UpdateUiState()
    data object InstallStarted : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
