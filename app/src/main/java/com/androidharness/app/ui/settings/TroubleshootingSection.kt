package com.androidharness.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.BuildConfig
import com.androidharness.app.data.AppSettings
import com.androidharness.app.data.env.EnvState
import com.androidharness.app.data.env.ShizukuState
import com.androidharness.app.data.env.UserServiceState
import com.androidharness.app.llm.ModelCatalog
import com.androidharness.app.llm.effectiveApiKey
import com.androidharness.app.ui.common.SystemGrants
import com.androidharness.app.ui.common.rememberStorageAccess
import com.androidharness.app.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Composable
internal fun TroubleshootingSection(container: AppContainer, onOpen: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = rememberStorageAccess()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val providers by container.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val project by container.workspace.currentProject.collectAsStateWithLifecycle(initialValue = null)
    val env by container.linuxEnv.state.collectAsStateWithLifecycle()
    val shizuku by container.shizuku.state.collectAsStateWithLifecycle()
    val service by container.shizuku.serviceState.collectAsStateWithLifecycle()
    val connectionFailure by container.shizuku.connectionFailure.collectAsStateWithLifecycle()
    val ssh by container.sshConnections.statuses.collectAsStateWithLifecycle()
    val servers by container.mcp.servers.collectAsStateWithLifecycle()
    val tools by container.mcp.statuses.collectAsStateWithLifecycle()
    val tampered by container.mcp.configTampered.collectAsStateWithLifecycle()
    val provider = providers.firstOrNull { it.id == settings.activeProviderId }
    var catalog by remember(provider) { mutableStateOf(CatalogCheck.NOT_CHECKED) }
    var folder by remember(project) { mutableStateOf(DiagnosticState.NOT_CHECKED) }
    var revision by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf((SystemGrants.isPostNotificationsGranted(context) && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())) }
    val owner = LocalLifecycleOwner.current
    fun refresh() {
        storage.refresh(); container.shizuku.refresh()
        notifications = (SystemGrants.isPostNotificationsGranted(context) && androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
        revision++
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    suspend fun checkFolder(): DiagnosticState = withContext(Dispatchers.IO) {
        try {
            val selected = project ?: return@withContext DiagnosticState.ISSUE
            val fs = container.workspace.fsFor(selected)
            val node = fs.resolve(".")
            if (!node.exists || !node.isDirectory || fs.shellRoot?.canRead() == false) DiagnosticState.ISSUE
            else { node.list(); DiagnosticState.READY }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { DiagnosticState.ISSUE }
    }
    LaunchedEffect(project, revision, storage.granted) {
        folder = if (project?.kind == WorkspaceManager.KIND_SSH) DiagnosticState.NOT_CHECKED else {
            folder = DiagnosticState.CHECKING
            checkFolder()
        }
    }
    val providerState = when {
        provider == null -> DiagnosticState.ISSUE
        catalog == CatalogCheck.CHECKING -> DiagnosticState.CHECKING
        catalog == CatalogCheck.REACHABLE -> DiagnosticState.REACHABLE
        catalog == CatalogCheck.NOT_CHECKED -> DiagnosticState.NOT_CHECKED
        else -> DiagnosticState.ISSUE
    }
    val linuxState = when (env) {
        EnvState.NotInstalled -> DiagnosticState.OFF
        EnvState.Ready -> DiagnosticState.INSTALLED
        is EnvState.Failed -> DiagnosticState.ISSUE
        else -> DiagnosticState.CHECKING
    }
    val shizukuState = shizukuDiagnostic(shizuku == ShizukuState.GRANTED,
        service.isReady, service == UserServiceState.BINDING, service == UserServiceState.BIND_FAILED)
    val enabledServers = servers.filter { it.enabled }
    val mcpState = when {
        tampered -> DiagnosticState.ISSUE
        enabledServers.isEmpty() -> DiagnosticState.OFF
        enabledServers.any { tools[it.name]?.state in setOf("failed", "auth") } -> DiagnosticState.ISSUE
        enabledServers.any { tools[it.name]?.state == "connecting" } -> DiagnosticState.CHECKING
        enabledServers.all { tools[it.name]?.state == "connected" } -> DiagnosticState.READY
        else -> DiagnosticState.NOT_CHECKED
    }
    val sshState = when {
        ssh.isEmpty() -> DiagnosticState.NOT_CHECKED
        ssh.values.any { it.uncertain } -> DiagnosticState.ISSUE
        ssh.values.any { it.connected } -> DiagnosticState.READY
        else -> DiagnosticState.OFF
    }
    val states = linkedMapOf(DiagnosticArea.PROVIDER to providerState, DiagnosticArea.STORAGE to
        if (storage.granted) DiagnosticState.READY else DiagnosticState.ISSUE,
        DiagnosticArea.WORKSPACE to folder, DiagnosticArea.LINUX to linuxState, DiagnosticArea.SHIZUKU to shizukuState,
        DiagnosticArea.SSH to sshState, DiagnosticArea.MCP to mcpState,
        DiagnosticArea.NOTIFICATIONS to if (notifications) DiagnosticState.READY else DiagnosticState.OFF)
    val report = diagnosticReport(BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, states, providers.size, enabledServers.size)
    LaunchedEffect(report) { copied = false }
    Text("检查默认提供商和工作区。可选功能可以保持关闭。",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = ::refresh) { Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("刷新状态") }
        OutlinedButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("故障排查", report))
            copied = true
        }) { Text(if (copied) "已复制" else "复制报告") }
        TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report)
        }, "分享故障排查报告")) }) { Text("分享报告") }
    }
    Text("报告不包含凭据、地址、文件路径和聊天。", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    DiagnosticCard(DiagnosticArea.PROVIDER.label, providerState,
        if (provider == null) "选择提供商以使用助手。" else "${provider.name}\n${catalog.detail}") {
        val local = provider?.let { com.androidharness.app.local.LocalModelCatalog.isLocal(it.id) } == true
        if (provider != null && !local) TextButton(enabled = catalog != CatalogCheck.CHECKING, onClick = {
            scope.launch {
                catalog = CatalogCheck.CHECKING
                try {
                    val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
                    val result = withTimeout(25_000) { ModelCatalog.listModels(provider,
                        provider.effectiveApiKey(container.providers.apiKey(provider.id)), client) }
                    catalog = when (result) {
                        is ModelCatalog.Result.Models -> if (result.models.isEmpty()) CatalogCheck.EMPTY else CatalogCheck.REACHABLE
                        is ModelCatalog.Result.Failed -> catalogFailure(result.message)
                    }
                } catch (_: TimeoutCancellationException) { catalog = CatalogCheck.TIMEOUT }
                catch (e: kotlinx.coroutines.CancellationException) { catalog = CatalogCheck.NOT_CHECKED; throw e }
                catch (_: Exception) { catalog = CatalogCheck.FAILED }
            }
        }) { Text("检查模型") }
        TextButton(onClick = { onOpen(if (local) SettingsPage.LOCAL_MODELS else SettingsPage.MODELS) }) { Text("管理提供商") }
    }
    DiagnosticCard("存储访问 · 必需", states.getValue(DiagnosticArea.STORAGE),
        if (storage.granted) "应用已获得存储权限。" else "授予存储访问权限以完成设置并使用设备文件夹。") {
        if (!storage.granted) TextButton(onClick = storage.request) { Text("授予访问权限") }
    }
    DiagnosticCard(DiagnosticArea.WORKSPACE.label, folder, (project?.name?.let { "$it\n" } ?: "") + when (folder) {
        DiagnosticState.READY -> "文件夹可读取，尚未测试写入权限。"
        DiagnosticState.ISSUE -> "文件夹不可用。请重新选择或检查连接。"
        DiagnosticState.CHECKING -> "正在检查文件夹访问权限…"
        else -> "尚未检查远程文件夹访问权限。"
    }) {
        if (project?.kind == WorkspaceManager.KIND_SSH) TextButton(enabled = folder != DiagnosticState.CHECKING, onClick = {
            scope.launch { folder = DiagnosticState.CHECKING; folder = checkFolder() }
        }) { Text("检查文件夹") }
        TextButton(onClick = { onOpen(SettingsPage.WORKSPACE) }) { Text("打开工作区") }
    }
    DiagnosticCard("Linux 工具 · 可选", linuxState, when (linuxState) {
        DiagnosticState.INSTALLED -> "工具链已安装，此处尚未测试各个命令。"
        DiagnosticState.ISSUE -> "安装需要处理。请打开终端设置进行修复或重试。"
        DiagnosticState.CHECKING -> "正在安装。"
        else -> "安装 Linux 工具以运行本地命令。"
    }) { TextButton(onClick = { onOpen(SettingsPage.ENVIRONMENT) }) { Text("打开终端设置") } }
    DiagnosticCard("Shizuku · 可选", shizukuState, when {
        service == UserServiceState.COMPAT_READY -> "已授权，兼容模式下的命令已就绪。"
        shizukuState == DiagnosticState.READY -> "已授权，命令服务已就绪。"
        shizuku == ShizukuState.RUNNING_NO_PERMISSION -> "Shizuku 正在运行。请在终端设置中授权。"
        shizukuState == DiagnosticState.CHECKING -> "正在连接命令服务…"
        shizukuState == DiagnosticState.ISSUE -> "命令服务连接失败。请在终端设置中重新连接。"
        shizuku == ShizukuState.GRANTED -> "已授权，命令服务尚未连接。"
        else -> "启动 Shizuku 以运行具有系统访问权限的命令。"
    }, extraDetails = { ShizukuFailureDetails(connectionFailure, container.shizuku::connectionReport,
        container.shizuku::startupReport, recovered = service == UserServiceState.COMPAT_READY) }) {
        TextButton(onClick = { onOpen(SettingsPage.ENVIRONMENT) }) { Text("打开 Shizuku 设置") }
    }
    DiagnosticCard("SSH 工作区 · 可选", sshState, when (sshState) {
        DiagnosticState.READY -> "有 ${ssh.values.count { it.connected }} 个连接处于活动状态。请单独检查文件夹以确认访问权限。"
        DiagnosticState.ISSUE -> "有连接需要处理。请在“工作区”中检查。"
        else -> "没有活动的 SSH 连接。需要时可在“工作区”中连接。"
    }) { TextButton(onClick = { onOpen(SettingsPage.WORKSPACE) }) { Text("管理连接") } }
    DiagnosticCard("已连接工具 · 可选", mcpState, when (mcpState) {
        DiagnosticState.READY -> "全部 ${enabledServers.size} 个已启用服务器均已连接。此处尚未测试工具执行。"
        DiagnosticState.ISSUE -> if (tampered) "服务器配置未通过完整性校验。请在“已连接工具”中检查并保存。" else "有服务器需要登录、重新连接或检查配置。"
        DiagnosticState.OFF -> "未启用 MCP 服务器。"
        DiagnosticState.CHECKING -> "有服务器正在连接…"
        else -> "部分已启用服务器尚未连接。"
    }) { TextButton(onClick = { onOpen(SettingsPage.MCP) }) { Text("管理工具") } }
    DiagnosticCard("通知 · 可选", states.getValue(DiagnosticArea.NOTIFICATIONS),
        if (notifications) "Android 已允许通知。" else "关闭通知后仍可使用应用。") {
        TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("通知设置") }
    }
}

@Composable
private fun DiagnosticCard(title: String, state: DiagnosticState, detail: String,
    extraDetails: @Composable () -> Unit = {}, actions: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = when (state) {
        DiagnosticState.ISSUE -> scheme.error
        DiagnosticState.READY, DiagnosticState.REACHABLE -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(when (state) {
                    DiagnosticState.ISSUE -> Icons.Outlined.ErrorOutline
                    DiagnosticState.READY, DiagnosticState.REACHABLE -> Icons.Outlined.CheckCircleOutline
                    else -> Icons.Outlined.Info
                }, null, Modifier.size(18.dp), tint = tint)
                Text(state.label, style = MaterialTheme.typography.labelMedium, color = tint)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            extraDetails()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
    }
}
