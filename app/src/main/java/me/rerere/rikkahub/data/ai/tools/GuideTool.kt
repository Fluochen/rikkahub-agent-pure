package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.guide.AgentGuide

/**
 * Name of the guide tool, shared by the registration site, the ungrouped-tool filter, and the
 * chat surface that must not catalogue a second copy of it.
 */
const val MOXW_GUIDE_TOOL_NAME = "moxw_guide"

/**
 * `moxw_guide` — this app's operating manual, served on demand.
 *
 * ## Why a tool and not prompt text
 *
 * The rules that must hold on every turn are injected (`SystemInjections`). The depth — how a
 * mission resumes, which tool records audio, where a deliverable goes, what a fresh install is
 * still missing — matters once per task, so it is a tool call the model makes when the task
 * needs it. Putting this text in the system prompt would pay for it on every request, which is
 * exactly the trade a hand-written 2,000-word prompt made by accident.
 *
 * ## Registration
 *
 * Always on, never a per-assistant toggle — the same treatment `check_app_updates` gets, and for
 * the same reason: it is part of the app rather than an optional capability. It is therefore in
 * [LocalToolFilter.UNGROUPED_TOOL_NAMES], so no group inventory claims it and no stored
 * `disabledLocalTools` entry can remove it. The `CLIENT` injection pack names it, which is only
 * honest while that unconditional registration holds.
 */
fun moxwGuideTool(): Tool = Tool(
    name = MOXW_GUIDE_TOOL_NAME,
    description = """
        Read this app's operating manual. It covers how the client is put together (chat, missions,
        schedules, sub-agents, skills and workflows, memory, files), how to drive the phone with
        the device tools, which tool to reach for in each situation, and what a fresh install
        still needs before a capability will work. Call it before asking the user how the app
        works, before reporting that a capability is missing, or when the right tool for a job is
        not obvious. Omit the topic to get the table of contents — one line per chapter — and
        call it again with the chapter you need.
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put(
                    "topic",
                    buildJsonObject {
                        put("type", "string")
                        put("description", "Chapter id from the table of contents. Omit for the index.")
                        put(
                            "enum",
                            buildJsonArray { AgentGuide.ALL.forEach { add(it.id) } },
                        )
                    },
                )
            },
        )
    },
    execute = {
        val topic = it.jsonObject["topic"]?.jsonPrimitive?.contentOrNull
        listOf(UIMessagePart.Text(AgentGuide.render(topic)))
    },
)
