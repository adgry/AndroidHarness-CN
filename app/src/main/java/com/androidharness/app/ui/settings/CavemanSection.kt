package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.caveman.CavemanIntensity
import com.androidharness.app.caveman.CavemanPolicy
import com.androidharness.app.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CavemanSection(container: AppContainer, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var showReports by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "无法保存 Caveman 设置。" }
            finally { busy = false }
        }
    }
    CavemanPanel("Caveman") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("内置技能包", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(if (settings.cavemanInstalled) "已安装" else "未安装",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text("精简回复和五项专用编程技能。使用现有模型，无需额外 API 密钥、终端或代理。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (settings.cavemanInstalled) {
            OutlinedButton(onClick = { confirmRemove = true }, enabled = !busy) { Text("卸载") }
        } else {
            Button(onClick = { save { container.settings.setCavemanInstalled(true) } }, enabled = !busy) { Text("安装 Caveman") }
            Text("从应用内安装。回复模式默认关闭，编程技能可单独关闭。", style = MaterialTheme.typography.labelSmall)
        }
        if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (settings.cavemanInstalled) {
        CavemanPanel("回复精简程度") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CavemanIntensity.entries.forEachIndexed { index, intensity ->
                    SegmentedButton(
                        selected = settings.cavemanIntensity == intensity,
                        onClick = { save { container.settings.setCavemanIntensity(intensity) } },
                        enabled = !busy,
                        shape = SegmentedButtonDefaults.itemShape(index, CavemanIntensity.entries.size),
                    ) { Text(intensity.displayLabel, maxLines = 1) }
                }
            }
            Text(settings.cavemanIntensity.description, style = MaterialTheme.typography.bodyMedium)
            Text("从下一次模型请求开始，在所有聊天中自动生效。关闭会移除风格指令，各项技能独立设置。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("文言文", style = MaterialTheme.typography.bodyMedium)
                    Text("所选精简程度的文言文版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.cavemanWenyan,
                    onCheckedChange = { save { container.settings.setCavemanWenyan(it) } },
                    enabled = !busy && settings.cavemanIntensity != CavemanIntensity.OFF,
                    modifier = Modifier.semantics { contentDescription = "文言文" })
            }
            Text("保留代码、路径和错误原文。警告和不明确的指令使用完整句子。", style = MaterialTheme.typography.labelSmall)
        }
        CavemanPanel("编程技能") {
            Text("仅在相关时加载。安装后可在技能选择器中使用。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val labels = listOf("提交信息", "代码评审", "先调查", "针对性修补", "验证后停止")
            CavemanPolicy.skills.keys.forEachIndexed { index, name ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(labels[index], style = MaterialTheme.typography.bodyMedium)
                        Text("/$name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = name !in settings.disabledSkills,
                        onCheckedChange = { enabled -> save { container.settings.setSkillEnabled(name, enabled) } },
                        enabled = !busy, modifier = Modifier.semantics { contentDescription = labels[index] })
                }
            }
        }
        CavemanPanel("词元报告") {
            Text("仅统计 Caveman 节省的词元，不含总用量或无关的上下文缩减。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { showReports = !showReports }) { Text(if (showReports) "收起报告" else "显示报告") }
            if (showReports) {
                Text("节省词元：尚未统计", style = MaterialTheme.typography.titleSmall)
                Text("回复模式用于引导模型，不会生成普通回复用于比较。需在关闭 Caveman 的同等运行中测量节省量。此报告不会额外请求模型。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("回复更短不保证总费用更低，风格指令也会消耗输入词元。", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { uri.openUri("https://github.com/JuliusBrussee/caveman") }) { Text("源码") }
        TextButton(onClick = { showLicense = true }) { Text("MIT 许可证") }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false }, title = { Text("卸载 Caveman？") },
        text = { Text("移除可选技能包并关闭回复模式。保留聊天和你自己的技能副本。已加载到聊天的指令仍保留在历史记录中。") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; save { container.settings.setCavemanInstalled(false) } }) { Text("卸载") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("取消") } },
    )
    if (showLicense) AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("Caveman 技能") },
        text = { Text("改编自 Julius Brussee 以 MIT 许可证发布的 Caveman 技能。Copyright (c) 2026 Julius Brussee。本集成不包含 BSL 压缩引擎或代理。") },
        confirmButton = { TextButton(onClick = { uri.openUri("https://github.com/JuliusBrussee/caveman/blob/main/LICENSE") }) { Text("完整许可证") } },
        dismissButton = { TextButton(onClick = { showLicense = false }) { Text("关闭") } })
}

@Composable
private fun CavemanPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

