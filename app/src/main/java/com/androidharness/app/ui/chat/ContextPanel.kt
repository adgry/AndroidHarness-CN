package com.androidharness.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidharness.app.llm.ModelPrices
import com.androidharness.app.ui.common.ThinLinearProgress

import com.androidharness.app.ui.common.formatTokenCount
import com.androidharness.app.ui.theme.HarnessMono

/**
 * Current-context dialog, modeled on GUI harnesses like Cline: a live
 * context-window bar (last request's total vs the model's window), the
 * cumulative ↑in / ↓out / cache reads/writes breakdown for this session,
 * and a list-price cost estimate at the same bucket rates they use.
 */
@Composable
fun ContextUsageDialog(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onContextLimits: () -> Unit,
) {
    val used = state.contextUsed.toLong().coerceAtLeast(0)
    val max = state.maxContextTokens.toLong()
    val fraction = (used.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    val estimate = state.estimate
    val estimateTotal = estimate?.total?.takeIf { it > 0 } ?: 1
    // Fresh prompt tokens = total input minus cache reads/writes (pi semantics).
    val freshInput = (state.usage.totalInput - state.usage.totalCached - state.usage.totalCacheWrite)
        .coerceAtLeast(0)
    val model = state.effectiveModel
    val providerKey = state.activeProvider?.let { com.androidharness.app.llm.ModelsDev.providerKeyFor(it.baseUrl) }
    val usesChatGptPlan = com.androidharness.app.chatgpt.ChatGptProtocol.isProvider(state.activeProvider?.id) ||
        state.sessionModelUsage.any { it.providerName.startsWith("ChatGPT · ") }
    val estimatedCost: Double? = if (usesChatGptPlan) null else if (state.sessionModelUsage.isNotEmpty()) {
        state.sessionModelUsage.sumOf { row ->
            val pKey = com.androidharness.app.llm.ModelsDev.providerKeyFor(row.providerName)
            ModelPrices.estimateSplit(
                model = row.model,
                totalInputTokens = row.inputTokens,
                outputTokens = row.outputTokens,
                cachedTokens = row.cachedTokens,
                cacheWriteTokens = row.cacheWriteTokens,
                peakInputTokens = row.peakInputTokens,
                peakOutputTokens = row.peakOutputTokens,
                peakCachedTokens = row.peakCachedTokens,
                peakCacheWriteTokens = row.peakCacheWriteTokens,
                providerKey = pKey,
            ) ?: 0.0
        }
    } else {
        model?.let {
            ModelPrices.estimate(
                it,
                totalInputTokens = state.usage.totalInput,
                outputTokens = state.usage.totalOutput,
                cachedTokens = state.usage.totalCached,
                cacheWriteTokens = state.usage.totalCacheWrite,
                providerKey = providerKey,
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onContextLimits) { Text("上下文与限制") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        title = { Text("上下文与用量") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Column {
                    Text(
                        "上下文窗口",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatTokenCount(used)} / ${formatTokenCount(max)} Token（" +
                            "%.1f%%".format(fraction * 100) + "）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ThinLinearProgress(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BreakdownRow("消息", estimate?.messagesTokens, estimateTotal)
                    BreakdownRow("工具与技能", estimate?.toolsTokens, estimateTotal)
                    BreakdownRow("系统提示词", estimate?.systemTokens, estimateTotal)
                    BreakdownRow("元上下文", estimate?.metaTokens, estimateTotal)
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "此聊天的 Token",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    TokenRow("新输入 Token", formatTokenCount(freshInput))
                    TokenRow("缓存 Token（命中）", formatTokenCount(state.usage.totalCached))
                    if (state.usage.totalCacheWrite > 0) {
                        TokenRow("缓存写入（创建）", formatTokenCount(state.usage.totalCacheWrite))
                    }
                    TokenRow("输出 Token", formatTokenCount(state.usage.totalOutput))
                    TokenRow("总请求数", state.usage.requests.toString())
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val hitRateText = when {
                        state.usage.requests == 0L && state.busy -> "正在计算…"
                        state.usage.totalInput > 0 -> "%.1f%%".format(state.usage.avgCacheHitRate * 100)
                        else -> "0.0%"
                    }
                    TokenRow("总体缓存命中率", hitRateText)
                    Text(
                        "每轮完成后更新命中率",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("预计费用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (usesChatGptPlan) "ChatGPT 套餐" else "$%.4f".format(estimatedCost ?: 0.0),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = HarnessMono,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val subText = if (usesChatGptPlan) {
                        "在 ChatGPT 设置中查看套餐用量与额度"
                    } else if (state.sessionModelUsage.size > 1) {
                        "此聊天所用 ${state.sessionModelUsage.size} 个模型的合计"
                    } else {
                        "按 ${model ?: "当前模型"} 的费率计算"
                    }
                    Text(
                        subText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                if (usesChatGptPlan) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    TextButton(onClick = {
                        com.androidharness.app.ui.common.openOAuthBrowser(context, android.net.Uri.parse(com.androidharness.app.chatgpt.ChatGptProtocol.USAGE_URL))
                    }) { Text("管理用量") }
                } else Text(
                    "推理和思考 Token 按输出计费，不会作为输入再次发送。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun TokenRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = HarnessMono,
        )
    }
}

@Composable
private fun BreakdownRow(label: String, tokens: Int?, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            tokens?.let { "%.1f%%".format(it.toFloat() / total * 100) } ?: "0.0%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
