package com.edukasyon.studentai.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

@HiltViewModel
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateManager"
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState

    private var pendingInfo: UpdateInfo? = null
    private var downloadJob: Job? = null

    suspend fun checkForUpdate(): UpdateResult {
        _uiState.value = UpdateUiState.Checking
        return updateChecker.checkForUpdate()
    }

    /** Surfaces an available update in the UI (in-app prompt with install button). */
    fun showAvailable(info: UpdateInfo) {
        pendingInfo = info
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "schedmate-${info.versionName}.apk")

        if (isValidApk(apkFile, info.versionCode)) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            _uiState.value = UpdateUiState.ReadyToInstall(
                apkUri = uri.toString(),
                file = apkFile,
                info = info,
                needsPermission = !canRequestPackageInstalls()
            )
        } else {
            _uiState.value = UpdateUiState.UpdateAvailable(info)
        }
    }

    /**
     * Shopee-style automatic background download: starts downloading silently
     * without interrupting the user. If the APK is already downloaded and valid,
     * immediately transitions to [UpdateUiState.ReadyToInstall].
     */
    fun startAutoDownload(updateInfo: UpdateInfo) {
        pendingInfo = updateInfo
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "schedmate-${updateInfo.versionName}.apk")

        if (isValidApk(apkFile, updateInfo.versionCode)) {
            Log.i(TAG, "Update APK already downloaded and valid: ${apkFile.absolutePath}")
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            }.getOrNull()
            _uiState.value = UpdateUiState.ReadyToInstall(
                apkUri = uri?.toString() ?: apkFile.toURI().toString(),
                file = apkFile,
                info = updateInfo,
                needsPermission = !canRequestPackageInstalls()
            )
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            performDownload(updateInfo, isBackground = true)
        }
    }

    /** Starts downloading the last [showAvailable] payload. */
    fun startPendingDownload() {
        val info = pendingInfo ?: return
        startDownload(info)
    }

    fun startDownload(updateInfo: UpdateInfo) {
        pendingInfo = updateInfo
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            performDownload(updateInfo, isBackground = false)
        }
    }

    private suspend fun performDownload(updateInfo: UpdateInfo, isBackground: Boolean) {
        _uiState.value = UpdateUiState.Downloading(
            progress = 0f,
            versionName = updateInfo.versionName,
            isBackground = isBackground
        )

        val url = updateInfo.apkUrl
        if (!isAllowedDownloadUrl(url)) {
            _uiState.value = UpdateUiState.Error("Update download URL must use HTTPS from a trusted domain")
            return
        }

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean up older cached APKs to prevent storage bloat
        updatesDir.listFiles()?.forEach { file ->
            if (file.name != "schedmate-${updateInfo.versionName}.apk") {
                runCatching { file.delete() }
            }
        }

        val destinationFile = File(updatesDir, "schedmate-${updateInfo.versionName}.apk")
        val tempFile = File(updatesDir, "schedmate-${updateInfo.versionName}.apk.tmp")

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                _uiState.value = UpdateUiState.Error("Download failed with HTTP ${response.code}")
                return
            }

            val body = response.body ?: run {
                _uiState.value = UpdateUiState.Error("Empty response received from server")
                return
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    var lastProgressTime = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 250 || (totalBytes > 0 && downloadedBytes == totalBytes)) {
                            lastProgressTime = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            _uiState.value = UpdateUiState.Downloading(
                                progress = progress,
                                versionName = updateInfo.versionName,
                                isBackground = isBackground
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destinationFile.exists()) destinationFile.delete()
                if (tempFile.renameTo(destinationFile)) {
                    if (isValidApk(destinationFile, updateInfo.versionCode)) {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            destinationFile
                        )
                        _uiState.value = UpdateUiState.ReadyToInstall(
                            apkUri = uri.toString(),
                            file = destinationFile,
                            info = updateInfo,
                            needsPermission = !canRequestPackageInstalls()
                        )
                    } else {
                        destinationFile.delete()
                        _uiState.value = UpdateUiState.Error("Downloaded package is invalid or corrupted")
                    }
                } else {
                    _uiState.value = UpdateUiState.Error("Failed to save downloaded update file")
                }
            } else {
                _uiState.value = UpdateUiState.Error("Download resulted in empty file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            tempFile.delete()
            _uiState.value = UpdateUiState.Error("Download interrupted: ${e.message}")
        }
    }

    private fun isValidApk(file: File, expectedVersionCode: Int): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        return try {
            val archiveInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (archiveInfo == null) {
                Log.w(TAG, "Cannot parse package info for ${file.name}")
                return false
            }
            if (archiveInfo.packageName != context.packageName) {
                Log.w(TAG, "Package name mismatch: ${archiveInfo.packageName} != ${context.packageName}")
                return false
            }
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.versionCode
            }
            archiveVersionCode >= expectedVersionCode
        } catch (e: Exception) {
            Log.w(TAG, "APK validation failed", e)
            false
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open install permission settings", e)
        }
    }

    fun installApk(targetFile: File? = null) {
        val file = targetFile ?: run {
            val current = _uiState.value
            if (current is UpdateUiState.ReadyToInstall) current.file else null
        }

        if (file == null || !file.exists()) {
            _uiState.value = UpdateUiState.Error("Update file not found. Please try downloading again.")
            return
        }

        if (!canRequestPackageInstalls()) {
            val current = _uiState.value
            if (current is UpdateUiState.ReadyToInstall) {
                _uiState.value = current.copy(needsPermission = true)
            }
            openInstallPermissionSettings()
            return
        }

        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            _uiState.value = UpdateUiState.InstallStarted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer", e)
            _uiState.value = UpdateUiState.Error("Failed to open package installer: ${e.message}")
        }
    }

    fun installApk(apkUriString: String) {
        val current = _uiState.value
        if (current is UpdateUiState.ReadyToInstall && current.file != null) {
            installApk(current.file)
            return
        }

        try {
            val uri = Uri.parse(apkUriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            _uiState.value = UpdateUiState.InstallStarted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch install from URI", e)
            _uiState.value = UpdateUiState.Error("Could not open installer: ${e.message}")
        }
    }

    fun onResume() {
        val current = _uiState.value
        if (current is UpdateUiState.ReadyToInstall && current.needsPermission && canRequestPackageInstalls()) {
            _uiState.value = current.copy(needsPermission = false)
            // Once permission is granted and user returns to the app, immediately prompt install!
            current.file?.let { installApk(it) }
        }
    }

    private fun isAllowedDownloadUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        val host = parsed.host?.lowercase() ?: return false

        val allowedHosts = setOf(
            "edukasyon-studentai.web.app",
            "edukasyon-studentai.firebaseapp.com",
            "schedmate-backend.vercel.app",
            "github.com",
            "githubusercontent.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    fun reset() {
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Idle
    }
}

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState()
    data class Downloading(
        val progress: Float,
        val versionName: String = "",
        val isBackground: Boolean = true,
    ) : UpdateUiState()
    data class ReadyToInstall(
        val apkUri: String,
        val file: File? = null,
        val info: UpdateInfo? = null,
        val needsPermission: Boolean = false,
    ) : UpdateUiState()
    data object InstallStarted : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
