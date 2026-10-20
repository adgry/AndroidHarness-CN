package com.androidharness.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.androidharness.app.llm.ProviderHeader
import com.androidharness.app.llm.ProviderHeaders
import java.util.UUID

internal data class ProviderHeaderDraft(val header: ProviderHeader, val id: String = UUID.randomUUID().toString())

@Composable
internal fun ProviderHeadersEditor(rows: List<ProviderHeaderDraft>, onChange: (List<ProviderHeaderDraft>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var preview by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("自定义请求头", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (rows.isNotEmpty()) Text("${rows.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            TextButton(onClick = { onChange(rows + ProviderHeaderDraft(ProviderHeader("", ""))) },
                enabled = rows.size < ProviderHeaders.MAX_HEADERS) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加请求头")
            }
        }
        rows.forEachIndexed { index, row ->
            key(row.id) {
                var visible by remember { mutableStateOf(false) }
                val focus = remember { FocusRequester() }
                LaunchedEffect(row.id) { if (row.header.name.isEmpty()) focus.requestFocus() }
                fun update(header: ProviderHeader) = onChange(rows.map { if (it.id == row.id) it.copy(header = header) else it })
                Surface(color = scheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 48.dp).toggleable(row.header.enabled, role = Role.Checkbox,
                                onValueChange = { update(row.header.copy(enabled = it)) })
                                .semantics { contentDescription = "启用第 ${index + 1} 个请求头" },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = row.header.enabled, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text("已启用", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { onChange(rows.filterNot { it.id == row.id }) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "移除第 ${index + 1} 个请求头")
                            }
                        }
                        OutlinedTextField(value = row.header.name, onValueChange = { update(row.header.copy(name = it)) },
                            label = { Text("请求头名称") }, placeholder = { Text("X-Client-Type") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                                .semantics { contentDescription = "第 ${index + 1} 个请求头名称" })
                        OutlinedTextField(value = row.header.value, onValueChange = { update(row.header.copy(value = it)) },
                            label = { Text("值") }, singleLine = true,
                            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { visible = !visible }) {
                                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (visible) "隐藏第 ${index + 1} 个请求头的值" else "显示第 ${index + 1} 个请求头的值")
                            } },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "第 ${index + 1} 个请求头的值" })
                    }
                }
            }
        }
        val error = ProviderHeaders.error(rows.map { it.header })
        Text(error ?: "仅发送给此提供商，值会安全存储。",
            style = MaterialTheme.typography.labelSmall, color = if (error == null) scheme.onSurfaceVariant else scheme.error)
        if (rows.isNotEmpty()) {
            TextButton(onClick = { preview = !preview }) { Text(if (preview) "收起请求头" else "请求头") }
            if (preview) {
                val enabled = rows.filter { it.header.enabled && it.header.name.isNotBlank() }
                Text(if (enabled.isEmpty()) "未启用自定义请求头" else enabled.joinToString("\n") { "${it.header.name.trim()}: ••••••••" },
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
