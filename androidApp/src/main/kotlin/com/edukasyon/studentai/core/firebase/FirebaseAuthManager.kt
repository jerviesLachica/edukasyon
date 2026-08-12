package com.edukasyon.studentai.core.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    suspend fun ensureAnonymousSession(): String? {
        currentUserId?.let { return it }
        return signInAnonymously().getOrNull()
    }

    suspend fun signInAnonymously(): Result<String> = runCatching {
        val result = auth.signInAnonymously().await()
        result.user?.uid ?: error("Anonymous sign-in returned no user")
    }.onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }

    suspend fun signOut() {
        runCatching { auth.signOut() }
            .onFailure { Log.w(TAG, "Sign-out failed", it) }
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }
}
