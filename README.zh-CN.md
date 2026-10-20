# AndroidHarness 中文版

基于 [Sanuu7/AndroidHarness](https://github.com/Sanuu7/AndroidHarness) 的简体中文版，保留上游 MIT 许可证及作者署名。原生 Android 应用，支持对话、项目文件、终端、模型服务、工具审批和自动化。

本分支沿用原有界面结构，翻译设置、引导、聊天、文件、终端、自动化和提示文案，并将应用内更新改为可配置的独立更新源。命令、API 字段、模型 ID、用户文件、模型生成内容和第三方返回的原始信息保留原样。

- 应用包名：`com.androidharness.app.cn`，可与原版共存。
- MCP OAuth 回调使用对应包名作为独立 URI scheme，避免与原版或调试版互相拦截登录。
- 调试包名：`com.androidharness.app.cn.debug`，与正式版分开安装。
- 当前版本：`2.5.0`，`versionCode = 30`。
- Android 8.0（API 26）或以上。

## 第一次使用

安装中文 APK，按中文引导授予存储权限。通知权限可选；Shizuku 用于需要系统权限的命令，可按实际需要配置。打开“设置 → 模型与提供商”配置可用的模型服务和凭证，或使用上游内置服务。联网模型的可用性受各自服务影响。

“设置 → 软件更新”已配置为 [adgry/AndroidHarness-CN](https://github.com/adgry/AndroidHarness-CN) 的公开 Releases，无需在手机上登录 GitHub。仓库尚未发布新版 APK 时，会显示未发现可安装的新版本。

更新源保存在可提交的 `update.properties` 中，可在被忽略的 `local.properties` 中覆盖；将 `UPDATE_REPOSITORY` 显式留空可停用所有更新检查。应用不会读取原作者仓库作为更新源。

## 本地构建

需要 JDK 17 或以上（本次使用 JDK 21）、Android SDK Platform 37、Build Tools，以及项目 Gradle Wrapper。完整本地模型能力还需要 NDK 和 CMake 3.22.1。默认 NDK 版本为 `27.2.12479018`，可在本地配置中用 `NDK_VERSION` 指定已安装版本。原生推理依赖由 CMake 按固定提交和 SHA-256 下载。

复制 `local.properties.example` 为 `local.properties`，填入本机 `sdk.dir`。此文件被 Git 忽略，不要上传。依赖缓存和 Android SDK 建议放在 D 盘。

如需在多个构建架构间复用原生依赖，可将 `LLAMA_SOURCE_DIR` 指向已解压的 llama.cpp 固定提交目录。请先按 `app/src/main/cpp/CMakeLists.txt` 中的 SHA-256 验证下载归档；留空则由 CMake 自动下载并校验。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME=/Volumes/D盘/软件数据/Gradle
export ANDROID_HOME=/Volumes/D盘/软件数据/Android/sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/`。正式 APK 使用下一节的独立签名配置，执行 `./gradlew :app:assembleRelease`。

## 正式版签名与后续覆盖更新

**首次交付的正式 APK 对应密钥保存在本地 `signing-keys/chinese-release.jks`，密码和别名保存在被忽略的 `local.properties`。请立即将这两个文件备份到你自己的安全位置。** 它们不在源代码归档中，也不应上传 GitHub。后续 APK 必须使用这把密钥，才能保留现有数据并覆盖安装；换签名需要卸载重装。

其他开发者构建自己的版本时，应创建自己的密钥，不要使用调试密钥公开发布：

```sh
mkdir -p signing-keys
keytool -genkeypair -keystore signing-keys/chinese-release.jks \
  -storetype JKS -alias androidharness-cn -keyalg RSA -keysize 3072 -validity 10000
```

按提示设置密码，然后在 `local.properties` 中填写 `RELEASE_STORE_FILE`、`RELEASE_KEY_ALIAS`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_PASSWORD`。未配置正式签名时，release 构建输出未签名 APK，不能直接安装发布；debug 构建仍可用于开发。

## 发布后续更新

公开 Release 的查询和 APK 下载不需要在手机上登录 GitHub，也不需要 OAuth Client ID、私人 token 或后台服务器。

1. 将此源码上传到已创建的公开仓库 `adgry/AndroidHarness-CN`，保留 `LICENSE` 及上游来源说明。
2. `update.properties` 已设置 `UPDATE_REPOSITORY=adgry/AndroidHarness-CN`，稳定版通道 `UPDATE_INCLUDE_PRERELEASES=false`。如需迁移仓库，可改这个公开配置，或用本地属性覆盖。
3. 首次交付的中文版 APK 已包含此更新源。后续版本继续使用同一把正式签名密钥构建，即可直接在应用内更新。
4. 每次发布都增加 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`。例如下一版 `versionCode = 31`、`versionName = "2.5.1"`。
5. 创建公开的 GitHub Release，标签写为 `v2.5.1`，填写更新说明，并上传同一签名的 APK。应发布正式版本；预发布版本只有在开启 `UPDATE_INCLUDE_PRERELEASES=true` 时才会被检查到。

每个 Release 建议仅上传一个通用 APK，命名如 `AndroidHarness-CN-2.5.1-universal.apk`。分架构发布时，文件名应包含准确的 `arm64-v8a`、`armeabi-v7a`、`x86_64` 或 `x86`，例如 `AndroidHarness-CN-2.5.1-arm64-v8a.apk`。避免混放 debug、benchmark 或未签名安装包。没有匹配设备架构的 APK 时不会提示该版本。

## 应用内更新如何工作

- 启动后自动检查，最多每 24 小时一次；设置页可手动检查。
- 默认过滤草稿和预发布，按版本标签选取最新兼容 APK，不依赖发布标题中的数字或列表顺序。
- 展示更新说明，确认后下载并显示进度；下载必须来自配置仓库的 HTTPS Release 资产。
- 固定临时文件名、防止不完整文件安装、校验长度；GitHub 提供 SHA-256 时必须通过校验。
- 安装前检查包名、递增的 versionCode 和相同签名。拒绝其他应用、降级或不同密钥的安装包。
- 已授权且可用时沿用 Shizuku 安装路径；否则使用 Android 系统安装器。授予“安装未知应用”权限返回后继续使用已下载的包，不重复下载。系统最终安装授权仍由 Android 决定。
- 网络、限流、下载或校验失败会显示中文提示，可重试。应用不会把“已打开安装器”显示成“安装成功”。

## 验证

执行完整 JVM 单元测试、debug/release APK 构建及 lint：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

更新策略测试位于 `app/src/test/java/com/androidharness/app/data/update/`；中文设置搜索测试位于 `app/src/test/java/com/androidharness/app/ui/settings/SettingsNavigationTest.kt`。实际执行结果见 [本次验证记录](docs/verification-zh-CN.md)。

仓库已配置，但尚未发布新版 APK 资产，因此交付时不能声称已验证从此仓库下载到新版本覆盖安装的完整远程流程；首次发布后应使用同签名、递增版本号的 APK 做一次真机验证。Shizuku 安装也需要具备实际授权的设备验证。
