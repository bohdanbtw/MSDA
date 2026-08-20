package com.msda.android

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File

/**
 * Shared mafile import helpers: SessionStore cleanup, login gating, and SteamID conflict UX (T065).
 */
object MafileImportHelper {
    private val STEAM_ID_KEYS = listOf("steamid", "SteamID", "SteamId")

    enum class ConflictChoice {
        REPLACE,
        KEEP_BOTH,
        OVERWRITE_FILENAME,
        CANCEL
    }

    sealed class ImportConflict {
        data class SameSteamId(
            val steamId: String,
            val existingFiles: List<File>,
            val preferredName: String
        ) : ImportConflict()

        data class FilenameDifferentSteam(
            val target: File,
            val existingSteamId: String,
            val incomingSteamId: String
        ) : ImportConflict()
    }

    fun parseSteamId(mafile: File): String? {
        return try {
            parseSteamIdFromContent(mafile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun parseSteamIdFromContent(content: String): String? {
        if (content.isBlank()) return null

        try {
            val json = JSONObject(content)
            for (key in STEAM_ID_KEYS) {
                if (!json.has(key)) continue
                val value = json.get(key).toString().trim()
                if (value.isNotBlank() && value != "unknown") return value
            }
            json.optJSONObject("SessionData")?.let { session ->
                val sid = session.optString("SteamID", session.optString("steamid", "")).trim()
                if (sid.isNotBlank() && sid != "unknown") return sid
            }
        } catch (_: Exception) {
            // Fall back to lightweight key search for non-standard JSON.
        }

        for (key in STEAM_ID_KEYS) {
            val quoted = Regex(""""$key"\s*:\s*"([0-9]+)"""", RegexOption.IGNORE_CASE)
                .find(content)?.groupValues?.getOrNull(1)?.trim()
            if (!quoted.isNullOrBlank()) return quoted

            val numeric = Regex(""""$key"\s*:\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                .find(content)?.groupValues?.getOrNull(1)?.trim()
            if (!numeric.isNullOrBlank()) return numeric
        }

        return null
    }

    fun listMafiles(importDir: File): List<File> {
        return importDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mafile", ignoreCase = true) }
            .orEmpty()
    }

    fun findFilesForSteamId(importDir: File, steamId: String): List<File> {
        if (steamId.isBlank()) return emptyList()
        return listMafiles(importDir).filter { parseSteamId(it) == steamId }
    }

    fun uniqueMafileName(importDir: File, preferredName: String): String {
        val base = preferredName.removeSuffix(".mafile").removeSuffix(".MAFILE")
            .ifBlank { "imported" }
        var candidate = "$base.mafile"
        var n = 2
        while (File(importDir, candidate).exists()) {
            candidate = "$base ($n).mafile"
            n++
        }
        return candidate
    }

    fun detectConflict(importDir: File, preferredName: String, content: String): ImportConflict? {
        val incomingId = parseSteamIdFromContent(content).orEmpty()
        if (incomingId.isNotBlank()) {
            val existing = findFilesForSteamId(importDir, incomingId)
            if (existing.isNotEmpty()) {
                return ImportConflict.SameSteamId(incomingId, existing, preferredName)
            }
        }

        val target = File(importDir, preferredName)
        if (!target.exists()) return null

        val existingId = parseSteamId(target).orEmpty()
        if (incomingId.isNotBlank() && existingId.isNotBlank() && incomingId == existingId) {
            // Same steamId + same file: treat as replace-or-keep (covered above when existing found).
            return ImportConflict.SameSteamId(incomingId, listOf(target), preferredName)
        }
        // Filename taken by a different (or unknown) account — never silent clobber.
        return ImportConflict.FilenameDifferentSteam(
            target = target,
            existingSteamId = existingId.ifBlank { "?" },
            incomingSteamId = incomingId.ifBlank { "?" }
        )
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    /** Remove any cached session for this mafile so fresh on-disk tokens take precedence. */
    fun clearSessionStoreForMafile(context: Context, mafile: File) {
        val steamId = parseSteamId(mafile) ?: return
        SessionStore.delete(context, steamId)
    }

    fun canSilentRenew(context: Context, auth: ConfirmationAuthContext): Boolean {
        if (auth.refreshToken.isNotBlank()) return true
        if (auth.accountName.isBlank()) return false
        return !PasswordManager.getPassword(context, auth.accountName).isNullOrBlank()
    }

    fun hasUsableSession(auth: ConfirmationAuthContext): Boolean {
        return auth.steamLoginSecure.isNotBlank() && auth.sessionId.isNotBlank()
    }

    /**
     * True when the user must enter a password to use confirmations after import.
     * Accounts with live session cookies in the mafile can skip the immediate prompt.
     */
    fun needsInteractiveLogin(context: Context, auth: ConfirmationAuthContext?): Boolean {
        if (auth == null) return false
        if (hasUsableSession(auth)) return false
        if (canSilentRenew(context, auth)) return false
        return auth.accountName.isNotBlank()
    }

    /**
     * Read URI → resolve SteamID / filename conflicts → write → native reload.
     * No Steam Guard / getlist traffic.
     */
    fun importFromUri(
        activity: Activity,
        uri: Uri,
        onStatus: (String) -> Unit,
        onImported: (targetFile: File, nativeOk: Boolean) -> Unit
    ) {
        val fileName = queryDisplayName(activity, uri) ?: "imported.mafile"
        if (!fileName.endsWith(".mafile", ignoreCase = true)) {
            onStatus(activity.getString(R.string.status_invalid_file))
            return
        }

        val content = try {
            activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Throwable) {
            null
        }
        if (content.isNullOrBlank()) {
            onStatus(activity.getString(R.string.status_copy_failed))
            return
        }

        val importDir = File(activity.filesDir, "mafiles")
        if (!importDir.exists()) importDir.mkdirs()

        val conflict = detectConflict(importDir, fileName, content)
        if (conflict == null) {
            finishWrite(activity, importDir, fileName, content, onStatus, onImported)
            return
        }

        showConflictDialog(activity, conflict) { choice ->
            when (choice) {
                ConflictChoice.CANCEL -> {
                    onStatus(activity.getString(R.string.status_import_cancelled))
                }
                ConflictChoice.REPLACE -> {
                    val same = conflict as? ImportConflict.SameSteamId
                    if (same != null) {
                        same.existingFiles.forEach { existing ->
                            runCatching { existing.delete() }
                        }
                        // Prefer requested name only if free; never clobber another SteamID's file.
                        val writeName = when {
                            !File(importDir, same.preferredName).exists() -> same.preferredName
                            else -> uniqueMafileName(importDir, same.preferredName)
                        }
                        finishWrite(activity, importDir, writeName, content, onStatus, onImported)
                    }
                }
                ConflictChoice.KEEP_BOTH -> {
                    val name = uniqueMafileName(importDir, fileName)
                    finishWrite(activity, importDir, name, content, onStatus, onImported)
                }
                ConflictChoice.OVERWRITE_FILENAME -> {
                    val clash = conflict as? ImportConflict.FilenameDifferentSteam
                    if (clash != null) {
                        // Drop SessionStore for the displaced account before clobber.
                        clearSessionStoreForMafile(activity, clash.target)
                        finishWrite(activity, importDir, clash.target.name, content, onStatus, onImported)
                    }
                }
            }
        }
    }

    private fun showConflictDialog(
        activity: Activity,
        conflict: ImportConflict,
        onChoice: (ConflictChoice) -> Unit
    ) {
        when (conflict) {
            is ImportConflict.SameSteamId -> {
                val names = conflict.existingFiles.joinToString(", ") { it.name }
                android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.import_conflict_title)
                    .setMessage(
                        activity.getString(
                            R.string.import_conflict_same_steamid,
                            conflict.steamId,
                            names
                        )
                    )
                    .setPositiveButton(R.string.import_conflict_replace) { _, _ ->
                        onChoice(ConflictChoice.REPLACE)
                    }
                    .setNeutralButton(R.string.import_conflict_keep_both) { _, _ ->
                        onChoice(ConflictChoice.KEEP_BOTH)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        onChoice(ConflictChoice.CANCEL)
                    }
                    .setOnCancelListener { onChoice(ConflictChoice.CANCEL) }
                    .show()
            }
            is ImportConflict.FilenameDifferentSteam -> {
                android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.import_conflict_title)
                    .setMessage(
                        activity.getString(
                            R.string.import_conflict_filename,
                            conflict.target.name,
                            conflict.existingSteamId,
                            conflict.incomingSteamId
                        )
                    )
                    .setPositiveButton(R.string.import_conflict_overwrite) { _, _ ->
                        onChoice(ConflictChoice.OVERWRITE_FILENAME)
                    }
                    .setNeutralButton(R.string.import_conflict_keep_both) { _, _ ->
                        onChoice(ConflictChoice.KEEP_BOTH)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        onChoice(ConflictChoice.CANCEL)
                    }
                    .setOnCancelListener { onChoice(ConflictChoice.CANCEL) }
                    .show()
            }
        }
    }

    private fun finishWrite(
        activity: Activity,
        importDir: File,
        fileName: String,
        content: String,
        onStatus: (String) -> Unit,
        onImported: (File, Boolean) -> Unit
    ) {
        val targetFile = File(importDir, fileName)
        val written = try {
            targetFile.writeText(content)
            true
        } catch (_: Throwable) {
            false
        }
        if (!written) {
            onStatus(activity.getString(R.string.status_copy_failed))
            return
        }

        clearSessionStoreForMafile(activity, targetFile)

        val imported = try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
            false
        }

        onStatus(
            if (imported) activity.getString(R.string.status_import_success)
            else activity.getString(R.string.status_import_failed)
        )
        onImported(targetFile, imported)
    }
}
