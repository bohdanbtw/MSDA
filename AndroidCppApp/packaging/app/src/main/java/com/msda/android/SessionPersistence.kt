package com.msda.android

import android.content.Context
import com.msda.android.steam.MafileRepository
import com.msda.android.steam.SessionLocks
import com.msda.android.steam.SteamSessionData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SessionPersistence {
    // Persistence triggered from UI callbacks must not block the main thread with
    // runBlocking. The work still runs under the per-account session lock.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Save session to the encrypted store and mirror it to the mafile session
     * block. Kotlin mafile state is the source of truth for the Nebula stack.
     */
    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        persistenceScope.launch {
            SessionLocks.withLock(steamId) {
                saveSessionUnlocked(context, steamId, session)
            }
        }
    }

    /**
     * Persist without taking the lock. Only call while [SessionLocks.withLock] is held for
     * [steamId]; callers outside a locked session operation must use [saveSession].
     *
     * The caller's [session] (including its expiry) is the single authoritative
     * value — [MafileRepository.writeSessionUnlocked] performs the one and only
     * write to SessionStore and receives that expiry verbatim.
     */
    internal fun saveSessionUnlocked(context: Context, steamId: String, session: StoredSteamSession) {
        MafileRepository(context).writeSessionUnlocked(
            SteamSessionData(
                steamId = steamId,
                accountName = session.accountName,
                sessionId = session.sessionId,
                steamLoginSecure = session.steamLoginSecure,
                refreshToken = session.refreshToken,
                accessToken = session.accessToken
            ),
            sessionExpiresAtMs = session.sessionExpiresAtMs
        )
    }
}
