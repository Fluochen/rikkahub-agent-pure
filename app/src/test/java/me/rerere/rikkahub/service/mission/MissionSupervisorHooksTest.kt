package me.rerere.rikkahub.service.mission

import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.agentrun.AgentRunKind
import me.rerere.rikkahub.data.ai.tools.ConversationSystemAddendum
import me.rerere.rikkahub.data.mission.FakeMissionDao
import me.rerere.rikkahub.data.mission.MissionEntity
import me.rerere.rikkahub.data.mission.MissionExecutionState
import me.rerere.rikkahub.data.mission.MissionHook
import me.rerere.rikkahub.data.mission.MissionHookSpec
import me.rerere.rikkahub.data.mission.MissionHookWhen
import me.rerere.rikkahub.data.mission.MissionLimits
import me.rerere.rikkahub.data.mission.MissionRepository
import me.rerere.rikkahub.data.mission.MissionStatus
import me.rerere.rikkahub.data.runevent.FakeRunEventDao
import me.rerere.rikkahub.data.runevent.RunEventEntity
import me.rerere.rikkahub.data.runevent.RunEventRepository
import me.rerere.rikkahub.data.runevent.RunEventType
import me.rerere.rikkahub.runtime.util.JsonInstant
import me.rerere.rikkahub.service.run.RunHandle
import me.rerere.rikkahub.service.run.RunRequest
import me.rerere.rikkahub.service.run.RunResult
import me.rerere.rikkahub.service.run.RunService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CONV = "33333333-3333-3333-3333-333333333333"

/**
 * 批 F — 督导里的两扇门：完成前检查（拦住 done）与轮末检查（拦住续跑），外加失败后检查
 * 的输出进交接提示。
 *
 * 用的是和 MissionSupervisorTest 同一套真仓库 + 假依赖的搭法；区别只是这里会真的经过
 * [MissionHookExecutor]，用一个脚本表决定每条命令的退出码。
 */
class MissionSupervisorHooksTest {

    private class FakeRunService : RunService {
        val submitted = mutableListOf<RunRequest>()

        override suspend fun submit(request: RunRequest): RunHandle {
            submitted += request
            return RunHandle(
                id = "run-${submitted.size}",
                conversationId = request.conversationId ?: Uuid.random(),
                timeoutMs = request.timeoutMs,
                missionId = request.missionId,
            )
        }

        override suspend fun awaitTerminal(handle: RunHandle): RunResult =
            error("not exercised")

        override suspend fun cancel(handle: RunHandle) = Unit

        override suspend fun resume(runId: String): RunResult? = null

        override fun events(handle: RunHandle): Flow<List<RunEventEntity>> = flowOf(emptyList())
    }

    private class Fixture {
        val missionDao = FakeMissionDao()
        val runEvents = RunEventRepository(FakeRunEventDao())
        val missionRepo = MissionRepository(missionDao, runEvents)
        val runService = FakeRunService()
        val notifications = mutableListOf<Pair<String, String>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val statuses = mutableMapOf<String, String?>()

        /** 命令 → 结果。没配的命令一律通过（0）。 */
        val scripts = mutableMapOf<String, HookResult>()

        val launcher = MissionLauncher(
            missionRepository = missionRepo,
            runService = runService,
            runEventRepository = runEvents,
            assistantResolver = { Uuid.random() },
        )

        val supervisor = MissionSupervisor(
            missionRepository = missionRepo,
            launcher = launcher,
            runEventRepository = runEvents,
            runStatusProbe = MissionSupervisor.RunStatusProbe { runId -> statuses[runId] },
            runKindProbe = { AgentRunKind.Mission.wire },
            conversationProbe = object : MissionSupervisor.MissionConversationProbe {
                override suspend fun userSpokeLast(conversationId: String) = false
                override suspend fun lastAssistantText(conversationId: String): String? = "上一轮干到这里"
            },
            notify = { title, content -> notifications += title to content },
            appScope = scope,
            hookExecutor = MissionHookExecutor(
                shell = MissionHookShell { _, command, _ ->
                    scripts[command] ?: HookResult(exitCode = 0)
                },
            ),
        )

        fun cleanup() {
            scope.cancel()
            MissionSessions.unregister(CONV)
            ConversationSystemAddendum.clear(Uuid.parse(CONV))
        }

        /** Emit a mission_report signal for [runId]. */
        suspend fun signal(runId: String, missionId: String, title: String, payload: String) {
            runEvents.emit(
                type = RunEventType.NOTE,
                title = title,
                payloadJson = payload,
                missionId = missionId,
                conversationId = CONV,
                runId = runId,
            )
        }

        suspend fun finished(runId: String, missionId: String): RunEventEntity {
            runEvents.emit(
                type = RunEventType.RUN_FINISHED,
                title = "finished",
                missionId = missionId,
                runId = runId,
            )
            return MissionEventPicker.pick(runEvents, runId)
        }
    }

    private fun mission(
        id: String = "m1",
        limits: MissionLimits = MissionLimits(),
        workspaceId: String? = "ws1",
        resumeCount: Int = 0,
    ) = MissionEntity(
        id = id,
        title = "写周报",
        description = "总结本周进展",
        status = MissionStatus.ACTIVE.name,
        conversationId = CONV,
        workspaceId = workspaceId,
        limitsJson = JsonInstant.encodeToString(limits),
        resumeCount = resumeCount,
        executionState = MissionExecutionState.RUNNING.name,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun hooksLimits(
        roundEnd: String = "",
        beforeDone: String = "",
        afterFailure: String = "",
        maxCheckFailures: Int = 2,
        action: String = MissionHook.ACTION_BLOCK,
    ) = MissionLimits(
        maxCheckFailures = maxCheckFailures,
        hooks = MissionHookSpec.of(roundEnd, beforeDone, afterFailure, action),
    )

    // ------------------------------------------------------------------ 完成前检查

    @Test
    fun `failing before-done check refuses the completion and starts a rework round`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(
                mission(limits = hooksLimits(beforeDone = "./verify.sh", maxCheckFailures = 2))
            )
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./verify.sh"] = HookResult(exitCode = 1, stderr = "2 tests failed\nFAILED test_report")
            fx.signal("r1", "m1", "mission_report: done", """{"mission_signal":"done","note":"写完了"}""")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            val row = fx.missionDao.getById("m1")!!
            assertEquals("任务不能因为模型说 done 就关闭", MissionStatus.ACTIVE.name, row.status)
            assertEquals(1, fx.runService.submitted.size)
            val prompt = fx.runService.submitted.single().prompt
            assertTrue(prompt.contains("检查未通过"))
            assertTrue(prompt.contains("2 tests failed"))
            assertTrue(prompt.contains("./verify.sh"))
            assertTrue(
                "失败必须留在时间线上",
                fx.runEvents.observeByMission("m1").first()
                    .any { it.title == "check_failed" },
            )
        } finally {
            fx.cleanup()
        }
    }

    @Test
    fun `passing before-done check lets the mission complete`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(mission(limits = hooksLimits(beforeDone = "./verify.sh")))
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./verify.sh"] = HookResult(exitCode = 0, stdout = "all good")
            fx.signal("r1", "m1", "mission_report: done", """{"mission_signal":"done","note":"写完了"}""")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            assertEquals(MissionStatus.DONE.name, fx.missionDao.getById("m1")!!.status)
            assertTrue(fx.runService.submitted.isEmpty())
        } finally {
            fx.cleanup()
        }
    }

    @Test
    fun `warn hooks never block a completion`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(
                mission(
                    limits = hooksLimits(
                        beforeDone = "./verify.sh",
                        action = MissionHook.ACTION_WARN,
                    )
                )
            )
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./verify.sh"] = HookResult(exitCode = 1, stderr = "nope")
            fx.signal("r1", "m1", "mission_report: done", """{"mission_signal":"done","note":"写完了"}""")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            assertEquals(MissionStatus.DONE.name, fx.missionDao.getById("m1")!!.status)
            assertTrue(fx.runService.submitted.isEmpty())
        } finally {
            fx.cleanup()
        }
    }

    @Test
    fun `one failing check with zero rework budget parks the mission for the user`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(
                mission(limits = hooksLimits(beforeDone = "./verify.sh", maxCheckFailures = 0))
            )
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./verify.sh"] = HookResult(exitCode = 2, stderr = "broken")
            fx.signal("r1", "m1", "mission_report: done", """{"mission_signal":"done","note":"写完了"}""")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            val row = fx.missionDao.getById("m1")!!
            assertEquals(MissionStatus.REVIEW.name, row.status)
            assertEquals(MissionExecutionState.IDLE.name, row.executionState)
            assertTrue(fx.runService.submitted.isEmpty())
            assertTrue(fx.notifications.single().first.contains("待确认"))
        } finally {
            fx.cleanup()
        }
    }

    // ------------------------------------------------------------------ 轮末检查

    @Test
    fun `blocking round-end failure stops the mission without spending a resume round`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(mission(limits = hooksLimits(roundEnd = "./gate.sh")))
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./gate.sh"] = HookResult(exitCode = 1, stderr = "acceptance 3/4")
            fx.signal("r1", "m1", "launch", "{}")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            val row = fx.missionDao.getById("m1")!!
            assertEquals(MissionStatus.BLOCKED.name, row.status)
            assertEquals(MissionExecutionState.BLOCKED.name, row.executionState)
            assertEquals("被检查挡住不该消耗续跑轮次", 0, row.resumeCount)
            assertTrue(fx.runService.submitted.isEmpty())
            assertTrue(fx.notifications.single().second.contains("轮末检查"))
        } finally {
            fx.cleanup()
        }
    }

    @Test
    fun `warn round-end failure still continues and carries the output forward`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(
                mission(limits = hooksLimits(roundEnd = "./gate.sh", action = MissionHook.ACTION_WARN))
            )
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./gate.sh"] = HookResult(exitCode = 1, stderr = "lint: 3 warnings")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            assertEquals(MissionStatus.ACTIVE.name, fx.missionDao.getById("m1")!!.status)
            val prompt = fx.runService.submitted.single().prompt
            assertTrue(prompt.contains("lint: 3 warnings"))
        } finally {
            fx.cleanup()
        }
    }

    // ------------------------------------------------------------------ 失败后检查

    @Test
    fun `after-failure check output is injected into the next handoff`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(mission(limits = hooksLimits(afterFailure = "./collect.sh")))
            fx.statuses["r1"] = "failed"
            fx.scripts["./collect.sh"] = HookResult(exitCode = 1, stderr = "ANR in com.tencent.mm")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            val prompt = fx.runService.submitted.single().prompt
            assertTrue(prompt.contains("ANR in com.tencent.mm"))
            assertTrue(prompt.contains("失败后检查"))
        } finally {
            fx.cleanup()
        }
    }

    @Test
    fun `hooks are skipped when the mission has no workspace`() = runBlocking {
        val fx = Fixture()
        try {
            fx.missionDao.upsert(
                mission(limits = hooksLimits(beforeDone = "./verify.sh"), workspaceId = null)
            )
            fx.statuses["r1"] = "succeeded"
            fx.scripts["./verify.sh"] = HookResult(exitCode = 9, stderr = "should never run")
            fx.signal("r1", "m1", "mission_report: done", """{"mission_signal":"done","note":"写完了"}""")

            fx.supervisor.handleRunFinished(fx.finished("r1", "m1"))

            // 检查跑不起来 ≠ 工作不合格：正常完成
            assertEquals(MissionStatus.DONE.name, fx.missionDao.getById("m1")!!.status)
        } finally {
            fx.cleanup()
        }
    }
}

/** 从 journal 里捞出某个 run 的 RUN_FINISHED 行（测试辅助）。 */
private object MissionEventPicker {
    suspend fun pick(repository: RunEventRepository, runId: String): RunEventEntity =
        repository.observeByRun(runId).first()
            .first { it.type == RunEventType.RUN_FINISHED.name }
}
