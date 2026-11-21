package com.msda.android

import com.msda.android.steam.MafileSecretsReader
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
     * Parse the pipe-delimited `NativeBridge.getAccounts()` output (`index|name|steamId`).
     * Lines that do not satisfy the contract are ignored, so a native-side format change
     * cannot make us resolve the wrong account.
     */
    fun parseAccounts(raw: String): List<AccountEntry> {
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
