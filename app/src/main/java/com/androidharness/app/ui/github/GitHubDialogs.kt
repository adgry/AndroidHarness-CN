package com.androidharness.app.ui.github

import com.androidharness.app.ui.common.AppLinearProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.automation.AutomationSchedule
import com.androidharness.app.automation.AutomationTask
import com.androidharness.app.github.*
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun GitHubDialog(title: String, busy: Boolean, onDismiss: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(12.dp).fillMaxHeight(0.9f)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                footer()
                if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
            }
        }
    }
}

@Composable
fun GitHubImportDialog(container: AppContainer, onDismiss: () -> Unit, onImported: () -> Unit = onDismiss,
    activateWorkspace: Boolean = true, onProjectImported: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var more by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val environment by container.linuxEnv.state.collectAsStateWithLifecycle()
    val gitToolsReady = environment is com.androidharness.app.data.env.EnvState.Ready && container.linuxEnv.gitToolsReady
    fun load() {
        busy = true
        scope.launch {
            try {
                val next = container.githubRepositories.listRepositories(page)
                repos = (repos + next).distinct(); page++; more = next.size == 30; message = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = container.github.safeMessage(e) }
            finally { busy = false }
        }
    }
    GitHubDialog("从 GitHub 导入", busy, onDismiss) {
        Text("将仓库克隆到独立的私有工作区。无需登录即可导入公开仓库。",
            style = MaterialTheme.typography.bodySmall)
        if (!gitToolsReady) {
            Text("在此设备上安装一次 Git 工具，即可导入仓库。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                busy = true; message = "正在安装 Git 工具…"
                scope.launch {
                    try {
                        container.linuxEnv.install(container.linuxEnv.corePackages).getOrThrow()
                        check(container.linuxEnv.gitToolsReady) { (container.linuxEnv.state.value as? com.androidharness.app.data.env.EnvState.Failed)?.message ?: "无法安装 Git 工具，请重试。" }
                        message = "Git 工具已就绪。"
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = container.github.safeMessage(e) }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("安装 Git 工具") }
        }
        OutlinedTextField(input, onValueChange = { input = it }, label = { Text("仓库网址或 owner/repository") },
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            busy = true; message = "正在导入仓库…"
            scope.launch {
                try {
                    var projectId: String? = null
                    container.githubRepositories.importRepository(input, activateWorkspace) { projectId = it }
                    projectId?.let(onProjectImported)
                    onImported()
                }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = container.github.safeMessage(e) }
                finally { busy = false }
            }
        }, enabled = !busy && gitToolsReady && input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("导入仓库") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        HorizontalDivider()
        OutlinedButton(onClick = ::load, enabled = !busy && more, modifier = Modifier.fillMaxWidth()) {
            Text(if (repos.isEmpty()) "浏览我的仓库" else "加载更多仓库")
        }
        repos.forEach { repo ->
            OutlinedButton(onClick = { input = repo }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(repo) }
        }
    }
}

/** Entry point from chat; both OAuth and PAT connections share the same controls. */
@Composable
fun GitHubWorkspaceDialog(container: AppContainer, projectId: String, projectName: String,
    onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val connection by container.github.state.collectAsStateWithLifecycle()
    if (connection.login == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("登录 GitHub") },
            text = { Text("尚未连接 GitHub。请登录 GitHub，或在设置中添加个人访问令牌，以导入私有仓库和发布作品。") },
            confirmButton = {
                TextButton(onClick = { onDismiss(); onOpenSettings() }) { Text("登录") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        )
    } else {
        GitHubPublishDialog(container, projectId, projectName, onDismiss)
    }
}

@Composable
fun GitHubPublishDialog(container: AppContainer, projectId: String, projectName: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val environment by container.linuxEnv.state.collectAsStateWithLifecycle()
    var fs by remember(projectId) { mutableStateOf<WorkspaceFs?>(null) }
    var state by remember(projectId) { mutableStateOf<GitHubRepoState?>(null) }
    var paths by remember(projectId) { mutableStateOf<Set<String>>(emptySet()) }
    var directory by remember(projectId) { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var repositoryInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preset by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val previousState = state
        val previousPaths = paths
        val previousDirectory = directory
        // Failed refreshes must not leave controls targeting an old repository.
        fs = null
        state = null
        paths = emptySet()
        directory = ""
        val project = container.workspace.projects.first().firstOrNull { it.id == projectId }
            ?: error("此工作区已移除。")
        val captured = container.workspace.fsFor(project)
        fs = captured
        if (!container.linuxEnv.gitToolsReady) return
        val snapshot = try { container.githubRepositories.inspect(captured) }
        catch (e: GitHubOperationFailure) {
            // A plain folder or an unconnected local repository needs setup controls, not an error.
            if (e.stage == "Repository" && (e.message.orEmpty().contains("not a git repository", ignoreCase = true) ||
                    e.message.orEmpty().contains("This workspace has no GitHub origin"))) {
                return
            }
            throw e
        }
        state = snapshot
        // Refresh keeps surviving selections only within the reviewed repository/branch.
        if (previousState?.remoteUrl == snapshot.remoteUrl && previousState.branch == snapshot.branch) {
            val tree = GitChangeTree(snapshot.changes)
            paths = previousPaths.intersect(tree.paths)
            directory = tree.survivingDirectory(previousDirectory)
        }
    }
    fun act(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val failure = container.github.safeMessage(e)
                // A commit can succeed even when its push fails. Refresh HEAD for an immediate push retry.
                try { refresh() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { }
                message = failure
            }
            finally { busy = false }
        }
    }
    LaunchedEffect(projectId) {
        busy = true
        try { refresh() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = container.github.safeMessage(e) }
        finally { busy = false }
    }
    if (importing) {
        GitHubImportDialog(container, onDismiss = { importing = false }, onImported = onDismiss)
        return
    }
    GitHubDialog("提交并推送", busy, onDismiss, footer = {
        state?.takeIf { it.changes.isNotEmpty() }?.let { snapshot ->
            GitHubCommitFooter(paths.size, snapshot.changes.size, commitMessage, { commitMessage = it }, !busy) {
                act {
                    message = container.githubRepositories.publish(requireNotNull(fs), snapshot, paths, commitMessage, true)
                    refresh()
                }
            }
        }
    }) {
        Text(projectName, style = MaterialTheme.typography.titleMedium)
        if (environment !is com.androidharness.app.data.env.EnvState.Ready || !container.linuxEnv.gitToolsReady) {
            OutlinedButton(onClick = { act {
                container.linuxEnv.install(container.linuxEnv.corePackages).getOrThrow()
                check(container.linuxEnv.gitToolsReady) { (container.linuxEnv.state.value as? com.androidharness.app.data.env.EnvState.Failed)?.message ?: "无法安装 Git 工具，请重试。" }
                refresh(); message = "Git 工具已就绪。"
            } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("安装或修复 Git 工具") }
        }
        state?.let { snapshot ->
            Text(snapshot.remoteUrl.removePrefix("https://github.com/").removeSuffix(".git"))
            Text("分支：${snapshot.branch}", style = MaterialTheme.typography.bodySmall)
            Text("${snapshot.changes.size} 个文件已更改 · 不含应用历史", style = MaterialTheme.typography.bodySmall)
            if (snapshot.changes.isNotEmpty()) {
                GitHubChangePicker(snapshot.changes, paths, directory, { directory = it }, { paths = it }, !busy)
            } else Text("没有未提交的更改，可推送现有本地提交。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { act {
                message = container.githubRepositories.publish(requireNotNull(fs), snapshot, emptySet(), "", false)
                refresh()
            } }, enabled = !busy && snapshot.head.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("推送现有提交") }
            OutlinedButton(onClick = { preset = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("保存可复用的推送预设") }
        }
        if (state == null && !busy) {
            OutlinedButton(onClick = { importing = true }, modifier = Modifier.fillMaxWidth()) { Text("从 GitHub 导入") }
            OutlinedTextField(repositoryInput, onValueChange = { repositoryInput = it }, label = { Text("现有 GitHub 仓库") },
                modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { act {
                container.githubRepositories.connect(requireNotNull(fs), repositoryInput)
                refresh(); message = "仓库已连接。"
            } }, enabled = fs?.shellRoot != null && container.linuxEnv.gitToolsReady && repositoryInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("连接仓库") }
        }
        OutlinedButton(onClick = { act { refresh(); message = null } }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("刷新仓库") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (preset && state != null) GitHubPushPresetDialog(container, projectId, projectName,
        initial = GitHubPushPreset(requireNotNull(state).remoteUrl, requireNotNull(state).branch,
            commitMessage.ifBlank { "来自 AndroidHarness 的更新" }, paths.toList()), onDismiss = { preset = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitHubPushPresetDialog(container: AppContainer, projectId: String, projectName: String,
    onDismiss: () -> Unit, initial: GitHubPushPreset? = null, task: AutomationTask? = null) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(initial ?: task?.githubPush) }
    var title by remember { mutableStateOf(task?.title ?: "将 $projectName 推送到 GitHub") }
    var commitMessage by remember { mutableStateOf(initial?.commitMessage ?: task?.githubPush?.commitMessage ?: "来自 AndroidHarness 的更新") }
    var schedule by remember { mutableStateOf(task?.schedule ?: AutomationSchedule.MANUAL) }
    var hour by remember { mutableStateOf((task?.hour ?: 8).toString()) }
    var minute by remember { mutableStateOf((task?.minute ?: 0).toString()) }
    var allChanges by remember { mutableStateOf(task?.githubPush?.paths?.isEmpty() == true) }
    var busy by remember { mutableStateOf(snapshot == null) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(projectId) {
        if (snapshot == null) {
            try {
                val project = container.workspace.projects.first().first { it.id == projectId }
                val state = container.githubRepositories.inspect(container.workspace.fsFor(project))
                snapshot = GitHubPushPreset(state.remoteUrl, state.branch, commitMessage, state.changes.map { it.path })
            } catch (e: Exception) { message = container.github.safeMessage(e) } finally { busy = false }
        }
    }
    GitHubDialog(if (task == null) "GitHub 推送预设" else "编辑 GitHub 推送预设", busy, onDismiss) {
        Text("$projectName · ${snapshot?.branch.orEmpty()}", style = MaterialTheme.typography.titleMedium)
        snapshot?.let { Text(it.remoteUrl, style = MaterialTheme.typography.bodySmall) }
        Text("在自动化中复用此仓库和分支。预设会直接运行 Git、检查推送结果，并在工作区有任务运行时停止。它使用你当前的 GitHub 连接。",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(title, onValueChange = { title = it }, label = { Text("预设名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commitMessage, onValueChange = { commitMessage = it }, label = { Text("提交说明") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().clickable { allChanges = !allChanges }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(allChanges, onCheckedChange = null)
            Text("包含此工作区今后的所有更改", modifier = Modifier.weight(1f))
        }
        if (!allChanges) Text("所选路径：${snapshot?.paths?.joinToString(", ")?.ifBlank { "尚未选择路径。请启用今后的更改，或在“提交并推送”中选择文件。" }}",
            style = MaterialTheme.typography.bodySmall)
        Text("定时安排", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AutomationSchedule.MANUAL, AutomationSchedule.HOURLY, AutomationSchedule.DAILY).forEach { choice ->
                FilterChip(selected = schedule == choice, onClick = { schedule = choice }, label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        if (schedule == AutomationSchedule.DAILY) {
            OutlinedTextField(hour, onValueChange = { hour = it.filter(Char::isDigit).take(2) }, label = { Text("小时（0–23）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minute, onValueChange = { minute = it.filter(Char::isDigit).take(2) }, label = { Text("分钟（0–59）") }, modifier = Modifier.fillMaxWidth())
        }
        if (schedule != AutomationSchedule.MANUAL) Text("Android 可能延迟定时任务。失败或受阻的推送仍会显示在自动化历史中。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            busy = true
            scope.launch {
                try {
                    val saved = requireNotNull(snapshot).copy(commitMessage = commitMessage.trim(), paths = if (allChanges) emptyList() else requireNotNull(snapshot).paths)
                    val base = task ?: AutomationTask(title = title, prompt = "将已审阅的更改发布到 GitHub", projectId = projectId, projectName = projectName)
                    container.automation.save(base.copy(title = title.trim(), githubPush = saved, schedule = schedule,
                        hour = hour.toIntOrNull() ?: -1, minute = minute.toIntOrNull() ?: -1, enabled = true))
                    onDismiss()
                } catch (e: Exception) { message = container.github.safeMessage(e) } finally { busy = false }
            }
        }, enabled = !busy && snapshot != null && title.isNotBlank() && commitMessage.isNotBlank() &&
            (allChanges || snapshot?.paths?.isNotEmpty() == true), modifier = Modifier.fillMaxWidth()) { Text("保存推送预设") }
        if (task != null) TextButton(onClick = { container.automation.delete(task.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("删除预设") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
