package com.androidharness.app.llm

/** A badge is presentation metadata, never a runtime permission or a promise of working tools. */
data class ModelCapabilities(val thinking: Capability, val tools: Capability, val images: Capability) {
    enum class Source { PROVIDER, DIRECTORY, CUSTOM, FAMILY, UNKNOWN }
    data class Capability(val supported: Boolean?, val source: Source) {
        val description: String get() = when (source) {
            Source.PROVIDER -> if (supported == true) "此服务商报告支持" else "此服务商报告不支持"
            Source.DIRECTORY -> if (supported == true) "模型目录列出支持，此连接尚未确认" else "模型目录列出不支持"
            Source.CUSTOM -> if (supported == true) "由你为此自定义模型设定" else "此自定义模型已标记为不支持"
            Source.FAMILY -> "未经确认的模型系列推测"
            Source.UNKNOWN -> "未报告"
        }
    }
    companion object {
        fun of(entry: ModelEntry, provider: ProviderConfig): ModelCapabilities = of(entry, provider.type,
            ModelsDev.entry(ModelsDev.providerKeyFor(provider.baseUrl), entry.id))

        internal fun of(entry: ModelEntry, type: ProviderType?, directory: ModelsDev.Entry?): ModelCapabilities {
            fun reported(value: Boolean?, listed: Boolean?): Capability = when {
                value != null -> Capability(value, Source.PROVIDER)
                listed != null -> Capability(listed, Source.DIRECTORY)
                else -> Capability(null, Source.UNKNOWN)
            }
            val reasoning = ModelReasoning.of(entry, type)
            val thinking = when (reasoning) {
                ModelReasoning.REPORTED -> Capability(true, Source.PROVIDER)
                ModelReasoning.CUSTOM -> Capability(true, Source.CUSTOM)
                ModelReasoning.UNSUPPORTED -> Capability(false, if (entry.custom) Source.CUSTOM else Source.PROVIDER)
                else -> when {
                    directory?.reasoning != null -> Capability(directory.reasoning, Source.DIRECTORY)
                    reasoning == ModelReasoning.INFERRED -> Capability(true, Source.FAMILY)
                    else -> Capability(null, Source.UNKNOWN)
                }
            }
            return ModelCapabilities(thinking, reported(entry.tools, directory?.tools), reported(entry.imageInput, directory?.imageInput))
        }
    }
}
