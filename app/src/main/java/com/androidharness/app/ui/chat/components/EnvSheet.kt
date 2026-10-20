package com.androidharness.app.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.androidharness.app.ui.settings.PackageManagerSheet
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.data.env.EnvState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.ui.common.PackageInstallProgress
import kotlinx.coroutines.launch

/**
 * Linux environment management from the chat: status, check-missing report,
 * update (installs anything missing/broken), install (fresh) and uninstall
 * (with confirmation). Mirrors the Settings card so the user never has to
 * leave the conversation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var showPackagesSheet by remember { mutableStateOf(false) }
    val linuxEnv = container.linuxEnv
    val envState = linuxEnv.state.collectAsStateWithLifecycle().value

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Linux 环境", style = MaterialTheme.typography.titleMedium)
            Text(
                when (envState) {
                    is EnvState.Ready -> "已安装"
                    is EnvState.Preparing -> "正在解析软件包…"
                    is EnvState.Downloading -> "正在下载软件包…"
                    is EnvState.Installing -> "正在安装软件包…"
                    is EnvState.Failed -> "失败：${envState.message}"
                    EnvState.NotInstalled -> "未安装"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (envState is EnvState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (envState) {
                is EnvState.Ready -> {
                    val installed = linuxEnv.installedPackages()
                    if (installed.isNotEmpty()) {
                        Text(
                            "软件包（${installed.size}）： ${installed.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    checkResult?.let { report ->
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (report.startsWith("全部可用")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTonalButton(onClick = { showPackagesSheet = true }) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("软件包")
                        }
                        OutlinedButton(
                            enabled = !checking,
                            onClick = {
                                checking = true
                                scope.launch {
                                    checkResult = linuxEnv.checkMissing()
                                    checking = false
                                }
                            },
                        ) { Text(if (checking) "正在检查…" else "检查缺失项") }
                        if (checkResult != null && !checkResult!!.startsWith("全部可用")) {
                            Button(onClick = {
                                checkResult = null
                                scope.launch { linuxEnv.updateEnvironment() }
                            }) { Text("更新") }
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmUninstall = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("卸载") }
                }
                is EnvState.NotInstalled, is EnvState.Failed -> {
                    Text(
                        if (envState is EnvState.Failed) "请解决问题（通常是网络问题）后重新安装。"
                        else "将 bash、git、python 3、pip、node.js 和 npm 安装到私有存储。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { scope.launch { linuxEnv.install(linuxEnv.fullPackages) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (envState is EnvState.Failed) "重试安装" else "安装完整环境") }
                }
                is EnvState.Downloading, is EnvState.Installing, is EnvState.Preparing -> {
                    PackageInstallProgress(envState)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("卸载 Linux 环境？") },
            text = { Text("这会移除所有本地工作区共用的工具链（bash、git、python、node、npm 和所有已下载软件包）。重新安装前，代理将使用 toybox sh。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    scope.launch {
                        try { container.storage.uninstallToolchain() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { checkResult = e.message }
                    }
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("取消") }
            },
        )
    }

    if (showPackagesSheet) {
        PackageManagerSheet(
            container = container,
            onDismiss = { showPackagesSheet = false },
        )
    }
}
