package com.androidharness.app.ui.settings

internal enum class SettingsPage(
    val title: String,
    val description: String,
    val group: String,
    val keywords: String,
) {
    GITHUB("GitHub", "登录、导入仓库并发布更改", "GitHub 集成", "oauth login git pat token scopes import commit push preset automation github 登录 导入 仓库 提交 推送 令牌 自动化"),
    CONNECTED_ACCOUNTS("已连接账号", "使用 ChatGPT 套餐编写代码和调用工具", "专用提供商", "chatgpt codex subscription sign in login account plan oauth connected accounts 账号 账户 登录 套餐 订阅 自动切换"),
    MODELS("模型与提供商", "连接、模型与备用路由", "你的助手", "api key token provider openai anthropic gemini thinking planning execution fallback retries quota privacy routing models & providers 模型 提供商 连接 密钥 思考 规划 执行 备用 回退 配额 重试 路由"),
    SUBAGENT("子代理设置", "选择子代理模型和操作工具", "你的助手", "subagent sub-agent sub agent model provider action tools permissions edit commands sub-agent settings 子代理 模型 提供商 工具 权限 编辑 命令"),
    LOCAL_MODELS("本地模型", "选择并管理在手机上运行的模型", "你的助手", "offline hugging face gguf safetensors llama ram memory storage cpu download uninstall context input output vision images threads local models 离线 本地 模型 下载 内存 存储 卸载 视觉 图像 线程"),
    AGENT("代理行为", "权限、上下文限制和项目指令", "你的助手", "approval full access iterations tools agents.md memory agent behavior 权限 审批 完全访问 上下文 工具 轮数 指令 记忆"),
    CHAT("聊天与命令", "启动行为、代码索引与斜杠命令", "你的助手", "resume last chat launch repo map shortcuts chat & commands 启动 恢复 聊天 代码 索引 斜杠 命令 快捷方式"),
    CODE_INTELLIGENCE("CodeGraph", "安装 CodeGraph 并管理项目索引", "集成", "codegraph graph symbols callers callees impact affected index semantic code intelligence codegraph 代码 图谱 符号 调用 影响 测试 语义 索引"),
    CAVEMAN("Caveman", "精简回复、可选编程技能和词元报告", "集成", "caveman install intensity off lite full ultra wenyan compression tokens caveman 精简 回复 文言文 压缩 词元 技能"),
    VOICE("语音输入", "语音识别与提供商偏好设置", "你的助手", "microphone groq whisper transcription speech voice input 麦克风 语音 转录 识别"),
    SKILLS("技能", "管理助手可用的操作指南", "你的助手", "catalog instructions add skills skills 技能 目录 指令 添加"),
    APPEARANCE("外观", "工作区布局、主题和配色", "个性化", "dark light amoled system wallpaper dynamic color landscape desktop tablet layout split panes progress flat squiggly wavy animation appearance 深色 浅色 主题 壁纸 动态 配色 横屏 桌面 平板 布局 分屏 进度 波浪 动画"),
    PRIVACY("隐私与安全", "应用锁、生物识别与截屏权限", "个性化", "fingerprint pin authentication screenshots privacy & security 指纹 面容 密码 认证 应用锁 截屏 截图 安全"),
    SEARCH("网页搜索", "搜索提供商与 API 凭据", "连接", "brave tavily keyless internet web search 网页 搜索 免密钥 互联网"),
    MCP("已连接工具", "添加并配置 MCP 服务器", "连接", "mcp integrations servers transport connected tools 工具 集成 服务器 传输 协议"),
    TELEGRAM("Telegram", "通过已配对的机器人继续聊天和编辑项目", "连接", "telegram bot remote control pairing notifications gateway telegram 机器人 远程 控制 配对 通知"),
    WORKSPACE("工作区", "选择、添加并管理项目文件夹", "工作区与设备", "storage files saf folder project remembered permissions grants revoke always workspaces 工作区 文件夹 项目 文件 权限 撤销"),
    STORAGE("存储", "共享工具、文件用量与安全清理", "工作区与设备", "storage cleanup disk space cache packages downloads build outputs storage 磁盘 空间 缓存 软件包 构建产物 清理 磁盘 空间 缓存 软件包 下载 构建产物"),
    ENVIRONMENT("终端与设备", "Linux 工具、存储权限与后台运行", "工作区与设备", "shizuku shell packages environment battery optimization keep alive terminal & device 终端 设备 环境 电池 优化 后台 保活 软件包"),
    TROUBLESHOOTING("故障排查", "检查连接、权限与设备工具", "工作区与设备", "diagnostics help doctor repair errors storage linux shizuku ssh mcp notifications provider report troubleshooting 故障 诊断 帮助 修复 错误 报告 连接"),
    BACKUP("备份", "加密设置备份与聊天备份", "应用与数据", "restore history archive transfer password providers keys encryption backups 备份 恢复 历史 导出 导入 密码 加密 密钥"),
    USAGE("用量", "词元用量、费用与活动趋势", "应用与数据", "stats statistics cache tokens usage 统计 用量 费用 词元 缓存 活动"),
    UPDATES("更新", "检查 GitHub Releases 中的新版本", "应用与数据", "version release download updates 更新 版本 发布 下载"),
    SETUP("设置指南", "重新查看应用设置步骤", "应用与数据", "onboarding notifications permissions setup guide 引导 设置 通知 权限"),
    ABOUT("关于", "版本、许可证、致谢与链接", "应用与数据", "about license mit copyright credits author sanuu github repository open source thanks about 关于 许可证 版权 作者 致谢 仓库 开源"),
}

internal data class SettingsSearchEntry(
    val title: String,
    val description: String,
    val page: SettingsPage,
    val keywords: String = "",
    val primary: Boolean = false,
    /**
     * Visible label of the single control this entry points at, when it describes
     * a setting rather than a whole page. Opening the entry scrolls that control
     * into view and tints it (see SettingsAnchor); the label must match what the
     * control renders, since that is also what the user searched for.
     */
    val anchor: String? = null,
)

private val primarySettingsEntries = SettingsPage.entries.map { page ->
    SettingsSearchEntry(page.title, page.description, page, page.keywords, primary = true)
}

private val subSettingsEntries = listOf(
    SettingsSearchEntry("进度条样式", "外观 · 平直或波浪样式，提供实时预览", SettingsPage.APPEARANCE,
        "progress loading indicator animation expressive flat squiggly wavy progress bar style", anchor = "进度条样式"),
    SettingsSearchEntry("备用模型", "模型与提供商 · 按顺序切换备用模型与路由隐私", SettingsPage.MODELS, "fallback failover retry retries quota ZDR privacy chain fallbacks", anchor = "备用模型"),
    SettingsSearchEntry("使用 ChatGPT 继续", "已连接账号 · 使用 ChatGPT 套餐", SettingsPage.CONNECTED_ACCOUNTS, "sign in codex subscription continue with chatgpt", anchor = "使用 ChatGPT 继续"),
    SettingsSearchEntry("管理提供商", "模型与提供商 · 添加连接、API 密钥并选择模型", SettingsPage.MODELS, "provider api key openai anthropic gemini login model manage providers", anchor = "管理提供商"),
    SettingsSearchEntry("双模型规划", "模型与提供商 · 分别配置规划模型和执行模型", SettingsPage.MODELS, "planning plan model execute execution model dual planning models", anchor = "双模型规划"),
    SettingsSearchEntry("规划模型", "模型与提供商 · 选择用于规划的模型", SettingsPage.MODELS, "planning dual plan model", anchor = "双模型规划"),
    SettingsSearchEntry("执行模型", "模型与提供商 · 选择用于执行的模型", SettingsPage.MODELS, "execution dual execute model", anchor = "双模型规划"),
    SettingsSearchEntry("默认权限模式", "代理行为 · 选择审批或完全访问行为", SettingsPage.AGENT, "permissions approval full access default permission mode", anchor = "默认权限模式"),
    SettingsSearchEntry("子代理操作工具", "子代理设置 · 允许子代理在执行模式中编辑文件和运行命令", SettingsPage.SUBAGENT, "subagent sub-agent permissions tools commands read only plan subagent action tools", anchor = "子代理操作工具"),
    SettingsSearchEntry("子代理模型", "子代理设置 · 为子代理选择独立的提供商和模型", SettingsPage.SUBAGENT, "subagent sub-agent model provider execution different separate subagent model", anchor = "子代理模型"),
    SettingsSearchEntry("最大上下文窗口", "代理行为 · 设置模型的最大上下文大小", SettingsPage.AGENT, "context tokens limit max context window", anchor = "最大上下文窗口"),
    SettingsSearchEntry("工具调用轮数限制", "代理行为 · 限制代理的工具调用轮数", SettingsPage.AGENT, "iterations tools max limit tool-call iteration limit", anchor = "工具调用轮数限制"),
    SettingsSearchEntry("项目指令（AGENTS.md）", "代理行为 · 编辑每次运行时注入的工作区指令", SettingsPage.AGENT, "agents instructions memory workspace prompt project instructions (agents.md)", anchor = "项目指令（AGENTS.md）"),
    SettingsSearchEntry("启动时恢复上次聊天", "聊天与命令 · 启动应用时打开最近的聊天", SettingsPage.CHAT, "startup launch chat resume resume last chat on launch", anchor = "启动时恢复上次聊天"),
    SettingsSearchEntry("显示实时代码", "聊天与命令 · 预览代码生成和行数变化", SettingsPage.CHAT, "writing streaming diff added removed tokens show live code", anchor = "显示实时代码"),
    SettingsSearchEntry("工作区代码索引（Repo map）", "聊天与命令 · 为代理上下文索引项目符号", SettingsPage.CHAT, "repo map code index symbols workspace code index (repo map)", anchor = "工作区代码索引（Repo map）"),
    SettingsSearchEntry("CodeGraph", "代码智能 · 安装 CodeGraph 并为工作区启用语义索引", SettingsPage.CODE_INTELLIGENCE, "codegraph explore node impact affected sync semantic graph codegraph"),
    SettingsSearchEntry("CodeGraph 工作区索引", "代码智能 · 启用、同步或重建当前项目索引", SettingsPage.CODE_INTELLIGENCE, "project workspace initialize reindex graph codegraph workspace index"),
    SettingsSearchEntry("斜杠命令", "聊天与命令 · 管理自定义斜杠命令", SettingsPage.CHAT, "shortcut snippets custom command slash commands", anchor = "斜杠命令"),
    SettingsSearchEntry("语音转文字引擎", "语音输入 · 选择系统识别或 Groq Whisper", SettingsPage.VOICE, "voice microphone groq whisper speech transcription speech-to-text engine", anchor = "语音转文字引擎"),
    SettingsSearchEntry("Whisper 模型", "语音输入 · 选择 Groq Whisper 转录模型", SettingsPage.VOICE, "groq voice transcription whisper model", anchor = "Whisper 模型"),
    SettingsSearchEntry("主题", "外观 · 选择跟随系统、浅色、深色或 AMOLED", SettingsPage.APPEARANCE, "dark light amoled color theme", anchor = "主题"),
    SettingsSearchEntry("工作区布局", "外观 · 自动、手机或桌面布局", SettingsPage.APPEARANCE, "landscape tablet split panes wide desktop workspace layout", anchor = "工作区布局"),
    SettingsSearchEntry("动态配色", "外观 · 应用配色跟随壁纸", SettingsPage.APPEARANCE, "material you wallpaper colors dynamic color", anchor = "动态配色"),
    SettingsSearchEntry("生物识别应用锁", "隐私与安全 · 要求指纹、面容或设备 PIN", SettingsPage.PRIVACY, "fingerprint face pin authentication lock biometric app lock"),
    SettingsSearchEntry("自动锁定时间", "隐私与安全 · 选择应用重新锁定的时间", SettingsPage.PRIVACY, "biometric timeout security auto-lock timeout"),
    SettingsSearchEntry("允许截屏", "隐私与安全 · 控制截屏权限", SettingsPage.PRIVACY, "screen capture screenshot privacy allow screenshots"),
    SettingsSearchEntry("GitHub 登录", "GitHub · 连接账号以访问仓库", SettingsPage.GITHUB, "oauth token pat repositories login github sign in"),
    SettingsSearchEntry("网页搜索后端", "网页搜索 · 选择免密钥、Brave 或 Tavily", SettingsPage.SEARCH, "internet brave tavily api key keyless web search backend"),
    SettingsSearchEntry("MCP 服务器", "已连接工具 · 添加并配置 MCP 集成", SettingsPage.MCP, "tools integrations server transport mcp servers"),
    SettingsSearchEntry("添加工作区", "工作区 · 添加其他项目文件夹", SettingsPage.WORKSPACE, "folder storage project saf add workspace"),
    SettingsSearchEntry("共享存储", "终端与设备 · 授予设备文件夹的读写权限", SettingsPage.ENVIRONMENT, "files storage permission all files shared storage", anchor = "共享存储"),
    SettingsSearchEntry("Shizuku（ADB 权限）", "终端与设备 · 配置提权 shell 访问", SettingsPage.ENVIRONMENT, "adb shell system paths permission shizuku (adb privileges)", anchor = "系统路径与任意文件夹"),
    SettingsSearchEntry("Linux 环境", "终端与设备 · 配置 Linux 工具链", SettingsPage.ENVIRONMENT, "terminal packages shell environment linux environment", anchor = "应用工作区 shell"),
    SettingsSearchEntry("电池优化", "终端与设备 · 保持代理在后台运行", SettingsPage.ENVIRONMENT, "background keep alive battery battery optimization"),
    SettingsSearchEntry("导出聊天", "聊天备份 · 将对话保存到备份文件", SettingsPage.BACKUP, "backup export history archive export chats"),
    SettingsSearchEntry("导入聊天", "聊天备份 · 从备份文件恢复对话", SettingsPage.BACKUP, "backup import restore history import chats"),
    SettingsSearchEntry("检查更新", "更新 · 检查 GitHub Releases 中的新版本", SettingsPage.UPDATES, "version update release download check for updates"),
    SettingsSearchEntry("许可证", "关于 · MIT 许可证及其对分支项目的意义", SettingsPage.ABOUT, "mit copyright permissive open source license"),
    SettingsSearchEntry("致谢", "关于 · AndroidHarness 使用的开源项目", SettingsPage.ABOUT, "thanks acknowledgements inspirations projects credits"),
    SettingsSearchEntry("作者", "关于 · AndroidHarness 的开发者和项目地址", SettingsPage.ABOUT, "sanuu github repository maintainer source code author"),
)

internal fun matchingSettingsEntries(query: String): List<SettingsSearchEntry> {
    val terms = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (terms.isEmpty()) return primarySettingsEntries
    return (primarySettingsEntries + subSettingsEntries).filter { entry ->
        val text = "${entry.title} ${entry.description} ${entry.page.title} ${entry.page.group} ${entry.keywords}".lowercase()
        terms.all { it in text }
    }.sortedWith(compareBy<SettingsSearchEntry> { it.primary }.thenBy { it.title })
}

internal fun matchingSettingsPages(query: String): List<SettingsPage> =
    matchingSettingsEntries(query).map { it.page }.distinct()

internal fun settingsDeepLink(target: String): SettingsPage? = when (target) {
    "troubleshooting" -> SettingsPage.TROUBLESHOOTING
    "github" -> SettingsPage.GITHUB
    "chatgpt" -> SettingsPage.CONNECTED_ACCOUNTS
    "planning" -> SettingsPage.MODELS
    "voice" -> SettingsPage.VOICE
    "telegram" -> SettingsPage.TELEGRAM
    "agent" -> SettingsPage.AGENT
    "storage" -> SettingsPage.STORAGE
    else -> null
}

/** The control a deep link should scroll to and tint, if it names a specific one. */
internal fun settingsDeepLinkAnchor(target: String): String? = when (target) {
    "planning" -> "双模型规划"
    "voice" -> "语音转文字引擎"
    else -> null
}

/** The control a search result should scroll to, or null when it opens a whole page. */
internal fun settingsAnchorFor(title: String): String? =
    subSettingsEntries.firstOrNull { it.title == title }?.anchor
