package com.msda.android

import android.content.Context

data class StoredSteamSession(
    val steamLoginSecure: String,
    val sessionId: String
)

object SessionStore {
    private const val PREFS = "msda_sessions"

    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("$steamId.steamLoginSecure", session.steamLoginSecure)
            .putString("$steamId.sessionid", session.sessionId)
            .apply()
    }

    fun loadSession(context: Context, steamId: String): StoredSteamSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val login = prefs.getString("$steamId.steamLoginSecure", null)
        val sessionId = prefs.getString("$steamId.sessionid", null)

        if (login.isNullOrBlank() || sessionId.isNullOrBlank()) {
            return null
        }

        return StoredSteamSession(login, sessionId)
    }
}
