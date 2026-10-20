package com.androidharness.app.ui.files

import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal object SmartEditorSupport {
    private val scripts = setOf("js", "jsx", "ts", "tsx", "mjs", "cjs", "mts", "cts")
    private val web = setOf("html", "htm", "css", "scss", "less", "json", "jsonc")
    private fun ext(path: String) = path.substringAfterLast('.', "").lowercase()
    fun completion(path: String) = ext(path) in scripts + web
    fun navigation(path: String) = ext(path) in scripts + setOf("css", "scss", "less")
    fun rename(path: String) = ext(path) in scripts
    fun outline(path: String) = completion(path) || ext(path) in setOf("md", "markdown")
    fun format(path: String) = outline(path) || ext(path) in setOf("yaml", "yml")
    fun source(path: String) = format(path) || path.substringAfterLast('/').let {
        it.startsWith(".prettierrc") || it.startsWith("prettier.config.") || it == ".editorconfig"
    }
}

internal data class SmartSource(val text: String, val version: String, val decoded: EditorFileCodec.Decoded)
internal data class SmartSnapshot(val sources: Map<String, SmartSource>, val limited: Boolean, val skipped: Int) {
    val texts get() = sources.mapValues { it.value.text }
    val notice get() = when {
        limited -> "部分项目文件超出索引限制，无法重命名。"
        skipped > 0 -> "$skipped 个项目文件未能建立索引，无法重命名。"
        else -> "项目符号与标准库 · 不含依赖文件夹"
    }
}

/** Snapshots share the same bounded path resolution as project search, including SAF/SSH grants. */
internal class SmartEditorWorkspace {
    private var cached: SmartSnapshot? = null
    private var cachedAt = 0L

    suspend fun snapshot(fs: WorkspaceFs, active: String, text: String, overrides: Map<String, String>,
        drafts: EditorDraftStore, draftKey: (String) -> String, refresh: Boolean = false): SmartSnapshot {
        val disk = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!refresh && cached != null && now - cachedAt < 5000) return@withContext cached!!
            val index = WorkspaceSearch.index(fs)
            val sources = linkedMapOf<String, SmartSource>()
            var bytesRead = 0L
            var limited = index.limited
            var skipped = index.unreadable
            // The active folder comes first when a large project reaches the memory budget.
            val parent = active.substringBeforeLast('/', "")
            for (path in index.paths.filter(SmartEditorSupport::source).sortedBy { if(it.substringBeforeLast('/', "") == parent) 0 else 1 }) {
                currentCoroutineContext().ensureActive()
                if (sources.size >= 500 || bytesRead >= 8_000_000) { limited = true; break }
                try {
                    val node = fs.resolve(path)
                    if (!node.isFile || node.length > WorkspaceSearch.MAX_FILE_BYTES) { skipped++; continue }
                    val bytes = node.openInputStream()?.use(::readSmartBytes)
                        ?: error("无法读取文件")
                    if (bytes.size > WorkspaceSearch.MAX_FILE_BYTES) { skipped++; continue }
                    bytesRead += bytes.size
                    val decoded = EditorFileCodec.decode(bytes) ?: run { skipped++; continue }
                    sources[path] = SmartSource(decoded.text, EditorDiskSnapshot(bytes).version, decoded)
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; skipped++ }
            }
            SmartSnapshot(sources, limited, skipped).also { cached = it; cachedAt = now }
        }
        val sources = disk.sources.toMutableMap()
        // Include durable drafts for unopened tabs too; requests never discard unsaved content.
        for (path in sources.keys.toList()) {
            val draft = withContext(Dispatchers.IO) { drafts.read(draftKey(path)) }
            if (draft != null) sources[path] = SmartSource(draft.text, draft.diskVersion, draft.decoded())
        }
        for ((path, value) in overrides + (active to text)) {
            require(value.length <= 512_000) { "此文件过大，无法使用智能编辑工具。" }
            val old = sources[path] ?: withContext(Dispatchers.IO) {
                val node = fs.resolve(path)
                check(node.length <= WorkspaceSearch.MAX_FILE_BYTES) { "此文件过大，无法使用智能编辑工具。" }
                val bytes = node.openInputStream()?.use(::readSmartBytes) ?: error("无法读取 $path")
                check(bytes.size <= WorkspaceSearch.MAX_FILE_BYTES) { "此文件过大，无法使用智能编辑工具。" }
                val decoded = EditorFileCodec.decode(bytes) ?: error("$path 已不再是文本。")
                SmartSource(decoded.text, EditorDiskSnapshot(bytes).version, decoded)
            }
            sources[path] = old.copy(text = value)
        }
        check(sources.values.sumOf { it.text.length.toLong() } <= 8_000_000) { "打开的项目过大，本地语言工具无法处理。请关闭部分大型草稿后重试。" }
        return disk.copy(sources = sources)
    }
}

internal fun readSmartBytes(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    val limit = WorkspaceSearch.MAX_FILE_BYTES.toInt() + 1
    while (output.size() < limit) {
        val size = input.read(chunk, 0, minOf(chunk.size, limit - output.size()))
        if (size < 0) break
        output.write(chunk, 0, size)
    }
    return output.toByteArray()
}

internal data class SmartTextEdit(val path: String, val start: Int, val end: Int, val text: String)

/** Validate the complete edit set before touching any buffer. No partial, overlapping or stale rename. */
internal fun applySmartEdits(source: String, edits: List<SmartTextEdit>): String {
    var right = source.length
    val text = StringBuilder(source)
    for (edit in edits.sortedByDescending { it.start }) {
        require(edit.start >= 0 && edit.start <= edit.end && edit.end <= right) { "语言服务返回了重叠或无效的编辑。" }
        text.replace(edit.start, edit.end, edit.text)
        right = edit.start
    }
    return text.toString()
}
