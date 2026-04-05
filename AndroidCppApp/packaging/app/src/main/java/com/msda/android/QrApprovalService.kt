package com.msda.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
    private const val GET_AUTH_SESSIONS_URL = "$AUTH_BASE/GetAuthSessionsForAccount/v1"
    private const val GET_AUTH_SESSION_INFO_URL = "$AUTH_BASE/GetAuthSessionInfo/v1"
    private const val UPDATE_AUTH_SESSION_URL = "$AUTH_BASE/UpdateAuthSessionWithMobileConfirmation/v1"
    private const val GENERATE_ACCESS_TOKEN_URL = "$AUTH_BASE/GenerateAccessTokenForApp/v1"

    private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val MOBILE_ORIGIN = "https://steamcommunity.com"
    private const val MOBILE_REFERER = "https://steamcommunity.com/mobileconf/"

    fun looksLikeSteamLoginQr(scannedText: String): Boolean {
        val lower = scannedText.lowercase()
        return lower.contains("steam") || lower.contains("s.team") || lower.contains("steammobile")
    }

    fun approveLoginRequest(context: android.content.Context, auth: ConfirmationAuthContext, scannedText: String): QrApprovalResult {
        if (!looksLikeSteamLoginQr(scannedText)) {
            return QrApprovalResult(false, ERROR_INVALID_QR)
        }

        val accessToken = resolveMobileAccessToken(auth)
            ?: return QrApprovalResult(false, ERROR_TOKEN_MISSING)

        return try {
            val qrClientId = resolveClientIdFromQr(scannedText, accessToken)
            val sessionIds = if (qrClientId != null) {
                listOf(qrClientId)
            } else {
                getPendingAuthSessionIds(accessToken)
            }

            if (sessionIds.isEmpty()) {
                return QrApprovalResult(false, ERROR_NO_REQUESTS)
            }
            if (sessionIds.size > 1) {
                return QrApprovalResult(false, ERROR_MULTIPLE_REQUESTS)
            }
            if (auth.sharedSecret.isBlank()) {
                return QrApprovalResult(false, ERROR_TOKEN_MISSING)
            }

            val clientId = sessionIds.single()
            getAuthSessionInfo(accessToken, clientId)
            approveAuthSession(auth, accessToken, clientId)
            persistResolvedToken(context, auth, accessToken)
            QrApprovalResult(true)
        } catch (ex: Throwable) {
            QrApprovalResult(false, ex.message ?: "QR approval failed")
        }
    }

    private fun resolveClientIdFromQr(scannedText: String, accessToken: String): ULong? {
        val directFromText = extractClientIdFromText(scannedText)
        if (directFromText != null && canReadAuthSession(accessToken, directFromText)) {
            return directFromText
        }

        val attempts = listOf(
            listOf("qrcode" to scannedText),
            listOf("qr_code" to scannedText),
            listOf("url" to scannedText)
        )

        for (body in attempts) {
            val clientId = try {
                val json = postJson(
                    url = "$GET_AUTH_SESSION_INFO_URL?access_token=${url(accessToken)}",
                    body = body,
                    accessToken = accessToken
                )
                parseClientIdFromSessionInfo(json)
            } catch (_: Throwable) {
                null
            }

            if (clientId != null && canReadAuthSession(accessToken, clientId)) {
                return clientId
            }
        }

        return null
    }

    private fun parseClientIdFromSessionInfo(root: JSONObject): ULong? {
        val response = root.optJSONObject("response") ?: root

        val fromString = response.optString("client_id", "").toULongOrNull()
        if (fromString != null) {
            return fromString
        }

        val fromLong = response.optLong("client_id", 0L)
        if (fromLong > 0L) {
            return fromLong.toULong()
        }

        return null
    }

    private fun canReadAuthSession(accessToken: String, clientId: ULong): Boolean {
        return try {
            getAuthSessionInfo(accessToken, clientId)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getAuthSessionInfo(accessToken: String, clientId: ULong): JSONObject {
        return postJson(
            url = "$GET_AUTH_SESSION_INFO_URL?access_token=${url(accessToken)}",
            body = listOf("client_id" to clientId.toString()),
            accessToken = accessToken
        )
    }

    private fun getPendingAuthSessionIds(accessToken: String): List<ULong> {
        val json = getJson(
            url = "$GET_AUTH_SESSIONS_URL?access_token=${url(accessToken)}",
            accessToken = accessToken
        )
        val response = json.optJSONObject("response") ?: json
        val ids = mutableSetOf<ULong>()

        val clientArrays = listOf(
            response.optJSONArray("client_ids"),
            response.optJSONArray("pending_client_ids"),
            response.optJSONArray("pending_confirmations")
        )

        for (array in clientArrays) {
            if (array == null) continue
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                when (item) {
                    is Number -> item.toLong().takeIf { it > 0L }?.toULong()?.let { ids.add(it) }
                    is String -> item.toULongOrNull()?.let { ids.add(it) }
                    is JSONObject -> {
                        val fromObj = item.optString("client_id", "").toULongOrNull()
                            ?: item.optLong("client_id", 0L).takeIf { it > 0L }?.toULong()
                        if (fromObj != null) ids.add(fromObj)
                    }
                }
            }
        }

        return ids.toList()
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

    private fun resolveMobileAccessToken(auth: ConfirmationAuthContext): String? {
        val direct = auth.accessToken.extractJwt().orEmpty()
        if (direct.isNotBlank()) {
            return direct
        }

        val cookieJwt = auth.steamLoginSecure.extractJwt().orEmpty()
        if (cookieJwt.isNotBlank()) {
            return cookieJwt
        }

        if (auth.refreshToken.isBlank()) {
            return null
        }

        val response = postJson(
            url = GENERATE_ACCESS_TOKEN_URL,
            body = listOf(
                "refresh_token" to auth.refreshToken,
                "steamid" to auth.steamId,
                "renewal_type" to "1"
            )
        )

        val responseJson = response.optJSONObject("response") ?: response
        return responseJson.optString("access_token", "").extractJwt()
    }

    private fun approveAuthSession(auth: ConfirmationAuthContext, accessToken: String, clientId: ULong) {
        val signature = computeConfirmationSignature(clientId, auth.steamId, auth.sharedSecret)
        val json = postJson(
            url = "$UPDATE_AUTH_SESSION_URL?access_token=${url(accessToken)}",
            body = listOf(
                "version" to "1",
                "client_id" to clientId.toString(),
                "steamid" to auth.steamId,
                "signature" to Base64.getEncoder().encodeToString(signature),
                "confirm" to "true",
                "persistence" to "1"
            ),
            accessToken = accessToken
        )

        if (json.has("success") && !json.optBoolean("success", true)) {
            val message = json.optString("message", "Steam rejected QR approval")
            if (message.contains("key=", ignoreCase = true) || message.contains("unauthorized", ignoreCase = true)) {
                throw IllegalStateException(ERROR_TOKEN_MISSING)
            }
            throw IllegalStateException(message)
        }
    }

    private fun computeConfirmationSignature(clientId: ULong, steamId: String, sharedSecret: String): ByteArray {
        val secret = Base64.getDecoder().decode(sharedSecret)
        val steamIdValue = steamId.toULongOrNull() ?: throw IllegalStateException("Invalid steamid")
        val data = ByteArray(18)
        val versionBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1).array()
        val clientBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(clientId.toLong()).array()
        val steamBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(steamIdValue.toLong()).array()
        System.arraycopy(versionBytes, 0, data, 0, 2)
        System.arraycopy(clientBytes, 0, data, 2, 8)
        System.arraycopy(steamBytes, 0, data, 10, 8)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun persistResolvedToken(context: android.content.Context, auth: ConfirmationAuthContext, accessToken: String) {
        val current = SessionStore.loadSession(context, auth.steamId)
        val session = StoredSteamSession(
            steamLoginSecure = current?.steamLoginSecure ?: auth.steamLoginSecure,
            sessionId = current?.sessionId ?: auth.sessionId,
            refreshToken = current?.refreshToken ?: auth.refreshToken,
            accessToken = accessToken
        )
        SessionStore.saveSession(context, auth.steamId, session)
    }

    private fun getJson(url: String, accessToken: String? = null): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        applyMobileHeaders(connection, accessToken)
        return readJson(connection)
    }

    private fun postJson(url: String, body: List<Pair<String, String>>, accessToken: String? = null): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.doOutput = true
        applyMobileHeaders(connection, accessToken)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.outputStream.use { it.write(toQuery(body).toByteArray(Charsets.UTF_8)) }
        return readJson(connection)
    }

    private fun applyMobileHeaders(connection: HttpURLConnection, accessToken: String?) {
        connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, text/javascript, text/html, application/xml, text/xml, */*")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Origin", MOBILE_ORIGIN)
        connection.setRequestProperty("Referer", MOBILE_REFERER)
        connection.setRequestProperty("X-Requested-With", "com.valvesoftware.android.steam.community")
        connection.setRequestProperty("Cookie", "mobileClient=android; mobileClientVersion=777777 3.6.1; Steam_Language=english")
        if (!accessToken.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
        }
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val raw = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            if (status == 401 && (err.contains("key=", ignoreCase = true) || err.contains("unauthorized", ignoreCase = true))) {
                throw IllegalStateException(ERROR_TOKEN_MISSING)
            }
            throw IllegalStateException("HTTP $status from Steam: $err")
        }
        return JSONObject(raw)
    }

    private fun toQuery(pairs: List<Pair<String, String>>): String {
        return pairs.joinToString("&") { (k, v) -> "${url(k)}=${url(v)}" }
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun String.extractJwt(): String? {
        if (isBlank()) return null
        val decoded = if (contains('%')) URLDecoder.decode(this, "UTF-8") else this
        val candidate = decoded.substringAfter("||", decoded).trim()
        if (candidate.isBlank()) return null
        val parts = candidate.split('.')
        return if (parts.size == 3 && parts.none { it.isBlank() }) candidate else null
    }
}
