package com.msda.android

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralized session renewal used by every code path (foreground confirmations,
 * background worker, proactive renewal, respond actions).
 *
 * Renewal order mirrors the official Steam mobile client:
 *   1. refresh_token  → GenerateAccessTokenForApp  (silent, rotates the refresh token)
 *   2. cached password → full LoginV2              (silent fallback)
 *
 * Every successful renewal is persisted through [SessionPersistence] (encrypted store +
 * native in-memory + mafile on disk), so a valid refresh token survives reinstalls and
 * keystore loss, letting the user stay logged in for as long as Steam keeps the refresh
 * token valid — even after months of inactivity.
 */
object SessionManager {
    private const val TAG = "SessionManager"

    // Per-account single-flight lock: prevents the hub bulk-renewal, the periodic worker,
    // and the foreground confirmation poll from refreshing the SAME account concurrently.
    // Concurrent refreshes would each rotate the refresh token and could invalidate each
    // other — fatal when scaling to hundreds of accounts.
    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Attempt to silently renew the session for [auth].
     * Returns the refreshed [ConfirmationAuthContext] on success, or null if no silent
     * path succeeded (caller should then prompt for a password).
     */
    suspend fun renew(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext? {
        val mutex = locks.getOrPut(auth.steamId) { Mutex() }
        return mutex.withLock {
            // Another caller may have just renewed this account while we waited for the lock.
            // Detect that by a changed steamLoginSecure and reuse it instead of hitting Steam again.
            val fresh = SessionStore.loadSession(context, auth.steamId)
            if (fresh != null &&
                fresh.steamLoginSecure.isNotBlank() &&
                fresh.steamLoginSecure != auth.steamLoginSecure
            ) {
                return@withLock auth.withSession(fresh)
            }
            renewLocked(context, auth)
        }
    }

    private suspend fun renewLocked(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext? {
        // 1. Refresh token (preferred, silent, rotating)
        if (auth.refreshToken.isNotBlank()) {
            try {
                val r = SteamAuthService.refreshSessionUsingToken(
                    refreshToken = auth.refreshToken,
                    steamId = auth.steamId
                )
                if (r.success && !r.steamLoginSecure.isNullOrBlank() && !r.sessionId.isNullOrBlank()) {
                    return persistAndBuild(context, auth, r)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Refresh-token renewal failed for ${auth.steamId}", e)
            }
        }

        // 2. Cached password (silent full re-login)
        if (auth.accountName.isNotBlank()) {
            val password = PasswordManager.getPassword(context, auth.accountName)
            if (!password.isNullOrBlank()) {
                try {
                    val r = SteamAuthService.refreshSessionUsingPassword(
                        accountName = auth.accountName,
                        password = password,
                        steamId = auth.steamId
                    )
                    if (r.success && !r.steamLoginSecure.isNullOrBlank() && !r.sessionId.isNullOrBlank()) {
                        return persistAndBuild(context, auth, r)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Password renewal failed for ${auth.steamId}", e)
                }
            }
        }

        return null
    }

    private fun persistAndBuild(
        context: Context,
        auth: ConfirmationAuthContext,
        result: SteamAuthResult
    ): ConfirmationAuthContext {
        val newAuth = auth.copy(
            steamLoginSecure = result.steamLoginSecure!!,
            sessionId = result.sessionId!!,
            accessToken = result.accessToken ?: auth.accessToken,
            refreshToken = result.refreshToken ?: auth.refreshToken
        )
        SessionPersistence.saveSession(
            context,
            auth.steamId,
            StoredSteamSession(
                steamLoginSecure = newAuth.steamLoginSecure,
                sessionId = newAuth.sessionId,
                refreshToken = newAuth.refreshToken,
                accessToken = newAuth.accessToken,
                accountName = auth.accountName,
                sessionExpiresAtMs = result.sessionExpiresAtMs
            )
        )
        return newAuth
    }
}
