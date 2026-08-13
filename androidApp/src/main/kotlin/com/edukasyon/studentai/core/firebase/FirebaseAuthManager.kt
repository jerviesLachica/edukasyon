package com.edukasyon.studentai.core.firebase

import android.util.Log
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class GoogleSignInOutcome(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val linkedFromAnonymous: Boolean,
    val mergedExistingAccount: Boolean = false,
)

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val preferences: UserPreferences,
    private val googleSignInHelper: GoogleSignInHelper,
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous == true

    val isGoogleSignedIn: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    val userEmail: String?
        get() = auth.currentUser?.email

    suspend fun ensureAnonymousSession(): String? {
        if (isGoogleSignedIn) {
            currentUserId?.let { return it }
        }
        currentUserId?.let { return it }
        return signInAnonymously().getOrNull()
    }

    suspend fun signInAnonymously(): Result<String> = runCatching {
        val result = auth.signInAnonymously().await()
        result.user?.uid ?: error("Anonymous sign-in returned no user")
    }.onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }

    suspend fun signInWithGoogle(idToken: String): Result<GoogleSignInOutcome> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser

        if (current?.isAnonymous == true) {
            try {
                val result = current.linkWithCredential(credential).await()
                val user = result.user ?: error("Google link returned no user")
                persistAuthState(user)
                GoogleSignInOutcome(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    linkedFromAnonymous = true,
                )
            } catch (collision: FirebaseAuthUserCollisionException) {
                Log.i(TAG, "Google account already exists — signing in and merging via sync")
                val result = auth.signInWithCredential(credential).await()
                val user = result.user ?: error("Google sign-in returned no user")
                persistAuthState(user)
                GoogleSignInOutcome(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    linkedFromAnonymous = false,
                    mergedExistingAccount = true,
                )
            }
        } else {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: error("Google sign-in returned no user")
            persistAuthState(user)
            GoogleSignInOutcome(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                linkedFromAnonymous = false,
            )
        }
    }.onFailure { Log.w(TAG, "Google sign-in failed", it) }

    suspend fun signOut() {
        runCatching {
            auth.signOut()
            googleSignInHelper.signOut()
            preferences.clearFirebaseAuth()
        }.onFailure { Log.w(TAG, "Sign-out failed", it) }
    }

    suspend fun refreshPersistedAuthState() {
        auth.currentUser?.let { persistAuthState(it) } ?: preferences.clearFirebaseAuth()
    }

    private suspend fun persistAuthState(user: FirebaseUser) {
        val googleLinked = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        preferences.setFirebaseAuthEmail(user.email)
        preferences.setGoogleAccountLinked(googleLinked)
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }
}
