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

    /** Legacy `index|name|steamId` wire format, still accepted for backwards compatibility. */
    private fun accountsRaw(): String = "0|bob|$bobId\n1|bobby|$bobbyId"

    /** Current JSON wire format emitted by NativeBridge.getAccounts(). */
    private fun accountsJson(): String =
        "[{\"index\":0,\"accountName\":\"bob\",\"steamId\":\"$bobId\"}," +
            "{\"index\":1,\"accountName\":\"bobby\",\"steamId\":\"$bobbyId\"}]"

    @Test
    fun parseAccounts_readsIndexNameAndSteamId() {
        val legacyEntries = AccountDeletionHelper.parseAccounts("0|bob|$bobId")
        assertEquals(1, legacyEntries.size)
        assertEquals(0, legacyEntries[0].index)
        assertEquals("bob", legacyEntries[0].name)
        assertEquals(bobId, legacyEntries[0].steamId)

        val jsonEntries = AccountDeletionHelper.parseAccounts(accountsJson())
        assertEquals(2, jsonEntries.size)
        assertEquals(0, jsonEntries[0].index)
        assertEquals("bob", jsonEntries[0].name)
        assertEquals(bobId, jsonEntries[0].steamId)
        assertEquals(1, jsonEntries[1].index)
        assertEquals("bobby", jsonEntries[1].name)
        assertEquals(bobbyId, jsonEntries[1].steamId)
    }

    @Test
    fun resolveTargetSteamIds_matchesWholeNameNotSubstring() {
        assertEquals(listOf(bobId), AccountDeletionHelper.resolveTargetSteamIds(accountsRaw(), "bob"))
        assertEquals(listOf(bobbyId), AccountDeletionHelper.resolveTargetSteamIds(accountsRaw(), "BOBBY"))

        assertEquals(listOf(bobId), AccountDeletionHelper.resolveTargetSteamIds(accountsJson(), "bob"))
        assertEquals(listOf(bobbyId), AccountDeletionHelper.resolveTargetSteamIds(accountsJson(), "BOBBY"))
    }

    @Test
    fun resolveTargetSteamIds_ignoresMalformedLines() {
        val raw = "garbage\n0|bob\neve|bob|$bobId|extra\n1|bobby|$bobbyId"
        // Only the well-formed row is accepted. "garbage" has no fields, "0|bob" is truncated,
        // and "eve|bob|..." has a non-numeric index. The valid row belongs to "bobby", which
        // must not be confused with "bob".
        assertEquals(listOf(bobbyId), AccountDeletionHelper.resolveTargetSteamIds(raw, "bobby"))
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds(raw, "bob"))
    }

    @Test
    fun resolveTargetSteamIds_unparseableFormat_returnsEmpty() {
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds("bob,bobby", "bob"))
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds("", "bob"))
        assertEquals(emptyList<String>(), AccountDeletionHelper.resolveTargetSteamIds("not json {", "bob"))
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
