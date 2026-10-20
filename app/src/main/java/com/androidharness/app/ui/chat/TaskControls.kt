package com.androidharness.app.ui.chat

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.*
import com.androidharness.app.core.Role

@Composable
fun TaskProgressCard(record: TaskRecord, busy: Boolean, onResume: () -> Unit, onSettings: () -> Unit) {
    if (!record.resumable || record.status == "running" || busy) return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(if (record.status == "interrupted") "任务已中断" else "任务已暂停",
                style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Text(record.reason ?: "进度已保存，可从上次完成的操作继续。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            record.reason?.let { CopyIconButton(it) }
            if (record.autoContinue && record.autoContinueAttempts >= record.autoContinueLimit.coerceIn(1, 5)) {
                Text("已达到自动继续次数上限，请手动恢复以继续。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSettings) { Text("上下文与限制") }
                FilledTonalButton(onClick = onResume) { Text("继续任务") }
            }
        }
    }
}

@Composable
fun MessageQueueCard(
    queue: List<QueuedPrompt>, onEdit: (String, String) -> Unit,
    onMove: (String, Int) -> Unit, onSendNow: (String) -> Unit,
) {
    if (queue.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QueuedPrompt?>(null) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 14.dp)
                .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text("${queue.size} 条排队中", style = MaterialTheme.typography.labelLarge)
                    if (!expanded) Text(com.androidharness.app.core.response.UserMessageContent.visibleText(queue.first().text), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "管理队列")
            }
            if (expanded) Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                Text("将在此聊天的“运行完成”通知后发送。", modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                queue.forEachIndexed { index, item ->
                    HorizontalDivider()
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(com.androidharness.app.core.response.UserMessageContent.visibleText(item.text), maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { editing = item }.padding(4.dp),
                            style = MaterialTheme.typography.bodySmall)
                        FlowRow(Modifier.fillMaxWidth()) {
                            IconButton(onClick = { onMove(item.id, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "上移消息")
                            }
                            IconButton(onClick = { onMove(item.id, 1) }, enabled = index < queue.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "下移消息")
                            }
                            IconButton(onClick = { editing = item }) { Icon(Icons.Default.Edit, "编辑排队消息") }
                            IconButton(onClick = { onEdit(item.id, "") }) { Icon(Icons.Default.Close, "移除排队消息") }
                            TextButton(onClick = { onSendNow(item.id) }) { Text("立即发送") }
                        }
                    }
                }
            }
        }
    }
    editing?.let { item ->
        var text by remember(item.id) { mutableStateOf(com.androidharness.app.core.response.UserMessageContent.visibleText(item.text)) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("编辑排队消息") },
            text = { OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8) },
            confirmButton = { TextButton(onClick = { onEdit(item.id, com.androidharness.app.core.response.UserMessageContent.edit(text, item.text)); editing = null }, enabled = text.isNotBlank()) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSettingsSheet(
    state: ChatUiState, onDismiss: () -> Unit,
    onSave: (String, String?, TaskLimits, Boolean) -> Unit,
    onRecoveryChange: (Boolean, Int) -> Unit,
) {
    val record = state.taskControl
    val savedSummary = record.summaryOverride ?: state.messages.lastOrNull {
        it.role == Role.SYSTEM && it.text.startsWith(AgentEngine.COMPACTION_PREFIX)
    }?.text?.removePrefix(AgentEngine.COMPACTION_PREFIX)?.trim().orEmpty()
    var pins by remember(state.sessionId) { mutableStateOf(record.pins) }
    var summary by remember(state.sessionId) { mutableStateOf(savedSummary) }
    var tokens by remember { mutableStateOf(record.limits.tokens.takeIf { it > 0 }?.toString().orEmpty()) }
    var cost by remember { mutableStateOf(record.limits.cost.takeIf { it > 0 }?.toString().orEmpty()) }
    var minutes by remember { mutableStateOf(record.limits.minutes.takeIf { it > 0 }?.toString().orEmpty()) }
    var clearOlder by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    var usage by remember { mutableStateOf(true) }
    val valid = (tokens.isBlank() || (tokens.toLongOrNull() ?: -1) > 0) &&
        (cost.isBlank() || cost.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true) &&
        (minutes.isBlank() || (minutes.toLongOrNull() ?: -1) in 1..525600)
    if (usage) {
        ContextUsageDialog(
            state = state,
            onDismiss = onDismiss,
            onContextLimits = { usage = false },
        )
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("上下文与限制", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { usage = true }) { Text("用量") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = section == 0, onClick = { section = 0 }, label = { Text("上下文") })
                    FilterChip(selected = section == 1, onClick = { section = 1 }, label = { Text("任务限制") })
                    FilterChip(selected = section == 2, onClick = { section = 2 }, label = { Text("恢复") })
                }
                Column(Modifier.weight(1f, fill = false).heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.busy && section != 2) Text("暂停任务以编辑这些设置。", style = MaterialTheme.typography.bodySmall)
                    if (section == 0) {
                        Text("保留重要指令，并选择代理需要记住的内容。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(pins, { pins = it }, label = { Text("固定指令") },
                            placeholder = { Text("使用 Kotlin，保留当前主题。") },
                            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.busy)
                        OutlinedTextField(summary, { summary = it }, label = { Text("已保存摘要") },
                            placeholder = { Text("尚未保存摘要") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, enabled = !state.busy)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("移除较早的上下文", style = MaterialTheme.typography.bodyMedium)
                                Text("保留最近一轮用户消息、摘要和固定指令，聊天记录仍可查看。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(clearOlder, { clearOlder = it }, enabled = !state.busy)
                        }
                    } else if (section == 1) {
                        Text("整个任务（含子代理）的可选限制，留空表示不限。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(tokens, { tokens = it }, label = { Text("Token 总量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy)
                        OutlinedTextField(cost, { cost = it }, label = { Text("预计费用 · 美元") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy)
                        OutlinedTextField(minutes, { minutes = it }, label = { Text("运行时间 · 分钟") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy)
                        Text("在下次请求或操作前暂停。进行中的工作可能超出限制。费用按目录价格或估算费率计算。达到限制后，请提高上限再继续。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (record.usedTokens > 0 || record.elapsedMs > 0) Text(
                            "已用：${record.usedTokens} Token · $${"%.4f".format(record.usedCost)} · ${record.elapsedMs / 60000} 分钟",
                            style = MaterialTheme.typography.labelMedium)
                        if (!valid) Text("请输入正数或留空。", color = MaterialTheme.colorScheme.error)
                    } else {
                        Row(Modifier.fillMaxWidth().clickable {
                            onRecoveryChange(!record.autoContinue, record.autoContinueLimit)
                        }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("自动继续", style = MaterialTheme.typography.bodyLarge)
                                Text("在服务器或连接中断后恢复已保存的进度。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(record.autoContinue, { onRecoveryChange(it, record.autoContinueLimit) })
                        }
                        Text("使用与“继续任务”相同的提示词。排队消息会等待此聊天成功完成。停止状态和任务限制需要你处理。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("每个任务的重试上限", modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRecoveryChange(record.autoContinue, record.autoContinueLimit - 1) },
                                enabled = record.autoContinueLimit > 1) { Icon(Icons.Default.Remove, "减少重试上限") }
                            Text("${record.autoContinueLimit.coerceIn(1, 5)}")
                            IconButton(onClick = { onRecoveryChange(record.autoContinue, record.autoContinueLimit + 1) },
                                enabled = record.autoContinueLimit < 5) { Icon(Icons.Default.Add, "增加重试上限") }
                        }
                        Text("已自动继续 ${record.autoContinueAttempts} 次。新任务会重置计数。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("更改会立即保存。", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (section == 2) {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("完成") }
                } else Button(onClick = {
                    onSave(pins, if (summary == savedSummary && !clearOlder) record.summaryOverride else summary,
                        TaskLimits(tokens.toLongOrNull() ?: 0, cost.toDoubleOrNull() ?: 0.0, minutes.toLongOrNull() ?: 0), clearOlder)
                    onDismiss()
                }, enabled = valid && !state.busy, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("保存更改") }
            }
        }
    }
}
