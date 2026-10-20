package com.androidharness.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.data.BackupEncryption
import com.androidharness.app.data.SettingsBackup
import com.androidharness.app.data.SettingsBackupFile
import com.androidharness.app.data.settingsBackupExportError
import com.androidharness.app.ui.common.SecureDialogEffect
import com.androidharness.app.ui.common.SecureScreenEffect
import com.androidharness.app.ui.common.ThinLinearProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsBackupSection(container: AppContainer) {
    val backup = remember(container) { SettingsBackup(container) }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf<Uri?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var includeKeys by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SettingsBackupFile?>(null) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        destination = it
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source = it }
    fun clear() {
        password = ""
        confirmation = ""
        showPassword = false
        showConfirmation = false
        source = null
        destination = null
        preview = null
        includeKeys = false
    }
    SecureScreenEffect(container, source != null || destination != null || preview != null || busy)
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("设置备份", style = MaterialTheme.typography.titleSmall)
            Text("使用密码加密备份 API 提供商和偏好设置，可选包含 API 密钥与 MCP 配置。不包含本地模型。聊天使用独立备份。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { message = null; exportPicker.launch("androidharness-settings.hbackup") }, enabled = !busy) { Text("导出") }
                OutlinedButton(onClick = { message = null; importPicker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy) { Text("导入") }
            }
            if (busy) ThinLinearProgress(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if ((destination != null || source != null) && preview == null) {
        val exporting = destination != null
        AlertDialog(
            onDismissRequest = { if (!busy) clear() },
            title = { Text(if (exporting) "加密设置" else "解锁备份") },
            text = {
                SecureDialogEffect()
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (exporting) "请至少使用 ${BackupEncryption.MIN_PASSWORD_LENGTH} 个字符。密码遗失后无法找回。应用不会上传任何内容；你选择的文件提供商可能同步加密文件。" else "输入备份密码。在检查并确认前，不会更改设置。")
                    OutlinedTextField(password, { if (it.length <= BackupEncryption.MAX_PASSWORD_LENGTH) password = it }, label = { Text("密码") },
                        singleLine = true, enabled = !busy, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !busy) {
                                Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    if (exporting) {
                        OutlinedTextField(confirmation, { if (it.length <= BackupEncryption.MAX_PASSWORD_LENGTH) confirmation = it }, label = { Text("确认密码") },
                            singleLine = true, enabled = !busy, visualTransformation = if (showConfirmation) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showConfirmation = !showConfirmation }, enabled = !busy) {
                                    Icon(if (showConfirmation) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showConfirmation) "隐藏确认密码" else "显示确认密码")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                        Row {
                            Checkbox(includeKeys, { includeKeys = it }, enabled = !busy)
                            Text("包含凭据与 MCP 服务器", modifier = Modifier.padding(top = 12.dp))
                        }
                        Text("MCP 命令、URL 和请求头可能包含机密，因此仅在包含凭据时备份 MCP 服务器。OAuth 会话、权限、应用锁和工作区访问权限不会恢复。", style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) ThinLinearProgress(Modifier.fillMaxWidth())
                    if (failed) message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && password.length >= BackupEncryption.MIN_PASSWORD_LENGTH && (!exporting || password == confirmation), onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    confirmation = ""
                    showPassword = false
                    showConfirmation = false
                    val target = destination
                    val input = source
                    busy = true
                    failed = false
                    scope.launch {
                        try {
                            if (target != null) {
                                backup.exportTo(target, chars, includeKeys)
                                clear()
                                message = "已导出加密设置，请单独保管密码。"
                            } else if (input != null) {
                                preview = backup.read(input, chars)
                                source = null
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            failed = true
                            message = if (exporting) settingsBackupExportError(e) else "无法解锁备份，请检查密码和文件。"
                        } finally { chars.fill('\u0000'); busy = false }
                    }
                }) { Text(if (exporting) "加密" else "解锁") }
            },
            dismissButton = { TextButton(onClick = { clear() }, enabled = !busy) { Text("取消") } },
        )
    }
    preview?.let { file ->
        AlertDialog(
            onDismissRequest = { if (!busy) clear() },
            title = { Text("恢复设置？") },
            text = {
                SecureDialogEffect()
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${file.providers.count { it.config.id != com.androidharness.app.llm.HarnessProvider.ID }} 个提供商 · ${file.servers.size} 个 MCP 服务器")
                    Text("提供商会作为新连接添加，不覆盖现有连接。偏好设置和所选模型会更改。保留现有应用锁、截屏策略和权限。MCP 服务器默认停用，启用前请检查。")
                    Text("仅恢复可信备份。导入的提供商端点可能接收后续消息，恢复过程不会连接这些端点。")
                    file.providers.filterNot { it.config.id == com.androidharness.app.llm.HarnessProvider.ID }.forEach { entry ->
                        Text("${entry.config.name}\n${entry.config.baseUrl}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("备份包含搜索和语音密钥时，会补充缺失的密钥，保留现有密钥。不包含 GitHub 令牌和 OAuth 会话。", style = MaterialTheme.typography.bodySmall)
                    if (busy) ThinLinearProgress(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        backup.restore(file)
                        failed = false
                        message = "设置已恢复。使用前请检查新连接。"
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) {
                        failed = true
                        message = "恢复未完成。请停止活动任务并检查连接后重试，部分条目可能已添加。"
                    } finally { clear(); busy = false }
                }
            }) { Text("恢复") } },
            dismissButton = { TextButton(onClick = { clear() }, enabled = !busy) { Text("取消") } },
        )
    }
}
