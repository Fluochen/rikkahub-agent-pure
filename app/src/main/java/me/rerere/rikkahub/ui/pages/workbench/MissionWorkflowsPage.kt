package me.rerere.rikkahub.ui.pages.workbench

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.service.mission.WorkflowTemplates
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import org.koin.androidx.compose.koinViewModel

/**
 * The mission workflow library.
 *
 * The workbench already let a mission pin a Markdown playbook, but the only way to author one was
 * to create the file inside a workspace by hand. This page closes that loop: pick a workspace,
 * see its documents, preview one, import a built-in template, or start a new document from the
 * six-section skeleton. It is also where the default mission workspace first gets seeded, so the
 * create sheet's picker is never empty on a new install.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionWorkflowsPage() {
    val vm: MissionWorkflowsVM = koinViewModel()
    val toaster = LocalToaster.current
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val selectedId by vm.workspaceId.collectAsStateWithLifecycle()
    val docs by vm.docs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showNew by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            toaster.show(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mission_workflows_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showNew = true }) {
                        Icon(
                            imageVector = HugeIcons.Add01,
                            contentDescription = stringResource(R.string.mission_workflows_new),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = stringResource(R.string.mission_workflows_workspace),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        workspaces.forEach { workspace ->
                            FilterChip(
                                selected = workspace.id == selectedId,
                                onClick = { vm.select(workspace.id) },
                                label = {
                                    Text(
                                        text = workspace.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.mission_workflows_section_docs),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                if (docs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.mission_workflows_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(docs, key = { it }) { fileName ->
                    ListItem(
                        modifier = Modifier.clickable {
                            scope.launch {
                                vm.readDoc(fileName)?.let { content -> preview = fileName to content }
                            }
                        },
                        headlineContent = { Text(fileName) },
                        supportingContent = {
                            Text(stringResource(R.string.mission_workflows_preview))
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = fileName }) {
                                Icon(
                                    imageVector = HugeIcons.Delete02,
                                    contentDescription = stringResource(R.string.mission_workflows_delete),
                                )
                            }
                        },
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.mission_workflows_section_templates),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(WorkflowTemplates.ALL, key = { "tpl-${it.id}" }) { template ->
                    ListItem(
                        headlineContent = { Text("${template.name} · ${template.category}") },
                        supportingContent = {
                            Text(
                                text = template.summary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            TextButton(onClick = { vm.importTemplate(template) }) {
                                Text(stringResource(R.string.mission_workflows_import))
                            }
                        },
                    )
                }
            }
        }
    }

    preview?.let { (fileName, content) ->
        var edited by remember(fileName) { mutableStateOf(content) }
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(fileName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = edited,
                        onValueChange = { edited = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(R.string.mission_workflows_edit_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.saveDoc(fileName, edited)
                        preview = null
                    },
                ) {
                    Text(stringResource(R.string.mission_workflows_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDelete = fileName
                        preview = null
                    },
                ) {
                    Text(stringResource(R.string.mission_workflows_delete))
                }
            },
        )
    }

    pendingDelete?.let { fileName ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.mission_workflows_delete_title)) },
            text = {
                Text(stringResource(R.string.mission_workflows_delete_message, fileName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteDoc(fileName)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.mission_workflows_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.mission_workflows_cancel))
                }
            },
        )
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        var templateId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text(stringResource(R.string.mission_workflows_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.mission_workflows_new_name)) },
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(R.string.mission_workflows_new_from),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = templateId == null,
                            onClick = { templateId = null },
                            label = { Text(stringResource(R.string.mission_workflows_blank)) },
                        )
                        WorkflowTemplates.ALL.forEach { template ->
                            FilterChip(
                                selected = templateId == template.id,
                                onClick = { templateId = template.id },
                                label = { Text(template.name) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        vm.createDoc(name, templateId) { created ->
                            if (created != null) {
                                showNew = false
                                name = ""
                                templateId = null
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.mission_workflows_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }) {
                    Text(stringResource(R.string.mission_workflows_cancel))
                }
            },
        )
    }
}
