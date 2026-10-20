package com.androidharness.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.androidharness.app.data.env.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun RootBrowserDialog(access: DeviceAccessManager, projectId: String?, sessionId: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var path by rememberSaveable { mutableStateOf("/") }
    var directory by remember { mutableStateOf<RootDirectory?>(null) }
    val textSaver = remember { listSaver<RootText?, String>(
        save = { file -> file?.let { listOf(it.path, it.text, it.hash) }.orEmpty() },
        restore = { values -> if (values.size == 3) RootText(values[0], values[1], values[2]) else null },
    ) }
    var opened by rememberSaveable(stateSaver = textSaver) { mutableStateOf<RootText?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var edit by rememberSaveable { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var leave by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestLeave(action: () -> Unit) {
        if (edit && draft != opened?.text) leave = action else action()
    }
    suspend fun load(offset: Int = 0) {
        busy = true; error = null
        try {
            val page = access.rootFiles(projectId, sessionId) { Json.decodeFromString(RootDirectory.serializer(), it.rootList(path, offset)) }
            directory = if (offset == 0) page else page.copy(entries = directory?.entries.orEmpty() + page.entries)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法读取此文件夹。" }
        finally { busy = false }
    }
    LaunchedEffect(path) { load() }
    Dialog(onDismissRequest = { requestLeave(onDismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { requestLeave {
                        if (opened != null) { opened = null; edit = false }
                        else if (path != "/") { directory = null; path = path.substringBeforeLast('/').ifBlank { "/" } }
                        else onDismiss()
                    } }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("Root 文件浏览器", style = MaterialTheme.typography.titleMedium)
                        Text(opened?.path ?: path, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    IconButton(onClick = { requestLeave(onDismiss) }) { Icon(Icons.Outlined.Close, "关闭 Root 文件浏览器") }
                }
                HorizontalDivider()
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val file = opened
                if (file != null) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (edit) "编辑中" else "只读", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        if (!edit) FilledTonalButton(onClick = { edit = true }) { Text("编辑文件") }
                        else {
                            TextButton(onClick = { draft = file.text; edit = false }) { Text("放弃") }
                            Button(onClick = { confirmSave = true }, enabled = !busy && draft != file.text) { Text("保存") }
                        }
                    }
                    OutlinedTextField(draft, onValueChange = { if (edit) draft = it }, readOnly = !edit,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(directory?.entries.orEmpty(), key = { it.path }) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name) },
                                supportingContent = { Text(if (entry.directory) "文件夹" else "${entry.size} 字节") },
                                leadingContent = { Icon(if (entry.directory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile, null) },
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    if (entry.directory) { directory = null; path = entry.path }
                                    else scope.launch {
                                        busy = true; error = null
                                        try {
                                            opened = access.rootFiles(projectId, sessionId) { Json.decodeFromString(RootText.serializer(), it.rootRead(entry.path)) }
                                            draft = opened!!.text; edit = false
                                        } catch (e: CancellationException) { throw e }
                                        catch (e: Exception) { error = e.message ?: "无法将此文件作为文本打开。" }
                                        finally { busy = false }
                                    }
                                },
                            )
                        }
                        directory?.nextOffset?.let { next -> item { TextButton(onClick = { scope.launch { load(next) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } } }
                    }
                }
            }
        }
    }
    leave?.let { action -> AlertDialog(onDismissRequest = { leave = null }, title = { Text("放弃更改？") },
        text = { Text("对此 Root 文件的更改尚未保存。") },
        confirmButton = { TextButton(onClick = { leave = null; action() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { leave = null }) { Text("继续编辑") } }) }
    if (confirmSave) AlertDialog(onDismissRequest = { confirmSave = false }, title = { Text("保存 Root 文件？") },
        text = { Text("将更改写入 ${opened?.path}？") },
        confirmButton = { Button(onClick = { confirmSave = false; scope.launch {
            val file = opened ?: return@launch
            busy = true; error = null
            try {
                opened = access.rootFiles(projectId, sessionId) { Json.decodeFromString(RootText.serializer(), it.rootWrite(file.path, file.hash, draft)) }
                draft = opened!!.text; edit = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "无法保存文件。" }
            finally { busy = false }
        } }) { Text("保存更改") } },
        dismissButton = { TextButton(onClick = { confirmSave = false }) { Text("取消") } })
}
