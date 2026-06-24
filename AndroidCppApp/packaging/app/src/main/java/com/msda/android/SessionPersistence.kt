package com.msda.android

import android.content.Context
import android.util.Log

private const val TAG = "SessionPersistence"

object SessionPersistence {
    /**
     * Save session to encrypted SharedPreferences, sync to native in-memory state,
     * and write session tokens back to the mafile on disk for persistence across reinstalls.
     */
    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        SessionStore.saveSession(context, steamId, session)
        syncToNative(steamId, session)
        writeBackToMafile(steamId, session)
    }

    private fun syncToNative(steamId: String, session: StoredSteamSession) {
        try {
            NativeBridge.updateSessionTokens(
                steamId,
                session.sessionId,
                session.steamLoginSecure,
                session.refreshToken,
                session.accessToken
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync session to native for $steamId", e)
        }
    }

    private fun writeBackToMafile(steamId: String, session: StoredSteamSession) {
        try {
            NativeBridge.updateMafileSessionTokens(
                steamId,
                session.sessionId,
                session.steamLoginSecure,
                session.refreshToken,
                session.accessToken
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write session back to mafile for $steamId", e)
        }
    }
}
