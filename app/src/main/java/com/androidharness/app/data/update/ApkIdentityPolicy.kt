package com.androidharness.app.data.update

/** Certificate values are SHA-256 fingerprints of the current APK signers. */
data class ApkIdentity(val packageName: String, val versionCode: Long, val signers: Set<String>)

object ApkIdentityPolicy {
    fun error(installed: ApkIdentity, candidate: ApkIdentity): String? = when {
        candidate.packageName != installed.packageName -> "安装包不属于当前应用，已阻止安装。"
        candidate.versionCode <= installed.versionCode -> "安装包版本号没有增加，已阻止降级或重复安装。"
        installed.signers.isEmpty() || candidate.signers.isEmpty() || installed.signers != candidate.signers ->
            "安装包签名与当前应用不一致，请使用同一签名密钥构建更新。"
        else -> null
    }
}
