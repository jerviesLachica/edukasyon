package com.edukasyon.studentai.core.firebase

import com.edukasyon.studentai.data.local.entity.*
import com.edukasyon.studentai.domain.model.SyncState

internal fun JeviDeckEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "description" to description,
    "subjectId" to subjectId,
    "sourceNoteId" to sourceNoteId,
    "colorHex" to colorHex,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toJeviDeckEntity(): JeviDeckEntity = JeviDeckEntity(
    id = string("id") ?: error("missing id"),
    title = string("title") ?: "",
    description = string("description"),
    subjectId = string("subjectId"),
    sourceNoteId = string("sourceNoteId"),
    colorHex = string("colorHex") ?: "#3949AB",
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun FlashcardEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "question" to question,
    "answer" to answer,
    "subjectId" to subjectId,
    "deckId" to deckId,
    "topic" to topic,
    "difficulty" to difficulty,
    "reviewCount" to reviewCount,
    "correctCount" to correctCount,
    "incorrectCount" to incorrectCount,
    "lastReviewedAt" to lastReviewedAt,
    "nextReviewAt" to nextReviewAt,
    "easeFactor" to easeFactor,
    "intervalDays" to intervalDays,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toFlashcardEntity(): FlashcardEntity = FlashcardEntity(
    id = string("id") ?: error("missing id"),
    question = string("question") ?: "",
    answer = string("answer") ?: "",
    subjectId = string("subjectId"),
    deckId = string("deckId"),
    topic = string("topic"),
    difficulty = string("difficulty") ?: "MEDIUM",
    reviewCount = int("reviewCount") ?: 0,
    correctCount = int("correctCount") ?: 0,
    incorrectCount = int("incorrectCount") ?: 0,
    lastReviewedAt = long("lastReviewedAt"),
    nextReviewAt = long("nextReviewAt"),
    easeFactor = double("easeFactor") ?: 2.5,
    intervalDays = int("intervalDays") ?: 1,
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun JeviReviewRecordEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "flashcardId" to flashcardId,
    "deckId" to deckId,
    "quality" to quality,
    "reviewedAt" to reviewedAt,
    "intervalBefore" to intervalBefore,
    "intervalAfter" to intervalAfter,
    "easeFactorAfter" to easeFactorAfter,
)

internal fun Map<String, Any?>.toJeviReviewRecordEntity(): JeviReviewRecordEntity = JeviReviewRecordEntity(
    id = string("id") ?: error("missing id"),
    flashcardId = string("flashcardId") ?: "",
    deckId = string("deckId"),
    quality = int("quality") ?: 0,
    reviewedAt = long("reviewedAt") ?: System.currentTimeMillis(),
    intervalBefore = int("intervalBefore") ?: 0,
    intervalAfter = int("intervalAfter") ?: 0,
    easeFactorAfter = double("easeFactorAfter") ?: 2.5,
)

internal fun NoteEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "content" to content,
    "subjectId" to subjectId,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isPinned" to isPinned,
    "isFavorite" to isFavorite,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toNoteEntity(): NoteEntity = NoteEntity(
    id = string("id") ?: error("missing id"),
    title = string("title") ?: "",
    content = string("content") ?: "",
    subjectId = string("subjectId"),
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    isPinned = bool("isPinned") ?: false,
    isFavorite = bool("isFavorite") ?: false,
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun NoteTagEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "noteId" to noteId,
    "tag" to tag,
    "updatedAt" to System.currentTimeMillis(),
)

internal fun Map<String, Any?>.toNoteTagEntity(): NoteTagEntity = NoteTagEntity(
    id = long("id") ?: 0L,
    noteId = string("noteId") ?: "",
    tag = string("tag") ?: "",
)

internal fun TaskEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "description" to description,
    "subjectId" to subjectId,
    "priority" to priority,
    "dueDate" to dueDate,
    "dueTime" to dueTime,
    "status" to status,
    "category" to category,
    "reminderAt" to reminderAt,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "completedAt" to completedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toTaskEntity(): TaskEntity = TaskEntity(
    id = string("id") ?: error("missing id"),
    title = string("title") ?: "",
    description = string("description"),
    subjectId = string("subjectId"),
    priority = string("priority") ?: "MEDIUM",
    dueDate = long("dueDate"),
    dueTime = string("dueTime"),
    status = string("status") ?: "PENDING",
    category = string("category"),
    reminderAt = long("reminderAt"),
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    completedAt = long("completedAt"),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun SubtaskEntity.toFirestoreMap(now: Long = System.currentTimeMillis()): Map<String, Any?> = mapOf(
    "id" to id,
    "taskId" to taskId,
    "title" to title,
    "isCompleted" to isCompleted,
    "sortOrder" to sortOrder,
    "updatedAt" to now,
)

internal fun Map<String, Any?>.toSubtaskEntity(): SubtaskEntity = SubtaskEntity(
    id = string("id") ?: error("missing id"),
    taskId = string("taskId") ?: "",
    title = string("title") ?: "",
    isCompleted = bool("isCompleted") ?: false,
    sortOrder = int("sortOrder") ?: 0,
    updatedAt = long("updatedAt") ?: 0L,
    deletedAt = long("deletedAt"),
)

internal fun AssignmentEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "subjectId" to subjectId,
    "description" to description,
    "dueDate" to dueDate,
    "dueTime" to dueTime,
    "attachmentUri" to attachmentUri,
    "priority" to priority,
    "status" to status,
    "grade" to grade,
    "notes" to notes,
    "reminderAt" to reminderAt,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toAssignmentEntity(): AssignmentEntity = AssignmentEntity(
    id = string("id") ?: error("missing id"),
    title = string("title") ?: "",
    subjectId = string("subjectId"),
    description = string("description"),
    dueDate = long("dueDate"),
    dueTime = string("dueTime"),
    attachmentUri = string("attachmentUri"),
    priority = string("priority") ?: "MEDIUM",
    status = string("status") ?: "PENDING",
    grade = string("grade"),
    notes = string("notes"),
    reminderAt = long("reminderAt"),
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun ExamEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "title" to title,
    "subjectId" to subjectId,
    "linkedDeckId" to linkedDeckId,
    "examDate" to examDate,
    "examTime" to examTime,
    "location" to location,
    "coverage" to coverage,
    "notes" to notes,
    "reminderAt" to reminderAt,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toExamEntity(): ExamEntity = ExamEntity(
    id = string("id") ?: error("missing id"),
    title = string("title") ?: "",
    subjectId = string("subjectId"),
    linkedDeckId = string("linkedDeckId"),
    examDate = long("examDate") ?: System.currentTimeMillis(),
    examTime = string("examTime"),
    location = string("location"),
    coverage = string("coverage"),
    notes = string("notes"),
    reminderAt = long("reminderAt"),
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun ScheduleItemEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "subjectId" to subjectId,
    "subjectName" to subjectName,
    "teacher" to teacher,
    "room" to room,
    "building" to building,
    "dayOfWeek" to dayOfWeek,
    "startTime" to startTime,
    "endTime" to endTime,
    "colorHex" to colorHex,
    "notes" to notes,
    "semester" to semester,
    "schoolYear" to schoolYear,
    "isRecurring" to isRecurring,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toScheduleItemEntity(): ScheduleItemEntity = ScheduleItemEntity(
    id = string("id") ?: error("missing id"),
    subjectId = string("subjectId"),
    subjectName = string("subjectName") ?: "",
    teacher = string("teacher"),
    room = string("room"),
    building = string("building"),
    dayOfWeek = string("dayOfWeek") ?: "MONDAY",
    startTime = string("startTime") ?: "08:00",
    endTime = string("endTime") ?: "09:00",
    colorHex = string("colorHex") ?: "#3949AB",
    notes = string("notes"),
    semester = string("semester") ?: "",
    schoolYear = string("schoolYear") ?: "",
    isRecurring = bool("isRecurring") ?: true,
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun SubjectEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "code" to code,
    "teacher" to teacher,
    "colorHex" to colorHex,
    "semester" to semester,
    "schoolYear" to schoolYear,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toSubjectEntity(): SubjectEntity = SubjectEntity(
    id = string("id") ?: error("missing id"),
    name = string("name") ?: "",
    code = string("code"),
    teacher = string("teacher"),
    colorHex = string("colorHex") ?: "#3949AB",
    semester = string("semester") ?: "",
    schoolYear = string("schoolYear") ?: "",
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun GradeEntryEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "subjectId" to subjectId,
    "assessment" to assessment,
    "category" to category,
    "score" to score,
    "maxScore" to maxScore,
    "weight" to weight,
    "term" to term,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "deletedAt" to deletedAt,
)

internal fun Map<String, Any?>.toGradeEntryEntity(): GradeEntryEntity = GradeEntryEntity(
    id = string("id") ?: error("missing id"),
    subjectId = string("subjectId") ?: "",
    assessment = string("assessment") ?: "",
    category = string("category") ?: "",
    score = double("score") ?: 0.0,
    maxScore = double("maxScore") ?: 100.0,
    weight = double("weight") ?: 1.0,
    term = string("term") ?: "",
    createdAt = long("createdAt") ?: System.currentTimeMillis(),
    updatedAt = long("updatedAt") ?: System.currentTimeMillis(),
    deletedAt = long("deletedAt"),
    syncState = SyncState.SYNCED.name,
)

internal fun noteTagFirestoreId(noteId: String, tag: String): String =
    "${noteId}_${tag.replace("/", "_")}"

private fun Map<String, Any?>.string(key: String): String? = this[key] as? String
private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()
private fun Map<String, Any?>.double(key: String): Double? = (this[key] as? Number)?.toDouble()
private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
