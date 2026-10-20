package com.androidharness.app.agent

import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.ToolCallData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

enum class ToolActivityKind { READ, SEARCH, EDIT, COMMAND, VERIFY, WEB, QUESTION, SUBAGENT, MEMORY, OTHER }

data class ToolPresentation(
    val title: String,
    val action: String,
    val detail: String? = null,
    val kind: ToolActivityKind = ToolActivityKind.OTHER,
)

fun toolPresentation(call: ToolCallData, result: ChatMessage? = null): ToolPresentation {
    val args = runCatching { Json.parseToJsonElement(call.argumentsJson).jsonObject }.getOrNull()
    fun arg(name: String): String? = when (val el = args?.get(name)) {
        is JsonPrimitive -> el.content
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString(", ")
        else -> null
    }
    fun target(name: String = "path"): String = arg(name)?.trim()?.ifBlank { null } ?: "文件"
    fun leaf(value: String): String = value.trimEnd('/').substringAfterLast('/').ifBlank { value }
    fun okTitle(success: String, failure: String = success): String = when {
        result == null -> success
        result.isError -> failure
        else -> success
    }
    fun countResultLines(): Int? {
        val text = result?.text ?: return null
        val count = text.lineSequence().count { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !trimmed.startsWith("[") && trimmed != "(empty directory)"
        }
        return count.takeIf { it > 0 }
    }

    val presentation = when (call.name) {
        "create_artifact" -> ToolPresentation(
            if (result == null) "正在创建概念" else okTitle("已创建概念", "概念创建失败"),
            "正在创建 ${arg("title") ?: "交互概念"}…", arg("title"), ToolActivityKind.OTHER)
        "read_artifact" -> ToolPresentation("已读取概念", "正在读取概念源码…", kind = ToolActivityKind.READ)
        "write_file" -> {
            val path = target()
            ToolPresentation(
                title = if (result == null) "创建 ${leaf(path)}" else okTitle("已创建 ${leaf(path)}", "无法创建 ${leaf(path)}"),
                action = "正在创建 $path…",
                detail = path,
                kind = ToolActivityKind.EDIT,
            )
        }
        "edit_file", "multi_edit" -> {
            val path = target()
            ToolPresentation(
                title = if (result == null) "编辑 ${leaf(path)}" else okTitle("已编辑 ${leaf(path)}", "编辑失败：${leaf(path)}"),
                action = "正在编辑 $path…",
                detail = path,
                kind = ToolActivityKind.EDIT,
            )
        }
        "apply_patch" -> ToolPresentation(
            title = if (result == null) "应用补丁" else okTitle("已应用补丁", "补丁应用失败"),
            action = "正在应用补丁…",
            kind = ToolActivityKind.EDIT,
        )
        "read_file" -> {
            val path = target()
            ToolPresentation("已读取 ${leaf(path)}", "正在读取 $path…", path, ToolActivityKind.READ)
        }
        "file_info" -> {
            val path = target()
            ToolPresentation("已查看 ${leaf(path)}", "正在查看 $path…", path, ToolActivityKind.READ)
        }
        "list_dir" -> {
            val path = arg("path") ?: "."
            ToolPresentation("已列出 $path", "正在列出 $path…", kind = ToolActivityKind.READ)
        }
        "search_files" -> {
            val pattern = arg("pattern") ?: "文件"
            val count = countResultLines()
            ToolPresentation(
                title = if (count != null) "找到 $count 个文件" else "已搜索文件",
                action = "正在查找 $pattern…",
                detail = pattern,
                kind = ToolActivityKind.SEARCH,
            )
        }
        "grep" -> {
            val pattern = arg("pattern") ?: "模式"
            val count = countResultLines()
            ToolPresentation(
                title = if (count != null) "搜索到 $count 个匹配项" else "已搜索代码",
                action = "正在搜索 $pattern…",
                detail = pattern,
                kind = ToolActivityKind.SEARCH,
            )
        }
        "codegraph_explore" -> {
            val query = arg("query") ?: "代码"
            ToolPresentation("已探索代码图谱", "正在探索 ${query.take(48)}…", query, ToolActivityKind.SEARCH)
        }
        "codegraph_node" -> {
            val node = arg("name") ?: arg("file") ?: "符号"
            ToolPresentation("已读取代码图谱节点", "正在读取 ${node.take(48)}…", node, ToolActivityKind.READ)
        }
        "codegraph_impact" -> {
            val symbol = arg("symbol") ?: "符号"
            ToolPresentation("已分析更改影响", "正在分析 ${symbol.take(48)}…", symbol, ToolActivityKind.SEARCH)
        }
        "codegraph_affected" -> ToolPresentation(
            "已查找受影响的测试", "正在查找受影响的测试…", kind = ToolActivityKind.SEARCH,
        )
        "codegraph_sync" -> ToolPresentation(
            if (result?.isError == true) "CodeGraph 同步失败" else "已同步 CodeGraph",
            "正在同步 CodeGraph…",
            kind = ToolActivityKind.READ,
        )
        "shell" -> {
            val command = arg("command")?.trim().orEmpty()
            val verification = isVerificationCommand(command)
            val title = if (verification) {
                when {
                    result == null -> "运行测试"
                    result.isError -> "测试失败"
                    else -> "测试通过"
                }
            } else {
                val short = command.lineSequence().firstOrNull()?.take(52)?.ifBlank { "命令" } ?: "命令"
                when {
                    result == null -> "运行 $short"
                    result.isError -> "命令失败"
                    else -> "已运行 $short"
                }
            }
            ToolPresentation(
                title = title,
                action = if (verification) "正在运行测试…" else "正在运行 ${command.take(48).ifBlank { "命令" }}…",
                detail = command.takeIf { it.isNotBlank() },
                kind = if (verification) ToolActivityKind.VERIFY else ToolActivityKind.COMMAND,
            )
        }
        "shell_background" -> {
            val command = arg("command")?.trim().orEmpty()
            ToolPresentation(
                title = if (result?.isError == true) "后台任务失败" else "已启动后台任务",
                action = "正在启动 ${command.take(48).ifBlank { "后台任务" }}…",
                detail = command.takeIf { it.isNotBlank() },
                kind = ToolActivityKind.COMMAND,
            )
        }
        "git_status" -> ToolPresentation("已检查 Git 状态", "正在检查 Git 状态…", kind = ToolActivityKind.READ)
        "git_diff" -> ToolPresentation("已读取 Git 差异", "正在读取 Git 差异…", kind = ToolActivityKind.READ)
        "git_log", "git_show" -> ToolPresentation("已读取 Git 历史", "正在读取 Git 历史…", kind = ToolActivityKind.READ)
        "git_commit" -> ToolPresentation(if (result?.isError == true) "提交失败" else "已提交更改", "正在提交…", kind = ToolActivityKind.EDIT)
        "web_fetch" -> {
            val url = arg("url") ?: "页面"
            ToolPresentation("已获取页面", "正在获取 ${url.take(48)}…", url, ToolActivityKind.WEB)
        }
        "web_search" -> {
            val query = arg("query") ?: ""
            ToolPresentation("已搜索网络", "正在搜索：${query.take(48)}…", query, ToolActivityKind.WEB)
        }
        "http_request" -> {
            val url = arg("url") ?: "API"
            ToolPresentation("已调用 API", "正在调用 ${url.take(48)}…", url, ToolActivityKind.WEB)
        }
        "ask_user" -> ToolPresentation("已提问", "等待你的回答…", kind = ToolActivityKind.QUESTION)
        "task" -> {
            val title = arg("title")?.takeIf { it.isNotBlank() } ?: "研究子代理"
            ToolPresentation("已委派 $title", "正在委派：$title…", kind = ToolActivityKind.SUBAGENT)
        }
        "memory_write" -> ToolPresentation("已保存记忆", "正在保存到记忆…", kind = ToolActivityKind.MEMORY)
        "memory_read", "memory_search" -> ToolPresentation("已读取记忆", "正在读取记忆…", kind = ToolActivityKind.MEMORY)
        "todo_write" -> ToolPresentation("已更新任务列表", "正在更新任务列表…", kind = ToolActivityKind.OTHER)
        "skill_view" -> {
            val name = arg("name") ?: "技能"
            ToolPresentation("已加载 $name", "正在加载技能 $name…", kind = ToolActivityKind.READ)
        }
        "skills_list" -> ToolPresentation("已列出技能", "正在列出技能…", kind = ToolActivityKind.READ)
        "pkg_install" -> {
            val packages = arg("packages") ?: arg("package") ?: "软件包"
            ToolPresentation(
                if (result?.isError == true) "安装失败" else "已安装 $packages",
                "正在安装 $packages…",
                kind = ToolActivityKind.EDIT,
            )
        }
        "pkg_search" -> {
            val query = arg("query") ?: "软件包"
            ToolPresentation("已搜索软件包", "正在搜索软件包 $query…", query, ToolActivityKind.SEARCH)
        }
        "pkg_list" -> ToolPresentation("已列出软件包", "正在列出已安装的软件包…", kind = ToolActivityKind.READ)
        "read_logcat" -> ToolPresentation("已读取 logcat", "正在读取 logcat…", kind = ToolActivityKind.READ)
        "read_image", "browser_screenshot" -> ToolPresentation("已查看图片", "正在查看图片…", kind = ToolActivityKind.READ)
        "browser_get_dom", "browser_get_logs", "browser_get_url" -> ToolPresentation("已查看浏览器", "正在查看浏览器…", kind = ToolActivityKind.READ)
        "browser_wait_for" -> ToolPresentation("已等待页面", "正在等待页面…", kind = ToolActivityKind.READ)
        else -> {
            val label = call.name.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ToolPresentation(label, "$label…")
        }
    }
    return call.workspaceName?.let { name -> presentation.copy(title = "$name · ${presentation.title}",
        action = "$name · ${presentation.action}", detail = listOfNotNull(name, presentation.detail).joinToString(" · ")) } ?: presentation
}

fun describeToolCall(call: ToolCallData): String = toolPresentation(call).action

internal fun isVerificationCommand(command: String): Boolean {
    val lower = command.lowercase()
    return listOf(
        "gradlew test", "gradle test", "testdebugunittest", "connectedandroidtest", "pytest",
        "python -m pytest", "python3 -m pytest", "npm test", "npm run test", "pnpm test",
        "yarn test", "cargo test", "go test", "mvn test", "mvnw test", "ctest",
    ).any { it in lower }
}
