package me.rerere.rikkahub.runtime

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.runtime.util.JsonInstantPretty

/**
 * The `<memories>` block.
 *
 * [omitted] is the number of live records the injection rules left out (over budget, or
 * expired). Saying so is the whole point: a model that believes this list is exhaustive will
 * answer "you never told me that" instead of admitting it cannot see a record — and the user,
 * who can see the record in the app, will conclude the assistant is ignoring them.
 */
internal fun buildMemoryPrompt(memories: List<LoopMemory>, omitted: Int = 0) =
    buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        append("These are memories stored via the memory_tool that you can reference in future conversations.")
        appendLine()
        val json = buildJsonArray {
            memories.forEach { memory ->
                add(buildJsonObject {
                    put("id", memory.id)
                    put("content", memory.content)
                })
            }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
        if (omitted > 0) {
            append("($omitted more memories are stored but are not listed above. Call memory_lookup ")
            append("to search them; if that finds nothing, say so instead of assuming it was never told.)")
            appendLine()
        }
    }

/**
 * The `<facts>` block: durable triples extracted from earlier conversations.
 *
 * Separate from `**Memories**` on purpose. Memory is what the model chose to write down, in its
 * own words; a fact is what a later pass could verify it had been told, with the triple that
 * makes superseding possible. Merging the two would mean one row cannot be updated without the
 * other, and they have completely different lifetimes.
 *
 * [omitted] carries the same promise as the memory block: the model must know the list is not
 * exhaustive, or it will deny knowledge it simply cannot see.
 */
internal fun buildFactsPrompt(facts: List<LoopFact>, omitted: Int = 0) =
    buildString {
        appendLine()
        append("**Known facts**")
        appendLine()
        append("Durable facts from earlier conversations. Each is s=subject, p=predicate, o=object.")
        appendLine()
        val json = buildJsonArray {
            facts.forEach { fact ->
                add(buildJsonObject {
                    put("s", fact.subject)
                    put("p", fact.predicate)
                    put("o", fact.objectValue)
                })
            }
        }
        append(json.toString())
        appendLine()
        if (omitted > 0) {
            append("($omitted more facts are stored but not listed. Call memory_lookup to search them.)")
            appendLine()
        }
    }

/**
 * The "Recent Chats" block. The query itself is :app's job (LoopRecentChatsProvider);
 * this function only formats the rows — the date is pre-formatted by :app because its
 * util layer owns the localisation.
 */
internal fun buildRecentChatsPrompt(entries: List<RecentChatEntry>): String {
    if (entries.isEmpty()) return ""
    return buildString {
        appendLine()
        append("**Recent Chats**")
        appendLine()
        append("These are some of the user's recent conversations. You can use them to understand user preferences:")
        appendLine()
        val json = buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("title", entry.title)
                    put("last_chat", entry.lastChatDate)
                })
            }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
    }
}
