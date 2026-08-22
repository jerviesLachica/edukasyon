package com.edukasyon.studentai.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.edukasyon.studentai.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val VERSION_URL = "https://edukasyon-studentai.web.app/version.json"
    }

    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VERSION_URL).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("Server returned ${response.code}")
                }
                val body = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                val updateInfo = json.decodeFromString<UpdateInfo>(body)
                val currentVersionCode = getCurrentVersionCode()

                Log.d(TAG, "Server versionCode=${updateInfo.versionCode}, current=$currentVersionCode")

                when {
                    updateInfo.versionCode > currentVersionCode -> UpdateResult.Available(updateInfo)
                    else -> UpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            packageInfo.longVersionCode.toInt()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not get package version", e)
            BuildConfig.VERSION_CODE
        }
    }
}
