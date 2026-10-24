package me.rerere.rikkahub.service.mission

import java.io.File

/**
 * 批 F — 把工作区 id 解析成 FILES 区的真实目录，供轮次快照拷贝。
 *
 * 单独抽一个出口而不是直接塞 `WorkspaceRepository` 进来：launcher 只需要「一个目录」，不需要整个
 * 仓库；测试也不必构造仓库（给一个临时目录即可）。
 *
 * 实现约定：工作区不存在时返回 null，不抛异常。
 */
fun interface MissionWorkspaceFiles {

    /** 工作区 FILES 区根目录；工作区不存在返回 null。 */
    suspend fun filesDir(workspaceId: String): File?
}
