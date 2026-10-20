package com.androidharness.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.env.*
import kotlinx.coroutines.launch

@Composable
internal fun RootAccessSection(container: AppContainer) {
    val access = container.deviceAccess
    val prefs by access.preferences.collectAsStateWithLifecycle()
    val root by access.root.state.collectAsStateWithLifecycle()
    val paused by access.paused.collectAsStateWithLifecycle()
    val allowance by access.allowanceLabel.collectAsStateWithLifecycle()
    val last by access.lastExecution.collectAsStateWithLifecycle()
    val shizukuUid by access.shizukuUid.collectAsStateWithLifecycle()
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val project by container.workspace.currentProject.collectAsStateWithLifecycle(initialValue = null)
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = com.androidharness.app.data.AppSettings())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var projectPicker by remember { mutableStateOf(false) }
    var browser by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var useMenu by remember { mutableStateOf(false) }
    var confirmUnlimited by remember { mutableStateOf(false) }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Root 访问", style = MaterialTheme.typography.titleMedium)
                    Text(root.status.displayLabel, style = MaterialTheme.typography.bodySmall,
                        color = if (root.status == RootStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy || root.status == RootStatus.REQUESTING) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
            Text(root.displayDetail, style = MaterialTheme.typography.bodySmall,
                color = if (root.status == RootStatus.DENIED || (root.status == RootStatus.UNAVAILABLE && root.checkedAt > 0))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            root.provider?.let { provider ->
                Text("Root 管理器：${provider.label}", style = MaterialTheme.typography.labelMedium,
                    color = if (root.status == RootStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shizukuUid == 0) Text("Shizuku · Root 可用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val rootEnabled = (root.status == RootStatus.READY || shizukuUid == 0) &&
                access.rootUseActive(settings.lastActiveSessionId, prefs)
            AccessActionPair(first = { buttonModifier ->
                if (rootEnabled) Surface(
                    modifier = buttonModifier.heightIn(min = 48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                        Text("已启用 Root", style = MaterialTheme.typography.titleSmall)
                    }
                } else Button(onClick = { scope.launch {
                    busy = true
                    try { access.grantRoot(project?.id, settings.lastActiveSessionId) } finally { busy = false }
                } }, enabled = !busy && root.status != RootStatus.REQUESTING, modifier = buttonModifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(if (root.status == RootStatus.READY) "启用 Root 使用" else "授予 Root 权限")
                }
            }, second = { buttonModifier ->
                OutlinedButton(onClick = { scope.launch {
                    busy = true
                    try { report = access.checkConnections() } finally { busy = false }
                } }, enabled = !busy, modifier = buttonModifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) { Text("检查访问权限") }
            })
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("自动回退", style = MaterialTheme.typography.titleSmall)
                    Text(if (prefs.mode == AccessMode.AUTO) "始终开启 · Root → Shizuku → 普通" else "Root → Shizuku → 普通", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = prefs.mode == AccessMode.AUTO || prefs.autoFallback, enabled = prefs.mode != AccessMode.AUTO, onCheckedChange = { scope.launch { access.store.setFallback(it) } })
            }
            Text("访问模式", style = MaterialTheme.typography.titleSmall)
            AccessModeChoices(prefs.mode) { scope.launch { access.store.setMode(it) } }
            Text(when {
                prefs.mode == AccessMode.AUTO -> "自动选择可用访问方式：依次尝试 Root、Shizuku、普通模式。除非关闭临时 Root 模式，否则 Root 仍需要此项目的权限。"
                prefs.mode == AccessMode.BOTH -> "同时保持连接：Shizuku 运行普通命令，直接 Root 处理 Root 请求。除非关闭临时 Root 模式，否则 Root 仍需授权。"
                !prefs.autoFallback && prefs.mode != AccessMode.NORMAL -> "所选访问方式不可用时，命令会失败。"
                else -> "在命令开始前进行回退。中断的命令不会自动重试。"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("临时 Root 模式", style = MaterialTheme.typography.titleSmall)
                    Text(if (prefs.temporaryRoot) "Root 仅限一次应用会话、一次聊天或一次操作，并限于已授权的项目。"
                        else "连接活动期间，所有项目和聊天均可使用 Root。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (prefs.temporaryRoot) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
                Switch(checked = prefs.temporaryRoot, onCheckedChange = { enabled ->
                    if (enabled) scope.launch { access.store.setTemporaryRoot(true) } else confirmUnlimited = true
                })
            }
            if (prefs.temporaryRoot) {
                Text("临时 Root 使用", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { useMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(prefs.rootUse.displayLabel, Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null)
                    }
                    DropdownMenu(expanded = useMenu, onDismissRequest = { useMenu = false }) {
                        RootUse.entries.forEach { use -> DropdownMenuItem(text = { Text(use.displayLabel) },
                            enabled = use != RootUse.CHAT || settings.lastActiveSessionId != null,
                            onClick = { useMenu = false; access.disableRootUse(); scope.launch { access.store.setRootUse(use) } }) }
                    }
                }
                Text(allowance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Text("每次启动应用后需授权一次 Root 以重新连接。重新开启此项会恢复单次使用限制。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { projectPicker = true }, enabled = prefs.temporaryRoot, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("项目 Root 权限", Modifier.weight(1f))
                Text("${projects.count { it.id in prefs.rootProjects }}", style = MaterialTheme.typography.labelMedium)
            }
            if (!prefs.temporaryRoot) Text("关闭临时 Root 模式时，不使用项目权限限制。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AccessActionPair(first = { buttonModifier ->
                FilledTonalButton(onClick = { browser = true }, enabled = root.status == RootStatus.READY || shizukuUid == 0,
                    modifier = buttonModifier) { Text("Root 文件浏览器") }
            }, second = { buttonModifier ->
                OutlinedButton(onClick = { report = access.report() }, modifier = buttonModifier) { Text("诊断") }
            })
            Text("上次使用：$last", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (paused) {
                FilledTonalButton(onClick = { scope.launch { report = access.resume() } }, modifier = Modifier.fillMaxWidth()) { Text("恢复特权访问") }
            } else {
                OutlinedButton(onClick = { scope.launch {
                    busy = true
                    try { report = access.stopPrivileged() } finally { busy = false }
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.StopCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("停止特权任务")
                }
            }
        }
    }
    if (projectPicker) AlertDialog(onDismissRequest = { projectPicker = false }, title = { Text("项目 Root 权限") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("允许代理在所选项目中请求 Root。完全访问模式不会覆盖此选择。", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(projects, key = { it.id }) { item ->
                    val checked = item.id in prefs.rootProjects
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { access.store.allowProject(item.id, !checked) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, onCheckedChange = { value -> scope.launch { access.store.allowProject(item.id, value) } })
                        Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { projectPicker = false }) { Text("完成") } })
    if (confirmUnlimited) AlertDialog(
        onDismissRequest = { confirmUnlimited = false },
        icon = { Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("在所有位置使用 Root？") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("临时 Root 模式将 Root 限制为一次应用会话、一次聊天或一次操作，并限于已授权项目。")
            Text("关闭后，只要 Root 连接保持活动，代理就能在所有项目和聊天中使用 Root。项目权限和单次使用限制将不再生效。")
            Text("仅在完全由你控制的设备上关闭。每次启动应用后仍需明确授权一次 Root。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = {
            confirmUnlimited = false
            scope.launch { access.store.setTemporaryRoot(false) }
        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("在所有位置使用 Root") } },
        dismissButton = { TextButton(onClick = { confirmUnlimited = false }) { Text("保留临时模式") } },
    )
    report?.let { text -> AlertDialog(onDismissRequest = { report = null }, title = { Text("设备访问诊断") },
        text = { SelectionContainer { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { report = null }) { Text("完成") } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("复制报告") } }) }
    if (browser) RootBrowserDialog(access, project?.id, settings.lastActiveSessionId) { browser = false }
}

@Composable
internal fun AccessModeChoices(selected: AccessMode, onSelect: (AccessMode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f) 1 else 2
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AccessMode.entries.chunked(columns).forEach { modes ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    modes.forEach { mode ->
                        OutlinedCard(modifier = Modifier.weight(1f).selectable(selected == mode, role = Role.RadioButton, onClick = { onSelect(mode) }),
                            colors = CardDefaults.outlinedCardColors(containerColor = if (selected == mode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == mode, onClick = null, modifier = Modifier.size(20.dp))
                                Text(mode.displayLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessActionPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 280.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.weight(1f)); second(Modifier.weight(1f))
            }
        }
    }
}
