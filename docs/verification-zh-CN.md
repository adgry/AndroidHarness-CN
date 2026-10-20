# 中文版验证记录

日期：2026-10-20。基于上游提交 `931be2e96cd91e8752e837ea70ae7c729be8e339`，中文版 `2.5.0`（30）。

## 已执行的源代码验证

- 完整 Gradle JVM 单元测试：190 个测试套件，1322 项测试，0 失败、0 错误、0 跳过。命令 `:app:testDebugUnitTest --no-configuration-cache`。
- 更新策略测试覆盖：空更新源、无效仓库、跨仓库地址、版本标签与发布标题、稳定/预发布通道、ABI 匹配与通用包、排除调试包、下载长度/摘要、包名/版本/签名策略、失败后的检查频率、待安装元数据与已完成更新清理。
- 中文设置搜索、设置状态兼容和 Telegram 等待状态已包含在完整测试中；独立聚焦测试记录为 28 项通过。
- 修正四处仍期待旧英文显示文本的测试断言。两处 Git 测试显式固定测试分支名；shell 语法错误测试以宿主 shell 的返回值为准，同时保留具体正常/异常退出码断言，避免 macOS 与 Android/Linux 的工具行为差异。
- `git diff --check` 通过。
- 完成独立代码评审，已修复更新检查节流、安装状态恢复、ABI 匹配、MCP 回调隔离及中文状态的生产/消费匹配问题。

## 构建与设备验证

- 正式包 `app/build/outputs/apk/release/app-release.apk` 构建成功（2026-10-20 09:17，`BUILD SUCCESSFUL`，57 个任务）。副本：`/Volumes/D盘/手机agent/AndroidHarness-CN-2.5.0-universal.apk`（约 50 MB）。
- `apksigner` 校验通过。包名 `com.androidharness.app.cn`，`versionName 2.5.0`，`versionCode 30`，minSdk 26，targetSdk 36。签名证书 `CN=AndroidHarness Chinese`，SHA-256 `5be51cfb60f8be082d1d1a22686f6d8ac88bb92f597d525124d983aae7d2371c`。后续覆盖更新必须使用同一把密钥。
- 生成的 `BuildConfig` 中，debug 与 release 的 `UPDATE_REPOSITORY` 均为 `adgry/AndroidHarness-CN`，稳定通道。
- 正式包包含 `arm64-v8a` 与 `x86_64` 的完整 `libharness_local.so`。`armeabi-v7a` 与 `x86` 按上游 CMake 规则只放入“本地模型不可用”的小库。
- 调试包此前已安装到模拟器 `emulator-5554`（`sdk_gphone64_arm64`）。设置搜索输入 `updates` 能打开“检查更新”。更新页显示更新源 `adgry/AndroidHarness-CN`、稳定版通道；手动检查后显示“未发现适合此设备的新版本。”
- 正式包已安装到同一模拟器并启动，进程无崩溃。首次引导为中文：授予“所有文件访问权限”后显示“已完成 1 / 5 项”和“准备就绪”，进入主页后可见“你想构建什么？”“应用工作区”“输入消息…”。系统权限页跟随模拟器的英文系统语言。
- 2026-10-20 09:29 再次执行 `:app:testDebugUnitTest`：190 个套件、1322 项、0 失败、0 错误、0 跳过，`BUILD SUCCESSFUL in 37s`。直接访问 Maven Central 时 Gradle 的 TLS 握手会失败，这次通过本机已有的 Maven 缓存桥完成。
- `:app:lintDebug` 第一次报告 18 个 error。定时发送页里一处 `if` 的缩进已按语句归属补齐，行为不变。复跑后为 17 个 error、156 个 warning。这 17 个都在未改动的上游代码或本机 `local.properties` 路径上（`NewApi`、`RestrictedApi`、Compose 状态读取、属性转义）。已安装的正式包早于这次仅缩进的修改。
- `git diff --check` 通过。签名文件 `signing-keys/` 与 `local.properties` 仍被 Git 忽略。源码和 APK 尚未推送到 GitHub。

## 更新源与验证边界

- 公开仓库 `adgry/AndroidHarness-CN` 已创建，应用公开配置及本地构建均指向该仓库。已核对仓库为公开、当前为空；Releases API 返回 HTTP 200 和空列表。
- 只创建了仓库，尚未上传源码、APK 或发布 Release。
- 尚未使用该仓库的真实新版资产验证完整下载与覆盖安装。首次发布后，应以相同签名、相同包名、更大的 versionCode 和更新的版本标签完成真机升级验证。
- Shizuku 安装需要已授权的真机，本次未验证。未下载模型、付费服务或凭证，未声称验证实际 AI 推理、第三方账号登录或全部设备/OEM 行为。
- 中文版保留技术名称、协议、用户内容及第三方原始输出；这类文本可能仍为英文。
