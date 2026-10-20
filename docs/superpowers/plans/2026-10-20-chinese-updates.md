# AndroidHarness Chinese and Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved Chinese Android fork with configurable, safe in-app updates.
**Architecture:** Keep Compose layouts and upstream agent contracts. Translate user-facing text in the existing source pattern; add focused pure update policy objects and integrate into the existing manager and UI.
**Tech Stack:** Kotlin, Jetpack Compose, OkHttp, kotlinx.serialization, JUnit4, Gradle.
**Spec:** docs/superpowers/specs/2026-10-20-chinese-updates-design.md

## Global Constraints
- UPDATE_REPOSITORY is adgry/AndroidHarness-CN per the user's later authorization; an explicit empty override disables checks. No GitHub token or login is required.
- Use Simplified Chinese for UI, leave wire protocols, model IDs and user content untouched.
- Preserve upstream MIT license and source attribution.
- All dependencies, build outputs and signing files stay on D volume; never publish private keys.
- Stable update channel by default; verify APK identity, newer versionCode and signing certificate before installation.

### Task 1: Chinese settings and onboarding
**Files:** app/src/main/java/com/androidharness/app/ui/settings/*.kt except UpdatesCard body; app/src/main/java/com/androidharness/app/ui/setup/SetupScreen.kt; corresponding settings tests.
**Interfaces:** Existing Compose signatures and SettingsPage enums remain unchanged. Search entries and anchors must still open the correct page/control in Chinese.
- [x] Translate settings navigation, descriptions, search, setup, provider forms and settings dialogs; update comparisons tied to labels.
- [x] Validate settings search with Chinese queries and preserve English technical keyword queries; keep existing deep links.
- [x] Compile and review diff for accidental translation of technical values.

### Task 2: Chinese workspace and chat UI
**Files:** app/src/main/java/com/androidharness/app/ui/ excluding settings, setup, update; visible notifications as assigned separately; resources app_name.
**Interfaces:** Existing Compose signatures unchanged; runtime data and agent tool protocol unchanged.
- [x] Translate chat/navigation, editor/files, terminal, projects, automation and common user-facing UI, including dynamic status text and accessibility labels.
- [x] Keep code generation templates, syntax tokens, commands, route keys, regexes and model response text intact.
- [x] Review diff and compile against original signatures.

### Task 3: Update policy and integration
**Files:** data/update/UpdateRepository.kt, UpdateVersion.kt, ReleasePolicy.kt, ApkIdentityPolicy.kt, VerifiedDownload.kt, UpdateReleaseCodec.kt, UpdateManager.kt, UpdateIntents.kt; ui/update/UpdateDialog.kt; ui/settings/UpdatesSection.kt; MainActivity.kt; app/build.gradle.kts; local.properties.example.
**Interfaces:** UpdateManager.step remains StateFlow<Step>; add NotConfigured state and an UpdatesSection UI. UpdateRepository.parse(String) returns null for empty input and rejects malformed owner/repo; ReleasePolicy selects newest eligible release; ApkIdentityPolicy checks package/version/signers.
- [x] Write JUnit tests with literal release JSON for empty config, draft/prerelease filtering, tag/title difference, ABI matching, corrupt downloads, traversal filenames, wrong package, old version and wrong signature. Run and observe missing-behavior failure before implementing.
- [x] Implement config from local.properties, pure selection/verification policies, and delegate manager decisions to them.
- [x] Bound/verify downloads, preserve coroutine cancellation, serialize operations, throttle startup checks and resume unknown-source install without downloading again.
- [x] Render all update states in Chinese and replace upstream changelog base with configured repository release URL.
- [x] Run update policy tests and existing UpdateManagerTest.

### Task 4: Delivery and verification
**Files:** README.zh-CN.md, local.properties.example, update.properties, docs/verification-zh-CN.md, build artifacts (ignored).
- [x] Document Chinese setup, no-source behavior, release naming/ABI, increasing versionCode, fixed signing identity and later GitHub release setup.
- [x] Run JVM tests, assembleDebug/assembleRelease and lint; record actual failures and repair within scope.
- [x] Use existing Android SDK/emulator if available to inspect Chinese setup/settings/update UI and retrieve screenshots.
- [x] Request independent code review, fix material findings, rerun affected checks and deliver source/APK paths with verification limits.
