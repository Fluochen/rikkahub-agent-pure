package me.rerere.rikkahub.ui.pages.setting.instances.sections

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.PermissionHelper
import me.rerere.rikkahub.data.ssh.SshHostDraft
import me.rerere.rikkahub.ui.pages.extensions.workspace.InstallRootfsDialog
import me.rerere.rikkahub.ui.pages.extensions.workspace.WorkspaceDetailVM
import me.rerere.rikkahub.ui.pages.setting.SettingSshViewModel
import me.rerere.rikkahub.ui.pages.setting.SshHostEditorSheet
import me.rerere.rikkahub.ui.pages.setting.toDraft
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 实例详情页里的「这一个实例」的配置分节。
 *
 * 与 P5 第一批（Termux）的差别：Termux 只有一个实例、配置就是一屏旋钮，所以整块搬了过来；
 * 这里三家各自带着"自己的世界"——SSH 主机是一行 Room 记录、工作区是一行 Room 记录 + rootfs
 * 安装机、App 宿主是一组系统授权。所以这三节**只渲染当前实例**，增删/浏览仍然留在原来的
 * 列表页（那是"管理所有"，不是"配置这一个"），避免把 CRUD 页面拆成两半。
 *
 * 三节都只读现有 ViewModel / 系统状态，不引入新的状态所有者。
 */

// ---------------------------------------------------------------- SSH

@Composable
fun SshInstanceSection(hostName: String) {
    val vm: SettingSshViewModel = koinViewModel()
    val hosts by vm.hosts.collectAsStateWithLifecycle()
    val testing by vm.testingName.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<SshHostDraft?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(hostName) { vm.refresh() }

    val host = hosts.firstOrNull { it.name == hostName }
    if (host == null) {
        Text(
            text = stringResource(R.string.instances_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp),
        )
        return
    }

    val result = testResult?.takeIf { it.first == hostName }?.second

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.instances_ssh_address)) },
                supportingContent = { Text("${host.user}@${host.host}:${host.port}") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.instances_ssh_auth)) },
                supportingContent = {
                    Text(
                        if (!host.privateKey.isNullOrBlank()) {
                            stringResource(R.string.instances_ssh_auth_key)
                        } else {
                            stringResource(R.string.instances_ssh_auth_password)
                        },
                    )
                },
            )
            if (testing == hostName) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            result?.let {
                Text(
                    text = if (it.ok) {
                        stringResource(R.string.instances_ssh_test_ok)
                    } else {
                        stringResource(R.string.instances_ssh_test_failed, it.errorCode ?: (it.detail ?: "?"))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { editing = host.toDraft() }) {
                    Text(stringResource(R.string.instances_ssh_edit))
                }
                OutlinedButton(onClick = { vm.test(host) }, enabled = testing != hostName) {
                    Text(stringResource(R.string.instances_ssh_test))
                }
                OutlinedButton(onClick = { confirmingDelete = true }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }

    editing?.let { draft ->
        SshHostEditorSheet(
            initialDraft = draft,
            existingNames = hosts.map { it.name }.toSet(),
            onDismiss = { editing = null },
            onSave = { saved ->
                vm.save(saved)
                editing = null
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.instances_ssh_delete_title)) },
            text = { Text(stringResource(R.string.instances_ssh_delete_body, hostName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(host)
                    confirmingDelete = false
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Workspace

@Composable
fun WorkspaceInstanceSection(workspaceId: String) {
    val vm: WorkspaceDetailVM = koinViewModel(parameters = { parametersOf(workspaceId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.installProgress.collectAsStateWithLifecycle()
    val installError by vm.installError.collectAsStateWithLifecycle()

    var showInstallDialog by remember { mutableStateOf(false) }

    val workspace = state.workspace
    if (workspace == null) {
        Text(
            text = stringResource(R.string.instances_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(4.dp),
        )
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.instances_workspace_root)) },
                supportingContent = { Text(workspace.root) },
            )
            progress?.let { p ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(
                    text = "${p.stage.name} ${p.bytesRead} / ${p.totalBytes ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            installError?.let { error ->
                Text(
                    text = stringResource(R.string.instances_workspace_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showInstallDialog = true },
                    enabled = progress == null,
                ) {
                    Text(
                        stringResource(
                            if (workspace.shellStatus == WorkspaceShellStatus.READY.name) {
                                R.string.instances_workspace_reinstall
                            } else {
                                R.string.instances_workspace_install
                            },
                        ),
                    )
                }
            }
        }
    }

    if (showInstallDialog) {
        InstallRootfsDialog(
            workspace = workspace,
            onDismiss = { showInstallDialog = false },
            onConfirm = { url ->
                vm.installRootfs(url)
                showInstallDialog = false
            },
        )
    }
}

// ---------------------------------------------------------------- App host

/**
 * App 宿主的授权面。这里**不**嵌入 设置 → 权限 整页：那是一张管理所有系统权限的表，
 * 而这一节只回答"这个实例现在还能做什么"。状态是本地读取，随每次重组刷新（很便宜）。
 */
@Composable
fun AppHostInstanceSection() {
    val context = LocalContext.current

    val storage = PermissionHelper.hasAllFilesAccess(context)
    val accessibility = PermissionHelper.hasAccessibilityService(context)
    val notifications = PermissionHelper.hasNotificationListener(context)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PermissionRow(
                title = stringResource(R.string.instances_perm_storage),
                granted = storage,
                onOpen = { context.startActivity(PermissionHelper.allFilesAccessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
            PermissionRow(
                title = stringResource(R.string.instances_perm_accessibility),
                granted = accessibility,
                onOpen = { context.startActivity(PermissionHelper.accessibilitySettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
            PermissionRow(
                title = stringResource(R.string.instances_perm_notifications),
                granted = notifications,
                onOpen = { context.startActivity(PermissionHelper.notificationListenerSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onOpen: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (granted) stringResource(R.string.instances_perm_granted)
                else stringResource(R.string.instances_perm_missing),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.instances_action_open)) }
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = CustomColors.topBarColors.containerColor,
        ),
    )
}
