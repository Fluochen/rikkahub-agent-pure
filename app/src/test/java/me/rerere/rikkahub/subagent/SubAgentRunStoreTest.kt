package me.rerere.rikkahub.subagent

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G 批第三片 — 子代理 run 的落盘。
 *
 * 三条要钉住的性质：
 * 1. **往返完整**：重启后 `subagent_send` 需要的字段（会话、专家、工具白名单、深度、预算根）
 *    一个都不能丢 —— 少一个就续不上。
 * 2. **进程丢失会被收敛**：磁盘上写着 RUNNING/PENDING 的记录，说明上个进程死在半路，
 *    读出来必须是 FAILED，而不是永远转圈。
 * 3. **没装 store 时完全退化**：单测/工装不 install，行为和以前（纯内存）一致。
 */
class SubAgentRunStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("subagent-runs").toFile()

    private fun run(
        id: String = "run-1",
        status: SubAgentStatus = SubAgentStatus.SUCCEEDED,
        startedAtMs: Long = 1_000L,
        conversationId: String? = "conv-1",
        depth: Int = 2,
        rootChatId: String? = "root-chat",
    ) = SubAgentRun(
        id = id,
        parentChatId = "parent-chat",
        conversationId = conversationId,
        depth = depth,
        rootChatId = rootChatId,
        agentName = "researcher",
        parentAssistantId = "asst-1",
        label = "调研",
        task = "调研 X",
        modelId = "model-a",
        tools = listOf("web_search"),
        runInBackground = false,
        timeoutSeconds = 300,
        maxTrips = 12,
        status = status,
        result = "结论",
        startedAtMs = startedAtMs,
        finishedAtMs = startedAtMs + 1_000,
    )

    @Test
    fun `a saved run round-trips with everything resume needs`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            SubAgentRunPersistence.save(run())

            val loaded = SubAgentRunPersistence.load("run-1")!!

            assertEquals("run-1", loaded.id)
            assertEquals("conv-1", loaded.conversationId)
            assertEquals("researcher", loaded.agentName)
            assertEquals(listOf("web_search"), loaded.tools)
            assertEquals(2, loaded.depth)
            assertEquals("root-chat", loaded.rootChatId)
            assertEquals(SubAgentStatus.SUCCEEDED, loaded.status)
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `an unknown id reads as null`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            assertNull(SubAgentRunPersistence.load("nope"))
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `list is newest first`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            SubAgentRunPersistence.save(run(id = "old", startedAtMs = 1_000))
            SubAgentRunPersistence.save(run(id = "new", startedAtMs = 9_000))

            assertEquals(listOf("new", "old"), SubAgentRunPersistence.list().map { it.id })
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `a run that was running when the process died comes back failed`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            SubAgentRunPersistence.save(run(status = SubAgentStatus.RUNNING))

            val loaded = SubAgentRunPersistence.load("run-1")!!

            assertEquals(SubAgentStatus.FAILED, loaded.status)
            assertTrue(loaded.error.orEmpty().contains("process lost"))
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `a pending run is recovered the same way`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            SubAgentRunPersistence.save(run(status = SubAgentStatus.PENDING))
            assertEquals(SubAgentStatus.FAILED, SubAgentRunPersistence.load("run-1")!!.status)
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `saving again overwrites the same record`() = runBlocking {
        SubAgentRunPersistence.install(tempDir())
        try {
            SubAgentRunPersistence.save(run(status = SubAgentStatus.RUNNING))
            SubAgentRunPersistence.save(run(status = SubAgentStatus.SUCCEEDED))

            assertEquals(1, SubAgentRunPersistence.list().size)
            assertEquals(SubAgentStatus.SUCCEEDED, SubAgentRunPersistence.load("run-1")!!.status)
        } finally {
            SubAgentRunPersistence.resetForTests()
        }
    }

    @Test
    fun `only the newest keep records survive`() = runBlocking {
        val dir = tempDir()
        val store = SubAgentRunStore(dir, keep = 2)
        store.save(run(id = "a", startedAtMs = 1_000))
        store.save(run(id = "b", startedAtMs = 2_000))
        store.save(run(id = "c", startedAtMs = 3_000))

        val ids = store.list().map { it.id }.toSet()
        assertEquals(setOf("c", "b"), ids)
        assertTrue(dir.listFiles().orEmpty().size <= 2)
    }

    @Test
    fun `not installed means pure memory as before`() = runBlocking {
        SubAgentRunPersistence.resetForTests()

        assertTrue(!SubAgentRunPersistence.isInstalled())
        SubAgentRunPersistence.save(run())
        assertNull(SubAgentRunPersistence.load("run-1"))
        assertTrue(SubAgentRunPersistence.list().isEmpty())
    }
}
