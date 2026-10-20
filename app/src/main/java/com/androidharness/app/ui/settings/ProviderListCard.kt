package com.androidharness.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.llm.ProviderConfig
import com.androidharness.app.llm.ProviderType
import com.androidharness.app.ui.common.ProviderMark

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProviderListCard(
    provider: ProviderConfig, active: Boolean, canMoveUp: Boolean, canMoveDown: Boolean,
    onSelect: () -> Unit, onEnabledChange: (Boolean) -> Unit, onMove: (Int) -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onBrowseModels: (() -> Unit)? = null,
    catalogSize: Int? = null, selectedModelCount: Int? = null,
    selectionMode: Boolean = false, selected: Boolean = false,
    onToggleSelection: () -> Unit = {}, onLongPress: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val editable = deletableProvider(provider)
    Surface(color = if (selected || active && !selectionMode) scheme.secondaryContainer else scheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = .4f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 56.dp)
                    .combinedClickable(onClick = { if (selectionMode) onToggleSelection() else if (provider.enabled) onSelect() },
                        onLongClick = onLongPress, onLongClickLabel = "选择提供商")
                    .semantics {
                        contentDescription = if (selectionMode) "选择提供商 ${provider.name}" else "选择 ${provider.name}"
                        if (selectionMode) {
                            role = Role.Checkbox
                            toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) Checkbox(checked = selected, onCheckedChange = null)
                    else ProviderMark(size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(provider.name, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(when {
                            provider.id == com.androidharness.app.llm.HarnessProvider.ID -> "内置"
                            com.androidharness.app.chatgpt.ChatGptProtocol.isProvider(provider.id) -> "ChatGPT"
                            com.androidharness.app.local.LocalModelCatalog.isLocal(provider.id) -> "本机"
                            provider.type == ProviderType.ANTHROPIC -> "Anthropic API"
                            provider.type == ProviderType.GEMINI -> "Gemini API"
                            else -> "API"
                        }, catalogSize?.let { "${selectedModelCount ?: it}/$it 个模型" },
                            if (!provider.enabled) "已停用" else if (active) "使用中" else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!selectionMode) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "${provider.name} 的操作") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("选择") }, onClick = { menu = false; onLongPress() })
                        DropdownMenuItem(text = { Text("上移") }, enabled = canMoveUp, onClick = { menu = false; onMove(-1) })
                        DropdownMenuItem(text = { Text("下移") }, enabled = canMoveDown, onClick = { menu = false; onMove(1) })
                        if (editable) {
                            DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("删除", color = scheme.error) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            if (!selectionMode) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.heightIn(min = 48.dp)
                    .toggleable(provider.enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .semantics { contentDescription = "启用 ${provider.name}" }, verticalAlignment = Alignment.CenterVertically) {
                    Switch(provider.enabled, onCheckedChange = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (provider.enabled) "已启用" else "已停用", style = MaterialTheme.typography.labelMedium)
                }
                if (!provider.enabled) TextButton(onClick = onSelect) { Text("启用并使用") }
                onBrowseModels?.let { browse -> TextButton(onClick = browse) { Text("管理模型") } }
            }
        }
    }
}
