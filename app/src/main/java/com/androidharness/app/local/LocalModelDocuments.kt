package com.androidharness.app.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.json.*

internal data class LocalModelDocument(val uri: Uri, val name: String, val bytes: Long?)

/** SAF grants are needed only while copying; imported models own their files. */
internal class LocalModelDocuments(private val resolver: ContentResolver) {
    fun file(uri: Uri): LocalModelDocument {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                return LocalModelDocument(uri, if (name >= 0) cursor.getString(name) else "model.gguf",
                    if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size).takeIf { it > 0 } else null)
            }
        }
        return LocalModelDocument(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "model.gguf", null)
    }

    fun folder(uri: Uri): List<LocalModelDocument> {
        require(DocumentsContract.isTreeUri(uri)) { "请选择包含模型文件的文件夹。" }
        val parent = DocumentsContract.getTreeDocumentId(uri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, parent)
        val documents = mutableListOf<LocalModelDocument>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(3) != DocumentsContract.Document.MIME_TYPE_DIR) documents += LocalModelDocument(
                    DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0)), cursor.getString(1),
                    if (!cursor.isNull(2)) cursor.getLong(2).takeIf { it > 0 } else null)
            }
        }
        val selected = LocalModelImportFiles.select(documents.map { it.name }).toSet()
        return documents.filter { it.name in selected }
    }

    fun open(document: LocalModelDocument): InputStream = requireNotNull(resolver.openInputStream(document.uri)) { "无法读取 ${document.name}。" }
}

/** Shared by folder imports and repository browsing to avoid duplicate weight sets. */
internal object LocalModelImportFiles {
    val companions = setOf("config.json", "tokenizer.json", "tokenizer_config.json", "generation_config.json", "chat_template.jinja", "model.safetensors.index.json")
    fun select(names: List<String>): List<String> {
        require(names.distinct().size == names.size) { "文件夹包含重复的文件名。" }
        val weights = if ("model.safetensors" in names) listOf("model.safetensors") else names.filter { it.endsWith(".safetensors") }.sorted()
        require(weights.isNotEmpty() && "config.json" in names && "tokenizer.json" in names) {
            "请选择包含 safetensors 权重、config.json 和 tokenizer.json 的文件夹。"
        }
        val shard = Regex(".*-(\\d{5})-of-(\\d{5})\\.safetensors")
        val shards = weights.mapNotNull { shard.matchEntire(it) }
        if (shards.isNotEmpty()) {
            val totals = shards.map { it.groupValues[2].toInt() }.distinct()
            require(shards.size == weights.size && totals.size == 1 && shards.size == totals.single() &&
                shards.map { it.groupValues[1].toInt() }.toSet() == (1..totals.single()).toSet()) { "缺少部分模型权重分片。" }
        }
        val result = weights + names.filter { it in companions }
        require(result.all { it.isNotBlank() && it !in listOf(".", "..") && '/' !in it && '\\' !in it }) { "模型文件名无效。" }
        return result
    }

    fun verifyIndex(folder: File) {
        if (File(folder, "model.safetensors").isFile) return
        val index = File(folder, "model.safetensors.index.json")
        if (!index.isFile) return
        val map = Json.parseToJsonElement(index.readText()).jsonObject["weight_map"]?.jsonObject
            ?: error("模型权重索引无效。")
        require(map.isNotEmpty() && map.values.all { value ->
            val name = value.jsonPrimitive.content
            name.endsWith(".safetensors") && '/' !in name && '\\' !in name && File(folder, name).isFile
        }) { "模型权重索引中列出的部分文件缺失。" }
    }

    fun copy(source: InputStream, target: File, expectedBytes: Long?, checkCancelled: () -> Unit, progress: (Long) -> Unit): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    checkCancelled()
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read); digest.update(buffer, 0, read); count += read
                    progress(count)
                }
                output.fd.sync()
            }
            checkCancelled()
            require(count > 0 && (expectedBytes == null || count == expectedBytes)) { "模型文件不完整，或在复制期间发生变化。" }
            return count to digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { target.delete(); throw e }
    }
}
