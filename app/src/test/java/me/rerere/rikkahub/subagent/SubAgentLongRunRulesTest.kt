package me.rerere.rikkahub.subagent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长跑子代理的两条纯规则：续聊资格 / 续聊请求的继承，以及 fan-out 的解析与上限。
 *
 * 执行层（真正发消息、等生成）在 [SubAgentEngine] 里，依赖 Android，跑不了裸 JVM；
 * 所以把「能不能续」「怎么拆」全部挤到这里，让最容易出错的部分有测试。
 */
class SubAgentLongRunRulesTest {

    private fun run(
        status: SubAgentStatus = SubAgentStatus.SUCCEEDED,
        conversationId: String? = "conv-1",
        modelId: String? = "model-a",
        agentName: String? = "researcher",
        tools: List<String>? = listOf("web_search"),
        maxTrips: Int = 12,
        timeoutSeconds: Int = 300,
        reasoningLevel: String? = "medium",
        label: String = "调研",
    ) = SubAgentRun(
        id = "run-1",
        parentChatId = "parent-conv",
        conversationId = conversationId,
        agentName = agentName,
        parentAssistantId = "asst-1",
        label = label,
        task = "调研 X",
        modelId = modelId,
        tools = tools,
        runInBackground = false,
        timeoutSeconds = timeoutSeconds,
        maxTrips = maxTrips,
        reasoningLevel = reasoningLevel,
        status = status,
        startedAtMs = 1_000L,
    )

    // ---------------------------------------------------------------- resume verdict

    @Test
    fun `a finished run with a conversation can be continued`() {
        assertEquals(SubAgentResumeRules.Verdict.Ok, SubAgentResumeRules.canResume(run()))
        assertEquals(
            SubAgentResumeRules.Verdict.Ok,
            SubAgentResumeRules.canResume(run(status = SubAgentStatus.FAILED)),
        )
        assertEquals(
            SubAgentResumeRules.Verdict.Ok,
            SubAgentResumeRules.canResume(run(status = SubAgentStatus.TIMED_OUT)),
        )
    }

    @Test
    fun `a running run cannot be interleaved`() {
        for (status in listOf(SubAgentStatus.PENDING, SubAgentStatus.RUNNING)) {
            val verdict = SubAgentResumeRules.canResume(run(status = status))
            assertTrue("$status must be refused", verdict is SubAgentResumeRules.Verdict.Refused)
            assertEquals("not_terminal", (verdict as SubAgentResumeRules.Verdict.Refused).code)
        }
    }

    @Test
    fun `a cancelled run stays cancelled`() {
        val verdict = SubAgentResumeRules.canResume(run(status = SubAgentStatus.CANCELLED))
        assertEquals("not_terminal", (verdict as SubAgentResumeRules.Verdict.Refused).code)
    }

    @Test
    fun `a run without a conversation is refused with its own reason`() {
        val verdict = SubAgentResumeRules.canResume(run(conversationId = null))
        assertEquals("no_conversation", (verdict as SubAgentResumeRules.Verdict.Refused).code)
    }

    // ---------------------------------------------------------------- resume request

    @Test
    fun `resume inherits model expert tools trips and reasoning`() {
        val request = SubAgentResumeRules.resumeRequest(run(), "把第 3 点展开")

        assertEquals("把第 3 点展开", request.task)
        assertEquals("model-a", request.modelId)
        assertEquals("researcher", request.agentName)
        assertEquals(listOf("web_search"), request.tools)
        assertEquals(12, request.maxTrips)
        assertEquals("medium", request.reasoningLevel)
        assertEquals("调研", request.label)
        // 续聊不重新引用父会话历史：自己的会话就是历史
        assertNull(request.contextRefs)
        assertFalse(request.attachParentMedia)
    }

    @Test
    fun `resume keeps the original timeout unless this message overrides it`() {
        assertEquals(300, SubAgentResumeRules.resumeRequest(run(), "x").timeoutSeconds)
        assertEquals(60, SubAgentResumeRules.resumeRequest(run(), "x", timeoutSeconds = 60).timeoutSeconds)
        assertTrue(SubAgentResumeRules.resumeRequest(run(), "x", runInBackground = true).runInBackground)
    }

    // ---------------------------------------------------------------- fan-out plan

    private fun tasksJson(vararg tasks: String) =
        Json.parseToJsonElement(tasks.joinToString(",", "[", "]")).jsonArray

    @Test
    fun `fanout rejects an empty or missing task list`() {
        val missing = SubAgentFanoutRules.plan(null, runInBackground = false)
        assertEquals("missing_tasks", (missing as SubAgentFanoutRules.Plan.Invalid).code)
        val empty = SubAgentFanoutRules.plan(tasksJson(), runInBackground = false)
        assertEquals("missing_tasks", (empty as SubAgentFanoutRules.Plan.Invalid).code)
    }

    @Test
    fun `fanout caps the number of tasks`() {
        val tooMany = (1..SubAgentFanoutRules.MAX_TASKS + 1)
            .map { "{\"task\":\"t$it\"}" }
            .toTypedArray()
        val plan = SubAgentFanoutRules.plan(tasksJson(*tooMany), runInBackground = false)
        assertEquals("too_many_tasks", (plan as SubAgentFanoutRules.Plan.Invalid).code)
    }

    @Test
    fun `fanout rejects a blank task instead of running a half batch`() {
        val plan = SubAgentFanoutRules.plan(
            tasksJson("{\"task\":\"  \"}", "{\"task\":\"ok\"}"),
            runInBackground = false,
        )
        val invalid = plan as SubAgentFanoutRules.Plan.Invalid
        assertEquals("invalid_task", invalid.code)
        assertTrue(invalid.reason.contains("tasks[0]"))
    }

    @Test
    fun `fanout rejects a non-object entry`() {
        val plan = SubAgentFanoutRules.plan(tasksJson("\"just a string\""), runInBackground = false)
        assertEquals("invalid_task", (plan as SubAgentFanoutRules.Plan.Invalid).code)
    }

    @Test
    fun `fanout parses per task overrides and falls back to the call defaults`() {
        val plan = SubAgentFanoutRules.plan(
            tasksJson(
                "{\"task\":\"审计模块 A\",\"label\":\"A\",\"agent\":\"auditor\",\"max_trips\":20}",
                "{\"task\":\"审计模块 B\"}",
            ),
            runInBackground = true,
            defaultTimeoutSeconds = 600,
            defaultMaxTrips = 15,
        )

        val requests = (plan as SubAgentFanoutRules.Plan.Ok).requests
        assertEquals(2, requests.size)
        assertEquals("A", requests[0].label)
        assertEquals("auditor", requests[0].agentName)
        assertEquals(20, requests[0].maxTrips)
        assertEquals(600, requests[0].timeoutSeconds)
        assertTrue(requests[0].runInBackground)
        assertEquals(15, requests[1].maxTrips)
        assertEquals(600, requests[1].timeoutSeconds)
    }

    @Test
    fun `fanout keeps a single task under the length ceiling`() {
        val plan = SubAgentFanoutRules.plan(
            tasksJson("{\"task\":\"${"x".repeat(SubAgentFanoutRules.MAX_TASK_CHARS + 1)}\"}"),
            runInBackground = false,
        )
        assertEquals("task_too_long", (plan as SubAgentFanoutRules.Plan.Invalid).code)
    }
}
