package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.androidharness.app.chatgpt.ChatGptAccount
import com.androidharness.app.chatgpt.ChatGptAccountState

@Composable
internal fun ChatGptAccountsPanel(
    state: ChatGptAccountState, selectedId: String?, selectedModel: String?, selecting: String?, error: String?,
    onAdd: () -> Unit, onCancelSignIn: () -> Unit, onAutoSwitch: (Boolean) -> Unit,
    onSelect: (ChatGptAccount) -> Unit, onFallback: (ChatGptAccount, Boolean, String?) -> Unit,
    onRefresh: (ChatGptAccount) -> Unit, onCheckModels: (ChatGptAccount) -> Unit,
    onReconnect: (ChatGptAccount) -> Unit, onSignOut: (ChatGptAccount) -> Unit, onUsage: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ChatGPT", style = MaterialTheme.typography.titleMedium)
                Text("连接账号并选择要使用的账号。", style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
                SettingsAnchor("使用 ChatGPT 继续") {
                    Button(onClick = onAdd, enabled = !state.signingIn) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.accounts.isEmpty()) "使用 ChatGPT 继续" else "添加账号")
                    }
                }
                if (state.signingIn) {
                    AppLinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("请在浏览器中完成登录，然后返回此处。", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancelSignIn) { Text("取消登录") }
                }
                error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall) }
            }
            if (state.accounts.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                AccountSwitchRow("达到用量限制时自动切换",
                    "使用其他账号继续。可用时保留相同模型，否则使用其备用模型。", state.autoSwitch, onAutoSwitch)
                if (state.autoSwitch && state.accounts.count { it.connected && it.useForAutoSwitch } < 2) {
                    Text("添加或启用其他账号后即可自动切换。", style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
                }
            }
            state.accounts.forEach { account ->
                key(account.providerId) {
                    HorizontalDivider(color = colors.outlineVariant)
                    var menu by remember { mutableStateOf(false) }
                    val selected = selectedId == account.providerId
                    val subtitle = when {
                        selecting == account.providerId -> "正在检查可用模型…"
                        !account.connected -> "已退出登录"
                        account.usageLimited -> if (selected) "已达到用量限制 · 请在聊天中重试" else "已达到用量限制"
                        selected -> account.models.firstOrNull { it.id == selectedModel }?.displayName ?: selectedModel.orEmpty()
                        else -> "${account.models.size} 个可用模型"
                    }
                    Surface(onClick = { if (account.connected) onSelect(account) else onReconnect(account) },
                        enabled = selecting == null && !state.signingIn, color = colors.surface,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = CircleShape, color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    Text(account.label.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(account.label, style = MaterialTheme.typography.titleSmall)
                                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                    color = if (account.usageLimited) colors.error else colors.onSurfaceVariant)
                                if (selected) Text("已选择", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                            }
                            if (selecting == account.providerId) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "管理 ${account.label}") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (account.connected) {
                                        DropdownMenuItem(text = { Text("刷新模型") }, onClick = { menu = false; onRefresh(account) })
                                        DropdownMenuItem(text = { Text("检查新模型") }, enabled = state.checkingModelsFor == null,
                                            onClick = { menu = false; onCheckModels(account) })
                                    }
                                    DropdownMenuItem(text = { Text(if (account.connected) "重新连接" else "重新登录") },
                                        enabled = !state.signingIn, onClick = { menu = false; onReconnect(account) })
                                    if (account.connected) DropdownMenuItem(text = { Text("退出登录", color = colors.error) },
                                        onClick = { menu = false; onSignOut(account) })
                                }
                            }
                        }
                    }
                    if (state.autoSwitch && account.connected) {
                        AccountSwitchRow("参与自动切换", null, account.useForAutoSwitch,
                            { onFallback(account, it, account.fallbackModel) }, inset = true)
                        if (account.useForAutoSwitch) FallbackModelRow(account) { onFallback(account, true, it) }
                    }
                    if (state.checkingModelsFor == account.providerId) {
                        AppLinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        Text("正在检查新模型。小型测试请求会使用此账号的套餐用量。",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp), color = colors.onSurfaceVariant)
                    }
                    state.modelCheckResults[account.providerId]?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            TextButton(onClick = onUsage, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("ChatGPT 中的限制与用量")
            }
        }
    }
}

@Composable
private fun AccountSwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit, inset: Boolean = false) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
        .padding(start = if (inset) 64.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun FallbackModelRow(account: ChatGptAccount, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val choice = account.models.firstOrNull { it.id == account.fallbackModel }
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, bottom = 12.dp)) {
        Surface(onClick = { expanded = true }, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "${account.label} 的备用模型"
            }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("备用模型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(choice?.displayName ?: choice?.id ?: "自动", style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("自动") }, onClick = { expanded = false; onSelect(null) })
            account.models.forEach { model -> DropdownMenuItem(text = { Text(model.displayName ?: model.id) },
                onClick = { expanded = false; onSelect(model.id) }) }
        }
    }
}
