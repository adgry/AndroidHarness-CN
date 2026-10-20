package com.androidharness.app.ui.settings

enum class DiagnosticState(val label: String) {
    READY("就绪"), ISSUE("需要处理"), OFF("关闭"), NOT_CHECKED("未检查"), CHECKING("正在检查…"), INSTALLED("已安装"), REACHABLE("目录可访问"),
}

enum class DiagnosticArea(val label: String) {
    PROVIDER("默认提供商"), STORAGE("存储访问"), WORKSPACE("默认工作区"), LINUX("Linux 工具"), SHIZUKU("Shizuku"), SSH("SSH 工作区"), MCP("已连接工具"), NOTIFICATIONS("通知"),
}

enum class CatalogCheck(val detail: String) {
    NOT_CHECKED("检查模型列表以测试访问。发送模型请求需要单独测试。"),
    CHECKING("正在连接提供商…"),
    REACHABLE("提供商已返回模型列表，尚未测试模型生成能力和配额。"),
    EMPTY("提供商未返回模型。请检查提供商地址和账号权限。"),
    AUTH("登录或 API 密钥被拒绝。请重新连接或更新密钥。"),
    RATE_LIMIT("提供商正在限制请求。请稍后重试，或选择其他提供商。"),
    FAILED("无法获取模型列表。请检查网络连接和提供商地址后重试。"),
    TIMEOUT("检查超时。请检查网络连接后重试。"),
}

fun catalogFailure(message: String): CatalogCheck = when {
    Regex("\\b(401|403)\\b").containsMatchIn(message) -> CatalogCheck.AUTH
    Regex("\\b429\\b").containsMatchIn(message) -> CatalogCheck.RATE_LIMIT
    else -> CatalogCheck.FAILED
}

fun shizukuDiagnostic(granted: Boolean, ready: Boolean, binding: Boolean, failed: Boolean): DiagnosticState = when {
    !granted -> DiagnosticState.OFF
    ready -> DiagnosticState.READY
    binding -> DiagnosticState.CHECKING
    failed -> DiagnosticState.ISSUE
    else -> DiagnosticState.NOT_CHECKED
}

/** Export a whitelist of states, never configuration, names, paths or raw errors. */
fun diagnosticReport(version: String, androidApi: Int, states: Map<DiagnosticArea, DiagnosticState>,
    providerCount: Int, enabledToolCount: Int): String = buildString {
    appendLine("AndroidHarness 故障排查")
    // Only a build version's conventional characters may enter the export.
    appendLine("应用：${version.filter { it.isLetterOrDigit() || it in ".-+" }.take(40)}")
    appendLine("Android API：$androidApi")
    appendLine("范围：默认提供商和默认工作区")
    states.forEach { (area, state) -> appendLine("${area.label}: ${state.label}") }
    appendLine("已保存提供商：$providerCount")
    appendLine("已启用 MCP 服务器：$enabledToolCount")
    appendLine("目录可访问不代表模型可生成内容或仍有剩余配额。")
    append("不包含凭据、端点、名称、文件路径、聊天和原始日志。")
}
