package com.androidharness.app.ui.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsStatusPolicyTest {
    @Test
    fun `complete environment report does not offer a repair`() {
        assertTrue(environmentCheckPassed("全部可用：bash、git、gh、python、pip、node、npm。"))
        assertTrue(environmentCheckPassed("All present: bash, git, gh, python, pip, node, npm."))
        assertFalse(environmentCheckPassed("缺少软件包：git"))
        assertFalse(environmentCheckPassed("Broken: bash"))
        assertFalse(environmentCheckPassed(null))
    }

    @Test
    fun `required toolchain keeps its requirement release control hidden`() {
        assertTrue(isRequiredToolchainProtection("应用工具链需要此软件包"))
        assertTrue(isRequiredToolchainProtection("Required by the app toolchain"))
        assertFalse(isRequiredToolchainProtection("为 workspace:example 保留"))
        assertFalse(isRequiredToolchainProtection(null))
    }

    @Test
    fun `installed model hides idle statuses while retaining informative messages`() {
        listOf("就绪", "已安装", "Ready", "Installed").forEach { status ->
            assertTrue(status, isIdleLocalModelStatus(status))
        }
        listOf("正在加载", "下载失败", "已安装 · 已转换为 Q4 GGUF", "视觉投影器已安装").forEach { status ->
            assertFalse(status, isIdleLocalModelStatus(status))
        }
    }

    @Test
    fun `conversion and quantization hide completed download byte progress`() {
        listOf("正在将 safetensors 转换为 Q4 GGUF", "正在量化模型", "Converting safetensors to Q4 GGUF", "Quantizing model").forEach { status ->
            assertTrue(status, isLocalModelConversionStatus(status))
        }
        assertFalse(isLocalModelConversionStatus("正在下载 50%"))
        assertFalse(isLocalModelConversionStatus("正在验证"))
        assertFalse(isLocalModelConversionStatus(null))
    }
}
