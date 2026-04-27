package com.msda.android

import android.content.Context
import org.json.JSONObject

data class StoredSteamSession(
    val steamLoginSecure: String,
    val sessionId: String,
    val refreshToken: String = "",
    val accessToken: String = "",
    val accountName: String = ""
)

object SessionStore {
    private const val PREFS = "msda_sessions"

    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$steamId.steamLoginSecure", session.steamLoginSecure)
            .putString("$steamId.sessionid", session.sessionId)
            .putString("$steamId.refreshToken", session.refreshToken)
            .putString("$steamId.accessToken", session.accessToken)
            .putString("$steamId.accountName", session.accountName)
            .apply()
    }

    fun loadSession(context: Context, steamId: String): StoredSteamSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val login = prefs.getString("$steamId.steamLoginSecure", null)
        val sessionId = prefs.getString("$steamId.sessionid", null)

        if (login.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return null
        }

        return StoredSteamSession(
            steamLoginSecure = login,
            sessionId = sessionId,
            refreshToken = prefs.getString("$steamId.refreshToken", "") ?: "",
            accessToken = prefs.getString("$steamId.accessToken", "") ?: "",
            accountName = prefs.getString("$steamId.accountName", "") ?: ""
        )
    }

    fun getAccountName(context: Context, steamId: String): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("$steamId.accountName", null)
    }

    /**
     * Attempt to restore a valid session without user interaction.
     * 1. Try a silent refresh using any stored refresh token.
     * 2. If that fails, fall back to the stored password.
     *
     * After a successful renewal or re‑authentication the new session tokens
     * are persisted to SharedPreferences so that loadSession returns valid
     * cookies immediately.
     *
     * @return true if the session is now live; false if all attempts failed.
     */
    fun renewSessionOrFallback(context: Context, steamId: String, accountName: String): Boolean {
        val deviceId = NativeBridge.getPermanentDeviceId(context)

        // 1. Token‑based refresh (native implementation).
        val refreshed = NativeBridge.tryRefreshSession(steamId, deviceId)
        if (refreshed) {
            updateStoredSessionFromActive(context, steamId, accountName)
            return true
        }

        // 2. Fallback: re‑authenticate with the stored password.
        val password = PasswordManager.getPassword(context, accountName)
        if (password.isNullOrBlank()) return false

        val reauthenticated = NativeBridge.reauthWithPassword(steamId, password, deviceId)
        if (reauthenticated) {
            updateStoredSessionFromActive(context, steamId, accountName)
            return true
        }
        return false
    }

    /**
     * Reads the active session from the native layer and saves it to SharedPreferences.
     */
    private fun updateStoredSessionFromActive(
        context: Context,
        steamId: String,
        fallbackAccountName: String
    ) {
        try {
            val jsonStr = NativeBridge.getActiveAccount()
            if (jsonStr.isBlank()) return

            val obj = JSONObject(jsonStr)
            val session = StoredSteamSession(
                steamLoginSecure = obj.optString("steamLoginSecure", ""),
                sessionId = obj.optString("sessionId", ""),
                refreshToken = obj.optString("refreshToken", ""),
                accessToken = obj.optString("accessToken", ""),
                accountName = obj.optString("accountName", fallbackAccountName)
            )
            if (session.steamLoginSecure.isNotBlank() && session.sessionId.isNotBlank()) {
                saveSession(context, steamId, session)
            }
        } catch (_: Exception) {
            // If parsing fails we simply do not update SharedPreferences.
            // The native state is still valid for the current session.
        }
    }
}
