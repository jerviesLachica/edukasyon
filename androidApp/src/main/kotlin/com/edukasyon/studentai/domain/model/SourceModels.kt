package com.edukasyon.studentai.domain.model

data class CitedSource(
    val id: String,
    val name: String,
    val chunkCount: Int,
    val updatedAt: Long
)

data class RankedChunk(
    val chunkId: Long,
    val sourceId: String,
    val sourceName: String,
    val ordinal: Int,
    val text: String,
    val score: Double
)
