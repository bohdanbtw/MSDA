package com.msda.android

import android.content.Context
import com.msda.android.steam.EncryptionHelper
import com.msda.android.steam.SessionHandler
import com.msda.android.steam.SteamProtoClient
import com.msda.android.steam.SteamProtoMessages
import java.net.URLDecoder

data class QrApprovalResult(
    val success: Boolean,
    val errorMessage: String? = null
)

object QrApprovalService {
    const val ERROR_INVALID_QR = "invalid_qr"
    const val ERROR_NO_REQUESTS = "no_requests"
    const val ERROR_MULTIPLE_REQUESTS = "multiple_requests"
    const val ERROR_TOKEN_MISSING = "token_missing"

    private const val AUTH_BASE = "https://api.steampowered.com/IAuthenticationService"

    fun looksLikeSteamLoginQr(scannedText: String): Boolean {
        val lower = scannedText.lowercase()
        return lower.contains("steam") || lower.contains("s.team") || lower.contains("steammobile")
    }

    suspend fun approveLoginRequest(
        context: Context,
        auth: ConfirmationAuthContext,
        scannedText: String
    ): QrApprovalResult {
        if (!looksLikeSteamLoginQr(scannedText)) {
            return QrApprovalResult(false, ERROR_INVALID_QR)
        }

        val effectiveAuth = resolveAuthForQr(context, auth)
            ?: return QrApprovalResult(false, ERROR_TOKEN_MISSING)
        val accessToken = extractAccessToken(effectiveAuth)
            ?: return QrApprovalResult(false, ERROR_TOKEN_MISSING)

        return try {
            SessionHandler.handle(context, effectiveAuth) { renewed, client ->
                val proto = SteamProtoClient(client)
                val qrClientId = extractClientIdFromText(scannedText)
                val sessionIds = if (qrClientId != null) {
                    listOf(qrClientId)
                } else {
                    getPendingAuthSessionIds(proto, accessToken)
                }
                if (sessionIds.isEmpty()) return@handle QrApprovalResult(false, ERROR_NO_REQUESTS)
                if (sessionIds.size > 1) return@handle QrApprovalResult(false, ERROR_MULTIPLE_REQUESTS)
                if (renewed.sharedSecret.isBlank()) return@handle QrApprovalResult(false, ERROR_TOKEN_MISSING)

                val clientId = sessionIds.single()
                getAuthSessionInfo(proto, accessToken, clientId)
                approveAuthSession(proto, renewed, accessToken, clientId)
                syncSessionAfterQrApproval(context, renewed, accessToken)
                QrApprovalResult(true)
            }
        } catch (ex: Throwable) {
            QrApprovalResult(false, ex.message ?: "QR approval failed")
        }
    }

    /**
     * Prefer a still-valid JWT; otherwise renew through [SessionManager] so refresh-token
     * rotation shares the same single-flight lock as every other renewal path.
     */
    private suspend fun resolveAuthForQr(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext? {
        if (hasValidAccessToken(auth)) {
            return auth
        }
        return SessionHandler.ensureValid(context, auth)
    }

    private fun hasValidAccessToken(auth: ConfirmationAuthContext): Boolean {
        val jwt = extractAccessToken(auth) ?: return false
        val expiresAtMs = SteamAuthService.parseJwtExpMs(jwt)
        if (expiresAtMs <= 0L) return true
        val skewMs = 5 * 60 * 1000L
        return System.currentTimeMillis() < (expiresAtMs - skewMs)
    }

    private fun extractAccessToken(auth: ConfirmationAuthContext): String? {
        return auth.accessToken.extractJwt() ?: auth.steamLoginSecure.extractJwt()
    }

    private fun syncSessionAfterQrApproval(
        context: Context,
        auth: ConfirmationAuthContext,
        accessToken: String
    ) {
        val steamLoginSecure = if (auth.steamId.isNotBlank()) {
            "${auth.steamId}%7C%7C$accessToken"
        } else {
            auth.steamLoginSecure
        }
        SessionPersistence.saveSession(
            context,
            auth.steamId,
            StoredSteamSession(
                steamLoginSecure = steamLoginSecure,
                sessionId = auth.sessionId,
                refreshToken = auth.refreshToken,
                accessToken = accessToken,
                accountName = auth.accountName,
                sessionExpiresAtMs = SteamAuthService.parseJwtExpMs(accessToken)
            )
        )
    }

    private suspend fun getAuthSessionInfo(proto: SteamProtoClient, accessToken: String, clientId: ULong) {
        val request = SteamProtoMessages.GetAuthSessionInfoRequest(clientId.toLong())
        val payload = SteamProtoMessages.encodeGetAuthSessionInfoRequest(request)
        proto.post("$AUTH_BASE/GetAuthSessionInfo/v1?access_token=${java.net.URLEncoder.encode(accessToken, "UTF-8")}", payload)
    }

    private suspend fun getPendingAuthSessionIds(proto: SteamProtoClient, accessToken: String): List<ULong> {
        val payload = proto.get(
            "$AUTH_BASE/GetAuthSessionsForAccount/v1?access_token=${java.net.URLEncoder.encode(accessToken, "UTF-8")}",
            byteArrayOf()
        )
        return SteamProtoMessages.decodeGetAuthSessionsForAccountResponse(payload)
            .clientIds
            .map { it.toULong() }
    }

    private fun extractClientIdFromText(scannedText: String): ULong? {
        val decoded = try {
            URLDecoder.decode(scannedText, "UTF-8")
        } catch (_: Throwable) {
            scannedText
        }

        val patterns = listOf(
            Regex("[?&](?:client_id|clientid|c|id)=([0-9]{5,})", RegexOption.IGNORE_CASE),
            Regex("(?:client_id|clientid|c|id)[:=]([0-9]{5,})", RegexOption.IGNORE_CASE),
            Regex("/q/\\d+/([0-9]{5,})(?:[/?#]|$)", RegexOption.IGNORE_CASE),
            Regex("/qr/([0-9]{5,})(?:[/?#]|$)", RegexOption.IGNORE_CASE),
            Regex("/(?:login|auth)/([0-9]{5,})(?:[/?#]|$)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val value = pattern.find(decoded)?.groupValues?.getOrNull(1)?.toULongOrNull()
            if (value != null) {
                return value
            }
        }

        return null
    }

    private suspend fun approveAuthSession(
        proto: SteamProtoClient,
        auth: ConfirmationAuthContext,
        accessToken: String,
        clientId: ULong
    ) {
        val signature = EncryptionHelper.computeQrSignature(
            version = 1,
            clientId = clientId.toLong(),
            steamId = auth.steamId.toLongOrNull() ?: 0L,
            sharedSecret = auth.sharedSecret
        )
        val request = SteamProtoMessages.UpdateAuthSessionWithMobileConfirmationRequest(
            version = 1,
            clientId = clientId.toLong(),
            steamId = auth.steamId.toLongOrNull() ?: 0L,
            signature = signature,
            confirm = true,
            persistence = 1
        )
        val payload = SteamProtoMessages.encodeUpdateAuthSessionWithMobileConfirmationRequest(request)
        proto.post(
            "$AUTH_BASE/UpdateAuthSessionWithMobileConfirmation/v1?access_token=${java.net.URLEncoder.encode(accessToken, "UTF-8")}",
            payload
        )
    }

    private fun String.extractJwt(): String? {
        if (isBlank()) return null
        val decoded = if (contains('%')) URLDecoder.decode(this, "UTF-8") else this
        val candidate = decoded.substringAfter("||", decoded).trim()
        if (candidate.isBlank()) return null
        val parts = candidate.split('.')
        return if (parts.size == 3 && parts.none { it.isBlank() }) candidate else null
    }
}
