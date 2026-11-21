package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun compareVersions_ordersSemverCorrectly() {
        assertTrue(UpdateChecker.isNewer("1.4.3", "1.4.2"))
        assertTrue(UpdateChecker.isNewer("1.5.0", "1.4.9"))
        assertFalse(UpdateChecker.isNewer("1.4.2", "1.4.2"))
        assertFalse(UpdateChecker.isNewer("1.4.1", "1.4.2"))
        assertTrue(UpdateChecker.isNewer("v1.4.3", "1.4.2"))
        assertEquals(0, UpdateChecker.compareVersions("1.4.2", "1.4.2"))
    }

    @Test
    fun parseLatestRelease_readsApkAsset() {
        val json = """
            {
              "tag_name": "1.4.3",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/bohdanbtw/MSDA/releases/tag/1.4.3",
              "body": "## What's Changed\n- Test note",
              "assets": [
                {
                  "name": "MSDA-1.4.3.apk",
                  "browser_download_url": "https://github.com/bohdanbtw/MSDA/releases/download/1.4.3/MSDA-1.4.3.apk"
                }
              ]
            }
        """.trimIndent()

        val release = UpdateChecker.parseLatestRelease(json)
        assertNotNull(release)
        assertEquals("1.4.3", release!!.versionName)
        assertTrue(release.apkDownloadUrl.endsWith("MSDA-1.4.3.apk"))
        assertTrue(release.releaseNotes.contains("Test note"))
    }

    @Test
    fun isTrustedDownloadUrl_requiresHttpsAndGithubHost() {
        assertTrue(UpdateChecker.isTrustedDownloadUrl(
            "https://github.com/bohdanbtw/MSDA/releases/download/1.5.0/MSDA-1.5.0.apk"
        ))
        assertTrue(UpdateChecker.isTrustedDownloadUrl(
            "https://objects.githubusercontent.com/MSDA-1.5.0.apk"
        ))
        assertFalse(UpdateChecker.isTrustedDownloadUrl(
            "http://github.com/bohdanbtw/MSDA/releases/download/1.5.0/MSDA-1.5.0.apk"
        ))
        assertFalse(UpdateChecker.isTrustedDownloadUrl(
            "https://evil.example.com/MSDA-1.5.0.apk"
        ))
    }

    @Test
    fun normalizeFingerprint_stripsSeparatorsAndCase() {
        assertEquals("ABCDEF0123", UpdateChecker.normalizeFingerprint("ab:cd:ef 01 23\n"))
    }

    @Test
    fun parseLatestRelease_rejectsUntrustedApkUrl() {
        val json = """
            {
              "tag_name": "1.5.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/bohdanbtw/MSDA/releases/tag/1.5.0",
              "assets": [
                {
                  "name": "MSDA-1.5.0.apk",
                  "browser_download_url": "http://evil.example.com/MSDA-1.5.0.apk"
                }
              ]
            }
        """.trimIndent()

        assertNull(UpdateChecker.parseLatestRelease(json))
    }
}
