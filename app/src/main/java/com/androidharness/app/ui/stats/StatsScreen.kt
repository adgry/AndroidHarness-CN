package com.androidharness.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.SessionRepository
import com.androidharness.app.data.db.CodeActivityTotals
import com.androidharness.app.ui.common.AppHeader
import com.androidharness.app.ui.common.formatTokenCount
import com.androidharness.app.ui.theme.HarnessMono
import com.androidharness.app.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

private enum class StatsSort(val label: String) { TOKENS("Token"), PRICE("费用"), REQUESTS("请求") }
private data class UsageView(val stats: StatsSnapshot = StatsSnapshot(), val code: CodeActivityTotals = CodeActivityTotals())

@Composable
fun StatsScreen(container: AppContainer, onBack: () -> Unit) = StatsScreen(container.sessions, onBack)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsScreen(repository: SessionRepository, onBack: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(StatsRange.WEEK) }
    var sort by rememberSaveable { mutableStateOf(StatsSort.TOKENS) }
    var definitions by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); delay(60_000) }
        }
    }
    val window = range.window(now)
    val flow = remember(repository, window, range) {
        combine(repository.sessions, repository.statsUsageBetween(window.since, window.until), repository.codeActivityBetween(window.since, window.until)) { sessions, usage, code ->
            UsageView(statsSnapshot(usage, sessions.takeIf { range == StatsRange.LIFETIME }), code)
        }
    }
    val view = key(window, range) { flow.collectAsStateWithLifecycle(initialValue = UsageView()).value }
    val bundle = view.stats.bundle
    val models = view.stats.byModel.map { row -> row to com.androidharness.app.llm.ModelPrices.estimateSplit(
        model = row.model, totalInputTokens = row.inputTokens, outputTokens = row.outputTokens,
        cachedTokens = row.cachedTokens, cacheWriteTokens = row.cacheWriteTokens,
        peakInputTokens = row.peakInputTokens, peakOutputTokens = row.peakOutputTokens,
        peakCachedTokens = row.peakCachedTokens, peakCacheWriteTokens = row.peakCacheWriteTokens,
        providerKey = com.androidharness.app.llm.ModelsDev.providerKeyFor(row.providerName)) }
    val knownPrices = models.mapNotNull { it.second }
    val cost = knownPrices.takeIf { it.isNotEmpty() }?.sum()
    val code = view.code
    val scheme = MaterialTheme.colorScheme
    val green = LocalStatusColors.current.success
    Scaffold(containerColor = scheme.surface, topBar = {
        AppHeader("用量", subtitle = "模型活动与 AI 代码更改", onBack = onBack)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (LocalDensity.current.fontScale > 1.2f) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatsRange.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FilterChip(selected = range == option, onClick = { range = option }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                label = { Text(option.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
                        }
                    }
                }
            } else SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsRange.entries.forEachIndexed { index, option ->
                    SegmentedButton(selected = range == option, onClick = { range = option }, icon = {},
                        shape = SegmentedButtonDefaults.itemShape(index, StatsRange.entries.size)) {
                        Text(option.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模型活动", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
                    Text(formatTokenCount(bundle.input + bundle.output), style = MaterialTheme.typography.displayMedium,
                        fontFamily = HarnessMono, color = scheme.onPrimaryContainer)
                    Text("Token 总量 · ${bundle.requests} 次请求 · ${bundle.sessionCount} 个聊天",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onPrimaryContainer)
                    HorizontalDivider(color = scheme.onPrimaryContainer.copy(alpha = .18f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        UsageMetric("预计费用", cost?.let { "$${"%.2f".format(it)}" } ?: "不可用", Modifier.weight(1f))
                        UsageMetric("缓存命中率", if (bundle.input > 0) "${"%.1f".format(bundle.cached.toDouble() / bundle.input * 100)}%" else "—", Modifier.weight(1f))
                    }
                    if (knownPrices.size < models.size) Text("费用仅包含已知公开定价的模型。", style = MaterialTheme.typography.labelSmall)
                }
            }
            UsageCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Code, null, tint = scheme.primary)
                    Column {
                        Text("AI 代码更改", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${range.label} · ${code.edits} 次已记录编辑", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = green.copy(alpha = .09f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("+${lineCount(code.added)}", style = if (lineCount(code.added).length > 8) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontFamily = HarnessMono, color = green)
                            Text("新增行数", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = scheme.errorContainer.copy(alpha = .45f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("−${lineCount(code.removed)}", style = if (lineCount(code.removed).length > 8) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontFamily = HarnessMono, color = scheme.error)
                            Text("删除行数", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                val total = code.added + code.removed
                if (total > 0) Row(Modifier.fillMaxWidth().height(6.dp)) {
                    if (code.added > 0) Box(Modifier.weight(code.added.toFloat() / total).fillMaxHeight().background(green, RoundedCornerShape(3.dp)))
                    if (code.removed > 0) Box(Modifier.weight(code.removed.toFloat() / total).fillMaxHeight().background(scheme.error, RoundedCornerShape(3.dp)))
                }
                val net = code.added - code.removed
                Text("净变化  ${if (net >= 0) "+" else "−"}${lineCount(kotlin.math.abs(net))} 行", style = MaterialTheme.typography.bodyMedium)
                if (code.edits == 0L) Text("此期间没有已记录的代码更改。", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text("源码与配置 · 删除聊天后保留统计", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            UsageCard {
                Text("Token 明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    UsageMetric("新输入", formatTokenCount(bundle.freshInput), Modifier.weight(1f))
                    UsageMetric("输出", formatTokenCount(bundle.output), Modifier.weight(1f))
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    UsageMetric("缓存读取", formatTokenCount(bundle.cached), Modifier.weight(1f))
                    UsageMetric("缓存写入", formatTokenCount(bundle.cacheWrite), Modifier.weight(1f))
                }
            }
            UsageCard {
                Text("按模型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StatsSort.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = sort == option, onClick = { sort = option }, icon = {}, shape = SegmentedButtonDefaults.itemShape(index, StatsSort.entries.size)) { Text(option.label) }
                    }
                }
                val sorted = when (sort) {
                    StatsSort.TOKENS -> models.sortedByDescending { it.first.totalTokens }
                    StatsSort.PRICE -> models.sortedByDescending { it.second ?: -1.0 }
                    StatsSort.REQUESTS -> models.sortedByDescending { it.first.requests }
                }
                if (sorted.isEmpty()) Text("此期间没有带日期的模型活动，早期用量可在“全部时间”中查看。", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                val max = sorted.maxOfOrNull { it.first.totalTokens }?.coerceAtLeast(1) ?: 1
                sorted.forEachIndexed { index, (model, price) ->
                    if (index > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .45f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(model.model, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${model.providerName} · ${model.requests} 次请求", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${formatTokenCount(model.totalTokens)} Token", fontFamily = HarnessMono, style = MaterialTheme.typography.bodySmall)
                            Text(price?.let { "≈ $${"%.2f".format(it)}" } ?: "费用不可用", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(progress = { model.totalTokens.toFloat() / max }, modifier = Modifier.fillMaxWidth().height(4.dp))
                    }
                }
            }
            UsageCard {
                TextButton(onClick = { definitions = !definitions }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Text("用量统计方式", Modifier.weight(1f), color = scheme.onSurface)
                    Icon(if (definitions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                if (definitions) {
                    Text("AI 代码更改统计已应用的源码和配置编辑，包括重复编辑及后续撤销。净变化为新增行数减去删除行数，并非当前 Git 差异。不含手动编辑器保存、文档、生成文件、依赖和二进制文件。", style = MaterialTheme.typography.bodySmall)
                    Text("包含已记录的文件工具操作及符合条件的本地前台 Shell 更改。后台命令、远程 Shell 更改以及超出大小或工作量限制的比较可能未被统计。早期总量取自保留的编辑记录，已删除的历史无法恢复。用量统计不保存文件内容。", style = MaterialTheme.typography.bodySmall)
                    Text("“今天”以设备日期为准。“1 周”包含今天和此前 6 天；“1 个月”包含今天和此前 29 天。无日期的 Token 用量仅显示在“全部时间”中。", style = MaterialTheme.typography.bodySmall)
                    Text("Token 总量包含子代理与上下文压缩。缓存读取和写入与新输入分开统计。费用按内置模型价目表估算，并非服务商账单。DeepSeek 请求在工作日 UTC 01:00–04:00 和 06:00–10:00 使用高峰费率，其他时段（含周末）按半价计算。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun UsageCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private fun lineCount(value: Long): String = java.text.NumberFormat.getIntegerInstance().format(value)

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontFamily = HarnessMono)
    }
}
