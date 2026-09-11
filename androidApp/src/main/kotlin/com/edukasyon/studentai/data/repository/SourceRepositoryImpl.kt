package com.edukasyon.studentai.data.repository

import com.edukasyon.studentai.core.ai.SourceChunker
import com.edukasyon.studentai.core.ai.VectorCodecs
import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.EmbedRequest
import com.edukasyon.studentai.data.local.dao.SourceDao
import com.edukasyon.studentai.data.local.entity.SourceChunkEntity
import com.edukasyon.studentai.data.local.entity.SourceEntity
import com.edukasyon.studentai.domain.model.CitedSource
import com.edukasyon.studentai.domain.model.RankedChunk
import com.edukasyon.studentai.domain.repository.SourceRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val api: AiApiService,
    private val authManager: FirebaseAuthManager,
) : SourceRepository {

    private fun uid(): String = authManager.currentUserId ?: "local"

    override fun observeSources(): Flow<List<CitedSource>> {
        val id = uid()
        return sourceDao.observeSources(id).map { list ->
            list.map { CitedSource(it.id, it.name, it.chunkCount, it.updatedAt) }
        }
    }

    override suspend fun ingestSource(name: String, mime: String, text: String): String {
        val id = uid()
        val chunks = SourceChunker.chunk(text)
        require(chunks.isNotEmpty()) { "Nothing to ingest" }
        val now = System.currentTimeMillis()
        val sourceId = UUID.randomUUID().toString()
        // Persist chunks first (vectors null) so a failed embed never loses text.
        sourceDao.insertSource(
            SourceEntity(
                id = sourceId, uid = id, name = name, mime = mime,
                charCount = text.length, chunkCount = chunks.size,
                embeddingModel = "gemini-embedding-001",
                createdAt = now, updatedAt = now, deletedAt = null
            )
        )
        sourceDao.insertChunks(
            chunks.mapIndexed { i, c ->
                SourceChunkEntity(
                    sourceId = sourceId, uid = id, ordinal = i,
                    text = c, vector = null, dims = 0, updatedAt = now
                )
            }
        )
        // Embed in endpoint-sized batches, then backfill vectors.
        chunks.chunked(50).forEach { batch ->
            val res = api.embed(EmbedRequest(batch))
            val rows = sourceDao.getChunksForSource(sourceId, id)
            rows.forEachIndexed { i, row ->
                val vec = res.vectors.getOrNull(i) ?: return@forEachIndexed
                sourceDao.insertChunks(
                    listOf(
                        row.copy(
                            vector = VectorCodecs.floatsToBytes(vec),
                            dims = vec.size, updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
        return sourceId
    }

    override suspend fun deleteSource(id: String) {
        val uid = uid()
        val now = System.currentTimeMillis()
        sourceDao.deleteChunksForSource(id, uid)
        sourceDao.softDeleteSource(id, uid, now, now)
    }

    override suspend fun retrieve(query: String, sourceIds: Set<String>?, topK: Int): List<RankedChunk> {
        val uid = uid()
        val qres = api.embed(EmbedRequest(listOf(query), "RETRIEVAL_QUERY"))
        val raw = qres.vectors.firstOrNull() ?: return emptyList()
        val qvec = VectorCodecs.bytesToFloats(VectorCodecs.floatsToBytes(raw))
        val names = sourceDao.observeSources(uid).first().associate { it.id to it.name }
        return sourceDao.getEmbeddedChunks(uid)
            .filter { row ->
                (sourceIds == null || row.sourceId in sourceIds) && row.vector != null
            }
            .map { row ->
                val score = VectorCodecs.cosine(qvec, VectorCodecs.bytesToFloats(row.vector!!))
                RankedChunk(
                    chunkId = row.id, sourceId = row.sourceId,
                    sourceName = names[row.sourceId] ?: "Source",
                    ordinal = row.ordinal, text = row.text, score = score
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }
}
