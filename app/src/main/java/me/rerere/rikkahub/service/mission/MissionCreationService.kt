package me.rerere.rikkahub.service.mission

import android.util.Log
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.mission.MissionEntity
import me.rerere.rikkahub.data.mission.MissionRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceStorageArea

/**
 * One creation path for missions, whether a human or the model starts them.
 *
 * Before this existed, three call sites each did their own dance: the workbench created a row,
 * attached a workspace maybe, then launched; the model had no path at all; and nobody seeded the
 * workflow folder, so the picker was empty on every fresh install. Centralising it means the
 * guarantees hold for both entry points:
 * - a mission always has a workspace (the one selected, or the default `任务工作区`),
 * - that workspace always has the built-in workflow documents on first use,
 * - a requested workflow is resolved to a real file before the row is written, so the launcher
 *   never has to guess,
 * - `startNow` either launches immediately or leaves a clean, armed-less mission.
 *
 * Best-effort around the edges (seeding, workflow resolution) and strict about the mission row:
 * a failed seed must not stop the mission, but an invalid title must.
 */
class MissionCreationService(
    private val missionRepository: MissionRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val missionLauncher: MissionLauncher,
) {

    data class Request(
        val title: String,
        val goal: String,
        val acceptance: List<String> = emptyList(),
        /** Built-in template id/name, a `workflows/name.md` path, or a bare document name. */
        val workflow: String? = null,
        /** Preferred workspace id; null or unknown falls back to the default mission workspace. */
        val workspaceId: String? = null,
        val assistantId: String? = null,
        /** true launches round 1 immediately; false creates the mission and waits for the user. */
        val startNow: Boolean = true,
        /**
         * 启动第 1 轮之前的最后一次配置（继承 limits / 审批姿态等）。
         *
         * 分叉以前先 `createAndStart(startNow = true)` 再 `updateLimits` / `updateApprovalMode`：
         * 第 1 轮在配置之前就发车了，子任务于是以「空 limits + approvalMode=null（=AUTO 免审）」
         * 跑起来，父任务的钩子、预算与审批姿态全部失效。
         */
        val configure: (suspend (String) -> Unit)? = null,
    )

    data class Outcome(
        val mission: MissionEntity,
        val workspaceId: String,
        val workflowPath: String?,
        val started: Boolean,
        /** Non-null when the mission exists but the first round could not be launched. */
        val launchError: String? = null,
    )

    /**
     * Creates the mission and, when [Request.startNow], launches its first round.
     *
     * Throws only for a blank title/goal; every other failure is reported in the outcome so the
     * caller can show it without losing the mission row.
     */
    suspend fun createAndStart(request: Request): Outcome {
        val title = request.title.trim().take(MAX_TITLE_CHARS)
        require(title.isNotBlank()) { "Mission title is blank" }
        val goal = request.goal.trim().take(MAX_GOAL_CHARS).ifBlank { title }

        val workspace = resolveWorkspace(request.workspaceId)
        seedWorkflows(workspace.id)
        val workflowPath = resolveWorkflowPath(workspace.id, request.workflow)

        val mission = missionRepository.create(
            title = title,
            description = goal,
            acceptance = request.acceptance
                .map { it.trim().take(MAX_ACCEPTANCE_CHARS) }
                .filter { it.isNotBlank() }
                .take(MAX_ACCEPTANCE_ITEMS),
            conversationId = null,
            assistantId = request.assistantId,
            workflowPath = workflowPath,
        )
        missionRepository.updateWorkspaceAndAssistant(
            id = mission.id,
            workspaceId = workspace.id,
            assistantId = request.assistantId,
        )

        // 配置必须先于第 1 轮：它决定这一轮的审批姿态与预算。
        request.configure?.let { configure -> runCatching { configure(mission.id) } }
        if (!request.startNow) {
            return Outcome(mission, workspace.id, workflowPath, started = false)
        }
        val launch = missionLauncher.launch(mission.id, LaunchSource.Create)
        return Outcome(
            mission = mission,
            workspaceId = workspace.id,
            workflowPath = workflowPath,
            started = launch.ok,
            launchError = launch.error,
        )
    }

    /**
     * The workspace a mission should use when none is selected. Named and reused rather than
     * created per mission, so the user's workflow edits accumulate in one familiar place.
     */
    suspend fun defaultWorkspace(): WorkspaceEntity {
        workspaceRepository.getAll()
            .firstOrNull { it.name == DEFAULT_MISSION_WORKSPACE_NAME }
            ?.let { return it }
        return runCatching {
            workspaceRepository.create(DEFAULT_MISSION_WORKSPACE_NAME)
        }.getOrElse {
            // Lost a creation race (or the name check raced): reuse the winner if it exists.
            workspaceRepository.getAll()
                .firstOrNull { it.name == DEFAULT_MISSION_WORKSPACE_NAME }
                ?: throw it
        }
    }

    private suspend fun resolveWorkspace(preferredId: String?): WorkspaceEntity =
        preferredId
            ?.takeIf { it.isNotBlank() }
            ?.let { id -> runCatching { workspaceRepository.getById(id) }.getOrNull() }
            ?: defaultWorkspace()

    /**
     * Writes the built-in workflow documents when the folder has none. Never overwrites an
     * existing document, so a user edit survives every later mission creation.
     *
     * @return number of files written (0 when the folder already had documents).
     */
    suspend fun seedWorkflows(workspaceId: String): Int {
        val existing = runCatching {
            workspaceRepository.listFiles(
                id = workspaceId,
                area = WorkspaceStorageArea.FILES,
                path = WORKFLOW_DIR,
            )
        }.getOrDefault(emptyList())
        if (existing.any { !it.isDirectory && it.name.endsWith(".md", ignoreCase = true) }) {
            return 0
        }
        runCatching {
            workspaceRepository.createDirectory(
                id = workspaceId,
                area = WorkspaceStorageArea.FILES,
                path = WORKFLOW_DIR,
            )
        }
        var written = 0
        WorkflowTemplates.ALL.forEach { template ->
            val ok = runCatching {
                workspaceRepository.writeText(
                    id = workspaceId,
                    path = "${WORKFLOW_DIR}/${template.fileName}",
                    text = template.body,
                    overwrite = false,
                )
            }.isSuccess
            if (ok) written++
        }
        return written
    }

    /**
     * Resolves a requested workflow to a real relative path, creating the built-in document when
     * the request names a template. Returns null (chooser mode) when the request is blank, says
     * "auto", or cannot be resolved — a missing playbook must not block a mission.
     */
    suspend fun resolveWorkflowPath(workspaceId: String, requested: String?): String? {
        val raw = requested?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("auto", ignoreCase = true) || raw == "由模型自选") return null

        val asTemplate = WorkflowTemplates.find(raw)
        if (asTemplate != null) {
            writeTemplateIfMissing(workspaceId, asTemplate)
            return MissionWorkflowDocs.pathOf(asTemplate.fileName)
        }

        if (MissionWorkflowDocs.isWorkflowDoc(raw)) {
            val name = raw.removePrefix("$WORKFLOW_DIR/")
            ensureTemplateForDocName(workspaceId, name)
            return if (docExists(workspaceId, name)) raw else null
        }

        val sanitized = MissionWorkflowDocs.sanitizeDocName(raw) ?: return null
        ensureTemplateForDocName(workspaceId, sanitized)
        return MissionWorkflowDocs.pathOf(sanitized).takeIf { docExists(workspaceId, sanitized) }
    }

    /** Names of the workflow documents available in [workspaceId], sorted. */
    suspend fun listWorkflowDocs(workspaceId: String): List<String> = runCatching {
        workspaceRepository.listFiles(
            id = workspaceId,
            area = WorkspaceStorageArea.FILES,
            path = WORKFLOW_DIR,
        )
            .filter { !it.isDirectory && it.name.endsWith(".md", ignoreCase = true) }
            .map { it.name }
            .sorted()
    }.getOrDefault(emptyList())

    suspend fun readWorkflowDoc(workspaceId: String, fileName: String): String? = runCatching {
        workspaceRepository.readText(
            id = workspaceId,
            path = MissionWorkflowDocs.pathOf(fileName),
        )
    }.getOrNull()

    /** Writes a document; refuses to clobber an existing one unless [overwrite]. */
    suspend fun writeWorkflowDoc(
        workspaceId: String,
        fileName: String,
        content: String,
        overwrite: Boolean = false,
    ): Boolean = runCatching {
        workspaceRepository.writeText(
            id = workspaceId,
            path = MissionWorkflowDocs.pathOf(fileName),
            text = content,
            overwrite = overwrite,
        )
        true
    }.getOrDefault(false)

    private suspend fun writeTemplateIfMissing(workspaceId: String, template: WorkflowTemplates.Template) {
        runCatching {
            workspaceRepository.writeText(
                id = workspaceId,
                path = "${WORKFLOW_DIR}/${template.fileName}",
                text = template.body,
                overwrite = false,
            )
        }
    }

    private suspend fun ensureTemplateForDocName(workspaceId: String, fileName: String) {
        val stem = fileName.removeSuffix(".md")
        WorkflowTemplates.find(stem)?.let { writeTemplateIfMissing(workspaceId, it) }
    }

    private suspend fun docExists(workspaceId: String, fileName: String): Boolean = runCatching {
        workspaceRepository.readText(
            id = workspaceId,
            path = MissionWorkflowDocs.pathOf(fileName),
        )
        true
    }.getOrDefault(false).also { exists ->
        if (!exists) Log.w(TAG, "workflow document missing after resolution: $fileName")
    }

    companion object {
        const val DEFAULT_MISSION_WORKSPACE_NAME: String = "任务工作区"
        private const val MAX_TITLE_CHARS: Int = 120
        private const val MAX_GOAL_CHARS: Int = 8_000
        private const val MAX_ACCEPTANCE_ITEMS: Int = 12
        private const val MAX_ACCEPTANCE_CHARS: Int = 500
        private const val TAG: String = "MissionCreation"
    }
}
