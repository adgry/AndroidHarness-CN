package com.androidharness.app.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.androidharness.app.BuildConfig
import com.androidharness.app.data.env.ShizukuManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** One process-wide updater. A blank source never makes a network request. */
class UpdateManager(private val context: Context, private val shizuku: ShizukuManager) {
    sealed interface Step {
        data object Idle : Step
        data object NotConfigured : Step
        data object Checking : Step
        data class UpToDate(val current: String) : Step
        data class Available(val release: UpdateRelease) : Step
        data class Downloading(val release: UpdateRelease, val percent: Int, val mb: Float, val totalMb: Float) : Step
        data class Installing(val release: UpdateRelease, val viaShizuku: Boolean) : Step
        data class AwaitingPermission(val release: UpdateRelease) : Step
        data class Done(val viaShizuku: Boolean) : Step
        data class Error(val message: String, val release: UpdateRelease? = null) : Step
    }

    val sourceLabel: String get() = BuildConfig.UPDATE_REPOSITORY
    private val source get() = UpdateRepository.parse(sourceLabel)
    private val operation = Mutex()
    private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val checkSchedule = UpdateCheckSchedule(
        { preferences.getLong("last_check", 0) },
        { preferences.edit().putLong("last_check", it).apply() },
    )
    private var staged: Pair<UpdateRelease, File>? = restoreStaged()
    private val _step = MutableStateFlow<Step>(staged?.let { pending ->
        if (context.packageManager.canRequestPackageInstalls()) Step.Available(pending.first)
        else Step.AwaitingPermission(pending.first)
    } ?: if (sourceLabel.isBlank()) Step.NotConfigured else Step.Idle)
    val step: StateFlow<Step> = _step.asStateFlow()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES).followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            require(url.isHttps && url.host in DOWNLOAD_HOSTS) { "更新地址不受信任，已停止连接。" }
            chain.proceed(chain.request())
        }.build()

    // A private staging directory is shared read-only with the platform installer.
    private val updateDir get() = File(context.cacheDir, "update").apply { mkdirs() }

    suspend fun check(manual: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (!operation.tryLock()) return@withContext
        try {
            if (_step.value is Step.Downloading || _step.value is Step.Installing || _step.value is Step.AwaitingPermission) return@withContext
            val repository = source
            if (repository == null) { _step.value = Step.NotConfigured; return@withContext }
            if (!manual && (_step.value is Step.Available || _step.value is Step.Error)) return@withContext
            val now = System.currentTimeMillis()
            if (!checkSchedule.tryStart(manual, now)) return@withContext
            _step.value = Step.Checking
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            val request = Request.Builder().url(repository.releasesApi)
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28").build()
            val payload = client.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> error("更新仓库不存在、尚未发布版本或不是公开仓库。")
                    403, 429 -> error("GitHub 请求受限，请稍后重试。")
                }
                check(response.isSuccessful) { "检查更新失败（HTTP ${response.code}）。" }
                val body = response.body ?: error("更新服务器返回了空内容。")
                val buffered = body.source()
                buffered.request(4 * 1024 * 1024L + 1)
                require(buffered.buffer.size <= 4 * 1024 * 1024L) { "更新信息过大，请稍后重试。" }
                buffered.readUtf8()
            }
            currentCoroutineContext().ensureActive()
            val release = ReleasePolicy.select(payload, repository, current,
                BuildConfig.UPDATE_INCLUDE_PRERELEASES, Build.SUPPORTED_ABIS.toList())
            _step.value = when {
                release != null -> Step.Available(release)
                manual -> Step.UpToDate(current)
                else -> Step.Idle
            }
        } catch (e: CancellationException) {
            _step.value = Step.Idle
            throw e
        } catch (e: Exception) {
            _step.value = if (manual) Step.Error(userError(e, "检查更新失败，请检查网络后重试。")) else Step.Idle
        } finally { operation.unlock() }
    }

    fun dismiss() {
        if (_step.value is Step.Downloading || _step.value is Step.Installing) return
        _step.value = if (sourceLabel.isBlank()) Step.NotConfigured else Step.Idle
    }

    suspend fun startUpdate(release: UpdateRelease, onOpenSystemInstaller: (File) -> Unit,
                            onOpenUnknownSourcesSettings: () -> Unit): Unit = withContext(Dispatchers.IO) {
        if (!operation.tryLock()) return@withContext
        try {
            val repository = source ?: error("尚未配置更新源。")
            require(repository.ownsAsset(release.apkUrl)) { "更新包来源与配置的仓库不一致。" }
            if (_step.value is Step.Installing) return@withContext
            val cached = staged?.takeIf { it.first == release && it.second.isFile }?.second
            val apk = cached ?: download(release).also {
                staged = release to it
                check(preferences.edit().putString("staged_release", UpdateReleaseCodec.encode(release)).commit()) {
                    "无法保存待安装更新，请检查存储空间。"
                }
            }
            verifyStagedFile(apk, release)
            validateApk(apk)
            _step.value = Step.Installing(release, viaShizuku = shizuku.isGranted())
            // Shizuku's shell UID cannot read a private FileProvider path. Its own
            // installer copy lives in external files only while the command runs.
            if (tryShizukuInstall(apk)) {
                clearStaged()
                _step.value = Step.Done(viaShizuku = true)
                apk.delete()
                return@withContext
            }
            if (!context.packageManager.canRequestPackageInstalls()) {
                _step.value = Step.AwaitingPermission(release)
                withContext(Dispatchers.Main.immediate) { onOpenUnknownSourcesSettings() }
            } else {
                _step.value = Step.Installing(release, viaShizuku = false)
                withContext(Dispatchers.Main.immediate) { onOpenSystemInstaller(apk) }
            }
        } catch (e: CancellationException) {
            _step.value = Step.Available(release)
            throw e
        } catch (e: Exception) {
            clearStaged()
            _step.value = Step.Error(userError(e, "下载或安装更新失败，请重试。"), release)
        } finally { operation.unlock() }
    }

    /** Permission activity result: keep the staged APK and verify it again. */
    suspend fun resumeAfterPermission(onOpenSystemInstaller: (File) -> Unit,
                                     onOpenUnknownSourcesSettings: () -> Unit) {
        val pending = when (val current = _step.value) {
            is Step.AwaitingPermission -> current.release
            is Step.Available -> current.release.takeIf { staged?.first == it }
            else -> null
        } ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _step.value = Step.Error(NEED_INSTALL_PERMISSION, pending)
            return
        }
        startUpdate(pending, onOpenSystemInstaller, onOpenUnknownSourcesSettings)
    }

    /** Android may return RESULT_CANCELED even after handoff; never claim success here. */
    fun systemInstallerReturned() {
        val installing = _step.value as? Step.Installing ?: return
        if (!installing.viaShizuku) _step.value = Step.Error("安装流程已结束。如尚未完成更新，可点击重试；已下载的安装包会继续使用。", installing.release)
    }

    private fun restoreStaged(): Pair<UpdateRelease, File>? {
        val payload = preferences.getString("staged_release", null) ?: return null
        val repository = runCatching { source }.getOrNull()
        val current = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
        val release = repository?.let { UpdateReleaseCodec.decode(payload, it, current) }
        val apk = File(updateDir, "update.apk")
        if (release != null && apk.isFile && apk.length() == release.apkBytes) return release to apk
        // A replacement install preserves app data: discard the completed stage.
        preferences.edit().remove("staged_release").apply()
        apk.delete()
        return null
    }

    private fun clearStaged() {
        staged?.second?.delete()
        staged = null
        preferences.edit().remove("staged_release").apply()
    }

    private suspend fun verifyStagedFile(apk: File, release: UpdateRelease) {
        require(apk.isFile && apk.length() == release.apkBytes) { "已下载的安装包不完整，请重新下载。" }
        val expected = release.digest ?: return
        require(Regex("sha256:[0-9a-fA-F]{64}").matches(expected)) { "更新包校验值格式不正确。" }
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual.equals(expected.substringAfter(':'), ignoreCase = true)) { "更新包校验失败，请重新下载。" }
    }

    private suspend fun download(release: UpdateRelease): File {
        _step.value = Step.Downloading(release, 0, 0f, release.apkBytes / 1_048_576f)
        val destination = File(updateDir, "update.apk")
        val coroutine = currentCoroutineContext()
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "下载更新包失败（HTTP ${response.code}）。" }
            val body = response.body ?: error("更新包内容为空。")
            val length = body.contentLength()
            require(length < 0 || length == release.apkBytes) { "下载大小与发布信息不一致。" }
            body.byteStream().use { input ->
                VerifiedDownload.copy(input, destination, release.apkBytes, release.digest) { read, total ->
                    coroutine.ensureActive()
                    _step.value = Step.Downloading(release, ((read * 100) / total).toInt(),
                        read / 1_048_576f, total / 1_048_576f)
                }
            }
        }
        return destination
    }

    @Suppress("DEPRECATION")
    private fun validateApk(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("下载的文件不是有效的 Android 安装包。")
        fun identity(info: PackageInfo): ApkIdentity {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            val signers = signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
            return ApkIdentity(info.packageName, if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), signers)
        }
        ApkIdentityPolicy.error(identity(installed), identity(candidate))?.let { error(it) }
    }

    private suspend fun tryShizukuInstall(apk: File): Boolean {
        if (!shizuku.isGranted()) return false
        val external = context.getExternalFilesDir(null) ?: return false
        val shared = File(external, "update").apply { mkdirs() }.resolve("verified-update.apk")
        return try {
            apk.copyTo(shared, overwrite = true)
            // Re-check the external copy immediately before shell installation.
            validateApk(shared)
            val quoted = "'" + shared.absolutePath.replace("'", "'\\''") + "'"
            val result = shizuku.runPrivileged(arrayOf("/system/bin/sh", "-c", "pm install -r $quoted"),
                env = null, dir = null, timeoutMs = 120_000, maxBytes = 4_000) ?: return false
            result.exitCode == 0 && result.output.lineSequence().any { it.trim() == "Success" }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { false }
          finally { shared.delete() }
    }

    private fun userError(error: Exception, fallback: String): String =
        error.message?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } } ?: fallback

    companion object {
        const val NEED_INSTALL_PERMISSION = "请允许此应用安装未知来源的应用，再点击重试。"
        private val DOWNLOAD_HOSTS = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com")
        internal fun isNewer(remote: String, current: String): Boolean = UpdateVersion.isNewer(remote, current)
    }
}
