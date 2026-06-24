package com.msda.android

import android.content.Context

object SessionPersistence {
    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        SessionStore.saveSession(context, steamId, session)
        try {
            NativeBridge.updateSessionTokens(
                steamId,
                session.sessionId,
                session.steamLoginSecure,
                session.refreshToken,
                session.accessToken
            )
        } catch (_: Throwable) {
        }
    }
}
