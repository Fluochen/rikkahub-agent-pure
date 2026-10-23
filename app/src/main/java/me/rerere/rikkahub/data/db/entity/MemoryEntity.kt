package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One hot-memory record — the short facts injected into every request.
 *
 * ## The columns beyond `content`
 *
 * A memory is not just text any more. It carries **what kind of thing it is** (which decides
 * how long it is worth repeating to the model), **when it was written** (so a transient state
 * ages out instead of being asserted months later), **an optional validity end** (a superseded
 * record stays readable but stops being injected), and **its embedding** (so the write gate can
 * recognise a reworded duplicate when no wording rule can).
 *
 * The embedding is stored on the row rather than in the vector index: the index is built for
 * documents that are chunked and searched, while hot memory is a few hundred short strings
 * compared pairwise on write. Keeping it here means the gate needs no second table, no source
 * lifecycle, and no re-index sweep on model change — a vector from another model simply does
 * not compare (see `MemoryWriteRules.cosine`).
 */
@Entity(indices = [Index(value = ["assistant_id"])])
data class MemoryEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    @ColumnInfo("content")
    val content: String = "",
    /**
     * `preference` / `fact` / `plan` / `state` / `note`. Free-form on purpose: unknown values
     * read back as `note` (see `MemoryKind.fromWire`), so adding a kind later needs no
     * migration.
     */
    @ColumnInfo("kind", defaultValue = "note")
    val kind: String = "note",
    /** Epoch millis. 0 means "written before this column existed" — treated as ancient. */
    @ColumnInfo("created_at", defaultValue = "0")
    val createdAt: Long = 0L,
    /** Epoch millis after which the record stops being injected. Null = still true. */
    @ColumnInfo("valid_to")
    val validTo: Long? = null,
    /** Encoded vector of [content] under [embeddingModel]. Null when none could be produced. */
    @ColumnInfo("embedding")
    val embedding: ByteArray? = null,
    /** Which embedding model produced [embedding]; vectors from different models never compare. */
    @ColumnInfo("embedding_model")
    val embeddingModel: String? = null,
) {
    // A data class with a ByteArray needs its own equality, or two identical rows with the same
    // vector compare unequal and every `assertEquals` in a test fails for the wrong reason.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryEntity) return false
        return id == other.id &&
            assistantId == other.assistantId &&
            content == other.content &&
            kind == other.kind &&
            createdAt == other.createdAt &&
            validTo == other.validTo &&
            embeddingModel == other.embeddingModel &&
            (embedding?.contentEquals(other.embedding) ?: (other.embedding == null))
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + assistantId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (validTo?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + (embeddingModel?.hashCode() ?: 0)
        return result
    }
}
