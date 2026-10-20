package com.androidharness.app.ui.projects

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.agent.MemoryNotes
import com.androidharness.app.ui.common.AppHeader
import com.androidharness.app.workspace.ProjectMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProjectMemoryScreen(container: AppContainer, projectId: String, onBack: () -> Unit) {
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    var baseline by rememberSaveable(projectId) { mutableStateOf("") }
    var draft by rememberSaveable(projectId) { mutableStateOf("") }
    var loaded by rememberSaveable(projectId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discardFor by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val dirty = loaded && draft != baseline
    val scope = rememberCoroutineScope()

    fun load() {
        val captured = project ?: return
        busy = true
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { ProjectMemory.read(container.workspace.fsFor(captured)) }
                baseline = text; draft = text; loaded = true; error = null; saved = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "无法读取记忆，请在工作区可用后重试。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(project?.id) { if (project != null && !loaded) load() }
    fun leave() { if (!busy) { if (dirty) discardFor = "back" else onBack() } }
    BackHandler { leave() }

    val ssh = remember(project) { project?.let { runCatching { container.workspace.fsFor(it) }.getOrNull() as? com.androidharness.app.workspace.SshFs } }
    Scaffold(topBar = {
        Column {
            AppHeader("记忆", subtitle = project?.name, onBack = ::leave)
            com.androidharness.app.ui.common.SshWorkspaceBar(container, ssh, includeStatusBarPadding = false)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("此项目内各聊天共用的笔记。", style = MaterialTheme.typography.bodyMedium)
            if (project == null) Text("此项目已不可用。")
            else {
                if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (loaded) {
                    OutlinedTextField(draft, onValueChange = { draft = it; saved = false },
                        label = { Text("项目笔记") }, enabled = !busy, minLines = 5, maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("${draft.length} / ${MemoryNotes.MAX_CHARS}") },
                        isError = draft.length > MemoryNotes.MAX_CHARS)
                    if (saved) Text("已保存", color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val captured = project
                            val expected = baseline
                            val content = draft
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        val fs = container.workspace.fsFor(captured)
                                        container.runManager.withIdleWorkspace(fs,
                                            busyMessage = "有任务正在此项目中运行。草稿已保留，请在任务结束后保存。") {
                                            ProjectMemory.save(fs, expected, content)
                                        }
                                    }
                                    baseline = content; error = null; saved = true
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "无法保存记忆。草稿已保留，请重试。" }
                                finally { busy = false }
                            }
                        }, enabled = dirty && !busy && draft.length <= MemoryNotes.MAX_CHARS,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("保存") }
                        OutlinedButton(onClick = { draft = ""; saved = false }, enabled = !busy && draft.isNotEmpty(),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("清空") }
                    }
                }
                TextButton(onClick = { if (dirty) discardFor = "reload" else load() }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (loaded) "重新加载最新笔记" else "重试") }
            }
        }
    }
    if (discardFor != null) AlertDialog(onDismissRequest = { discardFor = null },
        title = { Text("放弃更改？") }, text = { Text("未保存的笔记将会丢失。") },
        confirmButton = { TextButton(onClick = {
            val action = discardFor; discardFor = null
            if (action == "back") onBack() else load()
        }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discardFor = null }) { Text("继续编辑") } })
}
