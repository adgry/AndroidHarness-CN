package com.androidharness.app.ui.files

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.core.Diff
import com.androidharness.app.ui.common.ExpressiveIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class WorkspaceToolMode { FILES, SEARCH, COMMANDS }
internal val LocalWorkspaceToolLauncher = staticCompositionLocalOf<((WorkspaceToolMode) -> Unit)?> { null }
internal data class WorkspaceCommand(val title: String, val detail: String, val icon: ImageVector, val action: () -> Unit)
private data class ReplacementPreview(val change: ProjectReplacement, val diff: String, val blocked: String?)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WorkspaceToolsSheet(
    container: AppContainer,
    sessionId: String?,
    projectId: String?,
    initialMode: WorkspaceToolMode,
    onDismiss: () -> Unit,
    onOpenFile: (String, Int?) -> Unit,
    commands: List<WorkspaceCommand> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fs by remember(sessionId, projectId) { container.workspace.forTools(sessionId, projectId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var query by rememberSaveable(mode) { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var replaceOpen by rememberSaveable { mutableStateOf(false) }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var regex by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var include by rememberSaveable { mutableStateOf("") }
    var exclude by rememberSaveable { mutableStateOf("") }
    var index by remember(fs) { mutableStateOf<WorkspaceFileIndex?>(null) }
    var result by remember(fs) { mutableStateOf<ProjectSearchResult?>(null) }
    var resultOptions by remember { mutableStateOf<ProjectSearchOptions?>(null) }
    var busy by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var error by remember(fs) { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<ReplacementPreview>?>(null) }
    var previewFs by remember { mutableStateOf<com.androidharness.app.workspace.WorkspaceFs?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var receipt by remember { mutableStateOf<ReplacementReceipt?>(null) }
    val options = ProjectSearchOptions(query, matchCase, regex, include, exclude)
    val focus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var highlighted by remember(mode, query) { mutableIntStateOf(0) }
    val currentFs = fs
    LaunchedEffect(mode, sheetState.currentValue) {
        // Requesting focus during the opening animation can leave a blank sheet with a hardware keyboard.
        if (sheetState.currentValue == SheetValue.Expanded) focus.requestFocus()
    }

    LaunchedEffect(currentFs) {
        index = null
        if (currentFs == null) return@LaunchedEffect
        try { index = withContext(Dispatchers.IO) { WorkspaceSearch.index(currentFs) } }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "无法读取工作区。" }
    }
    LaunchedEffect(currentFs, index, options, mode, receipt) {
        result = null; resultOptions = null; error = null
        if (mode != WorkspaceToolMode.SEARCH || query.isEmpty() || preview != null || receipt != null) return@LaunchedEffect
        val f = currentFs ?: return@LaunchedEffect
        val files = index ?: return@LaunchedEffect
        busy = true
        try {
            delay(250)
            result = withContext(Dispatchers.IO) { WorkspaceSearch.search(f, files, options) }
            resultOptions = options
        } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "搜索失败。" }
        finally { busy = false }
    }
    val files = remember(index, query) {
        WorkspaceSearch.quickOpen(index?.paths.orEmpty(), WorkspaceSearch.quickOpenQuery(query).name)
    }
    val line = WorkspaceSearch.quickOpenQuery(query).line
    fun open(path: String, atLine: Int? = null) { onDismiss(); onOpenFile(path, atLine) }
    fun changeMode(next: WorkspaceToolMode) { if (!busy && !applying) { mode = next; preview = null; receipt = null; error = null } }
    val allCommands = listOf(
        WorkspaceCommand("快速打开", "按名称查找文件 · Ctrl+P", Icons.Outlined.FindInPage) { changeMode(WorkspaceToolMode.FILES) },
        WorkspaceCommand("在项目中搜索和替换", "跨文件查找文字 · Ctrl+Shift+F", Icons.Outlined.ManageSearch) { changeMode(WorkspaceToolMode.SEARCH) },
    ) + commands
    val matchingCommands = allCommands.filter { query.isBlank() || (it.title + " " + it.detail).contains(query.trim(), true) }
    fun activateHighlighted() {
        when(mode) {
            WorkspaceToolMode.FILES -> {
                val target = WorkspaceSearch.quickOpenQuery(query)
                val current = WorkspaceSearch.quickOpen(index?.paths.orEmpty(), target.name)
                current.getOrNull(highlighted.coerceAtMost((current.size - 1).coerceAtLeast(0)))?.let { open(it, target.line) }
            }
            WorkspaceToolMode.COMMANDS -> {
                val current = allCommands.filter { query.isBlank() || (it.title + " " + it.detail).contains(query.trim(), true) }
                current.getOrNull(highlighted.coerceAtMost((current.size - 1).coerceAtLeast(0)))?.action?.invoke()
            }
            WorkspaceToolMode.SEARCH -> Unit
        }
    }
    suspend fun hasDraft(path: String): Boolean {
        val f = currentFs ?: return true
        return container.editorDrafts.read(EditorDraftStore.key(sessionId, projectId, f.displayPath, path)) != null
    }
    fun review() {
        val found = result ?: return
        if (resultOptions != options || busy) return
        keyboard?.hide()
        busy = true; error = null
        scope.launch {
            try {
                val changes = withContext(Dispatchers.Default) { WorkspaceSearch.replacements(found, options, replacement) }
                val rows = changes.map { change -> ReplacementPreview(change,
                    Diff.unified(change.source.decoded.text, change.text, change.source.path), withContext(Dispatchers.IO) {
                        val f = currentFs
                        when {
                            f is com.androidharness.app.workspace.ChatWorkspaceFs && !f.target(change.source.path).first.canEdit -> "只读工作区"
                            hasDraft(change.source.path) -> "未保存的草稿 · 请先保存或放弃"
                            else -> null
                        }
                    }) }
                preview = rows
                previewFs = currentFs
                selected = rows.filter { it.blocked == null }.map { it.change.source.path }.toSet()
                expanded = rows.firstOrNull()?.let { setOf(it.change.source.path) }.orEmpty()
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "无法预览替换。" }
            finally { busy = false }
        }
    }
    fun apply() {
        val f = previewFs ?: return
        if (currentFs?.displayPath != f.displayPath) { error = "工作区已更改，请重新打开项目搜索以查看文件。"; return }
        val chosen = preview.orEmpty().filter { it.change.source.path in selected }.map { it.change }
        if (chosen.isEmpty() || applying) return
        applying = true; error = null
        container.editorScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    container.runManager.withIdleWorkspace(f, "代理正在此工作区中运行，请待其完成后重新审阅替换。") {
                        WorkspaceSearch.apply(f, chosen, ::hasDraft) { change ->
                            sessionId?.let { sid ->
                                val counts = Diff.lineCounts(change.source.decoded.text, change.text)
                                container.sessions.recordFileChange(sid, change.source.path, counts.first.toLong(), counts.second.toLong(),
                                    existedBefore = true, existsAfter = true, beforeText = change.source.decoded.text)
                            }
                        }
                    }
                }
                container.editorTabs.invalidateSaved(f.displayPath, outcome.saved.toSet())
                receipt = outcome; preview = null; result = null
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message ?: "替换失败。" }
            finally { applying = false }
        }
    }

    MaterialTheme(motionScheme = MotionScheme.expressive()) {
        ModalBottomSheet(
            onDismissRequest = { if (!applying) onDismiss() },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (preview != null) "审阅替换" else when (mode) {
                            WorkspaceToolMode.FILES -> "快速打开"
                            WorkspaceToolMode.SEARCH -> "项目搜索"
                            WorkspaceToolMode.COMMANDS -> "命令面板"
                        }, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(if (preview != null) "选择要更新的文件" else "当前工作区", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ExpressiveIconButton(onClick = { if (!applying) onDismiss() }, enabled = !applying) { Icon(Icons.Outlined.Close, "关闭工作区工具") }
                }
                Spacer(Modifier.height(12.dp))
                val reviewed = preview
                val completed = receipt
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "workspace-controls") {
                        Column(Modifier.fillMaxWidth()) {
                            if(currentFs == null && mode != WorkspaceToolMode.COMMANDS) {
                                Text("打开“文件”并选择工作区，以使用项目工具。", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                                commands.firstOrNull { it.title == "打开文件" }?.let { command -> TextButton(onClick = command.action) { Text("打开文件") } }
                            }
                            if (preview == null && receipt == null) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(WorkspaceToolMode.FILES to "文件", WorkspaceToolMode.SEARCH to "搜索", WorkspaceToolMode.COMMANDS to "命令").forEach { (item, label) ->
                                        FilterChip(selected = mode == item, onClick = { changeMode(item) }, enabled = !busy, label = { Text(label) })
                                    }
                                }
                                OutlinedTextField(query, { query = it; error = null }, Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                                        if(mode != WorkspaceToolMode.SEARCH && event.type == KeyEventType.KeyDown) {
                                            val count = if(mode == WorkspaceToolMode.FILES) files.size else matchingCommands.size
                                            when(event.key) {
                                                Key.DirectionDown -> { highlighted = (highlighted + 1).coerceAtMost((count - 1).coerceAtLeast(0)); true }
                                                Key.DirectionUp -> { highlighted = (highlighted - 1).coerceAtLeast(0); true }
                                                Key.Enter, Key.NumPadEnter -> { activateHighlighted(); true }
                                                else -> false
                                            }
                                        } else false
                                    }.semanticsDescription(when(mode) { WorkspaceToolMode.FILES -> "文件名或路径"; WorkspaceToolMode.SEARCH -> "在项目中查找"; WorkspaceToolMode.COMMANDS -> "搜索命令" }),
                                    enabled = !applying, singleLine = true, shape = RoundedCornerShape(24.dp),
                                    label = { Text(when(mode) { WorkspaceToolMode.FILES -> "文件名或路径"; WorkspaceToolMode.SEARCH -> "在项目中查找"; WorkspaceToolMode.COMMANDS -> "搜索命令" }) },
                                    leadingIcon = { Icon(if (mode == WorkspaceToolMode.COMMANDS) Icons.Outlined.Terminal else Icons.Outlined.Search, null) },
                                    trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清空搜索") } },
                                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        activateHighlighted()
                                    }))
                                if (mode == WorkspaceToolMode.SEARCH) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(matchCase, { matchCase = !matchCase }, label = { Text("Aa") }, modifier = Modifier.semanticsDescription("区分大小写"))
                                        FilterChip(regex, { regex = !regex }, label = { Text(".*") }, modifier = Modifier.semanticsDescription("正则表达式"))
                                        FilterChip(filtersOpen, { filtersOpen = !filtersOpen }, label = { Text("筛选条件") })
                                        FilterChip(replaceOpen, { replaceOpen = !replaceOpen }, label = { Text("替换") })
                                    }
                                    if (filtersOpen) {
                                        OutlinedTextField(include, { include = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("包含文件") }, placeholder = { Text("**/*.kt, **/*.js") }, shape = RoundedCornerShape(16.dp))
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(exclude, { exclude = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("排除文件") }, placeholder = { Text("**/generated/**") }, shape = RoundedCornerShape(16.dp))
                                    }
                                    if (replaceOpen) {
                                        Spacer(Modifier.height(6.dp))
                                        OutlinedTextField(replacement, { replacement = it }, Modifier.fillMaxWidth().semanticsDescription("替换为"), singleLine = true, label = { Text("替换为") },
                                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), supportingText = if(regex) {{ Text("使用 \$1 引用捕获组") }} else null, shape = RoundedCornerShape(20.dp))
                                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                            Button(onClick = ::review, enabled = !busy && resultOptions == options && result?.files?.isNotEmpty() == true && result?.limited == false) {
                                                Icon(Icons.Outlined.Difference, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("审阅更改")
                                            }
                                        }
                                    }
                                }
                            }
                            if (busy || applying || currentFs != null && index == null && mode != WorkspaceToolMode.COMMANDS && error == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            error?.let { Text(it, Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                        }
                    }
                    when {
                        completed != null -> {
                            item { Text("已更新 ${completed.saved.size} 个文件", style = MaterialTheme.typography.titleMedium) }
                            items(completed.saved, key = { "saved:$it" }) { path -> ListItem(headlineContent = { Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis) }, leadingContent = { Icon(Icons.Outlined.CheckCircle, null) }) }
                            items(completed.failed.toList(), key = { "failed:${it.first}" }) { (path, failure) -> ListItem(headlineContent = { Text(path) }, supportingContent = { Text(failure, color = MaterialTheme.colorScheme.error) }) }
                            item { TextButton(onClick = { receipt = null; query = ""; result = null }) { Text("新搜索") } }
                        }
                        reviewed != null -> {
                            if (reviewed.isEmpty()) item { Text("没有更改：替换结果与原文字相同。") }
                            items(reviewed, key = { it.change.source.path }) { row ->
                                val path = row.change.source.path
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(path in selected, { checked -> selected = if(checked) selected + path else selected - path }, enabled = row.blocked == null && !applying)
                                            Column(Modifier.weight(1f)) {
                                                Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(path.substringBeforeLast('/', "工作区根目录"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(row.blocked ?: "${row.change.source.count} 个匹配项", style = MaterialTheme.typography.labelSmall,
                                                    color = if(row.blocked != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { expanded = if(path in expanded) expanded - path else expanded + path }, modifier = Modifier.semanticsDescription("预览 $path")) { Icon(if(path in expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                                        }
                                        if(path in expanded) {
                                            HorizontalDivider()
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                val colors = MaterialTheme.colorScheme
                                                val success = com.androidharness.app.ui.theme.LocalStatusColors.current.success
                                                val diff = androidx.compose.ui.text.buildAnnotatedString {
                                                    row.diff.lineSequence().forEach { line ->
                                                        val color = when { line.startsWith("+++") || line.startsWith("---") -> colors.onSurfaceVariant; line.startsWith('+') -> success; line.startsWith('-') -> colors.error; else -> colors.onSurfaceVariant }
                                                        pushStyle(androidx.compose.ui.text.SpanStyle(color = color)); append(line); append('\n'); pop()
                                                    }
                                                }
                                                Text(diff, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        mode == WorkspaceToolMode.COMMANDS -> {
                            if(matchingCommands.isEmpty()) item { Text("没有匹配的命令", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(matchingCommands, key = { it.title }) { command ->
                                Surface(onClick = command.action, shape = RoundedCornerShape(20.dp), color = if(command == matchingCommands.getOrNull(highlighted)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    ListItem(headlineContent = { Text(command.title, style = MaterialTheme.typography.titleSmall) }, supportingContent = { Text(command.detail) }, leadingContent = { Icon(command.icon, null) },
                                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                                }
                            }
                        }
                        mode == WorkspaceToolMode.FILES -> {
                            item { Text(if(query.isBlank()) "项目文件" else "${files.size} 个匹配文件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if(files.isEmpty() && index != null) item { Text("没有匹配的文件", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(files, key = { it }) { path ->
                                Surface(onClick = { open(path, line) }, modifier = Modifier.semanticsDescription("打开文件 $path"), shape = RoundedCornerShape(18.dp), color = if(path == files.getOrNull(highlighted)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    ListItem(headlineContent = { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(path.substringBeforeLast('/', "工作区根目录"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = { Icon(Icons.Outlined.Description, null) }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                                }
                            }
                            if(index?.limited == true) item { Text("文件列表最多显示 10,000 个文件。", style = MaterialTheme.typography.bodySmall) }
                        }
                        mode == WorkspaceToolMode.SEARCH -> {
                            val found = result
                            item {
                                Text(when { query.isEmpty() -> "搜索此项目中的文件内容"; busy -> "正在搜索…"; found == null -> ""; found.files.isEmpty() -> "没有匹配项"; else -> "${found.files.size} 个文件中有 ${found.count} 个匹配项" },
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if(found?.limited == true) item { Text("结果数量受限，请缩小搜索范围或增加包含筛选条件，以启用替换。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            if(found != null && found.skipped > 0) item { Text("已跳过 ${found.skipped} 个二进制、大型或无法读取的文件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            found?.files?.forEach { file ->
                                item(key = "file:${file.path}") {
                                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                        Text(file.path, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(file.count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                items(file.hits, key = { "hit:${file.path}:${it.line}:${it.column}" }) { hit ->
                                    Surface(onClick = { open(file.path, hit.line) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(hit.line.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(hit.preview.ifEmpty { "↵" }, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if(reviewed != null) {
                    HorizontalDivider()
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { preview = null; error = null }, enabled = !applying) { Text("返回搜索") }
                        Button(onClick = ::apply, enabled = selected.isNotEmpty() && !applying) { Text(if(applying) "正在替换…" else "在 ${selected.size} 个文件中替换") }
                    }
                }
            }
        }
    }
}

private fun Modifier.semanticsDescription(label: String) = this.then(Modifier.semantics { contentDescription = label })
