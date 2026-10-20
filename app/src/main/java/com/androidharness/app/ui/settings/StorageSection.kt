package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.data.storage.StorageCandidate
import com.androidharness.app.data.storage.StorageInventory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

internal fun storageSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

@Composable
internal fun StorageSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf<StorageInventory?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmation by remember { mutableStateOf<List<StorageCandidate>?>(null) }
    var toolDetail by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<Pair<String, String>?>(null) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var report by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun scan() {
        scanning = true; error = null
        try {
            inventory = container.storage.scan()
            selected = selected.intersect(inventory!!.candidates.map { it.id }.toSet())
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法统计存储用量，请重试。" }
        finally { scanning = false }
    }
    LaunchedEffect(Unit) { scan() }
    val busy = scanning || cleaning
    val heading = remember { BringIntoViewRequester() }
    LaunchedEffect(tab, reviewing) { withFrameNanos { }; heading.bringIntoView() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().bringIntoViewRequester(heading), verticalAlignment = Alignment.CenterVertically) {
            Text(if (reviewing) "检查清理项" else "此设备上的存储", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { scan() } }, enabled = !busy && confirmation == null) {
                Icon(Icons.Outlined.Refresh, "刷新存储用量")
            }
        }
        if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        report?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        val data = inventory
        if (data == null) {
            Text(if (scanning) "正在统计文件…" else "存储不可用。", style = MaterialTheme.typography.bodyMedium)
            if (!scanning) OutlinedButton(onClick = { scope.launch { scan() } }) { Text("重试") }
        } else if (reviewing) {
            Text("仅选择可以重新生成的缓存和构建目录。删除前会再次检查每个路径。", style = MaterialTheme.typography.bodySmall)
            if (data.candidates.isEmpty()) Text("目前没有可安全清理的内容。", style = MaterialTheme.typography.bodyMedium)
            data.candidates.groupBy { it.group }.forEach { (group, items) ->
                Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SettingsPanel(Modifier.fillMaxWidth()) {
                    Column {
                        items.forEachIndexed { index, candidate ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Checkbox) {
                                selected = if (candidate.id in selected) selected - candidate.id else selected + candidate.id
                            }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(candidate.id in selected, onCheckedChange = null)
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(candidate.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(candidate.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(candidatePath(candidate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(storageSize(candidate.bytes), style = MaterialTheme.typography.labelMedium)
                            }
                            if (index < items.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            StorageRow(Icons.Outlined.Inventory2, "未使用的托管软件包", "归属不明或工作区需求不明确的内容会受保护", "详情") {
                detail = "托管软件包" to "新安装会记录文件、依赖和使用需求。在“共享”中，仅当工作区不再需要工具时才释放需求。只有不再有已记录需求和已安装的依赖方，且所属文件未改变的软件包才可清理。缺少元数据的旧安装会继续受保护。"
            }
            StorageRow(Icons.Outlined.Shield, "始终保留", "源码目录、必需依赖、模型、历史记录、撤销检查点和运行时部署", "") {
                detail = "始终保留" to "清理仅涉及你选择的确切路径。必需或无法验证的软件包、源码目录、模型、恢复数据和运行时暂存内容会受保护。"
            }
            val chosen = data.candidates.filter { it.id in selected }
            Button(onClick = { confirmation = chosen }, enabled = chosen.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                Text("检查 ${storageSize(chosen.sumOf { it.bytes })}")
            }
            TextButton(onClick = { reviewing = false }, enabled = !busy) { Text("返回存储") }
        } else {
            val tabs: @Composable () -> Unit = {
                listOf("概览", "共享", "工作区").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1, softWrap = false) })
                }
            }
            if (LocalDensity.current.fontScale > 1.2f) {
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, tabs = tabs)
            } else PrimaryTabRow(selectedTabIndex = tab, tabs = tabs)

            when (tab) {
                0 -> {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(storageSize(data.bytes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Text("已统计文件 · 共享工具仅计一次", style = MaterialTheme.typography.bodySmall)
                            Text(if (data.candidates.isEmpty()) "目前没有可安全清理的内容" else
                                "可检查 ${storageSize(data.candidates.sumOf { it.bytes })}", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    SettingsPanel(Modifier.fillMaxWidth()) {
                        Column {
                            data.usage.forEachIndexed { index, usage ->
                                StorageRow(when(index) { 0 -> Icons.Outlined.Terminal; 1 -> Icons.Outlined.Download; 2 -> Icons.Outlined.Folder;
                                    3 -> Icons.Outlined.Memory; 4 -> Icons.Outlined.History; else -> Icons.Outlined.Storage },
                                    usage.title, usage.note, storageSize(usage.bytes)) {
                                    when (index) {
                                        0 -> tab = 1
                                        2 -> tab = 2
                                        else -> detail = usage.title to when (index) {
                                            1 -> "npm 和 pip 在每个执行层级为本地工作区使用共享下载缓存。项目安装和配置保留在各自目录中。工具链主目录下的 Gradle 缓存会统计并保留。项目本地覆盖设置仍生效。旧缓存会统计并保留。"
                                            3 -> "已安装的模型文件保留在此设备上。请在“设置 → 本地模型”中管理。"
                                            4 -> "聊天数据库包含消息、撤销检查点、文件恢复快照、图片和已保存的任务状态。清理会保留这些文件。"
                                            else -> "包含设置、其他应用缓存和特权执行所需的工具链暂存归档。本次清理会保留这些内容。"
                                        }
                                    }
                                }
                                if (index < data.usage.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                    Button(onClick = { reviewing = true; selected = data.candidates.filter { it.id.startsWith("download:") }.map { it.id }.toSet() },
                        enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("检查清理项") }
                }
                1 -> {
                    Text("一个本地工具链供所有工作区使用。shell/root 使用各自必需的运行时和缓存。", style = MaterialTheme.typography.bodySmall)
                    StorageRow(Icons.Outlined.Download, "共享下载缓存", "npm · pip · 现有 Gradle 主目录", "详情") {
                        detail = "共享缓存" to "下载内容可在本地工作区间复用，无需合并项目配置、node_modules 或 Python 环境。Android 将应用与特权执行的权限分离，因此两者使用独立缓存。此处不统计特权缓存。"
                    }
                    if (data.packages.isEmpty()) Text("未安装 Linux 软件包。", style = MaterialTheme.typography.bodyMedium)
                    SettingsPanel(Modifier.fillMaxWidth()) {
                        Column {
                            data.packages.forEachIndexed { index, name ->
                                val tool = data.managedTools[name]
                                StorageRow(Icons.Outlined.Inventory2, name,
                                    data.protections[name] ?: "无已记录需求 · 检查清理项",
                                    if (tool?.complete == true) storageSize(tool.files.sumOf { it.size }) else "未知") {
                                    toolDetail = name
                                }
                                if (index < data.packages.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
                2 -> {
                    Text("工作区总量不包含共享工具。别名和嵌套目录仅统计一次。", style = MaterialTheme.typography.bodySmall)
                    if (data.workspaces.isEmpty()) Text("没有已保存的工作区。")
                    data.workspaces.forEach { workspace ->
                        SettingsPanel(Modifier.fillMaxWidth()) {
                            StorageRow(Icons.Outlined.Folder, workspace.name, workspace.note,
                                workspace.bytes?.let(::storageSize) ?: "未统计") {
                                detail = workspace.name to (workspace.path.takeIf { it.isNotEmpty() }?.let {
                                    "$it\n\n${workspace.note}。共享工具和下载缓存会单独统计。可在“检查清理项”中选择符合条件的旧 Gradle 构建产物。"
                                } ?: workspace.note)
                            }
                        }
                    }
                }
            }
            Text("显示逻辑文件大小，不代表 Android 的磁盘分配量。${if (data.incomplete) "部分文件无法统计。" else ""}不包含仅可通过文件选择器访问的文件夹、SSH 主机和特权运行时，也不跟随链接。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    toolDetail?.let { name ->
        val tool = inventory?.managedTools?.get(name)
        val protection = inventory?.protections?.get(name)
        AlertDialog(onDismissRequest = { toolDetail = null }, title = { Text(name) },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(protection ?: "无已记录需求或已安装的依赖方。符合条件的文件显示在“检查清理项”中。")
                    Text("已记录的需求与项目配置相互独立。释放需求后，共享安装会保留，直到你主动清理。", style = MaterialTheme.typography.bodySmall)
                    if (tool == null) Text("此旧安装没有文件清单。归属、大小和工作区使用情况未知，将继续受保护，无法移除。", style = MaterialTheme.typography.bodySmall)
                    else {
                        if (!tool.complete) Text("部分所属文件无法验证，此软件包会继续受保护。", style = MaterialTheme.typography.bodySmall)
                        if (tool.depends.isNotEmpty()) Text("依赖：${tool.depends.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        tool.requirements.sorted().forEach { owner ->
                            Text(owner, style = MaterialTheme.typography.bodySmall)
                            if (!isRequiredToolchainProtection(protection)) OutlinedButton(onClick = { release = name to owner }, enabled = !busy) { Text("释放需求") }
                        }
                        Text("记录的使用情况可能不包含 Harness 外部启动的命令。仍使用此工具的工作区应保留需求。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }, confirmButton = { TextButton(onClick = { toolDetail = null }) { Text("完成") } })
    }
    release?.let { (name, owner) ->
        AlertDialog(onDismissRequest = { release = null }, title = { Text("释放 $name 的需求？") },
            text = { Text("$owner\n\n仅当不再需要 $name 时才释放此记录的需求。其他需求和已安装的工具会保留，项目配置不会更改。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    try { container.linuxEnv.releasePackageRequirement(name, owner); release = null; scan() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message; release = null }
                }
            }) { Text("释放需求") } },
            dismissButton = { TextButton(onClick = { release = null }) { Text("取消") } })
    }
    detail?.let { (title, body) ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(title) }, text = { Text(body) },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("完成") } })
    }
    confirmation?.let { chosen ->
        AlertDialog(onDismissRequest = { if (!cleaning) confirmation = null },
            title = { Text("释放 ${storageSize(chosen.sumOf { it.bytes })}？") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("仅删除这些已检查的文件。缓存将重新下载，构建可能需要重新运行。")
                    chosen.forEach { Text("${it.title} · ${storageSize(it.bytes)}\n${candidatePath(it)}", style = MaterialTheme.typography.bodySmall) }
                    Text("跳过已更改的文件和链接，保留源码目录、模型和恢复数据。")
                }
            },
            confirmButton = {
                TextButton(enabled = !cleaning, onClick = {
                    cleaning = true; error = null
                    scope.launch {
                        try {
                            val result = container.storage.clean(chosen)
                            report = "已从 ${result.removedFiles} 个文件释放 ${storageSize(result.removedBytes)}" +
                                if (result.skipped > 0) " · 已跳过 ${result.skipped} 个已更改或不可用的项目" else ""
                            confirmation = null; selected = emptySet(); reviewing = false
                            scan()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "清理失败，请重新扫描。"; confirmation = null }
                        finally { cleaning = false }
                    }
                }) { Text(if (cleaning) "正在清理…" else "释放 ${storageSize(chosen.sumOf { it.bytes })}") }
            }, dismissButton = { TextButton(enabled = !cleaning, onClick = { confirmation = null }) { Text("取消") } })
    }
}

private fun candidatePath(candidate: StorageCandidate): String = candidate.target.absolutePath

@Composable
private fun StorageRow(icon: ImageVector, title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 64.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.labelMedium, modifier = Modifier.widthIn(max = 90.dp))
    }
}
