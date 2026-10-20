package com.androidharness.app.ui.files

import com.androidharness.app.workspace.WorkspaceFs
import com.androidharness.app.workspace.WorkspaceIgnore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class WorkspaceFileIndex(val paths: List<String>, val limited: Boolean, val unreadable: Int)
internal data class QuickOpenQuery(val name: String, val line: Int?)
internal data class ProjectSearchOptions(val query: String, val matchCase: Boolean = false, val regex: Boolean = false,
    val include: String = "", val exclude: String = "")
internal data class ProjectSearchHit(val line: Int, val column: Int, val preview: String)
internal data class ProjectSearchFile(val path: String, val version: String, val decoded: EditorFileCodec.Decoded,
    val hits: List<ProjectSearchHit>, val count: Int)
internal data class ProjectSearchResult(val files: List<ProjectSearchFile>, val limited: Boolean = false, val skipped: Int = 0) {
    val count get() = files.sumOf { it.count }
}
internal data class ProjectReplacement(val source: ProjectSearchFile, val text: String)
internal data class ReplacementReceipt(val saved: List<String>, val failed: Map<String, String>)

/** Bounded, cancellable UI indexing; resolve each node again to retain workspace/grant checks. */
internal object WorkspaceSearch {
    const val MAX_FILE_BYTES = 512_000L
    private const val MAX_FILES = 10_000
    private const val MAX_TOTAL_BYTES = 16_000_000L
    private const val MAX_RESULTS = 1_000

    fun quickOpenQuery(query: String): QuickOpenQuery {
        val value = query.trim()
        val line = value.substringAfterLast(':', "").toIntOrNull()?.takeIf { it > 0 }
        return QuickOpenQuery(if(line != null) value.substringBeforeLast(':') else value, line)
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (output.size() <= MAX_FILE_BYTES) {
            val read = input.read(chunk, 0, minOf(chunk.size, MAX_FILE_BYTES.toInt() + 1 - output.size()))
            if (read < 0) break
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }

    suspend fun index(fs: WorkspaceFs): WorkspaceFileIndex {
        val paths = linkedSetOf<String>()
        val seen = hashSetOf<String>()
        val pending = ArrayDeque<Pair<String, Int>>().apply { add("." to 0) }
        var inspected = 0
        var unreadable = 0
        var limited = false
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (path, depth) = pending.removeFirst()
            if (!seen.add(path)) continue
            if (++inspected > MAX_FILES * 2 || depth > 40) { limited = true; continue }
            try {
                val node = fs.resolve(path)
                if (node.isFile) {
                    if (paths.size >= MAX_FILES) { limited = true; break }
                    paths += path.removePrefix("./")
                } else if (node.isDirectory) {
                    node.list().forEach { child ->
                        val relative = child.relPath.removePrefix("./")
                        // An escaping symlink can report an absolute canonical path. Never follow it.
                        if (!relative.startsWith('/') && relative.split('/').none { it == ".." } &&
                            !WorkspaceIgnore.shouldSkip(relative)) pending.add(relative to depth + 1)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                unreadable++
            }
        }
        return WorkspaceFileIndex(paths.sortedWith(String.CASE_INSENSITIVE_ORDER), limited, unreadable)
    }

    fun quickOpen(paths: List<String>, query: String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return paths.take(80)
        fun score(path: String): Int? {
            val lower = path.lowercase()
            val name = lower.substringAfterLast('/')
            if (name == needle) return 0
            if (name.startsWith(needle)) return 10 + name.length
            if (name.contains(needle)) return 100 + name.indexOf(needle)
            if (lower.contains(needle)) return 200 + lower.indexOf(needle)
            var from = 0
            var distance = 0
            for (c in needle) {
                val at = lower.indexOf(c, from)
                if (at < 0) return null
                distance += at - from
                from = at + 1
            }
            return 400 + distance + lower.length
        }
        return paths.mapNotNull { path -> score(path)?.let { it to path } }
            .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second.length }.thenBy { it.second })
            .take(80).map { it.second }
    }

    fun expression(options: ProjectSearchOptions): Regex {
        require(options.query.isNotEmpty()) { "请输入搜索文字。" }
        require(options.query.length <= 500) { "搜索内容须少于 500 个字符。" }
        return Regex(if (options.regex) options.query else Regex.escape(options.query),
            if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
    }

    private fun patterns(value: String): List<Regex> = value.split(',').map(String::trim).filter(String::isNotEmpty).map { glob ->
        val out = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i++]) {
                '*' -> if (i < glob.length && glob[i] == '*') { i++; if (i < glob.length && glob[i] == '/') { i++; out.append("(?:.*/)?") } else out.append(".*") } else out.append("[^/]*")
                '?' -> out.append("[^/]")
                else -> out.append(Regex.escape(c.toString()))
            }
        }
        Regex(out.append('$').toString(), RegexOption.IGNORE_CASE)
    }

    suspend fun search(fs: WorkspaceFs, index: WorkspaceFileIndex, options: ProjectSearchOptions): ProjectSearchResult {
        val regex = expression(options)
        val include = patterns(options.include)
        val exclude = patterns(options.exclude)
        val result = mutableListOf<ProjectSearchFile>()
        var skipped = index.unreadable
        var totalBytes = 0L
        var count = 0
        var limited = index.limited
        for (path in index.paths) {
            currentCoroutineContext().ensureActive()
            fun matches(pattern: Regex) = pattern.matches(path) || pattern.matches(path.substringAfterLast('/'))
            if (include.isNotEmpty() && include.none(::matches) || exclude.any(::matches)) continue
            try {
                val node = fs.resolve(path)
                if (!node.isFile || node.length > MAX_FILE_BYTES) { skipped++; continue }
                val bytes = node.openInputStream()?.use(::readLimited) ?: run { skipped++; continue }
                if (bytes.size > MAX_FILE_BYTES) { skipped++; continue }
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_BYTES) { limited = true; break }
                val decoded = EditorFileCodec.decode(bytes) ?: run { skipped++; continue }
                val hits = mutableListOf<ProjectSearchHit>()
                var cursor = 0
                var line = 1
                var lineStart = 0
                for (match in regex.findAll(decoded.text)) {
                    currentCoroutineContext().ensureActive()
                    if (++count > MAX_RESULTS) { limited = true; break }
                    while (cursor < match.range.first) { if (decoded.text[cursor++] == '\n') { line++; lineStart = cursor } }
                    val end = decoded.text.indexOf('\n', lineStart).let { if (it < 0) decoded.text.length else it }
                    val previewStart = (match.range.first - 100).coerceAtLeast(lineStart)
                    hits += ProjectSearchHit(line, match.range.first - lineStart + 1,
                        (if (previewStart > lineStart) "…" else "") + decoded.text.substring(previewStart, end.coerceAtMost(previewStart + 240)))
                }
                if (hits.isNotEmpty()) result += ProjectSearchFile(path, EditorDiskSnapshot(bytes).version, decoded, hits, hits.size)
                if (limited && count > MAX_RESULTS) break
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                skipped++
            }
        }
        return ProjectSearchResult(result, limited, skipped)
    }

    fun replacements(result: ProjectSearchResult, options: ProjectSearchOptions, replacement: String): List<ProjectReplacement> {
        check(!result.limited) { "结果不完整，请缩小搜索范围后再替换。" }
        val expression = expression(options)
        return result.files.mapNotNull { file ->
            // Literal replacement keeps dollar signs and backslashes exactly as entered. Regex uses $1 groups.
            val text = if (options.regex) expression.replace(file.decoded.text, replacement)
                else expression.replace(file.decoded.text) { replacement }
            if (text == file.decoded.text) null else ProjectReplacement(file, text)
        }
    }

    /** Preflight every chosen file before the first write; recheck each one immediately before saving. */
    suspend fun apply(fs: WorkspaceFs, changes: List<ProjectReplacement>, hasDraft: suspend (String) -> Boolean,
        onSaved: suspend (ProjectReplacement) -> Unit = {}): ReplacementReceipt {
        require(changes.isNotEmpty()) { "请至少选择一个文件。" }
        for (change in changes) {
            currentCoroutineContext().ensureActive()
            if (fs is com.androidharness.app.workspace.ChatWorkspaceFs) {
                check(fs.target(change.source.path).first.canEdit) { "${change.source.path} 属于只读工作区。" }
            }
            check(!hasDraft(change.source.path)) { "${change.source.path} 有未保存的编辑，请保存或放弃后重新搜索。" }
            val node = fs.resolve(change.source.path)
            check(node.isFile && node.length <= MAX_FILE_BYTES) { "${change.source.path} 已更改，请重新搜索。" }
            val snapshot = EditorDiskSnapshot(node.openInputStream()?.use(::readLimited)
                ?: error("无法读取 ${change.source.path}。"))
            check(snapshot.version == change.source.version) { "${change.source.path} 在预览后已更改，请重新搜索。" }
        }
        val saved = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        for (change in changes) {
            currentCoroutineContext().ensureActive()
            try {
                check(!hasDraft(change.source.path)) { "文件有未保存的编辑。" }
                val result = saveEditorFile(fs, change.source.path, change.source.version, EditorFileCodec.encode(change.source.decoded, change.text))
                check(result is EditorSaveResult.Saved) { "文件在预览后已更改，请重新搜索。" }
                saved += change.source.path
                onSaved(change)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failed[change.source.path] = if (change.source.path in saved) "已保存，但记录更改失败：${e.message}"
                    else e.message ?: "无法保存文件。"
            }
        }
        return ReplacementReceipt(saved, failed)
    }
}
