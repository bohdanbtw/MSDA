package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the manifest-level part of the app-lock fix. */
class AndroidManifestSecurityTest {
    private val manifest: String by lazy {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("AndroidManifest.xml not found from ${File(".").absolutePath}")
        file.readText()
    }

    @Test
    fun hubActivityIsNotExported() {
        val hub = activityBlock("HubActivity")
        assertTrue("HubActivity must explicitly set exported=false", hub.contains("android:exported=\"false\""))
        assertFalse(hub.contains("android:exported=\"true\""))
    }

    @Test
    fun settingsAndMainActivityAreNotExported() {
        assertTrue(activityBlock("SettingsActivity").contains("android:exported=\"false\""))
        assertTrue(activityBlock("MainActivity").contains("android:exported=\"false\""))
    }

    @Test
    fun lockActivityIsTheSoleLauncher() {
        assertEquals(1, Regex("android.intent.category.LAUNCHER").findAll(manifest).count())
        assertTrue(activityBlock("LockActivity").contains("android.intent.category.LAUNCHER"))
    }

    @Test
    fun customApplicationTracksLockState() {
        assertTrue(manifest.contains("android:name=\".MsdaApplication\""))
    }

    private fun activityBlock(simpleName: String): String {
        val pattern = "<activity\\b[^>]*android:name=\"\\.$simpleName\"[^>]*?(?:/>|>.*?</activity>)"
        return Regex(pattern, Regex.DOTALL).find(manifest)?.value
            ?: error("Activity .$simpleName not found in manifest")
    }
}
