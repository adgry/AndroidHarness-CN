package com.androidharness.app.data.update

/** Numeric version comparison with SemVer prerelease ordering; never scans display titles. */
object UpdateVersion {
    private data class Version(val core: List<Long>, val pre: List<String>)
    private val pattern = Regex("""(?i)(?:alpha[- ]?)?v?(\d+(?:\.\d+)*)(?:-([0-9a-z.-]+))?(?:\+[0-9a-z.-]+)?""")

    private fun parse(value: String): Version? {
        val match = pattern.matchEntire(value.trim()) ?: return null
        val core = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
        val pre = match.groupValues[2].takeIf { it.isNotEmpty() }?.split('.')
            ?: if (value.trim().startsWith("alpha", ignoreCase = true)) listOf("alpha") else emptyList()
        return Version(core, pre)
    }

    fun isNewer(remote: String, current: String): Boolean = compare(remote, current)?.let { it > 0 } ?: false

    internal fun compare(left: String, right: String): Int? {
        val a = parse(left) ?: return null
        val b = parse(right) ?: return null
        for (i in 0 until maxOf(a.core.size, b.core.size)) {
            val diff = a.core.getOrElse(i) { 0L }.compareTo(b.core.getOrElse(i) { 0L })
            if (diff != 0) return diff
        }
        if (a.pre.isEmpty() && b.pre.isEmpty()) return 0
        if (a.pre.isEmpty()) return 1
        if (b.pre.isEmpty()) return -1
        for (i in 0 until minOf(a.pre.size, b.pre.size)) {
            val x = a.pre[i]; val y = b.pre[i]
            val xn = x.toLongOrNull(); val yn = y.toLongOrNull()
            val diff = when {
                xn != null && yn != null -> xn.compareTo(yn)
                xn != null -> -1
                yn != null -> 1
                else -> x.compareTo(y)
            }
            if (diff != 0) return diff
        }
        return a.pre.size.compareTo(b.pre.size)
    }
}
