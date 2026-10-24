package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One durable fact extracted from a conversation summary, stored bi-temporally.
 *
 * ## Why a triple and not a sentence
 *
 * The only operation that matters is **superseding**. When the user moves, "user lives_in
 * Shanghai" must replace "user lives_in Beijing"; two sentences cannot be compared, but two
 * normalised triples can. `(subject, predicate)` is the identity, the object is the claim.
 *
 * ## Why nothing is ever overwritten
 *
 * A change invalidates the old row (`valid_to = now`) and inserts a new one. That keeps two
 * properties that matter and that an UPDATE would destroy:
 *
 *  - only one row per `(assistant, subject, predicate)` has `valid_to IS NULL`, so the model is
 *    never told two contradictory things at once;
 *  - the history stays answerable ("what did we believe last week"), which is the whole point of
 *    the second timeline.
 *
 * `valid_from` is *world* time (when the claim became true, as far as we know) and
 * `created_at` is *ingestion* time (when this app learned it). They differ exactly when a
 * conversation reports something that happened earlier.
 */
@Entity(
    tableName = "memory_fact",
    indices = [
        // The injection query: live rows of one assistant, newest first.
        Index(value = ["assistant_id", "valid_to"]),
        // The supersede query: is there a live row for this identity?
        Index(value = ["assistant_id", "subject", "predicate"]),
    ],
)
data class MemoryFactEntity(
    @PrimaryKey(true)
    val id: Int = 0,
    @ColumnInfo("assistant_id")
    val assistantId: String,
    /** Lookup key: lowercased on write (see MemoryFactRules.normalizeSubject). */
    @ColumnInfo("subject")
    val subject: String,
    /** Lookup key: snake_case, lowercased on write. */
    @ColumnInfo("predicate")
    val predicate: String,
    /** The claim itself. Case is preserved — it can be a header name or an ID. */
    @ColumnInfo("object_value")
    val objectValue: String,
    @ColumnInfo("confidence")
    val confidence: Double = 0.0,
    /** Which conversation produced it; null only for rows written before this column existed. */
    @ColumnInfo("source_conversation_id")
    val sourceConversationId: String? = null,
    @ColumnInfo("valid_from")
    val validFrom: Long,
    /** Null = still believed. Non-null = superseded at that instant. */
    @ColumnInfo("valid_to")
    val validTo: Long? = null,
    @ColumnInfo("created_at")
    val createdAt: Long,
)
