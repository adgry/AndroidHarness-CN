package com.androidharness.app.ui.common

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.androidharness.app.chatgpt.ChatGptProtocol

/** Account-limit reads are separate from inference; this sign-in currently opens the usage page. */
@Composable
fun ChatGptUsageLimits(compact: Boolean = false) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("用量限制与重置", style = MaterialTheme.typography.titleSmall)
                    Text("在 ChatGPT 中查看实时限制", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { openOAuthBrowser(context, Uri.parse(ChatGptProtocol.USAGE_URL)) }) {
                    Text("查看用量")
                }
            }
            if (!compact) Text("在 ChatGPT 中查看剩余用量、重置时间和已积累的重置次数。仅显示适用于你套餐的限制。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
