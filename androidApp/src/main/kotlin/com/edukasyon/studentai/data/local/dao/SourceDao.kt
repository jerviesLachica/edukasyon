package com.edukasyon.studentai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.edukasyon.studentai.data.local.entity.SourceChunkEntity
import com.edukasyon.studentai.data.local.entity.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<SourceChunkEntity>)

    @Query("SELECT * FROM sources WHERE uid = :uid AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeSources(uid: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE id = :id AND uid = :uid AND deletedAt IS NULL LIMIT 1")
    suspend fun getSource(id: String, uid: String): SourceEntity?

    @Query("UPDATE sources SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id AND uid = :uid")
    suspend fun softDeleteSource(id: String, uid: String, deletedAt: Long, updatedAt: Long)

    @Query("SELECT * FROM source_chunks WHERE uid = :uid AND vector IS NOT NULL")
    suspend fun getEmbeddedChunks(uid: String): List<SourceChunkEntity>

    @Query("SELECT * FROM source_chunks WHERE sourceId = :sourceId AND uid = :uid ORDER BY ordinal")
    suspend fun getChunksForSource(sourceId: String, uid: String): List<SourceChunkEntity>

    @Query("DELETE FROM source_chunks WHERE sourceId = :sourceId AND uid = :uid")
    suspend fun deleteChunksForSource(sourceId: String, uid: String)
}
