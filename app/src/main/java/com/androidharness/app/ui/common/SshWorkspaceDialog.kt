package com.androidharness.app.ui.common

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.workspace.*
import com.jcraft.jsch.ChannelSftp
import kotlinx.coroutines.*

@Composable
fun SshWorkspaceDialog(container: AppContainer, existing: SshFs? = null, onSelected: (String) -> Unit = {}, activateWorkspace: Boolean = true, onDismiss: () -> Unit) {
    SecureScreenEffect(container)
    val initial = remember { existing?.let { runCatching { container.sshConnections.load(it.location.connectionId) }.getOrNull() }
        ?: SshConnection(id = existing?.location?.connectionId ?: java.util.UUID.randomUUID().toString()) }
    var config by remember { mutableStateOf(initial) }
    var keyMode by remember { mutableStateOf(initial.privateKey.isNotBlank()) }
    var showSetup by remember { mutableStateOf(false) }
    var observed by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }
    var folder by remember { mutableStateOf(existing?.root ?: "") }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun edit(next: SshConnection) { config = next; observed = null; verified = false; error = null }
    fun browse(path: String?): Job {
        val candidate = config
        busy = true
        return scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    container.sshConnections.preview(candidate) { s ->
                        val absolute = s.realpath(path?.takeIf { it.isNotBlank() } ?: s.home)
                        require(s.stat(absolute).isDir) { "选择目录" }
                        absolute to s.ls(absolute).filterIsInstance<ChannelSftp.LsEntry>()
                            .filter { it.filename != "." && it.filename != ".." && it.attrs.isDir && !it.attrs.isLink }
                            .map { it.filename }.sorted()
                    }
                }
                folder = result.first; folders = result.second; verified = true; observed = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "通过 SSH 连接" else "连接设置") },
        text = {
            SecureDialogEffect()
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!verified) {
                    Row {
                        FilterChip(selected = config.termux, onClick = { edit(config.copy(termux = true, host = "127.0.0.1", port = 8022)) },
                            enabled = !busy, label = { Text("Termux") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = !config.termux, onClick = { edit(config.copy(termux = false, host = "", port = 22)) },
                            enabled = !busy, label = { Text("另一台电脑") })
                    }
                    if (config.termux) {
                        TextButton(onClick = { showSetup = !showSetup }) { Text("设置 Termux") }
                        if (showSetup) SelectionContainer { Text("pkg install openssh git\npasswd\nwhoami\nsshd\n\n使用以下命令检查指纹：\nfor key in \$PREFIX/etc/ssh/ssh_host_*_key.pub; do ssh-keygen -lf \"\$key\" -E sha256; done\n\n项目可保存在 Termux 主目录中。请在 Termux 中配置 Git 身份和 GitHub 身份验证。") }
                    }
                    OutlinedTextField(config.host, { edit(config.copy(host = it.trim())) }, label = { Text("主机") }, enabled = !busy && !config.termux, singleLine = true)
                    var port by remember(config.termux) { mutableStateOf(config.port.toString()) }
                    OutlinedTextField(port, { port = it; edit(config.copy(port = it.toIntOrNull() ?: 0)) }, label = { Text("端口") }, enabled = !busy, singleLine = true)
                    OutlinedTextField(config.username, { edit(config.copy(username = it.trim())) }, label = { Text("用户名") }, enabled = !busy, singleLine = true)
                    Row {
                        FilterChip(!keyMode, { keyMode = false; edit(config.copy(privateKey = "", passphrase = "")) }, enabled = !busy, label = { Text("密码") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(keyMode, { keyMode = true; edit(config.copy(password = "")) }, enabled = !busy, label = { Text("SSH 密钥") })
                    }
                    if (keyMode) {
                        OutlinedTextField(config.privateKey, { edit(config.copy(privateKey = it)) }, label = { Text("粘贴私钥") },
                            visualTransformation = PasswordVisualTransformation(), enabled = !busy, minLines = 3, maxLines = 4)
                        OutlinedTextField(config.passphrase, { edit(config.copy(passphrase = it)) }, label = { Text("密钥口令（可选）") },
                            visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
                    } else OutlinedTextField(config.password, { edit(config.copy(password = it)) }, label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
                    observed?.let { pin ->
                        Text(if (initial.fingerprint.isNotBlank() && pin != initial.fingerprint) "主机密钥已更改。接受前，请在服务器上核对新指纹。" else "信任前，请核对此指纹与服务器是否一致。")
                        SelectionContainer { Text(pin) }
                        Button(enabled = !busy, onClick = { config = config.copy(fingerprint = pin); browse(existing?.root) }) { Text("信任并继续") }
                    }
                } else {
                    Text("选择项目文件夹")
                    SelectionContainer { Text(folder) }
                    if (existing == null && folder != "/") TextButton(enabled = !busy, onClick = { browse(folder.substringBeforeLast('/').ifEmpty { "/" }) }) { Text("↑ 上级文件夹") }
                    if (existing == null) folders.forEach { name -> TextButton(enabled = !busy, onClick = { browse(folder.trimEnd('/') + "/" + name) }, modifier = Modifier.fillMaxWidth()) { Text(name) } }
                    if (folders.isEmpty()) Text("没有子文件夹，可使用此文件夹。")
                    TextButton(enabled = !busy, onClick = { verified = false }) { Text("编辑连接") }
                }
                if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && observed == null, onClick = {
                error = null
                if (verified) {
                    busy = true
                    scope.launch {
                        try {
                            check(existing == null || (container.runManager.runningSessionIds.value.isEmpty() && !container.terminal.state.value.busy)) { "请等待命令运行完成后再更改连接" }
                            val selectedId = withContext(Dispatchers.IO) {
                                val id = if (existing == null) container.workspace.addSshProject(config, folder, activateWorkspace).id
                                else { container.sshConnections.save(config); null }
                                container.sshConnections.reconnect(config.id)
                                id
                            }
                            selectedId?.let(onSelected)
                            onDismiss()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                } else {
                    busy = true
                    scope.launch {
                        try {
                            val pin = withContext(Dispatchers.IO) { container.sshConnections.probe(config) }
                            if (pin == initial.fingerprint && pin.isNotBlank()) {
                                config = config.copy(fingerprint = pin); browse(existing?.root).join()
                            } else observed = pin
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { busy = false }
                    }
                }
            }) { Text(if (verified) if (existing == null) "使用此工作区" else "保存连接" else "连接") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
