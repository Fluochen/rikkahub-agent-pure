package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.MemoryFactEntity

/**
 * Reads and writes the bi-temporal fact table.
 *
 * There is deliberately no `@Update`: a fact is only ever *invalidated* and re-inserted, so the
 * history stays intact. `upsert` in `MemoryFactRepository` owns that transaction.
 */
@Dao
interface MemoryFactDAO {

    /** Live facts, newest first — what the prompt may assert right now. */
    @Query(
        "SELECT * FROM memory_fact WHERE assistant_id = :assistantId AND valid_to IS NULL " +
            "ORDER BY valid_from DESC, id DESC LIMIT :limit"
    )
    suspend fun getLiveFacts(assistantId: String, limit: Int): List<MemoryFactEntity>

    /** The live row that a new (subject, predicate) would supersede, if any. */
    @Query(
        "SELECT * FROM memory_fact WHERE assistant_id = :assistantId AND subject = :subject " +
            "AND predicate = :predicate AND valid_to IS NULL ORDER BY id DESC LIMIT 1"
    )
    suspend fun findLive(assistantId: String, subject: String, predicate: String): MemoryFactEntity?

    @Query("UPDATE memory_fact SET valid_to = :validTo WHERE id = :id")
    suspend fun invalidate(id: Int, validTo: Long)

    @Insert
    suspend fun insert(fact: MemoryFactEntity): Long

    @Query("SELECT COUNT(*) FROM memory_fact WHERE assistant_id = :assistantId AND valid_to IS NULL")
    suspend fun countLive(assistantId: String): Int

    /** Used when an assistant's memory is cleared wholesale. */
    @Query("DELETE FROM memory_fact WHERE assistant_id = :assistantId")
    suspend fun deleteAllFor(assistantId: String)
}
