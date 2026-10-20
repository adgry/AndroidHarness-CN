package com.androidharness.app.browser

import com.androidharness.app.core.ChatMessage
import com.androidharness.app.core.Role
import com.androidharness.app.core.ToolCallData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** Transcript-derived browser activity; no timer or global browser state is needed. */
data class BrowserToolActivity(val target: String?, val site: String, val action: String, val status: String) {
    companion object {
        fun isBrowserTool(name: String) = name.startsWith("browser_")

        /** Watch can reopen the saved workspace origin as well as a relative HTML path. */
        fun workspacePath(target: String, rootPath: String?): String? {
            val uri = runCatching { URI(target) }.getOrNull()
            val local = when {
                uri?.scheme == "https" && uri.host == "harness.workspace" && uri.rawPath?.startsWith("/ws/") == true ->
                    java.net.URLDecoder.decode(uri.rawPath.removePrefix("/ws/"), "UTF-8")
                uri?.scheme != null && uri.scheme != "file" -> return null
                else -> target
            }
            return BrowserController.normalizeWorkspacePath(local, rootPath)
        }

        private fun requestedUrl(call: ToolCallData): String? = runCatching {
            Json.parseToJsonElement(call.argumentsJson).jsonObject["url"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }

        private fun resultUrl(result: ChatMessage?): String? = result?.text?.lineSequence()
            ?.firstOrNull { it.startsWith("URL: ") }?.removePrefix("URL: ")?.trim()
            ?.takeIf { it.isNotBlank() && it != "(blank)" && it != "about:blank" }

        /** Preserve each call's own page when a later call navigates elsewhere. */
        fun targets(messages: List<ChatMessage>): Map<String, String> = buildMap {
            var current: String? = null
            for (message in messages) {
                if (message.role == Role.ASSISTANT) message.toolCalls.filter { isBrowserTool(it.name) }.forEach { call ->
                    if (call.name == "browser_navigate") current = requestedUrl(call) ?: current
                    current?.let { put(call.id, it) }
                }
                if (message.role == Role.TOOL && isBrowserTool(message.toolName.orEmpty())) {
                    resultUrl(message)?.let { url ->
                        current = url
                        message.toolCallId?.let { put(it, url) }
                    }
                }
            }
        }

        fun from(call: ToolCallData, result: ChatMessage?, running: Boolean, target: String? = null,
            awaitingApproval: Boolean = false): BrowserToolActivity {
            val url = resultUrl(result) ?: requestedUrl(call) ?: target
            val site = url?.let { runCatching { URI(it).host }.getOrNull() }?.takeIf { it.isNotBlank() }
                ?.let { if (it == "harness.workspace") "工作区页面" else it }
                ?: if (url != null) "工作区页面" else "浏览器页面"
            val status = when {
                result?.isError == true -> "失败"
                result != null -> "已完成"
                awaitingApproval -> "需要批准"
                running && call.name == "browser_wait_for" -> "等待"
                running -> "进行中"
                else -> "未完成"
            }
            val active = running && result == null && !awaitingApproval
            val action = when (call.name) {
                "browser_navigate" -> if (active) "正在打开页面…" else "打开页面"
                "browser_click" -> if (active) "正在点击页面元素…" else "点击页面元素"
                "browser_type" -> if (active) "正在输入内容…" else "输入内容"
                "browser_scroll" -> if (active) "正在滚动页面…" else "滚动页面"
                "browser_get_dom" -> if (active) "正在读取页面内容…" else "读取页面内容"
                "browser_screenshot" -> if (active) "正在截图…" else "截图"
                "browser_wait_for" -> if (active) "正在等待页面…" else "等待页面"
                "browser_eval" -> if (active) "正在检查页面…" else "检查页面"
                "browser_get_logs" -> if (active) "正在读取浏览器日志…" else "读取浏览器日志"
                "browser_back" -> if (active) "正在后退…" else "后退"
                "browser_forward" -> if (active) "正在前进…" else "前进"
                "browser_refresh" -> if (active) "正在重新加载页面…" else "重新加载页面"
                else -> if (active) "正在查看浏览器…" else "查看浏览器"
            }
            return BrowserToolActivity(url, site, action, status)
        }
    }
}
