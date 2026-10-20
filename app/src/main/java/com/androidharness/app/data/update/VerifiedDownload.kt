package com.androidharness.app.data.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Writes only verified complete APKs; never trusts an asset's filename as a filesystem path. */
object VerifiedDownload {
    const val MAX_BYTES = 512 * 1024 * 1024L

    fun copy(input: InputStream, destination: File, expectedBytes: Long, expectedDigest: String?,
             onProgress: (Long, Long) -> Unit) {
        val partial = File(destination.parentFile, destination.name + ".part")
        destination.delete()
        partial.delete()
        try {
            require(expectedBytes in 1..MAX_BYTES) { "更新包大小不正确或超过 512 MB。" }
            val sha = expectedDigest?.let {
                require(Regex("sha256:[0-9a-fA-F]{64}").matches(it)) { "更新包校验值格式不正确。" }
                it.substringAfter(':').lowercase()
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    read += count
                    require(read <= expectedBytes && read <= MAX_BYTES) { "下载内容超出声明的大小。" }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                    onProgress(read, expectedBytes)
                }
                output.fd.sync()
            }
            require(read == expectedBytes) { "更新包下载不完整，请重试。" }
            if (sha != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == sha) { "更新包 SHA-256 校验失败，请重新下载。" }
            }
            check(partial.renameTo(destination)) { "无法保存更新包，请检查存储空间。" }
        } finally { partial.delete() }
    }
}
