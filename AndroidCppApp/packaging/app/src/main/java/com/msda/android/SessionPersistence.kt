package com.msda.android

import android.content.Context
import com.msda.android.steam.MafileRepository
import com.msda.android.steam.SteamSessionData
import kotlinx.coroutines.runBlocking

object SessionPersistence {
    /**
     * Save session to encrypted SharedPreferences and mirror to mafile session blocks.
     * Kotlin mafile state is the source of truth for the Nebula session stack.
     */
    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        SessionStore.saveSession(context, steamId, session)
        runBlocking {
            MafileRepository(context).writeSession(
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
}
