package com.androidharness.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.data.env.EnvState
import com.androidharness.app.data.env.PkgMeta
import com.androidharness.app.ui.common.PackageInstallProgress
import com.androidharness.app.ui.common.packageProgress
import com.androidharness.app.ui.theme.HarnessMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val POPULAR_PACKAGES = listOf(
    "ripgrep" to "高速逐行正则搜索（rg）",
    "jq" to "轻量级命令行 JSON 处理器",
    "tree" to "递归显示目录树",
    "tmux" to "支持分屏与会话的终端复用器",
    "curl" to "通过 URL 传输数据的命令行工具",
    "clang" to "C 和 C++ 编译工具链",
    "rust" to "Rust 系统编程语言与 cargo",
    "htop" to "交互式进程监视器与资源查看器",
    "neovim" to "可扩展的 Vim 分支模态代码编辑器",
    "openjdk-17" to "Java 开发工具包 17",
    "diffutils" to "GNU diff 与 cmp 工具",
    "strace" to "系统调用跟踪与调试工具",
)

/**
 * Manage and search Linux packages from the Termux repository.
 * Allows users to inspect installed tools, discover popular CLI utilities,
 * search the repository closure, and install or remove packages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageManagerSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val linuxEnv = container.linuxEnv
    val envState by linuxEnv.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PkgMeta>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var installedList by remember { mutableStateOf(linuxEnv.installedPackages()) }
    var requestedPackage by remember { mutableStateOf<String?>(null) }

    val isBusy = requestedPackage != null || envState.packageProgress() != null
    val activePackage = (envState as? EnvState.Downloading)?.pkg ?: (envState as? EnvState.Installing)?.pkg

    fun installPackage(name: String) {
        if (isBusy) return
        requestedPackage = name
        errorMessage = null
        scope.launch {
            try {
                linuxEnv.install(listOf(name)).getOrThrow()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "安装 $name 失败"
            } finally {
                installedList = linuxEnv.installedPackages()
                requestedPackage = null
            }
        }
    }

    // Refresh after each completed package, without rereading storage for every byte update.
    val packageIndex = (envState as? EnvState.Downloading)?.index ?: (envState as? EnvState.Installing)?.index
    LaunchedEffect(envState is EnvState.Ready, packageIndex) {
        installedList = linuxEnv.installedPackages()
    }

    // Debounced search
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            searching = false
        } else {
            searching = true
            delay(250)
            searchResults = runCatching { linuxEnv.searchPackages(trimmed) }.getOrDefault(emptyList())
            searching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Linux 软件包", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已安装 ${installedList.size} 个 · Termux 软件源",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Installation progress indicator
            if (isBusy) {
                Surface(
                    color = scheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        PackageInstallProgress(if (envState.packageProgress() != null) envState else EnvState.Preparing)
                    }
                }
            }

            // Error banner
            errorMessage?.let { err ->
                Surface(
                    color = scheme.errorContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = scheme.error,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索软件包…") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空搜索", tint = scheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Package listings
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
            ) {
                if (query.isNotBlank()) {
                    // Search mode
                    if (searching) {
                        item {
                            Text(
                                "正在搜索软件源…",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else if (searchResults.isEmpty()) {
                        item {
                            Text(
                                "没有与 \"$query\" 匹配的软件包。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    } else {
                        items(searchResults, key = { it.name }) { pkg ->
                            PackageItemRow(
                                name = pkg.name,
                                version = pkg.version,
                                description = pkg.description,
                                sizeBytes = pkg.size,
                                isInstalled = pkg.name in installedList,
                                isCore = pkg.name in linuxEnv.fullPackages,
                                isBusy = isBusy,
                                isInstalling = pkg.name == activePackage || pkg.name == requestedPackage,
                                onInstall = { installPackage(pkg.name) },
                                onReviewRemoval = { container.pendingSettingsScroll.value = "storage"; onDismiss() },
                            )
                        }
                    }
                } else {
                    // Default browse mode: Popular Tools
                    item {
                        Text(
                            "常用工具",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                        )
                    }

                    items(POPULAR_PACKAGES, key = { it.first }) { (name, desc) ->
                        PackageItemRow(
                            name = name,
                            version = null,
                            description = desc,
                            sizeBytes = null,
                            isInstalled = name in installedList,
                            isCore = name in linuxEnv.fullPackages,
                            isBusy = isBusy,
                            isInstalling = name == activePackage || name == requestedPackage,
                            onInstall = { installPackage(name) },
                            onReviewRemoval = { container.pendingSettingsScroll.value = "storage"; onDismiss() },
                        )
                    }

                    // Installed Packages section
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "全部已安装（${installedList.size}）",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }

                    items(installedList.sorted(), key = { "installed-$it" }) { name ->
                        InstalledPackageRow(
                            name = name,
                            isCore = name in linuxEnv.fullPackages,
                            isBusy = isBusy,
                            onReviewRemoval = { container.pendingSettingsScroll.value = "storage"; onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageItemRow(
    name: String,
    version: String?,
    description: String,
    sizeBytes: Long?,
    isInstalled: Boolean,
    isCore: Boolean,
    isBusy: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onReviewRemoval: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = HarnessMono,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (version != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            version,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (sizeBytes != null && sizeBytes > 0) {
                        Spacer(Modifier.width(6.dp))
                        val sizeText = if (sizeBytes >= 1024 * 1024) {
                            "%.1f MB".format(sizeBytes.toDouble() / (1024 * 1024))
                        } else {
                            "${(sizeBytes + 1023) / 1024} KB"
                        }
                        Text(
                            sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.outline,
                        )
                    }
                }
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            if (isInstalling) {
                Text("正在安装…", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            } else if (isInstalled) {
                Surface(
                    color = scheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "已安装",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                        )
                    }
                }
                if (!isCore) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onReviewRemoval,
                        enabled = !isBusy,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Storage,
                            contentDescription = "查看共享存储",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            } else {
                Button(
                    onClick = onInstall,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("安装", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InstalledPackageRow(
    name: String,
    isCore: Boolean,
    isBusy: Boolean,
    onReviewRemoval: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLowest,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = HarnessMono,
                modifier = Modifier.weight(1f),
            )
            if (isCore) {
                Text(
                    "必需",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                )
            } else {
                IconButton(
                    onClick = onReviewRemoval,
                    enabled = !isBusy,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = "查看 $name 的共享存储",
                        tint = scheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}
