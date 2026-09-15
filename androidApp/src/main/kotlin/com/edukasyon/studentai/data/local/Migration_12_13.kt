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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_page_note_cache_pageNum ON page_note_cache(pageNum)")
    }
}
