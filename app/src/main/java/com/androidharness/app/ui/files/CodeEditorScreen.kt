package com.androidharness.app.ui.files

import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.core.Diff
import com.androidharness.app.ui.common.AppHeader
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoFixHigh
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Files above this many bytes are offered only for share/open-with (16 MB). */
private const val MAX_EDIT_BYTES = 16_000_000L

/**
 * Full code editor for workspace files, backed by sora-editor: gutter line
 * numbers, undo/redo, incremental highlighting via [BasicCodeLanguage], and a
 * find bar with match-case + regex toggles plus replace (in edit mode).
 * Saving restores the original BOM/charset/EOL shape. Binary and oversized
 * files degrade to an info card with Share / Open-with instead of pretending
 * to be text.
 *
 * When [sessionId] is set (chat context), manual saves also feed the session's
 * cumulative "文件已更改" tracker, using the load-time content as the
 * diff baseline candidate (the repository keeps the earliest baseline).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CodeEditorScreen(
    container: AppContainer,
    path: String,
    initialLine: Int? = null,
    sessionId: String? = null,
    onBack: () -> Unit,
    navigationGuard: EditorNavigationGuard? = null,
    projectId: String? = null,
    onAskAgent: ((String?, String) -> Unit)? = null,
    suppliedBuffer: EditorBuffer? = null,
    workspaceChrome: (@Composable () -> Unit)? = null,
    actions: EditorActions? = null,
    onQuickOpen: (() -> Unit)? = null,
    onProjectSearch: (() -> Unit)? = null,
    onCommands: (() -> Unit)? = null,
    smart: SmartEditorController? = null,
    onOpenSmartLocation: ((SmartLocation) -> Unit)? = null,
    initialColumn: Int? = null,
    jumpRequest: Long = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val fs by androidx.compose.runtime.key(sessionId, projectId) {
        remember(sessionId, projectId) { container.workspace.forTools(sessionId, projectId) }
            .collectAsStateWithLifecycle(initialValue = null)
    }

    // ---- load state ----
    val draftKey = remember(path, sessionId, projectId, fs?.displayPath) {
        fs?.let { EditorDraftStore.key(sessionId, projectId, it.displayPath, path) }
    }
    var loadState by remember(draftKey) { mutableStateOf<Load>(Load.Loading) }
    val buffers: EditorBufferViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val buffer = remember(draftKey) {
        suppliedBuffer ?: draftKey?.let { buffers.acquire(it) } ?: buffers.buffer
    }
    val diskBaseline = buffer.baseline
    val lastSavedText = buffer.saved
    val decoded = buffer.decoded

    LaunchedEffect(path, fs) {
        if (fs == null) return@LaunchedEffect
        if (buffer.text.value != null && buffer.dirty.value) {
            loadState = Load.Ready
            return@LaunchedEffect
        }
        val key = draftKey ?: return@LaunchedEffect
        try {
            container.editorDrafts.read(key)?.let { draft ->
                buffer.restore(draft)
                loadState = Load.Ready
                return@LaunchedEffect
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) {
            loadState = Load.Failed("无法恢复编辑器草稿，草稿已保留在设备上，已保存的文件未被更改。")
            return@LaunchedEffect
        }
        val node = withContext(Dispatchers.IO) {
            fs?.let { f -> runCatching { f.resolve(path).takeIf { it.exists && it.isFile } }.getOrNull() }
        }
        if (node == null) {
            loadState = Load.Failed("文件不存在或工作区不可用。")
            return@LaunchedEffect
        }
        val fileLength = withContext(Dispatchers.IO) { node.length }
        if (fileLength > MAX_EDIT_BYTES) {
            loadState = Load.TooLarge(fileLength)
            return@LaunchedEffect
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { node.openInputStream()?.use { it.readBytes() } }.getOrNull()
        }
        when {
            bytes == null -> loadState = Load.Failed("无法读取文件。")
            else -> {
                val dec = EditorFileCodec.decode(bytes)
                if (dec == null) loadState = Load.Binary(fileLength)
                else {
                    decoded.value = dec
                    if (buffer.text.value == null || buffer.diskVersion.value != EditorDiskSnapshot(bytes).version) {
                        buffer.document = null
                        buffer.text.value = dec.text
                        diskBaseline.value = dec.text
                        lastSavedText.value = dec.text
                        buffer.diskVersion.value = EditorDiskSnapshot(bytes).version
                        buffer.loaded = true
                    }
                    loadState = Load.Ready
                }
            }
        }
    }

    // ---- editor state ----
    val sourceProject by remember(sessionId, projectId) { container.workspace.projectForTools(sessionId, projectId) }
        .collectAsStateWithLifecycle(initialValue = null)
    var selection by remember(path) { mutableStateOf<SelectedCode?>(null) }
    var selectedLength by remember(path) { mutableIntStateOf(0) }
    var editorRef by remember(draftKey) { mutableStateOf<CodeEditor?>(null) }
    var editRevision by remember(buffer) { mutableIntStateOf(0) }
    var completionRevision by remember(buffer) { mutableIntStateOf(0) }
    var completionAt by remember(buffer) { mutableIntStateOf(0) }
    var editing by buffer.editing
    var dirty by buffer.dirty
    var saving by buffer.saving
    var showExitPrompt by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var smartAction by remember(path) { mutableStateOf<SmartEditorAction?>(null) }
    var languageSupport by remember { mutableStateOf(false) }
    val editorSettings by container.settings.settings.collectAsStateWithLifecycle(initialValue = com.androidharness.app.data.AppSettings())
    val wordwrapOn = editorSettings.editorWordWrap
    val draftStatus by remember(draftKey) { container.editorDrafts.status(draftKey.orEmpty()) }
        .collectAsStateWithLifecycle(initialValue = EditorDraftStatus.IDLE)
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var conflict by remember(buffer) { mutableStateOf<EditorDiskSnapshot?>(null) }
    var conflictThenExit by remember(buffer) { mutableStateOf(false) }
    var compareConflict by remember(buffer) { mutableStateOf(false) }

    fun persistDraft(immediate: Boolean = false) {
        val key = draftKey ?: return
        if (buffer.loaded) container.editorDrafts.submit(key, buffer.draft(), immediate)
    }
    val editorLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(buffer, draftKey, editorLifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) persistDraft(immediate = true)
        }
        editorLifecycle.addObserver(observer)
        onDispose {
            editorLifecycle.removeObserver(observer)
            persistDraft(immediate = true)
        }
    }

    fun editorText(): String = editorRef?.text?.toString().orEmpty()
    fun leave() {
        val action = pendingNavigation ?: onBack
        pendingNavigation = null
        action()
    }
    fun requestNavigation(action: () -> Unit) {
        if (saving) return
        if (dirty && editorText() != lastSavedText.value) {
            pendingNavigation = action
            showExitPrompt = true
        } else action()
    }
    androidx.compose.runtime.SideEffect { navigationGuard?.confirm = ::requestNavigation }
    androidx.compose.runtime.DisposableEffect(navigationGuard) {
        onDispose { navigationGuard?.confirm = null }
    }

    fun save(thenExit: Boolean = false, reviewedVersion: String? = null) {
        if (saving) return
        val dec = decoded.value ?: return
        val ed = editorRef ?: return
        val f = fs ?: return
        val expected = reviewedVersion ?: buffer.diskVersion.value ?: return
        val newText = editorText()
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    container.runManager.withIdleWorkspace(f, "代理正在此工作区中运行，请待其完成后再保存，你的编辑会保留。") {
                        saveEditorFile(f, path, expected, EditorFileCodec.encode(dec, newText))
                    }
                }
            }
            saving = false
            var saved = false
            result
                .onSuccess { outcome ->
                    if (outcome is EditorSaveResult.Conflict) {
                        conflict = outcome.current
                        conflictThenExit = thenExit
                        compareConflict = false
                        return@onSuccess
                    }
                    outcome as EditorSaveResult.Saved
                    saved = true
                    conflict = null
                    buffer.diskVersion.value = outcome.snapshot.version
                    buffer.text.value = editorText()
                    lastSavedText.value = newText
                    dirty = buffer.text.value != newText
                    buffer.recovered.value = false
                    persistDraft(immediate = true)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    // Feed the Files-changed tracker; repository keeps the
                    // earliest baseline even though we resend ours each time.
                    val sid = sessionId
                    if (sid != null) {
                        runCatching {
                            container.sessions.recordFileChange(
                                sessionId = sid,
                                relPath = path,
                                added = Diff.lineCounts(outcome.previous.text.orEmpty(), newText).first.toLong(),
                                removed = Diff.lineCounts(outcome.previous.text.orEmpty(), newText).second.toLong(),
                                existedBefore = outcome.previous.bytes != null,
                                existsAfter = true,
                                beforeText = diskBaseline.value,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            if (thenExit && saved && !dirty) leave()
        }
    }

    val keyboardSave by androidx.compose.runtime.rememberUpdatedState<() -> Unit>({ if (!saving && dirty) save() })
    BackHandler(enabled = com.androidharness.app.ui.LocalWorkspacePaneActive.current && dirty && !saving) { requestNavigation(onBack) }

    androidx.compose.runtime.SideEffect {
        actions?.ready = loadState != Load.Loading && (loadState != Load.Ready || editorRef != null)
        actions?.textAvailable = loadState == Load.Ready && editorRef != null
        actions?.close = ::requestNavigation
        actions?.save = { if (dirty) save() }
        actions?.find = { searchOpen = true }
        actions?.goToLine = { gotoOpen = true }
        actions?.toggleWrap = { container.editorScope.launch { container.settings.setEditorWordWrap(!wordwrapOn) } }
        actions?.outline = { smartAction = SmartEditorAction.OUTLINE }
        actions?.format = { smartAction = SmartEditorAction.FORMAT }
        actions?.definition = { smartAction = SmartEditorAction.DEFINITION }
        actions?.references = { smartAction = SmartEditorAction.REFERENCES }
        actions?.rename = { smartAction = SmartEditorAction.RENAME }
    }
    val undoAvailable = editRevision.let { editorRef?.text?.canUndo() == true }
    val redoAvailable = editRevision.let { editorRef?.text?.canRedo() == true }
    val completionImeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(completionRevision, editorRef, completionImeHeight) {
        if (completionRevision == 0 || smart == null) return@LaunchedEffect
        val ed = editorRef ?: return@LaunchedEffect
        val at = completionAt
        // Hardware keyboards do not always trigger Sora's IME completion path. Debounce both paths.
        delay(180)
        if (smartAction == null && !saving && ed.isEditable && ed.hasFocus() && !ed.cursor.isSelected && ed.cursor.left == at) {
            ed.ensurePositionVisible(ed.cursor.leftLine, ed.cursor.leftColumn)
            ed.getComponent(EditorAutoCompletion::class.java)?.requireCompletion()
        }
    }
    val controls: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        IconButton(
            onClick = { editorRef?.text?.undo() },
            enabled = undoAvailable,
        ) { Icon(Icons.Outlined.Undo, contentDescription = "撤销") }
        IconButton(
            onClick = { editorRef?.text?.redo() },
            enabled = redoAvailable,
        ) { Icon(Icons.Outlined.Redo, contentDescription = "重做") }
        if (workspaceChrome != null) Spacer(Modifier.weight(1f))
        if (smart != null && SmartEditorSupport.outline(path)) IconButton(onClick = { smartAction = SmartEditorAction.OUTLINE }, enabled = editorRef != null && !saving) {
            Icon(Icons.Outlined.AccountTree, contentDescription = "代码大纲")
        }
        IconButton(onClick = {
            editing = !editing
            editorRef?.isEditable = editing
            if (editing) searchOpen = false
            persistDraft()
        }) {
            Icon(
                if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                contentDescription = if (editing) "完成编辑" else "编辑",
                tint = if (editing) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        if (dirty && !saving) {
            IconButton(onClick = { save() }) {
                Icon(Icons.Outlined.Save, contentDescription = "保存", tint = scheme.primary)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (smart != null) {
                    if (SmartEditorSupport.format(path)) DropdownMenuItem(text = { Text("格式化文档") }, leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) }, enabled = editorRef != null && !saving,
                        onClick = { menuOpen = false; smartAction = SmartEditorAction.FORMAT })
                    if (SmartEditorSupport.navigation(path)) {
                        DropdownMenuItem(text = { Text("转到定义") }, enabled = editorRef != null, onClick = { menuOpen = false; smartAction = SmartEditorAction.DEFINITION })
                        DropdownMenuItem(text = { Text("查找引用") }, enabled = editorRef != null, onClick = { menuOpen = false; smartAction = SmartEditorAction.REFERENCES })
                    }
                    if (SmartEditorSupport.rename(path)) DropdownMenuItem(text = { Text("重命名符号") }, enabled = editorRef != null && !saving, onClick = { menuOpen = false; smartAction = SmartEditorAction.RENAME })
                    DropdownMenuItem(text = { Text("语言支持") }, onClick = { menuOpen = false; languageSupport = true })
                    androidx.compose.material3.HorizontalDivider()
                }
                onQuickOpen?.let { action -> DropdownMenuItem(text = { Text("快速打开") }, leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) }, onClick = { menuOpen = false; action() }) }
                onProjectSearch?.let { action -> DropdownMenuItem(text = { Text("搜索项目") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, onClick = { menuOpen = false; action() }) }
                onCommands?.let { action -> DropdownMenuItem(text = { Text("命令面板") }, onClick = { menuOpen = false; action() }) }
                if (onQuickOpen != null) androidx.compose.material3.HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (searchOpen) "隐藏查找" else "查找…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    onClick = {
                        menuOpen = false
                        searchOpen = !searchOpen
                        if (!searchOpen) editorRef?.searcher?.stopSearch()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (wordwrapOn) "自动换行：开" else "自动换行：关") },
                    onClick = {
                        menuOpen = false
                        val enabled = !wordwrapOn
                        editorRef?.setWordwrap(enabled)
                        container.editorScope.launch { container.settings.setEditorWordWrap(enabled) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("转到行…") },
                    onClick = { menuOpen = false; gotoOpen = true },
                )
                DropdownMenuItem(
                    text = { Text("分享") },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                    onClick = {
                        menuOpen = false
                        val f = fs ?: return@DropdownMenuItem
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.share(context, node)
                            }.onFailure { e ->
                                Toast.makeText(context, e.message ?: "分享失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("用其他应用打开") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                    onClick = {
                        menuOpen = false
                        val f = fs ?: return@DropdownMenuItem
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.openWith(context, node)
                            }.onFailure { e ->
                                Toast.makeText(context, e.message ?: "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
    Scaffold(
        containerColor = scheme.surface,
        topBar = {
            Column {
                if (workspaceChrome != null) {
                    workspaceChrome()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = controls)
                    androidx.compose.material3.HorizontalDivider(color = scheme.outlineVariant)
                } else AppHeader(title = path.substringAfterLast('/'), subtitle = path, onBack = { requestNavigation(onBack) }, actions = controls)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when (val st = loadState) {
                Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is Load.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(st.message, color = scheme.error)
                }

                is Load.Binary -> NotTextView(
                    sizeBytes = st.sizeBytes,
                    detail = "二进制文件，无法作为文本编辑。",
                    onShare = {
                        val f = fs ?: return@NotTextView
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.share(context, node)
                            }
                        }
                    },
                    onOpenWith = {
                        val f = fs ?: return@NotTextView
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.openWith(context, node)
                            }
                        }
                    },
                )

                is Load.TooLarge -> NotTextView(
                    sizeBytes = st.sizeBytes,
                    detail = "文件过大，无法在此编辑（超过 ${formatFileSize(MAX_EDIT_BYTES)}）。",
                    onShare = {
                        val f = fs ?: return@NotTextView
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.share(context, node)
                            }
                        }
                    },
                    onOpenWith = {
                        val f = fs ?: return@NotTextView
                        scope.launch {
                            runCatching {
                                val node = withContext(Dispatchers.IO) { f.resolve(path) }
                                FileOps.openWith(context, node)
                            }
                        }
                    },
                )

                Load.Ready -> Column(Modifier.fillMaxSize()) {
                    if (dirty) Text(
                        when {
                            draftStatus == EditorDraftStatus.FAILED -> "无法保存恢复草稿，请保持编辑器打开或保存文件。"
                            draftStatus == EditorDraftStatus.SAVING -> "正在将草稿保存到设备…"
                            buffer.recovered.value -> "已恢复未保存草稿 · 文件未更改"
                            else -> "草稿已保存到设备 · 文件未保存"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draftStatus == EditorDraftStatus.FAILED) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (onAskAgent != null && selectedLength > 0) Surface(color = scheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selectedLength > SelectedCode.MAX_CHARS) "请选择较少的代码再询问代理" else "所选代码",
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(enabled = selectedLength <= SelectedCode.MAX_CHARS, onClick = {
                                val ed = editorRef ?: return@TextButton
                                selection = SelectedCode.capture(path, ed.text.toString(), ed.cursor.left, ed.cursor.right, dirty)
                                ed.hideSoftInput(); ed.hideEditorWindows()
                            }) { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("询问代理") }
                        }
                    }
                    androidx.compose.runtime.key(draftKey) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .imePadding()
                                .background(scheme.surface),
                            factory = { ctx ->
                                CodeEditor(ctx).apply {
                                    setOnKeyListener { _, keyCode, event ->
                                        val smartKeyAction = when {
                                            smart == null -> null
                                            keyCode == android.view.KeyEvent.KEYCODE_F12 && SmartEditorSupport.navigation(path) -> if (event.isShiftPressed) SmartEditorAction.REFERENCES else SmartEditorAction.DEFINITION
                                            keyCode == android.view.KeyEvent.KEYCODE_F2 && SmartEditorSupport.rename(path) -> SmartEditorAction.RENAME
                                            event.isCtrlPressed && event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_I && SmartEditorSupport.format(path) -> SmartEditorAction.FORMAT
                                            else -> null
                                        }
                                        if (smartKeyAction != null) {
                                            if (event.action == android.view.KeyEvent.ACTION_UP && !saving) smartAction = smartKeyAction
                                            true
                                        } else if (event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_S) {
                                            if (event.action == android.view.KeyEvent.ACTION_UP) keyboardSave()
                                            true
                                        } else if (event.isCtrlPressed && keyCode == android.view.KeyEvent.KEYCODE_P && onQuickOpen != null) {
                                            if (event.action == android.view.KeyEvent.ACTION_UP) { if (event.isShiftPressed) onCommands?.invoke() else onQuickOpen.invoke() }
                                            true
                                        } else if (event.isCtrlPressed && event.isShiftPressed && keyCode == android.view.KeyEvent.KEYCODE_F && onProjectSearch != null) {
                                            if (event.action == android.view.KeyEvent.ACTION_UP) onProjectSearch.invoke()
                                            true
                                        } else false
                                    }
                                    typefaceText = Typeface.MONOSPACE
                                    typefaceLineNumber = Typeface.MONOSPACE
                                    isHighlightCurrentLine = false
                                    props.stickyScroll = false
                                    getComponent(EditorAutoCompletion::class.java)?.setEnabled(smart != null && SmartEditorSupport.completion(path))
                                    if (smart != null && SmartEditorSupport.completion(path)) getComponent(EditorAutoCompletion::class.java)?.setAdapter(MaterialCompletionAdapter())
                                    isEditable = editing
                                    setWordwrap(wordwrapOn)
                                    setEditorLanguage(BasicCodeLanguage(path, smart))
                                    setText(buffer.document ?: io.github.rosemoe.sora.text.Content(buffer.text.value ?: decoded.value!!.text), true, null)
                                    buffer.document = text
                                    val left = text.indexer.getCharPosition(buffer.selectionStart.coerceIn(0, text.length))
                                    val right = text.indexer.getCharPosition(buffer.selectionEnd.coerceIn(0, text.length))
                                    setSelectionRegion(left.line, left.column, right.line, right.column, false)
                                    val savedX = buffer.offsetX
                                    val savedY = buffer.offsetY
                                    post {
                                        if (!isReleased && initialLine == null) {
                                            scroller.startScroll(0, 0, savedX.coerceAtMost(scrollMaxX), savedY.coerceAtMost(scrollMaxY), 0)
                                            scroller.setEditorOffsets(); invalidate()
                                        }
                                    }
                                    applyEditorTheme(this, scheme)
                                    // Dirty flag via the event bus, survives setText
                                    // swapping out the Content object.
                                    subscribeEvent(
                                        io.github.rosemoe.sora.event.ContentChangeEvent::class.java,
                                    ) { event, _ ->
                                        editRevision++
                                        buffer.document = text
                                        buffer.text.value = text.toString()
                                        dirty = buffer.text.value != lastSavedText.value
                                        persistDraft()
                                        if (smart != null && smartAction == null && SmartEditorSupport.completion(path) && isEditable && hasFocus() &&
                                            event.action == io.github.rosemoe.sora.event.ContentChangeEvent.ACTION_INSERT &&
                                            !event.isCausedByUndoManager && event.changedText.length in 1..2) {
                                            completionAt = event.changeEnd.index
                                            completionRevision++
                                        }
                                    }
                                    subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent::class.java) { _, _ ->
                                        selectedLength = cursor.right - cursor.left
                                        buffer.selectionStart = cursor.left
                                        buffer.selectionEnd = cursor.right
                                    }
                                    subscribeEvent(io.github.rosemoe.sora.event.ScrollEvent::class.java) { _, _ ->
                                        buffer.offsetX = offsetX; buffer.offsetY = offsetY
                                    }
                                    editorRef = this
                                }
                            },
                            onRelease = { ed ->
                                buffer.document = ed.text
                                buffer.selectionStart = ed.cursor.left; buffer.selectionEnd = ed.cursor.right
                                buffer.offsetX = ed.offsetX; buffer.offsetY = ed.offsetY
                                ed.release()
                            },
                            update = { ed ->
                                applyEditorTheme(ed, scheme)
                                if (ed.isWordwrap != wordwrapOn) ed.setWordwrap(wordwrapOn)
                            },
                        )
                    }

                    if (searchOpen) {
                        FindBar(
                            editor = editorRef,
                            editingEnabled = editing,
                            matchCase = editorSettings.editorMatchCase,
                            regexOn = editorSettings.editorRegexSearch,
                            onMatchCaseChanged = { enabled ->
                                container.editorScope.launch { container.settings.setEditorSearchOptions(matchCase = enabled) }
                            },
                            onRegexChanged = { enabled ->
                                container.editorScope.launch { container.settings.setEditorSearchOptions(regex = enabled) }
                            },
                            onClosed = { searchOpen = false },
                        )
                    }

                    LaunchedEffect(initialLine, initialColumn, jumpRequest, editorRef) {
                        val ed = editorRef ?: return@LaunchedEffect
                        val line = (initialLine ?: 0) - 1
                        if (line >= 0 && ed.text.lineCount > 0) {
                            delay(120)
                            val target = line.coerceAtMost(ed.text.lineCount - 1).coerceAtLeast(0)
                            val column = ((initialColumn ?: 1) - 1).coerceIn(0, ed.text.getColumnCount(target))
                            ed.setSelection(target, column)
                            ed.ensurePositionVisible(target, column)
                        }
                    }
                }
            }
        }
    }

    selection?.let { captured ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { selection = null },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("询问所选代码", style = MaterialTheme.typography.titleLarge)
                Text(captured.location, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = scheme.surfaceContainer) {
                    Text(captured.text.take(700) + if (captured.text.length > 700) "\n…" else "",
                        fontFamily = com.androidharness.app.ui.theme.HarnessMono, style = MaterialTheme.typography.bodySmall,
                        maxLines = 7, modifier = Modifier.padding(12.dp))
                }
                Text("选择操作，然后在聊天中审阅并发送。", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                CodeAction.entries.forEach { action ->
                    Surface(onClick = {
                        selection = null
                        requestNavigation { onAskAgent?.invoke(sourceProject?.id, captured.prompt(action)) }
                    }, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color = scheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(action.label, style = MaterialTheme.typography.titleSmall)
                            Text(action.description, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (gotoOpen) GoToLineDialog(editor = editorRef, onDismiss = { gotoOpen = false })
    if (smart != null && editorRef != null) smartAction?.let { action ->
        androidx.compose.runtime.key(action, path) {
            SmartEditorSheet(action, path, editorRef!!, buffer, smart, onDismiss = { smartAction = null }, onOpenLocation = onOpenSmartLocation)
        }
    }
    if (languageSupport) AlertDialog(onDismissRequest = { languageSupport = false }, title = { Text("语言支持") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("JavaScript 与 TypeScript：自动补全、定义、引用、重命名、大纲及格式化。")
            Text("HTML、CSS、SCSS、LESS 与 JSON：自动补全、大纲及格式化。CSS 类语言还支持文件内定义和引用。")
            Text("Markdown：大纲及格式化。YAML：格式化。")
            Text("其他语言保留语法高亮及现有编辑工具。项目索引不包含依赖和生成文件夹。")
            Text("格式化会读取 JSON 格式的 Prettier 设置。JavaScript 配置和外部格式化插件须在终端中运行。")
        } }, confirmButton = { TextButton(onClick = { languageSupport = false }) { Text("知道了") } })

    conflict?.let { reviewed ->
        AlertDialog(
            onDismissRequest = { conflict = null },
            title = { Text(if (compareConflict) "比较更改" else "磁盘上的文件已更改") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("打开后，已保存的文件发生了更改。你的编辑仍然保留。重新加载将以已保存版本替换你的编辑。")
                    if (compareConflict) {
                        Text("当前已保存的文件", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(reviewed.text?.take(12000) ?: if (reviewed.bytes == null) "文件已删除" else "文件已不再是文本",
                                fontFamily = com.androidharness.app.ui.theme.HarnessMono, style = MaterialTheme.typography.bodySmall)
                        }
                        androidx.compose.material3.HorizontalDivider()
                        Text("你的编辑", style = MaterialTheme.typography.titleSmall)
                        Text(editorText().take(12000), fontFamily = com.androidharness.app.ui.theme.HarnessMono, style = MaterialTheme.typography.bodySmall)
                        if ((reviewed.text?.length ?: 0) > 12000 || editorText().length > 12000) Text("每个版本的预览已截短至 12,000 个字符。")
                    } else TextButton(onClick = { compareConflict = true }) { Text("比较") }
                    if (reviewed.text != null) TextButton(onClick = {
                        val text = requireNotNull(reviewed.text)
                        decoded.value = EditorFileCodec.decode(requireNotNull(reviewed.bytes))
                        editorRef?.setText(text)
                        buffer.text.value = text
                        lastSavedText.value = text
                        buffer.diskVersion.value = reviewed.version
                        dirty = false
                        buffer.recovered.value = false
                        persistDraft(immediate = true)
                        conflict = null
                        pendingNavigation = null
                    }) { Text("重新加载文件") }
                }
            },
            confirmButton = { TextButton(enabled = !saving, onClick = { save(conflictThenExit, reviewed.version) }) { Text("保留我的编辑") } },
            dismissButton = { TextButton(onClick = { conflict = null; pendingNavigation = null }) { Text("取消") } },
        )
    }
    if (showExitPrompt) {
        AlertDialog(
            onDismissRequest = { showExitPrompt = false; pendingNavigation = null },
            title = { Text("未保存的更改") },
            text = { Text("${path.substringAfterLast('/')} 有未保存的更改。") },
            confirmButton = {
                TextButton(onClick = { showExitPrompt = false; save(thenExit = true) }) {
                    Text("保存并退出")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitPrompt = false; pendingNavigation = null }) { Text("取消") }
                    TextButton(onClick = {
                        showExitPrompt = false
                        editorRef?.setText(lastSavedText.value)
                        buffer.text.value = lastSavedText.value
                        dirty = false
                        buffer.recovered.value = false
                        persistDraft(immediate = true)
                        leave()
                    }) {
                        Text("放弃")
                    }
                }
            },
        )
    }
}

/** Maps rich syntax highlight palette onto the editor slots based on current theme mode. */
private fun applyEditorTheme(ed: CodeEditor, scheme: androidx.compose.material3.ColorScheme) {
    val cs = ed.colorScheme
    val isDark = CodeTokenColors.isDarkSurface(scheme.surface)
    fun set(slot: Int, color: androidx.compose.ui.graphics.Color) = cs.setColor(slot, color.toArgb())
    fun token(type: TokenType) = CodeTokenColors.of(type, isDark, scheme.onSurface)

    set(EditorColorScheme.WHOLE_BACKGROUND, scheme.surface)
    set(EditorColorScheme.LINE_NUMBER_BACKGROUND, scheme.surfaceContainerLowest)
    set(EditorColorScheme.LINE_NUMBER, scheme.outline.copy(alpha = 0.55f))
    set(EditorColorScheme.LINE_NUMBER_CURRENT, scheme.primary)
    set(EditorColorScheme.TEXT_NORMAL, scheme.onSurface)
    set(EditorColorScheme.SELECTION_INSERT, scheme.primary.copy(alpha = 0.35f))
    set(EditorColorScheme.COMPLETION_WND_BACKGROUND, scheme.surfaceContainerHigh)
    set(EditorColorScheme.COMPLETION_WND_CORNER, scheme.outlineVariant)
    set(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, scheme.onSurface)
    set(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, scheme.onSurfaceVariant)
    set(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, scheme.secondaryContainer)

    // Colors come from the palette shared with chat code blocks, so the two
    // render the same snippet identically.
    set(EditorColorScheme.HTML_TAG, token(TokenType.HTML_TAG))
    set(EditorColorScheme.IDENTIFIER_VAR, token(TokenType.ATTRIBUTE_NAME))
    set(EditorColorScheme.ATTRIBUTE_NAME, token(TokenType.KEYWORD_CONTROL))
    set(EditorColorScheme.KEYWORD, token(TokenType.KEYWORD))
    set(EditorColorScheme.IDENTIFIER_NAME, token(TokenType.TYPE_NAME))
    set(EditorColorScheme.FUNCTION_NAME, token(TokenType.FUNCTION_NAME))
    set(EditorColorScheme.LITERAL, token(TokenType.STRING))
    set(EditorColorScheme.ATTRIBUTE_VALUE, token(TokenType.NUMBER))
    set(EditorColorScheme.COMMENT, token(TokenType.COMMENT))
    set(EditorColorScheme.ANNOTATION, token(TokenType.ANNOTATION))
    set(EditorColorScheme.OPERATOR, token(TokenType.OPERATOR))
}

// ---------------------------------------------------------------------------
// Non-text outcomes

private sealed interface Load {
    data object Loading : Load
    data object Ready : Load
    data class Failed(val message: String) : Load
    data class Binary(val sizeBytes: Long) : Load
    data class TooLarge(val sizeBytes: Long) : Load
}

@Composable
private fun NotTextView(
    sizeBytes: Long,
    detail: String,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(40.dp), tint = scheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(14.dp))
        Text(formatFileSize(sizeBytes), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("分享")
            }
            Button(onClick = onOpenWith) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("打开方式…")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Find / replace bar

/**
 * Owns its query state and uses saved matching preferences. Re-runs search on debounced typing;
 * matches read back from the searcher with a short settle poll (the search
 * pass runs on a worker thread).
 */
@Composable
private fun FindBar(
    editor: CodeEditor?,
    editingEnabled: Boolean,
    matchCase: Boolean,
    regexOn: Boolean,
    onMatchCaseChanged: (Boolean) -> Unit,
    onRegexChanged: (Boolean) -> Unit,
    onClosed: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var patternError by remember { mutableStateOf(false) }
    var matchInfo by remember { mutableStateOf(0 to 0) } // count to current

    Surface(color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("查找", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    isError = patternError,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                )
                Text(
                    if (matchInfo.first == 0) "-" else "${matchInfo.second + 1}/${matchInfo.first}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                IconButton(onClick = {
                    val s = editor?.searcher ?: return@IconButton
                    s.gotoPrevious()
                    matchInfo = matchInfo.first to s.getCurrentMatchedPositionIndex()
                }) { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个匹配项") }
                IconButton(onClick = {
                    val s = editor?.searcher ?: return@IconButton
                    s.gotoNext()
                    matchInfo = matchInfo.first to s.getCurrentMatchedPositionIndex()
                }) { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个匹配项") }
                ToggleChip("Aa", matchCase, "区分大小写", onMatchCaseChanged)
                ToggleChip(".*", regexOn, "正则", onRegexChanged)
                IconButton(onClick = {
                    editor?.searcher?.stopSearch()
                    onClosed()
                }) { Icon(Icons.Outlined.Close, contentDescription = "关闭查找") }
            }
            if (editingEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replaceWith,
                        onValueChange = { replaceWith = it },
                        placeholder = { Text("替换为", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp),
                    )
                    TextButton(onClick = {
                        editor?.searcher?.replaceCurrentMatch(replaceWith)
                    }) { Text("替换") }
                    TextButton(onClick = {
                        editor?.searcher?.replaceAll(replaceWith)
                    }) { Text("全部") }
                }
            }
        }
    }

    LaunchedEffect(editor, query, matchCase, regexOn) {
        val s = editor?.searcher ?: return@LaunchedEffect
        delay(250)
        if (query.isEmpty()) {
            s.stopSearch()
            matchInfo = 0 to 0
            patternError = false
            return@LaunchedEffect
        }
        try {
            s.search(
                query,
                EditorSearcher.SearchOptions(
                    if (regexOn) EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    else EditorSearcher.SearchOptions.TYPE_NORMAL,
                    !matchCase,
                ),
            )
            patternError = false
            var settled = -1
            repeat(15) {
                delay(60)
                val c = s.getMatchedPositionCount()
                if (c == settled && c >= 0) {
                    matchInfo = c to s.getCurrentMatchedPositionIndex()
                    return@LaunchedEffect
                }
                settled = c
            }
            matchInfo = settled to s.getCurrentMatchedPositionIndex()
        } catch (_: Exception) {
            patternError = true
            matchInfo = 0 to 0
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, description: String, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.padding(start = 4.dp).semantics { contentDescription = description },
    )
}

@Composable
private fun GoToLineDialog(editor: CodeEditor?, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("转到行") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.filter(Char::isDigit) },
                singleLine = true,
                placeholder = { Text("行号") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val n = value.toIntOrNull()
                if (n != null && editor != null && editor.text.lineCount > 0) {
                    val line = (n - 1).coerceIn(0, editor.text.lineCount - 1)
                    editor.setSelection(line, 0)
                    editor.ensurePositionVisible(line, 0)
                }
                onDismiss()
            }) { Text("前往") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
