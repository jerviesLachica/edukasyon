package com.edukasyon.studentai.core.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.edukasyon.studentai.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignInHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getSignInIntent(): Intent = getClient().signInIntent

    fun getIdTokenFromResult(data: Intent?): Result<String> = runCatching {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        val token = account.idToken
            ?: error("Google Sign-In returned no ID token. Add SHA-1/SHA-256 in Firebase Console and download an updated google-services.json.")
        token
    }

    suspend fun signOut() {
        runCatching { getClient().signOut() }
    }

    fun isConfigured(): Boolean = configurationIssue() == null

    /** Returns a user-facing reason when Google Sign-In cannot start, or null when ready. */
    fun configurationIssue(): String? {
        val clientId = resolveWebClientId()
        return when {
            clientId.isNullOrBlank() ->
                "Google Sign-In not configured — download an updated google-services.json from Firebase Console"
            clientId.startsWith("REPLACE_WITH") ->
                "Google Sign-In not configured — add SHA-1/SHA-256 in Firebase Console, then download google-services.json"
            else -> playServicesIssue()
        }
    }

    /** Detects broken/outdated Google Play Services — the usual cause of ApiException 8. */
    private fun playServicesIssue(): String? {
        val availability = GoogleApiAvailability.getInstance()
        val status = availability.isGooglePlayServicesAvailable(context)
        if (status == ConnectionResult.SUCCESS) return null
        return if (availability.isUserResolvableError(status)) {
            "Google Play Services needs an update — open the Play Store, update 'Google Play services', then try again"
        } else {
            "Google Play Services is unavailable on this device"
        }
    }

    /**
     * Maps a Google Sign-In failure to an actionable, user-facing message.
     * Returns null for benign outcomes (e.g. user cancelled) that should be silent.
     */
    fun describeSignInError(throwable: Throwable): String? {
        Log.w(TAG, "Google Sign-In failed", throwable)
        if (throwable !is ApiException) {
            val raw = throwable.message
            // Raw ApiException messages look like "8: " — never surface those verbatim.
            return if (raw != null && !raw.matches(Regex("\\d+: ?"))) {
                raw
            } else {
                "Google Sign-In failed — please try again"
            }
        }
        return when (throwable.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED, CommonStatusCodes.CANCELED -> null
            CommonStatusCodes.NETWORK_ERROR ->
                "Network error during Google Sign-In — check your connection and try again"
            CommonStatusCodes.DEVELOPER_ERROR ->
                "App configuration mismatch — verify this build's SHA-1 fingerprint in Firebase Console"
            CommonStatusCodes.INTERNAL_ERROR ->
                "Google had an internal error. Update 'Google Play services' from the Play Store, restart the app, then try signing in again"
            else -> "Google Sign-In failed (code ${throwable.statusCode}) — please try again"
        }
    }

    private fun getClient(): GoogleSignInClient {
        val webClientId = resolveWebClientId()
            ?: error(
                "Google Sign-In is not configured. Enable Google provider in Firebase Console, " +
                    "add your app SHA-1/SHA-256 fingerprints, and download an updated google-services.json."
            )
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    private fun resolveWebClientId(): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        return context.getString(resId).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "GoogleSignInHelper"
    }
}
