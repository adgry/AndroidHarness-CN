package com.androidharness.app.data.storage

import com.androidharness.app.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

/** No SAF traversal or SSH connection is initiated by a storage scan. */
data class StorageWorkspace(val id: String, val name: String, val path: String, val bytes: Long?, val note: String)
data class StorageUsage(val title: String, val bytes: Long, val note: String)
data class StorageInventory(
    val usage: List<StorageUsage>, val workspaces: List<StorageWorkspace>,
    val packages: List<String>, val managedTools: Map<String, ManagedTool>, val protections: Map<String, String?>, val candidates: List<StorageCandidate>, val incomplete: Boolean,
) { val bytes get() = usage.sumOf { it.bytes } }

class StorageManager(private val container: AppContainer) {
    private val context get() = container.appContext
    private val env get() = container.linuxEnv
    private fun canonical(file: File) = file.canonicalFile
    // Resolve Android's app-data aliases, but never turn a managed-folder symlink into a scan root.
    private fun managed(file: File) = if (StorageFiles.contained(context.dataDir, file)) canonical(file) else file.absoluteFile

    suspend fun scan(): StorageInventory = withContext(Dispatchers.IO) {
        val counter = StorageCounter()
        val candidates = mutableListOf<StorageCandidate>()
        val projects = container.workspace.projects.first()
        val localRoots = linkedMapOf<String, File>()
        val unavailable = mutableMapOf<String, String>()
        projects.forEach { project ->
            if (project.kind == "SSH") unavailable[project.id] = "位于 SSH 主机 · 未统计"
            else {
                val root = runCatching { container.workspace.fsFor(project).shellRoot?.let(::canonical) }.getOrNull()
                if (root == null) unavailable[project.id] = "仅通过文件选择器访问 · 未统计"
                else if (!root.isDirectory || !root.canRead()) unavailable[project.id] = "文件夹不可用 · 未统计"
                else localRoots[project.id] = root
            }
        }
        val roots = localRoots.values.distinct()
        val safeCacheRoot = StorageFiles.contained(context.filesDir, env.sharedCacheRoot)
        val safeDownloads = StorageFiles.contained(context.cacheDir, File(context.cacheDir, "deb-download"))
        val cacheRoot = managed(env.sharedCacheRoot)
        val legacyCaches = listOf("home/.npm", "home/.cache/pip", "home/.gradle/caches", "home/.gradle/wrapper/dists")
            .map { File(env.prefix, it) }.filter { StorageFiles.contained(env.prefix, it) }.map(::canonical)
        val downloads = managed(File(context.cacheDir, "deb-download"))
        val modelRoot = managed(File(context.noBackupFilesDir, "local-models"))
        val allSpecial = (roots + cacheRoot + legacyCaches + downloads + modelRoot).map { it.toPath() }.toSet()
        val workspaceBytes = mutableMapOf<Path, Long>()
        val managedPaths = (listOf(cacheRoot, downloads, modelRoot) + legacyCaches).map { it.toPath() }.toSet()
        val caches = counter.bytes(cacheRoot, managedPaths - cacheRoot.toPath()) +
            legacyCaches.sumOf { counter.bytes(it, managedPaths - it.toPath()) } + counter.bytes(downloads)
        val tools = counter.bytes(managed(env.prefix), managedPaths)
        val models = counter.bytes(modelRoot, managedPaths - modelRoot.toPath())
        val recoveryRoots = listOf(context.getDatabasePath("harness.db"), context.getDatabasePath("harness.db-wal"),
            context.getDatabasePath("harness.db-shm"), File(context.filesDir, "task-controls"), File(context.filesDir, "images"))
            .map(::managed)
        val recovery = recoveryRoots.sumOf { counter.bytes(it, managedPaths) }
        // Managed files take precedence even if a saved workspace contains the app data directory.
        val reserved = managedPaths + managed(env.prefix).toPath() + recoveryRoots.map { it.toPath() }
        // Nested and aliased folders are measured once. Their own row describes attribution honestly.
        roots.sortedByDescending { it.path.length }.forEach { root ->
            workspaceBytes[root.toPath()] = counter.bytes(root, (allSpecial - root.toPath()) + reserved)
        }
        val excluded = allSpecial + recoveryRoots.map { it.toPath() } + managed(env.prefix).toPath()
        val other = counter.bytes(canonical(context.dataDir), excluded) +
            (context.getExternalFilesDir(null)?.let { counter.bytes(canonical(it), excluded) } ?: 0)
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        // Serialize with package downloads, including queued installations, when making a preview.
        env.withPackageStorageLock {
            candidates += env.packageCleanupCandidates()
            (if (safeDownloads) downloads.listFiles().orEmpty() else emptyArray()).filter { it.isFile && it.name.endsWith(".deb") }.forEach { file ->
                StorageFiles.candidate("download:${file.name}", file.name, "中断的工具下载", "下载",
                    downloads, file, dayAgo)?.let(candidates::add)
            }
        }
        // These roots contain cache data only; credentials/configuration are outside them.
        (if (safeCacheRoot) listOf(File(cacheRoot, "npm/_cacache"), File(cacheRoot, "pip")) else emptyList()).forEach { file ->
            StorageFiles.candidate("cache:${file.name}", if (file.name == "pip") "pip 下载缓存" else "npm 下载缓存",
                "应用共享缓存 · 需要时会重新下载", "缓存", cacheRoot, file, dayAgo)?.let(candidates::add)
        }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        roots.forEach { root ->
            // Only recognized Gradle output trees, never arbitrary folders named build or project caches.
            val modules = listOf(root) + root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith('.') }
            modules.forEach { module ->
                if (!File(module, "build.gradle").isFile && !File(module, "build.gradle.kts").isFile) return@forEach
                val build = File(module, "build")
                if (!File(build, "outputs").isDirectory && !File(build, "intermediates").isDirectory) return@forEach
                // A workspace can point inside another project's output. That entire tree stays protected.
                if (roots.any { it != root && (it.toPath().startsWith(build.toPath()) || build.toPath().startsWith(it.toPath())) }) return@forEach
                listOf("outputs", "intermediates", "tmp").forEach { name ->
                    val output = File(build, name)
                    StorageFiles.candidate("build:${output.path}", "${module.name}/build/$name",
                        "${projects.firstOrNull { localRoots[it.id] == root }?.name ?: root.name} · 超过 7 天的构建产物",
                        "构建产物", root, output, weekAgo)?.let(candidates::add)
                }
            }
        }
        StorageInventory(
            listOf(StorageUsage("共享工具", tools, "本地工作区共用一套工具链"),
                StorageUsage("下载缓存", caches, "在同一执行层级共享"),
                StorageUsage("工作区", workspaceBytes.values.sum(), "项目文件与构建产物"),
                StorageUsage("本地模型", models, "保存在此设备上"),
                StorageUsage("聊天记录与恢复数据", recovery, "消息、图片和撤销检查点"),
                StorageUsage("应用数据与运行时暂存", other, "设置、其他缓存和必需的暂存数据")),
            projects.map { p ->
                val root = localRoots[p.id]
                StorageWorkspace(p.id, p.name, root?.path.orEmpty(), root?.let { workspaceBytes[it.toPath()] },
                    unavailable[p.id] ?: if (roots.any { it != root && it.toPath().startsWith(root!!.toPath()) })
                        "嵌套工作区的空间用量单独列出" else "不含共享工具")
            }, env.installedPackages().sorted(), env.managedTools(), env.installedPackages().associateWith(env::packageProtection),
            candidates.distinctBy { it.id }, counter.incomplete,
        )
    }

    suspend fun uninstallToolchain() = withContext(Dispatchers.IO) { withIdle { env.uninstall() } }

    private suspend fun <T> withIdle(action: suspend () -> T): T = container.runManager.withIdleStorage {
        container.backgroundProcesses.withIdleStorage { container.terminal.withClosedStorage(action) }
    }

    suspend fun clean(candidates: List<StorageCandidate>): StorageCleanupResult = withContext(Dispatchers.IO) {
        // Hold all start gates until deletion completes; a queued task/process/install cannot slip in.
        withIdle {
            env.withPackageStorageLock {
                var packageBytes = 0L; var packageFiles = 0; var skipped = 0
                for (candidate in candidates.filter { it.packageName != null }) {
                    try { packageBytes += env.removeUnusedPackage(candidate); packageFiles += candidate.stamps.size }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { skipped++ }
                }
                val result = StorageFiles.clean(candidates.filter { it.packageName == null })
                result.copy(removedBytes = result.removedBytes + packageBytes,
                    removedFiles = result.removedFiles + packageFiles, skipped = result.skipped + skipped)
            }
        }
    }
}
