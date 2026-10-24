package me.rerere.rikkahub.data.repository

import android.util.Log
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.vector.EmbeddingCodec
import me.rerere.rikkahub.data.vector.EmbeddingService
import me.rerere.rikkahub.runtime.tools.MemoryKind
import me.rerere.rikkahub.runtime.tools.MemoryLookupCandidate
import me.rerere.rikkahub.runtime.tools.MemoryLookupHit
import me.rerere.rikkahub.runtime.tools.MemoryLookupRules
import me.rerere.rikkahub.runtime.tools.MemoryLookupType

/**
 * Reads across both memory stores for `memory_lookup`.
 *
 * ## Why one service instead of two tools
 *
 * The user does not distinguish "a thing I told you" from "a fact you extracted". A query like
 * "what did we decide about the gateway" has to be able to hit both, ranked together — two
 * separate searches would hand the model two lists and make it guess which one matters.
 *
 * ## What can actually be scored
 *
 * Hot memories carry a stored vector from the write gate (when a model was installed at write
 * time), so they compete semantically *and* by words. Facts carry none: they are short triples
 * whose subject and predicate are already keywords, and a per-triple vector would need its own
 * column, migration and re-embed sweep to retrieve things word matching already finds. So facts
 * are scored by words only — a deliberate trade, not an omission.
 *
 * The query is embedded with `embedQuery` (the model's query prefix, not the document prefix);
 * using the document side here would quietly lower the similarity of every hit.
 */
class MemoryLookupService(
    private val memoryRepository: MemoryRepository,
    private val factRepository: MemoryFactRepository,
    private val embeddings: EmbeddingService,
) {

    companion object {
        private const val TAG = "MemoryLookup"
    }

    suspend fun search(
        assistantId: String,
        memoryScopeId: String,
        query: String,
        limit: Int = MemoryLookupRules.DEFAULT_LIMIT,
        nowMs: Long = System.currentTimeMillis(),
    ): List<MemoryLookupHit> {
        if (query.isBlank()) return emptyList()

        // One forward pass, and only when a model is installed. Everything below still works
        // without it (word matching), so a failure here is not a failure of the lookup.
        val embedded = runCatching {
            val model = embeddings.ensureLoaded() ?: return@runCatching null
            model.fileName to embeddings.embedQuery(query)
        }.onFailure { error ->
            Log.w(TAG, "query embedding failed; falling back to word matching", error)
        }.getOrNull()
        val queryVector = embedded?.second
        val modelId = embedded?.first

        val candidates = buildList {
            memoryRepository.getLiveMemories(memoryScopeId, nowMs).forEach { entity ->
                add(
                    MemoryLookupCandidate(
                        type = MemoryLookupType.MEMORY,
                        id = entity.id,
                        text = entity.content,
                        kind = MemoryKind.fromWire(entity.kind).wire,
                        createdAtMs = entity.createdAt,
                        vector = storedVector(entity, modelId),
                    )
                )
            }
            factRepository.liveFacts(assistantId).forEach { fact ->
                add(
                    MemoryLookupCandidate(
                        type = MemoryLookupType.FACT,
                        id = fact.id,
                        text = "${fact.subject} ${fact.predicate} ${fact.objectValue}",
                        subject = fact.subject,
                        predicate = fact.predicate,
                        objectValue = fact.objectValue,
                        createdAtMs = fact.validFrom,
                    )
                )
            }
        }
        if (candidates.isEmpty()) return emptyList()

        return MemoryLookupRules.rank(
            candidates = candidates,
            query = query,
            queryVector = queryVector,
            limit = limit,
        )
    }

    /**
     * The row's stored vector, but only when it came from [modelId].
     *
     * Comparing vectors from two different models is meaningless — `cosine` returns 0.0 for a
     * width mismatch and something arbitrary for an equal-width pair — so a stale vector is
     * dropped rather than trusted. The row still competes on words.
     */
    private fun storedVector(entity: MemoryEntity, modelId: String?): FloatArray? {
        if (modelId == null || entity.embeddingModel != modelId) return null
        val blob = entity.embedding ?: return null
        return runCatching { EmbeddingCodec.decode(blob) }.getOrNull()
    }
}
