package com.msda.android.steam

import android.content.Context
import com.msda.android.AppSettings
import com.msda.android.ConfirmationAuthContext
import com.msda.android.SessionStore

/** Merge native secrets payload with Kotlin-owned session state (mafile + encrypted cache). */
object AuthContextMerger {
    fun merge(context: Context, base: ConfirmationAuthContext): ConfirmationAuthContext {
        if (!AppSettings.isNebulaSessionStackEnabled(context)) {
            val saved = SessionStore.loadSession(context, base.steamId)
            return if (saved != null) base.withSession(saved) else base
        }
        val session = MafileRepository(context).readSession(base.steamId)
            ?: SessionStore.loadSession(context, base.steamId)?.let { stored ->
                SteamSessionData(
                    steamId = base.steamId,
                    accountName = stored.accountName.ifBlank { base.accountName },
                    sessionId = stored.sessionId,
                    steamLoginSecure = stored.steamLoginSecure,
                    refreshToken = stored.refreshToken,
                    accessToken = stored.accessToken.ifBlank {
                        SteamTokenHelper.extractJwt(stored.steamLoginSecure)
                    }
                )
            }
            ?: return base
        return base.copy(
            sessionId = session.sessionId,
            steamLoginSecure = session.steamLoginSecure,
            refreshToken = session.refreshToken,
            accessToken = session.accessToken,
            accountName = session.accountName.ifBlank { base.accountName }
        )
    }

    fun isSessionNearExpiry(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        if (AppSettings.isNebulaSessionStackEnabled(context)) {
            val session = MafileRepository(context).readSession(steamId) ?: return true
            val token = runCatching { SteamTokenHelper.parse(session.accessToken) }.getOrNull()
                ?: return true
            return System.currentTimeMillis() >= (token.expiresAtMs - 5 * 60 * 1000L)
        }
        return SessionStore.isSessionExpired(context, steamId) ||
            (SessionStore.loadSession(context, steamId)?.sessionExpiresAtMs ?: 0L) <= 0L
    }
}
