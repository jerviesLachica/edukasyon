package com.edukasyon.studentai.core.share

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Typed failures from [ShareRepository] so the UI can say the right thing. */
sealed class ShareError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidCode : ShareError("That code doesn't look right.")
    data object NotFound : ShareError("No share found for that code.")
    data object PermissionDenied : ShareError("The share server rejected this request.")
    data object Expired : ShareError("This share has expired.")
    data object AlreadyTaken : ShareError("That code is already in use.")
    data object TooLarge : ShareError("There's too much content to share.")
    class Network(cause: Throwable?) : ShareError("Couldn't reach the server.", cause)
}

/**
 * Publishes/redeems private shares at Firestore `shares/{code}`.
 *
 * Uses the same injected [FirebaseFirestore] instance pattern as
 * FirestoreSyncService. All content rules (size caps, expiry, code format)
 * live in the pure-JVM [SharePayload]/[ShareCode]/[ShareDocument] types, so
 * this class only orchestrates Firestore I/O.
 */
@Singleton
class ShareRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val shares: CollectionReference = firestore.collection(COLLECTION_SHARES)

    /**
     * Publish [doc] under [code] (a fresh unguessable code by default) and
     * return the code. Refuses invalid codes, expired content, oversized
     * payloads, and colliding codes.
     */
    suspend fun publish(
        doc: ShareDocument,
        code: String = ShareCode.generate(),
    ): Result<String> = runCatching {
        if (!ShareCode.isValid(code)) throw ShareError.InvalidCode
        if (doc.payloadJson.length > SharePayload.MAX_PAYLOAD_CHARS) throw ShareError.TooLarge
        if (doc.isExpiredAt(System.currentTimeMillis())) throw ShareError.Expired

        // Collision-check first; on a live clash, re-roll a fresh code up to the retry cap.
        var attemptCode = code
        var attempts = 0
        while (true) {
            val existing = try {
                shares.document(attemptCode).get().await()
            } catch (e: FirebaseFirestoreException) {
                throw wrapped(e)
            }
            val stale = existing.data?.let { ShareDocument.fromMap(it) }
                ?.isExpiredAt(System.currentTimeMillis()) == true
            if (!existing.exists() || stale) break
            if (++attempts > MAX_COLLISION_RETRIES) throw ShareError.AlreadyTaken
            attemptCode = ShareCode.generate()
        }

        try {
            shares.document(attemptCode).set(doc.toMap()).await()
        } catch (e: FirebaseFirestoreException) {
            throw wrapped(e)
        }
        attemptCode
    }.onFailure { Log.w(TAG, "publish failed", it) }

    /**
     * Fetch and validate a share for [code]: format, existence, permission,
     * expiry. Bumps the view counter best-effort — a failed bump never fails
     * the redeem.
     */
    suspend fun redeem(code: String): Result<ShareDocument> = runCatching {
        if (!ShareCode.isValid(code)) throw ShareError.InvalidCode

        val snapshot = try {
            shares.document(code).get().await()
        } catch (e: FirebaseFirestoreException) {
            // An expired doc also trips the read rule -> PERMISSION_DENIED; to the
            // redeemer that's indistinguishable from gone, and it should read as such.
            throw if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                ShareError.NotFound else wrapped(e)
        }
        if (!snapshot.exists()) throw ShareError.NotFound
        val data = snapshot.data ?: throw ShareError.NotFound
        val doc = ShareDocument.fromMap(data) ?: throw ShareError.NotFound
        if (doc.isExpiredAt(System.currentTimeMillis())) throw ShareError.Expired

        bumpViews(code)
        doc
    }.onFailure { Log.w(TAG, "redeem failed for $code", it) }

    /** views+1 on a server timestamp; swallow every failure — cosmetic counter only. */
    private suspend fun bumpViews(code: String) {
        runCatching {
            shares.document(code).update("views", FieldValue.increment(1)).await()
        }
    }

    private fun wrapped(e: FirebaseFirestoreException): Exception = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> ShareError.PermissionDenied
        FirebaseFirestoreException.Code.NOT_FOUND -> ShareError.NotFound
        else -> ShareError.Network(e)
    }

    private companion object {
        const val TAG = "ShareRepository"
        const val COLLECTION_SHARES = "shares"
        const val MAX_COLLISION_RETRIES = 5
    }
}
