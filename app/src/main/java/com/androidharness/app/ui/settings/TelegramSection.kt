package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.ChatModelState
import com.androidharness.app.telegram.*
import com.androidharness.app.ui.common.SecureDialogEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable internal fun TelegramSection(container: AppContainer) {
    val manager = container.telegram
    val setup by manager.setup.state.collectAsStateWithLifecycle()
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by container.sessions.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val models by remember(setup.sessionId) { container.chatModels.observe(setup.sessionId) }.collectAsStateWithLifecycle(initialValue = null)
    TelegramSettingsContent(manager, projects.map { TelegramChoiceItem(it.id, it.name) },
        sessions.filter { it.projectId == setup.projectId }.map { TelegramChoiceItem(it.id, it.title.ifBlank { "未命名聊天" }) }, models,
        onOpenChat = { setup.sessionId?.let { container.pendingSessionId.tryEmit(it) } },
        onPermissions = { container.pendingSettingsScroll.value = "agent" })
}

@Composable internal fun TelegramSettingsContent(manager: TelegramManager, projects: List<TelegramChoiceItem>, chats: List<TelegramChoiceItem>,
    modelState: ChatModelState? = null, onOpenChat: () -> Unit = {}, onPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val setup by manager.setup.state.collectAsStateWithLifecycle()
    val runtime by manager.runtime.collectAsStateWithLifecycle()
    val pairing by manager.pairing.collectAsStateWithLifecycle()
    val saved by manager.store.state.collectAsStateWithLifecycle()
    val serviceError by manager.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tokenDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var disconnectDialog by remember { mutableStateOf(false) }
    var helpDialog by remember { mutableStateOf(false) }
    var showAllSaved by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    LaunchedEffect(message, failed) {
        if (message != null && !failed) { delay(3_000); message = null }
    }
    val canConfigure = saved.requests.none { it.active || it.status == TelegramRequestStatus.QUEUED ||
        it.status == TelegramRequestStatus.RECEIVED && it.callback == null && !it.text.startsWith('/') }
    fun action(success: String, block: suspend () -> Unit) {
        busy = true; message = null; failed = false
        scope.launch {
            try { block(); message = success }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true; message = com.androidharness.app.tools.SecretRedactor.redact(e.message ?: "无法完成此操作，请重试。") }
            finally { busy = false }
        }
    }
    fun openBot(username: String, code: String? = null) {
        val link = "https://t.me/$username" + (code?.let { "?start=pair_$it" } ?: "")
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { failed = true; message = "请在 Telegram 中打开 @$username。" }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        action("已启用 Telegram。") { manager.enable() }
    }
    fun enable() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else action("已启用 Telegram。") { manager.enable() }
    }
    TelegramPanel {
        val canEnable = setup.ownerId != null && !busy && pairing.challenge == null && manager.store.loadError == null
        val status = when {
            serviceError != null || manager.store.loadError != null -> "需要处理"
            pairing.challenge != null -> "配对中"
            setup.enabled -> runtime.label
            else -> "关闭"
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.weight(1f).semantics { contentDescription = "启用 Telegram" }
                .toggleable(value = setup.enabled, enabled = canEnable, role = Role.Switch,
                    onValueChange = { checked -> if (checked) enable() else action("已暂停 Telegram。") { manager.pause() } }),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("远程控制", style = MaterialTheme.typography.titleSmall)
                    Text(status, style = MaterialTheme.typography.labelMedium,
                        color = if (serviceError != null || manager.store.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Switch(checked = setup.enabled, enabled = canEnable, onCheckedChange = null)
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Outlined.MoreVert, "更多选项") }
                DropdownMenu(moreMenu, { moreMenu = false }) {
                    if (setup.botUsername != null) {
                        DropdownMenuItem(text = { Text("更换令牌") }, enabled = !busy && canConfigure,
                            onClick = { moreMenu = false; token = ""; visible = false; tokenDialog = true })
                        DropdownMenuItem(text = { Text(if (setup.ownerId == null) "配对账号" else "重新配对") },
                            enabled = !busy && canConfigure && pairing.challenge == null,
                            onClick = { moreMenu = false; action("已开始配对。") { manager.beginPairing() } })
                    }
                    DropdownMenuItem(text = { Text("发送测试消息") }, enabled = setup.ownerId != null && !busy,
                        onClick = { moreMenu = false; action("测试消息已发送。") { manager.sendConnectionTest() } })
                    DropdownMenuItem(text = { Text("停止任务") }, enabled = setup.sessionId != null && !busy,
                        onClick = { moreMenu = false; action("任务已停止。") { manager.stopTasks() } })
                    DropdownMenuItem(text = { Text("电池设置") }, onClick = {
                        moreMenu = false
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                    if (setup.ownerId != null) DropdownMenuItem(text = { Text("取消账号配对") }, enabled = !busy,
                        onClick = { moreMenu = false; action("已取消账号配对。") { manager.unpair() } })
                    if (setup.botUsername != null) DropdownMenuItem(text = { Text("断开机器人", color = MaterialTheme.colorScheme.error) }, enabled = !busy,
                        onClick = { moreMenu = false; disconnectDialog = true })
                }
            }
        }
        Text("通过 Telegram 聊天和编辑项目。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (setup.ownerId != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { setup.botUsername?.let { openBot(it) } }, enabled = !busy) { Text("打开机器人") }
                if (!canConfigure) OutlinedButton(enabled = !busy, onClick = { action("任务已停止。") { manager.stopTasks() } }) { Text("停止任务") }
            }
        }
        if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
        (serviceError ?: manager.store.loadError ?: pairing.error ?: if (failed) message else runtime.message)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed || serviceError != null || pairing.error != null || manager.store.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!failed) message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        runtime.action?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    }
    TelegramGroup("连接", Icons.Outlined.AccountCircle) {
        TelegramDetail("机器人", setup.botUsername?.let { "@$it" } ?: "未连接", setup.botUsername != null)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        TelegramDetail("账号", setup.ownerName?.let { "$it · 已配对" } ?: "未配对", setup.ownerId != null)
        if (setup.botUsername == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { token = ""; visible = false; tokenDialog = true }) { Text("连接机器人") }
                TextButton(enabled = !busy, onClick = { openBot("BotFather") }) { Text("创建机器人") }
            }
        } else if (setup.ownerId == null) {
            Button(enabled = !busy && canConfigure && pairing.challenge == null,
                onClick = { action("已开始配对。") { manager.beginPairing() } }) { Text("配对账号") }
        }
    }
    TelegramGroup("工作区", Icons.Outlined.FolderOpen) {
        TelegramChoice("项目", projects.firstOrNull { it.id == setup.projectId }?.label ?: "选择项目", projects, !busy && canConfigure && projects.isNotEmpty()) { id -> action("已选择项目。") { manager.selectDestination(id, null) } }
        TelegramChoice("聊天", chats.firstOrNull { it.id == setup.sessionId }?.label ?: "新建 Telegram 聊天", listOf(TelegramChoiceItem("", "新建 Telegram 聊天")) + chats, !busy && canConfigure && setup.projectId != null) { id -> action("已选择聊天。") { manager.selectDestination(requireNotNull(setup.projectId), id.ifBlank { null }) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = setup.sessionId != null && !busy, onClick = onOpenChat) { Text("打开聊天") }
            TextButton(enabled = setup.projectId != null && !busy && canConfigure, onClick = { action("新聊天已就绪。") { manager.newChat() } }) { Text("新建聊天") }
        }
    }
    val activeChoice = if (modelState?.settings?.planningModelsEnabled == true) {
        (if (setup.mode == "PLAN") modelState.selection.planning else modelState.selection.execution) ?: modelState.selection.active
    } else modelState?.selection?.active
    var providerChoice by remember(activeChoice?.providerId) { mutableStateOf(activeChoice?.providerId) }
    var modelChoices by remember { mutableStateOf(emptyList<TelegramChoiceItem>()) }
    var providers by remember { mutableStateOf(emptyList<TelegramChoiceItem>()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var refreshModels by remember { mutableIntStateOf(0) }
    LaunchedEffect(setup.sessionId, activeChoice?.providerId) {
        try { providers = manager.bridge.providers(); if (providerChoice == null) providerChoice = manager.target()?.providerId }
        catch (e: CancellationException) { throw e } catch (_: Exception) { }
    }
    LaunchedEffect(providerChoice, refreshModels) {
        val id = providerChoice ?: return@LaunchedEffect
        loadingModels = true; modelError = null
        try { modelChoices = manager.bridge.models(id) }
        catch (e: CancellationException) { throw e } catch (e: Exception) { modelError = e.message ?: "无法刷新模型，请重试。" }
        finally { loadingModels = false }
    }
    TelegramGroup("代理", Icons.Outlined.Tune) {
        if (modelState?.settings?.sameModelAcrossChats == true) Text("在聊天间共享", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TelegramChoice("提供商", providers.firstOrNull { it.id == providerChoice }?.label ?: "选择提供商", providers, !busy && canConfigure && setup.sessionId != null) { providerChoice = it }
        val selectedModel = if (providerChoice == activeChoice?.providerId) activeChoice?.model else null
        TelegramChoice("模型", selectedModel ?: "选择模型", modelChoices, !busy && !loadingModels && canConfigure && setup.sessionId != null) { id -> action("已选择模型。") { manager.selectModel(requireNotNull(providerChoice), id) } }
        if (loadingModels) AppLinearProgressIndicator(Modifier.fillMaxWidth())
        modelError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        TextButton(enabled = !loadingModels && !busy, onClick = { refreshModels++ }) { Text("刷新模型") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = setup.mode == "PLAN", enabled = !busy && canConfigure && setup.ownerId != null, onClick = { action("已选择规划模式。") { manager.setMode("PLAN") } }, label = { Text("规划") })
            FilterChip(selected = setup.mode == "ACT", enabled = !busy && canConfigure && setup.ownerId != null, onClick = { action("已选择执行模式。") { manager.setMode("ACT") } }, label = { Text("执行") })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onPermissions).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Security, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text("权限", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(modelState?.settings?.permissionMode?.label ?: "应用默认值", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick = { helpDialog = true }) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("帮助与命令")
    }
    val requests = saved.requests.filter { it.retryable && it.sessionId != null && it.callback == null }.asReversed()
    val deliveries = saved.deliveries.filter { it.status in listOf(TelegramDeliveryStatus.FAILED, TelegramDeliveryStatus.UNCERTAIN) }.asReversed()
    if (requests.isNotEmpty() || deliveries.isNotEmpty()) TelegramGroup("已保存任务", Icons.Outlined.Tune) {
        (if (showAllSaved) requests else requests.take(5)).forEach { request ->
            Text("请求 #${request.id}", style = MaterialTheme.typography.titleSmall)
            Text(request.text.take(160), style = MaterialTheme.typography.bodySmall)
            Text(request.error ?: "已中断", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { action("任务已加入队列。") { manager.retry(request.id) } }) { Text("重试任务") }
            HorizontalDivider()
        }
        (if (showAllSaved) deliveries else deliveries.take(5)).forEach { delivery ->
            Text(delivery.text.take(160), style = MaterialTheme.typography.bodySmall)
            Text(delivery.error ?: "回复发送失败。重新发送前请检查 Telegram。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = !busy, onClick = { action("回复已加入队列。") { manager.resend(delivery.key) } }) { Text("重新发送回复") }
            HorizontalDivider()
        }
        if (requests.size > 5 || deliveries.size > 5) TextButton(onClick = { showAllSaved = !showAllSaved }) { Text(if (showAllSaved) "收起" else "显示所有已保存任务") }
    }
    if (tokenDialog) AlertDialog(onDismissRequest = { if (!busy) { tokenDialog = false; token = "" } }, title = { Text("连接你的机器人") }, text = {
        SecureDialogEffect()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("粘贴从 BotFather 获取的令牌。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(token, { token = it }, singleLine = true, label = { Text("机器人令牌") }, visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "隐藏机器人令牌" else "显示机器人令牌") }
            }, modifier = Modifier.fillMaxWidth())
            if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(enabled = !busy && token.isNotBlank(), onClick = { action("机器人已验证。") { manager.connect(token); tokenDialog = false; token = "" } }) { Text("验证机器人") } }, dismissButton = { TextButton(enabled = !busy, onClick = { tokenDialog = false; token = "" }) { Text("取消") } })
    val challenge = pairing.challenge
    if (challenge != null && pairing.candidate == null) {
        var remaining by remember(challenge) { mutableLongStateOf((challenge.expiresAt - System.currentTimeMillis()).coerceAtLeast(0)) }
        LaunchedEffect(challenge) { while (remaining > 0) { delay(1_000); remaining = (challenge.expiresAt - System.currentTimeMillis()).coerceAtLeast(0) } }
        AlertDialog(onDismissRequest = { manager.cancelPairing() }, title = { Text("配对你的账号") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("打开 @${setup.botUsername} 并点击 Start，或复制此代码。")
                Text("/pair ${challenge.code}", style = MaterialTheme.typography.titleMedium)
                Text("剩余有效期 ${remaining / 60_000}:${((remaining / 1_000) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Telegram 配对", "/pair ${challenge.code}")) }) { Text("复制代码") }
            }
        }, confirmButton = { TextButton(onClick = { setup.botUsername?.let { openBot(it, challenge.code) } }) { Text("打开机器人并配对") } }, dismissButton = { TextButton(onClick = { manager.cancelPairing() }) { Text("取消") } })
    }
    pairing.candidate?.let { candidate -> AlertDialog(onDismissRequest = { manager.cancelPairing() }, title = { Text("配对此账号？") }, text = {
        Text("${candidate.account.name}\n用户 ID ${candidate.account.userId}\n\n允许此账号控制你的聊天？")
    }, confirmButton = { TextButton(enabled = !busy, onClick = { action("账号已配对。") { manager.confirmPairing() } }) { Text("配对账号") } }, dismissButton = { TextButton(onClick = { manager.cancelPairing() }) { Text("取消") } }) }
    if (disconnectDialog) AlertDialog(onDismissRequest = { disconnectDialog = false }, title = { Text("断开机器人？") }, text = { Text("停止 Telegram 并保留聊天和文件？") },
        confirmButton = { TextButton(enabled = !busy, onClick = { disconnectDialog = false; action("机器人已断开。") { manager.disconnect() } }) { Text("断开连接") } }, dismissButton = { TextButton(onClick = { disconnectDialog = false }) { Text("取消") } })
    if (helpDialog) AlertDialog(onDismissRequest = { helpDialog = false }, title = { Text("Telegram 命令") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("发送消息开始聊天，然后使用按钮批准工具操作或回答问题。", style = MaterialTheme.typography.bodySmall)
            listOf("/projects" to "项目", "/chats" to "聊天", "/new" to "新建聊天", "/model" to "模型",
                "/mode plan" to "规划", "/mode act" to "执行", "/status" to "状态", "/stop" to "停止任务",
                "/retry" to "重试 / 恢复", "/pause" to "暂停 Telegram").forEach { (command, title) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(command, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("请保持手机联网；通知中提供暂停和停止按钮。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { helpDialog = false }) { Text("完成") } })
}

@Composable private fun TelegramPanel(content: @Composable ColumnScope.() -> Unit) = SettingsPanel(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
@Composable private fun TelegramGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TelegramPanel(content)
    }
}
@Composable private fun TelegramDetail(title: String, value: String, connected: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (connected) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TelegramChoice(label: String, value: String, options: List<TelegramChoiceItem>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { if (enabled) expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, enabled = enabled, singleLine = true, shape = MaterialTheme.shapes.medium, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).fillMaxWidth())
        ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { expanded = false; onSelect(item.id) }) } }
    }
}
