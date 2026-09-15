package com.edukasyon.studentai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.edukasyon.studentai.data.local.entity.PageNoteCacheEntity

/**
 * DAO for the SHA256-keyed page note cache.
 * On cache hit, zero AI calls are made for re-imports of identical documents.
 */
@Dao
interface PageNoteCacheDao {
    @Query("SELECT * FROM page_note_cache WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): PageNoteCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PageNoteCacheEntity)

    @Query("DELETE FROM page_note_cache WHERE sha256 = :sha256")
    suspend fun delete(sha256: String)

    @Query("DELETE FROM page_note_cache")
    suspend fun clearAll()
}
