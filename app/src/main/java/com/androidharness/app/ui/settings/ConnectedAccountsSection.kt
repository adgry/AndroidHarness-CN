package com.androidharness.app.ui.settings

import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.chatgpt.ChatGptProtocol
import com.androidharness.app.data.AppSettings
import com.androidharness.app.ui.common.openOAuthBrowser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ConnectedAccountsSection(container: AppContainer) {
    val state by container.chatGpt.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSignOut by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf<String?>(null) }
    var selectionError by remember { mutableStateOf<String?>(null) }
    fun update(action: suspend () -> Unit) { scope.launch {
        try { action(); selectionError = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            selectionError = when {
                e is java.io.IOException && e.message?.startsWith("ChatGPT") == true -> e.message
                e.message == "This account did not offer any models." -> "此账号未提供任何模型。"
                e.message == "Reconnect ChatGPT to use this account." -> "请重新连接 ChatGPT 以使用此账号。"
                e.message == "Choose a model offered by this account." -> "请选择此账号提供的模型。"
                else -> "无法更新此账号。请检查网络连接后重试。"
            }
        }
    } }
    ChatGptAccountsPanel(state, settings.activeProviderId, settings.activeModel, selecting,
        error = selectionError ?: state.error,
        onAdd = { container.chatGpt.startSignIn { openOAuthBrowser(context, Uri.parse(it)) } },
        onCancelSignIn = container.chatGpt::cancelSignIn,
        onAutoSwitch = { update { container.chatGpt.setAutoSwitch(it) } },
        onSelect = { account ->
            if (selecting == null) {
                selecting = account.providerId
                update {
                    try {
                        val config = container.chatGpt.selectAccount(account.providerId, settings.activeModel.orEmpty())
                        container.settings.setActiveSelection(config.id, config.model)
                    } finally { selecting = null }
                }
            }
        },
        onFallback = { account, enabled, model -> update { container.chatGpt.setFallback(account.providerId, enabled, model) } },
        onRefresh = { container.chatGpt.refresh(it.providerId) },
        onCheckModels = { container.chatGpt.checkNewerModels(it.providerId) },
        onReconnect = { account -> container.chatGpt.startSignIn(account.providerId) { openOAuthBrowser(context, Uri.parse(it)) } },
        onSignOut = { pendingSignOut = it.providerId },
        onUsage = { openOAuthBrowser(context, Uri.parse(ChatGptProtocol.USAGE_URL)) },
    )
    if (state.showWelcome) AlertDialog(
        onDismissRequest = { update { container.chatGpt.dismissWelcome() } },
        title = { Text("你正在使用 ChatGPT 套餐") },
        text = { Text("AndroidHarness 中符合条件的请求会使用你的 ChatGPT 套餐或点数。可在 ChatGPT 设置中查看用量并管理此应用的访问权限。") },
        confirmButton = { TextButton(onClick = { update { container.chatGpt.dismissWelcome() } }) { Text("知道了") } },
    )
    pendingSignOut?.let { id -> AlertDialog(
        onDismissRequest = { pendingSignOut = null },
        title = { Text("退出此账号？") },
        text = { Text("这会断开该账号与 AndroidHarness 的连接，并将其从自动切换中移除。之后可以重新登录。") },
        confirmButton = { TextButton(onClick = { container.chatGpt.signOut(id); pendingSignOut = null }) { Text("退出登录") } },
        dismissButton = { TextButton(onClick = { pendingSignOut = null }) { Text("取消") } },
    ) }
}
