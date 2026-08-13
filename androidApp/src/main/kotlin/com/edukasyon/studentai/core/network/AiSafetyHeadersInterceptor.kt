package com.edukasyon.studentai.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable device-scoped identifier sent to the backend for rate limiting and quotas.
 * Not personally identifiable — random UUID persisted locally.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = "android-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private companion object {
        const val PREFS_NAME = "ai_safety_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}

@Singleton
class AiSafetyHeadersInterceptor @Inject constructor(
    private val deviceIdProvider: DeviceIdProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Device-Id", deviceIdProvider.getDeviceId())
            .build()
        return chain.proceed(request)
    }
}
