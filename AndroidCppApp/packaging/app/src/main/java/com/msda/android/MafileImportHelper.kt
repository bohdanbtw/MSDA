package com.msda.android

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Shared mafile import helpers: drop stale SessionStore entries on re-import and decide
 * whether an interactive login is still required.
 */
object MafileImportHelper {
    private val STEAM_ID_KEYS = listOf("steamid", "SteamID", "SteamId")

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
}
