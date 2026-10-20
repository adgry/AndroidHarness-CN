package com.androidharness.app.ui.automation

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidharness.app.AppContainer
import com.androidharness.app.automation.AutomationSchedule
import com.androidharness.app.automation.AutomationTask
import com.androidharness.app.llm.effectiveApiKey
import com.androidharness.app.data.AppSettings
import com.androidharness.app.ui.chat.components.ModelPickerSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.Date

private enum class SchedulePage { DETAILS, CHAT, WORKSPACE, PROVIDER, MODEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleMessageSheet(
    container: AppContainer,
    initial: AutomationTask?,
    onDismiss: () -> Unit,
    onSaved: (AutomationTask) -> Unit = {},
    onCancel: (() -> Unit)? = null,
    defaultProjectId: String? = null,
) {
    val projects by container.workspace.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by container.sessions.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val providers by container.providers.providers.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalogs by container.providers.catalogs.collectAsStateWithLifecycle(initialValue = emptyMap())
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val preferences = remember { context.getSharedPreferences("schedule_preferences", android.content.Context.MODE_PRIVATE) }
    var prompt by rememberSaveable(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var projectId by rememberSaveable(initial?.id, defaultProjectId) { mutableStateOf(initial?.projectId ?: defaultProjectId) }
    var sessionId by rememberSaveable(initial?.id) { mutableStateOf(initial?.targetSessionId) }
    var providerId by rememberSaveable(initial?.id) { mutableStateOf(initial?.providerId) }
    var model by rememberSaveable(initial?.id) { mutableStateOf(initial?.model) }
    var schedule by rememberSaveable(initial?.id) { mutableStateOf(initial?.schedule ?: AutomationSchedule.ONCE) }
    var at by rememberSaveable(initial?.id) { mutableLongStateOf(initial?.scheduledAt ?: initial?.nextRunAt ?: ZonedDateTime.now().plusHours(1).withSecond(0).withNano(0).toInstant().toEpochMilli()) }
    var unmetered by rememberSaveable(initial?.id) { mutableStateOf(initial?.unmeteredOnly ?: preferences.getBoolean("unmetered", false)) }
    var page by rememberSaveable { mutableStateOf(SchedulePage.DETAILS) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var query by remember(page) { mutableStateOf("") }
    LaunchedEffect(page) { focus.clearFocus(); keyboard?.hide() }
    val existing = initial?.let { task -> container.automation.repository.tasks.value.any { it.id == task.id } } == true
    val project = projects.firstOrNull { it.id == projectId }
    val session = sessions.firstOrNull { it.id == sessionId }
    val provider = providers.firstOrNull { it.id == providerId }
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(at).atZone(zone)
    LaunchedEffect(projects, settings.activeProviderId, providers) {
        if (projectId == null) projectId = projects.firstOrNull()?.id
        if (providerId == null) {
            val id = if (settings.planningModelsEnabled) settings.executionProviderId ?: settings.activeProviderId else settings.activeProviderId
            providers.firstOrNull { it.id == id }?.let { p ->
                providerId = p.id
                model = (if (settings.planningModelsEnabled) settings.executionModel else settings.activeModel) ?: p.model
            }
        }
    }
    val valid = prompt.isNotBlank() && project != null && provider != null && !model.isNullOrBlank() &&
        (sessionId == null || session != null && session.projectId == projectId) &&
        (schedule != AutomationSchedule.ONCE || at > System.currentTimeMillis())
    fun save() {
        if (saving || !valid) return
        saving = true; error = null
        scope.launch {
            try {
                val task = (initial ?: AutomationTask(title = prompt.trim().lineSequence().first().take(64),
                    prompt = prompt.trim(), projectId = requireNotNull(projectId), projectName = requireNotNull(project).name)).copy(
                    prompt = prompt.trim(), projectId = requireNotNull(projectId), projectName = requireNotNull(project).name,
                    targetSessionId = sessionId, providerId = providerId, model = model, schedule = schedule,
                    scheduledAt = if (schedule == AutomationSchedule.ONCE) at else null, hour = date.hour, minute = date.minute,
                    unmeteredOnly = unmetered, enabled = initial?.enabled ?: true)
                withContext(Dispatchers.IO) { container.automation.save(task) }
                preferences.edit().putBoolean("unmetered", unmetered).apply()
                onSaved(task)
                onDismiss()
            } catch (e: Exception) { error = e.message ?: "无法保存定时安排" }
            finally { saving = false }
        }
    }
    if (page == SchedulePage.MODEL) {
        ModelPickerSheet(providers, providerId, model, catalogs,
            onDismiss = { page = SchedulePage.DETAILS },
            onSelect = { id, selected -> providerId = id; model = selected ?: providers.firstOrNull { it.id == id }?.model; page = SchedulePage.DETAILS },
            onRefreshCatalog = { id ->
                val selected = providers.firstOrNull { it.id == id }
                if (selected == null) "服务商不可用" else {
                    val key = if (id == com.androidharness.app.llm.HarnessProvider.ID) container.providers.harnessApiKey()
                        else selected.effectiveApiKey(container.providers.apiKey(id))
                    when (val result = com.androidharness.app.llm.ModelCatalog.listModels(selected, key)) {
                        is com.androidharness.app.llm.ModelCatalog.Result.Models -> { container.providers.saveCatalog(id, result.models); null }
                        is com.androidharness.app.llm.ModelCatalog.Result.Failed -> result.message
                    }
                }
            },
            onEnableProvider = { id -> scope.launch { container.providers.setEnabled(id, true) } },
            onManageProviders = { page = SchedulePage.PROVIDER })
    } else {
        ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != SchedulePage.DETAILS) IconButton(onClick = { page = SchedulePage.DETAILS }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回定时安排")
                    }
                    Text(when (page) {
                        SchedulePage.DETAILS -> if (existing) "编辑定时安排" else "定时发送消息"
                        SchedulePage.CHAT -> "选择聊天"
                        SchedulePage.WORKSPACE -> "选择工作区"
                        SchedulePage.PROVIDER -> "选择服务商"
                        else -> "选择模型"
                    }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if (existing && page == SchedulePage.DETAILS) Box {
                        IconButton(onClick = { showActions = true }) { Icon(Icons.Outlined.MoreVert, "定时安排操作") }
                        DropdownMenu(showActions, { showActions = false }) {
                            DropdownMenuItem(text = { Text(if (initial?.enabled == true) "暂停定时安排" else "恢复定时安排") },
                                onClick = {
                                    showActions = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (initial?.enabled == true) container.automation.pause(requireNotNull(initial).id)
                                            else container.automation.resumeSchedule(requireNotNull(initial).id)
                                        }
                                        onDismiss()
                                    }
                                })
                            DropdownMenuItem(text = { Text("取消定时安排") }, onClick = { showActions = false; confirmCancel = true })
                        }
                    }
                    IconButton(onClick = { if (!saving) onDismiss() }, enabled = !saving) { Icon(Icons.Outlined.Close, "关闭定时安排") }
                }
                LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page == SchedulePage.DETAILS) {
                        item {
                            OutlinedTextField(prompt, { prompt = it }, label = { Text("消息") },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                                enabled = !saving, shape = MaterialTheme.shapes.medium)
                        }
                        item {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                                Column {
                                    if (schedule == AutomationSchedule.ONCE) ScheduleRow(Icons.Outlined.CalendarToday, "日期", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))) {
                                        focus.clearFocus(); keyboard?.hide(); showDatePicker = true
                                    }
                                    if (schedule == AutomationSchedule.ONCE || schedule == AutomationSchedule.DAILY)
                                        ScheduleRow(Icons.Outlined.Schedule, "时间", DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))) {
                                            focus.clearFocus(); keyboard?.hide(); showTimePicker = true
                                        }
                                    var expanded by remember { mutableStateOf(false) }
                                    Box {
                                        ScheduleRow(Icons.Outlined.Repeat, "重复", schedule.scheduleLabel()) { expanded = true }
                                        DropdownMenu(expanded, { expanded = false }) {
                                            AutomationSchedule.entries.forEach { option ->
                                                DropdownMenuItem(text = { Text(option.scheduleLabel()) }, onClick = { schedule = option; expanded = false })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Text("连接", style = MaterialTheme.typography.labelLarge)
                            ConnectionChoice("任何网络", "移动数据或 Wi-Fi", !unmetered) { unmetered = false }
                            ConnectionChoice("仅不限流量网络", "等待不限流量的网络连接", unmetered) { unmetered = true }
                        }
                        item {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                                Column {
                                    ScheduleRow(Icons.AutoMirrored.Outlined.Chat, "聊天", if (sessionId == null) "新聊天" else session?.title ?: "聊天不可用") { page = SchedulePage.CHAT }
                                    ScheduleRow(Icons.Outlined.Folder, "工作区", project?.name ?: "选择工作区", enabled = sessionId == null) { page = SchedulePage.WORKSPACE }
                                    ScheduleRow(Icons.Outlined.SmartToy, "模型", model ?: "选择模型") { page = SchedulePage.MODEL }
                                }
                            }
                            if (sessionId != null) Text("工作区随所选聊天变化。", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        item {
                            Text(if (schedule == AutomationSchedule.MANUAL) "已保存，可从“自动化”中运行。"
                                else "条件允许时，在此时间或之后运行。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (schedule == AutomationSchedule.ONCE && at <= System.currentTimeMillis()) item {
                            Text("请选择未来的日期和时间。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        error?.let { item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) } }
                    } else {
                        item {
                            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                        }
                        if (page == SchedulePage.CHAT) {
                            item { ScheduleRow(Icons.Outlined.Add, "新聊天", "开始新聊天") { sessionId = null; page = SchedulePage.DETAILS } }
                            items(sessions.filter { it.title.contains(query, ignoreCase = true) }, key = { it.id }) { chat ->
                                ScheduleRow(Icons.AutoMirrored.Outlined.Chat, chat.title, projects.firstOrNull { it.id == chat.projectId }?.name ?: "工作区不可用",
                                    enabled = projects.any { it.id == chat.projectId }) {
                                    sessionId = chat.id; projectId = chat.projectId; page = SchedulePage.DETAILS
                                }
                            }
                        } else if (page == SchedulePage.WORKSPACE) {
                            items(projects.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { workspace ->
                                ScheduleRow(Icons.Outlined.Folder, workspace.name, "") { projectId = workspace.id; page = SchedulePage.DETAILS }
                            }
                        } else {
                            items(providers.filter { (it.enabled || query.isNotBlank()) && it.name.contains(query, ignoreCase = true) }, key = { it.id }) { p ->
                                ScheduleRow(Icons.Outlined.SmartToy, p.name, if (p.enabled) p.model else "已停用 · 启用并使用") {
                                    if (!p.enabled) scope.launch { container.providers.setEnabled(p.id, true) }
                                    providerId = p.id; model = p.model; page = SchedulePage.MODEL }
                            }
                        }
                    }
                }
                if (page == SchedulePage.DETAILS) {
                    Button(onClick = ::save, enabled = valid && !saving,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (existing) "保存更改" else if (schedule == AutomationSchedule.MANUAL) "保存任务" else "定时安排")
                    }
                } else Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (showDatePicker) {
        val today = java.time.LocalDate.now(zone).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = date.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            })
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { selected ->
                    at = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        .atTime(date.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                }
                showDatePicker = false
            }, enabled = picker.selectedDateMillis != null) { Text("完成") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) {
            DatePicker(picker, showModeToggle = true,
                modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .65f).dp).verticalScroll(rememberScrollState()))
        }
    }
    if (showTimePicker) {
        val picker = rememberTimePickerState(date.hour, date.minute, android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(onDismissRequest = { showTimePicker = false }, title = { Text("选择时间") },
            text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = {
                at = date.withHour(picker.hour).withMinute(picker.minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
                showTimePicker = false
            }) { Text("完成") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } })
    }
    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false }, title = { Text("取消此定时安排？") },
        text = { Text("今后的运行将停止。聊天记录和已完成的工作会保留。") },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { container.automation.cancel(requireNotNull(initial).id) }
                onCancel?.invoke(); onDismiss()
            }
        }) { Text("取消定时安排") } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("保留定时安排") } })
}

@Composable
private fun ScheduleRow(icon: ImageVector, label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (enabled) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ConnectionChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
}

internal fun AutomationSchedule.scheduleLabel(): String = when (this) {
    AutomationSchedule.ONCE -> "一次"
    AutomationSchedule.DAILY -> "每天"
    AutomationSchedule.HOURLY -> "每小时"
    AutomationSchedule.MANUAL -> "手动"
}
