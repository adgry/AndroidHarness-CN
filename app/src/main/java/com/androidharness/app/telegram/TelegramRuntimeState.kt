package com.androidharness.app.telegram

internal data class TelegramRuntimeState(val running: Boolean = false, val connected: Boolean = false, val reconnecting: Boolean = false,
    val message: String? = null, val action: String? = null, val activeRequestId: Long? = null, val lastContactAt: Long? = null) {
    val label get() = when {
        !running -> "已暂停"
        !connected -> if (reconnecting) "正在重新连接" else "正在连接"
        action == "等待你的批准" || action?.contains("approval", true) == true -> "等待批准"
        action == "等待你的回答" || action?.contains("answer", true) == true -> "等待回答"
        activeRequestId != null -> "正在处理"
        else -> "正在监听"
    }
}
