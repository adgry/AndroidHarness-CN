package com.androidharness.app.ui.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun `Chinese queries find focused controls and preserve technical searches and links`() {
        val context = matchingSettingsEntries("最大 上下文").single { !it.primary }
        assertEquals(SettingsPage.AGENT, context.page)
        assertEquals("最大上下文窗口", context.anchor)
        val planning = matchingSettingsEntries("规划 模型").first { it.anchor != null }
        assertEquals(SettingsPage.MODELS, planning.page)
        assertEquals("双模型规划", planning.anchor)
        assertEquals("双模型规划", settingsDeepLinkAnchor("planning"))
        assertEquals(listOf(SettingsPage.STORAGE), matchingSettingsPages("清理 磁盘"))
        assertTrue(SettingsPage.MODELS in matchingSettingsPages("api key"))
        assertEquals(SettingsPage.MODELS, settingsDeepLink("planning"))
    }

    @Test
    fun `storage is searchable and supports a direct link`() {
        assertEquals(listOf(SettingsPage.STORAGE), matchingSettingsPages("cleanup disk"))
        assertEquals(SettingsPage.STORAGE, settingsDeepLink("storage"))
    }

    @Test
    fun `search finds controls by familiar names and hidden keywords`() {
        assertEquals(listOf(SettingsPage.APPEARANCE), matchingSettingsPages("  DARK  theme "))
        assertEquals(listOf(SettingsPage.ENVIRONMENT), matchingSettingsPages("battery"))
        assertTrue(SettingsPage.MODELS in matchingSettingsPages("api\tkey"))
        assertEquals(listOf(SettingsPage.CHAT), matchingSettingsPages("repo map"))
        assertEquals(listOf(SettingsPage.PRIVACY), matchingSettingsPages("fingerprint"))
        assertEquals(listOf(SettingsPage.CODE_INTELLIGENCE), matchingSettingsPages("codegraph affected"))
    }

    @Test
    fun `search returns nested settings as primary style results`() {
        val context = matchingSettingsEntries("max context").first { it.title == "最大上下文窗口" }
        assertEquals(SettingsPage.AGENT, context.page)
        assertFalse(context.primary)

        val planning = matchingSettingsEntries("plan model").first { it.title == "规划模型" }
        assertEquals(SettingsPage.MODELS, planning.page)

        val lock = matchingSettingsEntries("auto lock timeout").first { it.title == "自动锁定时间" }
        assertEquals(SettingsPage.PRIVACY, lock.page)
        val subagent = matchingSettingsEntries("subagent model").first { it.title == "子代理模型" }
        assertEquals(SettingsPage.SUBAGENT, subagent.page)
        assertEquals("子代理模型", subagent.anchor)
    }

    @Test
    fun `empty search keeps the normal primary settings home`() {
        val entries = matchingSettingsEntries("")
        assertEquals(SettingsPage.entries.size, entries.size)
        assertTrue(entries.all { it.primary })
        assertEquals(SettingsPage.entries.toList(), matchingSettingsPages(""))
    }

    @Test
    fun `automation search finds integrated GitHub presets and build shortcut stays removed`() {
        assertEquals(listOf(SettingsPage.GITHUB), matchingSettingsPages("automation"))
        assertTrue(matchingSettingsPages("build test").isEmpty())
    }

    @Test
    fun `GitHub integration is the first settings page and supports a direct link`() {
        assertEquals(SettingsPage.GITHUB, matchingSettingsPages("").first())
        assertEquals(SettingsPage.GITHUB, settingsDeepLink("github"))
        assertEquals(listOf(SettingsPage.GITHUB), matchingSettingsPages("commit push"))
    }

    @Test
    fun `existing chat shortcuts land on their focused settings pages`() {
        assertEquals(SettingsPage.MODELS, settingsDeepLink("planning"))
        assertEquals(SettingsPage.VOICE, settingsDeepLink("voice"))
        assertNull(settingsDeepLink("unknown"))
    }
}
