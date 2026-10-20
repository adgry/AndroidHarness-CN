package com.androidharness.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.env.PathClassifier
import kotlinx.coroutines.launch

/**
 * The ways to attach a workspace, shared by Settings and the chat workspace
 * switcher:
 * 1. App workspace. Private folder, shell always works.
 * 2. Device folder. Browsed in-app, no typing. Full shell.
 * 3. System picker (SAF). Works without broad storage access. Directly writable
 *    device folders are upgraded to full shell; other picks use file tools.
 */
@Composable
fun AddWorkspaceDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onPickSaf: () -> Unit,
    onSelected: (String) -> Unit = {},
    activateWorkspace: Boolean = true,
) {
    ShizukuWorkspaceWarningEffect(container.shizuku)
    var destination by remember { mutableStateOf<String?>(null) }
    if (destination == "github") {
        com.androidharness.app.ui.github.GitHubImportDialog(container, onDismiss,
            onImported = onDismiss, activateWorkspace = activateWorkspace, onProjectImported = onSelected)
        return
    }
    if (destination == "ssh") {
        SshWorkspaceDialog(container, onDismiss = onDismiss, onSelected = onSelected, activateWorkspace = activateWorkspace)
        return
    }
    if (destination == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("添加工作区") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { destination = "github" }, modifier = Modifier.fillMaxWidth()) { Text("从 GitHub 导入") }
                Button(onClick = { destination = "local" }, modifier = Modifier.fillMaxWidth()) { Text("此设备上") }
                OutlinedButton(onClick = { destination = "ssh" }, modifier = Modifier.fillMaxWidth()) { Text("通过 SSH 连接") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
        return
    }
    val scope = rememberCoroutineScope()
    val projects = container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val appProject = projects.value.firstOrNull { it.kind == "APP" }
    var error by remember { mutableStateOf<String?>(null) }
    var showFolderBrowser by remember { mutableStateOf(false) }

    fun addBrowsedFolder(path: String) {
        val trimmed = path.trim()
        val assessment = container.workspace.assessPath(trimmed)
        when {
            !assessment.directoryExists ->
                error = "无法读取此文件夹，请选择其他文件夹。"
            assessment.region == PathClassifier.Region.APP_DATA ->
                error = "这是应用的私有存储，请使用应用工作区。"
            else -> scope.launch {
                onSelected(container.workspace.addShellProject(trimmed, activateWorkspace).id)
                onDismiss()
            }
        }
    }

    if (showFolderBrowser) {
        FolderPickerDialog(
            container = container,
            onPick = { picked ->
                showFolderBrowser = false
                addBrowsedFolder(picked)
            },
            onDismiss = { showFolderBrowser = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加工作区") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "工作区是代理读取和写入文件的位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("应用工作区", style = MaterialTheme.typography.titleSmall)
                Text(
                    "仅此应用可见的私有文件夹，可直接使用 Shell。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        if (appProject != null) scope.launch {
                            if (activateWorkspace) container.workspace.setActiveProject(appProject.id)
                            onSelected(appProject.id)
                            onDismiss()
                        } else onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("使用应用工作区") }

                HorizontalDivider()

                Text("设备文件夹", style = MaterialTheme.typography.titleSmall)
                Text(
                    "选择手机或 SD 卡上的任意文件夹。在内置" +
                        "浏览器中选择，无需手动输入。支持完整 Shell。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showFolderBrowser = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("浏览设备文件夹") }

                HorizontalDivider()

                Text("系统选择器", style = MaterialTheme.typography.titleSmall)
                Text(
                    "无需授予广泛的存储权限即可选择文件夹。文件工具" +
                        "可在所选文件夹中工作。Shell 命令需要直接存储" +
                        "访问权限，也可改用应用工作区。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onPickSaf, modifier = Modifier.fillMaxWidth()) {
                    Text("打开系统选择器")
                }

                error?.let { msg ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
