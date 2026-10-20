package com.androidharness.app.ui.settings

// These APIs currently expose display text rather than structured result states.
internal fun environmentCheckPassed(report: String?): Boolean =
    report?.startsWith("全部可用") == true || report?.startsWith("All present") == true

internal fun isRequiredToolchainProtection(protection: String?): Boolean =
    protection == "应用工具链需要此软件包" || protection == "Required by the app toolchain"

internal fun isIdleLocalModelStatus(status: String): Boolean =
    status == "就绪" || status == "已安装" || status == "Ready" || status == "Installed"

internal fun isLocalModelConversionStatus(status: String?): Boolean =
    status != null && (status.contains("转换") || status.contains("量化") ||
        status.contains("convert", ignoreCase = true) || status.contains("quantiz", ignoreCase = true))
