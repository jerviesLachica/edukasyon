package com.edukasyon.studentai.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS page_note_cache (
                sha256 TEXT NOT NULL PRIMARY KEY,
                markdown TEXT NOT NULL,
                pageNum INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )"""
        )
        // No index on pageNum: PageNoteCacheEntity declares none, and Room's
        // post-migration validation compares against the entity — creating
        // index_page_note_cache_pageNum here made every open-from-12 migration
        // fail validation and crash the app on first DB query (Schedule/Planner).
    }
}
