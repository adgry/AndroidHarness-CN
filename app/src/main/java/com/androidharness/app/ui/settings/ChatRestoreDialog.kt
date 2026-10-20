package com.androidharness.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.ChatBackupManager
import com.androidharness.app.data.ChatImportPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ChatRestoreDialog(container: AppContainer, preview: ChatImportPreview, onDismiss: () -> Unit,
    onImported: (ChatBackupManager.ImportResult) -> Unit) {
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    var mapping by remember(preview) { mutableStateOf(emptyMap<String, String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try { container.workspace.addPickedFolder(uri, activate = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("重新关联已恢复的聊天") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("为${if (preview.sessions.size == 1) "此恢复的聊天" else "这 ${preview.sessions.size} 个恢复的聊天"}选择工作区。保留现有聊天。备份不包含文件和文件夹权限。")
                preview.missingWorkspaces.forEach { source ->
                    var expanded by remember(source.id) { mutableStateOf(false) }
                    Text(source.name, style = MaterialTheme.typography.titleSmall)
                    Box {
                        OutlinedButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(projects.firstOrNull { it.id == mapping[source.id] }?.name ?: "选择工作区")
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = {
                                mapping = mapping + (source.id to project.id); expanded = false; error = null
                            }) }
                        }
                    }
                }
                TextButton(onClick = { adding = true }, enabled = !busy) { Text("添加工作区") }
                Text("只读访问仍保持只读。曾使用多个工作区的聊天，需要分别为每个工作区选择文件夹。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && preview.missingWorkspaces.all { source -> projects.any { it.id == mapping[source.id] } }, onClick = {
                busy = true; error = null
                scope.launch {
                    try { onImported(container.chatBackup.importPrepared(preview, mapping)) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "无法恢复聊天。" }
                    finally { busy = false }
                }
            }) { Text(if (busy) "正在恢复…" else "恢复聊天") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } })
    if (adding) com.androidharness.app.ui.common.AddWorkspaceDialog(container, onDismiss = { adding = false },
        onSelected = { adding = false }, onPickSaf = { adding = false; picker.launch(null) }, activateWorkspace = false)
}
