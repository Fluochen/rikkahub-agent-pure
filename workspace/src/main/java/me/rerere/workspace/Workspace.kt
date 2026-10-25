package me.rerere.workspace

data class Workspace(
    val id: String,
    val name: String,
    val root: String,
    val shellStatus: WorkspaceShellStatus = WorkspaceShellStatus.DISABLED,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessAt: Long? = null,
)

enum class WorkspaceShellStatus {
    DISABLED,
    INSTALLING,
    READY,
    BROKEN,
}

enum class WorkspaceStorageArea {
    FILES,
    LINUX,
}

enum class RootfsInstallStage {
    DOWNLOADING,
    EXTRACTING,
    INSTALLED,
}

data class RootfsInstallProgress(
    val stage: RootfsInstallStage,
    val bytesRead: Long = 0,
    val totalBytes: Long? = null,
    val entriesExtracted: Int = 0,
    val currentEntry: String? = null,
)

data class WorkspaceConfig(
    val maxReadBytes: Long = 512 * 1024,
    val maxWriteBytes: Long = 2 * 1024 * 1024,
    val maxImportBytes: Long = 256L * 1024 * 1024,
    val maxListEntries: Int = 500,
    val maxSearchResults: Int = 100,
)

data class WorkspaceFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
)

data class WorkspaceSearchMatch(
    val path: String,
    val line: Int,
    val text: String,
)

/** 递归目录树的一个条目, [depth] 相对于树的起点目录, 直接子项为 1 */
data class WorkspaceTreeEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val depth: Int,
)

data class WorkspaceTreeResult(
    val entries: List<WorkspaceTreeEntry>,
    val truncated: Boolean,
)

data class WorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
    /**
     * 命令**没跑起来**（Rootfs 未安装、proot/loader 缺失……），而不是跑完返回了非 0。
     *
     * 两者的区别对上层是决定性的：hook 与机器验收把「exit != 0」当成工作不合格，
     * 把「基础设施故障」当成「未得到结论」放行。以前这类失败只体现在 `exitCode = 127`
     * 和一句 stderr 里，上层无从分辨，于是没装 Rootfs 的工作区上每条验收都判失败。
     */
    val infraFailure: Boolean = false,
)
