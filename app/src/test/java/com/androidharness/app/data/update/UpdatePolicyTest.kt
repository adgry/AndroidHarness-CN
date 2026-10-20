package com.androidharness.app.data.update

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class UpdatePolicyTest {
    private val repo = UpdateRepository.parse("my-name/phone-agent")!!

    @Test fun `empty update source disables remote checks and rejects URLs and path traversal`() {
        assertNull(UpdateRepository.parse("  "))
        assertEquals("https://api.github.com/repos/my-name/phone-agent/releases?per_page=100", repo.releasesApi)
        for (invalid in listOf("https://github.com/a/b", "a/../b", "a/..", "a/b?token=secret", "a/b/c")) {
            assertThrows(IllegalArgumentException::class.java) { UpdateRepository.parse(invalid) }
        }
        assertFalse(repo.ownsAsset("https://github.com/Sanuu7/AndroidHarness/releases/download/v2/app.apk"))
        assertFalse(repo.ownsAsset("http://github.com/my-name/phone-agent/releases/download/v2/app.apk"))
        assertTrue(repo.ownsAsset("https://github.com/my-name/phone-agent/releases/download/v2/app.apk"))
    }

    @Test fun `release selection uses tags filters drafts and prereleases and ignores ordering and titles`() {
        val json = """[
          ${release("v9.0", "测试版", prerelease=true)},
          ${release("v8.0", "草稿", draft=true)},
          ${release("v2.0", "Build 999")},
          ${release("v2.1", "中文发布 2026")}
        ]"""
        val stable = ReleasePolicy.select(json, repo, "1.9", false, listOf("arm64-v8a"))!!
        assertEquals("v2.1", stable.tag)
        assertEquals("v9.0", ReleasePolicy.select(json, repo, "1.9", true, listOf("arm64-v8a"))!!.tag)
        assertNull(ReleasePolicy.select(json, repo, "3.0", false, listOf("arm64-v8a")))
    }

    @Test fun `ABI selects device APK instead of last listed asset and ignores foreign downloads`() {
        val assets = """[
          ${asset("app-arm64-v8a.apk")}, ${asset("app-x86_64.apk")},
          ${asset("foreign.apk", url="https://github.com/other/project/releases/download/v2/foreign.apk")}
        ]"""
        val selected = ReleasePolicy.select("[${release("v2.0", "2.0", assets=assets)}]", repo, "1.0", false, listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("app-arm64-v8a.apk", selected.apkName)
        assertNull(ReleasePolicy.select("[${release("v2.0", "2.0", assets="[${asset("app-x86_64.apk")}] ")}]", repo, "1.0", false, listOf("arm64-v8a")))
    }

    @Test fun `universal APK works and newer releases without compatible APK do not hide installable releases`() {
        val json = """[${release("v3.0", "3", assets="[]")}, ${release("v2.0", "2", assets="[${asset("app-universal.apk")}]")}]"""
        assertEquals("v2.0", ReleasePolicy.select(json, repo, "1.0", false, listOf("x86_64"))!!.tag)
    }

    @Test fun `version parsing compares numeric core and prerelease identifiers without trailing digit bugs`() {
        assertTrue(UpdateVersion.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateVersion.isNewer("v2.0", "2.0-rc.2"))
        assertTrue(UpdateVersion.isNewer("v2.0-rc.10", "2.0-rc.2"))
        assertFalse(UpdateVersion.isNewer("v1.9-cn.2", "2.0-cn.1"))
        assertFalse(UpdateVersion.isNewer("Release 2026 build 999", "1.0"))
        assertFalse(UpdateVersion.isNewer("nightly", "1.0"))
        assertFalse(UpdateVersion.isNewer("v1.0", "1.0.0"))
        assertTrue(UpdateVersion.isNewer("Alpha-v0.3", "0.2-alpha"))
    }

    @Test fun `APK identity refuses wrong app rollback and signer even after valid download`() {
        val current = ApkIdentity("com.example.cn", 24, setOf("cert-a"))
        assertNull(ApkIdentityPolicy.error(current, ApkIdentity("com.example.cn", 25, setOf("cert-a"))))
        assertNotNull(ApkIdentityPolicy.error(current, ApkIdentity("com.original.app", 25, setOf("cert-a"))))
        assertNotNull(ApkIdentityPolicy.error(current, ApkIdentity("com.example.cn", 24, setOf("cert-a"))))
        assertNotNull(ApkIdentityPolicy.error(current, ApkIdentity("com.example.cn", 23, setOf("cert-a"))))
        assertNotNull(ApkIdentityPolicy.error(current, ApkIdentity("com.example.cn", 25, setOf("cert-b"))))
        assertNotNull(ApkIdentityPolicy.error(current, ApkIdentity("com.example.cn", 25, emptySet())))
    }

    @Test fun `download is atomic checks hash and length and deletes partial files on failure`() {
        val dir = Files.createTempDirectory("update-policy-").toFile()
        try {
            val file = File(dir, "update.apk")
            val bytes = "abc".toByteArray()
            val sha = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            VerifiedDownload.copy(ByteArrayInputStream(bytes), file, 3, sha) { _, _ -> }
            assertEquals("abc", file.readText())
            assertThrows(IllegalArgumentException::class.java) {
                VerifiedDownload.copy(ByteArrayInputStream(bytes), file, 4, sha) { _, _ -> }
            }
            assertFalse(file.exists())
            assertFalse(File(dir, "update.apk.part").exists())
            assertThrows(IllegalArgumentException::class.java) {
                VerifiedDownload.copy(ByteArrayInputStream(bytes), file, 3, "sha256:" + "0".repeat(64)) { _, _ -> }
            }
            assertFalse(file.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun `auto checks wait for interval but manual checks bypass it`() {
        assertTrue(UpdateCheckPolicy.shouldCheck(false, 0, 1_000))
        assertFalse(UpdateCheckPolicy.shouldCheck(false, 1_000, 2_000))
        assertTrue(UpdateCheckPolicy.shouldCheck(true, 1_000, 2_000))
        assertTrue(UpdateCheckPolicy.shouldCheck(false, 1_000, 86_401_000))
        assertTrue(UpdateCheckPolicy.shouldCheck(false, 2_000, 1_000))
    }

    @Test fun `failed automatic attempts still consume interval while manual retry remains available`() {
        var lastAttempt = 0L
        val schedule = UpdateCheckSchedule({ lastAttempt }, { lastAttempt = it })
        assertTrue(schedule.tryStart(false, 1_000))
        // Simulate the following request failing: no success callback occurs.
        assertFalse(schedule.tryStart(false, 2_000))
        assertTrue(schedule.tryStart(true, 2_000))
        assertFalse(schedule.tryStart(false, 3_000))
    }

    @Test fun `unknown architecture APKs are not treated as universal`() {
        for (name in listOf("app-riscv64.apk", "app-arm64.apk", "app-mips.apk", "app-debug.apk")) {
            val json = "[${release("v2.0", "2", assets="[${asset(name)}]")}]"
            assertNull(name, ReleasePolicy.select(json, repo, "1.0", false, listOf("x86_64")))
        }
    }

    @Test fun `staged metadata roundtrips without trusting file paths or other repository sources`() {
        val release = UpdateRelease("v2.0", "中文版本", "更新说明", "https://github.com/my-name/phone-agent/releases/tag/v2.0",
            "https://github.com/my-name/phone-agent/releases/download/v2/app.apk", "app.apk", 3)
        val saved = UpdateReleaseCodec.encode(release)
        assertEquals(release, UpdateReleaseCodec.decode(saved, repo))
        assertNull(UpdateReleaseCodec.decode(saved, UpdateRepository.parse("other/project")!!))
        assertNull(UpdateReleaseCodec.decode("{broken", repo))
    }

    @Test fun `restoring stage after successful installation does not advertise installed or older release`() {
        val release = UpdateRelease("v2.0", "中文版本", "更新说明", "https://github.com/my-name/phone-agent/releases/tag/v2.0",
            "https://github.com/my-name/phone-agent/releases/download/v2/app.apk", "app.apk", 3)
        val saved = UpdateReleaseCodec.encode(release)
        assertNotNull(UpdateReleaseCodec.decode(saved, repo, "1.9"))
        assertNull(UpdateReleaseCodec.decode(saved, repo, "2.0"))
        assertNull(UpdateReleaseCodec.decode(saved, repo, "2.1"))
    }

    private fun release(tag: String, name: String, draft: Boolean=false, prerelease: Boolean=false, assets: String="[${asset("app.apk")}] ") =
        """{"tag_name":"$tag","name":"$name","draft":$draft,"prerelease":$prerelease,"body":"更新","html_url":"https://github.com/my-name/phone-agent/releases/tag/$tag","assets":$assets}"""
    private fun asset(name: String, url: String="https://github.com/my-name/phone-agent/releases/download/v2/$name") =
        """{"name":"$name","size":3,"browser_download_url":"$url","state":"uploaded"}"""
}
