package com.androidharness.app.ui.files

import android.content.Context
import com.androidharness.app.workspace.WorkspaceFs
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal data class SmartAnswer(val snapshot: SmartSnapshot, val result: JsonElement)
internal data class SmartCompletion(val label: String, val detail: String, val text: String, val start: Int, val end: Int, val cursor: Int)
internal data class SmartSymbol(val name: String, val kind: String, val start: Int, val end: Int, val line: Int, val depth: Int)
internal data class SmartLocation(val path: String, val start: Int, val line: Int, val column: Int, val preview: String)
internal data class SmartRenamePlan(val snapshot: SmartSnapshot, val edits: List<SmartTextEdit>) {
    val changes: Map<String, String> = edits.groupBy { it.path }.mapValues { (path, changes) ->
        applySmartEdits(snapshot.sources[path]?.text ?: error("重命名涉及此工作区外的位置。"), changes)
    }.filter { (path, text) -> text != snapshot.sources.getValue(path).text }
}

internal class SmartEditorController(
    context: Context,
    private val fs: WorkspaceFs,
    private val drafts: EditorDraftStore,
    private val draftKey: (String) -> String,
    private val buffers: () -> Map<String, EditorBuffer>,
    private val bufferFor: (String) -> EditorBuffer,
    private val openRenamed: (List<String>) -> Unit,
) : AutoCloseable {
    private val engine = SmartEditorEngine(context)
    private val workspace = SmartEditorWorkspace()
    private val requests = Mutex()

    suspend fun request(op: String, path: String, text: String, offset: Int = 0, newName: String? = null): SmartAnswer = requests.withLock {
        val overrides = withContext(Dispatchers.Main.immediate) {
            buffers().filter { (file, buffer) -> SmartEditorSupport.source(file) && buffer.loaded && buffer.text.value != null }.mapValues { it.value.text.value!! }
        }
        val snapshot = workspace.snapshot(fs, path, text, overrides, drafts, draftKey, refresh = op != "complete")
        if (op == "rename") check(!snapshot.limited && snapshot.skipped == 0) { "重命名需要完整的项目索引，部分文件未能建立索引。" }
        SmartAnswer(snapshot, engine.request(op, path, snapshot.texts, offset, newName))
    }

    suspend fun completions(path: String, text: String, offset: Int): List<SmartCompletion> {
        val result = request("complete", path, text, offset).result.jsonArray
        return result.map { item -> item.jsonObject.let {
            SmartCompletion(it.getValue("label").jsonPrimitive.content, it.getValue("detail").jsonPrimitive.content,
                it.getValue("text").jsonPrimitive.content, it.getValue("start").jsonPrimitive.int, it.getValue("end").jsonPrimitive.int,
                it["cursor"]?.jsonPrimitive?.intOrNull ?: it.getValue("text").jsonPrimitive.content.length)
        } }
    }

    /** A rename creates reviewed drafts, retaining normal Save conflict checks and per-file Undo. */
    suspend fun applyRename(plan: SmartRenamePlan) {
        check(plan.changes.isNotEmpty()) { "此重命名没有产生更改。" }
        withContext(Dispatchers.IO) {
            for ((path, _) in plan.changes) {
                val source = plan.snapshot.sources.getValue(path)
                if (fs is com.androidharness.app.workspace.ChatWorkspaceFs) check(fs.target(path).first.canEdit) { "$path 属于只读工作区。" }
                val node = fs.resolve(path)
                check(node.isFile && node.length <= WorkspaceSearch.MAX_FILE_BYTES) { "$path 在预览后已更改，请重试。" }
                val bytes = node.openInputStream()?.use(::readSmartBytes) ?: error("无法读取 $path")
                check(EditorDiskSnapshot(bytes).version == source.version) { "$path 在预览后已更改，请重试。" }
                val draft = drafts.read(draftKey(path))
                if (draft != null) check(draft.text == source.text && draft.diskVersion == source.version) { "$path 有更新的编辑，请重试。" }
            }
        }
        withContext(Dispatchers.Main.immediate) {
            // Do not yield between the final buffer check and applying the entire reviewed set.
            for ((path, _) in plan.changes) {
                val current = buffers()[path] ?: continue
                check(!current.saving.value && (!current.loaded || current.text.value == plan.snapshot.sources.getValue(path).text)) {
                    "$path 有更新的编辑，请重试。"
                }
            }
            for ((path, text) in plan.changes) {
                val source = plan.snapshot.sources.getValue(path)
                val buffer = bufferFor(path)
                if (!buffer.loaded) {
                    buffer.baseline.value = source.decoded.text
                    buffer.saved.value = source.decoded.text
                    buffer.decoded.value = source.decoded
                    buffer.diskVersion.value = source.version
                    buffer.loaded = true
                }
                val content = buffer.document ?: Content(source.text).also { buffer.document = it }
                content.beginBatchEdit()
                try {
                    for (edit in plan.edits.filter { it.path == path }.sortedByDescending { it.start }) content.replace(edit.start, edit.end, edit.text)
                } finally { content.endBatchEdit() }
                buffer.text.value = text
                buffer.dirty.value = text != buffer.saved.value
                buffer.editing.value = true
                buffer.selectionStart = buffer.selectionStart.coerceAtMost(text.length)
                buffer.selectionEnd = buffer.selectionEnd.coerceAtMost(text.length)
                drafts.submit(draftKey(path), buffer.draft(), immediate = true)
            }
            openRenamed(plan.changes.keys.toList())
        }
    }

    override fun close() = engine.close()
}
