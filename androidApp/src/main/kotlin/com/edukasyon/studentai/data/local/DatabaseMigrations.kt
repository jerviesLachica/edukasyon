package com.edukasyon.studentai.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE flashcards ADD COLUMN easeFactor REAL NOT NULL DEFAULT 2.5")
        db.execSQL("ALTER TABLE flashcards ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS ph_holidays_cache (
                date TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                localName TEXT NOT NULL,
                type TEXT NOT NULL,
                year INTEGER NOT NULL,
                fetchedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ph_holidays_cache_year ON ph_holidays_cache(year)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assignments ADD COLUMN dueTime TEXT")
        db.execSQL("ALTER TABLE assignments ADD COLUMN reminderAt INTEGER")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS lecture_files (
                id TEXT NOT NULL PRIMARY KEY,
                subjectId TEXT,
                title TEXT NOT NULL,
                fileUri TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lecture_files_subjectId ON lecture_files(subjectId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lecture_files_createdAt ON lecture_files(createdAt)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Idempotent repair — ensures holiday cache index matches CachedHolidayEntity.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ph_holidays_cache_year ON ph_holidays_cache(year)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN conversationType TEXT")
        db.execSQL("ALTER TABLE conversations ADD COLUMN backendConversationId TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentName TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentIsImage INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN metadataJson TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS jevi_decks (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                subjectId TEXT,
                sourceNoteId TEXT,
                colorHex TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncState TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jevi_decks_subjectId ON jevi_decks(subjectId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jevi_decks_updatedAt ON jevi_decks(updatedAt)")

        db.execSQL("ALTER TABLE flashcards ADD COLUMN deckId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_deckId ON flashcards(deckId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_flashcards_nextReviewAt ON flashcards(nextReviewAt)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS jevi_review_records (
                id TEXT NOT NULL PRIMARY KEY,
                flashcardId TEXT NOT NULL,
                deckId TEXT,
                quality INTEGER NOT NULL,
                reviewedAt INTEGER NOT NULL,
                intervalBefore INTEGER NOT NULL,
                intervalAfter INTEGER NOT NULL,
                easeFactorAfter REAL NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jevi_review_records_flashcardId ON jevi_review_records(flashcardId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jevi_review_records_deckId ON jevi_review_records(deckId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_jevi_review_records_reviewedAt ON jevi_review_records(reviewedAt)")

        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT OR IGNORE INTO jevi_decks
                (id, title, description, subjectId, sourceNoteId, colorHex, createdAt, updatedAt, deletedAt, syncState)
                VALUES ('jevi-default-deck', 'General', 'Default deck for existing flashcards', NULL, NULL, '#6366F1', $now, $now, NULL, 'LOCAL_ONLY')"""
        )
        db.execSQL("UPDATE flashcards SET deckId = 'jevi-default-deck', updatedAt = $now WHERE deckId IS NULL AND deletedAt IS NULL")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN bio TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE users ADD COLUMN preferredStatus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE users ADD COLUMN lastProfileEditAt INTEGER")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exams ADD COLUMN linkedDeckId TEXT")
    }
}
