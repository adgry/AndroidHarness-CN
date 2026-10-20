package com.androidharness.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.androidharness.app.agent.RunManager
import com.androidharness.app.data.MessageHit
import com.androidharness.app.data.db.ProjectEntity
import com.androidharness.app.data.db.SessionEntity
import com.androidharness.app.ui.common.HarnessMark
import com.androidharness.app.ui.theme.LocalStatusColors
import com.androidharness.app.ui.common.formatRelativeTime
import com.androidharness.app.ui.theme.expressiveEffectsSpec
import com.androidharness.app.ui.theme.expressiveSpatialSpec

internal enum class SidebarProjectAction { OPEN, RENAME, PIN, ARCHIVE, DELETE }
internal enum class SidebarChatAction { RENAME, PIN, ARCHIVE, DETAILS, DELETE }
internal enum class SidebarActivity { WORKING, NEEDS_YOU }

internal fun sidebarActivity(running: Boolean, pendingPlan: String?, live: RunManager.LiveRunState? = null): SidebarActivity? = when {
    !pendingPlan.isNullOrBlank() || live?.pendingApproval != null || live?.pendingQuestion != null ||
        live?.pendingEnvironment != null || !live?.pendingPlan.isNullOrBlank() -> SidebarActivity.NEEDS_YOU
    running || live?.running == true -> SidebarActivity.WORKING
    else -> null
}

/** Pinned and archived rows appear once; legacy chats keep a reachable home. */
internal data class SidebarChats(
    val pinned: List<SessionEntity>, val projects: Map<String, List<SessionEntity>>,
    val other: List<SessionEntity>, val archived: List<SessionEntity>,
) {
    companion object {
        fun of(projects: List<ProjectEntity>, sessions: List<SessionEntity>, pinned: Set<String>, archived: Set<String>): SidebarChats {
            val known = projects.mapTo(mutableSetOf()) { it.id }
            val active = sessions.filterNot { it.id in archived }
            val rest = active.filterNot { it.id in pinned }
            return SidebarChats(active.filter { it.id in pinned }, rest.filter { it.projectId in known }.groupBy { it.projectId!! },
                rest.filter { it.projectId !in known }, sessions.filter { it.id in archived })
        }
    }
}

/** Separate conversation and project views, shared by phone and desktop. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectNavigationSidebar(
    projects: List<ProjectEntity>, sessions: List<SessionEntity>, selectedSession: String?, selectedProject: String?,
    pinned: Set<String>, archived: Set<String>, query: String, onQueryChange: (String) -> Unit,
    messageHits: List<MessageHit>, fuzzy: Boolean, onFuzzyChange: () -> Unit,
    activity: @Composable (SessionEntity) -> SidebarActivity?,
    onNewChat: () -> Unit, onChat: (String) -> Unit, onMessage: (MessageHit) -> Unit,
    onProjectChat: (ProjectEntity) -> Unit, onProjects: () -> Unit,
    onChatActions: (SessionEntity) -> Unit, onAction: (SessionEntity, SidebarChatAction) -> Unit,
    onBuild: () -> Unit, onTerminal: () -> Unit, onAutomation: () -> Unit, onProviders: () -> Unit, onSettings: () -> Unit,
    workspaceLabel: String, workspaceSummary: String, onWorkspaces: () -> Unit,
    onCollapse: () -> Unit, currentRoute: String?, projectCreationEnabled: Boolean = true,
    changesSummary: String? = null, onChanges: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    projectsView: Boolean = false, onProjectsViewChange: (Boolean) -> Unit = {},
    projectPins: Set<String> = emptySet(), projectArchives: Set<String> = emptySet(),
    onProjectAction: (ProjectEntity, SidebarProjectAction) -> Unit = { _, _ -> },
) {
    val scheme = MaterialTheme.colorScheme
    var expandedProjects by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(projectsView) { showArchived = false }
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(0.dp) }
    val currentProject = sessions.firstOrNull { it.id == selectedSession }?.projectId ?: selectedProject
    val expanded = expandedProjects
    val grouped = remember(projects, sessions, pinned, archived) { SidebarChats.of(projects, sessions, pinned, archived) }
    val matching = remember(sessions, projects, query) {
        val names = projects.associate { it.id to it.name }
        sessions.filter { it.title.contains(query.trim(), true) || names[it.projectId]?.contains(query.trim(), true) == true }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    @Composable fun Chat(chat: SessionEntity, indented: Boolean = false, showProject: Boolean = false) {
        SidebarChatRow(chat, chat.id == selectedSession, chat.id in pinned, chat.id in archived, activity(chat),
            projectName = if (showProject) projects.firstOrNull { it.id == chat.projectId }?.name else null,
            indented = indented, onClick = { keyboard?.hide(); onChat(chat.id) }, onActions = { onChatActions(chat) },
            onAction = { onAction(chat, it) })
    }
    @Composable fun Footer() {
        Column(Modifier.fillMaxWidth().onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
            .padding(horizontal = 10.dp, vertical = 8.dp)) {
            SidebarNavRow("工具", Icons.Outlined.Widgets, expanded = toolsOpen, onClick = { toolsOpen = !toolsOpen })
            if (toolsOpen) Column(Modifier.padding(start = 16.dp)) {
                SidebarNavRow("终端", Icons.Outlined.Terminal, currentRoute?.startsWith("terminal") == true, onClick = onTerminal)
                SidebarNavRow("构建与测试", Icons.Outlined.Build, currentRoute?.startsWith("build-test") == true, onClick = onBuild)
                SidebarNavRow("定时任务", Icons.Outlined.Schedule, currentRoute == "automation", onClick = onAutomation)
                SidebarNavRow(workspaceLabel, Icons.Outlined.FolderOpen, subtitle = workspaceSummary, onClick = onWorkspaces)
                changesSummary?.let { SidebarNavRow(it, Icons.Outlined.Difference, onClick = onChanges) }
            }
            SidebarNavRow("服务商", Icons.Outlined.Hub, currentRoute == "providers", onClick = onProviders)
            SidebarNavRow("设置", Icons.Outlined.Settings, currentRoute == "settings", onClick = onSettings)
        }
    }
    // The drawer's opening focus request should land here, not in chat search.
    Surface(color = scheme.surface, modifier = Modifier.fillMaxSize().focusable().semantics { contentDescription = "项目与聊天侧边栏" }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short = maxHeight < 440.dp
            val narrow = maxWidth < 280.dp
            @Composable fun Header() {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        HarnessMark(26.dp, Modifier.semantics { contentDescription = "AndroidHarness 图标" })
                        Text("AndroidHarness 中文版", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = onCollapse) { Icon(Icons.AutoMirrored.Outlined.MenuOpen, "收起侧边栏", Modifier.size(19.dp)) }
                    }
                    Button(onClick = onNewChat, enabled = projectCreationEnabled, shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).heightIn(min = 44.dp)) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp))
                        Text("新聊天", style = MaterialTheme.typography.labelLarge)
                    }
                    BasicTextField(query, onQueryChange, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                        cursorBrush = SolidColor(scheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                            .semantics { contentDescription = "搜索聊天和消息" },
                        decorationBox = { field ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(scheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
                                .padding(start = 12.dp, end = if (query.isBlank()) 12.dp else 0.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) Text(if (narrow) "搜索聊天" else "搜索聊天和消息",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                                    field()
                                }
                                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Outlined.Close, "清除搜索", Modifier.size(17.dp))
                                }
                            }
                        })
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).background(scheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(3.dp)) {
                            listOf(false to "聊天", true to "项目").forEach { (projectTab, label) ->
                                val selectedTab = projectsView == projectTab
                                val tabColor by animateColorAsState(
                                    if (selectedTab) scheme.secondaryContainer else scheme.surfaceContainer,
                                    animationSpec = expressiveEffectsSpec(), label = "sidebar tab color",
                                )
                                val tabCorner by animateDpAsState(
                                    if (selectedTab) 18.dp else 9.dp,
                                    animationSpec = expressiveSpatialSpec(), label = "sidebar tab shape",
                                )
                                Surface(onClick = { onProjectsViewChange(projectTab) }, shape = RoundedCornerShape(tabCorner),
                                    color = tabColor,
                                    modifier = Modifier.weight(1f).semantics { role = Role.Tab; selected = selectedTab }) {
                                    Box(Modifier.heightIn(min = 42.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelLarge,
                                            color = if (selectedTab) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { onQueryChange(""); showArchived = !showArchived }) {
                            Icon(if (showArchived) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Archive,
                                if (showArchived) "返回未归档${if (projectsView) "项目" else "聊天"}" else "显示已归档${if (projectsView) "项目" else "聊天"}",
                                Modifier.size(19.dp), tint = if (showArchived) scheme.primary else scheme.onSurfaceVariant)
                        }
                    }
                    if (query.isNotBlank()) TextButton(onClick = onFuzzyChange,
                        modifier = Modifier.padding(horizontal = 14.dp).semantics { stateDescription = if (fuzzy) "包含文字" else "匹配词语" }) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (fuzzy) "包含文字" else "匹配词语", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                if (!short) Header()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 2.dp, bottom = 10.dp)) {
                    if (short) item(key = "sidebar-header") { Header() }
                    if (query.isNotBlank()) {
                        if (matching.isNotEmpty()) {
                            item { SidebarSectionHeader("聊天", true, null) }
                            items(matching, key = { "search:${it.id}" }) { Chat(it, showProject = true) }
                        }
                        if (messageHits.isNotEmpty()) {
                            item { SidebarSectionHeader("消息", true, null) }
                            items(messageHits, key = { "message:${it.messageId}" }) { hit ->
                                MessageHitRow(hit, query, fuzzy) { keyboard?.hide(); onMessage(hit) }
                            }
                        }
                        if (matching.isEmpty() && messageHits.isEmpty()) item {
                            Text("未找到聊天，请尝试其他标题或消息。", Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    } else if (projectsView) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (showArchived) "已归档项目" else "你的项目", Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                TextButton(onClick = onProjects) { Text("管理项目", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        val visibleProjects = projects.filter { (it.id in projectArchives) == showArchived }
                            .sortedBy { if (it.id in projectPins) 0 else 1 }
                        visibleProjects.forEachIndexed { index, project ->
                            val chats = sessions.filter { it.projectId == project.id && it.id !in archived }
                                .sortedBy { if (it.id in pinned) 0 else 1 }
                            if (!showArchived && project.id in projectPins && index == 0) item { SidebarSectionHeader("已置顶", true, null) }
                            if (!showArchived && project.id !in projectPins && index > 0 && visibleProjects[index - 1].id in projectPins)
                                item { SidebarSectionHeader("所有项目", true, null) }
                            item(key = "project:${project.id}") {
                                SidebarProjectRow(project, project.id in expanded, project.id in projectPins, project.id in projectArchives,
                                    project.id == currentProject, chats.size,
                                    onExpand = { expandedProjects = if (project.id in expanded) expanded - project.id else expanded + project.id },
                                    onNewChat = { onProjectChat(project) }, creationEnabled = projectCreationEnabled,
                                    onAction = { onProjectAction(project, it) })
                            }
                            if (project.id in expanded) {
                                items(chats, key = { "project-chat:${it.id}" }) { Chat(it, indented = true) }
                                if (chats.isEmpty()) item(key = "empty:${project.id}") {
                                    Text("暂无聊天", Modifier.padding(start = 54.dp, top = 4.dp, bottom = 12.dp),
                                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (visibleProjects.isEmpty()) item {
                            Text(if (showArchived) "暂无已归档项目" else "添加项目，整理其中的聊天和工作区。",
                                Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    } else if (showArchived) {
                        item { SidebarSectionHeader("已归档聊天", true, null) }
                        items(grouped.archived, key = { "archive:${it.id}" }) { Chat(it, showProject = true) }
                        if (grouped.archived.isEmpty()) item {
                            Text("暂无已归档聊天", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    } else {
                        if (grouped.pinned.isNotEmpty()) {
                            item { SidebarSectionHeader("已置顶", true, null) }
                            items(grouped.pinned, key = { "pinned:${it.id}" }) { Chat(it, showProject = true) }
                        }
                        val recent = sessions.filter { it.id !in pinned && it.id !in archived }
                        if (recent.isNotEmpty()) {
                            item { SidebarSectionHeader("最近聊天", true, null) }
                            items(recent, key = { "chat:${it.id}" }) { Chat(it, showProject = true) }
                        }
                        if (recent.isEmpty() && grouped.pinned.isEmpty()) item {
                            Text("你的聊天会显示在这里。", Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (short) item { HorizontalDivider(color = scheme.outlineVariant); Footer() }
                }
                if (!short) { HorizontalDivider(color = scheme.outlineVariant); Footer() }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (short) 8.dp else footerHeight + 8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarProjectRow(project: ProjectEntity, expanded: Boolean, pinned: Boolean, archived: Boolean,
    selected: Boolean, count: Int, onExpand: () -> Unit, onNewChat: () -> Unit, creationEnabled: Boolean,
    onAction: (SidebarProjectAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
        .background(if (selected) scheme.surfaceContainer else scheme.surface, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).secondaryClick { menu = true }
            .combinedClickable(onClick = onExpand, onLongClick = { menu = true })
            .semantics { contentDescription = "${if (expanded) "收起" else "展开"} ${project.name}"; stateDescription = if (expanded) "已展开" else "已收起" }
            .heightIn(min = 48.dp).padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null, Modifier.size(15.dp), tint = scheme.onSurfaceVariant)
            Box(Modifier.size(26.dp).background(scheme.secondaryContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
            }
            Text(project.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(12.dp), tint = scheme.onSurfaceVariant)
            Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "项目 ${project.name} 的操作", Modifier.size(18.dp), tint = scheme.onSurfaceVariant) }
            DropdownMenu(menu, { menu = false }) {
                if (!archived) DropdownMenuItem(text = { Text("新聊天") }, leadingIcon = { Icon(Icons.Outlined.Add, null) },
                    enabled = creationEnabled, modifier = Modifier.semantics { contentDescription = "在 ${project.name} 中新建聊天" },
                    onClick = { menu = false; onNewChat() })
                listOf(Triple(SidebarProjectAction.OPEN, "打开项目", Icons.Outlined.FolderOpen),
                    Triple(SidebarProjectAction.RENAME, "重命名项目", Icons.Outlined.Edit),
                    Triple(SidebarProjectAction.PIN, if (pinned) "取消置顶项目" else "置顶项目", Icons.Outlined.PushPin),
                    Triple(SidebarProjectAction.ARCHIVE, if (archived) "恢复项目" else "归档项目", Icons.Outlined.Archive)).forEach { (action, label, icon) ->
                    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { menu = false; onAction(action) })
                }
                if (project.kind != com.androidharness.app.workspace.WorkspaceManager.KIND_APP) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("删除项目", color = scheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = scheme.error) },
                        onClick = { menu = false; onAction(SidebarProjectAction.DELETE) })
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(label: String, expanded: Boolean, onClick: (() -> Unit)?) {
    val contents: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = if (onClick == null) 32.dp else 44.dp).padding(start = 20.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (label == "已置顶") {
                Icon(Icons.Outlined.PushPin, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onClick != null) Icon(if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick == null) contents() else Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.semantics { contentDescription = "$label 聊天"; stateDescription = if (expanded) "已展开" else "已收起" }) { contents() }
}

@Composable
private fun SidebarNavRow(label: String, icon: ImageVector, selected: Boolean = false, subtitle: String? = null,
    expanded: Boolean? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = if (selected) scheme.surfaceContainerHigh else scheme.surface,
        shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected; expanded?.let { stateDescription = if (it) "已展开" else "已收起" }
        }) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (expanded != null) Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                null, Modifier.size(15.dp), tint = scheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarChatRow(chat: SessionEntity, selected: Boolean, pinned: Boolean, archived: Boolean,
    activity: SidebarActivity?, projectName: String?, indented: Boolean, onClick: () -> Unit,
    onActions: () -> Unit, onAction: (SidebarChatAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val statusColors = LocalStatusColors.current
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = if (indented) 30.dp else 10.dp, end = 10.dp, top = 2.dp, bottom = 2.dp)) {
        if (indented) Box(Modifier.width(1.dp).height(56.dp).background(scheme.outlineVariant))
        Row(Modifier.weight(1f).padding(start = if (indented) 5.dp else 0.dp)
            .background(if (selected) scheme.primary.copy(alpha = .07f) else if (focused) scheme.surfaceContainerHigh else scheme.surface, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).secondaryClick(onActions).pointerHoverIcon(PointerIcon.Hand)
                .onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { event ->
                    if (event.key == Key.Menu || event.key == Key.F10 && event.isShiftPressed) {
                        if (event.type == KeyEventType.KeyUp) onActions(); true
                    } else false
                }.combinedClickable(onClick = onClick, onLongClick = onActions, onLongClickLabel = "聊天操作")
                .semantics { this.selected = selected; contentDescription = "打开 ${chat.title}" }
                .heightIn(min = 56.dp).padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(chat.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = if (LocalDensity.current.fontScale > 1.3f) 2 else 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val color = when (activity) { SidebarActivity.WORKING -> statusColors.success; SidebarActivity.NEEDS_YOU -> statusColors.warning; null -> scheme.onSurfaceVariant }
                        if (activity == SidebarActivity.WORKING) Box(Modifier.size(6.dp).background(color, CircleShape))
                        if (activity == SidebarActivity.NEEDS_YOU) Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(13.dp), tint = color)
                        Text(listOfNotNull(when (activity) { SidebarActivity.WORKING -> "正在处理"; SidebarActivity.NEEDS_YOU -> "需要你处理"; null -> projectName },
                            if (archived) "已归档" else null,
                            if (activity == null) formatRelativeTime(chat.updatedAt) else null).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pinned) Icon(Icons.Outlined.PushPin, "已置顶", Modifier.size(13.dp), tint = scheme.onSurfaceVariant)
                if (selected) Icon(Icons.Outlined.Check, "当前聊天", Modifier.size(15.dp), tint = scheme.primary)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.Outlined.MoreHoriz, "${chat.title} 的操作", Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
                DropdownMenu(menu, { menu = false }) {
                    listOf(Triple(SidebarChatAction.RENAME, "重命名", Icons.Outlined.Edit),
                        Triple(SidebarChatAction.PIN, if (pinned) "取消置顶" else "置顶", Icons.Outlined.PushPin),
                        Triple(SidebarChatAction.DETAILS, "聊天详情", Icons.Outlined.Info),
                        Triple(SidebarChatAction.ARCHIVE, if (archived) "取消归档" else "归档", Icons.Outlined.Archive),
                        Triple(SidebarChatAction.DELETE, "删除", Icons.Outlined.DeleteOutline)).forEach { (action, label, icon) ->
                        DropdownMenuItem(text = { Text(label, color = if (action == SidebarChatAction.DELETE) scheme.error else scheme.onSurface) },
                            leadingIcon = { Icon(icon, null, tint = if (action == SidebarChatAction.DELETE) scheme.error else scheme.onSurfaceVariant) },
                            onClick = { menu = false; onAction(action) })
                    }
                }
            }
        }
    }
}
