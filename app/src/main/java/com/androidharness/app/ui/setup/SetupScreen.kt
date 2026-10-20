package com.androidharness.app.ui.setup

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.env.EnvState
import com.androidharness.app.data.env.ShizukuState
import com.androidharness.app.ui.common.HarnessMark
import com.androidharness.app.ui.common.SystemGrants
import com.androidharness.app.ui.common.ThinLinearProgress
import com.androidharness.app.ui.common.rememberStorageAccess
import com.androidharness.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * First-run setup requires storage access. Notifications and additional tools are optional.
 */
@Composable
fun SetupScreen(
    container: AppContainer,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val success = LocalStatusColors.current.success
    val lifecycleOwner = LocalLifecycleOwner.current

    val shizukuState by container.shizuku.state.collectAsStateWithLifecycle()
    val serviceState by container.shizuku.serviceState.collectAsStateWithLifecycle()
    val envState by container.linuxEnv.state.collectAsStateWithLifecycle()

    val storageAccess = rememberStorageAccess()
    val storageGranted = storageAccess.granted
    var notifGranted by remember {
        mutableStateOf(SystemGrants.isPostNotificationsGranted(context))
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }
    var batteryExempt by remember { mutableStateOf(SystemGrants.isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifGranted = SystemGrants.isPostNotificationsGranted(context)
                batteryExempt = SystemGrants.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun finish() {
        if (!storageGranted) return
        scope.launch {
            container.settings.setOnboardingDone(true)
            onFinish()
        }
    }

    // ---- Step completion -------------------------------------------------
    val shizukuDone = shizukuState == ShizukuState.GRANTED && serviceState.isReady
    val envDone = envState is EnvState.Ready
    val completed = listOf(storageGranted, notifGranted, shizukuDone, envDone, batteryExempt).count { it }

    Scaffold(containerColor = scheme.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                HarnessMark(size = 44.dp)
                Spacer(Modifier.size(2.dp))
                Text("欢迎使用 AndroidHarness", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "授予存储访问权限即可开始编写代码。通知和其他工具均为可选。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "已完成 $completed / 5 项",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = scheme.onSurfaceVariant,
                    )
                    ThinLinearProgress(
                        progress = { completed / 5f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(2.dp))

                // -- 1. Storage access (required) --------------------------
                SetupStep(
                    icon = { Icon(Icons.Outlined.SdStorage, null, Modifier.size(16.dp), scheme.onSurfaceVariant) },
                    title = "存储访问",
                    status = if (storageGranted) "已授予设备存储访问权限"
                             else "必需：允许访问设备文件和文件夹",
                    complete = storageGranted,
                    optional = false,
                ) {
                    if (!storageGranted) {
                        Button(onClick = storageAccess.request) { Text("授权") }
                    }
                }

                // -- 2. Notifications (optional) ---------------------------
                SetupStep(
                    icon = { Icon(Icons.Outlined.Notifications, null, Modifier.size(16.dp), scheme.onSurfaceVariant) },
                    title = "通知",
                    status = if (notifGranted) "运行进度和审批请求将通过通知提醒"
                             else "在应用外接收进度和审批请求",
                    complete = notifGranted,
                    optional = true,
                ) {
                    if (!notifGranted) {
                        Button(onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else notifGranted = true
                        }) { Text("允许") }
                    }
                }

                // -- 3. Shizuku (optional) ---------------------------------
                SetupStep(
                    icon = { Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), scheme.onSurfaceVariant) },
                    title = "Shizuku",
                    status = when (shizukuState) {
                        ShizukuState.NOT_INSTALLED -> "可选：完整 shell 工具链和后台服务器"
                        ShizukuState.NOT_RUNNING -> "打开 Shizuku 应用并启动服务"
                        ShizukuState.RUNNING_NO_PERMISSION -> "授予权限以使用 ADB 级别的命令"
                        ShizukuState.GRANTED ->
                            if (serviceState.isReady)
                                "已连接：特权 shell 就绪"
                            else "已授权：正在等待用户服务…"
                    },
                    complete = shizukuDone,
                    optional = true,
                ) {
                    when (shizukuState) {
                        ShizukuState.RUNNING_NO_PERMISSION ->
                            Button(onClick = { container.shizuku.requestPermission() }) { Text("授权") }
                        ShizukuState.NOT_INSTALLED, ShizukuState.NOT_RUNNING ->
                            OutlinedButton(onClick = { container.shizuku.refresh() }) { Text("刷新") }
                        ShizukuState.GRANTED -> {}
                    }
                }

                // -- 4. Linux environment (optional) -----------------------
                SetupStep(
                    icon = { Icon(Icons.Outlined.Terminal, null, Modifier.size(16.dp), scheme.onSurfaceVariant) },
                    title = "Linux 环境",
                    status = when (val s = envState) {
                        EnvState.Ready -> "就绪：可使用 bash、git、python、node 和 npm 运行命令"
                        is EnvState.Downloading, is EnvState.Installing, EnvState.Preparing -> ""
                        is EnvState.Failed -> s.message.take(80)
                        EnvState.NotInstalled -> "可选：安装 bash、git、python、pip、node、npm 以运行命令"
                    },
                    complete = envDone,
                    optional = true,
                    busy = envState is EnvState.Downloading || envState is EnvState.Installing || envState is EnvState.Preparing,
                    progress = { com.androidharness.app.ui.common.PackageInstallProgress(envState) },
                ) {
                    when (envState) {
                        EnvState.NotInstalled, is EnvState.Failed ->
                            OutlinedButton(onClick = {
                                scope.launch { container.linuxEnv.install(container.linuxEnv.fullPackages) }
                            }) { Text(if (envState is EnvState.Failed) "重试" else "安装") }
                        else -> {}
                    }
                }

                // -- 5. Battery optimization (optional) --------------------
                SetupStep(
                    icon = {
                        Icon(Icons.Outlined.BatteryChargingFull, null, Modifier.size(16.dp), scheme.onSurfaceVariant)
                    },
                    title = "电池优化",
                    status = if (batteryExempt) "已豁免：熄屏后长时间任务仍可运行"
                             else "可选：避免设备厂商的后台限制中断长时间任务",
                    complete = batteryExempt,
                    optional = true,
                ) {
                    if (!batteryExempt) {
                        OutlinedButton(onClick = {
                            SystemGrants.requestBatteryExemption(context)
                            batteryExempt = SystemGrants.isIgnoringBatteryOptimizations(context)
                        }) { Text("调整") }
                    }
                }
            }

            // Footer actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (storageGranted) "准备就绪" else "授予存储访问权限后即可开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (storageGranted) success else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Button(onClick = ::finish, enabled = storageGranted) { Text("开始使用") }
            }
        }
    }

}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SetupStep(
    icon: @Composable () -> Unit,
    title: String,
    status: String,
    complete: Boolean,
    optional: Boolean,
    busy: Boolean = false,
    progress: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val success = LocalStatusColors.current.success
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(9.dp)),
            ) { icon() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
                    if (optional) {
                        Text(
                            "可选",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (status.isNotEmpty()) Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (complete) success else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (busy) {
                    if (progress != null) progress() else ThinLinearProgress(modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(8.dp))
            if (complete) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "完成",
                    tint = success,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box { trailing() }
            }
        }
    }
}
