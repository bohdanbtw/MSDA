package com.msda.android.steam



import android.content.Context

import kotlinx.coroutines.delay

import okhttp3.FormBody

import org.json.JSONObject

import java.util.regex.Pattern



class LoginV2Executor(private val context: Context, private val steamId: String) {

    private val client = SteamHttpClient.create(context, steamId)

    private val protoClient = SteamProtoClient(client)



    suspend fun login(accountName: String, password: String, sharedSecret: String = ""): LoginV2Result {
        val normalizedAccount = accountName.trim()

        val sessionId = fetchLoginPageSessionId()

        val rsa = requestRsa(normalizedAccount)

        val encryptedPassword =

            EncryptionHelper.toBase64EncryptedPassword(rsa.publicKeyExp, rsa.publicKeyMod, password)



        val begin = beginAuth(normalizedAccount, encryptedPassword, rsa.timestamp, rsa)

        val resolvedSteamId = begin.steamId.takeIf { it > 0L }?.toString().orEmpty()

            .ifBlank { steamId }



        var poll: SteamProtoMessages.PollAuthSessionStatusResponse? = null
        for (attempt in 0 until 3) {
            if (shouldSubmitGuard(begin.allowedConfirmations)) {
                val guardType = selectGuardType(begin.allowedConfirmations)
                    ?: throw IllegalStateException("Steam requested an unsupported login confirmation type")
                submitGuard(begin.clientId, resolvedSteamId, sharedSecret, guardType)
            }

            val attemptPoll = pollOnce(begin.clientId, begin.requestId)
            poll = attemptPoll
            if (attemptPoll.refreshToken.isNotBlank() && attemptPoll.accessToken.isNotBlank()) {
                break
            }

            val waitMs = (begin.interval.coerceAtLeast(0.5f) * 1000f).toLong()
            delay(waitMs)
        }



        val tokens = poll

            ?: throw IllegalStateException("LoginV2 polling timed out")

        if (tokens.refreshToken.isBlank() || tokens.accessToken.isBlank()) {

            throw IllegalStateException("LoginV2 polling timed out")

        }



        val finalized = finalizeLogin(sessionId, resolvedSteamId, tokens.refreshToken, tokens.accessToken)

        return LoginV2Result(
            steamId = resolvedSteamId,
            accountName = normalizedAccount,
            sessionId = finalized.sessionId,
            steamLoginSecure = SteamTokenHelper.combineJwtWithSteamId(resolvedSteamId, tokens.accessToken),
            refreshToken = tokens.refreshToken,
            accessToken = tokens.accessToken,
            expiresAtMs = runCatching { SteamTokenHelper.parse(tokens.accessToken).expiresAtMs }.getOrDefault(0L)
        )

    }



    private fun shouldSubmitGuard(allowed: List<SteamProtoMessages.AllowedConfirmation>): Boolean {

        if (allowed.isEmpty()) return false

        return allowed.all { it.confirmationType != GUARD_NONE }

    }



    private fun selectGuardType(allowed: List<SteamProtoMessages.AllowedConfirmation>): Int? {

        val types = allowed.map { it.confirmationType }

        if (types.contains(GUARD_DEVICE_CODE)) return GUARD_DEVICE_CODE

        if (types.contains(GUARD_DEVICE_CONFIRMATION)) return GUARD_DEVICE_CONFIRMATION

        return types.firstOrNull { it != GUARD_NONE && it != GUARD_UNKNOWN }

    }



    private suspend fun submitGuard(

        clientId: Long,

        steamId: String,

        sharedSecret: String,

        guardType: Int

    ) {

        when (guardType) {

            GUARD_DEVICE_CODE -> {

                val code = SteamGuardCodeGenerator.forLogin(context, steamId, sharedSecret)

                require(code.isNotBlank()) { "Steam Guard code is unavailable" }

                updateWithGuardCode(clientId, steamId, code, GUARD_DEVICE_CODE)

            }

            GUARD_DEVICE_CONFIRMATION -> {

                updateWithMobileConfirmation(clientId, steamId, sharedSecret)

            }

            else -> throw IllegalStateException("Unsupported guard type: $guardType")

        }

    }



    private suspend fun requestRsa(accountName: String): SteamProtoMessages.GetPasswordRsaPublicKeyResponse {

        val payload = SteamProtoMessages.encodeGetPasswordRsaPublicKeyRequest(

            SteamProtoMessages.GetPasswordRsaPublicKeyRequest(accountName)

        )

        val response = protoClient.get(

            "https://api.steampowered.com/IAuthenticationService/GetPasswordRSAPublicKey/v1",

            payload

        )

        val decoded = SteamProtoMessages.decodeGetPasswordRsaPublicKeyResponse(response)
        if (decoded.publicKeyMod.isBlank() || decoded.publicKeyExp.isBlank() || decoded.timestamp <= 0L) {
            throw IllegalStateException(
                "GetPasswordRSAPublicKey returned invalid key (mod/exp empty or timestamp=0). " +
                    "eResult=${protoClient.lastEResult ?: "unknown"}"
            )
        }
        return decoded

    }



    private suspend fun beginAuth(

        accountName: String,

        encryptedPassword: String,

        rsaTimestamp: Long,

        rsa: SteamProtoMessages.GetPasswordRsaPublicKeyResponse

    ): SteamProtoMessages.BeginAuthSessionViaCredentialsResponse {

        val request = SteamProtoMessages.BeginAuthSessionViaCredentialsRequest(

            deviceFriendlyName = "",

            accountName = accountName,

            encryptedPassword = encryptedPassword,

            encryptionTimestamp = rsaTimestamp,

            rememberLogin = true,

            platformType = 3,

            persistence = 1,

            websiteId = "Mobile",

            deviceDetails = DeviceDetails(

                deviceFriendlyName = "Pixel 6 Pro",

                platformType = 3,

                osType = -500,

                gamingDeviceType = 528

            )

        )

        val payload = SteamProtoMessages.encodeBeginAuthSessionViaCredentialsRequest(request)

        val response = protoClient.post(

            "https://api.steampowered.com/IAuthenticationService/BeginAuthSessionViaCredentials/v1",

            payload

        )

        val decoded = SteamProtoMessages.decodeBeginAuthSessionViaCredentialsResponse(response)

        if (decoded.clientId <= 0L || decoded.requestId.isEmpty()) {
            val rsaHint = if (
                rsa.publicKeyMod.isBlank() || rsa.publicKeyExp.isBlank() || rsa.timestamp <= 0L
            ) {
                " Hint: RSA public key was empty or invalid."
            } else {
                ""
            }
            val eResultSuffix = protoClient.lastEResult?.let { " (eResult=$it)" }.orEmpty()
            val message = decoded.extendedErrorMessage.ifBlank { "BeginAuthSession failed" }
            throw IllegalStateException("$message$eResultSuffix.$rsaHint")

        }

        return decoded

    }



    private suspend fun updateWithGuardCode(clientId: Long, steamId: String, code: String, codeType: Int) {

        val request = SteamProtoMessages.UpdateAuthSessionWithSteamGuardCodeRequest(

            clientId = clientId,

            steamId = steamId.toLongOrNull() ?: 0L,

            code = code,

            codeType = codeType

        )

        val payload = SteamProtoMessages.encodeUpdateAuthSessionWithSteamGuardCodeRequest(request)

        protoClient.post(

            "https://api.steampowered.com/IAuthenticationService/UpdateAuthSessionWithSteamGuardCode/v1",

            payload

        )

    }



    private suspend fun updateWithMobileConfirmation(clientId: Long, steamId: String, sharedSecret: String) {

        require(sharedSecret.isNotBlank()) { "Shared secret is required for mobile confirmation login" }

        val signature = EncryptionHelper.computeQrSignature(

            version = 1,

            clientId = clientId,

            steamId = steamId.toLongOrNull() ?: 0L,

            sharedSecret = sharedSecret

        )

        val request = SteamProtoMessages.UpdateAuthSessionWithMobileConfirmationRequest(

            version = 1,

            clientId = clientId,

            steamId = steamId.toLongOrNull() ?: 0L,

            signature = signature,

            confirm = true,

            persistence = 1

        )

        val payload = SteamProtoMessages.encodeUpdateAuthSessionWithMobileConfirmationRequest(request)

        protoClient.post(

            "https://api.steampowered.com/IAuthenticationService/UpdateAuthSessionWithMobileConfirmation/v1",

            payload

        )

    }



    private suspend fun pollOnce(

        clientId: Long,

        requestId: ByteArray

    ): SteamProtoMessages.PollAuthSessionStatusResponse {

        val request = SteamProtoMessages.PollAuthSessionStatusRequest(clientId, requestId)

        val payload = SteamProtoMessages.encodePollAuthSessionStatusRequest(request)

        val response = protoClient.post(

            "https://api.steampowered.com/IAuthenticationService/PollAuthSessionStatus/v1",

            payload

        )

        return SteamProtoMessages.decodePollAuthSessionStatusResponse(response)

    }



    private suspend fun finalizeLogin(

        sessionId: String,

        steamId: String,

        refreshToken: String,

        accessToken: String

    ): SteamSessionData {

        val finalizeBody = FormBody.Builder()

            .add("nonce", refreshToken)

            .add("sessionid", sessionId)

            .build()

        val finalizeRequest = SteamHttpClient.newRequest("https://login.steampowered.com/jwt/finalizelogin")

            .post(finalizeBody)

            .build()

        val finalizeResponse = client.newCall(finalizeRequest).execute()

        val finalizeJson = JSONObject(finalizeResponse.body?.string().orEmpty())

        val transferInfo = finalizeJson.optJSONArray("transfer_info")

            ?: finalizeJson.optJSONObject("response")?.optJSONArray("transfer_info")



        var loginSecureCookie: String? = extractCookieValue(finalizeResponse.headers("Set-Cookie"), "steamLoginSecure")

        var finalSessionId: String? = extractCookieValue(finalizeResponse.headers("Set-Cookie"), "sessionid")



        if (transferInfo != null) {

            for (i in 0 until transferInfo.length()) {

                val transfer = transferInfo.optJSONObject(i) ?: continue

                val url = transfer.optString("url")

                if (url.isBlank()) continue

                val params = transfer.optJSONObject("params") ?: transfer.optJSONObject("transfer_info_params")

                val body = FormBody.Builder()

                    .add("nonce", params?.optString("nonce").orEmpty())

                    .add("auth", params?.optString("auth").orEmpty())

                    .add("steamID", steamId)

                    .add("steamid", steamId)

                    .build()

                val response = client.newCall(SteamHttpClient.newRequest(url).post(body).build()).execute()

                if (loginSecureCookie.isNullOrBlank()) {

                    loginSecureCookie = extractCookieValue(response.headers("Set-Cookie"), "steamLoginSecure")

                }

                if (finalSessionId.isNullOrBlank()) {

                    finalSessionId = extractCookieValue(response.headers("Set-Cookie"), "sessionid")

                }

            }

        }



        val resolvedSessionId = finalSessionId ?: sessionId
        if (resolvedSessionId.isBlank()) {
            throw IllegalStateException("Finalize login failed to produce session cookies")
        }
        // Confirmations need the mobile JWT from poll, not the web cookie from finalizelogin.
        val mobileLoginSecure = SteamTokenHelper.combineJwtWithSteamId(steamId, accessToken)
        return SteamSessionData(
            steamId = steamId,
            accountName = accountNameFromSteamId(steamId),
            sessionId = resolvedSessionId,
            steamLoginSecure = mobileLoginSecure,
            refreshToken = refreshToken,
            accessToken = accessToken
        )

    }



    private suspend fun fetchLoginPageSessionId(): String {

        val request = SteamHttpClient.newRequest("https://steamcommunity.com/login/home/")

            .get()

            .build()

        val response = client.newCall(request).execute()

        val html = response.body?.string().orEmpty()

        val fromCookie = extractCookieValue(response.headers("Set-Cookie"), "sessionid")

        if (!fromCookie.isNullOrBlank()) return fromCookie



        val regex = Pattern.compile("g_sessionID\\s*=\\s*\"([a-zA-Z0-9]+)\"")

        val matcher = regex.matcher(html)

        if (matcher.find()) {

            return matcher.group(1).orEmpty()

        }

        throw IllegalStateException("Failed to resolve sessionid from Steam login page")

    }



    private fun extractCookieValue(setCookies: List<String>, key: String): String? {

        val prefix = "$key="

        for (header in setCookies) {

            val token = header.substringBefore(';').trim()

            if (token.startsWith(prefix, ignoreCase = true)) {

                return token.substring(prefix.length)

            }

        }

        return null

    }



    private fun accountNameFromSteamId(steamId: String): String {
        return MafileSecretsReader.read(context, steamId)?.accountName.orEmpty()
    }



    companion object {

        private const val GUARD_UNKNOWN = 0

        private const val GUARD_NONE = 1

        private const val GUARD_DEVICE_CODE = 3

        private const val GUARD_DEVICE_CONFIRMATION = 4

    }

}


