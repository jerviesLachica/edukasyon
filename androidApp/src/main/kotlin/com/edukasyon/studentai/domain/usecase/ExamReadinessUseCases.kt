package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.core.study.ExamReadinessCalculator
import com.edukasyon.studentai.core.util.DateUtils
import com.edukasyon.studentai.core.util.SubjectPickerMerger
import com.edukasyon.studentai.data.local.dao.JeviDeckDao
import com.edukasyon.studentai.data.preferences.FocusPreferences
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.ExamReadiness
import com.edukasyon.studentai.domain.model.ExamReadinessContext
import com.edukasyon.studentai.domain.model.ExamReadinessMode
import com.edukasyon.studentai.domain.model.ExamReadinessNote
import com.edukasyon.studentai.domain.model.ExamReadinessStatus
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.Subject
import com.edukasyon.studentai.domain.repository.ExamRepository
import com.edukasyon.studentai.domain.repository.JeviRepository
import com.edukasyon.studentai.domain.repository.NoteRepository
import com.edukasyon.studentai.domain.repository.QuizRepository
import com.edukasyon.studentai.domain.repository.ScheduleRepository
import com.edukasyon.studentai.domain.repository.SubjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ComputeExamReadinessUseCase @Inject constructor(
    private val jeviRepository: JeviRepository,
    private val jeviDeckDao: JeviDeckDao,
    private val noteRepository: NoteRepository,
    private val quizRepository: QuizRepository,
    private val focusPreferences: FocusPreferences,
) {
    suspend fun resolveDeckId(exam: Exam): String? {
        exam.linkedDeckId?.let { return it }
        val subjectId = exam.subjectId ?: return null
        return jeviDeckDao.getFirstBySubject(subjectId)?.id
    }

    fun observeForExam(
        exam: Exam,
        subjectName: String? = null,
    ): Flow<ExamReadiness> {
        val deckIdFlow = when {
            exam.linkedDeckId != null -> flowOf(exam.linkedDeckId)
            exam.subjectId != null -> jeviDeckDao.observeFirstBySubject(exam.subjectId).map { it?.id }
            else -> flowOf(null)
        }

        return deckIdFlow.flatMapLatest { deckId ->
            if (deckId == null) {
                flowOf(unlinkedReadiness(exam, subjectName))
            } else {
                combine(
                    jeviRepository.observeDeck(deckId),
                    jeviRepository.observeDeckFlashcards(deckId),
                    noteRepository.observeNotes(),
                    quizRepository.observeByDeck(deckId),
                    focusPreferences.sessionHistory,
                ) { deck, cards, allNotes, quizzes, focusSessions ->
                    val subjectNotes = allNotes
                        .filter { note ->
                            exam.subjectId != null && note.subjectId == exam.subjectId
                        }
                        .map { note ->
                            ExamReadinessNote(
                                title = note.title,
                                tags = note.tags,
                                updatedAt = note.updatedAt,
                            )
                        }

                    val focusMinutes = focusSessions
                        .filter { session ->
                            matchesSubject(session.subjectLabel, subjectName, exam.subjectId)
                        }
                        .sumOf { it.totalFocusMinutes }

                    val context = ExamReadinessContext(
                        daysUntilExam = DateUtils.daysUntil(exam.examDate),
                        subjectNotes = subjectNotes,
                        deckQuizCount = quizzes.size,
                        deckQuizQuestionCount = quizzes
                            .map { it.questions.size }
                            .filter { it > 0 }
                            .average()
                            .takeIf { !it.isNaN() }
                            ?.toInt()
                            ?: 0,
                        focusMinutesForSubject = focusMinutes,
                    )

                    buildReadiness(
                        exam = exam,
                        deckId = deckId,
                        deckTitle = deck?.title,
                        subjectName = subjectName,
                        cards = cards,
                        context = context,
                    )
                }
            }
        }
    }

    private fun unlinkedReadiness(exam: Exam, subjectName: String?): ExamReadiness =
        ExamReadiness(
            examId = exam.id,
            status = ExamReadinessStatus.UNLINKED,
            mode = ExamReadinessMode.UNLINKED,
            subjectId = exam.subjectId,
            subjectName = subjectName,
        )

    private fun buildReadiness(
        exam: Exam,
        deckId: String,
        deckTitle: String?,
        subjectName: String?,
        cards: List<Flashcard>,
        context: ExamReadinessContext,
    ): ExamReadiness {
        val now = System.currentTimeMillis()
        val dueCount = cards.count { it.nextReviewAt == null || it.nextReviewAt <= now }
        val mode = if (exam.linkedDeckId != null) ExamReadinessMode.MANUAL else ExamReadinessMode.AUTO

        if (cards.isEmpty() && context.subjectNotes.isEmpty()) {
            return ExamReadiness(
                examId = exam.id,
                status = ExamReadinessStatus.EMPTY_DECK,
                mode = mode,
                linkedDeckId = deckId,
                linkedDeckTitle = deckTitle,
                subjectId = exam.subjectId,
                subjectName = subjectName,
            )
        }

        val readinessPercent = ExamReadinessCalculator.computeReadinessPercent(cards, context, now)
        val (strong, moderate, weak) = ExamReadinessCalculator.classifyTopics(cards, context.subjectNotes)
        val recommendations = ExamReadinessCalculator.buildRecommendations(cards, weak, context, now)

        return ExamReadiness(
            examId = exam.id,
            status = ExamReadinessStatus.READY,
            mode = mode,
            readinessPercent = readinessPercent,
            strongTopics = strong,
            moderateTopics = moderate,
            weakTopics = weak,
            recommendations = recommendations,
            linkedDeckId = deckId,
            linkedDeckTitle = deckTitle,
            subjectId = exam.subjectId,
            subjectName = subjectName,
            totalCards = cards.size,
            dueCards = dueCount,
        )
    }

    private fun matchesSubject(
        sessionLabel: String?,
        subjectName: String?,
        subjectId: String?,
    ): Boolean {
        val label = sessionLabel?.trim()?.lowercase().orEmpty()
        if (label.isBlank()) return false
        subjectName?.trim()?.lowercase()?.let { name ->
            if (label == name || label.contains(name) || name.contains(label)) return true
        }
        subjectId?.trim()?.lowercase()?.let { id ->
            if (label == id) return true
        }
        return false
    }
}

class ObserveExamReadinessMapUseCase @Inject constructor(
    private val examRepository: ExamRepository,
    private val subjectRepository: SubjectRepository,
    private val computeExamReadiness: ComputeExamReadinessUseCase,
) {
    operator fun invoke(): Flow<Map<String, ExamReadiness>> =
        combine(
            examRepository.observeExams(),
            subjectRepository.observeSubjects(),
        ) { exams, subjects ->
            exams to subjects.associate { it.id to it.name }
        }.flatMapLatest { (exams, subjectNames) ->
            if (exams.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val flows = exams.map { exam ->
                    computeExamReadiness.observeForExam(
                        exam = exam,
                        subjectName = exam.subjectId?.let { subjectNames[it] },
                    ).map { exam.id to it }
                }
                combine(flows) { entries -> entries.toMap() }
            }
        }
}

class LinkExamStudyUseCase @Inject constructor(
    private val examRepository: ExamRepository,
    private val subjectRepository: SubjectRepository,
    private val scheduleRepository: ScheduleRepository,
    private val createJeviDeck: CreateJeviDeckUseCase,
) {
    suspend fun linkSubjectAndDeck(
        exam: Exam,
        subjectId: String,
        deckId: String?,
        newDeckTitle: String?,
    ) {
        val resolvedSubjectId = ensureSubjectExists(subjectId)
        val resolvedDeckId = when {
            !deckId.isNullOrBlank() -> deckId
            !newDeckTitle.isNullOrBlank() -> {
                val now = System.currentTimeMillis()
                val newDeckId = java.util.UUID.randomUUID().toString()
                createJeviDeck.invoke(
                    com.edukasyon.studentai.domain.model.JeviDeck(
                        id = newDeckId,
                        title = newDeckTitle.trim(),
                        description = "Study deck for ${exam.title}",
                        subjectId = resolvedSubjectId,
                        sourceNoteId = null,
                        colorHex = com.edukasyon.studentai.domain.model.JeviConstants.DEFAULT_DECK_COLOR,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                newDeckId
            }
            else -> null
        }

        examRepository.saveExam(
            exam.copy(
                subjectId = resolvedSubjectId,
                linkedDeckId = resolvedDeckId,
            )
        )
    }

    private suspend fun ensureSubjectExists(subjectId: String): String {
        subjectRepository.getById(subjectId)?.let { return it.id }

        val scheduleItems = scheduleRepository.observeSchedule().first()
        val scheduleMatch = scheduleItems.firstOrNull { item ->
            SubjectPickerMerger.stableSubjectIdForScheduleName(item.subjectName.trim()) == subjectId
        } ?: return subjectId

        val name = scheduleMatch.subjectName.trim()
        if (name.isBlank()) return subjectId

        subjectRepository.saveSubject(
            Subject(
                id = subjectId,
                name = name,
                code = null,
                teacher = scheduleMatch.teacher,
                colorHex = scheduleMatch.colorHex.ifBlank { "#3949AB" },
                semester = scheduleMatch.semester,
                schoolYear = scheduleMatch.schoolYear,
            ),
        )
        return subjectId
    }
}
