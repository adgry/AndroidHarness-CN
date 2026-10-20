# AndroidHarness 中文版与应用内更新设计

用户于 2026-10-20 确认聊天中提出的方案。

## 目标与约束
基于上游原生 Android/Kotlin/Jetpack Compose 应用交付可继续维护的简体中文版。保留现有导航、Material 组件、代理能力和 MIT 署名。翻译用户可见界面、引导、操作、空状态和提示；保留 API 字段、模型 ID、文件名、协议、shell 命令及用户/模型原始内容。不新增 GitHub 登录要求。

## 更新设计
复用全局更新弹窗与设置页。构建时从 update.properties 读取公开默认值，并允许 local.properties 覆盖 UPDATE_REPOSITORY（owner/repo）及 UPDATE_INCLUDE_PRERELEASES（默认 false）。按用户后续授权，更新源为 adgry/AndroidHarness-CN；显式空值可停用检查。设置页显示实际来源、版本与明确的未配置状态；未配置时自动检查无网络请求、手动检查说明如何配置。启动自动检查限频，每 24 小时一次；手动检查可随时重试。公开 Releases 不需要 token。

按 tag 比较版本，稳定版默认排除 draft/prerelease；不以任意 release 标题中出现的数字决定更新。仅提示有 APK 的新版本，并以设备 ABI 或 universal 匹配选择；下载必须使用当前配置仓库的 GitHub release 资产地址且使用 HTTPS。

下载写入固定文件名 .part 临时文件、限制大小、检查实际长度与 GitHub 提供的 SHA-256（有则必须匹配），完整后原子移入安装缓存。安装前验证 APK 包名、versionCode 大于当前和签名匹配。下载/安装只允许一个任务；失败给出中文原因和重试入口，取消保留协程取消语义。优先沿用已授权的 Shizuku；否则调用系统安装器，未知来源权限授权返回后继续安装，取消安装回到可重试状态，不能假装安装完成。

GitHub 未配置时可交付和测试本地中文 APK，但远程端到端更新需后续创建公开仓库、上传相同签名的新 APK 并增加 versionCode。签名密钥只保存在忽略目录；提供独立本地签名配置说明，保留首版密钥以供以后覆盖升级。

## 验证与交付
先测试更新源解析、release 选择、版本规则、下载完整性、包信息校验边界，再实现。运行现有 JVM 测试和新测试、APK 构建及 lint；可用时在 Android 模拟器验证中文引导、聊天入口、设置搜索、未配置更新状态。交付 README.zh-CN.md、更新配置示例、源码和可安装 APK，明确真机/Shizuku/远程发布尚未覆盖的验证。

## 已批准的范围更新
2026-10-20 用户连接 GitHub 并明确选择新建公开仓库 adgry/AndroidHarness-CN 作为更新源。公开默认值写入 update.properties，并允许 local.properties 覆盖，空值仍可停用检查。只创建空仓库并补齐应用配置，源码和 Release 发布尚未授权。
