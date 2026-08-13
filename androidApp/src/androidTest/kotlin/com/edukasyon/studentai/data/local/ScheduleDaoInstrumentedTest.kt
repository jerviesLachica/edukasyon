package com.edukasyon.studentai.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.edukasyon.studentai.data.local.entity.ScheduleItemEntity
import com.edukasyon.studentai.domain.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleDaoInstrumentedTest {

    private lateinit var db: StudentAiDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudentAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserveSchedule() = runTest {
        val entity = ScheduleItemEntity(
            id = "1", subjectId = null, subjectName = "Math", teacher = "Mr. A",
            room = "101", building = null, dayOfWeek = "MONDAY",
            startTime = "08:00", endTime = "09:00", colorHex = "#000",
            notes = null, semester = "1st", schoolYear = "2025-2026",
            isRecurring = true, createdAt = 0, updatedAt = 0,
            deletedAt = null, syncState = SyncState.LOCAL_ONLY.name
        )
        db.scheduleDao().insert(entity)
        val items = db.scheduleDao().observeAll().first()
        assertEquals(1, items.size)
        assertEquals("Math", items.first().subjectName)
    }
}
