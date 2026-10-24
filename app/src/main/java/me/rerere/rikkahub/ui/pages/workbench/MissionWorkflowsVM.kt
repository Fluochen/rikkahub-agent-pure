package me.rerere.rikkahub.ui.pages.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.service.mission.MissionCreationService
import me.rerere.rikkahub.service.mission.MissionWorkflowDocs
import me.rerere.rikkahub.service.mission.WorkflowTemplates

/**
 * The workflow library's state holder.
 *
 * One page, one workspace at a time: the documents are files in that workspace, so showing a
 * global list would lie about where an edit lands. Selecting a workspace reloads the list; the
 * default mission workspace is created on first open, which is also what makes the create sheet's
 * picker non-empty for a brand-new install.
 */
class MissionWorkflowsVM(
    private val workspaceRepository: WorkspaceRepository,
    private val creation: MissionCreationService,
) : ViewModel() {

    val workspaces: StateFlow<List<WorkspaceEntity>> = workspaceRepository.listFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _workspaceId = MutableStateFlow<String?>(null)
    val workspaceId: StateFlow<String?> = _workspaceId.asStateFlow()

    private val _docs = MutableStateFlow<List<String>>(emptyList())
    val docs: StateFlow<List<String>> = _docs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val id = runCatching { creation.defaultWorkspace().id }.getOrNull()
            if (id != null) {
                // First open of the library is also the first seed: the default workspace gets the
                // built-in documents, and the page has something to show besides templates.
                runCatching { creation.seedWorkflows(id) }
                select(id)
            }
        }
    }

    fun select(id: String) {
        _workspaceId.value = id
        reload(id)
    }

    fun reload() {
        _workspaceId.value?.let { reload(it) }
    }

    private fun reload(id: String) {
        viewModelScope.launch {
            _busy.value = true
            _docs.value = runCatching { creation.listWorkflowDocs(id) }.getOrDefault(emptyList())
            _busy.value = false
        }
    }

    fun importTemplate(template: WorkflowTemplates.Template) {
        val id = _workspaceId.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val ok = creation.writeWorkflowDoc(id, template.fileName, template.body, overwrite = false)
            _busy.value = false
            _message.value = if (ok) {
                "已导入：${template.name}"
            } else {
                "导入失败：同名流程可能已存在"
            }
            reload(id)
        }
    }

    /**
     * Creates a document from a built-in template (when [templateId] matches) or from the blank
     * six-section skeleton. Returns the file name so the caller can open it immediately.
     */
    fun createDoc(name: String, templateId: String?, onCreated: (String?) -> Unit) {
        val id = _workspaceId.value ?: return onCreated(null)
        val sanitized = MissionWorkflowDocs.sanitizeDocName(name) ?: return onCreated(null)
        val template = templateId?.let { WorkflowTemplates.find(it) }
        val body = template?.body ?: MissionWorkflowDocs.template(name.trim(), "")
        viewModelScope.launch {
            _busy.value = true
            val ok = creation.writeWorkflowDoc(id, "$sanitized.md", body, overwrite = false)
            _busy.value = false
            _message.value = if (ok) "已创建：$sanitized.md" else "创建失败：同名流程可能已存在"
            reload(id)
            onCreated(if (ok) "$sanitized.md" else null)
        }
    }

    /** Saves an edited document in place. The launcher reads the file next round, so no mission row changes. */
    fun saveDoc(fileName: String, content: String) {
        val id = _workspaceId.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val ok = creation.writeWorkflowDoc(id, fileName, content, overwrite = true)
            _busy.value = false
            _message.value = if (ok) "已保存：$fileName" else "保存失败：$fileName"
            reload(id)
        }
    }

    fun deleteDoc(fileName: String) {        val id = _workspaceId.value ?: return
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                workspaceRepository.deleteFile(
                    id = id,
                    area = me.rerere.workspace.WorkspaceStorageArea.FILES,
                    path = MissionWorkflowDocs.pathOf(fileName),
                    recursive = false,
                )
            }.getOrDefault(false)
            _busy.value = false
            _message.value = if (ok) "已删除：$fileName" else "删除失败：$fileName"
            reload(id)
        }
    }

    suspend fun readDoc(fileName: String): String? {
        val id = _workspaceId.value ?: return null
        return creation.readWorkflowDoc(id, fileName)
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
