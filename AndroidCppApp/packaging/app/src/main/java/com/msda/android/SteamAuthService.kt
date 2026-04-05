package com.msda.android

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigInteger
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.HttpCookie
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

data class SteamAuthResult(
    val success: Boolean,
    val steamId: String? = null,
    val steamLoginSecure: String? = null,
    val sessionId: String? = null,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val errorMessage: String? = null
)

object SteamAuthService {
    private const val STEAM_BASE = "https://steamcommunity.com"
    private const val STEAM_LOGIN_PAGE_URL = "$STEAM_BASE/login/home/"
    private const val STEAM_API_BASE = "https://api.steampowered.com/IAuthenticationService"
    private const val STEAM_RSA_URL = "$STEAM_API_BASE/GetPasswordRSAPublicKey/v1"
    private const val STEAM_BEGIN_AUTH_URL = "$STEAM_API_BASE/BeginAuthSessionViaCredentials/v1"
    private const val STEAM_UPDATE_GUARD_URL = "$STEAM_API_BASE/UpdateAuthSessionWithSteamGuardCode/v1"
    private const val STEAM_POLL_AUTH_URL = "$STEAM_API_BASE/PollAuthSessionStatus/v1"
    private const val STEAM_FINALIZE_URL = "https://login.steampowered.com/jwt/finalizelogin"

    fun showPasswordDialog(
        context: Context,
        accountName: String,
        onResult: (SteamAuthResult) -> Unit,
        onProgress: ((String) -> Unit)? = null
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 16, 48, 16)
        }

        val passwordInput = EditText(context).apply {
            hint = "Steam Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(passwordInput)

        AlertDialog.Builder(context)
            .setTitle("Steam Login")
            .setMessage("Enter your Steam password to continue")
            .setView(container)
            .setPositiveButton("Login") { dialog, _ ->
                val password = passwordInput.text.toString()
                dialog.dismiss()
                if (password.isBlank()) {
                    onResult(SteamAuthResult(false, errorMessage = "Password cannot be empty"))
                } else {
                    performLogin(accountName, password, onResult, onProgress)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onResult(SteamAuthResult(false, errorMessage = "Login cancelled"))
            }
            .show()
    }

    private fun performLogin(
        accountName: String,
        password: String,
        onResult: (SteamAuthResult) -> Unit,
        onProgress: ((String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                emitProgress(onProgress, "Authentificating… preparing account")
                val authPayload = NativeBridge.getActiveConfirmationAuthPayload()
                val context = ConfirmationService.parseAuthPayload(authPayload)
                    ?: return@launch withContext(Dispatchers.Main) {
                        onResult(SteamAuthResult(false, errorMessage = "Failed to parse account data"))
                    }

                val twoFactorCode = NativeBridge.getActiveCode().trim()
                if (twoFactorCode.isBlank()) {
                    return@launch withContext(Dispatchers.Main) {
                        onResult(SteamAuthResult(false, errorMessage = "Guard code is unavailable"))
                    }
                }

                doLoginRequest(
                    accountName = accountName,
                    password = password,
                    twoFactorCode = twoFactorCode,
                    steamId = context.steamId,
                    existingSteamLoginSecure = context.steamLoginSecure,
                    existingSessionId = context.sessionId,
                    onProgress = onProgress
                )
            } catch (e: Exception) {
                SteamAuthResult(false, errorMessage = e.message ?: "Login failed")
            }

            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    private suspend fun emitProgress(onProgress: ((String) -> Unit)?, message: String) {
        if (onProgress == null) return
        withContext(Dispatchers.Main) {
            onProgress(message)
        }
    }

    private suspend fun doLoginRequest(
        accountName: String,
        password: String,
        twoFactorCode: String,
        steamId: String,
        existingSteamLoginSecure: String,
        existingSessionId: String,
        onProgress: ((String) -> Unit)?
    ): SteamAuthResult {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        val previousHandler = CookieHandler.getDefault()
        CookieHandler.setDefault(cookieManager)

        return try {
            emitProgress(onProgress, "Authentificating… requesting RSA key")
            val initialSessionId = ensureInitialSession(cookieManager)

            val rsa = requestRsaKey(accountName)
            val encryptedPassword = encryptPassword(password, rsa.modulusHex, rsa.exponentHex)

            emitProgress(onProgress, "Authentificating… sending credentials")
            val beginBody = formBody(
                "account_name" to accountName,
                "encrypted_password" to encryptedPassword,
                "encryption_timestamp" to rsa.timestamp,
                "remember_login" to "true",
                "persistence" to "1",
                "website_id" to "Community",
                "platform_type" to "3",
                "device_friendly_name" to "MSDA"
            )

            val beginResponseRoot = postJson(STEAM_BEGIN_AUTH_URL, beginBody)
            val beginResponse = beginResponseRoot.optJSONObject("response") ?: JSONObject()

            val clientId = beginResponse.optString("client_id", "").ifBlank {
                beginResponse.optLong("client_id", 0L).takeIf { it > 0L }?.toString().orEmpty()
            }
            val requestId = beginResponse.optString("request_id", "")
            val resolvedSteamId = beginResponse.optString("steamid", "").ifBlank { steamId }

            if (clientId.isBlank() || requestId.isBlank() || resolvedSteamId.isBlank()) {
                val extendedError = beginResponse.optString("extended_error_message", "")
                return SteamAuthResult(
                    false,
                    errorMessage = "BeginAuthSession failed: missing client/request identifiers. $extendedError"
                )
            }

            emitProgress(onProgress, "Authentificating… confirming Guard code")
            val guardBody = formBody(
                "client_id" to clientId,
                "steamid" to resolvedSteamId,
                "code" to twoFactorCode,
                "code_type" to "3"
            )
            postText(STEAM_UPDATE_GUARD_URL, guardBody, "$STEAM_BASE/")

            var refreshToken = ""
            var accessToken = ""
            var pollInterval = beginResponse.optInt("interval", 1).coerceIn(1, 5)
            val pollStartedAt = System.currentTimeMillis()
            val maxWaitMs = 35_000L
            var attempt = 0

            while ((System.currentTimeMillis() - pollStartedAt) < maxWaitMs) {
                attempt += 1
                val elapsedSec = ((System.currentTimeMillis() - pollStartedAt) / 1000L).toInt()
                emitProgress(onProgress, "Authentificating… waiting Steam approval (${elapsedSec}s)")

                val pollBody = formBody(
                    "client_id" to clientId,
                    "request_id" to requestId
                )
                val pollRoot = postJson(STEAM_POLL_AUTH_URL, pollBody)
                val pollResponse = pollRoot.optJSONObject("response") ?: JSONObject()

                val nextRefresh = pollResponse.optString("refresh_token", "")
                if (nextRefresh.isNotBlank()) {
                    refreshToken = nextRefresh
                    accessToken = pollResponse.optString("access_token", "")
                    break
                }

                val serverInterval = pollResponse.optInt("interval", pollInterval).coerceIn(1, 5)
                pollInterval = serverInterval

                // Adaptive wait: first checks are faster, then align to server interval
                val delayMs = when {
                    attempt <= 2 -> 700L
                    attempt <= 4 -> 1200L
                    else -> (pollInterval * 1000L).toLong()
                }

                val remainingMs = maxWaitMs - (System.currentTimeMillis() - pollStartedAt)
                if (remainingMs <= 0L) break
                Thread.sleep(minOf(delayMs, remainingMs))
            }

            if (refreshToken.isBlank()) {
                return SteamAuthResult(
                    false,
                    errorMessage = "LoginV2 polling timed out waiting for refresh token"
                )
            }

            emitProgress(onProgress, "Authentificating… finalizing session")
            val finalizeBody = formBody(
                "nonce" to refreshToken,
                "sessionid" to initialSessionId
            )
            val finalizeRoot = postJson(STEAM_FINALIZE_URL, finalizeBody)

            var headerSteamLoginSecure: String? = null
            var headerSessionId: String? = null

            val transferInfo = finalizeRoot.optJSONArray("transfer_info")
                ?: finalizeRoot.optJSONObject("response")?.optJSONArray("transfer_info")

            if (transferInfo != null) {
                for (i in 0 until transferInfo.length()) {
                    val transferEntry = transferInfo.optJSONObject(i) ?: continue
                    val transferUrl = transferEntry.optString("url", "")
                    if (transferUrl.isBlank()) continue

                    val params = transferEntry.optJSONObject("params")
                        ?: transferEntry.optJSONObject("transfer_info_params")
                    val nonce = params?.optString("nonce", "").orEmpty()
                    val auth = params?.optString("auth", "").orEmpty()

                    val transferBody = formBody(
                        "steamID" to resolvedSteamId,
                        "steamid" to resolvedSteamId,
                        "nonce" to nonce,
                        "auth" to auth
                    )

                    val transferResult = postText(transferUrl, transferBody, "$STEAM_BASE/")
                    if (headerSteamLoginSecure.isNullOrBlank()) {
                        headerSteamLoginSecure = extractCookieFromSetCookieHeaders(transferResult.setCookies, "steamLoginSecure")
                    }
                    if (headerSessionId.isNullOrBlank()) {
                        headerSessionId = extractCookieFromSetCookieHeaders(transferResult.setCookies, "sessionid")
                    }
                }
            }

            val cookies = cookieManager.cookieStore.cookies
            var steamLoginSecure = findCookieValue(cookies, "steamLoginSecure")
            var sessionId = findCookieValue(cookies, "sessionid")

            if (steamLoginSecure.isNullOrBlank()) {
                steamLoginSecure = headerSteamLoginSecure
            }
            if (sessionId.isNullOrBlank()) {
                sessionId = headerSessionId
            }

            if (steamLoginSecure.isNullOrBlank() && accessToken.isNotBlank()) {
                steamLoginSecure = "$resolvedSteamId%7C%7C$accessToken"
            }

            if (sessionId.isNullOrBlank()) {
                sessionId = initialSessionId.ifBlank { existingSessionId }
            }
            if (steamLoginSecure.isNullOrBlank()) {
                steamLoginSecure = existingSteamLoginSecure
            }

            if (steamLoginSecure.isNullOrBlank()) {
                return SteamAuthResult(
                    false,
                    errorMessage = "LoginV2 succeeded but no session token was produced"
                )
            }

            SteamAuthResult(
                success = true,
                steamId = resolvedSteamId,
                steamLoginSecure = steamLoginSecure,
                sessionId = sessionId,
                refreshToken = refreshToken,
                accessToken = accessToken
            )
        } catch (e: Exception) {
            SteamAuthResult(false, errorMessage = e.message ?: "Network error")
        } finally {
            CookieHandler.setDefault(previousHandler)
        }
    }

    private fun ensureInitialSession(cookieManager: CookieManager): String {
        val connection = URL(STEAM_LOGIN_PAGE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connection.inputStream.use { it.readBytes() }
        connection.disconnect()

        val sessionId = findCookieValue(cookieManager.cookieStore.cookies, "sessionid")
        return sessionId ?: createSessionId()
    }

    private fun requestRsaKey(accountName: String): RsaKeyResponse {
        val queryUrl = "$STEAM_RSA_URL?account_name=${url(accountName)}"
        val jsonRoot = getJson(queryUrl)
        val json = jsonRoot.optJSONObject("response") ?: jsonRoot

        val modulusHex = json.optString("publickey_mod", "")
        val exponentHex = json.optString("publickey_exp", "")
        val timestamp = json.optString("timestamp", "")

        if (modulusHex.isBlank() || exponentHex.isBlank() || timestamp.isBlank()) {
            val message = json.optString("message", "Failed to request RSA key")
            throw IllegalStateException(message)
        }

        return RsaKeyResponse(
            modulusHex = modulusHex,
            exponentHex = exponentHex,
            timestamp = timestamp
        )
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )

        val status = connection.responseCode
        val body = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("HTTP $status $error")
        }
        connection.disconnect()
        return JSONObject(body)
    }

    private fun encryptPassword(password: String, modulusHex: String, exponentHex: String): String {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun postJson(url: String, body: String): JSONObject {
        val result = postText(url, body)
        return JSONObject(result.body)
    }

    private fun postText(url: String, body: String, referer: String = "$STEAM_BASE/login"): HttpResult {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty("Origin", STEAM_BASE)
        connection.setRequestProperty("X-Requested-With", "com.valvesoftware.android.steam.community")

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val bodyText = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("HTTP $status $error")
        }

        val setCookies = mutableListOf<String>()
        for ((key, values) in connection.headerFields) {
            if (key != null && key.equals("Set-Cookie", ignoreCase = true) && values != null) {
                setCookies.addAll(values)
            }
        }

        connection.disconnect()
        return HttpResult(bodyText, setCookies)
    }

    private fun extractCookieFromSetCookieHeaders(setCookies: List<String>, name: String): String? {
        val prefix = "$name="
        for (cookieHeader in setCookies) {
            val parts = cookieHeader.split(';')
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.startsWith(prefix, ignoreCase = true)) {
                    return trimmed.substring(prefix.length)
                }
            }
        }
        return null
    }

    private fun formBody(vararg pairs: Pair<String, String>): String {
        return pairs.joinToString("&") { (key, value) ->
            "${url(key)}=${url(value)}"
        }
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun findCookieValue(cookies: List<HttpCookie>, name: String): String? {
        return cookies.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
    }

    private fun createSessionId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    private fun parseOauth(loginResponse: JSONObject): JSONObject? {
        if (!loginResponse.isNull("oauth")) {
            val raw = loginResponse.opt("oauth")
            when (raw) {
                is JSONObject -> return raw
                is String -> {
                    if (raw.isBlank()) return null
                    return try {
                        JSONObject(raw)
                    } catch (_: Throwable) {
                        null
                    }
                }
            }
        }
        return null
    }

    private data class HttpResult(
        val body: String,
        val setCookies: List<String>
    )

    private data class RsaKeyResponse(
        val modulusHex: String,
        val exponentHex: String,
        val timestamp: String
    )
}
