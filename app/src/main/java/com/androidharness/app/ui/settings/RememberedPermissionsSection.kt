package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.ui.common.SecureScreenEffect

@Composable
internal fun RememberedPermissionsSection(container: AppContainer) {
    val grants by container.runManager.rememberedGrants.collectAsStateWithLifecycle()
    val workspace by container.workspace.current.collectAsStateWithLifecycle(initialValue = null)
    val selected = grants.filter { it.workspacePath == workspace?.displayPath }
    SecureScreenEffect(container, selected.isNotEmpty())
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("已记住的权限", style = MaterialTheme.typography.titleSmall)
            Text("仅适用于当前工作区。“始终允许”持续到当前运行结束。撤销后，需要审批时会再次询问，但无法停止已批准的操作。全自动和完全访问模式仍可跳过这些提示。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selected.isEmpty()) Text("没有已记住的权限", style = MaterialTheme.typography.bodyMedium)
            selected.forEach { grant ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(grant.key.replaceFirst("#", ": "), style = MaterialTheme.typography.bodyMedium)
                        Text("运行 ${grant.sessionId.take(8)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { container.runManager.revokeGrant(grant) }) { Text("撤销") }
                }
            }
        }
    }
}
