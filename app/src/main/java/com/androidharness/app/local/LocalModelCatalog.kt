package com.androidharness.app.local

import kotlinx.serialization.Serializable

const val GIB = 1_073_741_824L

@Serializable
enum class LocalModelFormat { GGUF, SAFETENSORS }

@Serializable
data class LocalModelAsset(val filename: String, val url: String, val bytes: Long, val sha256: String = "")

@Serializable
data class LocalModelSpec(
    val id: String,
    val title: String,
    val repository: String,
    val revision: String,
    val filename: String,
    val bytes: Long,
    val sha256: String,
    val minimumRamGiB: Int,
    val kvBytesPerToken: Long,
    val description: String,
    val license: String = "Apache-2.0",
    val downloadUrl: String? = null,
    val sourcePage: String? = null,
    val custom: Boolean = false,
    val maxContext: Int = 8192,
    val format: LocalModelFormat = LocalModelFormat.GGUF,
    val assets: List<LocalModelAsset> = emptyList(),
    val projector: LocalModelAsset? = null,
    val localFile: Boolean = false,
    val abliterated: Boolean = false,
) {
    val url get() = downloadUrl ?: "https://huggingface.co/$repository/resolve/$revision/$filename"
    val modelPage get() = sourcePage ?: "https://huggingface.co/$repository"
    val defaultContext get() = minOf(2048, maxContext)
    val downloadBytes get() = (if (assets.isEmpty()) bytes else assets.sumOf { it.bytes }) + (projector?.bytes ?: 0)
    fun estimatedMemory(context: Int): Long = bytes + (projector?.bytes ?: 0) + 640L * 1024 * 1024 + context.toLong() * kvBytesPerToken
}

object LocalModelCatalog {
    val models = listOf(
        LocalModelSpec("qwen-05b", "Qwen 2.5 0.5B", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            "9217f5db79a29953eb74d5343926648285ec7e67", "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            491400032, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db",
            3, 24576, "下载较小，适合基础聊天，编码能力有限。"),
        LocalModelSpec("qwen-15b", "Qwen 2.5 1.5B", "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            "91cad51170dc346986eccefdc2dd33a9da36ead9", "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            1117320736, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            4, 28672, "通用聊天效果更好，CPU 速度取决于手机性能。"),
        LocalModelSpec("qwen-coder-15b", "Qwen 2.5 Coder 1.5B", "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            "f86cb2c1fa58255f8052cc32aeede1b7482d4361", "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            1117320768, "cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046",
            4, 28672, "适合编码辅助和小幅修改，工具调用可靠性取决于具体任务。"),
        LocalModelSpec("qwen-3b", "Qwen 2.5 3B", "Qwen/Qwen2.5-3B-Instruct-GGUF",
            "7dabda4d13d513e3e842b20f0d435c732f172cbe", "qwen2.5-3b-instruct-q4_k_m.gguf",
            2104932768, "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
            6, 73728, "质量更高，下载量更大，发热更多。", "Qwen Research License"),
    ) + LocalModelRecommendations.models
    fun find(id: String) = models.firstOrNull { it.id == id }
    const val PROVIDER_PREFIX = "local-model:"
    fun isLocal(id: String) = id.startsWith(PROVIDER_PREFIX)
}

@Serializable
data class LocalModelLimits(
    val context: Int = 2048,
    val input: Int = 1536,
    val output: Int = 512,
    val threads: Int = 4,
) {
    fun validate() {
        require(context > 0) { "上下文长度必须为正数。" }
        require(input > 0 && output > 0) { "输入和输出上限必须为正数。" }
        require(input.toLong() + output < Int.MAX_VALUE) { "合计令牌预算超出引擎可寻址的范围。" }
        require(threads > 0) { "CPU 线程数必须为正数。" }
    }
}

data class LocalDeviceProfile(
    val totalRam: Long,
    val availableRam: Long,
    val freeStorage: Long,
    val abi: String,
    val cores: Int,
    val lowMemory: Boolean,
) {
    val supported get() = abi == "arm64-v8a" || abi == "x86_64"
    val rank get() = when {
        !supported -> "不支持的架构"
        totalRam < 3 * GIB -> "内存有限"
        totalRam < 4 * GIB -> "基础"
        totalRam < 6 * GIB -> "均衡"
        else -> "较大内存"
    }
    fun fits(model: LocalModelSpec, context: Int = model.defaultContext): Boolean =
        supported && context > 0 && totalRam >= model.minimumRamGiB * GIB * 9 / 10 &&
            model.estimatedMemory(context) <= totalRam * 55 / 100
    fun canLoad(model: LocalModelSpec, context: Int = model.defaultContext): Boolean = fits(model, context) &&
        !lowMemory && availableRam >= model.estimatedMemory(context) + 256L * 1024 * 1024
}

/** Estimates inform consent, never decide whether the user may try a model. */
fun localModelWarnings(model: LocalModelSpec, device: LocalDeviceProfile, limits: LocalModelLimits,
    downloading: Boolean = false): List<String> = buildList {
    if (!device.supported) add("此设备架构没有可用的本地引擎。可以下载模型，但推理可能失败。")
    if (!device.canLoad(model, limits.context)) add("预计模型内存用量超出建议预算或可用内存。Android 可能会关闭应用，生成也可能失败。")
    if (limits.context > model.maxContext) add("上下文超出模型的训练长度，回答质量可能下降，引擎也可能失败。")
    if (limits.input.toLong() + limits.output > limits.context) add("输入与输出合计超出已保存的上下文长度。引擎会在需要时提示扩展，这将使用更多内存。")
    if (limits.threads > device.cores) add("线程数超过 CPU 核心数，可能导致生成变慢并增加发热。")
    val storage = if (model.format == LocalModelFormat.SAFETENSORS) model.downloadBytes * 3 else model.downloadBytes
    if (downloading && device.freeStorage < storage + 256L * 1024 * 1024) add("可用存储空间可能不足以下载${if (model.format == LocalModelFormat.SAFETENSORS) "和转换" else ""}，操作可能失败。")
}
