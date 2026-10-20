package com.androidharness.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.PermissionMode
import com.androidharness.app.data.AppSettings
import com.androidharness.app.ui.chat.components.FullAccessOrange
import com.androidharness.app.ui.common.formatTokenCount

private enum class BehaviorPicker { PERMISSIONS, CONTEXT, ITERATIONS }

private data class BehaviorChoice(val key: String, val title: String, val description: String)

private fun PermissionMode.description(): String = when (this) {
    PermissionMode.CONFIRM_ALL -> "每次工具操作前都询问，包括读取操作。"
    PermissionMode.CONFIRM_RISKY -> "自由读取，编辑和运行命令前询问。"
    PermissionMode.FULL_AUTO -> "自动运行，保留工作区和命令保护。"
    PermissionMode.FULL_ACCESS -> "无需审批、工作区限制或命令保护即可运行。"
}

/** Presentation only: all changes still go through the existing settings repository. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentBehaviorSection(
    settings: AppSettings,
    onPermissionMode: (PermissionMode) -> Unit,
    onContextLimit: (Int) -> Unit,
    onIterationLimit: (Int) -> Unit,
    onProjectInstructions: () -> Unit,
) {
    var pickerName by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = pickerName?.let(BehaviorPicker::valueOf)

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "选择代理的工作方式，也可以在聊天中调整权限。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BehaviorGroup("权限", Icons.Outlined.Security) {
            SettingsAnchor("默认权限模式") {
                BehaviorValueRow(
                    title = "默认权限模式",
                    value = settings.permissionMode.label,
                    description = settings.permissionMode.description(),
                    onClick = { pickerName = BehaviorPicker.PERMISSIONS.name },
                    caution = settings.permissionMode == PermissionMode.FULL_ACCESS,
                )
            }
        }
        BehaviorGroup("运行限制", Icons.Outlined.Tune) {
            SettingsAnchor("最大上下文窗口") {
                BehaviorValueRow(
                    title = "最大上下文窗口",
                    value = "${formatTokenCount(settings.maxContextTokens.toLong())} 词元",
                    description = "限制发送给模型的上下文大小。",
                    onClick = { pickerName = BehaviorPicker.CONTEXT.name },
                )
            }
            BehaviorDivider()
            SettingsAnchor("工具调用轮数限制") {
                BehaviorValueRow(
                    title = "工具调用轮数限制",
                    value = if (settings.maxIterations <= 0) "不限" else "${settings.maxIterations} 轮",
                    description = "单次运行允许的工具调用轮数。",
                    onClick = { pickerName = BehaviorPicker.ITERATIONS.name },
                )
            }
        }
        BehaviorGroup("项目指令", Icons.Outlined.Description) {
            SettingsAnchor("项目指令（AGENTS.md）") {
                BehaviorValueRow(
                    title = "项目指令（AGENTS.md）",
                    description = "查看或编辑当前工作区的规则。",
                    onClick = onProjectInstructions,
                )
            }
        }
    }

    if (picker != null) {
        val title: String
        val description: String
        val selected: String
        val choices: List<BehaviorChoice>
        when (picker) {
            BehaviorPicker.PERMISSIONS -> {
                title = "默认权限模式"
                description = "选择代理何时请求审批。安装软件包总会询问；记住的授权会跳过重复提示。"
                selected = settings.permissionMode.name
                choices = PermissionMode.entries.map {
                    BehaviorChoice(it.name, it.label, it.description())
                }
            }
            BehaviorPicker.CONTEXT -> {
                title = "最大上下文窗口"
                description = "更大的上下文可以保留更多历史，但会消耗更多词元，仍受模型自身限制。"
                selected = settings.maxContextTokens.toString()
                choices = (listOf(131_072, 262_144, 400_000, 1_000_000, 2_000_000) + settings.maxContextTokens)
                    .distinct().sorted().map {
                        BehaviorChoice(it.toString(), "${formatTokenCount(it.toLong())} 词元", "")
                    }
            }
            BehaviorPicker.ITERATIONS -> {
                title = "工具调用轮数限制"
                description = "一轮可以包含多个工具调用。不限轮数时，代理会持续工作直到任务完成或你手动停止。"
                selected = settings.maxIterations.coerceAtLeast(0).toString()
                choices = (listOf(0, 25, 100, 250) + settings.maxIterations.coerceAtLeast(0))
                    .distinct().sorted().map {
                        BehaviorChoice(it.toString(), if (it == 0) "不限" else "$it 轮", "")
                    }
            }
        }
        ModalBottomSheet(
            onDismissRequest = { pickerName = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    choices.forEach { choice ->
                        val isSelected = choice.key == selected
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        when (picker) {
                                            BehaviorPicker.PERMISSIONS -> onPermissionMode(PermissionMode.valueOf(choice.key))
                                            BehaviorPicker.CONTEXT -> onContextLimit(choice.key.toInt())
                                            BehaviorPicker.ITERATIONS -> onIterationLimit(choice.key.toInt())
                                        }
                                        pickerName = null
                                    },
                                ).padding(horizontal = 12.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(choice.title, style = MaterialTheme.typography.titleSmall)
                                    if (choice.description.isNotEmpty()) Text(
                                        choice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (picker == BehaviorPicker.PERMISSIONS && choice.key == PermissionMode.FULL_ACCESS.name)
                                            FullAccessOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { pickerName = null }, modifier = Modifier.align(Alignment.End)) { Text("取消") }
            }
        }
    }
}

@Composable
private fun BehaviorGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.padding(start = 4.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsPanel(Modifier.fillMaxWidth(), content)
    }
}

@Composable
private fun BehaviorValueRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    value: String? = null,
    caution: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (value != null) Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (caution) FullAccessOrange else MaterialTheme.colorScheme.primary,
            )
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = if (caution) FullAccessOrange else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BehaviorDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}
