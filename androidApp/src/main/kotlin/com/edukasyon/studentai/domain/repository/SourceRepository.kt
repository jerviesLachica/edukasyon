package com.edukasyon.studentai.domain.repository

import com.edukasyon.studentai.domain.model.CitedSource
import com.edukasyon.studentai.domain.model.RankedChunk
import kotlinx.coroutines.flow.Flow

interface SourceRepository {
    fun observeSources(): Flow<List<CitedSource>>
    suspend fun ingestSource(name: String, mime: String, text: String): String
    suspend fun deleteSource(id: String)
    suspend fun retrieve(query: String, sourceIds: Set<String>? = null, topK: Int = 5): List<RankedChunk>
}
