package com.msda.android.steam

import android.content.Context
import com.msda.android.SessionStore
import com.msda.android.StoredSteamSession
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MafileRepository(private val context: Context) {

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

    suspend fun writeSession(session: SteamSessionData) {
        SessionLocks.withLock(session.steamId) {
            writeSessionUnlocked(session)
        }
    }

    /**
     * Persist without taking the lock. Only call while [SessionLocks.withLock] is held for
     * [SteamSessionData.steamId]; all other callers must use [writeSession].
     */
    internal fun writeSessionUnlocked(session: SteamSessionData) {
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
        writeJsonAtomically(file, json.toString(2))
    }

    /** Replace the mafile via a temp file in the same directory so readers never see a partial write. */
    private fun writeJsonAtomically(file: File, contents: String) {
        val directory = file.parentFile ?: return
        val temp = File.createTempFile(".${file.name}.", ".tmp", directory)
        try {
            temp.outputStream().use { output ->
                output.write(contents.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
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
