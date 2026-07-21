package com.msda.android.steam

object SteamMobileApi {
    private const val GENERATE_ACCESS_TOKEN =
        "https://api.steampowered.com/IAuthenticationService/GenerateAccessTokenForApp/v1"

    suspend fun refreshJwt(protoClient: SteamProtoClient, refreshToken: String, steamId: String): String {
        val request = SteamProtoMessages.GenerateAccessTokenForAppRequest(
            refreshToken = refreshToken,
            steamId = steamId.toLongOrNull() ?: 0L,
            tokenRenewalType = true
        )
        val payload = SteamProtoMessages.encodeGenerateAccessTokenForAppRequest(request)
        val response = protoClient.post(GENERATE_ACCESS_TOKEN, payload)
        val decoded = SteamProtoMessages.decodeGenerateAccessTokenForAppResponse(response)
        if (decoded.accessToken.isBlank()) {
            throw SessionInvalidException("GenerateAccessTokenForApp returned empty access token")
        }
        return decoded.accessToken
    }
}
