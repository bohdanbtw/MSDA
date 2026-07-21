package com.msda.android.steam

import android.content.Context
import com.msda.android.SessionStore
import com.msda.android.StoredSteamSession
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MafileRepository(private val context: Context) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun readSession(steamId: String): SteamSessionData? {
        if (steamId.isBlank()) return null
        val cached = SessionStore.loadSession(context, steamId)
        val mafileJson = readMafileJson(steamId)
        return MafileSessionReader.parse(
            steamId = steamId,
            mafileJson = mafileJson,
            cachedAccountName = cached?.accountName,
            cachedSessionId = cached?.sessionId,
            cachedSteamLoginSecure = cached?.steamLoginSecure,
            cachedRefreshToken = cached?.refreshToken,
            cachedAccessToken = cached?.accessToken
        )
    }

    fun writeSessionBlocking(session: SteamSessionData) {
        kotlinx.coroutines.runBlocking { writeSession(session) }
    }

    suspend fun writeSession(session: SteamSessionData) {
        val mutex = locks.getOrPut(session.steamId) { Mutex() }
        mutex.withLock {
            writeMafileSession(session)
            SessionStore.saveSession(
                context,
                session.steamId,
                StoredSteamSession(
                    steamLoginSecure = session.steamLoginSecure,
                    sessionId = session.sessionId,
                    refreshToken = session.refreshToken,
                    accessToken = session.accessToken,
                    accountName = session.accountName,
                    sessionExpiresAtMs = runCatching { SteamTokenHelper.parse(session.accessToken).expiresAtMs }
                        .getOrDefault(0L)
                )
            )
        }
    }

    private fun writeMafileSession(session: SteamSessionData) {
        val file = findMafileForSteamId(session.steamId) ?: return
        val json = try {
            JSONObject(file.readText())
        } catch (_: Throwable) {
            JSONObject()
        }
        val sessionData = json.optJSONObject("SessionData") ?: JSONObject().also { json.put("SessionData", it) }
        sessionData.put("SessionID", session.sessionId)
        sessionData.put("steamLoginSecure", session.steamLoginSecure)
        sessionData.put("RefreshToken", session.refreshToken)
        sessionData.put("AccessToken", session.accessToken)
        sessionData.put("SteamID", session.steamId)

        json.put("sessionid", session.sessionId)
        json.put("steamLoginSecure", session.steamLoginSecure)
        json.put("refresh_token", session.refreshToken)
        json.put("OAuthToken", session.refreshToken)
        json.put("access_token", session.accessToken)
        if (session.accountName.isNotBlank()) {
            json.put("account_name", session.accountName)
        }
        file.writeText(json.toString(2))
    }

    private fun readMafileJson(steamId: String): JSONObject? {
        val file = findMafileForSteamId(steamId) ?: return null
        return try {
            JSONObject(file.readText())
        } catch (_: Throwable) {
            null
        }
    }

    private fun findMafileForSteamId(steamId: String): File? {
        val mafilesDir = File(context.filesDir, "mafiles")
        if (!mafilesDir.exists()) return null
        return mafilesDir.listFiles()
            ?.firstOrNull { file ->
                if (!file.isFile || !file.name.endsWith(".mafile", ignoreCase = true)) return@firstOrNull false
                MafileSecretsReader.steamIdForFile(file) == steamId
            }
    }
}
