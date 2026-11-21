package com.msda.android.steam

import android.content.Context
import com.msda.android.ConfirmationAuthContext
import com.msda.android.NeedPasswordException
import com.msda.android.PasswordManager

object SessionHandler {

    suspend fun <T> handle(
        context: Context,
        auth: ConfirmationAuthContext,
        block: suspend (ConfirmationAuthContext, okhttp3.OkHttpClient) -> T
    ): T {
        return SessionLocks.withLock(auth.steamId) {
            val valid = ensureValidUnlocked(context, auth)
            try {
                val client = SteamHttpClient.create(context, valid.steamId)
                AdmissionHelper.seedMobileSessionCookies(valid.steamId, valid.toSessionData())
                block(valid, client)
            } catch (e: Throwable) {
                if (!isSessionInvalid(e)) throw e
                val refreshed = refreshOrReloginUnlocked(context, valid)
                val client = SteamHttpClient.create(context, refreshed.steamId)
                AdmissionHelper.seedMobileSessionCookies(refreshed.steamId, refreshed.toSessionData())
                block(refreshed, client)
            }
        }
    }

    suspend fun ensureValid(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext {
        return SessionLocks.withLock(auth.steamId) {
            ensureValidUnlocked(context, auth)
        }
    }

    private suspend fun ensureValidUnlocked(
        context: Context,
        auth: ConfirmationAuthContext
    ): ConfirmationAuthContext {
        val session = readSessionOrAuth(context, auth) ?: return refreshOrReloginUnlocked(context, auth)
        val token = runCatching { SteamTokenHelper.parse(session.accessToken) }.getOrNull()
        val expired = token?.expiresAtMs?.let { System.currentTimeMillis() >= (it - 5 * 60 * 1000L) } ?: false
        return if (!expired) {
            auth.copy(
                sessionId = session.sessionId,
                steamLoginSecure = session.steamLoginSecure,
                refreshToken = session.refreshToken,
                accessToken = session.accessToken
            )
        } else {
            refreshOrReloginUnlocked(
                context,
                auth.copy(
                    sessionId = session.sessionId,
                    steamLoginSecure = session.steamLoginSecure,
                    refreshToken = session.refreshToken,
                    accessToken = session.accessToken
                )
            )
        }
    }

    suspend fun loginAgain(
        context: Context,
        auth: ConfirmationAuthContext,
        password: String
    ): ConfirmationAuthContext {
        return SessionLocks.withLock(auth.steamId) {
            loginAgainUnlocked(context, auth, password)
        }
    }

    private suspend fun loginAgainUnlocked(
        context: Context,
        auth: ConfirmationAuthContext,
        password: String
    ): ConfirmationAuthContext {
        val result = LoginV2Executor(context, auth.steamId).login(auth.accountName, password, auth.sharedSecret)
        val sessionData = SteamSessionData(
            steamId = result.steamId,
            accountName = result.accountName,
            sessionId = result.sessionId,
            steamLoginSecure = result.steamLoginSecure,
            refreshToken = result.refreshToken,
            accessToken = result.accessToken
        )
        MafileRepository(context).writeSessionUnlocked(sessionData)
        AdmissionHelper.seedMobileSessionCookies(result.steamId, sessionData)
        return auth.copy(
            steamId = result.steamId,
            sessionId = result.sessionId,
            steamLoginSecure = result.steamLoginSecure,
            refreshToken = result.refreshToken,
            accessToken = result.accessToken
        )
    }

    private suspend fun refreshOrReloginUnlocked(
        context: Context,
        auth: ConfirmationAuthContext
    ): ConfirmationAuthContext {
        if (auth.refreshToken.isNotBlank()) {
            val refreshed = runCatching { refreshWithJwtUnlocked(context, auth) }.getOrNull()
            if (refreshed != null) return refreshed
        }
        val password = if (auth.accountName.isBlank()) null else PasswordManager.getPassword(context, auth.accountName)
        if (!password.isNullOrBlank()) {
            return loginAgainUnlocked(context, auth, password)
        }
        throw NeedPasswordException(auth.accountName)
    }

    private suspend fun refreshWithJwtUnlocked(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext {
        val client = SteamHttpClient.create(context, auth.steamId)
        val protoClient = SteamProtoClient(client)
        val accessToken = SteamMobileApi.refreshJwt(protoClient, auth.refreshToken, auth.steamId)
        val steamLoginSecure = SteamTokenHelper.combineJwtWithSteamId(auth.steamId, accessToken)
        val sessionData = auth.toSessionData().copy(
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken
        )
        MafileRepository(context).writeSessionUnlocked(sessionData)
        AdmissionHelper.seedMobileSessionCookies(auth.steamId, sessionData)
        return auth.copy(
            steamLoginSecure = steamLoginSecure,
            accessToken = accessToken,
            refreshToken = auth.refreshToken.ifBlank { sessionData.refreshToken }
        )
    }

    private fun isSessionInvalid(e: Throwable): Boolean {
        if (e is SessionInvalidException) return true
        val msg = e.message.orEmpty().lowercase()
        return msg.contains("needauth") || msg.contains("401") || msg.contains("redirect")
    }

    private fun readSessionOrAuth(context: Context, auth: ConfirmationAuthContext): SteamSessionData? {
        MafileRepository(context).readSession(auth.steamId)?.let { return it }
        if (auth.steamLoginSecure.isBlank() || auth.sessionId.isBlank()) return null
        return SteamSessionData(
            steamId = auth.steamId,
            accountName = auth.accountName,
            sessionId = auth.sessionId,
            steamLoginSecure = auth.steamLoginSecure,
            refreshToken = auth.refreshToken,
            accessToken = auth.accessToken.ifBlank { SteamTokenHelper.extractJwt(auth.steamLoginSecure) }
        )
    }

    private fun ConfirmationAuthContext.toSessionData(): SteamSessionData {
        return SteamSessionData(
            steamId = steamId,
            accountName = accountName,
            sessionId = sessionId,
            steamLoginSecure = steamLoginSecure,
            refreshToken = refreshToken,
            accessToken = if (accessToken.isNotBlank()) accessToken else SteamTokenHelper.extractJwt(steamLoginSecure)
        )
    }
}
