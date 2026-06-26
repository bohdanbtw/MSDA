package com.msda.android.steam

import org.json.JSONObject

/** Pure mafile session parsing — Nebula v3 SessionData + SDA legacy top-level fields. */
object MafileSessionReader {
    fun parse(
        steamId: String,
        mafileJson: JSONObject?,
        cachedAccountName: String? = null,
        cachedSessionId: String? = null,
        cachedSteamLoginSecure: String? = null,
        cachedRefreshToken: String? = null,
        cachedAccessToken: String? = null
    ): SteamSessionData? {
        if (steamId.isBlank()) return null

        val sessionData = mafileJson?.optJSONObject("SessionData")
        val accountName = firstNonBlank(
            mafileJson?.optString("account_name"),
            mafileJson?.optString("AccountName"),
            cachedAccountName
        )
        val sessionId = firstNonBlank(
            cachedSessionId,
            sessionData?.optString("SessionID"),
            sessionData?.optString("sessionid"),
            mafileJson?.optString("sessionid")
        )
        val steamLoginSecure = firstNonBlank(
            cachedSteamLoginSecure,
            sessionData?.optString("steamLoginSecure"),
            mafileJson?.optString("steamLoginSecure")
        )
        val refresh = firstNonBlank(
            cachedRefreshToken,
            sessionData?.optString("RefreshToken"),
            sessionData?.optString("refresh_token"),
            sessionData?.optString("OAuthToken"),
            mafileJson?.optString("refresh_token"),
            mafileJson?.optString("OAuthToken")
        )
        val access = firstNonBlank(
            cachedAccessToken,
            sessionData?.optString("AccessToken"),
            sessionData?.optString("access_token"),
            mafileJson?.optString("access_token")
        )

        if (steamLoginSecure.isBlank() || sessionId.isBlank()) return null
        val resolvedAccess = if (access.isBlank()) SteamTokenHelper.extractJwt(steamLoginSecure) else access
        return SteamSessionData(
            steamId = steamId,
            accountName = accountName,
            sessionId = sessionId,
            steamLoginSecure = steamLoginSecure,
            refreshToken = refresh,
            accessToken = resolvedAccess
        )
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }
}
