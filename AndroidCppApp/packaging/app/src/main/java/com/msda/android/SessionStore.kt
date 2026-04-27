package com.msda.android

import android.content.Context

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
     * @return true if the session is now live; false if all attempts failed.
     */
    fun renewSessionOrFallback(context: Context, steamId: String, accountName: String): Boolean {
        // 1. Token‑based refresh (native implementation).
        val refreshed = NativeBridge.tryRefreshSession(steamId)
        if (refreshed) {
            // The native layer should have updated the active session.
            return true
        }

        // 2. Fallback: re‑authenticate with the stored password.
        val password = PasswordManager.getPassword(context, accountName)
        if (password.isNullOrBlank()) return false

        val reauthed = NativeBridge.reauthWithPassword(steamId, password)
        if (reauthed) {
            // After successful re‑auth the session is live;
            // the caller can reload it via loadSession.
            return true
        }
        return false
    }
}
