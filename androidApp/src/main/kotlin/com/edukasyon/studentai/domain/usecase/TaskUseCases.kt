package com.edukasyon.studentai.domain.usecase

import com.edukasyon.studentai.domain.model.Subtask
import com.edukasyon.studentai.domain.model.Task
import kotlinx.coroutines.flow.first
import com.edukasyon.studentai.domain.repository.TaskRepository
import javax.inject.Inject

data class DeleteSubtaskParams(val taskId: String, val subtaskId: String)

class GetAllTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Unit, List<Task>> {
    override suspend fun execute(params: Unit): List<Task> = taskRepository.observeTasks().first()
}

class GetUpcomingTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Int, List<Task>> {
    override suspend fun execute(params: Int): List<Task> = taskRepository.observeUpcoming(params).first()
}

class CreateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Task, Unit> {
    override suspend fun execute(params: Task): Unit = taskRepository.createTask(params)
}

class UpdateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Task, Unit> {
    override suspend fun execute(params: Task): Unit = taskRepository.updateTask(params)
}

class CompleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = taskRepository.completeTask(params)
}

class UncompleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = taskRepository.uncompleteTask(params)
}

class InsertSubtaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Subtask, Unit> {
    override suspend fun execute(params: Subtask): Unit = taskRepository.insertSubtask(params)
}

class UpdateSubtaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<Subtask, Unit> {
    override suspend fun execute(params: Subtask): Unit = taskRepository.updateSubtask(params)
}

class DeleteSubtaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<DeleteSubtaskParams, Unit> {
    override suspend fun execute(params: DeleteSubtaskParams): Unit =
        taskRepository.deleteSubtask(params.taskId, params.subtaskId)
}

class DeleteTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<String, Unit> {
    override suspend fun execute(params: String): Unit = taskRepository.deleteTask(params)
}

class SearchTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) : UseCase<String, List<Task>> {
    override suspend fun execute(params: String): List<Task> = taskRepository.search(params).first()
}