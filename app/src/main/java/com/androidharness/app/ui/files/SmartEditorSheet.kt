package com.androidharness.app.ui.files

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.ui.common.ExpressiveIconButton
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

internal enum class SmartEditorAction(val title: String, val operation: String) {
    OUTLINE("代码大纲", "outline"), DEFINITION("转到定义", "definition"),
    REFERENCES("查找引用", "references"), RENAME("重命名符号", "rename"), FORMAT("格式化文档", "format"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SmartEditorSheet(action: SmartEditorAction, path: String, editor: CodeEditor,
    buffer: EditorBuffer, controller: SmartEditorController, onDismiss: () -> Unit,
    onOpenLocation: ((SmartLocation) -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { editor.text.toString() }
    val offset = remember { editor.cursor.left }
    var loading by remember { mutableStateOf(action != SmartEditorAction.RENAME) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<SmartAnswer?>(null) }
    var rename by remember { mutableStateOf<SmartRenamePlan?>(null) }
    var patches by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var applied by remember { mutableStateOf(false) }
    fun current() = !buffer.saving.value && editor.text.toString() == source
    suspend fun load(newName: String? = null) {
        loading = true; error = null
        try {
            check(current()) { "文档已更改，请关闭此面板后重试。" }
            val result = controller.request(action.operation, path, source, offset, newName)
            check(current()) { "文档已更改，请关闭此面板后重试。" }
            if (action == SmartEditorAction.FORMAT) {
                val formatted = result.result.jsonPrimitive.content
                if (formatted != source) {
                    val change = minimalSmartEdit(source, formatted)
                    editor.isEditable = true; buffer.editing.value = true
                    editor.text.beginBatchEdit()
                    try { editor.text.replace(change.first, change.second, change.third) } finally { editor.text.endBatchEdit() }
                    val pos = editor.text.indexer.getCharPosition(offset.coerceAtMost(formatted.length))
                    editor.setSelection(pos.line, pos.column)
                }
                Toast.makeText(context, if(formatted == source) "已格式化" else "文档已格式化 · 可撤销", Toast.LENGTH_SHORT).show()
                onDismiss()
            } else if (action == SmartEditorAction.RENAME) {
                val edits = result.result.jsonArray.map { item -> item.jsonObject.let {
                    SmartTextEdit(it.getValue("path").jsonPrimitive.content, it.getValue("start").jsonPrimitive.int,
                        it.getValue("end").jsonPrimitive.int, it.getValue("text").jsonPrimitive.content)
                } }
                rename = SmartRenamePlan(result.snapshot, edits)
                check(rename!!.changes.isNotEmpty()) { "新名称未产生更改。" }
                patches = rename!!.changes.mapValues { (file, text) ->
                    com.androidharness.app.core.Diff.unified(result.snapshot.sources.getValue(file).text, text, file)
                }
            }
            answer = result
        } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "语言操作未能完成。" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) {
        editor.hideEditorWindows(); editor.hideSoftInput()
        if (action != SmartEditorAction.RENAME) load()
    }
    ModalBottomSheet(onDismissRequest = { if (!loading || action != SmartEditorAction.RENAME) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(action.title, style = MaterialTheme.typography.titleLargeEmphasized)
                    Text(path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ExpressiveIconButton(onClick = onDismiss, enabled = !loading || action != SmartEditorAction.RENAME) { Icon(Icons.Outlined.Close, "关闭代码工具") }
            }
            Spacer(Modifier.height(12.dp))
            if (action == SmartEditorAction.OUTLINE) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text("查找符号") }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) }, shape = RoundedCornerShape(18.dp))
            if (action == SmartEditorAction.RENAME && !applied) {
                OutlinedTextField(name, { name = it; rename = null; error = null }, Modifier.fillMaxWidth(),
                    label = { Text("新符号名称") }, singleLine = true, enabled = !loading, shape = RoundedCornerShape(18.dp))
                Text("请审阅所有受影响的文件。更改将作为草稿打开，确认后请逐个保存。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp))
                if(rename == null) Button(onClick = { scope.launch { load(name.trim()) } }, enabled = name.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth()) { Text("审阅重命名") }
            }
            if (loading) Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text(if(action == SmartEditorAction.FORMAT) "正在格式化草稿…" else "正在读取项目符号…")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)) }
            if (applied) {
                val count = rename?.changes?.size ?: 0
                Text("已在 $count 个文件中重命名。请审阅打开的草稿，确认后保存。", modifier = Modifier.padding(vertical = 16.dp))
                Button(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("返回编辑器") }
            } else if (!loading) {
                val result = answer
                if (action == SmartEditorAction.OUTLINE && result != null) {
                    val symbols = result.result.jsonArray.map { item -> item.jsonObject.let {
                        SmartSymbol(it.getValue("name").jsonPrimitive.content, it.getValue("kind").jsonPrimitive.content,
                            it.getValue("start").jsonPrimitive.int, it.getValue("end").jsonPrimitive.int,
                            it.getValue("line").jsonPrimitive.int, it.getValue("depth").jsonPrimitive.int)
                    } }.filter { query.isBlank() || it.name.contains(query, true) }
                    Text("${symbols.size} 个符号", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 12.dp))
                    if(symbols.isEmpty()) Text(if(query.isBlank()) "此文档没有符号。" else "没有匹配的符号。", style = MaterialTheme.typography.bodyMedium)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(symbols, key = { index, symbol -> "$index:${symbol.start}" }) { _, symbol ->
                            SmartResultRow(symbol.name, "${symbolKind(symbol.kind)} · 第 ${symbol.line} 行", depth = symbol.depth) {
                                if(current()) { val pos = editor.text.indexer.getCharPosition(symbol.start); editor.setSelection(pos.line, pos.column); editor.ensurePositionVisible(pos.line, pos.column); onDismiss() }
                                else error = "文档已更改，请重新打开大纲。"
                            }
                        }
                    }
                } else if ((action == SmartEditorAction.DEFINITION || action == SmartEditorAction.REFERENCES) && result != null) {
                    val locations = result.result.jsonArray.map { item -> item.jsonObject.let {
                        SmartLocation(it.getValue("path").jsonPrimitive.content, it.getValue("start").jsonPrimitive.int,
                            it.getValue("line").jsonPrimitive.int, it.getValue("column").jsonPrimitive.int, it.getValue("preview").jsonPrimitive.content)
                    } }
                    Text(if(locations.isEmpty()) "未找到项目位置，请将光标置于符号上后重试。" else "${locations.size} 个位置",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp))
                    Text(result.snapshot.notice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(locations, key = { index, item -> "$index:${item.path}:${item.start}" }) { _, item ->
                            SmartResultRow("${item.path} · ${item.line}:${item.column}", item.preview) {
                                if (!current()) error = "文档已更改，请关闭此面板后重试。"
                                else if (item.path == path) { val pos = editor.text.indexer.getCharPosition(item.start); editor.setSelection(pos.line, pos.column); editor.ensurePositionVisible(pos.line, pos.column); onDismiss() }
                                else { onDismiss(); onOpenLocation?.invoke(item) }
                            }
                        }
                    }
                } else if (action == SmartEditorAction.RENAME && rename != null) {
                    val plan = rename!!
                    Text("${plan.changes.size} 个文件中的 ${plan.edits.size} 个引用", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 12.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(plan.changes.entries.toList(), key = { _, entry -> entry.key }) { _, entry ->
                            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(entry.key, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    val patch = patches[entry.key].orEmpty()
                                    com.androidharness.app.ui.common.VisualDiffViewer(patch, filePath = entry.key, maxHeight = 300.dp, showFileHeader = false)
                                }
                            }
                        }
                    }
                    Button(onClick = { scope.launch {
                        loading = true; error = null
                        try { check(current()) { "文档已更改，请重试。" }; controller.applyRename(plan); applied = true }
                        catch(e: Exception) { if(e is CancellationException) throw e; error = e.message ?: "无法应用重命名。" }
                        finally { loading = false }
                    } }, enabled = !loading && plan.changes.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("在 ${plan.changes.size} 个文件中重命名") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SmartResultRow(title: String, detail: String, depth: Int = 0, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = (12 + depth.coerceAtMost(3) * 12).dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun symbolKind(kind: String): String = when(kind) {
    "1" -> "文件"; "2" -> "模块"; "3" -> "命名空间"; "4" -> "包"; "5" -> "类"; "6" -> "方法"
    "7" -> "属性"; "8" -> "字段"; "9" -> "构造函数"; "10" -> "枚举"; "11" -> "接口"; "12" -> "函数"
    "13" -> "变量"; "14" -> "常量"; "15" -> "字符串"; "16" -> "数字"; "17" -> "布尔值"; "18" -> "数组"
    "19" -> "对象"; "20" -> "键"; "21" -> "空值"; "22" -> "枚举成员"; "23" -> "结构体"; "24" -> "事件"
    "25" -> "运算符"; "26" -> "类型参数"; else -> kind.replaceFirstChar { it.uppercase() }
}

internal fun minimalSmartEdit(before: String, after: String): Triple<Int, Int, String> {
    var start = 0
    while (start < before.length && start < after.length && before[start] == after[start]) start++
    var oldEnd = before.length; var newEnd = after.length
    while (oldEnd > start && newEnd > start && before[oldEnd - 1] == after[newEnd - 1]) { oldEnd--; newEnd-- }
    return Triple(start, oldEnd, after.substring(start, newEnd))
}
