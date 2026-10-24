package me.rerere.rikkahub.runtime.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

/**
 * `memory_lookup` — finds stored memories and facts that are not in the prompt right now.
 *
 * ## Why this exists
 *
 * Injection is budgeted (8K characters of memories, 12 facts). Everything past the budget is
 * stored, shown in the app, and told to the model only as a count — so without a read path those
 * records are effectively lost: the model knows they exist and can never see them. This is that
 * read path, and it is also what makes the budgets safe to keep tight.
 *
 * ## Why a separate tool from `memory_tool`
 *
 * `memory_tool` writes (create / edit / delete) and its schema carries the write arguments.
 * Folding a query in would mean every write call also pays for the query fields, and the model
 * would have to choose between "record this" and "look this up" in one enum. Two verbs, two
 * tools.
 *
 * ## Why not reuse `memory_search`
 *
 * That name belongs to cold memory (the Markdown knowledge base). Confusing the two would send
 * the model to a search over documents when it meant a fact someone told it once.
 */
fun buildMemoryLookupTool(
    search: suspend (query: String, limit: Int) -> List<MemoryLookupHit>,
): Tool = Tool(
    name = "memory_lookup",
    description = """
        Find stored memories and extracted facts that are not currently visible in the prompt.

        Use it when the user refers to something from an earlier conversation that you cannot
        see — a preference, a decision, a machine, an ID — or when the memories block says some
        records were not listed. Prefer it over guessing, and over asking the user to repeat
        themselves.

        - Searches both stores: `memories` (free text, writable with `memory_tool`) and `facts`
          (extracted triples, read-only).
        - Matching is by meaning when an embedding model is installed, and by words otherwise.
          `score` compares within one result, never across calls.
        - An empty result means nothing stored matches; it does not mean the user never said it.
        - This only reads. To store something, use `memory_tool`.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        "What to look for, in the user's own words. A few keywords beat a full " +
                            "sentence."
                    )
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Maximum hits, 1..${MemoryLookupRules.MAX_LIMIT}. " +
                            "Default ${MemoryLookupRules.DEFAULT_LIMIT}."
                    )
                })
            },
            required = listOf("query"),
        )
    },
    execute = { arguments ->
        val params = runCatching { arguments.jsonObject }.getOrNull()
        val query = params?.get("query")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (query.isEmpty()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", "missing_query")
                        put("detail", "Pass a non-empty `query` string.")
                    }.toString()
                )
            )
        }
        val limit = params?.get("limit")?.jsonPrimitive?.intOrNull
            ?: MemoryLookupRules.DEFAULT_LIMIT

        val hits = search(query, limit)
        listOf(UIMessagePart.Text(renderLookup(query, hits)))
    },
)

/** Renders hits as two labelled arrays, so the model never confuses a triple with a sentence. */
internal fun renderLookup(query: String, hits: List<MemoryLookupHit>): String = buildJsonObject {
    put("query", query)
    put("memories", buildJsonArray {
        hits.filter { it.candidate.type == MemoryLookupType.MEMORY }.forEach { hit ->
            add(buildJsonObject {
                put("id", hit.candidate.id)
                hit.candidate.kind?.let { put("kind", it) }
                put("content", hit.candidate.text)
                put("score", JsonPrimitive(roundScore(hit.score)))
            })
        }
    })
    put("facts", buildJsonArray {
        hits.filter { it.candidate.type == MemoryLookupType.FACT }.forEach { hit ->
            add(buildJsonObject {
                put("id", hit.candidate.id)
                put("s", hit.candidate.subject.orEmpty())
                put("p", hit.candidate.predicate.orEmpty())
                put("o", hit.candidate.objectValue.orEmpty())
                put("score", JsonPrimitive(roundScore(hit.score)))
            })
        }
    })
    if (hits.isEmpty()) {
        // Explicit rather than an empty pair of arrays: "nothing matched" and "the tool is
        // broken" must not look the same to a model deciding whether to ask the user.
        put(
            "note",
            "No stored memory or fact matches this query. Do not assume the user never said it; " +
                "ask them instead of inventing an answer."
        )
    }
}.toString()

/** Two decimals: enough to order the list, few enough that the number is not mistaken for a fact. */
private fun roundScore(score: Double): Double =
    kotlin.math.round(score * 100) / 100.0
