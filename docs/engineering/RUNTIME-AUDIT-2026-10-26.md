# 后端运行时审计 — 2026-10-26（第二轮）

范围：生成/工具执行循环、子进程层、后台服务与轮询循环、Ktor Web 服务运行时、
并发/取消/资源泄漏。第一轮报告见 `BUG-AUDIT-2026-10-26.md`（7 项修复已由
PR #423 合入 `moxw`：`114cf6a`）。

方法同第一轮：逐文件通读关键运行时路径（无本地 JDK/Android SDK，靠代码阅读
确认、用 PR 的 CI 跑编译与单测）。

## 已修复（本轮 PR）

| 编号 | 位置 | 问题 |
|---|---|---|
| R1 | `workspace/.../WorkspaceBackgroundProcesses.kt` | 后台进程 stdin 永不关闭：读 stdin 的命令（`cat`、交互登录等）永久阻塞，占住每 workspace 仅 5 个的后台槽位之一。前台路径 `Process.readResult` 早已为此关 stdin，后台路径漏了。修复：注册时即 `process.outputStream.close()`（后台路径本就忽略 stdin）。含 FakeProcess 关闭断言 + 真实 `cat` EOF 退出测试；原 kill 测试改用 `sleep`。 |
| R2 | `app/.../service/ChatService.kt`（`removeSession`/`dropSession`） | 四张按会话 id 的注册表从不清理：`toolActivations`、`generationFailures`、`toolUnavailableWarned`、`conversationNotificationSequence`。headless/cron 每轮新建会话，条目随进程终身累积。修复：随会话驱逐一并 remove（`toolActivations` 本就注明 session-scoped；其余均为运行时记账，重置语义安全）。 |
| R3 | `app/.../tools/local/TermuxSessionTool.kt`（`readUntilDone`） | 每 200 ms 追加一份整屏字符串且永不剪枝，最长 600 s 可达 3000 份。修复：内容变化即丢弃旧样本——`evaluatePoll` 的 settle 扫描在第一个不同样本处停止，旧样本永不再用，语义等价。 |
| R4 | `app/.../shizuku/ShizukuCommandRunner.kt` | `waitFor` 的 `InterruptedException` 直接逃逸，不销毁进程，shell UID 命令会无人看管地跑完。修复：捕获后 `destroyForcibly()` 再抛，与 `readResult` 对齐。含中断即返回的单测。 |
| R5 | `workspace/.../WorkspaceBackgroundProcesses.kt`（`toStatus`） | 进程退出后每次状态查询都 `join(1000)`×2；若孙进程继承管道 fd 不关，drain 线程永久阻塞，`list/status` 每次恒付 2 s。修复：仅第一次查询时 join（AtomicBoolean）。 |
| R6 | `app/.../service/ChatService.kt`（`addError`） | `_errors` 只增不清，headless/cron 无人 dismiss，列表随进程终身增长（且每个 SSE 订阅者都全量转发）。修复：保留最近 50 条。 |

## 报告项（未修，建议）

- **R7 孙进程残留**：host/shizuku 路径超时只 `destroyForcibly()` 直接子进程，
  `sh -c` 派生的孙进程可残留（继续占用端口/锁、继续耗电）。proot 路径已有
  `--kill-on-exit` 覆盖。可选方案：同 UID 场景用 `java.lang.ProcessHandle`
  （API 26+）遍历 `descendants()`；Android 10+ hidepid 下跨 UID（shizuku shell
  UID）不可见，需在 user-service 进程内处理。无真机验证前不盲改 kill 语义。
- **R8 生成循环的超时非抢占**：`GenerationLoop` 同级工具串行执行，
  `withTimeoutOrNull` 无法抢占阻塞在非协作 IO 里的工具；30 分钟墙钟上限只在
  挂起点生效。阻塞式工具应各自在 `runInterruptible` + 子进程超时内自带上限
  （现状基本如此），此项仅记录边界。
- **R9 Telegram at-most-once**：`pollLoop` 先推进并持久化 offset、再异步
  dispatch，窗口内进程被杀会丢消息。这是注释里写明取舍（防不可解析 update
  无限重投），仅记录。
- **R10 Web SSE 成本**：`/{id}/stream` 每来一个 token 就对整段会话做一次
  `toDto` + diff；超长会话时 CPU 成本显著。`knownErrorIds` 随连接时长无界
  增长（量级小）。可考虑节点级增量 flow（工作量较大）。
- **R11 子代理前台 join 吞取消（观察，无害）**：`SubAgentEngine` 前台
  `executionJob.join()` 外围 `catch (Throwable)` 会吞 `CancellationException`
  并返回当前注册表状态；子 run 本身有独立取消通道
  （`stopGeneration` → `cancelAllForParent`），父轮次也会在下个挂起点感知
  取消，故仅记录。

## 已查并确认健康

- `Process.readResult`：并发 drain、上限后继续 drain、中断时销毁并回收线程。
- Telegram `pollLoop`：指数退避、401/404 停用并通知、429 尊重 `retry_after`、
  offset 持久化、token 变更复位 offset、WakeLock 超时回收。
- MCP：`callTool` 120 s 超时、取消例外正确重抛、连接生命周期同一 mutex 串行。
- `CronJobWorker`：WorkManager replay 抑制、超时停生成、slot 语义。
- `SubAgentEngine`：槽位检查与占位在同一 lock 原子完成；
  `invokeOnCompletion` 安全网覆盖「body 前被取消」的 run。
- OkHttp 主客户端：dispatcher 上限已抬高避免同 host 排队假挂死、ping 保活。
- `WebServerManager`：启停串行化 mutex（旧 race 已修）。
- `ConversationSession`：作业替换 CAS、waiter 先取消序。
- `MissionSupervisor`：事件驱动，无轮询 ticker。
