package com.msda.android

import android.content.Context
import com.msda.android.steam.MafileRepository
import com.msda.android.steam.SessionLocks
import com.msda.android.steam.SteamSessionData
import kotlinx.coroutines.runBlocking

object SessionPersistence {
    /**
     * Save session to encrypted SharedPreferences and mirror to mafile session blocks.
     * Kotlin mafile state is the source of truth for the Nebula session stack.
     */
    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        runBlocking {
            SessionLocks.withLock(steamId) {
                saveSessionUnlocked(context, steamId, session)
            }
        }
    }

    /**
     * Persist without taking the lock. Only call while [SessionLocks.withLock] is held for
     * [steamId]; callers outside a locked session operation must use [saveSession].
     */
    internal fun saveSessionUnlocked(context: Context, steamId: String, session: StoredSteamSession) {
        SessionStore.saveSession(context, steamId, session)
        MafileRepository(context).writeSessionUnlocked(
            SteamSessionData(
                steamId = steamId,
                accountName = session.accountName,
                sessionId = session.sessionId,
                steamLoginSecure = session.steamLoginSecure,
                refreshToken = session.refreshToken,
                accessToken = session.accessToken
            )
        )
    }
}
