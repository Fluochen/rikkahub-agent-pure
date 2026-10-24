package me.rerere.rikkahub.subagent

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.runtime.util.JsonInstant

/**
 * G 批第三片 — 子代理 run 的落盘。
 *
 * 为什么需要：`SubAgentRegistry` 是纯内存的，进程一死，`subagent_list` 就空了，
 * `subagent_get` / `subagent_send` 全部报 unknown_id。但**会话本身是持久的**（对话、用量、
 * 台账都在库里），丢的只是「handle」这一层。一个跑了几十分钟的子代理，重启后连它做过什么都查不到，
 * 这在一个要连续跑几小时的系统里是不能接受的。
 *
 * 做法：一条 run 一个 JSON 文件，放在应用私有目录。刻意**不建表**——理由和任务快照一样：
 * 这是运行期簿记，不是业务实体，加一张表要背上 schema 迁移、DAO、测试 fake 的一整套成本，
 * 而这里需要的能力只有「按 id 读、列出最近的、先写先删」。
 *
 * 失败一律吞掉：落盘是观测能力，不是执行前提。写不动就退化成今天的行为（重启后查不到），
 * 绝不能让一次派发因为磁盘满而失败。
 */
class SubAgentRunStore(
    private val baseDir: File,
    private val keep: Int = DEFAULT_KEEP,
) {

    private val mutex = Mutex()

    /** 保存/覆盖一条 run。终态与状态变化都走这里，后写覆盖先写。 */
    suspend fun save(run: SubAgentRun) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                baseDir.mkdirs()
                val file = fileFor(run.id)
                val temp = File(baseDir, "${file.name}.tmp")
                temp.writeText(JsonInstant.encodeToString(run))
                if (!temp.renameTo(file)) {
                    file.delete()
                    temp.renameTo(file)
                }
                pruneLocked()
            }
            Unit
        }
    }

    /** 按 id 读；不存在或损坏返回 null。 */
    suspend fun load(runId: String): SubAgentRun? = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val file = fileFor(runId)
                if (!file.isFile) return@runCatching null
                JsonInstant.decodeFromString<SubAgentRun>(file.readText())
            }.getOrNull()
        }
    }

    /** 最近的 run，最新的在前。 */
    suspend fun list(): List<SubAgentRun> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                baseDir.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.name.endsWith(JSON_SUFFIX) }
                    .mapNotNull { file ->
                        runCatching {
                            JsonInstant.decodeFromString<SubAgentRun>(file.readText())
                        }.getOrNull()
                    }
                    .sortedByDescending { it.startedAtMs }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun delete(runId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { fileFor(runId).delete() }
            Unit
        }
    }

    private fun fileFor(runId: String): File =
        File(baseDir, runId.filter { it.isLetterOrDigit() || it == '-' || it == '_' } + JSON_SUFFIX)

    /**
     * 超过 [keep] 条时删最旧的。排序用 run 自己的 `startedAtMs`，不用文件 mtime ——
     * 同一毫秒内连写几条时 mtime 分不出先后，会删错刚写的那条。读不出来的文件退回 mtime。
     */
    private fun pruneLocked() {
        val files = baseDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(JSON_SUFFIX) }
            ?: return
        if (files.size <= keep) return
        val ranked = files.map { file ->
            val startedAt = runCatching {
                JsonInstant.decodeFromString<SubAgentRun>(file.readText()).startedAtMs
            }.getOrDefault(file.lastModified())
            file to startedAt
        }.sortedByDescending { it.second }
        ranked.drop(keep).forEach { (file, _) -> runCatching { file.delete() } }
    }

    companion object {
        /** 单任务目录里保留多少条 run。比登记表的 LRU 宽一些：磁盘便宜，查不到很贵。 */
        const val DEFAULT_KEEP: Int = 80

        const val JSON_SUFFIX: String = ".json"
    }
}

/**
 * 进程级的落盘入口。
 *
 * 用对象而不是往 [SubAgentEngine] / 工具构造里加参数，是为了让这一层可以被"没装就退化成纯内存"：
 * JVM 工装和单元测试不 install，行为与之前完全一致；应用启动时装一次，整条链路自动生效。
 * 与 `SubAgentDepths` / `ConversationSystemAddendum` 的既有模式一致。
 */
object SubAgentRunPersistence {

    @Volatile
    private var store: SubAgentRunStore? = null

    /** 应用启动时调一次；重复调用覆盖（测试里的临时目录会用到）。 */
    fun install(baseDir: File) {
        store = SubAgentRunStore(baseDir)
    }

    fun isInstalled(): Boolean = store != null

    /** 把一条 run 写到磁盘；没装 store 时是 no-op。永不抛。 */
    suspend fun save(run: SubAgentRun) {
        runCatching { store?.save(run) }
    }

    /**
     * 按 id 读一条。磁盘上还是 RUNNING/PENDING 的记录意味着**上一个进程死在半路** ——
     * 按进程丢失处理，与 `AgentRunBootRecovery` 对台账的处理一致，否则它会永远显示"运行中"。
     */
    suspend fun load(runId: String): SubAgentRun? {
        val loaded = runCatching { store?.load(runId) }.getOrNull() ?: return null
        return recovered(loaded)
    }

    /** 最近的 run；坏文件逐条跳过。 */
    suspend fun list(): List<SubAgentRun> =
        runCatching { store?.list() }.getOrNull().orEmpty().map(::recovered)

    /** 测试与"清空子代理历史"用。 */
    fun resetForTests() {
        store = null
    }

    /**
     * 进程丢失的 run 直接把状态改成 FAILED：调用方（list/get/send）拿到的必须是**能解释的**
     * 终态，而不是一个永远转圈的 RUNNING。
     */
    private fun recovered(run: SubAgentRun): SubAgentRun {
        if (run.status != SubAgentStatus.RUNNING && run.status != SubAgentStatus.PENDING) return run
        return run.copy(
            status = SubAgentStatus.FAILED,
            error = "process lost: the app was restarted while this sub-agent was running",
            finishedAtMs = run.finishedAtMs ?: System.currentTimeMillis(),
        )
    }
}
