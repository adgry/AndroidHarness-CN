package com.androidharness.app.ui.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.R
import com.androidharness.app.ui.common.CatalogLoadingIndicator
import com.androidharness.app.ui.common.ExpressiveIconButton
import com.androidharness.app.ui.theme.expressiveEffectsSpec
import com.androidharness.app.chatgpt.ChatGptProtocol
import com.androidharness.app.data.ModelShortcut
import com.androidharness.app.data.ModelShortcuts
import com.androidharness.app.data.ModelVisibility
import com.androidharness.app.llm.*
import com.androidharness.app.ui.settings.ModelVisibilitySheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Unified discovery with a separate provider chooser; browsing never changes the active model. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfig>,
    activeProviderId: String?,
    activeModel: String?,
    catalogs: Map<String, List<ModelEntry>>,
    onDismiss: () -> Unit,
    onSelect: (providerId: String, model: String?) -> Unit,
    onRefreshCatalog: suspend (providerId: String) -> String?,
    onManageProviders: () -> Unit,
    browseProviderId: String? = null,
    onAddCustomModel: (providerId: String, model: String, reasoning: Boolean?) -> Unit = { _, _, _ -> },
    onDeleteCustomModel: (providerId: String, model: String) -> Unit = { _, _ -> },
    sameModelAcrossChats: Boolean? = null,
    onModelScopeChange: (Boolean) -> Unit = {},
    modelScopeEnabled: Boolean = true,
    modelShortcuts: ModelShortcuts? = null,
    searchAllProvidersInitially: Boolean = false,
    onEnableProvider: (String) -> Unit = {},
    fallbackSummary: String? = null,
    fallbackEditEnabled: Boolean = true,
    onEditFallbacks: () -> Unit = {},
    modelVisibility: ModelVisibility? = null,
    manageModelsInitially: Boolean = false,
    onAddProvider: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val compactHeight = LocalConfiguration.current.screenHeightDp < 600 * LocalDensity.current.fontScale
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val shortcuts = remember(context, modelShortcuts) { modelShortcuts ?: ModelShortcuts.get(context) }
    val visibility = remember(context, modelVisibility) { modelVisibility ?: ModelVisibility.get(context) }
    val saved by shortcuts.state.collectAsStateWithLifecycle()
    val visible by visibility.state.collectAsStateWithLifecycle()
    val directoryRevision by ModelsDev.revision.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var providerQuery by rememberSaveable { mutableStateOf("") }
    var listedId by rememberSaveable {
        mutableStateOf(browseProviderId ?: activeProviderId.takeUnless { searchAllProvidersInitially })
    }
    var showAll by rememberSaveable { mutableStateOf(searchAllProvidersInitially) }
    var collection by rememberSaveable { mutableStateOf("All") }
    var thinkOnly by rememberSaveable { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(manageModelsInitially) }
    var showAddCustom by rememberSaveable { mutableStateOf(false) }
    var choosingProvider by rememberSaveable { mutableStateOf(false) }
    var showScope by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var pendingProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingModel by rememberSaveable { mutableStateOf<String?>(null) }
    var enableChoice by remember { mutableStateOf<ModelShortcut?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    val initialShared = remember { sameModelAcrossChats }
    val activeProvider = providers.firstOrNull { it.id == activeProviderId }
    val effective = activeModel?.takeIf(String::isNotBlank) ?: activeProvider?.model
    val listedProvider = providers.firstOrNull { it.id == listedId }
    val chosenProviderId = pendingProvider ?: activeProviderId
    val chosenModelId = pendingModel ?: effective
    val chosenProvider = providers.firstOrNull { it.id == chosenProviderId }
    val chosenEntry = chosenProvider?.let { p -> ProviderModelSearch.models(p, catalogs[p.id].orEmpty()).firstOrNull { it.id == chosenModelId } }
    val searching = query.isNotBlank()
    val scopedProviders = if (listedId == null) providers else providers.filter { it.id == listedId }
    val shortcutProviderIds = scopedProviders.mapTo(mutableSetOf()) { it.id }
    fun hasShortcuts(keys: List<ModelShortcut>) = keys.any {
        it.providerId in shortcutProviderIds && (showAll || visible.includes(it.providerId, it.modelId))
    }
    val collections = buildList {
        add("All" to "所有模型")
        if (hasShortcuts(saved.recent)) add("Recent" to "最近")
        if (hasShortcuts(saved.favourites)) add("Favourites" to "收藏")
    }
    val activeCollection = collection.takeIf { key -> collections.any { it.first == key } } ?: "All"
    LaunchedEffect(activeCollection) { collection = activeCollection }
    val rows = ProviderModelSearch.results(
        scopedProviders, catalogs, query, if (searching) "All" else activeCollection,
        saved.favourites, saved.recent,
        includeDisabled = showAll || searching || listedProvider?.enabled == false || activeCollection != "All",
        visibility = if (showAll || searching) null else visible,
    ).filter { !thinkOnly || ModelCapabilities.of(it.model, it.provider).thinking.supported == true }.let { results ->
        if (ChatGptProtocol.isProvider(listedId) && (searching || activeCollection == "All")) results.sortedWith { a, b -> newestChatGptFirst.compare(a.model, b.model) }
        else results
    }
    LaunchedEffect(providers.map { it.id }, listedId) {
        if (listedId != null && providers.none { it.id == listedId }) listedId = null
    }
    fun commit(providerId: String, model: String) {
        if (!modelScopeEnabled) return
        onSelect(providerId, model)
        shortcuts.selected(ModelShortcut(providerId, model))
        onDismiss()
    }
    suspend fun refresh(ids: List<String>) {
        if (isRefreshing || ids.isEmpty()) return
        isRefreshing = true; refreshError = null
        try {
            val limit = Semaphore(3)
            val failures = supervisorScope {
                ids.map { id -> async { limit.withPermit {
                    val error = try { onRefreshCatalog(id) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { "无法刷新" }
                    error?.let { "${providers.firstOrNull { it.id == id }?.name ?: "服务商"}: $it" }
                } } }.awaitAll().filterNotNull()
            }
            refreshError = failures.firstOrNull()?.let { if (failures.size > 1) "${failures.size} 个服务商无法刷新。$it" else it }
        } finally { isRefreshing = false }
    }
    LaunchedEffect(listedId, providers.map { it.id }, managing) {
        if (!managing) refresh(scopedProviders.filter { it.enabled && (catalogs[it.id].isNullOrEmpty() ||
            ChatGptProtocol.isProvider(it.id) || it.id == HarnessProvider.ID) }.map { it.id })
    }
    if (managing) {
        ModelVisibilitySheet(providers, catalogs, listedId,
            onDismiss = { if (manageModelsInitially) onDismiss() else managing = false },
            visibility = visibility, onRefreshCatalog = onRefreshCatalog)
        return
    }
    @Composable fun SearchField() {
        OutlinedTextField(value = if (choosingProvider) providerQuery else query,
                onValueChange = { if (choosingProvider) providerQuery = it else query = it }, singleLine = true,
                placeholder = { Text(if (choosingProvider) "搜索服务商" else "搜索模型或服务商",
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(19.dp)) },
                trailingIcon = { if ((if (choosingProvider) providerQuery else query).isNotEmpty()) IconButton(onClick = {
                    if (choosingProvider) providerQuery = "" else query = ""
                }) { Icon(Icons.Filled.Close, "清除搜索", Modifier.size(18.dp)) } },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = if (compactHeight) null else ({ BottomSheetDefaults.DragHandle() }), containerColor = scheme.surface) {
        Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp * if (compactHeight) .96f else .86f).dp)
            .navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (choosingProvider) IconButton(onClick = { choosingProvider = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回模型", Modifier.size(20.dp))
                }
                Text(if (choosingProvider) "切换服务商" else "选择模型", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                sameModelAcrossChats?.let { shared ->
                    TextButton(onClick = { showScope = true }, enabled = modelScopeEnabled) {
                        Text(if (shared) "所有聊天" else "此聊天", style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                    }
                }
                if (compactHeight && !choosingProvider) IconButton(onClick = { details = true }, enabled = chosenEntry != null) { Icon(Icons.Outlined.Info, "所选模型详情", Modifier.size(19.dp)) }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭", Modifier.size(20.dp)) }
            }
            if (choosingProvider) {
                LazyColumn(Modifier.weight(1f)) {
                    item { SearchField() }
                    item { ProviderChoiceRow("所有服务商", "搜索所有服务商", listedId == null,
                        onClick = { focusManager.clearFocus(); listedId = null; query = ""; collection = "All"; choosingProvider = false }) }
                    val matches = providers.filter { ProviderModelSearch.providerMatches(it, catalogs[it.id].orEmpty(), providerQuery) }
                    items(matches, key = { it.id }) { provider ->
                        val count = ProviderModelSearch.models(provider, catalogs[provider.id].orEmpty()).size
                        ProviderChoiceRow(provider.name, "$count 个模型 · ${if (provider.enabled) "已启用" else "已停用"}",
                            listedId == provider.id, onClick = {
                                focusManager.clearFocus(); listedId = provider.id; query = ""; collection = "All"; choosingProvider = false
                            })
                    }
                    if (matches.isEmpty()) item { Text("没有匹配的服务商。", Modifier.padding(vertical = 24.dp), color = scheme.onSurfaceVariant) }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                if (compactHeight) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { onDismiss(); (onAddProvider ?: onManageProviders)() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加服务商")
                    }
                    TextButton(onClick = { onDismiss(); onManageProviders() }, Modifier.weight(1f)) { Text("管理服务商") }
                } else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { onDismiss(); (onAddProvider ?: onManageProviders)() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("添加服务商")
                    }
                    TextButton(onClick = { onDismiss(); onManageProviders() }, Modifier.fillMaxWidth()) { Text("管理服务商") }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        SearchField()
                        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { focusManager.clearFocus(); choosingProvider = true }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("切换服务商")
                            }
                            Text(listedProvider?.name ?: "所有服务商", style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            collections.forEach { (key, label) ->
                                val selected = if (searching) key == "All" else activeCollection == key
                                val collectionColor by animateColorAsState(
                                    if (selected) scheme.secondaryContainer else scheme.surface,
                                    animationSpec = expressiveEffectsSpec(), label = "model collection",
                                )
                                Surface(onClick = { collection = key; query = "" }, shape = RoundedCornerShape(9.dp),
                                    color = collectionColor,
                                    modifier = Modifier.semantics { this.selected = selected; role = Role.Tab }) {
                                    Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${rows.size} 个模型${if (showAll) " · 显示全部" else ""}",
                                Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            TextButton(onClick = { managing = true }) { Text("编辑列表", style = MaterialTheme.typography.labelMedium) }
                            ExpressiveIconButton(onClick = { scope.launch { refresh(scopedProviders.filter { it.enabled || listedId == it.id }.map { it.id }) } }, enabled = !isRefreshing) {
                                if (isRefreshing) CatalogLoadingIndicator(Modifier.size(20.dp))
                                else Icon(Icons.Outlined.Refresh, "刷新模型", Modifier.size(18.dp))
                            }
                        }
                        refreshError?.let { Text("无法刷新，已保存的模型仍可使用。$it", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = scheme.error) }
                    }
                    if (rows.isEmpty()) item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(when {
                                isRefreshing -> "正在加载服务商目录…"
                                providers.isEmpty() -> "添加服务商以选择模型。"
                                collection == "Favourites" -> "收藏模型后会显示在这里。"
                                collection == "Recent" -> "你使用的模型会显示在这里。"
                                else -> "没有匹配的模型，请尝试其他搜索或显示所有模型。"
                            }, color = scheme.onSurfaceVariant)
                            TextButton(onClick = { collection = "All"; query = ""; showAll = true }) { Text("显示所有模型") }
                            if (providers.isEmpty()) TextButton(onClick = { onDismiss(); (onAddProvider ?: onManageProviders)() }) { Text("添加服务商") }
                        }
                    }
                    items(rows, key = { "${it.provider.id.length}:${it.provider.id}:${it.model.id}" }) { result ->
                        val entry = result.model; val owner = result.provider
                        val capabilities = remember(entry, owner, directoryRevision) { ModelCapabilities.of(entry, owner) }
                        Box(Modifier.padding(horizontal = 12.dp)) { ModelRow(entry, owner, owner.id == chosenProviderId && entry.id == chosenModelId,
                            !visible.includes(owner.id, entry.id),
                            ctxLabel(entry.contextTokens ?: ModelsDev.entry(ModelsDev.providerKeyFor(owner.baseUrl), entry.id)?.contextTokens),
                            if (!result.available) "不可用 · 请刷新或选择其他模型" else entry.note,
                            modelScopeEnabled && result.available, result.shortcut in saved.favourites,
                            onFavourite = { shortcuts.toggle(result.shortcut) },
                            onClick = { focusManager.clearFocus(); pendingProvider = owner.id; pendingModel = entry.id },
                            capabilities = capabilities, current = owner.id == activeProviderId && entry.id == effective,
                            onDelete = if (entry.custom) ({ onDeleteCustomModel(owner.id, entry.id) }) else null) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = thinkOnly, onClick = { thinkOnly = !thinkOnly }, label = { Text("仅推理模型") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_brain), null, Modifier.size(15.dp)) })
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreHoriz, "模型选项") }
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text(if (showAll) "使用自定义列表" else "显示全部") }, onClick = { showAll = !showAll; more = false })
                            DropdownMenuItem(text = { Text("管理模型") }, onClick = { more = false; managing = true })
                            if (listedProvider != null && !ChatGptProtocol.isProvider(listedId) && !com.androidharness.app.local.LocalModelCatalog.isLocal(listedProvider.id))
                                DropdownMenuItem(text = { Text("自定义模型") }, onClick = { more = false; showAddCustom = true })
                            if (collection == "Recent") DropdownMenuItem(text = { Text("清空最近记录") }, onClick = {
                                if (listedId != null) shortcuts.clearRecent(listedId!!) else providers.forEach { shortcuts.clearRecent(it.id) }; more = false
                            })
                            fallbackSummary?.let { summary -> DropdownMenuItem(text = { Column { Text("回退模型"); Text(summary, style = MaterialTheme.typography.bodySmall) } },
                                enabled = modelScopeEnabled && fallbackEditEnabled, onClick = { more = false; onEditFallbacks() }) }
                        }
                    }
                }
                if (!compactHeight && ChatGptProtocol.isProvider(chosenProviderId)) com.androidharness.app.ui.common.ChatGptUsageLimits(compact = true)
                HorizontalDivider(color = scheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (!compactHeight) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(when (sameModelAcrossChats) { true -> "适用于所有聊天"; false -> "适用于此聊天"; null -> "适用于此次选择" }, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            Text(chosenEntry?.displayName ?: chosenModelId ?: "选择模型", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            chosenProvider?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { details = true }, enabled = chosenEntry != null) { Icon(Icons.Outlined.Info, "所选模型详情", Modifier.size(19.dp)) }
                    }
                    val unchanged = pendingModel == null && initialShared == sameModelAcrossChats && chosenEntry != null
                    Button(onClick = {
                        if (chosenProvider != null && chosenModelId != null) {
                            if (chosenProvider.enabled) commit(chosenProvider.id, chosenModelId)
                            else enableChoice = ModelShortcut(chosenProvider.id, chosenModelId)
                        }
                    }, enabled = modelScopeEnabled && chosenEntry != null && !unchanged,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(when { unchanged -> "当前模型"; chosenProvider?.enabled == false -> "启用并使用"; sameModelAcrossChats == true -> "在所有聊天中使用"; else -> "使用模型" })
                        Spacer(Modifier.width(8.dp)); Icon(if (unchanged) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    enableChoice?.let { choice ->
        val provider = providers.firstOrNull { it.id == choice.providerId }
        AlertDialog(onDismissRequest = { enableChoice = null }, title = { Text("启用 ${provider?.name ?: "服务商"}？") },
            text = { Text("启用此服务商，并${if (sameModelAcrossChats == true) "在所有聊天中" else "对此次选择"}使用 ${choice.modelId}。") },
            confirmButton = { TextButton(enabled = provider != null && modelScopeEnabled, onClick = { onEnableProvider(choice.providerId); commit(choice.providerId, choice.modelId) }) { Text("启用并使用") } },
            dismissButton = { TextButton(onClick = { enableChoice = null }) { Text("取消") } })
    }
    if (showScope && sameModelAcrossChats != null) AlertDialog(onDismissRequest = { showScope = false }, title = { Text("模型适用范围") },
        text = { Column { listOf(false to "此聊天", true to "所有聊天").forEach { (shared, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(sameModelAcrossChats == shared, enabled = modelScopeEnabled, role = Role.RadioButton,
                onClick = { onModelScopeChange(shared); showScope = false }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(sameModelAcrossChats == shared, null); Spacer(Modifier.width(8.dp)); Text(label)
            }
        } } }, confirmButton = { TextButton(onClick = { showScope = false }) { Text("关闭") } })
    if (details && chosenEntry != null && chosenProvider != null) ModelDetailsDialog(chosenEntry, chosenProvider, onDismiss = { details = false })
    if (showAddCustom && listedProvider != null) AddCustomModelDialog(listedProvider.name,
        onDismiss = { showAddCustom = false }, onAdd = { model, reasoning ->
            onAddCustomModel(listedProvider.id, model, reasoning)
            if (visible.selected.containsKey(listedProvider.id)) visibility.save(listedProvider.id, visible.selected.getValue(listedProvider.id) + model)
            commit(listedProvider.id, model)
        })
}

@Composable
private fun ProviderChoiceRow(name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelProviderMark(name, 30.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.Check, "所选服务商", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ModelRow(
    entry: ModelEntry, provider: ProviderConfig, selected: Boolean, hidden: Boolean, context: String?,
    note: String?, enabled: Boolean, favourite: Boolean, onFavourite: () -> Unit, onClick: () -> Unit,
    capabilities: ModelCapabilities, current: Boolean, onDelete: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        if (selected) scheme.secondaryContainer else scheme.surface,
        animationSpec = expressiveEffectsSpec(), label = "model selection",
    )
    Surface(color = containerColor,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, if (selected) scheme.primary.copy(alpha = .65f) else scheme.surface),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 72.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = "选择 ${provider.name} 的 ${entry.displayName ?: entry.id}" }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ModelProviderMark(provider.name, 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(entry.displayName ?: entry.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(provider.name, if (current) "当前" else null, if (hidden) "已隐藏" else null, if (entry.custom) "自定义" else null, context).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    if (!provider.enabled) Text("已停用 · 启用并使用", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    ModelCapabilityLabels(capabilities)
                    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant) }
                }
                Box(Modifier.size(17.dp), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Outlined.CheckCircle, "已选择", Modifier.size(17.dp), tint = scheme.onSecondaryContainer)
                }
            }
            ExpressiveIconButton(onClick = onFavourite) { Icon(if (favourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                if (favourite) "取消收藏 ${entry.displayName ?: entry.id}" else "收藏 ${entry.displayName ?: entry.id}",
                Modifier.size(18.dp), tint = if (favourite) scheme.primary else scheme.onSurfaceVariant) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, "移除自定义模型", Modifier.size(17.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCapabilityLabels(capabilities: ModelCapabilities) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        @Composable fun Label(text: String, icon: Painter, value: ModelCapabilities.Capability) {
            if (value.supported == true) Row(Modifier.semantics { contentDescription = "$text: ${value.description}" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val inferred = capabilities.thinking.source == ModelCapabilities.Source.FAMILY
        Label(if (inferred) "推理支持待确认" else "思考", painterResource(R.drawable.ic_brain), capabilities.thinking)
        Label("工具", rememberVectorPainter(Icons.Outlined.Build), capabilities.tools)
        Label("图片", rememberVectorPainter(Icons.Outlined.Image), capabilities.images)
    }
}

@Composable
private fun ModelProviderMark(name: String, size: Dp) {
    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase().orEmpty(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ModelDetailsDialog(entry: ModelEntry, provider: ProviderConfig, onDismiss: () -> Unit) {
    val revision by ModelsDev.revision.collectAsStateWithLifecycle()
    val capabilities = remember(entry, provider, revision) { ModelCapabilities.of(entry, provider) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(entry.displayName ?: entry.id) }, text = {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(provider.name, style = MaterialTheme.typography.bodyMedium)
            listOf("思考" to capabilities.thinking, "工具" to capabilities.tools, "图片" to capabilities.images).forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(value.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("支持推理不代表一定能显示思考文本。同一模型在不同服务商上的能力可能不同。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })
}

@Composable
private fun AddCustomModelDialog(
    providerName: String,
    onDismiss: () -> Unit,
    onAdd: (modelId: String, reasoning: Boolean?) -> Unit,
) {
    var modelId by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val clean = modelId.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "添加 $providerName 支持的任意模型 ID。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("例如 meta-llama/llama-3.3-70b") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { thinking = !thinking }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = thinking,
                        onCheckedChange = { thinking = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("推理模型", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "为此模型启用思考级别选项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = clean.isNotBlank(),
                onClick = {
                    onAdd(clean, if (thinking) true else null)
                    onDismiss()
                },
            ) {
                Text("添加并选择")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private val newestChatGptFirst = Comparator<ModelEntry> { left, right ->
    fun version(entry: ModelEntry) = entry.id.lowercase().removePrefix("gpt-")
        .substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val leftVersion = version(left)
    val rightVersion = version(right)
    for (index in 0 until maxOf(leftVersion.size, rightVersion.size)) {
        val order = rightVersion.getOrElse(index) { 0 }.compareTo(leftVersion.getOrElse(index) { 0 })
        if (order != 0) return@Comparator order
    }
    0
}

/** Compact context-window label for picker rows ("200K ctx", "1M ctx", "1B ctx"). */
private fun ctxLabel(tokens: Long?): String? = when {
    tokens == null || tokens <= 0 -> null
    tokens >= 1_000_000_000 -> "${tokens / 1_000_000_000}B 上下文"
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M 上下文"
    tokens >= 1_000 -> "${tokens / 1_000}K 上下文"
    else -> "$tokens 上下文"
}
