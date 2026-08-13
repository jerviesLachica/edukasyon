package com.edukasyon.studentai.core.firebase

import android.util.Log
import com.edukasyon.studentai.core.network.ConnectivityMonitor
import com.edukasyon.studentai.data.local.dao.*
import com.edukasyon.studentai.data.local.entity.SyncMetadataEntity
import com.edukasyon.studentai.data.mapper.toDomain
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.domain.model.SyncResult
import com.edukasyon.studentai.domain.model.SyncState
import com.edukasyon.studentai.domain.model.SyncSummary
import com.edukasyon.studentai.domain.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bidirectional Firestore sync for structured app data.
 * Cloud is the source of truth when signed in; Room is the offline cache.
 * Merge strategy: last-write-wins using [updatedAt] timestamps.
 */
@Singleton
class FirestoreSyncService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authManager: FirebaseAuthManager,
    private val connectivity: ConnectivityMonitor,
    private val preferences: UserPreferences,
    private val syncMetadataDao: SyncMetadataDao,
    private val userDao: UserDao,
    private val jeviDeckDao: JeviDeckDao,
    private val flashcardDao: FlashcardDao,
    private val reviewRecordDao: JeviReviewRecordDao,
    private val noteDao: NoteDao,
    private val noteTagDao: NoteTagDao,
    private val taskDao: TaskDao,
    private val subtaskDao: SubtaskDao,
    private val assignmentDao: AssignmentDao,
    private val examDao: ExamDao,
    private val scheduleDao: ScheduleDao,
    private val subjectDao: SubjectDao,
    private val gradeEntryDao: GradeEntryDao,
) {
    suspend fun syncUserProfile(user: UserProfile): Result<Unit> = runCatching {
        val uid = authManager.currentUserId ?: return@runCatching
        firestore.collection(COLLECTION_USERS)
            .document(uid)
            .set(user.toFirestoreMap(), SetOptions.merge())
            .await()
    }.onFailure { Log.w(TAG, "User profile sync failed", it) }

    suspend fun syncAll(): SyncResult {
        if (!connectivity.isCurrentlyOnline()) {
            return SyncResult.Offline
        }
        if (authManager.currentUserId == null || !authManager.isGoogleSignedIn) {
            return SyncResult.NotAuthenticated
        }

        return runCatching {
            var pushed = 0
            var pulled = 0
            val uid = authManager.currentUserId!!

            userDao.getUser()?.toDomain()?.let { syncUserProfile(it) }

            syncJeviDecks(uid).let { pushed += it.first; pulled += it.second }
            syncFlashcards(uid).let { pushed += it.first; pulled += it.second }
            syncReviewRecords(uid).let { pushed += it.first; pulled += it.second }
            syncNotes(uid).let { pushed += it.first; pulled += it.second }
            syncNoteTags(uid).let { pushed += it.first; pulled += it.second }
            syncSubjects(uid).let { pushed += it.first; pulled += it.second }
            syncScheduleItems(uid).let { pushed += it.first; pulled += it.second }
            syncTasks(uid).let { pushed += it.first; pulled += it.second }
            syncSubtasks(uid).let { pushed += it.first; pulled += it.second }
            syncAssignments(uid).let { pushed += it.first; pulled += it.second }
            syncExams(uid).let { pushed += it.first; pulled += it.second }
            syncGradeEntries(uid).let { pushed += it.first; pulled += it.second }

            val now = System.currentTimeMillis()
            val summary = SyncSummary(pushed = pushed, pulled = pulled, syncedAt = now)
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    entityType = ENTITY_TYPE_ALL,
                    lastSyncedAt = now,
                    pendingCount = 0,
                    failedCount = 0,
                    status = SyncState.SYNCED.name,
                )
            )
            preferences.setLastSyncedAt(now)
            Log.i(TAG, "Sync complete: pushed=$pushed pulled=$pulled")
            SyncResult.Success(summary)
        }.getOrElse { error ->
            Log.w(TAG, "Sync failed", error)
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    entityType = ENTITY_TYPE_ALL,
                    lastSyncedAt = System.currentTimeMillis(),
                    pendingCount = 0,
                    failedCount = 1,
                    status = SyncState.FAILED.name,
                )
            )
            SyncResult.Error(error.message ?: "Sync failed")
        }
    }

    private suspend fun syncJeviDecks(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_JEVI_DECKS,
            localItems = jeviDeckDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toJeviDeckEntity() },
            upsertLocal = { jeviDeckDao.insert(it) },
        )

    private suspend fun syncFlashcards(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_FLASHCARDS,
            localItems = flashcardDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toFlashcardEntity() },
            upsertLocal = { flashcardDao.insert(it) },
        )

    private suspend fun syncReviewRecords(uid: String): Pair<Int, Int> =
        syncWithTimestamp(
            uid = uid,
            collection = COLLECTION_REVIEW_RECORDS,
            localItems = reviewRecordDao.getAllForSync(),
            getId = { it.id },
            getTimestamp = { it.reviewedAt },
            mapTimestampKey = "reviewedAt",
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toJeviReviewRecordEntity() },
            upsertLocal = { reviewRecordDao.insert(it) },
        )

    private suspend fun syncNotes(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_NOTES,
            localItems = noteDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toNoteEntity() },
            upsertLocal = { noteDao.insert(it) },
        )

    private suspend fun syncNoteTags(uid: String): Pair<Int, Int> {
        val localItems = noteTagDao.getAllForSync()
        val remoteDocs = fetchRemote(uid, COLLECTION_NOTE_TAGS)
        var pushed = 0
        var pulled = 0

        val localByKey = localItems.associateBy { noteTagFirestoreId(it.noteId, it.tag) }
        val remoteByKey = remoteDocs.associateBy { it.first }

        for (key in localByKey.keys + remoteByKey.keys) {
            val local = localByKey[key]
            val remote = remoteByKey[key]
            when {
                local == null && remote != null -> {
                    noteTagDao.insert(remote.second.toNoteTagEntity())
                    pulled++
                }
                local != null && remote == null -> {
                    pushRemote(uid, COLLECTION_NOTE_TAGS, key, local.toFirestoreMap())
                    pushed++
                }
                local != null && remote != null -> {
                    pushRemote(uid, COLLECTION_NOTE_TAGS, key, local.toFirestoreMap())
                    pushed++
                }
            }
        }
        return pushed to pulled
    }

    private suspend fun syncSubjects(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_SUBJECTS,
            localItems = subjectDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toSubjectEntity() },
            upsertLocal = { subjectDao.insert(it) },
        )

    private suspend fun syncScheduleItems(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_SCHEDULE,
            localItems = scheduleDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toScheduleItemEntity() },
            upsertLocal = { scheduleDao.insert(it) },
        )

    private suspend fun syncTasks(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_TASKS,
            localItems = taskDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toTaskEntity() },
            upsertLocal = { taskDao.insert(it) },
        )

    private suspend fun syncSubtasks(uid: String): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        return syncWithTimestamp(
            uid = uid,
            collection = COLLECTION_SUBTASKS,
            localItems = subtaskDao.getAllForSync(),
            getId = { it.id },
            getTimestamp = { now },
            mapTimestampKey = "updatedAt",
            toMap = { it.toFirestoreMap(now) },
            fromMap = { _, map -> map.toSubtaskEntity() },
            upsertLocal = { subtaskDao.insert(it) },
        )
    }

    private suspend fun syncAssignments(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_ASSIGNMENTS,
            localItems = assignmentDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toAssignmentEntity() },
            upsertLocal = { assignmentDao.insert(it) },
        )

    private suspend fun syncExams(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_EXAMS,
            localItems = examDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toExamEntity() },
            upsertLocal = { examDao.insert(it) },
        )

    private suspend fun syncGradeEntries(uid: String): Pair<Int, Int> =
        syncWithUpdatedAt(
            uid = uid,
            collection = COLLECTION_GRADES,
            localItems = gradeEntryDao.getAllForSync(),
            getId = { it.id },
            getUpdatedAt = { it.updatedAt },
            toMap = { it.toFirestoreMap() },
            fromMap = { _, map -> map.toGradeEntryEntity() },
            upsertLocal = { gradeEntryDao.insert(it) },
        )

    private suspend fun <T> syncWithUpdatedAt(
        uid: String,
        collection: String,
        localItems: List<T>,
        getId: (T) -> String,
        getUpdatedAt: (T) -> Long,
        toMap: (T) -> Map<String, Any?>,
        fromMap: (String, Map<String, Any?>) -> T,
        upsertLocal: suspend (T) -> Unit,
    ): Pair<Int, Int> = syncWithTimestamp(
        uid = uid,
        collection = collection,
        localItems = localItems,
        getId = getId,
        getTimestamp = getUpdatedAt,
        mapTimestampKey = "updatedAt",
        toMap = toMap,
        fromMap = fromMap,
        upsertLocal = upsertLocal,
    )

    private suspend fun <T> syncWithTimestamp(
        uid: String,
        collection: String,
        localItems: List<T>,
        getId: (T) -> String,
        getTimestamp: (T) -> Long,
        mapTimestampKey: String,
        toMap: (T) -> Map<String, Any?>,
        fromMap: (String, Map<String, Any?>) -> T,
        upsertLocal: suspend (T) -> Unit,
    ): Pair<Int, Int> {
        val remoteDocs = fetchRemote(uid, collection)
        var pushed = 0
        var pulled = 0

        val localById = localItems.associateBy(getId)
        val remoteById = remoteDocs.associateBy { it.first }

        for (id in localById.keys + remoteById.keys) {
            val local = localById[id]
            val remote = remoteById[id]
            when {
                local == null && remote != null -> {
                    upsertLocal(fromMap(remote.first, remote.second))
                    pulled++
                }
                local != null && remote == null -> {
                    pushRemote(uid, collection, id, toMap(local))
                    upsertLocal(local)
                    pushed++
                }
                local != null && remote != null -> {
                    val localTs = getTimestamp(local)
                    val remoteTs = remote.second.long(mapTimestampKey) ?: 0L
                    if (localTs >= remoteTs) {
                        pushRemote(uid, collection, id, toMap(local))
                        upsertLocal(local)
                        pushed++
                    } else {
                        upsertLocal(fromMap(remote.first, remote.second))
                        pulled++
                    }
                }
            }
        }
        return pushed to pulled
    }

    private suspend fun fetchRemote(
        uid: String,
        collection: String,
    ): List<Pair<String, Map<String, Any?>>> {
        val snapshot = userCollection(uid, collection).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            doc.id to data
        }
    }

    private suspend fun pushRemote(
        uid: String,
        collection: String,
        id: String,
        data: Map<String, Any?>,
    ) {
        userCollection(uid, collection)
            .document(id)
            .set(data, SetOptions.merge())
            .await()
    }

    private fun userCollection(uid: String, collection: String) =
        firestore.collection(COLLECTION_USERS).document(uid).collection(collection)

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
        "bio" to bio,
        "preferredStatus" to preferredStatus,
        "lastProfileEditAt" to lastProfileEditAt,
        "updatedAt" to System.currentTimeMillis(),
    )

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    companion object {
        private const val TAG = "FirestoreSyncService"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_JEVI_DECKS = "jevi_decks"
        private const val COLLECTION_FLASHCARDS = "flashcards"
        private const val COLLECTION_REVIEW_RECORDS = "jevi_review_records"
        private const val COLLECTION_NOTES = "notes"
        private const val COLLECTION_NOTE_TAGS = "note_tags"
        private const val COLLECTION_SUBJECTS = "subjects"
        private const val COLLECTION_SCHEDULE = "schedule_items"
        private const val COLLECTION_TASKS = "tasks"
        private const val COLLECTION_SUBTASKS = "subtasks"
        private const val COLLECTION_ASSIGNMENTS = "assignments"
        private const val COLLECTION_EXAMS = "exams"
        private const val COLLECTION_GRADES = "grade_entries"
        const val ENTITY_TYPE_ALL = "all"
    }
}
