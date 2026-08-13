package com.edukasyon.studentai.core.firebase

import android.content.Context
import android.content.Intent
import com.edukasyon.studentai.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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
            else -> null
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
}
