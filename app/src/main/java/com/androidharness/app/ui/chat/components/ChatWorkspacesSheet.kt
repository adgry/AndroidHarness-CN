package com.androidharness.app.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidharness.app.data.db.ProjectEntity
import com.androidharness.app.workspace.ChatWorkspaceGrant
import com.androidharness.app.workspace.ChatWorkspaceSelection
import com.androidharness.app.workspace.WorkspaceDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatWorkspaceManagerSheet(
    container: com.androidharness.app.AppContainer,
    sessionId: String?,
    onDismiss: () -> Unit,
    onSave: (suspend (ChatWorkspaceSelection) -> Unit)? = null,
) {
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val selected by remember(sessionId) { container.workspace.selectionForChat(sessionId) }.collectAsStateWithLifecycle(initialValue = null)
    val primary by remember(sessionId) { container.workspace.projectForChat(sessionId) }.collectAsStateWithLifecycle(initialValue = null)
    val initial = selected ?: primary?.let { ChatWorkspaceSelection.single(it.id) }
        ?: projects.firstOrNull()?.let { ChatWorkspaceSelection.single(it.id) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try { container.workspace.addPickedFolder(uri, activate = false) }
            catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "无法添加工作区", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    if (initial != null) ChatWorkspacesSheet(projects, initial, container.workspace::describe,
        onSave = { value -> if (onSave != null) onSave(value) else container.runManager.assignWorkspaces(requireNotNull(sessionId), value) },
        onDismiss = onDismiss, onAdd = { adding = true })
    if (adding) com.androidharness.app.ui.common.AddWorkspaceDialog(container,
        onSelected = { adding = false }, onDismiss = { adding = false },
        onPickSaf = { adding = false; picker.launch(null) }, activateWorkspace = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWorkspacesSheet(
    projects: List<ProjectEntity>,
    selection: ChatWorkspaceSelection,
    describe: (ProjectEntity) -> WorkspaceDescription,
    onSave: suspend (ChatWorkspaceSelection) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    var draftJson by rememberSaveable(selection) { mutableStateOf(ChatWorkspaceSelection.encode(selection)) }
    // Empty drafts are allowed while choosing; validation belongs to Save.
    val draft = remember(draftJson) { kotlinx.serialization.json.Json.decodeFromString<ChatWorkspaceSelection>(draftJson) }
    fun update(value: ChatWorkspaceSelection) { draftJson = kotlinx.serialization.json.Json.encodeToString(ChatWorkspaceSelection.serializer(), value) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("此聊天的工作区", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Filled.Close, "关闭且不保存") }
            }
            Text("为每个项目选择访问权限。新文件默认保存在主要工作区。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 430.dp)) {
                items(projects, key = { it.id }) { project ->
                    val grant = draft.grants.firstOrNull { it.projectId == project.id }
                    var accessMenu by remember { mutableStateOf(false) }
                    val desc = describe(project)
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().toggleable(value = grant != null, enabled = !saving, role = Role.Checkbox) { checked ->
                            val grants = if (checked) draft.grants + ChatWorkspaceGrant(project.id) else draft.grants.filterNot { it.projectId == project.id }
                            update(draft.copy(grants = grants, primaryId = if (grants.any { it.projectId == draft.primaryId }) draft.primaryId else grants.firstOrNull()?.projectId.orEmpty()))
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = grant != null, onCheckedChange = null, modifier = Modifier.padding(end = 10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(project.name, style = MaterialTheme.typography.titleSmall)
                                Text(desc.kindLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (grant != null) FlowRow(Modifier.padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box {
                                OutlinedButton(onClick = { accessMenu = true }, enabled = !saving,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.semantics { contentDescription = "${project.name} 访问权限： ${if (grant.canEdit) "可编辑" else "只读"}" }) {
                                    Text(if (grant.canEdit) "可编辑" else "只读", style = MaterialTheme.typography.labelMedium)
                                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp))
                                }
                                DropdownMenu(accessMenu, { accessMenu = false }) {
                                    listOf(false to "只读", true to "可编辑").forEach { (canEdit, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = {
                                            update(draft.copy(grants = draft.grants.map { if (it.projectId == project.id) it.copy(canEdit = canEdit) else it }))
                                            accessMenu = false
                                        })
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.toggleable(draft.primaryId == project.id, enabled = !saving, role = Role.RadioButton) {
                                    update(draft.copy(primaryId = project.id))
                                }.heightIn(min = 44.dp).semantics { contentDescription = "主要工作区：${project.name}" }) {
                                RadioButton(draft.primaryId == project.id, onClick = null)
                                Text("主要", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
                val missing = draft.grants.filter { grant -> projects.none { it.id == grant.projectId } }
                items(missing, key = { "missing-${it.projectId}" }) { grant ->
                    TextButton(enabled = !saving, onClick = {
                        val remaining = draft.grants - grant
                        update(draft.copy(grants = remaining, primaryId = if (draft.primaryId == grant.projectId) remaining.firstOrNull()?.projectId.orEmpty() else draft.primaryId))
                    }) { Text("移除不可用的工作区") }
                }
                if (onAdd != null) item { TextButton(onClick = onAdd, enabled = !saving) { Text("添加工作区…") } }
            }
            if (draft.grants.any { !it.canEdit }) Text("命令和集成需要对所有所选工作区拥有编辑权限。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp))
            if (draft.grants.size > 1) Text("多工作区聊天暂不支持工作区 MCP 服务器。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Button(enabled = !saving && draft.grants.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), onClick = {
                saving = true; error = null
                scope.launch {
                    try { onSave(draft.validated()); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "无法保存工作区访问权限。" }
                    finally { saving = false }
                }
            }) { Text(if (saving) "正在保存…" else "使用 ${draft.grants.size} 个工作区") }
        }
    }
}
