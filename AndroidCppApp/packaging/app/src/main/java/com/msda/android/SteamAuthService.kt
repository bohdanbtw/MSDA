package com.msda.android

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.EditText
import android.widget.LinearLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

private const val TAG = "SteamAuthService"

data class SteamAuthResult(
    val success: Boolean,
    val steamId: String? = null,
    val steamLoginSecure: String? = null,
    val sessionId: String? = null,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val sessionExpiresAtMs: Long = 0L,
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
    private const val STEAM_GENERATE_ACCESS_TOKEN_URL = "$STEAM_API_BASE/GenerateAccessTokenForApp/v1"

    private const val MOBILE_USER_AGENT = "okhttp/3.12.12"

    private const val MIN_REQUEST_INTERVAL_MS = 1200L
    private const val DEFAULT_RETRY_AFTER_MS = 10_000L
    private const val MAX_RETRY_AFTER_MS = 60_000L
    private val rateLimitLock = Any()
    private var nextAllowedRequestAtMs = 0L

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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
                    performLogin(accountName, password, context, onResult, onProgress)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onResult(SteamAuthResult(false, errorMessage = "Login cancelled"))
            }
            .show()
    }

    /**
     * Refresh session using the Steam mobile token API (GenerateAccessTokenForApp).
     * Builds steamLoginSecure = "steamId||accessToken" directly — no web cookies needed.
     * This is what the official Steam mobile app uses for session renewal.
     */
    suspend fun refreshSessionUsingToken(
        refreshToken: String,
        steamId: String = ""
    ): SteamAuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = formBody(
                    "refresh_token" to refreshToken,
                    "steamid" to steamId,
                    "renewal_type" to "1"
                )
                val root = postJson(STEAM_GENERATE_ACCESS_TOKEN_URL, body)
                val response = root.optJSONObject("response") ?: root

                val newAccessToken = response.optString("access_token", "").trim()
                val newRefreshToken = response.optString("refresh_token", "").trim()
                    .ifBlank { refreshToken }

                if (newAccessToken.isBlank()) {
                    return@withContext SteamAuthResult(
                        false,
                        errorMessage = "GenerateAccessTokenForApp returned no access_token"
                    )
                }

                val resolvedSteamId = steamId.ifBlank {
                    extractSteamIdFromJwt(newAccessToken) ?: ""
                }

                // Build the mobile steamLoginSecure cookie value
                val steamLoginSecure = if (resolvedSteamId.isNotBlank()) {
                    "${resolvedSteamId}%7C%7C${newAccessToken}"
                } else {
                    newAccessToken
                }

                val expiresAtMs = parseJwtExpMs(newAccessToken)

                SteamAuthResult(
                    success = true,
                    steamId = resolvedSteamId.ifBlank { null },
                    steamLoginSecure = steamLoginSecure,
                    sessionId = createSessionId(),
                    refreshToken = newRefreshToken,
                    accessToken = newAccessToken,
                    sessionExpiresAtMs = expiresAtMs
                )
            } catch (e: Exception) {
                SteamAuthResult(false, errorMessage = e.message ?: "Token refresh error")
            }
        }
    }

    suspend fun refreshSessionUsingPassword(
        accountName: String,
        password: String
    ): SteamAuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val authPayload = NativeBridge.getActiveConfirmationAuthPayload()
                val ctx = ConfirmationService.parseAuthPayload(authPayload)
                    ?: return@withContext SteamAuthResult(false, errorMessage = "Failed to parse account data")

                val twoFactorCode = NativeBridge.getActiveCode().trim()
                if (twoFactorCode.isBlank()) {
                    return@withContext SteamAuthResult(false, errorMessage = "Guard code is unavailable")
                }

                doLoginRequest(
                    accountName = accountName,
                    password = password,
                    twoFactorCode = twoFactorCode,
                    steamId = ctx.steamId,
                    existingSteamLoginSecure = ctx.steamLoginSecure,
                    existingSessionId = ctx.sessionId,
                    onProgress = null
                )
            } catch (e: Exception) {
                SteamAuthResult(false, errorMessage = e.message ?: "Password login failed")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal login flow
    // -------------------------------------------------------------------------

    private fun performLogin(
        accountName: String,
        password: String,
        context: Context,
        onResult: (SteamAuthResult) -> Unit,
        onProgress: ((String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                emitProgress(onProgress, "Authenticating… preparing account")
                val authPayload = NativeBridge.getActiveConfirmationAuthPayload()
                val authCtx = ConfirmationService.parseAuthPayload(authPayload)
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
                    steamId = authCtx.steamId,
                    existingSteamLoginSecure = authCtx.steamLoginSecure,
                    existingSessionId = authCtx.sessionId,
                    onProgress = onProgress
                )
            } catch (e: Exception) {
                SteamAuthResult(false, errorMessage = e.message ?: "Login failed")
            }

            withContext(Dispatchers.Main) {
                if (result.success) {
                    if (result.steamId != null && result.steamLoginSecure != null && result.sessionId != null) {
                        SessionPersistence.saveSession(
                            context,
                            result.steamId,
                            StoredSteamSession(
                                steamLoginSecure = result.steamLoginSecure,
                                sessionId = result.sessionId,
                                refreshToken = result.refreshToken ?: "",
                                accessToken = result.accessToken ?: "",
                                accountName = accountName,
                                sessionExpiresAtMs = result.sessionExpiresAtMs
                            )
                        )
                    }
                    // Auto-save password for silent session renewal
                    try {
                        PasswordManager.savePassword(context, accountName, password)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to auto-save password for $accountName", e)
                    }
                }
                onResult(result)
            }
        }
    }

    private suspend fun emitProgress(onProgress: ((String) -> Unit)?, message: String) {
        if (onProgress == null) return
        withContext(Dispatchers.Main) { onProgress(message) }
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
            emitProgress(onProgress, "Authenticating… requesting RSA key")
            val initialSessionId = ensureInitialSession(cookieManager)

            val rsa = requestRsaKey(accountName)
            val encryptedPassword = encryptPassword(password, rsa.modulusHex, rsa.exponentHex)

            emitProgress(onProgress, "Authenticating… sending credentials")
            val beginBody = formBody(
                "account_name" to accountName,
                "encrypted_password" to encryptedPassword,
                "encryption_timestamp" to rsa.timestamp,
                "remember_login" to "true",
                "persistence" to "1",
                "website_id" to "Mobile",
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
                    errorMessage = "BeginAuthSession failed: missing identifiers. $extendedError"
                )
            }

            emitProgress(onProgress, "Authenticating… confirming Guard code")
            postText(STEAM_UPDATE_GUARD_URL, formBody(
                "client_id" to clientId,
                "steamid" to resolvedSteamId,
                "code" to twoFactorCode,
                "code_type" to "3"
            ), "$STEAM_BASE/")

            var refreshToken = ""
            var accessToken = ""
            var pollInterval = beginResponse.optInt("interval", 1).coerceIn(1, 5)
            val pollStartedAt = System.currentTimeMillis()
            val maxWaitMs = 35_000L
            var attempt = 0

            while ((System.currentTimeMillis() - pollStartedAt) < maxWaitMs) {
                attempt++
                val elapsedSec = ((System.currentTimeMillis() - pollStartedAt) / 1000L).toInt()
                emitProgress(onProgress, "Authenticating… waiting Steam approval (${elapsedSec}s)")

                val pollRoot = postJson(STEAM_POLL_AUTH_URL, formBody(
                    "client_id" to clientId,
                    "request_id" to requestId
                ))
                val pollResponse = pollRoot.optJSONObject("response") ?: JSONObject()

                val nextRefresh = pollResponse.optString("refresh_token", "")
                if (nextRefresh.isNotBlank()) {
                    refreshToken = nextRefresh
                    accessToken = pollResponse.optString("access_token", "")
                    break
                }

                pollInterval = pollResponse.optInt("interval", pollInterval).coerceIn(1, 5)
                val delayMs = when {
                    attempt <= 2 -> 700L
                    attempt <= 4 -> 1200L
                    else -> pollInterval * 1000L
                }
                val remainingMs = maxWaitMs - (System.currentTimeMillis() - pollStartedAt)
                if (remainingMs <= 0L) break
                delay(minOf(delayMs, remainingMs))
            }

            if (refreshToken.isBlank()) {
                return SteamAuthResult(
                    false,
                    errorMessage = "LoginV2 polling timed out waiting for refresh token"
                )
            }

            emitProgress(onProgress, "Authenticating… finalizing session")
            finalizeLoginSession(
                refreshToken = refreshToken,
                resolvedSteamId = resolvedSteamId,
                accessToken = accessToken,
                initialSessionId = initialSessionId,
                existingSessionId = existingSessionId,
                existingSteamLoginSecure = existingSteamLoginSecure,
                cookieManager = cookieManager
            )
        } catch (e: Exception) {
            SteamAuthResult(false, errorMessage = e.message ?: "Network error")
        } finally {
            CookieHandler.setDefault(previousHandler)
        }
    }

    /**
     * Runs finalizelogin + domain transfer cookies for the initial full login.
     * For renewal, use [refreshSessionUsingToken] (GenerateAccessTokenForApp) instead.
     * Fails closed: returns error if Steam does not produce fresh session cookies.
     */
    private fun finalizeLoginSession(
        refreshToken: String,
        resolvedSteamId: String,
        accessToken: String,
        initialSessionId: String,
        existingSessionId: String,
        existingSteamLoginSecure: String,
        cookieManager: CookieManager
    ): SteamAuthResult {
        val sessionId = ensureInitialSession(cookieManager)

        val finalizeRoot = postJson(STEAM_FINALIZE_URL, formBody(
            "nonce" to refreshToken,
            "sessionid" to sessionId
        ))

        var headerSteamLoginSecure: String? = null
        var headerSessionId: String? = null

        val transferInfo = finalizeRoot.optJSONArray("transfer_info")
            ?: finalizeRoot.optJSONObject("response")?.optJSONArray("transfer_info")

        if (transferInfo != null) {
            for (i in 0 until transferInfo.length()) {
                val entry = transferInfo.optJSONObject(i) ?: continue
                val transferUrl = entry.optString("url", "").takeIf { it.isNotBlank() } ?: continue

                val params = entry.optJSONObject("params") ?: entry.optJSONObject("transfer_info_params")
                val nonce = params?.optString("nonce", "").orEmpty()
                val auth  = params?.optString("auth",  "").orEmpty()

                val result = postText(transferUrl, formBody(
                    "steamID" to resolvedSteamId,
                    "steamid" to resolvedSteamId,
                    "nonce" to nonce,
                    "auth" to auth
                ), "$STEAM_BASE/")

                if (headerSteamLoginSecure.isNullOrBlank()) {
                    headerSteamLoginSecure = extractCookieFromSetCookieHeaders(result.setCookies, "steamLoginSecure")
                }
                if (headerSessionId.isNullOrBlank()) {
                    headerSessionId = extractCookieFromSetCookieHeaders(result.setCookies, "sessionid")
                }
            }
        }

        val cookies = cookieManager.cookieStore.cookies
        var steamLoginSecure = findCookieValue(cookies, "steamLoginSecure")
            ?: headerSteamLoginSecure

        var finalSessionId = findCookieValue(cookies, "sessionid")
            ?: headerSessionId

        // JWT-based fallback: use accessToken to build steamLoginSecure (works for mobile)
        if (steamLoginSecure.isNullOrBlank() && accessToken.isNotBlank()) {
            steamLoginSecure = "${resolvedSteamId}%7C%7C${accessToken}"
        }

        if (finalSessionId.isNullOrBlank()) {
            finalSessionId = sessionId.ifBlank { initialSessionId.ifBlank { existingSessionId } }
        }

        // FAIL CLOSED: never return expired cookies as a "successful" renewal
        if (steamLoginSecure.isNullOrBlank()) {
            return SteamAuthResult(
                false,
                errorMessage = "Login finalize failed: no session cookies produced"
            )
        }

        val expiresAtMs = parseJwtExpMs(accessToken)

        return SteamAuthResult(
            success = true,
            steamId = resolvedSteamId,
            steamLoginSecure = steamLoginSecure,
            sessionId = finalSessionId,
            refreshToken = refreshToken,
            accessToken = accessToken,
            sessionExpiresAtMs = expiresAtMs
        )
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Decode JWT exp claim and return as epoch-milliseconds, or 0 if unavailable.
     */
    fun parseJwtExpMs(token: String): Long {
        if (token.isBlank()) return 0L
        val candidate = if (token.contains("||")) token.substringAfter("||") else token
        val parts = candidate.split(".")
        if (parts.size < 2) return 0L
        return try {
            val padding = (4 - parts[1].length % 4) % 4
            val padded = parts[1] + "=".repeat(padding)
            val payload = Base64.getDecoder().decode(
                padded.replace('-', '+').replace('_', '/')
            )
            JSONObject(String(payload, Charsets.UTF_8)).optLong("exp", 0L) * 1000L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractSteamIdFromJwt(token: String): String? {
        if (token.isBlank()) return null
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val padding = (4 - parts[1].length % 4) % 4
            val padded = parts[1] + "=".repeat(padding)
            val payload = Base64.getDecoder().decode(
                padded.replace('-', '+').replace('_', '/')
            )
            val json = JSONObject(String(payload, Charsets.UTF_8))
            json.optString("sub", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureInitialSession(cookieManager: CookieManager): String {
        val connection = URL(STEAM_LOGIN_PAGE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
        connection.inputStream.use { it.readBytes() }
        connection.disconnect()
        return findCookieValue(cookieManager.cookieStore.cookies, "sessionid") ?: createSessionId()
    }

    private fun requestRsaKey(accountName: String): RsaKeyResponse {
        val jsonRoot = getJson("$STEAM_RSA_URL?account_name=${url(accountName)}")
        val json = jsonRoot.optJSONObject("response") ?: jsonRoot

        val modulusHex = json.optString("publickey_mod", "")
        val exponentHex = json.optString("publickey_exp", "")
        val timestamp = json.optString("timestamp", "")

        if (modulusHex.isBlank() || exponentHex.isBlank() || timestamp.isBlank()) {
            throw IllegalStateException(json.optString("message", "Failed to request RSA key"))
        }
        return RsaKeyResponse(modulusHex, exponentHex, timestamp)
    }

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)

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
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    private fun postJson(url: String, body: String): JSONObject {
        return JSONObject(postText(url, body).body)
    }

    private fun postText(url: String, body: String, referer: String = "$STEAM_BASE/login"): HttpResult {
        waitForRateLimitWindow()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("User-Agent", MOBILE_USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, text/plain, */*")
        connection.setRequestProperty("Referer", referer)
        connection.setRequestProperty("Origin", STEAM_BASE)
        connection.setRequestProperty("X-Requested-With", "com.valvesoftware.android.steam.community")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val bodyText = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            if (status == 429) applyRateLimitBackoff(connection)
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

    private fun waitForRateLimitWindow() {
        val waitMs: Long = synchronized(rateLimitLock) {
            val now = System.currentTimeMillis()
            val delay = (nextAllowedRequestAtMs - now).coerceAtLeast(0L)
            nextAllowedRequestAtMs = now + delay + MIN_REQUEST_INTERVAL_MS
            delay
        }
        if (waitMs > 0) {
            try { Thread.sleep(waitMs) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    private fun applyRateLimitBackoff(connection: HttpURLConnection) {
        val retryAfterMs = connection.getHeaderField("Retry-After")
            ?.trim()?.toLongOrNull()?.times(1000L)
            ?.coerceIn(1000L, MAX_RETRY_AFTER_MS)
            ?: DEFAULT_RETRY_AFTER_MS
        synchronized(rateLimitLock) {
            val target = System.currentTimeMillis() + retryAfterMs
            if (target > nextAllowedRequestAtMs) nextAllowedRequestAtMs = target
        }
    }

    private fun extractCookieFromSetCookieHeaders(setCookies: List<String>, name: String): String? {
        val prefix = "$name="
        for (header in setCookies) {
            for (part in header.split(';')) {
                val trimmed = part.trim()
                if (trimmed.startsWith(prefix, ignoreCase = true)) {
                    return trimmed.substring(prefix.length)
                }
            }
        }
        return null
    }

    private fun formBody(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${url(k)}=${url(v)}" }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun findCookieValue(cookies: List<HttpCookie>, name: String): String? =
        cookies.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

    private fun createSessionId(): String = UUID.randomUUID().toString().replace("-", "")

    private data class HttpResult(val body: String, val setCookies: List<String>)
    private data class RsaKeyResponse(val modulusHex: String, val exponentHex: String, val timestamp: String)
}
