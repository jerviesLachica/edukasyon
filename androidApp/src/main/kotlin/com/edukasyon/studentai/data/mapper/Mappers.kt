package com.edukasyon.studentai.data.mapper

import com.edukasyon.studentai.data.local.entity.*
import com.edukasyon.studentai.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun UserEntity.toDomain() = UserProfile(
    id = id, displayName = displayName, email = email, school = school,
    gradeLevel = gradeLevel, section = section, schoolYear = schoolYear,
    semester = semester, isGuest = isGuest, avatarUri = avatarUri,
    bio = bio, preferredStatus = preferredStatus, lastProfileEditAt = lastProfileEditAt,
)

fun UserProfile.toEntity(existingCreatedAt: Long? = null, now: Long = System.currentTimeMillis()) = UserEntity(
    id = id, displayName = displayName, email = email, school = school,
    gradeLevel = gradeLevel, section = section, schoolYear = schoolYear,
    semester = semester, isGuest = isGuest, avatarUri = avatarUri,
    bio = bio, preferredStatus = preferredStatus, lastProfileEditAt = lastProfileEditAt,
    createdAt = existingCreatedAt ?: now, updatedAt = now, syncState = SyncState.LOCAL_ONLY.name
)

fun SubjectEntity.toDomain() = Subject(
    id = id, name = name, code = code, teacher = teacher, colorHex = colorHex,
    semester = semester, schoolYear = schoolYear
)

fun ScheduleItemEntity.toDomain() = ScheduleItem(
    id = id, subjectId = subjectId, subjectName = subjectName, teacher = teacher,
    room = room, building = building,
    dayOfWeek = DayOfWeek.fromString(dayOfWeek) ?: DayOfWeek.MONDAY,
    startTime = startTime, endTime = endTime, colorHex = colorHex, notes = notes,
    semester = semester, schoolYear = schoolYear, isRecurring = isRecurring
)

fun ScheduleItem.toEntity(now: Long = System.currentTimeMillis()) = ScheduleItemEntity(
    id = id, subjectId = subjectId, subjectName = subjectName, teacher = teacher,
    room = room, building = building, dayOfWeek = dayOfWeek.name,
    startTime = startTime, endTime = endTime, colorHex = colorHex, notes = notes,
    semester = semester, schoolYear = schoolYear, isRecurring = isRecurring,
    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun TaskEntity.toDomain(subtasks: List<Subtask> = emptyList()) = Task(
    id = id, title = title, description = description, subjectId = subjectId,
    priority = Priority.valueOf(priority), dueDate = dueDate, dueTime = dueTime,
    status = TaskStatus.valueOf(status), category = category, reminderAt = reminderAt,
    createdAt = createdAt, updatedAt = updatedAt, completedAt = completedAt, subtasks = subtasks
)

fun Task.toEntity(now: Long = System.currentTimeMillis()) = TaskEntity(
    id = id, title = title, description = description, subjectId = subjectId,
    priority = priority.name, dueDate = dueDate, dueTime = dueTime,
    status = status.name, category = category, reminderAt = reminderAt,
    createdAt = createdAt, updatedAt = now, completedAt = completedAt,
    deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun SubtaskEntity.toDomain() = Subtask(id = id, taskId = taskId, title = title, isCompleted = isCompleted, sortOrder = sortOrder)

fun AssignmentEntity.toDomain() = Assignment(
    id = id, title = title, subjectId = subjectId, description = description,
    dueDate = dueDate, dueTime = dueTime, attachmentUri = attachmentUri,
    priority = Priority.valueOf(priority), status = TaskStatus.valueOf(status),
    grade = grade, notes = notes, reminderAt = reminderAt
)

fun Assignment.toEntity(now: Long = System.currentTimeMillis()) = AssignmentEntity(
    id = id, title = title, subjectId = subjectId, description = description,
    dueDate = dueDate, dueTime = dueTime, attachmentUri = attachmentUri, priority = priority.name,
    status = status.name, grade = grade, notes = notes, reminderAt = reminderAt,
    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun ExamEntity.toDomain() = Exam(
    id = id, title = title, subjectId = subjectId, linkedDeckId = linkedDeckId,
    examDate = examDate, examTime = examTime, location = location, coverage = coverage,
    notes = notes, reminderAt = reminderAt
)

fun Exam.toEntity(now: Long = System.currentTimeMillis()) = ExamEntity(
    id = id, title = title, subjectId = subjectId, linkedDeckId = linkedDeckId,
    examDate = examDate, examTime = examTime, location = location, coverage = coverage,
    notes = notes, reminderAt = reminderAt,
    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun NoteEntity.toDomain(tags: List<String> = emptyList()) = Note(
    id = id, title = title, content = content, subjectId = subjectId, tags = tags,
    createdAt = createdAt, updatedAt = updatedAt, isPinned = isPinned, isFavorite = isFavorite
)

fun Note.toEntity(now: Long = System.currentTimeMillis()) = NoteEntity(
    id = id, title = title, content = content, subjectId = subjectId,
    createdAt = createdAt, updatedAt = now, isPinned = isPinned, isFavorite = isFavorite,
    deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun FlashcardEntity.toDomain() = Flashcard(
    id = id, question = question, answer = answer, subjectId = subjectId,
    deckId = deckId, topic = topic, difficulty = difficulty, reviewCount = reviewCount,
    correctCount = correctCount, incorrectCount = incorrectCount,
    lastReviewedAt = lastReviewedAt, nextReviewAt = nextReviewAt,
    easeFactor = easeFactor, intervalDays = intervalDays
)

fun JeviDeckEntity.toDomain(
    cardCount: Int = 0,
    dueCount: Int = 0,
    masteredCount: Int = 0,
) = JeviDeck(
    id = id,
    title = title,
    description = description,
    subjectId = subjectId,
    sourceNoteId = sourceNoteId,
    colorHex = colorHex,
    createdAt = createdAt,
    updatedAt = updatedAt,
    cardCount = cardCount,
    dueCount = dueCount,
    masteredCount = masteredCount,
)

fun JeviDeck.toEntity(now: Long = System.currentTimeMillis()) = JeviDeckEntity(
    id = id,
    title = title,
    description = description,
    subjectId = subjectId,
    sourceNoteId = sourceNoteId,
    colorHex = colorHex,
    createdAt = createdAt.takeIf { it > 0 } ?: now,
    updatedAt = now,
    deletedAt = null,
    syncState = SyncState.LOCAL_ONLY.name,
)

fun JeviReviewRecordEntity.toDomain() = JeviReviewRecord(
    id = id,
    flashcardId = flashcardId,
    deckId = deckId,
    quality = quality,
    reviewedAt = reviewedAt,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
    easeFactorAfter = easeFactorAfter,
)

fun JeviReviewRecord.toEntity() = JeviReviewRecordEntity(
    id = id,
    flashcardId = flashcardId,
    deckId = deckId,
    quality = quality,
    reviewedAt = reviewedAt,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
    easeFactorAfter = easeFactorAfter,
)

fun GradeEntryEntity.toDomain() = GradeEntry(
    id = id, subjectId = subjectId, assessment = assessment, category = category,
    score = score, maxScore = maxScore, weight = weight, term = term
)

fun GradeEntry.toEntity(now: Long = System.currentTimeMillis()) = GradeEntryEntity(
    id = id, subjectId = subjectId, assessment = assessment, category = category,
    score = score, maxScore = maxScore, weight = weight, term = term,
    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id, title = title, description = description, startAt = startAt,
    endAt = endAt, type = type, referenceId = referenceId, colorHex = colorHex
)

fun CalendarEvent.toEntity(now: Long = System.currentTimeMillis()) = CalendarEventEntity(
    id = id, title = title, description = description, startAt = startAt, endAt = endAt,
    type = type, referenceId = referenceId, colorHex = colorHex,
    createdAt = now, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun QuizQuestionEntity.toDomain() = QuizQuestion(
    id = id, quizId = quizId, type = QuestionType.valueOf(type),
    question = question, options = json.decodeFromString<List<String>>(optionsJson),
    correctAnswer = correctAnswer
)

fun QuizEntity.toDomain(questions: List<QuizQuestion> = emptyList()) = Quiz(
    id = id,
    title = title,
    subjectId = subjectId,
    sourceNoteId = sourceNoteId,
    questions = questions,
    createdAt = createdAt,
)

fun Quiz.toEntity(now: Long = System.currentTimeMillis()) = QuizEntity(
    id = id, title = title, subjectId = subjectId, sourceNoteId = sourceNoteId,
    createdAt = createdAt, updatedAt = now, deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
)

fun QuizQuestion.toEntity() = QuizQuestionEntity(
    id = id, quizId = quizId, type = type.name, question = question,
    optionsJson = json.encodeToString(options), correctAnswer = correctAnswer
)

fun LectureFileEntity.toDomain() = LectureFile(
    id = id,
    subjectId = subjectId,
    title = title,
    fileUri = fileUri,
    mimeType = mimeType,
    createdAt = createdAt
)

fun LectureFile.toEntity() = LectureFileEntity(
    id = id,
    subjectId = subjectId,
    title = title,
    fileUri = fileUri,
    mimeType = mimeType,
    createdAt = createdAt
)
