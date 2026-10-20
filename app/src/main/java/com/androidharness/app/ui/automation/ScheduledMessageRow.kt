package com.androidharness.app.ui.automation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.automation.AutomationConnection
import com.androidharness.app.automation.AutomationStatus
import com.androidharness.app.automation.AutomationTask
import java.text.DateFormat
import java.util.Date

internal fun scheduleStatus(task: AutomationTask, connection: AutomationConnection, now: Long): String = when {
    task.lastStatus == AutomationStatus.RUNNING -> "运行中"
    task.lastStatus == AutomationStatus.BLOCKED || task.lastStatus == AutomationStatus.FAILED -> "需要你处理"
    !task.enabled -> when (task.lastStatus) {
        AutomationStatus.COMPLETED, AutomationStatus.PASSED -> "已完成"
        AutomationStatus.CANCELLED -> "已取消"
        else -> "已暂停"
    }
    task.lastStatus == AutomationStatus.QUEUED || task.nextRunAt?.let { it <= now } == true ->
        if (!connection.allows(task)) connection.waitingMessage(task) else task.lastMessage?.takeIf { it.startsWith("正在等待此聊天") || it.startsWith("Waiting for this chat") } ?: "等待开始"
    task.nextRunAt != null -> "计划于 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(task.nextRunAt))}"
    else -> "就绪"
}

@Composable
internal fun ScheduledMessageRow(tasks: List<AutomationTask>, connection: AutomationConnection, now: Long, onOpen: () -> Unit) {
    if (tasks.isEmpty()) return
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(if (tasks.size == 1) scheduleStatus(tasks.first(), connection, now) else "${tasks.size} 条定时消息",
                    style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tasks.size == 1) Text(tasks.first().title, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, "打开定时消息", modifier = Modifier.size(18.dp))
        }
    }
}
