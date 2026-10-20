package com.androidharness.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.CheckStatus
import com.androidharness.app.agent.VerificationCheck
import com.androidharness.app.ui.theme.HarnessMono
import com.androidharness.app.ui.theme.LocalStatusColors

/** A quiet receipt; command outcomes stay available without competing with the answer. */
@Composable
internal fun VerificationCard(checks: List<VerificationCheck>) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalStatusColors.current
    var expanded by rememberSaveable(checks) { mutableStateOf(false) }
    val failed = checks.count { it.status == CheckStatus.FAILED }
    val summary = if (checks.isEmpty()) "验证 · 未运行检查" else
        "验证 · " + checks.groupingBy { it.status }.eachCount().entries.joinToString(" · ") { (status, count) ->
            "$count " + when (status) {
                CheckStatus.PASSED -> "已通过"
                CheckStatus.FAILED -> "失败"
                CheckStatus.UNKNOWN -> "未确认"
                CheckStatus.STALE -> "需要复查"
            }
        }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(summary, style = MaterialTheme.typography.labelMedium,
            color = if (failed > 0) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp))
        if (expanded) {
            if (checks.isEmpty()) Text("此轮未记录构建、测试或 Lint 结果。",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            checks.forEach { check ->
                val label = when (check.status) {
                    CheckStatus.PASSED -> "已通过"
                    CheckStatus.FAILED -> "失败"
                    CheckStatus.UNKNOWN -> "未确认"
                    CheckStatus.STALE -> "需要复查"
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = when (check.status) {
                    CheckStatus.PASSED -> colors.success
                    CheckStatus.FAILED -> scheme.error
                    else -> scheme.onSurfaceVariant
                })
                Text(check.command, fontFamily = HarnessMono, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
