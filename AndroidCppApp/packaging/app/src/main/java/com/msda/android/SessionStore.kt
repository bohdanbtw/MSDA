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

}
