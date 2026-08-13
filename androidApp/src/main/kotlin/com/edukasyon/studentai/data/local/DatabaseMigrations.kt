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
