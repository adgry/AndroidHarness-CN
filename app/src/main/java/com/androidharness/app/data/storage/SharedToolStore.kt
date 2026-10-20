package com.androidharness.app.data.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@Serializable
data class ManagedTool(
    val name: String, val version: String, val depends: List<String>,
    internal val files: List<StorageStamp> = emptyList(), val complete: Boolean = false,
    val requirements: Set<String> = emptySet(),
    val ownedPaths: List<String> = files.map { it.path },
)

/** Private atomic metadata; a missing/corrupt record never authorizes removal. Caller holds installMutex. */
internal class SharedToolStore(private val root: File, private val prefix: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordsFile = File(root, "packages.json")
    private val journal = File(root, "removal.json")
    private val quarantine = File(root, "removing")
    private var records: Map<String, ManagedTool> = load()
    private fun load(): Map<String, ManagedTool> = runCatching {
        json.decodeFromString<List<ManagedTool>>(recordsFile.readText()).associateBy { it.name }
    }.getOrDefault(emptyMap())
    private fun write(file: File, text: String) {
        root.mkdirs()
        val temp = File(root, file.name + ".tmp")
        temp.outputStream().use { it.write(text.toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }
    @Synchronized fun all(): Map<String, ManagedTool> = records.toMap()
    @Synchronized fun put(record: ManagedTool) {
        val next = records + (record.name to record)
        write(recordsFile, json.encodeToString(next.values.toList())); records = next
    }
    @Synchronized fun require(names: List<String>, owner: String) {
        names.forEach { name -> records[name]?.let { put(it.copy(requirements = it.requirements + owner)) } }
    }
    @Synchronized fun release(name: String, owner: String) {
        records[name]?.let { put(it.copy(requirements = it.requirements - owner)) }
    }

    fun reason(name: String, installed: Set<String>, required: Set<String>): String? {
        val all = all()
        if (name in required) return "应用工具链需要此软件包"
        if (installed.any { all[it] == null }) return "旧软件包的依赖关系和文件归属未知"
        val record = all[name] ?: return "文件归属未知"
        if (!record.complete) return "无法验证文件归属"
        if (record.requirements.isNotEmpty()) return "为 ${record.requirements.joinToString()} 保留"
        val dependents = installed.filter { it != name && name in all[it]?.depends.orEmpty() }
        if (dependents.isNotEmpty()) return "${dependents.joinToString()} 需要此软件包"
        if (all.filterKeys { it in installed && it != name }.values.any { other ->
                other.ownedPaths.any { path -> record.files.any { it.path == path } }
            }) return "与其他已安装软件包共用文件"
        return null
    }

    suspend fun preview(name: String, installed: Set<String>, required: Set<String>): StorageCandidate? {
        if (reason(name, installed, required) != null) return null
        val record = all()[name] ?: return null
        if (record.files.isEmpty() || !unchanged(record)) return null
        return StorageCandidate("package:$name", name, "无已记录的需求，也无已安装的软件包依赖它", "受管理的软件包",
            prefix.parentFile!!, prefix, record.files, packageName = name)
    }

    private suspend fun unchanged(record: ManagedTool): Boolean = record.files.all { stamp ->
        val file = File(prefix, stamp.path)
        if (!StorageFiles.contained(prefix, file)) return@all false
        val actual = try { StorageFiles.snapshot(prefix, file).single().copy(path = stamp.path) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        actual == stamp
    }

    /** Quarantine first; a process death rolls back on next launch unless the installed marker committed. */
    suspend fun remove(candidate: StorageCandidate, installed: Set<String>, required: Set<String>, commit: () -> Unit): Long {
        val name = requireNotNull(candidate.packageName)
        check(reason(name, installed, required) == null) { "软件包需求已更改，请重新扫描。" }
        val record = requireNotNull(all()[name])
        check(record.files == candidate.stamps && unchanged(record)) { "软件包文件已更改，请重新扫描。" }
        check(!journal.exists()) { "之前的软件包移除操作需要恢复，请重启应用。" }
        write(journal, json.encodeToString(record))
        try {
            for (stamp in record.files) {
                val source = File(prefix, stamp.path)
                check(StorageFiles.contained(prefix, source)) { "软件包路径已更改。" }
                check(StorageFiles.snapshot(prefix, source).single().copy(path = stamp.path) == stamp) { "软件包文件已更改。" }
                val destination = File(quarantine, stamp.path)
                destination.parentFile!!.mkdirs()
                Files.move(source.toPath(), destination.toPath(), ATOMIC_MOVE)
            }
            commit()
        } catch (e: Exception) {
            recover(installed); throw e
        }
        // Once committed, failure to delete quarantine is retained for recovery on the next launch.
        recover(installed - name)
        return candidate.bytes
    }

    fun recover(installed: Set<String>) {
        if (!journal.exists()) return
        val record = json.decodeFromString<ManagedTool>(journal.readText())
        for (stamp in record.files) {
            check(stamp.path.isNotEmpty() && !File(stamp.path).isAbsolute && stamp.path.split('/', '\\').none { it == ".." || it == "." }) { "软件包恢复路径无效。" }
            val saved = File(quarantine, stamp.path)
            check(StorageFiles.contained(root, saved)) { "软件包恢复存储位置已更改。" }
            if (!saved.exists()) continue
            if (record.name in installed) {
                val destination = File(prefix, stamp.path)
                check(StorageFiles.contained(prefix, destination) && !destination.exists()) { "软件包恢复路径已被占用。" }
                destination.parentFile!!.mkdirs()
                Files.move(saved.toPath(), destination.toPath(), ATOMIC_MOVE)
            } else check(saved.delete()) { "无法完成软件包移除。" }
        }
        check(quarantine.deleteRecursively() || !quarantine.exists())
        check(journal.delete())
    }
}
