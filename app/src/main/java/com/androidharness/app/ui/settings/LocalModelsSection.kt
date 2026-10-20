package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.androidharness.app.AppContainer
import com.androidharness.app.data.AppSettings
import com.androidharness.app.local.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun LocalModelsSection(container: AppContainer) {
    val manager = container.localModels
    val catalog by manager.models.collectAsStateWithLifecycle()
    val installed by manager.installed.collectAsStateWithLifecycle()
    val statuses by manager.status.collectAsStateWithLifecycle()
    val running by manager.running.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val work by remember { WorkManager.getInstance(container.appContext).getWorkInfosByTagFlow("local-model-download") }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var device by remember { mutableStateOf(manager.device()) }
    var contexts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var format by rememberSaveable { mutableStateOf(LocalModelFormat.GGUF) }
    var customDialog by remember { mutableStateOf(false) }
    var deviceDialog by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<LocalModelSpec?>(null) }
    var projectorModel by remember { mutableStateOf<LocalModelSpec?>(null) }
    var download by remember { mutableStateOf<LocalModelSpec?>(null) }
    var removing by remember { mutableStateOf<LocalModelSpec?>(null) }
    var editing by remember { mutableStateOf<LocalModelSpec?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importProgress by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var onlyAbliterated by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable(format, search, onlyAbliterated) { mutableStateOf(false) }
    fun importFiles(uri: android.net.Uri, kind: LocalModelFormat) {
        importJob = scope.launch {
            importProgress = "正在检查模型文件"
            try {
                val model = manager.importFromFiles(uri, kind) { importProgress = it }
                message = "已导入 ${model.title}。点击“用于聊天”以选择。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "无法导入模型。" }
            finally { importProgress = null; importJob = null }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFiles(it, LocalModelFormat.GGUF) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { importFiles(it, LocalModelFormat.SAFETENSORS) }
    }
    DisposableEffect(Unit) { onDispose { importJob?.cancel() } }
    LaunchedEffect(catalog, installed) {
        while (true) {
            contexts = withContext(Dispatchers.IO) { catalog.associate { it.id to manager.limits(it.id).context } }
            device = manager.device()
            delay(3000)
        }
    }
    fun activeWork(model: LocalModelSpec) = work.firstOrNull { !it.state.isFinished && "model:${model.id}" in it.tags }
    fun removeNow(model: LocalModelSpec) {
        scope.launch {
            busy = model.id
            try { manager.remove(model.id) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message }
            finally { busy = null }
        }
    }
    val savedModels = catalog.filter { it.id in installed }.sortedByDescending {
        settings.activeProviderId == LocalModelCatalog.PROVIDER_PREFIX + it.id
    }
    val transfers = catalog.filter { it.id !in installed && activeWork(it) != null }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("此手机上", style = MaterialTheme.typography.titleSmall)
                Text("可用内存 ${size(device.availableRam)} · 可用存储 ${size(device.freeStorage)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { deviceDialog = true }) { Icon(Icons.Default.Info, "设备与本地模型信息") }
        }
    }
    manager.catalogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = settings.localModelAgentContext, role = Role.Switch,
                onValueChange = { enabled -> scope.launch { container.settings.setLocalModelAgentContext(enabled) } }),
                verticalAlignment = Alignment.CenterVertically) {
                Text("系统指令与工具", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.localModelAgentContext, onCheckedChange = null)
            }
            Text("默认关闭。本地模型仅接收聊天消息。开启后可使用 Harness 指令和工具处理项目。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("开启后输入量会明显增加，并消耗更多内存和算力，可能需要几分钟才开始回复。更改从下次回复生效。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    message?.let { text ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { message = null }) { Icon(Icons.Default.Close, "关闭消息") }
        }
    }
    Text("你的模型", style = MaterialTheme.typography.titleMedium)
    if (savedModels.isEmpty()) {
        SettingsPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("尚未添加模型", style = MaterialTheme.typography.titleSmall)
                Text("从下方下载 GGUF，或导入自己的模型即可开始。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    savedModels.forEach { model ->
        val selected = settings.activeProviderId == LocalModelCatalog.PROVIDER_PREFIX + model.id
        LocalModelCard(model, contexts[model.id] ?: model.defaultContext, device, selected = selected, installed = true,
            status = statuses[model.id]?.takeUnless(::isIdleLocalModelStatus),
            working = running == model.id, enabled = busy == null,
            onPrimary = {
                scope.launch {
                    busy = model.id
                    try {
                        manager.confirmResources(model)
                        container.settings.setActiveModel(null)
                        container.settings.setActiveProvider(LocalModelCatalog.PROVIDER_PREFIX + model.id)
                        message = "已选择 ${model.title} 用于聊天。"
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.message }
                    finally { busy = null }
                }
            }, onSettings = { editing = model }, onDetails = { details = model },
            onImageSupport = if (model.custom) ({ projectorModel = model }) else null,
            onRemove = { removing = model }, onStop = { manager.stop(model.id) })
    }
    if (transfers.isNotEmpty()) {
        Text("下载", style = MaterialTheme.typography.titleMedium)
        transfers.forEach { model ->
            LocalModelCard(model, contexts[model.id] ?: model.defaultContext, device,
                transfer = activeWork(model), status = statuses[model.id], enabled = busy == null,
                onPrimary = { removeNow(model) }, onDetails = { details = model })
        }
    }
    importProgress?.let { text ->
        SettingsPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                AppLinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { importProgress = "正在取消导入…"; importJob?.cancel() }) { Text("取消导入") }
            }
        }
    }
    Text("添加模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    TabRow(selectedTabIndex = format.ordinal) {
        LocalModelFormat.entries.forEach { option ->
            Tab(selected = format == option, onClick = { format = option }, text = {
                Text(if (option == LocalModelFormat.GGUF) "GGUF" else "Safetensors")
            })
        }
    }
    Text(if (format == LocalModelFormat.GGUF) "选择下载、粘贴链接，或从文件中打开 GGUF。"
        else "通过链接导入，或选择包含权重、config.json 和 tokenizer.json 的文件夹。转换在手机上完成。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { customDialog = true }, enabled = importJob == null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Link, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("通过链接")
        }
        OutlinedButton(onClick = { if (format == LocalModelFormat.GGUF) filePicker.launch(arrayOf("*/*")) else folderPicker.launch(null) },
            enabled = importJob == null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(if (format == LocalModelFormat.GGUF) "导入文件" else "导入文件夹")
        }
    }
    Text("推荐模型", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(search, { search = it }, placeholder = { Text("搜索模型") }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = {
            if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "清空模型搜索") }
        }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !onlyAbliterated, onClick = { onlyAbliterated = false }, label = { Text("全部") })
        FilterChip(selected = onlyAbliterated, onClick = { onlyAbliterated = true }, label = { Text("减少拒答") })
    }
    val recommendations = catalog.filter { model ->
        model.format == format && !model.localFile && model.id !in installed && activeWork(model) == null &&
            (!onlyAbliterated || model.abliterated) && search.trim().split(Regex("\\s+")).all { term ->
                "${model.title} ${model.description} ${model.repository}".contains(term, ignoreCase = true)
            }
    }
    if (recommendations.isEmpty()) Text("没有匹配的模型。请换个关键词，或导入自己的模型。", style = MaterialTheme.typography.bodySmall)
    (if (showAll) recommendations else recommendations.take(6)).forEach { model ->
        val latest = work.filter { "model:${model.id}" in it.tags }.maxByOrNull { it.generation }
        val failure = latest?.takeIf { it.state == WorkInfo.State.FAILED }?.outputData?.getString("error")
        LocalModelCard(model, contexts[model.id] ?: model.defaultContext, device, status = failure,
            enabled = busy == null, onPrimary = { download = model }, onDetails = { details = model })
    }
    if (!showAll && recommendations.size > 6) TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth()) {
        Text("显示全部 ${recommendations.size} 个模型")
    }
    if (onlyAbliterated) Text("减少拒答的社区变体。模型质量和工具调用能力各不相同。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (format == LocalModelFormat.SAFETENSORS) {
        Text("支持采用 BPE 分词器的稠密 Qwen 2/2.5、Qwen 3 和 Llama 3 模型。导入需要额外存储空间。其他模型请使用 GGUF。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (deviceDialog) LocalDeviceDialog(device) { deviceDialog = false }
    details?.let { model -> LocalModelDetailsDialog(model, contexts[model.id] ?: model.defaultContext) { details = null } }
    projectorModel?.let { model -> ProjectorDialog(model, manager) { projectorModel = null } }
    download?.let { model ->
        val downloadLimits = LocalModelLimits(context = model.defaultContext, input = (model.defaultContext * 3 / 4).coerceAtLeast(1),
            output = (model.defaultContext / 4).coerceAtLeast(1), threads = device.cores.coerceIn(1, 4))
        val warnings = localModelWarnings(model, device, downloadLimits, downloading = true)
        AlertDialog(onDismissRequest = { if (busy == null) download = null }, title = { Text("下载模型") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(model.title, style = MaterialTheme.typography.titleSmall)
                Text("${size(model.downloadBytes)} · ${model.url.substringAfter("https://").substringBefore('/')}\n许可证：${model.license}")
                if (model.format == LocalModelFormat.SAFETENSORS) Text("使用前将在手机上转换模型。")
                warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(enabled = busy == null, onClick = {
                busy = model.id
                scope.launch {
                    try {
                        manager.acknowledgeResources(model, downloadLimits, downloading = true)
                        if (model.custom) manager.downloadCustom(model) else manager.download(model.id)
                        download = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.message; download = null }
                    finally { busy = null }
                }
            }) { Text(if (warnings.isEmpty()) "下载" else "继续") } },
            dismissButton = { TextButton(enabled = busy == null, onClick = { download = null }) { Text("取消") } })
    }
    if (customDialog) CustomModelDialog(manager, format, onDismiss = { customDialog = false },
        onDownload = { customDialog = false; download = it })
    removing?.let { model ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除模型？") },
            text = { Text("从此手机移除 ${model.title} 及其设置？聊天会保留，以后可再次下载模型。") },
            confirmButton = { TextButton(onClick = { removing = null; removeNow(model) }) { Text("移除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("保留") } })
    }
    editing?.let { model -> LocalLimitsDialog(model, manager, onDismiss = { editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalModelCard(model: LocalModelSpec, context: Int, device: LocalDeviceProfile,
    selected: Boolean = false, installed: Boolean = false, working: Boolean = false,
    transfer: WorkInfo? = null, status: String? = null, enabled: Boolean,
    onPrimary: () -> Unit, onDetails: () -> Unit, onSettings: (() -> Unit)? = null,
    onImageSupport: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, onStop: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.title, style = MaterialTheme.typography.titleSmall)
                    Text("${size(if (installed) model.bytes else model.downloadBytes)} · ${if (model.format == LocalModelFormat.GGUF) "GGUF" else "Safetensors 导入"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "${model.title} 的选项")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("模型详情") }, onClick = { menu = false; onDetails() })
                        onImageSupport?.let { action -> DropdownMenuItem(text = { Text("图像支持") },
                            enabled = enabled, onClick = { menu = false; action() }) }
                        onRemove?.let { action -> DropdownMenuItem(text = { Text("移除模型", color = MaterialTheme.colorScheme.error) },
                            enabled = enabled, onClick = { menu = false; action() }) }
                    }
                }
            }
            if (!installed && transfer == null && !model.localFile) {
                Text(model.description, style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected) LocalModelBadge("已选择", positive = true)
                if (model.abliterated) LocalModelBadge("减少拒答")
                if (installed && model.projector != null) LocalModelBadge("已添加视觉文件", positive = true)
                Text("预估内存 ${size(model.estimatedMemory(context))}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                if (!device.canLoad(model, context)) LocalModelBadge("可能超出可用内存")
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (transfer != null) {
                val received = transfer.progress.getLong("received", 0)
                val total = transfer.progress.getLong("total", 0)
                val converting = isLocalModelConversionStatus(status)
                if (total > 0 && !converting) {
                    AppLinearProgressIndicator(progress = { (received.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("已下载 ${size(received)} / ${size(total)}", style = MaterialTheme.typography.bodySmall)
                } else AppLinearProgressIndicator(Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!selected || !installed) Button(onClick = onPrimary, enabled = enabled) {
                    Text(if (transfer != null) "取消下载" else if (installed) "用于聊天" else "下载")
                }
                onSettings?.let { action -> OutlinedButton(onClick = action, enabled = enabled) {
                    Icon(Icons.Default.Tune, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("设置")
                } }
                if (working) onStop?.let { action -> OutlinedButton(onClick = action) { Text("停止") } }
            }
        }
    }
}

@Composable
private fun LocalModelBadge(text: String, positive: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small,
        color = if (positive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = if (positive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun LocalDeviceDialog(device: LocalDeviceProfile, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("关于本地模型") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("模型在手机上运行，无需 API 密钥。代理工具遵循现有权限设置，部分工具可能联网。")
            Text("总内存 ${size(device.totalRam)}\n可用内存 ${size(device.availableRam)}\n可用存储 ${size(device.freeStorage)}\n${device.cores} 个 CPU 核心 · ${device.abi}")
            if (!device.supported) Text("此设备没有兼容的本地引擎。", color = MaterialTheme.colorScheme.error)
            Text("内存预估仅供参考，你可以忽略警告继续。更大的模型和更长的对话可能导致速度下降，或使 Android 关闭应用。")
            Text("图像输入需要视觉模型和配套视觉文件。工具调用取决于模型能力。")
            Text("中断的下载会重新开始。每次回复后会卸载内存中的模型。")
            TextButton(onClick = { uri.openUri("https://github.com/ggml-org/llama.cpp") }) { Text("llama.cpp · MIT 许可证") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun LocalModelDetailsDialog(model: LocalModelSpec, context: Int, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("模型详情") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(model.title, style = MaterialTheme.typography.titleSmall)
            Text("${size(if (model.localFile) model.bytes else model.downloadBytes)} ${if (model.localFile) "在此手机上" else "下载"}\n${size(model.estimatedMemory(context))} 预估内存\n对话容量 $context 词元\n许可证：${model.license}")
            Text(model.filename, style = MaterialTheme.typography.bodySmall)
            if (model.description.isNotBlank()) Text(model.description, style = MaterialTheme.typography.bodySmall)
            Text(if (model.localFile) "已从你的文件复制，原始文件已保留。"
                else if (model.sha256.isNotBlank()) "下载内容会根据来源校验和进行验证。"
                else "检查文件大小和模型头信息，来源提供校验和时也会验证。", style = MaterialTheme.typography.bodySmall)
            if (!model.localFile) TextButton(onClick = { uri.openUri(model.modelPage) }) { Text("打开模型页面") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun LocalLimitsDialog(model: LocalModelSpec, manager: LocalModelManager, onDismiss: () -> Unit) {
    var saved by remember { mutableStateOf<LocalModelLimits?>(null) }
    LaunchedEffect(model.id) { saved = withContext(Dispatchers.IO) { manager.limits(model.id) } }
    val limits = saved ?: return
    var context by remember { mutableStateOf(limits.context.toString()) }
    var input by remember { mutableStateOf(limits.input.toString()) }
    var output by remember { mutableStateOf(limits.output.toString()) }
    var threads by remember { mutableStateOf(limits.threads.toString()) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("模型设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(model.title, style = MaterialTheme.typography.titleSmall)
            Text("词元是文本片段。数值越大，内存用量越高。更改从下次回复生效。", style = MaterialTheme.typography.bodySmall)
            LocalNumberField("对话大小", context, "词元", "用于指令、聊天历史和回复的空间。", !saving) { context = it }
            LocalNumberField("回复长度", output, "词元", "单次回复的最大长度。", !saving) { output = it }
            Text("预估内存 ${size(model.estimatedMemory(context.toIntOrNull()?.coerceAtLeast(1) ?: limits.context))}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { advanced = !advanced }, enabled = !saving) {
                Text(if (advanced) "收起高级设置" else "高级设置")
                Spacer(Modifier.width(6.dp)); Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (advanced) {
                LocalNumberField("输入预算", input, "词元", "包括指令和聊天历史。超出此预估值时仍可继续。", !saving) { input = it }
                LocalNumberField("处理线程数", threads, "线程", "更多线程可能增加发热，且不一定提高速度。", !saving) { threads = it }
            }
            TextButton(enabled = !saving, onClick = {
                context = model.defaultContext.toString()
                input = (model.defaultContext * 3 / 4).coerceAtLeast(1).toString()
                output = (model.defaultContext / 4).coerceAtLeast(1).toString()
                threads = manager.device().cores.coerceIn(1, 4).toString()
                error = null
            }) { Text("恢复默认值") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving, onClick = {
        scope.launch {
            saving = true
            try {
                manager.saveLimits(model.id, LocalModelLimits(context.toIntOrNull() ?: 0, input.toIntOrNull() ?: 0,
                    output.toIntOrNull() ?: 0, threads.toIntOrNull() ?: 0))
                onDismiss()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { saving = false }
        }
    }) { Text("保存") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun LocalNumberField(label: String, value: String, unit: String, help: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(unit) }, supportingText = { Text(help) },
        singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

private fun size(bytes: Long): String = if (bytes < 1_000_000_000L) String.format(Locale.US, "%.0f MB", bytes / 1_000_000.0)
    else String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)

@Composable
internal fun CustomModelDialog(manager: LocalModelManager, format: LocalModelFormat = LocalModelFormat.GGUF, onDismiss: () -> Unit, onDownload: (LocalModelSpec) -> Unit) {
    CustomModelLinkDialog(resolve = { manager.resolveLink(it, format) }, inspect = manager::inspectCustom, format = format,
        device = manager::device, onDismiss = onDismiss, onDownload = onDownload)
}

@Composable
internal fun CustomModelLinkDialog(resolve: suspend (String) -> List<LocalModelSpec>,
    inspect: suspend (LocalModelSpec) -> LocalModelSpec, device: () -> LocalDeviceProfile,
    onDismiss: () -> Unit, onDownload: (LocalModelSpec) -> Unit, format: LocalModelFormat = LocalModelFormat.GGUF) {
    var link by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<LocalModelSpec>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (format == LocalModelFormat.GGUF) "添加 GGUF" else "导入 safetensors") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (format == LocalModelFormat.GGUF) "粘贴 Hugging Face 页面或 GGUF 直接下载链接。"
                else "粘贴 Hugging Face 模型页面。文件将在手机上下载并转换。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(link, { link = it; candidates = emptyList(); error = null },
                label = { Text("模型链接") }, enabled = !loading, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            if (loading) { AppLinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在检查模型文件…", style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (candidates.isNotEmpty()) Text("选择文件", style = MaterialTheme.typography.titleSmall)
            candidates.forEach { model ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(model.filename.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(size(model.downloadBytes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(enabled = !loading, onClick = {
                                loading = true; error = null
                                scope.launch {
                                    try { onDownload(inspect(model)) }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "无法检查模型。" }
                                    finally { loading = false }
                                }
                            }) { Text("选择文件") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(enabled = link.isNotBlank() && !loading, onClick = {
        loading = true; error = null; candidates = emptyList()
        scope.launch {
            try {
                candidates = resolve(link)
                if (candidates.isEmpty()) error = "此链接中没有找到支持的模型文件。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "无法查找模型文件。" }
            finally { loading = false }
        }
    }) { Text("查找模型文件") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
