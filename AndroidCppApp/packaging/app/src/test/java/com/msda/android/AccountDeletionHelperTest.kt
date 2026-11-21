package com.msda.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class AccountDeletionHelperTest {
    private val bobId = "76561198000000001"
    private val bobbyId = "76561198000000002"

    private fun accountsRaw(): String = "0|bob|$bobId\n1|bobby|$bobbyId"

    @Test
    fun parseAccounts_readsIndexNameAndSteamId() {
        val entries = AccountDeletionHelper.parseAccounts("0|bob|$bobId")
        assertEquals(1, entries.size)
        assertEquals(0, entries[0].index)
        assertEquals("bob", entries[0].name)
        assertEquals(bobId, entries[0].steamId)
    }

    @Test
    fun resolveTargetSteamIds_matchesWholeNameNotSubstring() {
        assertEquals(listOf(bobId), AccountDeletionHelper.resolveTargetSteamIds(accountsRaw(), "bob"))
        assertEquals(listOf(bobbyId), AccountDeletionHelper.resolveTargetSteamIds(accountsRaw(), "BOBBY"))
    }

    @Test
    fun resolveTargetSteamIds_ignoresMalformedLines() {
        val raw = "garbage\n0|bob\neve|bob|$bobId|extra\n1|bobby|$bobbyId"
        assertEquals(listOf(bobbyId), AccountDeletionHelper.resolveTargetSteamIds(raw, "bob"))
    }

    @Test
    fun resolveTargetSteamIds_unparseableFormat_returnsEmpty() {
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds("bob,bobby", "bob"))
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds("", "bob"))
    }

    @Test
    fun classifyFiles_deletesOnlyTheMatchingSteamId() {
        val dir = Files.createTempDirectory("msda-delete-test").toFile()
        val bobFile = writeMafile(dir, "bob.mafile", "bob", bobId)
        val bobbyFile = writeMafile(dir, "bobby.mafile", "bobby", bobbyId)

        val result = AccountDeletionHelper.classifyFiles(listOf(bobFile, bobbyFile), listOf(bobId))

        assertEquals(listOf(bobFile), result.toDelete)
        assertTrue(result.unreadable.isEmpty())
    }

    @Test
    fun classifyFiles_neverOffersUnreadableFileForDeletion() {
        val dir = Files.createTempDirectory("msda-delete-test").toFile()
        val bad = File(dir, "bad.mafile").apply { writeText("not json") }

        val parsed = AccountDeletionHelper.classifyFiles(listOf(bad), listOf(bobId))
        assertTrue(parsed.toDelete.isEmpty())
        assertEquals(listOf(bad), parsed.unreadable)

        val thrown = AccountDeletionHelper.classifyFiles(listOf(bad), listOf(bobId)) { _: File ->
            throw IOException("transient read failure")
        }
        assertTrue(thrown.toDelete.isEmpty())
        assertEquals(listOf(bad), thrown.unreadable)
        assertTrue(bad.exists())
    }

    private fun writeMafile(dir: File, name: String, account: String, steamId: String): File {
        val json = JSONObject()
            .put("account_name", account)
            .put("steamid", steamId)
            .put("shared_secret", "AAAA")
            .put("identity_secret", "BBBB")
            .put("device_id", "android:device")
        return File(dir, name).apply { writeText(json.toString()) }
    }
}
