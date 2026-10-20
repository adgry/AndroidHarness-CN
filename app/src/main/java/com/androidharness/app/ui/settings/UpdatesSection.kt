package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.BuildConfig
import com.androidharness.app.data.update.UpdateManager
import kotlinx.coroutines.launch

@Composable
internal fun UpdatesSection(container: AppContainer) {
    val step by container.updates.step.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val source = container.updates.sourceLabel
    val busy = step is UpdateManager.Step.Checking || step is UpdateManager.Step.Downloading || step is UpdateManager.Step.Installing
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Text("软件更新", style = MaterialTheme.typography.titleMedium)
            }
            Text("当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodyMedium)
            if (source.isBlank()) {
                Text("尚未配置更新源", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("中文版的更新入口已准备好。创建公开 GitHub 仓库后，在构建配置中填写 UPDATE_REPOSITORY，再构建并安装一次，即可接收后续版本更新。无需登录 GitHub。",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("更新源：$source", style = MaterialTheme.typography.bodySmall)
                Text(if (BuildConfig.UPDATE_INCLUDE_PRERELEASES) "更新通道：包含预发布版" else "更新通道：稳定版",
                    style = MaterialTheme.typography.bodySmall)
                Text("启动后自动检查更新，每 24 小时最多一次。发现新版本后，由你确认下载和安装。", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { scope.launch { container.updates.check(manual = true) } }, enabled = !busy && source.isNotBlank()) {
                if (step is UpdateManager.Step.Checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (step is UpdateManager.Step.Checking) "正在检查…" else "检查更新")
            }
            if (step is UpdateManager.Step.UpToDate) {
                Text("未发现适合此设备的新版本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (step is UpdateManager.Step.Error) {
                Text((step as UpdateManager.Step.Error).message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
