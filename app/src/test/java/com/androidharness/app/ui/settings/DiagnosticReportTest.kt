package com.androidharness.app.ui.settings

import org.junit.Assert.*
import org.junit.Test

class DiagnosticReportTest {
    @Test fun permissionAloneCannotReportShizukuReady() {
        assertEquals(DiagnosticState.NOT_CHECKED, shizukuDiagnostic(true, false, false, false))
        assertEquals(DiagnosticState.CHECKING, shizukuDiagnostic(true, false, true, false))
        assertEquals(DiagnosticState.ISSUE, shizukuDiagnostic(true, false, false, true))
        assertEquals(DiagnosticState.READY, shizukuDiagnostic(true, true, false, false))
        assertEquals(DiagnosticState.OFF, shizukuDiagnostic(false, true, false, false))
    }

    @Test fun rawFailureSecretsAreReplacedWithUsefulFixedAdvice() {
        val secret = "private-secret-value"
        assertEquals(CatalogCheck.AUTH, catalogFailure("HTTP 401 api_key=$secret"))
        assertEquals(CatalogCheck.AUTH, catalogFailure("HTTP 403 token=$secret"))
        assertEquals(CatalogCheck.RATE_LIMIT, catalogFailure("HTTP 429 url=https://user:$secret@host"))
        val unknown = catalogFailure("server returned password=$secret")
        assertEquals(CatalogCheck.FAILED, unknown)
        assertFalse(unknown.detail.contains(secret))
        assertTrue(CatalogCheck.REACHABLE.detail.contains("尚未测试模型生成能力和配额"))
    }

    @Test fun reportsIncludeScopeAndOptionalStatusWithoutRawConfiguration() {
        val report = diagnosticReport("2.1", 35, linkedMapOf(DiagnosticArea.PROVIDER to DiagnosticState.REACHABLE,
            DiagnosticArea.STORAGE to DiagnosticState.ISSUE, DiagnosticArea.NOTIFICATIONS to DiagnosticState.OFF), 3, 2)
        assertTrue(report.contains("范围：默认提供商和默认工作区"))
        assertTrue(report.contains("存储访问: 需要处理"))
        assertTrue(report.contains("通知: 关闭"))
        assertTrue(report.contains("已保存提供商：3"))
        assertTrue(report.contains("目录可访问不代表模型可生成内容"))
        assertFalse(report.contains("http"))
    }

    @Test fun troubleshootingIsSearchable() {
        assertTrue(SettingsPage.TROUBLESHOOTING in matchingSettingsPages("troubleshooting"))
        assertTrue(SettingsPage.TROUBLESHOOTING in matchingSettingsPages("diagnostics"))
        assertTrue(SettingsPage.TROUBLESHOOTING in matchingSettingsPages("repair"))
        assertEquals(SettingsPage.TROUBLESHOOTING, settingsDeepLink("troubleshooting"))
    }
}
