package me.rerere.rikkahub.data.mission

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.runevent.FakeRunEventDao
import me.rerere.rikkahub.data.runevent.RunEventRepository
import me.rerere.rikkahub.data.runevent.RunEventType
import me.rerere.rikkahub.subagent.SubAgentRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [MissionEngine]'s signal persistence and approval-posture hooks.
 */
class MissionEngineTest {

    @Test
    fun `reportSignal uses the caller's mission id with a cold cache and no DB backfill`() = runBlocking {
        val missionDao = FakeMissionDao()
        val runEventDao = FakeRunEventDao()
        val runEvents = RunEventRepository(runEventDao)
        val missionRepo = MissionRepository(missionDao, runEvents)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = MissionEngine(missionRepo, runEvents, SubAgentRegistry(), scope)

        try {
            // Deliberately no mission row and no binding: this is the first-round shape, where
            // the mission's conversation_id has not been persisted yet. The tool resolved the
            // mission while assembling its schema, so the signal must still land.
            engine.reportSignal(
                conversationId = "conv-fresh",
                runId = "run-1",
                status = "done",
                note = "交付摘要",
                missionId = "m-fresh",
            )

            val signal = runEventDao.snapshot()
                .single { it.type == RunEventType.NOTE.name && it.title == "mission_report: done" }
            assertEquals("m-fresh", signal.missionId)
            assertEquals("conv-fresh", signal.conversationId)
            assertEquals("run-1", signal.runId)
            assertEquals("交付摘要", signal.detail)
        } finally {
            scope.cancel()
        }
    }

    // ---------------------------------------------------------------- 批 E — approval posture

    @Test
    fun `awaiting approval parks a running mission and resolution restores it`() = runBlocking {
        val missionDao = FakeMissionDao()
        val runEventDao = FakeRunEventDao()
        val runEvents = RunEventRepository(runEventDao)
        val missionRepo = MissionRepository(missionDao, runEvents)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = MissionEngine(missionRepo, runEvents, SubAgentRegistry(), scope)

        try {
            missionDao.upsert(
                MissionEntity(
                    id = "m-approval",
                    title = "整理相册",
                    description = "",
                    conversationId = "conv-approval",
                    executionState = MissionExecutionState.RUNNING.name,
                    lastRunId = "run-approval",
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            )
            engine.bindConversation("conv-approval", "m-approval")

            engine.onAwaitingApproval("conv-approval", "workspace_write_file")

            assertEquals(
                MissionExecutionState.WAITING_APPROVAL.name,
                missionDao.getById("m-approval")!!.executionState,
            )
            val note = runEventDao.snapshot().single { it.title == "awaiting_approval" }
            assertEquals("workspace_write_file", note.detail)
            assertEquals("run-approval", note.runId)

            engine.onApprovalResolved("conv-approval")

            assertEquals(
                MissionExecutionState.RUNNING.name,
                missionDao.getById("m-approval")!!.executionState,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a late approval callback cannot resurrect a parked mission`() = runBlocking {
        val missionDao = FakeMissionDao()
        val runEventDao = FakeRunEventDao()
        val runEvents = RunEventRepository(runEventDao)
        val missionRepo = MissionRepository(missionDao, runEvents)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = MissionEngine(missionRepo, runEvents, SubAgentRegistry(), scope)

        try {
            missionDao.upsert(
                MissionEntity(
                    id = "m-stopped",
                    title = "整理相册",
                    description = "",
                    conversationId = "conv-stopped",
                    executionState = MissionExecutionState.IDLE.name,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                )
            )
            engine.bindConversation("conv-stopped", "m-stopped")

            engine.onAwaitingApproval("conv-stopped", "workspace_write_file")

            assertEquals(
                "a mission the user stopped must stay parked",
                MissionExecutionState.IDLE.name,
                missionDao.getById("m-stopped")!!.executionState,
            )
            assertEquals(
                "no approval event for a parked mission",
                0,
                runEventDao.snapshot().count { it.title == "awaiting_approval" },
            )
        } finally {
            scope.cancel()
        }
    }
}
