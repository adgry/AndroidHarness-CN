package com.androidharness.app.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.ApprovalRequest
import com.androidharness.app.agent.describeToolCall
import com.androidharness.app.data.env.EnvState
import com.androidharness.app.ui.common.PackageInstallProgress
import com.androidharness.app.ui.common.packageProgress
import com.androidharness.app.ui.common.VisualDiffViewer
import com.androidharness.app.ui.theme.LocalStatusColors

/**
 * One shared skeleton for every "agent needs you" card (approvals, plans,
 * environment installs): flat hairline card, quiet title row,
 * content, and a right-aligned action row with a single filled accent button.
 * The old design gave each card its own colors, icons and elevation, the
 *sameness here is what makes the feed readable.
 */
@Composable
private fun ActionCard(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = iconTint)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized)
            }
            Spacer(Modifier.height(10.dp))
            content()
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                actions()
            }
        }
    }
}

@Composable
internal fun ApprovalCard(
    approval: ApprovalRequest,
    onApprove: (Boolean) -> Unit,
    onDeny: () -> Unit,
) {
    val isPkg = approval.call.name == "pkg_install"
    val scheme = MaterialTheme.colorScheme
    ActionCard(
        title = if (isPkg) "安装 Linux 软件包？" else "批准此操作？",
        icon = if (isPkg) Icons.Outlined.Inventory2 else Icons.Outlined.Shield,
        iconTint = if (isPkg) scheme.tertiary else scheme.primary,
        actions = {
            TextButton(onClick = onDeny) { Text(if (isPkg) "拒绝" else "拒绝") }
            if (!isPkg) {
                FilledTonalButton(onClick = { onApprove(true) }) { Text("始终允许") }
            }
            Button(onClick = { onApprove(false) }) { Text("允许") }
        },
    ) {
        if (isPkg) {
            Surface(
                color = scheme.errorContainer.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = scheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "注意：代理即将在你的设备上下载并安装软件包，请确认后继续。",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onErrorContainer,
                    )
                }
            }
        }
        Text(
            describeToolCall(approval.call),
            style = MaterialTheme.typography.bodyMedium,
        )
        approval.diffPreview?.let { preview ->
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                if (isPkg) {
                    MonoBlock(preview)
                } else {
                    DiffView(preview)
                }
            }
        }
        if (approval.diffPreview == null) {
            Spacer(Modifier.height(10.dp))
            MonoBlock(prettyArgs(approval.call.argumentsJson))
        }
    }
}

@Composable
internal fun PlanApprovalCard(
    plan: String,
    onApprove: () -> Unit,
    onDiscard: () -> Unit,
) {
    var expanded by remember(plan) { mutableStateOf(false) }
    val longPlan = plan.length > 2_000
    ActionCard(
        title = "计划已准备好执行",
        actions = {
            TextButton(onClick = onDiscard) { Text("放弃") }
            Button(onClick = onApprove) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("批准并执行")
            }
        },
    ) {
        if (longPlan && !expanded) {
            Text(plan.take(2_000), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 8, overflow = TextOverflow.Ellipsis)
        } else {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    com.androidharness.app.ui.chat.MarkdownText(plan)
                }
            }
        }
        if (longPlan) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起" else "显示完整计划")
        }
    }
}

@Composable
internal fun EnvironmentInstallCard(
    request: com.androidharness.app.agent.EnvironmentRequest,
    envState: EnvState,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    installStarted: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val repair = request.repair
    val installing = installStarted || envState.packageProgress() != null
    val progressState = if (installStarted && envState.packageProgress() == null && envState !is EnvState.Failed) EnvState.Preparing else envState
    ActionCard(
        title = if (installing) {
            if (repair) "正在修复 Linux 环境" else "正在安装 Linux 环境"
        } else if (repair) {
            "Linux 环境需要修复"
        } else {
            "需要 Linux 环境"
        },
        icon = Icons.Outlined.Terminal,
        actions = {
            if (!installing) {
                TextButton(onClick = onSkip) { Text("跳过") }
                Button(onClick = onInstall) { Text(if (repair) "修复" else "立即安装") }
            }
        },
    ) {
        if (repair) {
            Text(
                "已安装的 Linux 环境中缺少“${request.missingTool}”" +
                    "（命令提示“not found”而失败）。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                "要运行的命令：${request.command.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "修复将重新安装工具链，然后代理会重新运行命令。",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
        } else {
            Text(
                "代理要运行：${request.command.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "需要：${request.hints.joinToString(" · ")}",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
        }
        when (progressState) {
            is EnvState.Downloading, is EnvState.Installing, is EnvState.Preparing -> {
                Spacer(Modifier.height(10.dp))
                PackageInstallProgress(progressState)
            }
            is EnvState.Failed -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    "安装失败：${progressState.message}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.error,
                )
            }
            else -> Unit
        }
    }
}

// ---------------------------------------------------------------------------
// Unified diff rendering

@Composable
internal fun DiffView(diff: String) {
    VisualDiffViewer(
        diffText = diff,
        maxHeight = 260.dp,
        showFileHeader = false,
    )
}
