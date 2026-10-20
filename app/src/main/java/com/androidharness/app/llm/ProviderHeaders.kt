package com.androidharness.app.llm

import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

@Serializable
data class ProviderHeader(val name: String, val value: String, val enabled: Boolean = true) {
    override fun toString(): String = "ProviderHeader(name=$name, enabled=$enabled)"
}

/** One policy for the editor, encrypted storage, backups, and every provider transport. */
object ProviderHeaders {
    const val MAX_HEADERS = 20
    private val token = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    private val managed = setOf("authorization", "proxy-authorization", "x-api-key", "x-goog-api-key",
        "host", "content-type", "content-length", "connection", "transfer-encoding", "upgrade", "trailer")

    fun error(headers: List<ProviderHeader>): String? {
        if (headers.size > MAX_HEADERS) return "最多可添加 $MAX_HEADERS 个自定义请求头。"
        val names = mutableSetOf<String>()
        headers.forEach { header ->
            val name = header.name.trim()
            if (name.length !in 1..256 || !token.matches(name)) return "请输入有效的请求头名称。"
            val lower = name.lowercase(Locale.ROOT)
            if (lower in managed) return "身份验证和连接请求头由应用管理。"
            if (!names.add(lower)) return "请求头名称不能重复。"
            if (header.value.length > 8192 || header.value.any { it != '\t' && it !in ' '..'~' }) {
                return "请求头值必须为单行 HTTP 文本，最多 8192 个字符。"
            }
        }
        return null
    }

    fun normalized(headers: List<ProviderHeader>): List<ProviderHeader> {
        require(error(headers) == null) { error(headers).orEmpty() }
        return headers.map { it.copy(name = it.name.trim()) }
    }

    fun editable(config: ProviderConfig): Boolean = config.id != HarnessProvider.ID &&
        !com.androidharness.app.chatgpt.ChatGptProtocol.isProvider(config.id) &&
        !com.androidharness.app.local.LocalModelCatalog.isLocal(config.id) && !config.baseUrl.startsWith("local://")

    fun apply(builder: Request.Builder, config: ProviderConfig): Request.Builder {
        if (editable(config)) normalized(config.headers).filter { it.enabled }.forEach { builder.header(it.name, it.value) }
        return builder
    }

    /** Custom headers can contain credentials; never forward them through a redirect. */
    fun client(config: ProviderConfig, client: OkHttpClient): OkHttpClient =
        if (editable(config) && config.headers.any { it.enabled }) client.newBuilder()
            .followRedirects(false).followSslRedirects(false).build() else client
}
