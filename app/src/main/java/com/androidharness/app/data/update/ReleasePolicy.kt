package com.androidharness.app.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A release always includes an APK compatible with this device. */
data class UpdateRelease(
    val tag: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkBytes: Long,
    val digest: String? = null,
)

object ReleasePolicy {
    private val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun select(payload: String, source: UpdateRepository, current: String,
               includePrereleases: Boolean, supportedAbis: List<String>): UpdateRelease? {
        val releases = Json.parseToJsonElement(payload).jsonArray
        return releases.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if (obj.value("draft") == "true" || (!includePrereleases && obj.value("prerelease") == "true")) return@mapNotNull null
            val tag = obj.value("tag_name") ?: return@mapNotNull null
            if (!UpdateVersion.isNewer(tag, current)) return@mapNotNull null
            val candidates = runCatching { obj["assets"]?.jsonArray }.getOrNull().orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { asset ->
                    val name = asset.value("name").orEmpty()
                    name.endsWith(".apk", ignoreCase = true) &&
                        name.none { it == '/' || it == '\\' } &&
                        !Regex("(?i)(?:^|[-_.])(debug|benchmark|androidtest)(?:[-_.]|$)").containsMatchIn(name) &&
                        asset.value("state").let { it == null || it == "uploaded" } &&
                        source.ownsAsset(asset.value("browser_download_url").orEmpty())
                }
                .mapNotNull { asset ->
                    val lower = asset.value("name")!!.lowercase()
                    val abi = knownAbis.firstOrNull { candidate ->
                        Regex("(?:^|[-_.])${Regex.escape(candidate)}(?:[-_.]|$)").containsMatchIn(lower)
                    }
                    val universal = Regex("(?:^|[-_.])universal(?:[-_.]|$)").containsMatchIn(lower)
                    val generic = Regex("(?:app(?:-release)?|androidharness(?:-cn)?(?:-v?\\d+(?:\\.\\d+)*)?(?:-release)?)\\.apk").matches(lower)
                    val rank = if (abi == null) {
                        if (universal || generic) supportedAbis.size else -1
                    } else supportedAbis.indexOf(abi)
                    if (rank < 0) null else rank to asset
                }
            val apk = candidates.minByOrNull { it.first }?.second ?: return@mapNotNull null
            val bytes = apk.value("size")?.toLongOrNull() ?: return@mapNotNull null
            if (bytes <= 0 || bytes > VerifiedDownload.MAX_BYTES) return@mapNotNull null
            val url = obj.value("html_url").orEmpty().takeIf {
                it.startsWith("${source.webUrl}/releases/tag/", ignoreCase = true)
            } ?: "${source.webUrl}/releases"
            UpdateRelease(tag, obj.value("name").orEmpty().ifBlank { tag }, obj.value("body").orEmpty(), url,
                apk.value("browser_download_url")!!, apk.value("name")!!, bytes, apk.value("digest"))
        }.maxWithOrNull { a, b -> UpdateVersion.compare(a.tag, b.tag) ?: 0 }
    }

    private fun JsonObject.value(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it.content == "null" }?.content
}
