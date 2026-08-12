package com.edukasyon.studentai.domain.usecase

import kotlinx.coroutines.flow.first
import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.domain.model.CalendarEvent
import com.edukasyon.studentai.domain.model.Flashcard
import com.edukasyon.studentai.domain.model.GradeCategory
import com.edukasyon.studentai.domain.model.GradeEntry
import com.edukasyon.studentai.domain.model.Quiz
import com.edukasyon.studentai.domain.model.StudyPlan
import com.edukasyon.studentai.core.ai.StudyPlanContext
import com.edukasyon.studentai.domain.model.Subject
import com.edukasyon.studentai.domain.repository.CalendarRepository
import com.edukasyon.studentai.domain.repository.FlashcardRepository
import com.edukasyon.studentai.domain.repository.GradeRepository
import com.edukasyon.studentai.domain.repository.SubjectRepository
import com.edukasyon.studentai.domain.repository.SearchRepository
import javax.inject.Inject

class GetGradesUseCase @Inject constructor(
    private val gradeRepository: GradeRepository
) : UseCase<String?, List<GradeEntry>> {
    override suspend fun execute(params: String?): List<GradeEntry> = gradeRepository.observeGrades(params).first()
}

class SaveGradeUseCase @Inject constructor(
    private val gradeRepository: GradeRepository
) : UseCase<GradeEntry, Unit> {
    override suspend fun execute(params: GradeEntry): Unit = gradeRepository.saveGrade(params)
}

class DeleteGradeUseCase @Inject constructor(
    private val gradeRepository: GradeRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = gradeRepository.deleteGrade(params)
}

class CalculateWeightedGradeUseCase @Inject constructor(
    private val gradeRepository: GradeRepository
) : UseCase<List<GradeEntry>, Double> {
    override suspend fun execute(params: List<GradeEntry>): Double = gradeRepository.calculateWeightedGrade(params)
}

class GetAllSubjectsUseCase @Inject constructor(
    private val subjectRepository: SubjectRepository
) : UseCase<Unit, List<Subject>> {
    override suspend fun execute(params: Unit): List<Subject> = subjectRepository.observeSubjects().first()
}

class SaveSubjectUseCase @Inject constructor(
    private val subjectRepository: SubjectRepository
) : UseCase<Subject, Unit> {
    override suspend fun execute(params: Subject): Unit = subjectRepository.saveSubject(params)
}

class GetCalendarEventsUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository
) : UseCase<LongRange, List<CalendarEvent>> {
    override suspend fun execute(params: LongRange): List<CalendarEvent> = calendarRepository.observeEvents(params.start, params.endInclusive).first()
}

class SaveCalendarEventUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository
) : UseCase<CalendarEvent, Unit> {
    override suspend fun execute(params: CalendarEvent): Unit = calendarRepository.saveEvent(params)
}

class GetFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) : UseCase<Unit, List<Flashcard>> {
    override suspend fun execute(params: Unit): List<Flashcard> = flashcardRepository.observeFlashcards().first()
}

class SaveFlashcardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository
) : UseCase<List<Flashcard>, Unit> {
    override suspend fun execute(params: List<Flashcard>): Unit = flashcardRepository.saveFlashcards(params)
}

class AiChatUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<com.edukasyon.studentai.core.ai.AiChatRequest, com.edukasyon.studentai.core.ai.AiChatResponse> {
    override suspend fun execute(params: com.edukasyon.studentai.core.ai.AiChatRequest): com.edukasyon.studentai.core.ai.AiChatResponse = aiService.chat(params)
}

class AiSummarizeUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<String, String> {
    override suspend fun execute(params: String): String = aiService.summarize(params)
}

class AiGenerateFlashcardsUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<String, List<Flashcard>> {
    override suspend fun execute(params: String): List<Flashcard> = aiService.generateFlashcards(params)
}

class AiGenerateQuizUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<String, Quiz> {
    override suspend fun execute(params: String): Quiz = aiService.generateQuiz(params)
}

class AiGenerateStudyPlanUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<StudyPlanContext, StudyPlan> {
    override suspend fun execute(params: StudyPlanContext): StudyPlan = aiService.generateStudyPlan(params)
}

class AiAnalyzeScheduleUseCase @Inject constructor(
    private val aiService: AiService
) : UseCase<ByteArray, com.edukasyon.studentai.core.ai.ScheduleAnalysisResult> {
    override suspend fun execute(params: ByteArray): com.edukasyon.studentai.core.ai.ScheduleAnalysisResult = aiService.analyzeSchedule(params)
}

class GlobalSearchUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) : UseCase<String, Map<String, List<String>>> {
    override suspend fun execute(params: String): Map<String, List<String>> = searchRepository.globalSearch(params).first()
}

data class LongRange(val start: Long, val endInclusive: Long)