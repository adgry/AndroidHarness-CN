package com.androidharness.app.ui.files

data class SelectedCode(val path: String, val startLine: Int, val endLine: Int, val text: String, val unsaved: Boolean) {
    val location: String get() = if (startLine == endLine) "$path · 第 $startLine 行" else "$path · 第 $startLine–$endLine 行"

    fun prompt(action: CodeAction): String {
        val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
        return "${action.instruction}\n\nFile: $path\nLines: $startLine–$endLine\n" +
            (if (unsaved) "This is a selection from an unsaved editor buffer. Check the current file before making changes.\n" else "") +
            "Keep the work focused on this selection; read surrounding code if needed.\n\n$fence\n$text\n$fence"
    }

    companion object {
        const val MAX_CHARS = 64_000
        fun capture(path: String, text: String, start: Int, end: Int, unsaved: Boolean): SelectedCode? {
            if (start < 0 || end > text.length || start >= end || end - start > MAX_CHARS) return null
            var first = 1
            for (index in 0 until start) if (text[index] == '\n') first++
            var last = first
            for (index in start until end) if (text[index] == '\n') last++
            if (text[end - 1] == '\n') last--
            return SelectedCode(path, first, last.coerceAtLeast(first), text.substring(start, end), unsaved)
        }
    }
}

enum class CodeAction(val label: String, val description: String, val instruction: String) {
    EXPLAIN("解释", "了解这段代码的作用", "Explain the selected code and any important assumptions. Do not edit files."),
    FIX("修复", "查找并修复所选代码的问题", "Find and fix bugs in the selected code. Explain the issue and verify the fix."),
    REFACTOR("重构", "在保留行为的同时提高可读性", "Refactor the selected code for clarity while preserving its behavior. Verify the result."),
    TESTS("编写测试", "覆盖此代码的行为和边界情况", "Write meaningful tests for the selected code, including relevant edge cases. Run the tests if possible."),
}

/** A targeted composer handoff; an unrelated mounted chat must never consume it. */
data class CodeChatDraft(val sessionId: String, val prompt: String)
