package com.androidharness.app.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.androidharness.app.R
import com.androidharness.app.llm.ModelEntry
import com.androidharness.app.llm.ModelReasoning
import com.androidharness.app.llm.ProviderType

@Composable
fun ModelReasoningIcon(entry: ModelEntry, providerType: ProviderType? = null, directory: Boolean = false) {
    val status = ModelReasoning.of(entry, providerType)
    if (status == ModelReasoning.UNKNOWN || status == ModelReasoning.UNSUPPORTED) return
    var details by remember { mutableStateOf(false) }
    val description = when (status) {
        ModelReasoning.REPORTED -> if (directory) "模型目录列出了推理支持" else "服务商报告支持推理"
        ModelReasoning.CUSTOM -> "由你设定为推理模型"
        else -> "根据模型名称推测支持推理"
    }
    IconButton(onClick = { details = true }) {
        Icon(painterResource(R.drawable.ic_brain),
            contentDescription = description, modifier = Modifier.size(20.dp),
            tint = if (status == ModelReasoning.INFERRED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary)
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(description) },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) { Text(when (status) {
            ModelReasoning.REPORTED -> if (directory) "模型目录列出了推理支持，但此连接尚未确认。能否显示思考文本取决于服务商。"
                else "此连接的目录报告支持推理，但不保证服务商会提供思考文本。"
            ModelReasoning.CUSTOM -> "你已将此自定义模型标为推理模型，服务商尚未确认此设置。"
            else -> "此模型系列可能支持推理，但此连接尚未确认。思考控制和思考文本的可见性取决于服务商。"
        }) } }, confirmButton = { TextButton(onClick = { details = false }) { Text("知道了") } })
}
