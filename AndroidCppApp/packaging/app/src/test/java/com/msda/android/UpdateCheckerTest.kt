package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
