package com.edukasyon.studentai.data.repository

import com.edukasyon.studentai.core.ai.SourceChunker
import com.edukasyon.studentai.core.ai.VectorCodecs
import com.edukasyon.studentai.core.firebase.FirebaseAuthManager
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.EmbedRequest
import com.edukasyon.studentai.data.local.dao.SourceDao
import com.edukasyon.studentai.data.local.entity.SourceChunkEntity
import com.edukasyon.studentai.data.local.entity.SourceEntity
import com.edukasyon.studentai.data.preferences.UserPreferences
import com.edukasyon.studentai.domain.model.CitedSource
import com.edukasyon.studentai.domain.model.RankedChunk
import com.edukasyon.studentai.domain.repository.SourceRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val api: AiApiService,
    private val authManager: FirebaseAuthManager,
    private val preferences: UserPreferences,
) : SourceRepository {

    private fun uid(): String = authManager.currentUserId ?: "local"

    override fun observeSources(): Flow<List<CitedSource>> {
        val id = uid()
        return sourceDao.observeSources(id).map { list ->
            list.map { CitedSource(it.id, it.name, it.chunkCount, it.updatedAt) }
        }.onStart { seedWelcomeSourceIfNeeded() }
    }

    /**
     * One-time starter source so the picker and citations are useful immediately:
     * 1 local source + auto web research yields citations [1]–[6]. Runs once per
     * install; deleting it never re-seeds. Text is persisted even when the
     * embedding call fails offline (vectors backfill on next ingest).
     */
    private suspend fun seedWelcomeSourceIfNeeded() {
        if (preferences.sourcesSeeded.first()) return
        val id = uid()
        if (sourceDao.observeSources(id).first().isNotEmpty()) {
            preferences.setSourcesSeeded()
            return
        }
        runCatching { ingestSource(WELCOME_NAME, "text/plain", WELCOME_TEXT) }
        preferences.setSourcesSeeded()
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

    override suspend fun chunksForSource(sourceId: String): List<RankedChunk> {
        val uid = uid()
        val name = sourceDao.getSource(sourceId, uid)?.name ?: "Source"
        return sourceDao.getChunksForSource(sourceId, uid).map { row ->
            RankedChunk(
                chunkId = row.id, sourceId = row.sourceId, sourceName = name,
                ordinal = row.ordinal, text = row.text, score = 1.0
            )
        }
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

    companion object {
        const val WELCOME_NAME = "Welcome to JEVI Sources"
        val WELCOME_TEXT = """
            JEVI answers using your sources. Add class notes, chapters, or pasted text with Add Source, then tap a chip to include or exclude it; JEVI reads the selected ones before answering.
            Factual claims carry numbered citations like [1] that point back to these sources. Tap a citation to read the exact passage it came from.
            JEVI also searches the web automatically for every question. Web citations show a link icon and open in your browser when tapped, so local notes and fresh web results appear side by side.
            """.trimIndent()
    }
}
