package com.msda.android

import com.msda.android.steam.MafileSecretsReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pure helpers for account deletion. Kept free of Android framework types so the destructive
 * decisions (which SteamIDs to purge and which files to remove) can be verified in unit tests.
 */
object AccountDeletionHelper {
    data class AccountEntry(val index: Int, val name: String, val steamId: String)

    data class FileClassification(
        val toDelete: List<File>,
        val unreadable: List<File>
    )

    /**
     * Parse the output of `NativeBridge.getAccounts()`.
     *
     * The current native bridge emits a JSON array of objects shaped like
     * `{"index":0,"accountName":"bob","steamId":"7656119..."}`. Older builds emitted one
     * `index|name|steamId` row per line, so that legacy format is still accepted to keep
     * existing state working. Anything that does not satisfy either contract is ignored: a
     * native-side format change must never make us resolve the wrong account.
     */
    fun parseAccounts(raw: String): List<AccountEntry> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        return if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
            parseJsonAccounts(trimmed)
        } else {
            parseLegacyAccounts(raw)
        }
    }

    private fun parseJsonAccounts(raw: String): List<AccountEntry> {
        return try {
            val array = if (raw.startsWith('[')) {
                JSONArray(raw)
            } else {
                JSONArray().put(JSONObject(raw))
            }
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val index = obj.optInt("index", -1)
                val name = obj.optString("accountName").ifBlank { obj.optString("name") }.trim()
                val steamId = obj.optString("steamId").trim()
                if (index < 0 || name.isBlank() || steamId.isBlank()) return@mapNotNull null
                AccountEntry(index, name, steamId)
            }
        } catch (_: Exception) {
            // org.json throws JSONException (and sometimes IllegalArgumentException/NPE) on
            // hostile input. Treat a broken payload as "no accounts" so deletion is aborted.
            emptyList()
        }
    }

    private fun parseLegacyAccounts(raw: String): List<AccountEntry> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 3) return@mapNotNull null
                val index = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1].trim()
                val steamId = parts[2].trim()
                if (name.isBlank() || steamId.isBlank()) return@mapNotNull null
                AccountEntry(index, name, steamId)
            }
    }

    /**
     * Resolve every SteamID whose account name matches [accountName] exactly (ignoring case).
     * An empty result means the target could not be resolved: deletion must be aborted, never
     * replaced by a raw-text/substring match.
     */
    fun resolveTargetSteamIds(raw: String, accountName: String): List<String> {
        return parseAccounts(raw)
            .filter { it.name.equals(accountName, ignoreCase = true) }
            .map { it.steamId }
            .distinct()
    }

    /**
     * Classify mafiles by their parsed SteamID. Only files whose parsed identity is in
     * [targetSteamIds] are safe to delete. A file that cannot be read or parsed is reported
     * as [unreadable] and is never offered for deletion, so a transient error cannot destroy it.
     */
    fun classifyFiles(
        mafiles: List<File>,
        targetSteamIds: Collection<String>,
        steamIdForFile: (File) -> String? = { MafileSecretsReader.steamIdForFile(it) }
    ): FileClassification {
        val targets = targetSteamIds.toSet()
        val toDelete = mutableListOf<File>()
        val unreadable = mutableListOf<File>()

        mafiles.forEach { file ->
            val steamId = try {
                steamIdForFile(file)
            } catch (_: Throwable) {
                null
            }
            val id = steamId
            when {
                id == null || id.isBlank() -> unreadable.add(file)
                targets.contains(id) -> toDelete.add(file)
            }
        }

        return FileClassification(toDelete, unreadable)
    }
}
