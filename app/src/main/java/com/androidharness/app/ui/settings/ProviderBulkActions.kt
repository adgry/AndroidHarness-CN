package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidharness.app.llm.ProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun deletableProvider(provider: ProviderConfig) = provider.id != com.androidharness.app.llm.HarnessProvider.ID &&
    !com.androidharness.app.chatgpt.ChatGptProtocol.isProvider(provider.id) &&
    !com.androidharness.app.local.LocalModelCatalog.isLocal(provider.id)

@Stable
internal class ProviderSelectionState(selecting: Boolean = false, ids: Set<String> = emptySet()) {
    var selecting by mutableStateOf(selecting)
    var ids by mutableStateOf(ids)
    var deleting by mutableStateOf<List<String>?>(null)
    var reviewing by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun toggle(id: String) { ids = if (id in ids) ids - id else ids + id }
    fun start(id: String) { selecting = true; ids = ids + id }
    fun done() { selecting = false; ids = emptySet(); error = null }
    fun requestDelete(ids: List<String>) { deleting = ids; reviewing = false; error = null }
}

@Composable
internal fun rememberProviderSelection(): ProviderSelectionState = rememberSaveable(saver = listSaver(
    save = { listOf(if (it.selecting) "yes" else "no") + it.ids },
    restore = { ProviderSelectionState(it.first() == "yes", it.drop(1).toSet()) },
)) { ProviderSelectionState() }

/** Confirmation is shared by the full settings screen and every in-chat provider manager. */
@Composable
internal fun ProviderBulkActions(
    state: ProviderSelectionState, providers: List<ProviderConfig>,
    onEnabledChange: suspend (String, Boolean) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selected = providers.filter { it.id in state.ids }
    if (state.selecting) {
        Text("已选择 ${selected.size} 项", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "启用", false to "停用").forEach { (enabled, label) ->
                OutlinedButton(enabled = selected.isNotEmpty() && !state.busy, onClick = {
                    scope.launch {
                        state.busy = true; state.error = null
                        try { selected.forEach { onEnabledChange(it.id, enabled) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { state.error = "部分提供商更新失败。请检查列表后重试。" }
                        finally { state.busy = false }
                    }
                }) { Text(label) }
            }
            OutlinedButton(enabled = selected.isNotEmpty() && selected.all(::deletableProvider) && !state.busy,
                onClick = { state.requestDelete(selected.map { it.id }) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
        if (selected.any { !deletableProvider(it) }) Text("可在此停用内置提供商、已连接账号和已安装模型。仅 API 连接可删除。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    state.deleting?.let { requested ->
        val targets = providers.filter { it.id in requested }
        val valid = targets.size == requested.size && targets.all(::deletableProvider)
        val multiple = requested.size > 1
        AlertDialog(onDismissRequest = { if (!state.busy) state.deleting = null },
            title = { Text(if (state.reviewing) "删除全部 ${requested.size} 个提供商？"
                else if (multiple) "删除 ${requested.size} 个提供商？" else "删除 ${targets.firstOrNull()?.name ?: "提供商"}？") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("将移除已保存的 API 密钥和连接，保留聊天记录。使用这些连接的聊天和计划任务需要选择其他提供商。")
                    if (state.reviewing || !multiple) targets.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
                    if (!valid) Text("提供商列表已更改。请调整选择后重试。", color = MaterialTheme.colorScheme.error)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = valid && !state.busy, onClick = {
                    if (multiple && !state.reviewing) state.reviewing = true
                    else scope.launch {
                        state.busy = true; state.error = null
                        val remaining = targets.map { it.id }.toMutableSet()
                        try {
                            targets.forEach { provider ->
                                onDelete(provider.id)
                                remaining.remove(provider.id)
                                state.ids = state.ids - provider.id
                            }
                            state.deleting = null
                            if (state.ids.isEmpty()) state.done()
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            state.deleting = null
                            state.selecting = true
                            state.ids = state.ids + remaining
                            state.error = "部分提供商无法删除。其余提供商仍被选中，请检查后重试。"
                        } finally { state.busy = false }
                    }
                }) { Text(if (state.busy) "正在删除…" else if (multiple && !state.reviewing) "继续" else if (multiple) "全部删除" else "删除提供商") }
            }, dismissButton = { TextButton(enabled = !state.busy, onClick = { state.deleting = null }) {
                Text(if (state.reviewing) "调整选择" else "取消")
            } })
    }
}
