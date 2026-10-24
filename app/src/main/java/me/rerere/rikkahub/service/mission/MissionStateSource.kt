package me.rerere.rikkahub.service.mission

/**
 * 批 F — 任务状态文件（`task-state.md`）的读取出口。
 *
 * 运行时要它做两件事：每轮开跑前把内容注入提示词（[MissionLauncher]），每轮结束后比指纹看有没有
 * 更新（[MissionSupervisor]）。DI 接 `WorkspaceRepository.readText`，测试塞假实现。
 *
 * 实现必须宽容：文件不存在 / 工作区没了 / 读失败都返回 null，绝不能因为「读不到进度」而挡住一轮任务。
 */
fun interface MissionStateSource {

    /** 读工作区 FILES 根目录下的 `task-state.md`；不存在或读不了返回 null。 */
    suspend fun read(workspaceId: String): String?
}
