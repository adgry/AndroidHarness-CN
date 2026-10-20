package com.androidharness.app.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.*
import com.androidharness.app.llm.ProviderConfig
import com.androidharness.app.llm.ReasoningMapping
import com.androidharness.app.llm.ReasoningDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomReasoningSheet(
    config: ProviderConfig,
    saved: CustomReasoningConfig?,
    spec: ThinkingSpecs.Spec,
    onDismiss: () -> Unit,
    onSave: (CustomReasoningConfig?) -> Unit,
    selectedLevel: String? = null,
    onDetectLevels: (suspend () -> List<String>)? = null,
    startAdding: Boolean = false,
) {
    val defaults = remember(spec) {
        CustomReasoning.defaultLevels(spec)
    }
    val initialLevels = saved?.levels ?: defaults
    var levels by rememberSaveable(config.id, config.model) {
        mutableStateOf(ArrayList(initialLevels + if (startAdding && initialLevels.size < 16) listOf("") else emptyList()))
    }
    var levelsEdited by rememberSaveable(config.id, config.model) { mutableStateOf(startAdding) }
    var detecting by remember { mutableStateOf(false) }
    var detectionMessage by remember { mutableStateOf<String?>(null) }
    var detectionFailed by remember { mutableStateOf(false) }
    LaunchedEffect(defaults) {
        if (saved == null && !levelsEdited && !detecting) levels = ArrayList(defaults)
    }
    var editingLevel by rememberSaveable { mutableStateOf<Int?>(if (startAdding && initialLevels.size < 16) initialLevels.size else null) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var showLevelHelp by remember { mutableStateOf(false) }
    val levelFocus = remember { FocusRequester() }
    LaunchedEffect(editingLevel) { if (editingLevel != null) levelFocus.requestFocus() }
    var mapping by rememberSaveable(config.id, config.model) { mutableStateOf(saved?.mapping.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var previewLevel by rememberSaveable {
        mutableIntStateOf(saved?.levels?.indexOfFirst { CustomReasoning.wire(it) == selectedLevel?.let(CustomReasoning::wire) }?.takeIf { it >= 0 } ?: 0)
    }
    var validating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val json = remember { Json { prettyPrint = true } }
    val requestConfig = remember(config) { com.androidharness.app.llm.OpenAiCompatProvider.requestConfig(ThinkingSpecs.routedConfig(config), hasTools = true) }
    val inheritedMapping = remember(requestConfig, spec) { ReasoningDefaults.inherited(requestConfig, spec) }
    val effectiveMapping = mapping.trim().takeIf { it.isNotEmpty() } ?: inheritedMapping
    val localControl = ReasoningDefaults.isLocal(config)
    LaunchedEffect(effectiveMapping, levels.toList(), previewLevel) {
        preview = null
        if (effectiveMapping != null && levels.isNotEmpty()) {
            delay(300)
            val name = levels[previewLevel.coerceIn(levels.indices)]
            preview = withContext(Dispatchers.Default) {
                runCatching { json.encodeToString(JsonObject.serializer(), ReasoningMapping.evaluate(effectiveMapping, name)) }
                    .getOrElse { it.message ?: "映射无效" }
            }
        }
    }
    fun editLevels(change: MutableList<String>.() -> Unit) {
        levelsEdited = true
        detectionMessage = null
        levels = ArrayList(levels.toMutableList().apply(change)); error = null
    }
    if (showLevelHelp) AlertDialog(onDismissRequest = { showLevelHelp = false },
        title = { Text("关于推理级别") }, text = { Text(CustomReasoning.LEVEL_HELP) },
        confirmButton = { TextButton(onClick = { showLevelHelp = false }) { Text("知道了") } })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("自定义思考", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭思考设置") }
            }
            Text("${config.name} · ${config.model}", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text("更改将用于下一条消息。",
                Modifier.padding(top = 6.dp, bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("推理级别", style = MaterialTheme.typography.titleSmall)
                        Text("按思考强度从低到高排列", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showLevelHelp = true }) { Icon(Icons.Outlined.Info, "关于推理级别", Modifier.size(20.dp)) }
                }
                if (onDetectLevels != null && !localControl) {
                    TextButton(enabled = !validating && !detecting, onClick = {
                        detecting = true; levelsEdited = true; detectionMessage = null; detectionFailed = false
                        scope.launch {
                            try {
                                val detected = CustomReasoning.normalized(CustomReasoningConfig(onDetectLevels())).levels
                                val selected = levels.getOrNull(previewLevel)?.let(CustomReasoning::wire)
                                levelsEdited = true; levels = ArrayList(detected); editingLevel = null
                                previewLevel = detected.indexOfFirst { CustomReasoning.wire(it) == selected }.coerceAtLeast(0)
                                error = null
                                detectionMessage = "检测到 ${detected.size} 个级别，保存后生效。"
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e
                            } catch (e: Exception) {
                                detectionFailed = true
                                detectionMessage = e.message ?: "无法检测级别，请重试。"
                            } finally { detecting = false }
                        }
                    }) {
                        if (detecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (detecting) "正在检查服务商…" else "自动检测级别")
                    }
                    detectionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (detectionFailed) scheme.error else scheme.primary) }
                }
                if (saved == null && !levelsEdited && spec.reportedLevels != null) Text("从服务商检测",
                    style = MaterialTheme.typography.bodySmall, color = scheme.primary)
                Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow) {
                  Column {
                    levels.forEachIndexed { index, name ->
                        var actions by remember(index) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable(enabled = !validating && !detecting) { editingLevel = index }
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}", Modifier.width(28.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            if (editingLevel == index) {
                                OutlinedTextField(name, { value -> editLevels { set(index, value.take(32)) } },
                                    modifier = Modifier.weight(1f).focusRequester(levelFocus)
                                        .semantics { contentDescription = "第 ${index + 1} 级名称" }, singleLine = true,
                                    enabled = !validating && !detecting, label = { Text("级别名称") })
                                IconButton(onClick = { editingLevel = null }) { Icon(Icons.Outlined.Check, "完成级别编辑", Modifier.size(20.dp)) }
                            } else Text(name.takeIf { it.isNotBlank() }?.let(CustomReasoning::label) ?: "新建级别",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Box {
                                IconButton(onClick = { actions = true }, enabled = !validating && !detecting) {
                                    Icon(Icons.Outlined.MoreVert, "第 ${index + 1} 级操作", Modifier.size(20.dp))
                                }
                                DropdownMenu(actions, { actions = false }) {
                                    DropdownMenuItem(text = { Text("重命名") }, onClick = { actions = false; editingLevel = index })
                                    DropdownMenuItem(text = { Text("上移") }, enabled = index > 0,
                                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) }, onClick = {
                                            actions = false; editLevels { java.util.Collections.swap(this, index, index - 1) }; editingLevel = null
                                        })
                                    DropdownMenuItem(text = { Text("下移") }, enabled = index < levels.lastIndex,
                                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) }, onClick = {
                                            actions = false; editLevels { java.util.Collections.swap(this, index, index + 1) }; editingLevel = null
                                        })
                                    DropdownMenuItem(text = { Text("移除", color = scheme.error) },
                                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.error) }, onClick = {
                                            actions = false; editLevels { removeAt(index) }; editingLevel = null
                                        })
                                }
                            }
                        }
                        if (index < levels.lastIndex) HorizontalDivider(Modifier.padding(start = 42.dp, end = 14.dp), color = scheme.outlineVariant.copy(alpha = .5f))
                    }
                    TextButton(onClick = {
                        editLevels { add("") }; editingLevel = levels.lastIndex
                    }, enabled = !validating && !detecting && levels.size < 16) {
                        Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("添加级别")
                    }
                  }
                }
                Text("点按名称可编辑，请仅添加模型支持的级别。",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Surface(modifier = Modifier.semantics {
                    contentDescription = "高级请求映射"
                    stateDescription = if (advancedExpanded) "已展开" else "已收起"
                }, shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow,
                    onClick = { advancedExpanded = !advancedExpanded }) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Code, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("高级请求映射", style = MaterialTheme.typography.titleSmall)
                            Text(if (mapping.isBlank()) "可选 · 服务商默认值" else "正在使用自定义 CEL 映射",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        Icon(if (advancedExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            null)
                    }
                }
                if (advancedExpanded) {
                Text("推理参数映射（可选）", style = MaterialTheme.typography.titleSmall)
                Text("留空可沿用此模型或服务商的默认映射。输入 CEL 表达式可覆盖默认值，其返回的 JSON 对象会合并到发送的请求体中。",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("reasoningLevel 使用小写（如 disabled、low、high、max、xhigh）。嵌套对象会合并，null 会移除字段。",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                OutlinedTextField(mapping, { mapping = it.take(8_192); error = null },
                    Modifier.fillMaxWidth().semantics { contentDescription = "CEL 表达式（可选）" },
                    minLines = 4, maxLines = 8, enabled = !validating && !localControl,
                    placeholder = { Text(inheritedMapping ?: if (localControl) "原生开关控制" else "没有可沿用的映射，请输入 CEL 表达式。",
                        maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                Text(when {
                    localControl -> "在此设备上使用原生开关控制。"
                    mapping.isNotBlank() -> "正在使用自定义 CEL 映射"
                    inheritedMapping != null -> "正在沿用模型或服务商映射"
                    else -> "没有可沿用的映射，需要自定义 CEL 映射。"
                }, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (inheritedMapping != null) TextButton(onClick = {
                    mapping = if (mapping.isBlank()) inheritedMapping else ""; error = null
                }, enabled = !validating) {
                    Text(if (mapping.isBlank()) "编辑沿用的映射" else "使用沿用的映射")
                }
                if (effectiveMapping != null && levels.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("请求字段预览", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        var showLevels by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { showLevels = true }) { Text(levels[previewLevel.coerceIn(levels.indices)].ifBlank { "未命名" }) }
                            DropdownMenu(showLevels, { showLevels = false }) {
                                levels.forEachIndexed { i, label -> DropdownMenuItem(text = { Text(label.ifBlank { "未命名" }) },
                                    onClick = { previewLevel = i; showLevels = false }) }
                            }
                        }
                    }
                    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainerHigh) {
                        Text(preview ?: "正在检查表达式…", Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                }
                Spacer(Modifier.height(4.dp))
            }
            error?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                val stacked = maxWidth < 300.dp * LocalDensity.current.fontScale
                val saveAction: () -> Unit = {
                    val draft = CustomReasoningConfig(levels.toList(), mapping)
                    scope.launch {
                        validating = true
                        val result = withContext(Dispatchers.Default) { runCatching { ReasoningMapping.validate(draft, requestConfig, spec) } }
                        validating = false
                        result.onSuccess(onSave).onFailure {
                            error = it.message ?: "请检查推理设置。"
                            if (error.orEmpty().contains("mapping", ignoreCase = true) || error.orEmpty().contains("CEL")) advancedExpanded = true
                        }
                    }
                }
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!stacked) TextButton(onClick = { onSave(null) }, enabled = !validating && !detecting) { Text("使用默认值") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss, enabled = !validating) { Text("取消", maxLines = 1) }
                        Button(enabled = !validating && !detecting, onClick = saveAction) { Text(if (validating) "正在检查…" else "保存", maxLines = 1) }
                    }
                    if (stacked) TextButton(onClick = { onSave(null) }, enabled = !validating && !detecting) { Text("使用默认值") }
                }
            }
        }
    }
}
