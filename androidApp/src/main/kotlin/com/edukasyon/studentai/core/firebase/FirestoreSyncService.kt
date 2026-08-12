package com.edukasyon.studentai.core.firebase

import android.util.Log
import com.edukasyon.studentai.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore sync skeleton. Room remains the source of truth; this pushes user profile
 * snapshots when a Firebase session exists. Full entity sync is not implemented yet.
 */
@Singleton
class FirestoreSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authManager: FirebaseAuthManager
) {
    suspend fun syncUserProfile(user: UserProfile): Result<Unit> = runCatching {
        val uid = authManager.currentUserId ?: return@runCatching
        firestore.collection(COLLECTION_USERS)
            .document(uid)
            .set(user.toFirestoreMap(), SetOptions.merge())
            .await()
    }.onFailure { Log.w(TAG, "User profile sync failed", it) }

    private fun UserProfile.toFirestoreMap(): Map<String, Any?> = mapOf(
        "displayName" to displayName,
        "email" to email,
        "school" to school,
        "gradeLevel" to gradeLevel,
        "section" to section,
        "schoolYear" to schoolYear,
        "semester" to semester,
        "isGuest" to isGuest,
        "avatarUri" to avatarUri,
        "updatedAt" to System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "FirestoreSyncService"
        private const val COLLECTION_USERS = "users"
    }
}
