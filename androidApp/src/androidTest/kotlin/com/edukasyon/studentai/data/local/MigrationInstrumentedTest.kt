package com.edukasyon.studentai.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StudentAiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_addsFlashcardSm2Columns() {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                """INSERT INTO flashcards (id, question, answer, subjectId, topic, difficulty,
                    reviewCount, correctCount, incorrectCount, lastReviewedAt, nextReviewAt,
                    createdAt, updatedAt, deletedAt, syncState)
                    VALUES ('c1', 'Q', 'A', NULL, NULL, 'medium', 0, 0, 0, NULL, NULL, 0, 0, NULL, 'LOCAL_ONLY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 2, true, MIGRATION_1_2)
        helper.createDatabase(testDb, 2).use { db ->
            val cursor = db.query("PRAGMA table_info(flashcards)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            assertTrue(columns.contains("easeFactor"))
            assertTrue(columns.contains("intervalDays"))
        }
    }

    @Test
    fun migrate2To3_createsHolidayCacheTable() {
        helper.createDatabase(testDb, 2).close()
        helper.runMigrationsAndValidate(testDb, 3, true, MIGRATION_2_3)
        helper.createDatabase(testDb, 3).use { db ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='ph_holidays_cache'")
            assertTrue(cursor.moveToFirst())
            cursor.close()
        }
    }

    @Test
    fun migrate3To4_addsAssignmentScheduleColumns() {
        helper.createDatabase(testDb, 3).apply {
            execSQL(
                """INSERT INTO assignments (id, title, subjectId, description, dueDate, attachmentUri,
                    priority, status, grade, notes, createdAt, updatedAt, deletedAt, syncState)
                    VALUES ('a1', 'Essay', NULL, NULL, 1000, NULL, 'MEDIUM', 'PENDING', NULL, NULL, 0, 0, NULL, 'LOCAL_ONLY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 4, true, MIGRATION_3_4)
        helper.createDatabase(testDb, 4).use { db ->
            val cursor = db.query("PRAGMA table_info(assignments)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            assertTrue(columns.contains("dueTime"))
            assertTrue(columns.contains("reminderAt"))
        }
    }

    @Test
    fun migrate4To5_createsLectureFilesTable() {
        helper.createDatabase(testDb, 4).close()
        helper.runMigrationsAndValidate(testDb, 5, true, MIGRATION_4_5)
        helper.createDatabase(testDb, 5).use { db ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='lecture_files'")
            assertTrue(cursor.moveToFirst())
            cursor.close()
        }
    }

    @Test
    fun migrate5To6_ensuresHolidayCacheIndex() {
        helper.createDatabase(testDb, 5).close()
        helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_5_6)
        helper.createDatabase(testDb, 6).use { db ->
            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ph_holidays_cache_year'"
            )
            assertTrue(cursor.moveToFirst())
            cursor.close()
        }
    }
}
