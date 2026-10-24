package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.mission.MissionCreationService
import kotlin.uuid.Uuid

/**
 * Model-initiated missions: the missing half of the workbench.
 *
 * Until now a long task could only start when the user opened the workbench and filled a form.
 * That is backwards for an agent: by the time the model has understood a request like "把这套
 * 稿子整理成电子书，做完发我" it is the actor that knows the work is multi-step. `mission_create`
 * lets it start the same first-class mission a human would, with a goal, acceptance criteria and
 * a workflow — and then answer the user in one line instead of pretending the whole thing will
 * happen inside this turn.
 *
 * Deliberate limits:
 * - Registered only outside a mission round, so a mission cannot spawn missions recursively.
 * - The tool asks for approval (when the user's approval mode asks at all), because starting a
 *   background run spends tokens after the current turn ends.
 * - It never binds the CURRENT conversation: a mission gets its own execution conversation, so
 *   launching it cannot hijack or race the turn that is still generating.
 */
fun createMissionCreateTool(
    creation: MissionCreationService,
    assistantId: Uuid,
    /** The assistant's workspace, if it has one; null lets the service use the default. */
    workspaceId: String?,
): Tool = Tool(
    name = "mission_create",
    description = """
        创建一个后台任务（mission）并立即开始第一轮。任务会拥有独立会话、工作区和时间线，可以在本轮对话结束后继续运行、自动续跑，并在完成时通知用户。

        什么时候用：
        - 用户要的是多步骤、耗时较长、需要持续产出或后台推进的工作（写作项目、调研报告、整套代码改造、批量整理、素材生产等）。
        - 工作在当前回合内明显做不完，或需要跨轮保持状态（文件、验收清单、进度）。
        - 用户明确说「开个任务」「后台做」「慢慢做」「做完告诉我」。

        什么时候不要用：
        - 一句话能答完的问题、单次查询、单次文件编辑。
        - 用户没有授权就开始消耗大量 token 的后台工作。

        调用后：用一两句话告诉用户任务标题、已开始（或未开始）、结果会出现在工作台；不要在正文里重复任务的全部细节。
    """.trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("title", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("任务标题，简短可辨识，例如「整理《XX》电子书」。"))
                })
                put("goal", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("要达成的结果，写清背景、范围、约束和交付物位置。"))
                })
                put("acceptance", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("description", JsonPrimitive("可勾选的验收标准，2-6 条；每条都要能被实际检查。"))
                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                })
                put("workflow", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive(
                        "可选。内置流程名（coding / bugfix / visual / writing / research / media / automation）、" +
                            "工作区里的流程文件名，或留空让执行轮自行选择。"
                    ))
                })
                put("start_now", buildJsonObject {
                    put("type", JsonPrimitive("boolean"))
                    put("description", JsonPrimitive("是否立即开始第一轮，默认 true。用户只想先立项时传 false。"))
                })
            },
            required = listOf("title", "goal"),
        )
    },
    // A background run outlives the turn; in SEMI/MANUAL modes the user should see it before it
    // starts spending. AUTO mode approves it like any other tool.
    needsApproval = { true },
    execute = { args ->
        val params = runCatching { args.jsonObject }.getOrNull()
        if (params == null) {
            return@Tool listOf(UIMessagePart.Text(errorJson("invalid_arguments", "arguments must be an object")))
        }
        val title = params["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val goal = params["goal"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (title.isBlank() || goal.isBlank()) {
            return@Tool listOf(
                UIMessagePart.Text(
                    errorJson("missing_fields", "title and goal are required and must not be blank")
                )
            )
        }
        val acceptance = params["acceptance"]
            ?.takeIf { it is JsonArray }
            ?.jsonArray
            ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val workflow = params["workflow"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        val startNow = params["start_now"]?.jsonPrimitive?.booleanOrNull ?: true

        val outcome = runCatching {
            creation.createAndStart(
                MissionCreationService.Request(
                    title = title,
                    goal = goal,
                    acceptance = acceptance,
                    workflow = workflow,
                    workspaceId = workspaceId,
                    assistantId = assistantId.toString(),
                    startNow = startNow,
                )
            )
        }.getOrElse { error ->
            return@Tool listOf(
                UIMessagePart.Text(
                    errorJson("mission_create_failed", error.message ?: error::class.simpleName.orEmpty())
                )
            )
        }

        val payload = buildJsonObject {
            put("mission_id", JsonPrimitive(outcome.mission.id))
            put("title", JsonPrimitive(outcome.mission.title))
            put("workspace_id", JsonPrimitive(outcome.workspaceId))
            put("started", JsonPrimitive(outcome.started))
            outcome.workflowPath?.let { put("workflow", JsonPrimitive(it)) }
            outcome.launchError?.let { put("launch_error", JsonPrimitive(it)) }
            put(
                "note",
                JsonPrimitive(
                    if (outcome.started) {
                        "任务已创建并开始第一轮，结果会出现在工作台；请用一两句话告知用户，不要重复细节。"
                    } else {
                        "任务已创建但未开始（start_now=false，或首轮启动失败）。请告知用户当前状态。"
                    }
                )
            )
        }
        listOf(UIMessagePart.Text(payload.toString()))
    },
)

private fun errorJson(code: String, detail: String): String = buildJsonObject {
    put("error", JsonPrimitive(code))
    put("detail", JsonPrimitive(detail))
}.toString()
