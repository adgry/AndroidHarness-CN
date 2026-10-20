package com.androidharness.app.data.update

import java.net.URI

/** Public release source. An empty value deliberately disables network checks. */
class UpdateRepository private constructor(val slug: String) {
    val releasesApi: String get() = "https://api.github.com/repos/$slug/releases?per_page=100"
    val webUrl: String get() = "https://github.com/$slug"

    fun ownsAsset(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true) &&
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.startsWith("/$slug/releases/download/", ignoreCase = true) &&
            uri.rawPath.substringAfter("/releases/download/").split('/').size == 2 &&
            uri.rawPath.split('/').none { it == ".." || it == "." }
    }.getOrDefault(false)

    companion object {
        fun parse(value: String): UpdateRepository? {
            val slug = value.trim()
            if (slug.isEmpty()) return null
            val parts = slug.split('/')
            require(parts.size == 2 && OWNER.matches(parts[0]) && REPO.matches(parts[1]) &&
                parts[1] != "." && parts[1] != "..") {
                "更新源格式不正确，请填写 GitHub 用户名/仓库名。"
            }
            return UpdateRepository(slug)
        }
        private val OWNER = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
        private val REPO = Regex("[A-Za-z0-9_.-]{1,100}")
    }
}

object UpdateCheckPolicy {
    private const val INTERVAL_MS = 24 * 60 * 60 * 1000L
    fun shouldCheck(manual: Boolean, lastCheck: Long, now: Long): Boolean =
        manual || lastCheck == 0L || now < lastCheck || now - lastCheck >= INTERVAL_MS
}

/** Record attempts before doing I/O, so failures cannot create restart-driven retries. */
class UpdateCheckSchedule(private val lastAttempt: () -> Long, private val recordAttempt: (Long) -> Unit) {
    fun tryStart(manual: Boolean, now: Long): Boolean {
        if (!UpdateCheckPolicy.shouldCheck(manual, lastAttempt(), now)) return false
        recordAttempt(now)
        return true
    }
}
