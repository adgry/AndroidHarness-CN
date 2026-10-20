package com.androidharness.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.llm.*
import com.androidharness.app.ui.chat.components.ModelPickerSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FallbackSettingsSection(container: AppContainer, policy: FallbackPolicy) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SettingsAnchor("备用模型") {
        SettingsPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clickable { editing = true }.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("备用模型", style = MaterialTheme.typography.titleSmall)
                Text(if (policy.enabled) "${policy.targets.size} 个备用模型 · 重试 ${policy.retries} 次" else "未设置默认备用链 · 设置备用模型",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (editing) FallbackEditorSheet(container, policy, onDismiss = { editing = false },
        onSave = { _, next -> container.settings.setFallbackPolicy(next) })
}

/** One scroll surface keeps the chain, routing controls, and Save reachable on short screens. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FallbackEditorSheet(
    container: AppContainer,
    defaultPolicy: FallbackPolicy,
    onDismiss: () -> Unit,
    onSave: suspend (FallbackMode?, FallbackPolicy) -> Unit,
    chatMode: FallbackMode? = null,
    customPolicy: FallbackPolicy? = null,
    selectedProviderId: String? = null,
) {
    val providers by container.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalogs by container.providers.catalogs.collectAsStateWithLifecycle(initialValue = emptyMap())
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = com.androidharness.app.data.AppSettings())
    val scope = rememberCoroutineScope()
    var draftJson by rememberSaveable { mutableStateOf(FallbackPolicy.encode(customPolicy ?: defaultPolicy)) }
    val draft = FallbackPolicy.decode(draftJson)
    var modeName by rememberSaveable { mutableStateOf(chatMode?.name ?: FallbackMode.CUSTOM.name) }
    val mode = FallbackMode.valueOf(modeName)
    var picking by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var browseProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val editable = chatMode == null || mode == FallbackMode.CUSTOM
    val shown = if (editable) draft else if (mode == FallbackMode.DEFAULT) defaultPolicy else FallbackPolicy()
    fun update(policy: FallbackPolicy) { draftJson = FallbackPolicy.encode(policy); error = null }

    if (managing) {
        ProviderManagerSheet(providers, browseProviderId, container.providers::apiKey,
            onDismiss = { managing = false; picking = true },
            onSetActive = { id -> browseProviderId = id; managing = false; picking = true },
            onDelete = { id -> container.providers.delete(id) },
            catalogs = catalogs, onRefreshCatalog = container.providers::refreshCatalog,
            onEnabledChange = { id, enabled -> container.providers.setEnabled(id, enabled) },
            onMove = { id, direction, visible -> scope.launch { container.providers.move(id, direction, visible) } },
            onSave = { old, name, type, url, model, key, headers -> scope.launch {
                try {
                    if (old == null) container.providers.add(name, type, url, model, key, headers)
                    else container.providers.update(old.copy(name = name, type = type, baseUrl = url, model = model, headers = headers), key)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "无法保存提供商。请检查连接后重试。" }
            } })
        return
    }

    if (picking) {
        ModelPickerSheet(providers, null, null, catalogs,
            onDismiss = { picking = false },
            onSelect = { providerId, model ->
                val id = model ?: providers.firstOrNull { it.id == providerId }?.model
                if (!id.isNullOrBlank()) {
                    val target = FallbackTarget(providerId, id)
                    if (target !in draft.targets && draft.targets.size < FallbackPolicy.MAX_TARGETS)
                        update(draft.copy(enabled = true, targets = draft.targets + target))
                }
                picking = false
            },
            onRefreshCatalog = { id ->
                val provider = providers.firstOrNull { it.id == id }
                if (provider == null) "提供商不可用" else {
                    val key = if (id == HarnessProvider.ID) container.providers.harnessApiKey()
                        else provider.effectiveApiKey(container.providers.apiKey(id))
                    if (key.isBlank()) "重新连接此提供商" else when (val result = ModelCatalog.listModels(provider, key)) {
                        is ModelCatalog.Result.Models -> { container.providers.saveCatalog(id, result.models); null }
                        is ModelCatalog.Result.Failed -> result.message
                    }
                }
            },
            onManageProviders = { picking = false; managing = true },
            searchAllProvidersInitially = browseProviderId == null,
            browseProviderId = browseProviderId,
            onEnableProvider = { id -> scope.launch { container.providers.setEnabled(id, true) } },
        )
        return
    }

    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * .9f).dp)
            .navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("备用模型", style = MaterialTheme.typography.titleMedium)
                        Text(if (chatMode == null) "聊天默认设置" else "仅此聊天", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, enabled = !saving) { Icon(Icons.Default.Close, "关闭备用模型") }
                }
            }
            if (chatMode != null) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FallbackMode.DEFAULT to "使用默认设置", FallbackMode.CUSTOM to "自定义备用链", FallbackMode.OFF to "关闭").forEach { (value, label) ->
                        FilterChip(selected = mode == value, onClick = { modeName = value.name }, label = { Text(label) }, enabled = !saving)
                    }
                }
            }
            if (editable) item {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(draft.enabled, enabled = !saving, role = Role.Switch,
                    onValueChange = { update(draft.copy(enabled = it)) }), verticalAlignment = Alignment.CenterVertically) {
                    Text("自动切换备用模型", Modifier.weight(1f))
                    Switch(checked = draft.enabled, onCheckedChange = null, enabled = !saving)
                }
            }
            item { Text("首先使用所选模型，然后按顺序尝试这些模型。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(shown.targets, key = { _, it -> "${it.providerId}:${it.model}" }) { index, target ->
                val provider = providers.firstOrNull { it.id == target.providerId }
                val available = provider?.let { it.enabled && ProviderModelSearch.models(it, catalogs[it.id].orEmpty()).any { it.id == target.model } } == true
                val restricted = shown.sameProviderOnly && target.providerId != (selectedProviderId ?: settings.activeProviderId)
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(target.model, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(provider?.name ?: "提供商不可用", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!available) Text("不可用 · 启用并恢复前会跳过", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        else if (restricted) Text("已被同提供商路由跳过", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (privacy && provider != null) Text(runCatching { java.net.URI(
                            if (provider.id == HarnessProvider.ID) HarnessProvider.pool[target.model]?.baseUrl ?: provider.baseUrl else provider.baseUrl
                        ).host }.getOrNull() ?: "本地设备", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (editable) Box {
                        IconButton(onClick = { menu = true }, enabled = !saving) { Icon(Icons.Default.MoreVert, "第 ${index + 1} 个备用模型操作") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            fun move(offset: Int) {
                                val targets = draft.targets.toMutableList()
                                val other = index + offset
                                val swap = targets[other]; targets[other] = targets[index]; targets[index] = swap
                                update(draft.copy(targets = targets)); menu = false
                            }
                            DropdownMenuItem(text = { Text("上移") }, onClick = { move(-1) }, enabled = index > 0)
                            DropdownMenuItem(text = { Text("下移") }, onClick = { move(1) }, enabled = index < draft.targets.lastIndex)
                            DropdownMenuItem(text = { Text("移除") }, onClick = { update(draft.copy(targets = draft.targets - target)); menu = false })
                        }
                    }
                }
            }
            if (shown.targets.isEmpty()) item { Text(if (mode == FallbackMode.OFF && chatMode != null) "此聊天已关闭自动切换。" else "尚未添加备用模型。",
                style = MaterialTheme.typography.bodyMedium) }
            if (chatMode != null && mode == FallbackMode.DEFAULT && !defaultPolicy.enabled) item {
                Text("未设置默认备用链。已连接账号的切换设置仍生效。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editable) item {
                TextButton(onClick = { picking = true }, enabled = !saving && draft.targets.size < FallbackPolicy.MAX_TARGETS) { Text("添加模型（${draft.targets.size}/4）") }
            }
            if (editable) item {
                var retriesMenu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("切换前重试次数", Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { retriesMenu = true }, enabled = !saving) { Text("重试 ${draft.retries} 次") }
                        DropdownMenu(expanded = retriesMenu, onDismissRequest = { retriesMenu = false }) {
                            (0..5).forEach { count -> DropdownMenuItem(text = { Text("重试 $count 次") }, onClick = { update(draft.copy(retries = count)); retriesMenu = false }) }
                        }
                    }
                }
            }
            item { Text("配额耗尽时立即切换；临时失败时先重试。保留部分回复以供查看。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { TextButton(onClick = { privacy = !privacy }) { Text(if (privacy) "收起隐私与路由" else "隐私与路由") } }
            if (privacy) {
                item { Text((if (shown.enabled) "仅所选连接和此备用链中的模型可能接收对话，包括子代理和自动压缩请求。"
                    else if (chatMode != null && mode != FallbackMode.DEFAULT) "此聊天已关闭自动切换。明确配置的子代理模型仍生效。"
                    else "未启用默认备用链。已连接账号和子代理设置仍生效。") +
                    "数据保留遵循各提供商的政策，此设置不保证零数据保留（ZDR）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(shown.sameProviderOnly, enabled = editable && !saving, role = Role.Switch,
                        onValueChange = { update(draft.copy(sameProviderOnly = it)) }), verticalAlignment = Alignment.CenterVertically) {
                        Text("仅使用所选提供商", Modifier.weight(1f))
                        Switch(shown.sameProviderOnly, onCheckedChange = null, enabled = editable && !saving)
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(onClick = {
                    saving = true; error = null
                    scope.launch {
                        try { onSave(if (chatMode == null) null else mode, draft); onDismiss() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "无法保存备用模型，请重试。" }
                        finally { saving = false }
                    }
                }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "正在保存…" else "保存备用模型") }
            }
        }
    }
}
