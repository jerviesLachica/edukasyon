package com.edukasyon.studentai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SHA256-keyed cache for page note responses from the vision pipeline.
 * Identical page content (same SHA256) triggers zero AI calls on re-import.
 */
@Entity(tableName = "page_note_cache")
data class PageNoteCacheEntity(
    @PrimaryKey val sha256: String,
    val markdown: String,
    val pageNum: Int,
    val createdAt: Long,
)
