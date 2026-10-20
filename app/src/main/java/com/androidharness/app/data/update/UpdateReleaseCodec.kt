package com.androidharness.app.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Only release metadata is persisted; filesystem paths are always chosen locally. */
object UpdateReleaseCodec {
    fun encode(release: UpdateRelease): String = buildJsonObject {
        put("tag", release.tag); put("name", release.name); put("body", release.body)
        put("htmlUrl", release.htmlUrl); put("apkUrl", release.apkUrl)
        put("apkName", release.apkName); put("apkBytes", release.apkBytes)
        release.digest?.let { put("digest", it) }
    }.toString()

    fun decode(payload: String, source: UpdateRepository, currentVersion: String? = null): UpdateRelease? = runCatching {
        val obj = Json.parseToJsonElement(payload).jsonObject
        fun text(key: String): String = (obj[key] as? JsonPrimitive)?.content ?: error("Missing field")
        val tag = text("tag")
        val apk = text("apkUrl")
        val bytes = text("apkBytes").toLong()
        val name = text("apkName")
        require(source.ownsAsset(apk) && bytes in 1..VerifiedDownload.MAX_BYTES)
        require(name.endsWith(".apk", true) && '/' !in name && '\\' !in name)
        require(UpdateVersion.compare(tag, tag) != null)
        require(currentVersion == null || UpdateVersion.isNewer(tag, currentVersion))
        UpdateRelease(tag, text("name"), text("body"), text("htmlUrl"), apk, name, bytes,
            (obj["digest"] as? JsonPrimitive)?.content)
    }.getOrNull()
}
