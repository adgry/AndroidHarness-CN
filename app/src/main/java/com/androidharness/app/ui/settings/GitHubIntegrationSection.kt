package com.androidharness.app.ui.settings

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.ui.github.*
import com.androidharness.app.ui.common.SecureScreenEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GitHubIntegrationSection(container: AppContainer) {
    val state by container.github.state.collectAsStateWithLifecycle()
    val project by container.workspace.currentProject.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var patForm by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var workflow by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var logout by remember { mutableStateOf(false) }
    SecureScreenEffect(container, patForm)

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "无法打开浏览器。请在浏览器中打开 $url。" }
    }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.login?.takeIf { it.isNotEmpty() }?.let { "已连接：$it" } ?: "连接 GitHub",
                style = MaterialTheme.typography.titleMedium)
            Text(if (state.login != null) {
                if (state.oauth) "GitHub 登录 · 凭据安全存储在此设备上"
                else "个人访问令牌 · 凭据安全存储在此设备上"
            } else "登录后可导入私有仓库并发布更改。", style = MaterialTheme.typography.bodySmall)

            if (state.code != null) {
                val code = requireNotNull(state.code)
                Text(code.userCode, style = MaterialTheme.typography.headlineSmall)
                Text("复制此代码并在 GitHub 输入。批准访问后返回此处。")
                Button(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("GitHub 登录代码", code.userCode))
                    open(code.url)
                }, modifier = Modifier.fillMaxWidth()) { Text("复制代码并打开 GitHub") }
                TextButton(onClick = { container.github.cancelSignIn() }, modifier = Modifier.fillMaxWidth()) { Text("取消登录") }
            } else if (!patForm && !state.busy) {
                Button(onClick = { message = null; container.github.startSignIn(workflow) },
                    enabled = !working && container.github.clientId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.login == null) "使用 GitHub 登录" else "登录其他账号")
                }
                if (container.github.clientId.isBlank()) Text("此版本无法使用 GitHub 登录。你可以使用个人访问令牌。",
                    style = MaterialTheme.typography.bodySmall)
                Row {
                    Checkbox(workflow, onCheckedChange = { workflow = it })
                    Text("允许编辑 GitHub Actions 工作流", modifier = Modifier.weight(1f).padding(top = 12.dp))
                }
                OutlinedButton(onClick = { patForm = true; token = ""; message = null },
                    enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("使用个人访问令牌") }
            }
            if (state.busy || working) AppLinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (patForm) {
                Text("使用细粒度令牌时，请选择目标仓库并授予 Contents 读写权限。修改工作流还需 Workflows 权限。可能需要组织批准或 SSO 授权。",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { open("https://github.com/settings/tokens?type=beta") },
                    modifier = Modifier.fillMaxWidth()) { Text("在 GitHub 创建令牌") }
                OutlinedTextField(token, onValueChange = { token = it }, label = { Text("个人访问令牌") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !working,
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    working = true
                    scope.launch {
                        try { container.github.connectPat(token); token = ""; patForm = false; message = null }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = container.github.safeMessage(e) }
                        finally { working = false }
                    }
                }, enabled = !working && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("验证并连接") }
                TextButton(onClick = { token = ""; patForm = false }, enabled = !working,
                    modifier = Modifier.fillMaxWidth()) { Text("取消") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.login != null) TextButton(onClick = { logout = true }, enabled = !working && !state.busy,
                modifier = Modifier.fillMaxWidth()) { Text("断开 GitHub") }
        }
    }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("仓库", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { importing = true }, modifier = Modifier.fillMaxWidth()) { Text("从 GitHub 导入") }
            Text(project?.let { "当前工作区：${it.name}" } ?: "选择工作区以发布更改。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { project?.let { publishing = it.id to it.name } },
                enabled = project != null, modifier = Modifier.fillMaxWidth()) { Text("提交并推送") }
            Text("发布前请检查仓库、分支和文件。可在此保存推送预设，在自动化中重复使用。",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (importing) GitHubImportDialog(container, onDismiss = { importing = false })
    publishing?.let { (id, name) -> GitHubPublishDialog(container, id, name, onDismiss = { publishing = null }) }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("断开 GitHub？") },
        text = { Text("从此设备移除已保存的连接和仓库凭据。保留仓库和本地提交。") },
        confirmButton = { TextButton(onClick = {
            logout = false; working = true
            scope.launch {
                try { container.github.disconnect() }
                catch (e: Exception) { message = container.github.safeMessage(e) }
                finally { working = false }
            }
        }) { Text("断开连接") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
}
