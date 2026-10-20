package com.androidharness.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.androidharness.app.data.env.ShizukuConnectionFailure
import com.androidharness.app.data.env.ShizukuStartupReport
import kotlinx.coroutines.launch

@Composable
internal fun ShizukuFailureDetails(failure: ShizukuConnectionFailure?, report: () -> String,
    startupReport: suspend () -> ShizukuStartupReport, recovered: Boolean = false) {
    if (failure == null) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(failure) { mutableStateOf(false) }
    var copied by remember(failure) { mutableStateOf(false) }
    var collecting by remember(failure) { mutableStateOf(false) }
    var startup by remember(failure) { mutableStateOf<ShizukuStartupReport?>(null) }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Shizuku 连接", text))
        copied = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${if (recovered) "标准启动错误" else "上次连接错误"} · ${failure.reason.name}",
            style = MaterialTheme.typography.labelMedium,
            color = if (recovered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        Text(if (recovered) "兼容模式已恢复连接。" else failure.reason.summary,
            style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (failure.detail.isNotBlank() || startup != null) TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起详情" else "显示详情")
            }
            TextButton(onClick = {
                copy(startup?.report ?: report())
            }) { Text(if (copied) "已复制" else "复制错误详情") }
            TextButton(enabled = !collecting, onClick = {
                collecting = true
                scope.launch {
                    try {
                        val result = startupReport()
                        startup = result
                        copy(result.report)
                    } finally { collecting = false }
                }
            }) { Text(if (collecting) "正在读取启动日志…" else "复制启动日志") }
        }
        startup?.let { Text("${it.status}。报告已复制，反馈此连接问题时请粘贴报告。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (expanded) SelectionContainer {
            Text(startup?.report ?: failure.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
