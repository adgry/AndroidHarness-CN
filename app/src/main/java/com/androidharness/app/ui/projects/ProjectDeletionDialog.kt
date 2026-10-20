package com.androidharness.app.ui.projects

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.data.db.ProjectEntity
import com.androidharness.app.workspace.ProjectDeletionPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ProjectDeletionDialog(
    container: AppContainer,
    project: ProjectEntity,
    onDismiss: () -> Unit,
    onDeleted: (Set<String>) -> Unit,
) {
    var preview by remember(project.id) { mutableStateOf<ProjectDeletionPreview?>(null) }
    var error by remember(project.id) { mutableStateOf<String?>(null) }
    var deleting by remember(project.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(project.id) {
        try { preview = container.projectDeletion.preview(project.id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法检查此项目。" }
    }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("删除项目？") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall)
                val review = preview
                if (review == null && error == null) Text("正在检查项目…")
                if (review != null) {
                    val chats = "${review.chatIds.size} 个已保存聊天"
                    val automations = "${review.automationIds.size} 个自动化"
                    Text("将从应用中移除此项目、$chats 和 $automations。这些聊天的消息记录和撤销历史也会删除，此操作无法撤销。")
                    Text("磁盘上的文件夹和文件会保留。", style = MaterialTheme.typography.bodyMedium)
                    review.blocker?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting && error == null && preview?.blocker == null && preview != null,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = {
                    val review = preview ?: return@TextButton
                    deleting = true
                    scope.launch {
                        try { container.projectDeletion.delete(review); onDeleted(review.chatIds) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "无法删除此项目。" }
                        finally { deleting = false }
                    }
                },
            ) { Text(if (deleting) "正在删除…" else "删除项目") }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text("保留项目") } },
    )
}
