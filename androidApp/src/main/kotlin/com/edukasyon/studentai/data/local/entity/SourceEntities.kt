package com.edukasyon.studentai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-owned citation source (note text, uploaded doc). Each user embeds
 * and searches only their own rows ([uid] scoping).
 */
@Entity(
    tableName = "sources",
    indices = [Index("uid"), Index("updatedAt")]
)
data class SourceEntity(
    @PrimaryKey val id: String,
    val uid: String,
    val name: String,
    val mime: String,
    val charCount: Int,
    val chunkCount: Int,
    val embeddingModel: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

/**
 * One retrievable passage. [vector] is a little-endian float array BLOB
 * (dims from the embedding model, e.g. 768); null until embedded.
 */
@Entity(
    tableName = "source_chunks",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceId"), Index("uid"), Index("sourceId", "ordinal")]
)
data class SourceChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val uid: String,
    val ordinal: Int,
    val text: String,
    val vector: ByteArray?,
    val dims: Int,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourceChunkEntity) return false
        return id == other.id && sourceId == other.sourceId && ordinal == other.ordinal &&
            text == other.text && dims == other.dims
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + ordinal
        return result
    }
}
