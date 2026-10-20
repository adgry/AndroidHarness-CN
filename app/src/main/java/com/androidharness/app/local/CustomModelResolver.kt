package com.androidharness.app.local

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Public Hugging Face repositories/file pages, or direct HTTPS GGUF files. */
class CustomModelResolver(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS).followSslRedirects(false).build()) {
    data class Link(val url: String, val repository: String? = null, val revision: String? = null, val file: String? = null)

    fun resolve(text: String, format: LocalModelFormat = LocalModelFormat.GGUF): List<LocalModelSpec> {
        val link = parseLink(text, format)
        if (link.repository == null) return listOf(inspect(spec(link.url, link.url.substringBefore('?').substringAfterLast('/'), 24, "", link.url)))
        val endpoint = "https://huggingface.co/api/models/${link.repository}".toHttpUrlOrNull()!!.newBuilder()
        link.revision?.let { endpoint.addPathSegment("revision").addPathSegment(it) }
        endpoint.addQueryParameter("blobs", "true")
        val root = client.newCall(Request.Builder().url(endpoint.build()).build()).execute().use { response ->
            checkResponse(response.code)
            val bytes = readPrefix(checkNotNull(response.body).byteStream(), 6 * 1024 * 1024 + 1)
            check(bytes.size <= 6 * 1024 * 1024) { "仓库过大，无法浏览。请粘贴 GGUF 的 HTTPS 直接下载链接。" }
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        }
        return if (format == LocalModelFormat.SAFETENSORS) listOf(parseSafetensors(link, root)) else parseRepository(link, root)
    }

    internal fun parseRepository(link: Link, root: JsonObject): List<LocalModelSpec> {
        val revision = root["sha"]?.jsonPrimitive?.content.orEmpty()
        require(revision.matches(Regex("[a-f0-9]{40}"))) { "Hugging Face 未返回固定的模型修订版本。" }
        val license = root["cardData"]?.let { it as? JsonObject }?.get("license")?.let { it as? JsonPrimitive }?.content
            ?: "许可证条款请参阅模型来源"
        val repo = requireNotNull(link.repository)
        val files = root["siblings"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val obj = entry.jsonObject
            val filename = obj["rfilename"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!isChatFile(filename) || link.file != null && link.file != filename) return@mapNotNull null
            val lfs = obj["lfs"] as? JsonObject
            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: lfs?.get("size")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val hash = lfs?.get("sha256")?.jsonPrimitive?.content.orEmpty()
            if (size < 24 || !hash.matches(Regex("[a-f0-9]{64}"))) return@mapNotNull null
            val url = "https://huggingface.co".toHttpUrlOrNull()!!.newBuilder()
            repo.split('/').forEach(url::addPathSegment)
            url.addPathSegment("resolve").addPathSegment(revision)
            filename.split('/').forEach(url::addPathSegment)
            spec(url.build().toString(), filename.substringAfterLast('/'), size, hash, "https://huggingface.co/$repo")
                .copy(repository = repo, revision = revision, filename = filename, license = license)
        }.sortedBy { it.bytes }
        require(files.isNotEmpty()) { "No supported single-file GGUF found. Paste an Instruct/chat GGUF model link; safetensors, split files and vision projectors cannot be used here." }
        return files
    }

    internal fun parseSafetensors(link: Link, root: JsonObject): LocalModelSpec {
        val revision = root["sha"]?.jsonPrimitive?.content.orEmpty()
        require(revision.matches(Regex("[a-f0-9]{40}"))) { "模型来源未提供固定的修订版本。" }
        val repo = requireNotNull(link.repository) { "请使用 Hugging Face 仓库，以包含配置和分词器文件。" }
        val folder = link.file?.substringBeforeLast('/', "").orEmpty()
        val files = root["siblings"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val obj = entry.jsonObject
            val filename = obj["rfilename"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (filename.substringBeforeLast('/', "") != folder) return@mapNotNull null
            val name = filename.substringAfterLast('/')
            if (!name.endsWith(".safetensors") && name !in listOf("config.json", "tokenizer.json", "tokenizer_config.json", "generation_config.json", "chat_template.jinja")) return@mapNotNull null
            val lfs = obj["lfs"] as? JsonObject
            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: lfs?.get("size")?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            require(size > 0 && filename.split('/').all { it.isNotEmpty() && it != "." && it != ".." })
            val url = "https://huggingface.co".toHttpUrlOrNull()!!.newBuilder()
            repo.split('/').forEach(url::addPathSegment)
            url.addPathSegment("resolve").addPathSegment(revision)
            filename.split('/').forEach(url::addPathSegment)
            LocalModelAsset(name, url.build().toString(), size, lfs?.get("sha256")?.jsonPrimitive?.content.orEmpty())
        }
        val selectedNames = LocalModelImportFiles.select(files.map { it.filename }).toSet()
        val selectedFiles = files.filter { it.filename in selectedNames }
        val page = "https://huggingface.co/$repo/tree/$revision"
        val id = MessageDigest.getInstance("SHA-256").digest((page + folder).toByteArray()).take(16).joinToString("") { "%02x".format(it) }
        return LocalModelSpec("custom-$id", repo.substringAfter('/'), repo, revision, "model.safetensors", selectedFiles.sumOf { it.bytes }, "",
            1, 256 * 1024, "Safetensors 会在此设备上转换为 Q4 GGUF。工具调用取决于模型的聊天模板和训练。",
            sourcePage = page, downloadUrl = page, custom = true, format = LocalModelFormat.SAFETENSORS, assets = selectedFiles)
    }

    fun resolveProjector(text: String): LocalModelAsset {
        val link = parseLink(text, projector = true)
        val url = link.url.toHttpUrlOrNull()!!.newBuilder()
        if (link.repository != null) {
            require(link.file != null) { "请粘贴匹配的视觉投影器文件链接，而不是仓库页面。" }
            url.encodedPath("/${link.repository}/resolve/${link.revision}/${link.file}")
        }
        val model = inspect(spec(url.build().toString(), link.file?.substringAfterLast('/') ?: "mmproj.gguf", 24, "", text))
        return LocalModelAsset(model.filename, model.url, model.bytes, model.sha256)
    }

    fun inspect(model: LocalModelSpec): LocalModelSpec {
        if (model.format == LocalModelFormat.SAFETENSORS) return model
        val request = Request.Builder().url(model.url).header("Range", "bytes=0-${GgufMetadata.PROBE_BYTES - 1}").build()
        return client.newCall(request).execute().use { response ->
            checkResponse(response.code)
            val body = checkNotNull(response.body) { "模型响应为空。" }
            val size = if (response.code == 206) response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                else body.contentLength().takeIf { it > 0 }
            require(size != null && size >= 24) { "服务器必须提供完整的 GGUF 文件大小。" }
            require(model.bytes == 24L || model.bytes == size) { "文件大小已更改，请重新粘贴链接。" }
            val estimate = body.byteStream().use { GgufMetadata.inspect(readPrefix(it, GgufMetadata.PROBE_BYTES)) }
            model.copy(bytes = size, kvBytesPerToken = estimate.kvBytesPerToken, maxContext = estimate.maxContext)
        }
    }

    private fun spec(url: String, title: String, size: Long, hash: String, page: String): LocalModelSpec {
        val id = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(16).joinToString("") { "%02x".format(it) }
        return LocalModelSpec("custom-$id", title.removeSuffix(".gguf"), "", "", title, size, hash,
            1, 256 * 1024, "自定义 GGUF，需要本地引擎支持的聊天模板。内存用量根据模型头估算，无法估算时采用保守值。",
            "许可证条款请参阅模型来源", url, page, true)
    }

    private fun checkResponse(code: Int) {
        check(code in 200..299) { when (code) {
            401, 403 -> "此模型为私有或受限模型，请使用公开的 GGUF 下载链接。"
            404 -> "未找到模型或文件，请检查链接。"
            429 -> "下载服务繁忙，请稍后重试。"
            else -> "模型服务器返回 HTTP $code，请稍后重试。"
        } }
    }

    companion object {
        fun parseLink(text: String, format: LocalModelFormat = LocalModelFormat.GGUF, projector: Boolean = false): Link {
            val url = text.trim().toHttpUrlOrNull() ?: error("请粘贴有效的 Hugging Face 或 GGUF HTTPS 链接。")
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "请使用不包含用户名和密码的 HTTPS 链接。" }
            val clean = url.newBuilder().fragment(null).build()
            val parts = clean.pathSegments.filter { it.isNotEmpty() }
            if (clean.host in listOf("huggingface.co", "www.huggingface.co")) {
                require(parts.size >= 2 && parts.take(2).all { it.matches(Regex("[A-Za-z0-9_.-]+")) }) { "请粘贴 Hugging Face 模型仓库或 GGUF 文件链接。" }
                val repo = parts.take(2).joinToString("/")
                if (parts.size == 2) return Link(clean.toString(), repo)
                require(parts.size >= 4 && parts[2] in listOf("blob", "resolve", "tree")) { "请粘贴模型仓库、文件页面或 GGUF 下载链接。" }
                val file = if (parts[2] == "tree") null else parts.drop(4).joinToString("/")
                require(file == null || (if (format == LocalModelFormat.SAFETENSORS) file.endsWith(".safetensors") else if (projector) file.endsWith(".gguf", true) else isChatFile(file))) { "请选择单文件聊天 GGUF，不要选择 safetensors、分片模型或视觉投影器。" }
                return Link(clean.toString(), repo, parts[3], file)
            }
            require(format == LocalModelFormat.GGUF && (if (projector) parts.lastOrNull().orEmpty().endsWith(".gguf", true) else isChatFile(parts.lastOrNull().orEmpty()))) { "必须是单个 .gguf 模型文件的直接链接。" }
            return Link(clean.toString())
        }
        private fun isChatFile(name: String): Boolean = name.endsWith(".gguf", ignoreCase = true) &&
            !name.substringAfterLast('/').startsWith("mmproj", ignoreCase = true) &&
            !Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(name)
    }
}

internal fun readPrefix(input: java.io.InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var count = 0
    while (count < limit) {
        val read = input.read(buffer, count, limit - count)
        if (read < 0) break
        if (read > 0) count += read
    }
    return buffer.copyOf(count)
}
