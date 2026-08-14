package com.edukasyon.studentai.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edukasyon.studentai.data.local.StudentAiDatabase
import com.edukasyon.studentai.data.local.entity.SubtaskEntity
import com.edukasyon.studentai.data.local.entity.TaskEntity
import com.edukasyon.studentai.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskSubtaskPersistenceTest {

    private lateinit var db: StudentAiDatabase
    private val taskId = "task-1"
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudentAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            db.taskDao().insert(
                TaskEntity(
                    id = taskId,
                    title = "Essay",
                    description = null,
                    subjectId = null,
                    priority = "MEDIUM",
                    dueDate = now + 86_400_000L,
                    dueTime = null,
                    status = TaskStatus.COMPLETED.name,
                    category = null,
                    reminderAt = null,
                    createdAt = now,
                    updatedAt = now,
                    completedAt = now,
                    deletedAt = null,
                    syncState = "LOCAL_ONLY",
                )
            )
            db.subtaskDao().insert(SubtaskEntity("sub-1", taskId, "Outline", true, 0))
            db.subtaskDao().insert(SubtaskEntity("sub-2", taskId, "Draft", false, 1))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun updateSubtask_preservesSiblingSubtasks() = runBlocking {
        db.subtaskDao().insert(SubtaskEntity("sub-1", taskId, "Outline", false, 0))

        val subtasks = db.subtaskDao().observeByTask(taskId).first()
        assertEquals(2, subtasks.size)
        assertFalse(subtasks.first { it.id == "sub-1" }.isCompleted)
        assertFalse(subtasks.first { it.id == "sub-2" }.isCompleted)
    }

    @Test
    fun updatingTaskAfterAddingSubtask_preservesNewSubtask() = runBlocking {
        db.subtaskDao().insert(SubtaskEntity("sub-3", taskId, "Revise", false, 2))

        val task = db.taskDao().observeAll().first().single()
        db.taskDao().insert(task.copy(updatedAt = now + 1))

        val subtasks = db.subtaskDao().observeByTask(taskId).first()
        assertEquals(3, subtasks.size)
        assertTrue(subtasks.any { it.id == "sub-3" && it.title == "Revise" })
    }

    @Test
    fun uncompleteTaskStatusChange_preservesSubtasks() = runBlocking {
        val entity = db.taskDao().observeAll().first().single()
        db.taskDao().insert(
            entity.copy(
                status = TaskStatus.PENDING.name,
                completedAt = null,
                updatedAt = now + 1,
            )
        )

        val subtasks = db.subtaskDao().observeByTask(taskId).first()
        assertEquals(2, subtasks.size)
        assertTrue(subtasks.any { it.id == "sub-1" })
        assertTrue(subtasks.any { it.id == "sub-2" })
    }
}
