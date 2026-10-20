package com.androidharness.app.local

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Read only a bounded header, never the tensor data or a whole multi-GB file. */
object GgufMetadata {
    const val PROBE_BYTES = 2 * 1024 * 1024
    data class Estimate(val kvBytesPerToken: Long = 256 * 1024, val maxContext: Int = 8192)

    fun inspect(bytes: ByteArray): Estimate {
        require(bytes.size >= 24 && bytes.take(4) == listOf<Byte>(71, 71, 85, 70)) { "此链接的内容不是 GGUF 模型。" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        require(buffer.int in 2..3) { "不支持此 GGUF 版本，请使用 GGUF v2 或 v3 模型。" }
        require(buffer.long > 0) { "此 GGUF 不含模型张量。" }
        val count = buffer.long
        require(count in 1..100_000) { "GGUF 元数据无效。" }
        val numbers = mutableMapOf<String, Long>()
        var architecture: String? = null
        var split = false
        fun string(): String {
            val length = buffer.long
            require(length in 0..PROBE_BYTES.toLong()) { "GGUF 字符串长度无效。" }
            if (length > buffer.remaining()) throw java.nio.BufferUnderflowException()
            val data = ByteArray(length.toInt()); buffer.get(data)
            return data.toString(Charsets.UTF_8)
        }
        fun scalar(type: Int): Long? = when (type) {
            0 -> buffer.get().toLong() and 255; 1 -> buffer.get().toLong()
            2 -> buffer.short.toLong() and 65535; 3 -> buffer.short.toLong()
            4 -> buffer.int.toLong() and 0xffffffffL; 5 -> buffer.int.toLong()
            6 -> { buffer.float; null }; 7 -> buffer.get().toLong()
            8 -> { string(); null }; 10, 11 -> buffer.long
            12 -> { buffer.double; null }; else -> error("GGUF 元数据类型无效。")
        }
        // Metadata can exceed the probe (for example a tokenizer vocabulary). Use a
        // conservative cache allowance if the architecture details are incomplete.
        try {
            repeat(count.toInt()) {
                val key = string()
                val type = buffer.int
                if (type == 9) {
                    val element = buffer.int; val length = buffer.long
                    require(length in 0..1_000_000 && element != 9)
                    repeat(length.toInt()) { scalar(element) }
                } else if (key == "general.architecture" && type == 8) {
                    architecture = string()
                } else {
                    val number = scalar(type)
                    if (number != null) numbers[key] = number
                    if (key == "split.count" && number != null && number > 1) split = true
                }
            }
        } catch (_: java.nio.BufferUnderflowException) {
            // A large tokenizer can extend past the bounded probe.
        }
        require(!split) { "不支持分片 GGUF 模型，请选择单文件 GGUF。" }
        val prefix = architecture ?: return Estimate()
        val context = numbers["$prefix.context_length"] ?: 8192
        require(context > 0) { "模型上下文长度无效。" }
        val fallback = Estimate(maxContext = context.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val layers = numbers["$prefix.block_count"] ?: return fallback
        val heads = numbers["$prefix.attention.head_count"] ?: return fallback
        val kvHeads = numbers["$prefix.attention.head_count_kv"] ?: heads
        val embedding = numbers["$prefix.embedding_length"] ?: return fallback
        if (layers !in 1..1024 || heads !in 1..1024 || kvHeads !in 1..1024 || embedding !in 1..65536) return fallback
        val keySize = numbers["$prefix.attention.key_length"] ?: (embedding / heads)
        val valueSize = numbers["$prefix.attention.value_length"] ?: (embedding / heads)
        if (keySize !in 1..65536 || valueSize !in 1..65536) return fallback
        val cache = (2L * layers * kvHeads * (keySize + valueSize)).coerceAtLeast(24 * 1024)
        if (cache > 64 * 1024 * 1024) return fallback
        return Estimate(cache, context.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
