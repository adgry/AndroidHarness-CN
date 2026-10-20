package com.androidharness.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.androidharness.app.AppContainer
import com.androidharness.app.R
import com.androidharness.app.agent.PermissionMode
import com.androidharness.app.llm.effectiveApiKey
import com.androidharness.app.data.AppSettings
import com.androidharness.app.data.ChatBackupException
import com.androidharness.app.data.ThemeMode
import com.androidharness.app.ui.chat.components.FullAccessOrange
import com.androidharness.app.ui.chat.components.ModelPickerSheet
import com.androidharness.app.ui.settings.ProviderManagerSheet
import com.androidharness.app.data.db.ProjectEntity
import com.androidharness.app.data.env.ShizukuState
import com.androidharness.app.data.env.UserServiceState
import com.androidharness.app.ui.common.formatTokenCount
import com.androidharness.app.ui.common.AppHeader
import com.androidharness.app.ui.common.HarnessMark
import com.androidharness.app.ui.common.openOAuthBrowser
import com.androidharness.app.ui.common.AddWorkspaceDialog
import com.androidharness.app.ui.common.BiometricAuth
import com.androidharness.app.ui.common.findFragmentActivity
import com.androidharness.app.ui.common.SecureDialogEffect
import com.androidharness.app.ui.common.SecureScreenEffect
import com.androidharness.app.ui.common.SystemGrants
import com.androidharness.app.ui.common.rememberStorageAccess
import com.androidharness.app.ui.common.ThinLinearProgress
import com.androidharness.app.ui.theme.HarnessMono
import com.androidharness.app.ui.theme.LocalStatusColors
import com.androidharness.app.tools.mcp.McpConfigParser
import com.androidharness.app.tools.mcp.McpServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenStats: () -> Unit = {},
    onRunSetup: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val providers by container.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val workspace by container.workspace.current.collectAsStateWithLifecycle(initialValue = null)
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentProject by container.workspace.currentProject.collectAsStateWithLifecycle(initialValue = null)
    val envState by container.linuxEnv.state.collectAsStateWithLifecycle()
    val shizukuState by container.shizuku.state.collectAsStateWithLifecycle(initialValue = ShizukuState.NOT_INSTALLED)
    val serviceState by container.shizuku.serviceState.collectAsStateWithLifecycle(initialValue = UserServiceState.NOT_BOUND)
    val scope = rememberCoroutineScope()

    // Settings itself is screenshot-able; the surfaces that actually put a
    // key or token on screen raise the FLAG_SECURE policy themselves (the
    // web-search entry forms below, plus the MCP dialogs).

    var showAddWorkspace by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectEntity?>(null) }

    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val desktop = com.androidharness.app.ui.LocalDesktopLayout.current
    val page = pageName?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } }
        ?: if (desktop) SettingsPage.APPEARANCE else null
    // Label of the control a search result or deep link asked for, cleared once
    // the user has had a moment to see where they landed.
    var highlight by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(highlight) {
        if (highlight != null) {
            kotlinx.coroutines.delay(2600)
            highlight = null
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val homeScroll = rememberScrollState()
    fun goBack() {
        when {
            desktop -> onBack()
            page != null -> pageName = null
            query.isNotBlank() -> query = ""
            else -> onBack()
        }
    }
    BackHandler(enabled = page != null || query.isNotBlank()) { goBack() }
    LaunchedEffect(Unit) {
        container.pendingSettingsScroll.filterNotNull().collect { target ->
            settingsDeepLink(target)?.let { pageName = it.name }
            highlight = settingsDeepLinkAnchor(target)
            container.pendingSettingsScroll.value = null
        }
    }
    fun openPage(destination: SettingsPage, anchor: String? = null) {
        highlight = anchor
        when (destination) {
            SettingsPage.SKILLS -> onOpenSkills()
            SettingsPage.USAGE -> onOpenStats()
            SettingsPage.SETUP -> onRunSetup()
            else -> pageName = destination.name
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) scope.launch { container.workspace.addPickedFolder(uri) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AppHeader(
                title = if (desktop) "设置" else page?.title ?: "设置",
                onBack = { goBack() },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (desktop) {
                DesktopSettingsNavigation(page, query, { query = it }, ::openPage,
                    Modifier.width(240.dp).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        if (page == null) {
            SettingsHome(
                query = query,
                onQueryChange = { query = it },
                onOpen = ::openPage,
                modifier = Modifier.fillMaxSize().verticalScroll(homeScroll),
            )
        } else {
            key(page) {
                SettingsHighlightScope(highlight) {
                    Column(
                        modifier = Modifier.then(if (desktop) Modifier.widthIn(max = 880.dp) else Modifier)
                            .fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (page != SettingsPage.LOCAL_MODELS && page != SettingsPage.AGENT && page != SettingsPage.TELEGRAM && page != SettingsPage.STORAGE) SettingsPageIntro(page)
                    when (page) {
                        SettingsPage.CONNECTED_ACCOUNTS -> ConnectedAccountsSection(container)
                        SettingsPage.MODELS -> {
                            SettingsPanel(Modifier.fillMaxWidth()) {
                                SettingsAnchor("管理提供商") {
                                    SettingRow(
                                        icon = Icons.Outlined.Key,
                                        title = "管理提供商",
                                        subtitle = "添加连接、登录并选择模型",
                                        onClick = onOpenProviders,
                                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                    )
                                }
                            }
                            SettingsAnchor("双模型规划") {
                                PlanningModelSection(container, settings, scope, onOpenProviders)
                            }
                            CurrentSetupCard(settings, providers)
                            FallbackSettingsSection(container, settings.fallbackPolicy)
                        }
                        SettingsPage.SUBAGENT -> {
                            SubagentModelSection(container, settings)
                            SubagentSettingsSection(
                                settings = settings,
                                onSubagentTools = { scope.launch { container.settings.setSubagentFullAccess(it) } },
                            )
                        }
                        SettingsPage.LOCAL_MODELS -> LocalModelsSection(container)
                        SettingsPage.AGENT -> AgentSection(container, settings, scope)
                        SettingsPage.CHAT -> {
                            ChatBehaviorSection(container, settings, scope)
                            SlashCommandsSection(container)
                        }
                        SettingsPage.CODE_INTELLIGENCE -> CodeIntelligenceSection(container, workspace, currentProject)
                        SettingsPage.CAVEMAN -> CavemanSection(container, settings)
                        SettingsPage.VOICE -> VoiceSpeechSection(container, settings, scope)
                        SettingsPage.APPEARANCE -> AppearanceSection(container, settings, scope)
                        SettingsPage.PRIVACY -> PrivacySection(container, settings, scope)
                        SettingsPage.GITHUB -> GitHubIntegrationSection(container)
                        SettingsPage.SEARCH -> WebSearchSection(container)
                        SettingsPage.MCP -> McpSection(container)
                        SettingsPage.TELEGRAM -> TelegramSection(container)
                        SettingsPage.WORKSPACE -> {
                            RememberedPermissionsSection(container)
                            WorkspaceSection(
                                container, projects, currentProject, workspace?.displayPath, shizukuState,
                                onAddWorkspace = { showAddWorkspace = true },
                                onDeleteWorkspace = { pendingDelete = it },
                                onSelectWorkspace = { id -> scope.launch { container.workspace.setActiveProject(id) } },
                            )
                        }
                        SettingsPage.STORAGE -> StorageSection(container)
                        SettingsPage.ENVIRONMENT -> TerminalSection(container, envState, shizukuState, serviceState)
                        SettingsPage.TROUBLESHOOTING -> TroubleshootingSection(container, ::openPage)
                        SettingsPage.BACKUP -> {
                            SettingsBackupSection(container)
                            ChatsBackupSection(container)
                        }
                        SettingsPage.UPDATES -> UpdatesCard(container)
                        SettingsPage.ABOUT -> AboutSection(container)
                        SettingsPage.SKILLS, SettingsPage.USAGE, SettingsPage.SETUP -> Unit
                    }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
            }
        }
    }

    if (showAddWorkspace) {
        AddWorkspaceDialog(
            container = container,
            onDismiss = { showAddWorkspace = false },
            onPickSaf = {
                showAddWorkspace = false
                folderPicker.launch(null)
            },
        )
    }

    pendingDelete?.let { project ->
        var deleting by remember(project.id) { mutableStateOf(false) }
        var deleteError by remember(project.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("删除工作区？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从列表中移除 \"${project.name}\"？不会删除磁盘上的文件。")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    deleteError = null
                    scope.launch {
                        try {
                            container.workspace.deleteProject(project)
                            pendingDelete = null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            deleteError = e.message?.takeIf { it.isNotBlank() } ?: "无法移除工作区，请重试。"
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(if (deleting) "正在删除…" else "删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Workspace
// ---------------------------------------------------------------------------

@Composable
private fun WorkspaceSection(
    container: AppContainer,
    projects: List<ProjectEntity>,
    currentProject: ProjectEntity?,
    workspacePath: String?,
    shizukuState: ShizukuState,
    onAddWorkspace: () -> Unit,
    onDeleteWorkspace: (ProjectEntity) -> Unit,
    onSelectWorkspace: (String) -> Unit,
) {
    com.androidharness.app.ui.common.ShizukuWorkspaceWarningEffect(container.shizuku)
    SettingsHeader("工作区")

    currentProject?.let { current ->
        val desc = container.workspace.describe(current)
        val scheme = MaterialTheme.colorScheme
        Surface(
            color = scheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = scheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("当前工作区", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(current.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        workspacePath ?: desc.kindLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "使用中",
                    tint = scheme.primary,
                )
            }
        }
    }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            projects.forEachIndexed { index, project ->
                val desc = container.workspace.describe(project)
                val isActive = project.id == currentProject?.id
                SettingRow(
                    icon = if (project.kind == "APP") Icons.Outlined.Folder else Icons.Outlined.FolderOpen,
                    title = project.name,
                    subtitle = desc.kindLabel + " · " + if (desc.shellCapable) "完整 shell" else "仅文件工具",
                    onClick = { onSelectWorkspace(project.id) }.takeIf { !isActive },
                    divider = index != projects.lastIndex,
                    trailing = {
                        if (isActive) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "当前工作区", tint = MaterialTheme.colorScheme.primary)
                        } else if (project.kind != "APP") {
                            IconButton(onClick = { onDeleteWorkspace(project) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "删除 ${project.name}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                )
            }
        }
        Button(
            onClick = onAddWorkspace,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("添加工作区")
        }
    }
}

// ---------------------------------------------------------------------------
// Terminal & environment
// ---------------------------------------------------------------------------

@Composable
private fun TerminalSection(
    container: AppContainer,
    envState: com.androidharness.app.data.env.EnvState,
    shizukuState: ShizukuState,
    serviceState: UserServiceState,
) {
    val storageAccess = rememberStorageAccess()
    val allFiles = storageAccess.granted
    val connectionFailure by container.shizuku.connectionFailure.collectAsStateWithLifecycle()

    SettingsHeader("终端与环境")

    LinuxEnvironmentCard(container = container, envState = envState)

    // What the shell can reach right now.
    val appShellOk = envState is com.androidharness.app.data.env.EnvState.Ready
    val storageText = if (allFiles) "已授权 ✓" else "需要授权"
    val systemText = when (shizukuState) {
        ShizukuState.GRANTED -> when (serviceState) {
            UserServiceState.BOUND_READY -> "Shizuku ✓"
            UserServiceState.COMPAT_READY -> "兼容模式 ✓"
            UserServiceState.BINDING -> "正在连接…"
            UserServiceState.BIND_FAILED -> "连接失败"
            UserServiceState.NOT_BOUND -> "未连接"
        }
        ShizukuState.RUNNING_NO_PERMISSION -> "需要授权"
        ShizukuState.NOT_RUNNING -> "启动 Shizuku"
        ShizukuState.NOT_INSTALLED -> "未安装"
    }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            SettingsAnchor("应用工作区 shell") {
                SettingRow(
                    icon = Icons.Outlined.Terminal,
                    title = "应用工作区 shell",
                    subtitle = "私有工作区中的完整 Linux 工具链",
                    divider = true,
                    trailing = { StatusText(if (appShellOk) "就绪" else "toybox", ok = appShellOk) },
                )
            }
            SettingsAnchor("共享存储") {
                SettingRow(
                    icon = Icons.Outlined.SdStorage,
                    title = "共享存储",
                    subtitle = "读写共享存储中的项目文件",
                    onClick = storageAccess.request,
                    divider = true,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusText(storageText, ok = allFiles)
                            if (!allFiles) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "打开存储设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            }
            SettingsAnchor("系统路径与任意文件夹") {
                SettingRow(
                    icon = Icons.Outlined.Shield,
                    title = "系统路径与任意文件夹",
                    subtitle = "通过 Shizuku 获得 ADB shell 权限",
                    divider = false,
                    trailing = { StatusText(systemText, ok = shizukuState == ShizukuState.GRANTED && serviceState.isReady) },
                )
            }
        }
    }

    StorageAccessCard(allFiles = allFiles, onGrant = storageAccess.request)
    ShizukuCard(
        state = shizukuState,
        serviceState = serviceState,
        failure = connectionFailure,
        connectionReport = container.shizuku::connectionReport,
        startupReport = container.shizuku::startupReport,
        onRefresh = { container.shizuku.refresh() },
        onGrant = { container.shizuku.requestPermission() },
    )
    RootAccessSection(container)
    BatteryCard(container = container)
}

// ---------------------------------------------------------------------------
// Code intelligence
// ---------------------------------------------------------------------------

@Composable
private fun CodeIntelligenceSection(
    container: AppContainer,
    workspace: com.androidharness.app.workspace.WorkspaceFs?,
    currentProject: ProjectEntity?,
) {
    val codeGraphState by container.codeGraph.state.collectAsStateWithLifecycle()
    val workspaceRun by container.codeGraph.workspaceState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val rootPath = workspace?.shellRoot?.absolutePath
    var indexRevision by remember(rootPath) { mutableStateOf(0) }
    val indexed = remember(rootPath, indexRevision) { container.codeGraph.isIndexed(workspace) }

    LaunchedEffect(rootPath) {
        container.codeGraph.refresh()
        // Ask GitHub once per visit so the buttons can say what is actually
        // available instead of offering a blind "更新".
        container.codeGraph.checkForUpdates()
    }

    SettingsHeader("CodeGraph")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CodeGraph", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "本地语义代码图谱，支持符号、调用关系、影响范围和受影响测试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusText(
                    when {
                        codeGraphState.version == null -> "未安装"
                        codeGraphState.updateAvailable -> "v${codeGraphState.version} → v${codeGraphState.latest}"
                        codeGraphState.latest != null -> "v${codeGraphState.version} · 最新"
                        else -> "v${codeGraphState.version}"
                    },
                    ok = codeGraphState.version != null,
                )
            }

            if (codeGraphState.busy) {
                ThinLinearProgress(modifier = Modifier.fillMaxWidth())
                Text(
                    codeGraphState.action ?: "正在处理…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            codeGraphState.message?.takeLast(1_200)?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (codeGraphState.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (codeGraphState.version == null) {
                    Button(
                        enabled = !codeGraphState.busy,
                        onClick = { scope.launch { container.codeGraph.installOrUpdate() } },
                    ) { Text("安装 CodeGraph") }
                } else {
                    // Only offer an update when a newer release actually exists;
                    // otherwise the tap is a check that answers "already latest".
                    if (codeGraphState.updateAvailable) {
                        Button(
                            enabled = !codeGraphState.busy,
                            onClick = { scope.launch { container.codeGraph.installOrUpdate() } },
                        ) { Text("更新至 v${codeGraphState.latest}") }
                    } else {
                        OutlinedButton(
                            enabled = !codeGraphState.busy,
                            onClick = { scope.launch { container.codeGraph.checkForUpdates() } },
                        ) { Text("检查更新") }
                    }
                    OutlinedButton(
                        enabled = !codeGraphState.busy,
                        onClick = { scope.launch { container.codeGraph.uninstall() } },
                    ) { Text("卸载") }
                }
            }

            Text(
                "Harness 会将 CodeGraph 安装到私有 Linux 环境，包含所需的 Node 运行时约 170 MB。无需安装 Termux、Shizuku，也无需为代理单独设置 CodeGraph。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SettingsHeader("当前工作区")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(currentProject?.name ?: "没有工作区", style = MaterialTheme.typography.titleSmall)
                    Text(
                        rootPath ?: "此工作区没有可用的真实文件系统路径",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rootPath != null) {
                    StatusText(if (indexed) "已建立索引" else "未建立索引", ok = indexed)
                }
            }

            // Both an install and a sync make this card unusable for a moment,
            // so the row reports whichever is running.
            val workspaceBusy = workspaceRun.busy || codeGraphState.busy
            val workspaceAction = workspaceRun.action ?: codeGraphState.action

            if (workspaceBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        workspaceAction ?: "正在处理…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Live output of the run that is happening or has just finished,
            // shown here rather than in the install card above, which is not
            // what was tapped. During a long index these lines are the only
            // sign of life, so the tail scrolls with the work.
            workspaceRun.message?.takeLast(1_200)?.let { message ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        message.trim().lines().takeLast(9).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                        color = if (workspaceRun.failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            when {
                rootPath == null -> Text(
                    "CodeGraph 需要支持 shell 且具有真实设备路径的工作区。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                codeGraphState.version == null -> Text(
                    "请先安装 CodeGraph，再为此工作区启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !indexed -> Button(
                    enabled = !workspaceBusy,
                    onClick = {
                        val current = workspace
                        scope.launch {
                            container.codeGraph.initWorkspace(current)
                            indexRevision++
                        }
                    },
                ) { Text("为此工作区启用") }
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        enabled = !workspaceBusy,
                        onClick = {
                            val current = workspace
                            scope.launch {
                                container.codeGraph.syncWorkspace(current)
                                indexRevision++
                            }
                        },
                    ) { Text("同步") }
                    OutlinedButton(
                        enabled = !workspaceBusy,
                        onClick = {
                            val current = workspace
                            scope.launch {
                                container.codeGraph.reindexWorkspace(current)
                                indexRevision++
                            }
                        },
                    ) { Text("重新索引") }
                    TextButton(
                        enabled = !workspaceBusy,
                        onClick = {
                            val current = workspace
                            scope.launch {
                                container.codeGraph.disableWorkspace(current)
                                indexRevision++
                            }
                        },
                    ) { Text("停用") }
                }
            }
        }
    }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("代理集成", style = MaterialTheme.typography.titleSmall)
            Text(
                "为此工作区建立索引后，Harness 会向编程代理提供 CodeGraph 的 explore、node、impact、affected-tests 和 sync 工具。查询会先同步已更改文件，再读取图谱。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Web search
// ---------------------------------------------------------------------------

@Composable
private fun WebSearchSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val provider = settings.webSearchProvider
    val providerName = when (provider) {
        "brave" -> "Brave Search API"
        "tavily" -> "Tavily API"
        else -> "免密钥"
    }
    var keyDraft by remember { mutableStateOf("") }
    var keyEpoch by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val hasKey = remember(provider, keyEpoch) { container.keys.searchApiKey(provider) != null }
    val keyUrl = if (provider == "brave") "https://brave.com/search/api/" else "https://app.tavily.com/home"

    // The pasted key is only on screen while the entry form is open.
    SecureScreenEffect(container, expanded)

    SettingsHeader("网页搜索")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("web_search 搜索后端", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            provider == "keyless" -> "公共搜索引擎，无需设置"
                            hasKey -> "$providerName · 已保存密钥"
                            else -> "$providerName · 尚无密钥（回退至免密钥搜索）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusText(
                    when {
                        provider == "keyless" -> "免密钥"
                        hasKey -> "开启"
                        else -> "无密钥"
                    },
                    ok = provider == "keyless" || hasKey,
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("keyless" to "免密钥", "brave" to "Brave", "tavily" to "Tavily")
                    .forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = provider == value,
                            onClick = {
                                if (provider != value) {
                                    expanded = false
                                    status = null
                                    keyDraft = ""
                                    scope.launch { container.settings.setWebSearchProvider(value) }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text(label) }
                    }
            }

            when {
                provider == "keyless" -> Text(
                    "代理可直接搜索公共引擎；Brave 或 Tavily " +
                        "可提供更简洁、质量更高的结果。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                expanded -> {
                    Text(
                        (if (provider == "brave") {
                            "可在 brave.com/search/api 获取免费密钥（免费套餐每秒 1 次查询）。"
                        } else {
                            "可在 app.tavily.com 获取免费密钥（每月 1,000 次请求）。"
                        }) + "保存前会向提供商验证密钥，并且" +
                            "仅保存在应用的加密存储中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("获取 API 密钥")
                    }
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        label = { Text(if (hasKey) "替换 API 密钥" else "粘贴 API 密钥") },
                        placeholder = { Text(if (provider == "brave") "BSA…" else "tvly-…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = keyDraft.isNotBlank() && !checking,
                            onClick = {
                                checking = true
                                status = "正在向 $providerName 验证密钥…"
                                scope.launch {
                                    val key = keyDraft.trim()
                                    val error = withContext(Dispatchers.IO) { checkSearchKey(provider, key) }
                                    if (error == null) {
                                        withContext(Dispatchers.IO) { container.keys.putSearchApiKey(provider, key) }
                                        keyDraft = ""
                                        expanded = false
                                        status = "密钥已验证并保存"
                                        keyEpoch++
                                    } else {
                                        status = error
                                    }
                                    checking = false
                                }
                            },
                        ) { Text(if (checking) "正在检查…" else "保存并验证") }
                        TextButton(onClick = {
                            expanded = false
                            keyDraft = ""
                            status = null
                        }) { Text("取消") }
                    }
                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                hasKey -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        expanded = true
                        keyDraft = ""
                        status = null
                    }) { Text("替换密钥") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { container.keys.removeSearchApiKey(provider) }
                            keyEpoch++
                        }
                    }) { Text("移除", color = MaterialTheme.colorScheme.error) }
                }

                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl)))
                        }
                    }) { Text("获取 API 密钥") }
                    OutlinedButton(onClick = {
                        expanded = true
                        keyDraft = ""
                        status = null
                    }) { Text("我已有密钥") }
                }
            }
        }
    }
}

/**
 * Asks the provider to accept a search API key before it is saved, so an
 * invalid paste never becomes the stored credential.
 * Returns null when the key works, or a human-readable reason why not.
 */
private fun checkSearchKey(provider: String, key: String): String? {
    return runCatching {
        val req = when (provider) {
            "brave" -> okhttp3.Request.Builder()
                .url("https://api.search.brave.com/res/v1/web/search?q=android&count=1")
                .header("X-Subscription-Token", key)
                .header("Accept", "application/json")
                .build()
            else -> okhttp3.Request.Builder()
                .url("https://api.tavily.com/search")
                .post(
                    """{"api_key":"$key","query":"android","max_results":1}"""
                        .toRequestBody("application/json".toMediaTypeOrNull()),
                )
                .build()
        }
        val name = if (provider == "brave") "Brave" else "Tavily"
        okhttp3.OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val detail = Regex("\"(?:message|detail|error)\"\\s*:\\s*\"([^\"]+)\"")
                    .find(body)?.groupValues?.get(1)
                when {
                    resp.code == 401 || resp.code == 403 ->
                        "$name 拒绝了此密钥" + (detail?.let { " ($it)" } ?: "") +
                            "。请确认已完整复制密钥，密钥属于 $name，" +
                            "且未被停用。"
                    !resp.isSuccessful ->
                        "$name 返回 HTTP ${resp.code}" + (detail?.let { ": $it" } ?: "") +
                            "。请稍后重试。"
                    else -> null
                }
            }
    }.getOrElse { "无法连接提供商。请检查网络连接后重试。" }
}

// ---------------------------------------------------------------------------
// Voice & speech (Groq Whisper / Inbuilt)
// ---------------------------------------------------------------------------

@Composable
private fun VoiceSpeechSection(
    container: AppContainer,
    settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val context = LocalContext.current
    val voiceEngine = settings.voiceEngine
    val groqModel = settings.groqWhisperModel
    val isGroq = voiceEngine == AppSettings.VOICE_ENGINE_GROQ

    var keyDraft by remember { mutableStateOf("") }
    var keyEpoch by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val hasGroqKey = remember(keyEpoch) { container.keys.groqApiKey() != null }
    val groqKeyUrl = "https://console.groq.com/keys"

    // The pasted key is only on screen while the entry form is open.
    SecureScreenEffect(container, expanded)

    SettingsHeader("语音")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SettingsAnchor("语音转文字引擎") {
                        Text("语音转文字引擎", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        when {
                            !isGroq -> "Android 系统语音识别"
                            hasGroqKey -> "Groq Whisper（$groqModel）· 已保存密钥"
                            else -> "Groq Whisper · 尚无密钥"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusText(
                    when {
                        !isGroq -> "系统内置"
                        hasGroqKey -> "开启"
                        else -> "无密钥"
                    },
                    ok = !isGroq || hasGroqKey,
                )
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(
                    AppSettings.VOICE_ENGINE_INBUILT to "系统内置",
                    AppSettings.VOICE_ENGINE_GROQ to "Groq",
                ).forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = voiceEngine == value,
                        onClick = {
                            if (voiceEngine != value) {
                                expanded = false
                                status = null
                                keyDraft = ""
                                scope.launch { container.settings.setVoiceEngine(value) }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) { Text(label) }
                }
            }

            if (!isGroq) {
                Text(
                    "使用设备或系统语音识别，不向外部 API 发送音频。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SettingsAnchor("Whisper 模型") {
                    Text(
                        "Whisper 模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        AppSettings.GROQ_MODEL_WHISPER_V3 to "Whisper 3",
                        AppSettings.GROQ_MODEL_WHISPER_TURBO to "Turbo",
                    ).forEachIndexed { index, (value, label) ->
                        SegmentedButton(
                            selected = groqModel == value,
                            onClick = {
                                if (groqModel != value) {
                                    scope.launch { container.settings.setGroqWhisperModel(value) }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        ) { Text(label) }
                    }
                }

                when {
                    expanded -> {
                        Text(
                            "可在 console.groq.com/keys 获取免费 API 密钥。保存前会向" +
                                "Groq 验证密钥，并且仅保存在应用的加密存储中。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(groqKeyUrl)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("获取 Groq API 密钥")
                        }
                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = { Text(if (hasGroqKey) "替换 Groq API 密钥" else "粘贴 Groq API 密钥") },
                            placeholder = { Text("gsk_…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = keyDraft.isNotBlank() && !checking,
                                onClick = {
                                    checking = true
                                    status = "正在向 Groq 验证密钥…"
                                    scope.launch {
                                        val key = keyDraft.trim()
                                        val res = com.androidharness.app.data.audio.GroqWhisperClient.validateApiKey(key)
                                        if (res.success) {
                                            withContext(Dispatchers.IO) { container.keys.putGroqApiKey(key) }
                                            keyDraft = ""
                                            expanded = false
                                            status = "密钥已验证并保存"
                                            keyEpoch++
                                        } else {
                                            status = res.error ?: "验证失败"
                                        }
                                        checking = false
                                    }
                                },
                            ) { Text(if (checking) "正在检查…" else "保存并验证") }
                            TextButton(onClick = {
                                expanded = false
                                keyDraft = ""
                                status = null
                            }) { Text("取消") }
                        }
                        status?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    hasGroqKey -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            expanded = true
                            keyDraft = ""
                            status = null
                        }) { Text("替换密钥") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { container.keys.removeGroqApiKey() }
                                keyEpoch++
                            }
                        }) { Text("移除", color = MaterialTheme.colorScheme.error) }
                    }

                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(groqKeyUrl)))
                            }
                        }) { Text("获取 API 密钥") }
                        OutlinedButton(onClick = {
                            expanded = true
                            keyDraft = ""
                            status = null
                        }) { Text("我已有密钥") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MCP servers
// ---------------------------------------------------------------------------

@Composable
private fun McpSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val servers by container.mcp.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val statuses by container.mcp.statuses.collectAsStateWithLifecycle(initialValue = emptyMap())
    val configTampered by container.mcp.configTampered.collectAsStateWithLifecycle(initialValue = false)
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val anyConnected = servers.any { statuses[it.name]?.state == "connected" }

    SettingsHeader("MCP 服务器")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (configTampered) {
                Text(
                    "MCP 服务器列表被应用外部修改，完整性校验" +
                        "未通过，已忽略其内容。请在下方重新添加服务器以重建列表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("MCP 服务器", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "代理可在任何聊天中使用的额外工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusText(
                    when {
                        servers.isEmpty() -> "关闭"
                        anyConnected -> "开启"
                        else -> "空闲"
                    },
                    ok = anyConnected,
                )
            }

            servers.forEach { server ->
                val status = statuses[server.name]
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when {
                            !server.enabled -> MaterialTheme.colorScheme.outlineVariant
                            status?.state == "connected" -> LocalStatusColors.current.success
                            status?.state == "auth" -> LocalStatusColors.current.warning
                            status?.state == "failed" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(Modifier.size(9.dp).background(dotColor, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${server.name} · ${server.type}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = server.enabled,
                            onCheckedChange = { checked ->
                                scope.launch { container.mcp.setServerEnabled(server.name, checked) }
                            },
                        )
                    }
                    Text(
                        when {
                            !server.enabled -> "已关闭（启用后可使用其工具）"
                            status == null -> "尚未连接；点击“测试”，或等待运行前自动连接"
                            status.state == "connected" -> "已连接 · ${status.toolCount} 个工具"
                            status.state == "auth" -> "登录后可使用此服务器"
                            status.state == "connecting" -> "正在连接…"
                            else -> "失败：${status.error?.take(110) ?: "未知错误"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status?.needsAuth == true && server.enabled) {
                        Button(
                            onClick = {
                                authError = null
                                scope.launch {
                                    container.mcp.startAuthentication(server.name).fold(
                                        onSuccess = { url ->
                                            runCatching {
                                                openOAuthBrowser(context, Uri.parse(url))
                                            }
                                        },
                                        onFailure = { e ->
                                            authError = e.message?.take(160)
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("授权登录") }
                        authError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { container.mcp.testConnection(server) } }
                        }) { Text("测试") }
                        TextButton(onClick = { editing = server }) { Text("编辑") }
                        TextButton(onClick = {
                            scope.launch { container.mcp.removeServer(server.name) }
                        }) { Text("移除") }
                    }
                }
            }

            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加服务器")
            }
        }
    }

    if (showAdd) {
        McpAddDialog(container, onDismiss = { showAdd = false }, onManual = {
            showAdd = false
            editing = McpServerConfig(name = "", type = "stdio")
        })
    }
    editing?.let { config ->
        McpServerDialog(container, initial = config, onDismiss = { editing = null })
    }
}

/**
 * Paste-first add dialog: accepts a Claude-style `{"mcpServers": {…}}` JSON, a
 * single-server JSON object, or a `claude mcp add …` command line, showing a
 * live preview of what will be added. A manual editor is one tap away.
 */
@Composable
private fun McpAddDialog(
    container: AppContainer,
    onDismiss: () -> Unit,
    onManual: () -> Unit,
) {
    // Pasted config routinely carries auth headers; the dialog has its own
    // window, so it raises FLAG_SECURE itself.
    SecureDialogEffect()
    val scope = rememberCoroutineScope()
    var paste by remember { mutableStateOf("") }
    val parsed = remember(paste) { McpConfigParser.parsePaste(paste) }
    val existing = container.mcp.servers.value.map { it.name.lowercase() }
    val conflicts = parsed.count { it.name.lowercase() in existing }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 MCP 服务器") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = paste,
                    onValueChange = { paste = it },
                    label = { Text("粘贴配置") },
                    placeholder = {
                        Text(
                            "{\"mcpServers\": {\"supabase\": {\"type\": \"http\", \"url\": \"…\"}}}\n" +
                                "或：claude mcp add --transport http supabase \"https://…\"",
                        )
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    paste.isBlank() -> Text(
                        "支持完整的 mcpServers JSON（Claude Desktop / Cursor 格式）、" +
                            "单个服务器 JSON 对象，或 `claude mcp add` 命令。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    parsed.isEmpty() -> Text(
                        "无法解析，请使用下方的手动编辑器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Column {
                        Text(
                            "将添加 ${parsed.size} 个服务器：",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        parsed.forEach { c ->
                            Text(
                                "• ${c.name} · ${c.type} · " +
                                    (c.url ?: c.command + if (c.args.isEmpty()) "" else " ${c.args.joinToString(" ")}"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (conflicts > 0) {
                            Text(
                                "有 $conflicts 个名称已存在，将被替换。",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalStatusColors.current.warning,
                            )
                        }
                    }
                }
                Text(
                    "工具在聊天中显示为 mcp__server__tool，并遵循常规" +
                        "权限规则。如果服务器需要登录，“测试”后会显示" +
                        "“授权登录”按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed.isNotEmpty(),
                onClick = {
                    scope.launch {
                        parsed.forEach { container.mcp.addServer(it) }
                        onDismiss()
                    }
                },
            ) { Text(if (parsed.size > 1) "全部添加" else "添加") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onManual) { Text("手动编辑器") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun McpServerDialog(
    container: AppContainer,
    initial: McpServerConfig?,
    onDismiss: () -> Unit,
) {
    // Header and env values are secrets; this dialog owns its own window.
    SecureDialogEffect()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: "stdio") }
    var command by remember { mutableStateOf(initial?.command ?: "") }
    var argsText by remember { mutableStateOf(initial?.args?.joinToString("\n") ?: "") }
    var envText by remember {
        mutableStateOf(initial?.env?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "")
    }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var headersText by remember {
        mutableStateOf(initial?.headers?.entries?.joinToString("\n") { "${it.key}: ${it.value}" } ?: "")
    }
    val duplicate = (initial == null || initial.name.isBlank()) &&
        container.mcp.servers.value.any { it.name.equals(name.trim(), ignoreCase = true) }
    val valid = when (type) {
        "http", "sse" -> name.isNotBlank() && url.isNotBlank() && !duplicate
        else -> name.isNotBlank() && command.isNotBlank() && !duplicate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial?.name.isNullOrBlank()) "添加 MCP 服务器" else "编辑 MCP 服务器") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如 filesystem、supabase") },
                    singleLine = true,
                    supportingText = { if (duplicate) Text("已存在同名服务器。") },
                    isError = duplicate,
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("stdio" to "本地", "http" to "HTTP", "sse" to "SSE")
                        .forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = type == value,
                                onClick = { type = value },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                            ) { Text(label, maxLines = 1) }
                        }
                }
                if (type == "stdio") {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("命令") },
                        placeholder = { Text("例如 npx、python3、/full/path/server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = argsText,
                        onValueChange = { argsText = it },
                        label = { Text("参数（每行一个）") },
                        placeholder = { Text("-y\n@modelcontextprotocol/server-filesystem /path") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = envText,
                        onValueChange = { envText = it },
                        label = { Text("环境变量（每行 KEY=value）") },
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "本地进程（stdio 传输）以应用用户身份运行，使用" +
                            "Linux 工具链的 PATH；在运行前按需启动，随应用" +
                            "退出而结束。需要 Linux 环境中的 node/python。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("服务器 URL") },
                        placeholder = { Text("https://mcp.example.com/mcp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text("请求头（每行 Key: value，可选）") },
                        placeholder = { Text("Authorization: Bearer …") },
                        minLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (type == "http") {
                            "可流式 HTTP 传输。如果服务器要求登录，请先保存；" +
                                "服务器请求授权后会显示“授权登录”按钮。"
                        } else {
                            "面向旧版远程服务器的传统 HTTP+SSE 传输。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    fun kvLines(text: String, sep: Char) = text.lines().mapNotNull { line ->
                        val parts = line.split(sep, limit = 2)
                        if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                            parts[0].trim() to parts[1].trim()
                        } else null
                    }.toMap()
                    val config = McpServerConfig(
                        name = name.trim(),
                        type = type,
                        command = if (type == "stdio") command.trim() else "",
                        args = if (type == "stdio") argsText.lines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList(),
                        env = if (type == "stdio") kvLines(envText, '=') else emptyMap(),
                        url = if (type == "stdio") null else url.trim(),
                        headers = if (type == "stdio") emptyMap() else kvLines(headersText, ':'),
                        enabled = initial?.enabled ?: true,
                    )
                    scope.launch {
                        container.mcp.addServer(config)
                        onDismiss()
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun StorageAccessCard(allFiles: Boolean, onGrant: () -> Unit) {
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("存储访问", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (allFiles) "已授权：shell 和文件工具可使用共享存储。"
                        else "可选：使用应用工作区，或授予设备文件夹访问权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!allFiles) {
                    Button(onClick = onGrant) { Text("授权") }
                }
            }
        }
    }
}

@Composable
internal fun ShizukuCard(
    state: ShizukuState,
    serviceState: UserServiceState,
    failure: com.androidharness.app.data.env.ShizukuConnectionFailure?,
    connectionReport: () -> String,
    startupReport: suspend () -> com.androidharness.app.data.env.ShizukuStartupReport,
    onRefresh: () -> Unit,
    onGrant: () -> Unit,
) {
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Shizuku（ADB 权限）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (state) {
                            ShizukuState.NOT_INSTALLED -> "未安装：可选功能。"
                            ShizukuState.NOT_RUNNING -> "已安装，未运行。"
                            ShizukuState.RUNNING_NO_PERMISSION -> "正在运行：请授予 AndroidHarness 访问权限。"
                            ShizukuState.GRANTED -> when (serviceState) {
                                UserServiceState.BOUND_READY -> "已连接：shell 权限就绪。"
                                UserServiceState.COMPAT_READY -> "已通过兼容模式连接：shell 权限就绪。"
                                UserServiceState.BINDING -> "已授权。正在连接 shell 执行服务…"
                                UserServiceState.BIND_FAILED -> "已授权，但无法连接 shell 执行服务。请重试连接。"
                                UserServiceState.NOT_BOUND -> "已授权。shell 执行服务尚未连接。"
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ShizukuFailureDetails(failure, connectionReport, startupReport,
                recovered = serviceState == UserServiceState.COMPAT_READY)
            when (state) {
                ShizukuState.GRANTED -> {
                    if (serviceState.isReady) {
                        Text(
                            if (serviceState == UserServiceState.COMPAT_READY) "兼容执行服务就绪 ✓" else "特权执行服务就绪 ✓",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = onRefresh, enabled = serviceState != UserServiceState.BINDING) {
                        Text(if (serviceState.isReady) "重新检查状态" else "重试连接")
                    }
                }
                ShizukuState.RUNNING_NO_PERMISSION -> Button(onClick = onGrant) { Text("授予 Shizuku 权限") }
                ShizukuState.NOT_RUNNING -> OutlinedButton(onClick = onRefresh) { Text("刷新状态") }
                ShizukuState.NOT_INSTALLED -> OutlinedButton(onClick = onRefresh) { Text("再次检查") }
            }
        }
    }
}

@Composable
private fun LinuxEnvironmentCard(
    container: AppContainer,
    envState: com.androidharness.app.data.env.EnvState,
) {
    val scope = rememberCoroutineScope()
    var checkResult by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var showPackagesSheet by remember { mutableStateOf(false) }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Linux 环境", style = MaterialTheme.typography.titleSmall)
                    Text("bash, git, gh, python, pip, node, npm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusText(
                    when (envState) {
                        is com.androidharness.app.data.env.EnvState.Ready -> "已安装"
                        is com.androidharness.app.data.env.EnvState.Downloading -> "正在下载…"
                        is com.androidharness.app.data.env.EnvState.Installing -> "正在安装…"
                        is com.androidharness.app.data.env.EnvState.Preparing -> "正在解析…"
                        is com.androidharness.app.data.env.EnvState.Failed -> "失败"
                        else -> "未安装"
                    },
                    ok = envState is com.androidharness.app.data.env.EnvState.Ready,
                )
            }

            when (envState) {
                is com.androidharness.app.data.env.EnvState.Ready -> {
                    val installed = container.linuxEnv.installedPackages()
                    if (installed.isNotEmpty()) {
                        Text(
                            "软件包（${installed.size}）：${installed.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    checkResult?.let { report ->
                        Text(
                            report,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (environmentCheckPassed(report)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Step 1: check. Step 2 (only when something is actually
                    // missing/broken): confirm and update. Nothing to do → no
                    // dead button.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTonalButton(onClick = { showPackagesSheet = true }) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("软件包")
                        }
                        OutlinedButton(
                            enabled = !checking,
                            onClick = {
                                checking = true
                                scope.launch {
                                    checkResult = container.linuxEnv.checkMissing()
                                    checking = false
                                }
                            },
                        ) {
                            Text(if (checking) "正在检查…" else "检查缺失项")
                        }
                        if (checkResult != null && !environmentCheckPassed(checkResult)) {
                            Button(onClick = {
                                checkResult = null
                                scope.launch { container.linuxEnv.updateEnvironment() }
                            }) {
                                Text("更新")
                            }
                        }
                        OutlinedButton(onClick = { confirmUninstall = true }) {
                            Text("卸载")
                        }
                    }
                }
                is com.androidharness.app.data.env.EnvState.NotInstalled,
                is com.androidharness.app.data.env.EnvState.Failed -> {
                    if (envState is com.androidharness.app.data.env.EnvState.Failed) {
                        Text(
                            "安装失败：${envState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            "将 bash、git、gh、python 3、pip、node.js 和 npm 安装到私有存储。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { scope.launch { container.linuxEnv.install(container.linuxEnv.fullPackages) } }) {
                        Text(if (envState is com.androidharness.app.data.env.EnvState.Failed) "重试安装" else "安装完整环境")
                    }
                }
                is com.androidharness.app.data.env.EnvState.Downloading,
                is com.androidharness.app.data.env.EnvState.Installing,
                is com.androidharness.app.data.env.EnvState.Preparing -> {
                    com.androidharness.app.ui.common.PackageInstallProgress(envState)
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("卸载 Linux 环境？") },
            text = { Text("这将移除所有本地工作区共享的工具链（bash、git、python、node、npm 及所有已下载的软件包）。重新安装前，代理将回退到 toybox sh。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    checkResult = null
                    scope.launch {
                        try { container.storage.uninstallToolchain() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { checkResult = e.message }
                    }
                }) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("取消") }
            },
        )
    }

    if (showPackagesSheet) {
        PackageManagerSheet(
            container = container,
            onDismiss = { showPackagesSheet = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Agent / Appearance / slash commands
// ---------------------------------------------------------------------------

/**
 * Read-only snapshot of what's actually driving requests right now. Model and
 * thinking changed from the chat header (picker + overflow), so they show
 * here as status, not settings, keeps this screen single-purpose.
 */
@Composable
private fun CurrentSetupCard(
    settings: AppSettings,
    providers: List<com.androidharness.app.llm.ProviderConfig>,
) {
    val active = providers.firstOrNull { it.id == settings.activeProviderId }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("当前配置", style = MaterialTheme.typography.titleSmallEmphasized)
            SetupLine(
                "模型",
                if (active == null) "未连接提供商"
                else "${active.name} · ${settings.activeModel?.takeIf { it.isNotBlank() } ?: active.model}",
            )
            SetupLine("思考", settings.thinkingLevel.displayLabel)
            SetupLine("上下文窗口", formatTokenCount(settings.maxContextTokens.toLong()))
            SetupLine("默认权限", settings.permissionMode.label)
            Text(
                "可在聊天顶部更改模型和思考设置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SetupLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f).padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.62f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}


@Composable
private fun AgentSection(
    container: AppContainer,
    settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var showAgentsDialog by remember { mutableStateOf(false) }
    AgentBehaviorSection(
        settings = settings,
        onPermissionMode = { scope.launch { container.settings.setPermissionMode(it) } },
        onContextLimit = { scope.launch { container.settings.setMaxContextTokens(it) } },
        onIterationLimit = { scope.launch { container.settings.setMaxIterations(it) } },
        onProjectInstructions = { showAgentsDialog = true },
    )
    if (showAgentsDialog) {
        AgentsInstructionsDialog(container, onDismiss = { showAgentsDialog = false })
    }
}

/** Maximum size injected into the system prompt (AgentEngine.readWorkspaceDoc). */
private const val AGENTS_MD_CAP = 16_000

private val AGENTS_MD_TEMPLATE = """# AGENTS.md

## Project
(What this project is, in one or two sentences.)

## Build & run
(Commands to build, run, and test; plain names, e.g. `gradlew assembleDebug`.)

## Conventions
(Code style, naming, file organization the agent should follow.)

## Constraints
(Things the agent must never do in this workspace.)
"""

@Composable
private fun AgentsInstructionsDialog(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) } // null = loading
    var existed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(): Pair<Boolean, String> {
        val fs = container.workspace.currentOnce()
        val node = runCatching { fs.resolve("AGENTS.md") }.getOrNull()
        return if (node != null && node.exists && node.isFile) true to node.readText() else false to ""
    }

    LaunchedEffect(Unit) {
        val (found, content) = runCatching { load() }.getOrElse { false to "" }
        existed = found
        text = if (found) content else AGENTS_MD_TEMPLATE
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("项目指令（AGENTS.md）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!existed) {
                    Text(
                        "此工作区没有 AGENTS.md。可使用下方模板，" +
                            "或在聊天中输入 /init，让代理分析工作区并编写。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text ?: "",
                    onValueChange = {
                        text = it
                        if (it.length > AGENTS_MD_CAP) {
                            error = "仅前 $AGENTS_MD_CAP 个字符会被注入提示词。"
                        } else if (error != null) {
                            error = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 420.dp),
                    label = { Text("AGENTS.md 内容") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = HarnessMono,
                    ),
                )
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val content = text ?: return@TextButton
                    scope.launch {
                        runCatching {
                            val fs = container.workspace.currentOnce()
                            fs.resolve("AGENTS.md").writeText(content)
                        }.onSuccess {
                            existed = true
                            onDismiss()
                        }.onFailure { e -> error = "无法保存：${e.message}" }
                    }
                },
                enabled = text != null,
            ) { Text(if (existed) "保存" else "创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AppearanceSection(container: AppContainer, settings: AppSettings, scope: kotlinx.coroutines.CoroutineScope) {
    SettingsHeader("外观")
    WorkspaceLayoutSection(settings.workspaceLayout) { scope.launch { container.settings.setWorkspaceLayout(it) } }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsAnchor("主题") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("主题", style = MaterialTheme.typography.titleSmall)
                ThemeMode.entries.chunked(2).forEach { modes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        modes.forEach { mode ->
                            ThemeChoice(
                                mode = mode,
                                selected = settings.themeMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                            )
                        }
                    }
                }
            }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsAnchor("动态配色") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("动态配色", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "跟随壁纸配色（Android 12+）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { scope.launch { container.settings.setDynamicColor(it) } },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            SettingsAnchor("进度条样式") {
                ProgressBarStyleSection(settings.progressBarStyle) {
                    scope.launch { container.settings.setProgressBarStyle(it) }
                }
            }
        }
    }
}

@Composable
private fun ChatBehaviorSection(container: AppContainer, settings: AppSettings, scope: kotlinx.coroutines.CoroutineScope) {
    SettingsHeader("聊天行为")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsAnchor("显示实时代码") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
                    .clickable { scope.launch { container.settings.setShowLiveCode(!settings.showLiveCode) } }) {
                    Column(Modifier.weight(1f)) {
                        Text("显示实时代码", style = MaterialTheme.typography.bodyLarge)
                        Text("代理生成代码时，预览文件、行数变化和预估词元用量。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.showLiveCode,
                        onCheckedChange = { scope.launch { container.settings.setShowLiveCode(it) } })
                }
            }
            HorizontalDivider()
            SettingsAnchor("启动时恢复上次聊天") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("启动时恢复上次聊天", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.resumeLastChat) {
                                "开启：启动应用时打开最近的聊天。"
                            } else {
                                "关闭：每次打开应用时新建空白聊天。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.resumeLastChat,
                        onCheckedChange = { scope.launch { container.settings.setResumeLastChat(it) } },
                    )
                }
            }
            HorizontalDivider()
            SettingsAnchor("工作区代码索引（Repo map）") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("工作区代码索引（Repo map）", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.repoMapEnabled) {
                                "开启：将精简的项目符号图谱（类、函数、类型）注入上下文，让代理无需额外搜索即可理解代码结构。"
                            } else {
                                "关闭：代理仅通过工具命令探索文件。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.repoMapEnabled,
                        onCheckedChange = { scope.launch { container.settings.setRepoMapEnabled(it) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(container: AppContainer, settings: AppSettings, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    SettingsHeader("隐私")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("生物识别应用锁", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.biometricLockEnabled) {
                            "开启：打开应用时需要指纹、面容或设备 PIN。"
                        } else {
                            "关闭：无需验证身份即可打开应用。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.biometricLockEnabled,
                    onCheckedChange = { targetState ->
                        val activity = context.findFragmentActivity()
                        if (activity != null) {
                            BiometricAuth.prompt(
                                activity = activity,
                                title = if (targetState) "启用生物识别锁" else "停用生物识别锁",
                                subtitle = "使用指纹、面容或 PIN 确认",
                                onSuccess = {
                                    scope.launch { container.settings.setBiometricLockEnabled(targetState) }
                                },
                            )
                        } else {
                            scope.launch { container.settings.setBiometricLockEnabled(targetState) }
                        }
                    },
                )
            }
            if (settings.biometricLockEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("自动锁定时间", style = MaterialTheme.typography.labelLarge)
                    val timeouts = listOf(0 to "立即", 1 to "1 分钟", 5 to "5 分钟", 15 to "15 分钟")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        timeouts.forEachIndexed { index, (mins, label) ->
                            SegmentedButton(
                                selected = settings.biometricLockTimeoutMinutes == mins,
                                onClick = { scope.launch { container.settings.setBiometricLockTimeout(mins) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = timeouts.size),
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("允许截屏", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.allowScreenshots) {
                            "开启：除屏幕显示密钥或令牌时，其他界面均可截屏。"
                        } else {
                            "关闭：整个应用禁止截屏和最近任务预览。" +
                                "即使开启，密钥界面仍禁止截屏。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.allowScreenshots,
                    onCheckedChange = { scope.launch { container.settings.setAllowScreenshots(it) } },
                )
            }
        }
    }
}

@Composable
private fun PlanningModelSection(
    container: AppContainer,
    settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    onOpenProviders: () -> Unit,
) {
    val providers by container.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalogs by container.providers.catalogs.collectAsStateWithLifecycle(initialValue = emptyMap())
    var pickingRole by remember { mutableStateOf<String?>(null) }
    var managingRoleProvider by remember { mutableStateOf<String?>(null) }

    // The sheet opens on the role's provider when one is set, else the
    // active one, so the list is never empty for a first pick.
    val fallbackProviderId = settings.activeProviderId ?: providers.firstOrNull()?.id

    SettingsHeader("双模型规划")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text("分别设置规划模型与执行模型", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "配置双模型规划。从聊天启用后，" +
                        "规划使用规划模型，执行使用执行模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModelRoleRow(
                label = "规划模型",
                model = settings.planningModel
                    ?: providers.firstOrNull { it.id == (settings.planningProviderId ?: fallbackProviderId) }?.model,
            ) { pickingRole = "plan" }
            ModelRoleRow(
                label = "执行模型",
                model = settings.executionModel
                    ?: providers.firstOrNull { it.id == (settings.executionProviderId ?: fallbackProviderId) }?.model,
            ) { pickingRole = "exec" }
        }
    }

    pickingRole?.let { role ->
        val isPlan = role == "plan"
        ModelPickerSheet(
            providers = providers,
            activeProviderId = (if (isPlan) settings.planningProviderId else settings.executionProviderId)
                ?: fallbackProviderId,
            activeModel = if (isPlan) settings.planningModel else settings.executionModel,
            catalogs = catalogs,
            onDismiss = { pickingRole = null },
            onSelect = { providerId, model ->
                scope.launch {
                    if (isPlan) container.settings.setPlanningModel(providerId, model)
                    else container.settings.setExecutionModel(providerId, model)
                }
                pickingRole = null
            },
            onRefreshCatalog = { providerId ->
                val provider = providers.firstOrNull { it.id == providerId }
                val key = container.providers.apiKey(providerId)
                when {
                    provider == null -> "未知提供商"
                    else -> {
                        val usable = provider.effectiveApiKey(key)
                        if (usable.isBlank()) "此提供商未配置 API 密钥"
                        else when (val result = com.androidharness.app.llm.ModelCatalog.listModels(provider, usable)) {
                            is com.androidharness.app.llm.ModelCatalog.Result.Models -> {
                                container.providers.saveCatalog(providerId, result.models)
                                null
                            }
                            is com.androidharness.app.llm.ModelCatalog.Result.Failed -> result.message
                        }
                    }
                }
            },
            onAddCustomModel = { providerId, model, reasoning ->
                scope.launch { container.providers.addCustomModel(providerId, model, reasoning) }
            },
            onDeleteCustomModel = { providerId, model ->
                scope.launch { container.providers.removeCustomModel(providerId, model) }
            },
            onEnableProvider = { id -> scope.launch { container.providers.setEnabled(id, true) } },
            onManageProviders = {
                managingRoleProvider = role
                pickingRole = null
            },
        )
    }

    managingRoleProvider?.let { role ->
        val isPlan = role == "plan"
        ProviderManagerSheet(
            providers = providers,
            activeProviderId = (if (isPlan) settings.planningProviderId else settings.executionProviderId)
                ?: fallbackProviderId,
            apiKey = container.providers::apiKey,
            onDismiss = { managingRoleProvider = null },
            onSetActive = { providerId ->
                scope.launch {
                    if (isPlan) container.settings.setPlanningModel(providerId, null)
                    else container.settings.setExecutionModel(providerId, null)
                }
                managingRoleProvider = null
            },
            catalogs = catalogs, onRefreshCatalog = container.providers::refreshCatalog,
            onDelete = { providerId ->
                container.providers.delete(providerId)
            },
            onEnabledChange = { id, enabled -> container.providers.setEnabled(id, enabled) },
            onMove = { id, direction, visible -> scope.launch { container.providers.move(id, direction, visible) } },
            onSave = { existing, name, type, baseUrl, model, apiKey, headers ->
                scope.launch {
                    val created = if (existing == null) {
                        container.providers.add(name, type, baseUrl, model, apiKey, headers)
                    } else {
                        val updated = existing.copy(name = name, type = type, baseUrl = baseUrl, model = model, headers = headers)
                        container.providers.update(updated, apiKey)
                        updated
                    }
                    if (isPlan) container.settings.setPlanningModel(created.id, null)
                    else container.settings.setExecutionModel(created.id, null)
                    managingRoleProvider = null
                }
            },
        )
    }
}

@Composable
private fun ModelRoleRow(label: String, model: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                model ?: "提供商默认值",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "选择 $label 模型",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SlashCommandsSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    SettingsAnchor("斜杠命令") {
        SettingsHeader("斜杠命令")
    }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var snippetName by remember { mutableStateOf("") }
            var snippetBody by remember { mutableStateOf("") }
            var showAdd by remember { mutableStateOf(false) }
            TextButton(onClick = { showAdd = !showAdd }) {
                Text(if (showAdd) "取消" else "添加自定义命令")
            }
            AnimatedVisibility(showAdd) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = snippetName, onValueChange = { snippetName = it },
                        label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = snippetBody, onValueChange = { snippetBody = it },
                        label = { Text("提示词（使用 \$ARG 表示参数）") }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = {
                        val n = snippetName.trim(); val b = snippetBody.trim()
                        if (n.isNotBlank() && b.isNotBlank()) {
                            scope.launch { container.snippets.add(n, b) }
                            snippetName = ""; snippetBody = ""; showAdd = false
                        }
                    }) { Text("保存") }
                }
            }
            val snippets by container.snippets.snippets.collectAsStateWithLifecycle(initialValue = emptyList())
            snippets.forEach { snippet ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "/${snippet.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = HarnessMono,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { scope.launch { container.snippets.delete(snippet) } }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(container: AppContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    var exempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("保持任务运行", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "运行任务或终端时保持 CPU 唤醒，避免应用最小化后终端被终止。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.keepAlive,
                    onCheckedChange = { scope.launch { container.settings.setKeepAlive(it) } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("电池优化", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (exempt) "已豁免：Android 不会限制后台运行。"
                        else "已开启：三星等厂商可能会终止后台任务。请为应用排除电池优化，确保长时间任务可靠运行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!exempt) {
                    Button(onClick = {
                        requestBatteryExemption(context)
                        exempt = isIgnoringBatteryOptimizations(context)
                    }) { Text("调整") }
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean =
    com.androidharness.app.ui.common.SystemGrants.isIgnoringBatteryOptimizations(context)

private fun requestBatteryExemption(context: android.content.Context) =
    com.androidharness.app.ui.common.SystemGrants.requestBatteryExemption(context)

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SettingsHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) = SettingsRow(
    leading = {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    },
    title = title,
    subtitle = subtitle,
    onClick = onClick,
    divider = divider,
    trailing = trailing,
)

/** Same row, for the places that need something other than a themed icon up front. */
@Composable
private fun SettingsRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    divider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
    if (divider) {
        HorizontalDivider(
            Modifier.padding(start = 50.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun StatusText(text: String, ok: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (ok) LocalStatusColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DropdownSetting(
    label: String,
    current: String,
    entries: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    divider: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 12.dp),
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "更改 $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Box {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { (value, entryLabel) ->
                    val isFullAccess = value == PermissionMode.FULL_ACCESS.name
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entryLabel,
                                    Modifier.weight(1f),
                                    color = if (isFullAccess) FullAccessOrange else Color.Unspecified,
                                )
                                if (entryLabel == current) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isFullAccess) FullAccessOrange else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesCard(container: AppContainer) = UpdatesSection(container)

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

private const val AUTHOR_URL = "https://github.com/Sanuu7"
private const val REPO_URL = "https://github.com/Sanuu7/AndroidHarness"
private const val LICENSE_URL = "https://github.com/Sanuu7/AndroidHarness/blob/main/LICENSE"

/** Installed version, shared by the About page and the update card. */
private fun appVersionLabel(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "?" }

/** A project AndroidHarness leans on, listed on the About page. */
private data class Credit(val name: String, val role: String, val url: String)

private val BUILT_WITH = listOf(
    Credit("Termux", "Linux 软件包与 shell 工具", "https://github.com/termux"),
    Credit("Shizuku", "无需 Root 的提权 shell 访问", "https://github.com/RikkaApps/Shizuku"),
    Credit("CodeGraph", "图谱工具使用的语义代码索引", "https://github.com/colbymchenry/codegraph"),
    Credit("Caveman", "精简回复模式，改编自 Julius Brussee 的技能", "https://github.com/JuliusBrussee/caveman"),
    Credit("sora-editor", "应用内代码编辑器", "https://github.com/Rosemoe/sora-editor"),
    Credit("Eruda", "网页预览中的开发者工具", "https://github.com/liriliri/eruda"),
)

private val INSPIRED_BY = listOf(
    Credit("Hermes Agent", "代理循环与计划任务", "https://github.com/NousResearch/hermes-agent"),
    Credit("Aider", "代码图谱与编辑工作流", "https://github.com/Aider-AI/aider"),
    Credit("pi (ohmypi)", "终端代理设计", "https://github.com/earendil-works/pi"),
    Credit("OpenCode", "提供商与中继处理", "https://github.com/anomalyco/opencode"),
    Credit("Claude Code", "工具卡片与权限模型", "https://github.com/anthropics"),
    Credit("Roo Code", "审批流程与模式", "https://github.com/RooCodeInc/Roo-Code"),
    Credit("Cline", "审批流程与模式", "https://github.com/cline/cline"),
    Credit("browser-use", "代理驱动的浏览", "https://github.com/browser-use/browser-use"),
    Credit("llama.cpp", "本地服务器兼容性", "https://github.com/ggerganov/llama.cpp"),
)

@Composable
private fun AboutSection(container: AppContainer) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val version = remember(container) { appVersionLabel(container.appContext) }
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HarnessMark(size = 58.dp)
            Text("AndroidHarness", style = MaterialTheme.typography.titleLarge)
            Text(
                "手机上的编程代理。",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Surface(color = scheme.surfaceContainerHigh, shape = CircleShape) {
                Text(
                    "版本 $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }

    SettingsHeader("开发者")
    SettingsPanel(Modifier.fillMaxWidth()) {
        SettingsRow(
            leading = { GitHubMark() },
            title = "Sanuu",
            subtitle = "github.com/Sanuu7",
            onClick = { open(AUTHOR_URL) },
            divider = true,
            trailing = { ExternalLinkIcon() },
        )
        SettingsRow(
            leading = {
                Icon(Icons.Outlined.Code, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            },
            title = "源代码",
            subtitle = "github.com/Sanuu7/AndroidHarness",
            onClick = { open(REPO_URL) },
            trailing = { ExternalLinkIcon() },
        )
    }

    SettingsHeader("许可证")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("MIT 许可证", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Surface(color = scheme.primaryContainer, shape = CircleShape) {
                    Text(
                        "开源",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                "你可以使用、修改和分发，甚至用于商业应用。唯一条件是" +
                    "代码需附带版权声明和许可证文本。本软件不提供任何担保。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { open(LICENSE_URL) }.padding(vertical = 4.dp),
            ) {
                Text("阅读完整许可证", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }

    SettingsHeader("致谢")
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "AndroidHarness 基于开源项目构建，并借鉴了前辈代理工具的设计。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        CreditsGroup("使用的开源项目", BUILT_WITH, open)
        CreditsGroup("灵感来源", INSPIRED_BY, open)
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "各项目保留自己的许可证。AndroidHarness 与这些项目无隶属关系。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** One labelled block of the credits list, separated from the block above it. */
@Composable
private fun CreditsGroup(label: String, credits: List<Credit>, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
    Text(
        label,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = scheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
    credits.forEachIndexed { index, credit ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(credit.url) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(credit.name, style = MaterialTheme.typography.bodyMedium)
                Text(credit.role, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        if (index != credits.lastIndex) {
            HorizontalDivider(
                Modifier.padding(start = 16.dp, end = 16.dp),
                color = scheme.outlineVariant.copy(alpha = 0.3f),
            )
        }
    }
}

/** GitHub's mark, tinted with the theme like every other icon in settings. */
@Composable
private fun GitHubMark() {
    Icon(
        painterResource(R.drawable.ic_github),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun ExternalLinkIcon() {
    Icon(
        Icons.AutoMirrored.Outlined.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun ChatsBackupSection(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<com.androidharness.app.data.ChatImportPreview?>(null) }

    val suggestedName = remember {
        "androidharness-chats-" +
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) +
            ".json"
    }

    fun report(text: String, error: Boolean = false) {
        statusText = text
        statusIsError = error
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = null
            try {
                val r = container.chatBackup.exportTo(uri)
                report("已导出 ${r.sessions} 个聊天（${r.messages} 条消息）。")
            } catch (e: ChatBackupException) {
                report(e.message ?: "导出失败。", error = true)
            } catch (e: Exception) {
                report("导出失败：${e.message ?: "未知错误"}", error = true)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            statusText = null
            try {
                val preview = container.chatBackup.prepareImport(uri)
                if (preview.missingWorkspaces.isNotEmpty()) {
                    restorePreview = preview
                    return@launch
                }
                val r = container.chatBackup.importPrepared(preview, emptyMap())
                report(
                    "已导入 ${r.sessions} 个聊天（${r.messages} 条消息）" +
                        if (r.skipped > 0) "，${r.skipped} 个已存在。" else ".",
                )
            } catch (e: ChatBackupException) {
                report(e.message ?: "导入失败。", error = true)
            } catch (e: Exception) {
                report("导入失败：${e.message ?: "未知错误"}", error = true)
            } finally {
                busy = false
            }
        }
    }

    restorePreview?.let { preview -> ChatRestoreDialog(container, preview,
        onDismiss = { restorePreview = null }, onImported = { result ->
            restorePreview = null
            report("已导入 ${result.sessions} 个聊天（${result.messages} 条消息），${result.skipped} 个已存在。")
        }) }
    SettingsPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("聊天备份", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "将所有聊天保存到文件，或从文件恢复。" +
                            "文件仅包含聊天和消息，不含 API 密钥、提供商或设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch(suggestedName) }, enabled = !busy) {
                    Text("导出")
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                    },
                    enabled = !busy,
                ) {
                    Text("导入")
                }
            }
            if (busy) ThinLinearProgress(Modifier.fillMaxWidth())
            statusText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
