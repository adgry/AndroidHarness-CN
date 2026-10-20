package com.androidharness.app.ui.projects

import com.androidharness.app.AppContainer
import com.androidharness.app.github.gitCommand
import com.androidharness.app.github.GitCommandResult
import com.androidharness.app.github.parseGitChanges
import com.androidharness.app.workspace.WorkspaceFs
import kotlinx.coroutines.CancellationException

internal data class ProjectGitOverview(val branch: String? = null, val changedFiles: Int? = null,
    val note: String? = null)

/** Repository status does not require a GitHub account, origin, or a publishable branch. */
internal suspend fun loadProjectGitOverview(container: AppContainer, fs: WorkspaceFs): ProjectGitOverview {
    val remote = fs is com.androidharness.app.workspace.SshFs
    if (!remote && fs.shellRoot == null) return ProjectGitOverview(note = "Git 需要可访问 Shell 的工作区")
    if (!remote && !container.linuxEnv.gitToolsReady) return ProjectGitOverview(note = "请在“终端与设备”中安装 Git 工具")
    return readProjectGitOverview { args ->
        val result = container.shellRouter.runWorkspace(gitCommand(args), fs, timeoutMs = 10_000, maxOutput = 256_000)
        check(!result.timedOut && result.rawOutput.length < 256_000) { "仓库检查未完成" }
        GitCommandResult(result.exitCode, result.rawOutput, result.rawStderr)
    }
}

internal suspend fun readProjectGitOverview(execute: suspend (List<String>) -> GitCommandResult): ProjectGitOverview =
    try {
        suspend fun run(vararg args: String) = execute(args.toList())
        val repository = run("rev-parse", "--is-inside-work-tree")
        if (repository.code != 0 || repository.out.trim() != "true") {
            ProjectGitOverview(note = if (repository.code == 0 || "not a git repository" in repository.err.lowercase())
                "没有 Git 仓库" else "仓库状态不可用 · 重试")
        } else {
            val branch = run("symbolic-ref", "--quiet", "--short", "HEAD")
            val label = if (branch.code == 0) branch.out.trim() else if (branch.code == 1) "分离的 HEAD" else null
            val changes = run("status", "--porcelain=v1", "-z", "--untracked-files=all", "--", ".")
            if (changes.code != 0) {
                ProjectGitOverview(branch = label, note = "更改不可用 · 重试")
            } else ProjectGitOverview(branch = label, changedFiles = parseGitChanges(changes.out).size,
                note = if (label == null) "分支不可用 · 重试" else null)
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { ProjectGitOverview(note = "仓库状态不可用 · 重试") }
