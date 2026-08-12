package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.domain.model.Assignment
import com.edukasyon.studentai.domain.model.Exam
import com.edukasyon.studentai.domain.model.Note
import kotlinx.coroutines.flow.first
import com.edukasyon.studentai.domain.repository.AssignmentRepository
import com.edukasyon.studentai.domain.repository.ExamRepository
import com.edukasyon.studentai.domain.repository.NoteRepository
import javax.inject.Inject

class GetAllAssignmentsUseCase @Inject constructor(
    private val assignmentRepository: AssignmentRepository
) : UseCase<Unit, List<Assignment>> {
    override suspend fun execute(params: Unit): List<Assignment> = assignmentRepository.observeAssignments().first()
}

class SaveAssignmentUseCase @Inject constructor(
    private val assignmentRepository: AssignmentRepository
) : UseCase<Assignment, Unit> {
    override suspend fun execute(params: Assignment): Unit = assignmentRepository.saveAssignment(params)
}

class DeleteAssignmentUseCase @Inject constructor(
    private val assignmentRepository: AssignmentRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = assignmentRepository.deleteAssignment(params)
}

class GetAllExamsUseCase @Inject constructor(
    private val examRepository: ExamRepository
) : UseCase<Unit, List<Exam>> {
    override suspend fun execute(params: Unit): List<Exam> = examRepository.observeExams().first()
}

class GetUpcomingExamsUseCase @Inject constructor(
    private val examRepository: ExamRepository
) : UseCase<Int, List<Exam>> {
    override suspend fun execute(params: Int): List<Exam> = examRepository.observeUpcoming(params).first()
}

class SaveExamUseCase @Inject constructor(
    private val examRepository: ExamRepository
) : UseCase<Exam, Unit> {
    override suspend fun execute(params: Exam): Unit = examRepository.saveExam(params)
}

class DeleteExamUseCase @Inject constructor(
    private val examRepository: ExamRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = examRepository.deleteExam(params)
}

class GetAllNotesUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) : UseCase<Unit, List<Note>> {
    override suspend fun execute(params: Unit): List<Note> = noteRepository.observeNotes().first()
}

class SaveNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) : UseCase<Note, Unit> {
    override suspend fun execute(params: Note): Unit = noteRepository.saveNote(params)
}

class DeleteNoteUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = noteRepository.deleteNote(params)
}

class SearchNotesUseCase @Inject constructor(
    private val noteRepository: NoteRepository
) : UseCase<String, List<Note>> {
    override suspend fun execute(params: String): List<Note> = noteRepository.search(params).first()
}