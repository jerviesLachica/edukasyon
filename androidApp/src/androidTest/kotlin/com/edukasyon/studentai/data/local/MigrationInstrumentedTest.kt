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

    @Test
    fun migrate6To7_addsConversationMetadataColumns() {
        helper.createDatabase(testDb, 6).close()
        helper.runMigrationsAndValidate(testDb, 7, true, MIGRATION_6_7)
        helper.createDatabase(testDb, 7).use { db ->
            val cursor = db.query("PRAGMA table_info(conversations)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            assertTrue(columns.contains("conversationType"))
            assertTrue(columns.contains("backendConversationId"))
        }
    }

    @Test
    fun migrate7To8_createsJeviTablesAndDeckId() {
        helper.createDatabase(testDb, 7).apply {
            execSQL(
                """INSERT INTO flashcards (id, question, answer, subjectId, topic, difficulty,
                    reviewCount, correctCount, incorrectCount, lastReviewedAt, nextReviewAt,
                    easeFactor, intervalDays, createdAt, updatedAt, deletedAt, syncState)
                    VALUES ('fc1', 'Q', 'A', NULL, NULL, 'medium', 0, 0, 0, NULL, NULL, 2.5, 1, 0, 0, NULL, 'LOCAL_ONLY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 8, true, MIGRATION_7_8)
        helper.createDatabase(testDb, 8).use { db ->
            val deckCursor = db.query("SELECT id FROM jevi_decks WHERE id = 'jevi-default-deck'")
            assertTrue(deckCursor.moveToFirst())
            deckCursor.close()

            val fcCursor = db.query("SELECT deckId FROM flashcards WHERE id = 'fc1'")
            assertTrue(fcCursor.moveToFirst())
            assertTrue(fcCursor.getString(0) == "jevi-default-deck")
            fcCursor.close()

            val tableCursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='jevi_review_records'"
            )
            assertTrue(tableCursor.moveToFirst())
            tableCursor.close()
        }
    }

    @Test
    fun migrate8To9_addsProfileColumns() {
        helper.createDatabase(testDb, 8).apply {
            execSQL(
                """INSERT INTO users (id, displayName, email, school, gradeLevel, section, schoolYear,
                    semester, isGuest, avatarUri, createdAt, updatedAt, syncState)
                    VALUES ('u1', 'Jervies', NULL, 'Rtu', '3rd Year', 'A', '2025-2026', '1st', 1, NULL, 0, 0, 'LOCAL_ONLY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 9, true, MIGRATION_8_9)
        helper.createDatabase(testDb, 9).use { db ->
            val cursor = db.query("PRAGMA table_info(users)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            assertTrue(columns.contains("bio"))
            assertTrue(columns.contains("preferredStatus"))
            assertTrue(columns.contains("lastProfileEditAt"))
        }
    }

    @Test
    fun migrate9To10_addsExamLinkedDeckId() {
        helper.createDatabase(testDb, 9).apply {
            execSQL(
                """INSERT INTO exams (id, title, subjectId, examDate, examTime, location, coverage, notes,
                    reminderAt, createdAt, updatedAt, deletedAt, syncState)
                    VALUES ('e1', 'Database Exam', 'sub1', 0, NULL, NULL, NULL, NULL, NULL, 0, 0, NULL, 'LOCAL_ONLY')"""
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 10, true, MIGRATION_9_10)
        helper.createDatabase(testDb, 10).use { db ->
            val cursor = db.query("PRAGMA table_info(exams)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndex("name")))
            }
            cursor.close()
            assertTrue(columns.contains("linkedDeckId"))
        }
    }
}
