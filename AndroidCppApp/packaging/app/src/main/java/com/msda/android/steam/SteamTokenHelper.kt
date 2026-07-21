package com.msda.android.steam

import java.net.URLDecoder
import java.util.Base64

enum class SteamTokenType {
    Unknown,
    Web,
    AccessToken,
    Mobile,
    Refresh,
    MobileRefresh
}

data class ParsedSteamToken(
    val rawJwt: String,
    val steamId: String,
    val expiresAtMs: Long,
    val tokenType: SteamTokenType
)

object SteamTokenHelper {
    fun extractJwt(tokenOrCookie: String): String {
        val decoded = if (tokenOrCookie.contains('%')) URLDecoder.decode(tokenOrCookie, "UTF-8") else tokenOrCookie
        return decoded.substringAfter("||", decoded).trim()
    }

    fun combineJwtWithSteamId(steamId: String, jwt: String): String {
        return if (jwt.contains("%7C%7C") || jwt.contains("||")) jwt else "${steamId}%7C%7C$jwt"
    }

    fun parse(tokenOrCookie: String): ParsedSteamToken {
        val jwt = extractJwt(tokenOrCookie)
        val parts = jwt.split('.')
        require(parts.size >= 2) { "Invalid JWT token" }
        val payload = decodeBase64Url(parts[1])
        val steamId = extractStringClaim(payload, "sub")
        val exp = extractLongClaim(payload, "exp") * 1000L
        val audiences = extractAudienceClaims(payload)
        val type = resolveType(audiences)
        return ParsedSteamToken(jwt, steamId, exp, type)
    }

    private fun resolveType(audiences: List<String>): SteamTokenType {
        if (audiences.isEmpty()) return SteamTokenType.Unknown
        val hasWeb = audiences.contains("web")
        val hasMobile = audiences.contains("mobile")
        val hasRenew = audiences.contains("renew")
        return when {
            hasWeb && hasMobile && hasRenew -> SteamTokenType.MobileRefresh
            hasWeb && hasMobile -> SteamTokenType.Mobile
            hasWeb && hasRenew -> SteamTokenType.Refresh
            hasWeb && audiences.size == 1 -> SteamTokenType.Web
            else -> SteamTokenType.AccessToken
        }
    }

    private fun decodeBase64Url(value: String): String {
        val padding = (4 - value.length % 4) % 4
        val padded = value + "=".repeat(padding)
        val bytes = Base64.getDecoder().decode(padded.replace('-', '+').replace('_', '/'))
        return String(bytes, Charsets.UTF_8)
    }

    private fun extractStringClaim(payload: String, key: String): String {
        val match = Regex(""""$key"\s*:\s*"([^"]*)"""").find(payload)
        return match?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun extractLongClaim(payload: String, key: String): Long {
        val match = Regex(""""$key"\s*:\s*([0-9]+)""").find(payload)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
    }

    private fun extractAudienceClaims(payload: String): List<String> {
        val arrayMatch = Regex(""""aud"\s*:\s*\[([^\]]*)]""").find(payload)
        if (arrayMatch != null) {
            return Regex(""""([^"]+)"""")
                .findAll(arrayMatch.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
        }
        val singleMatch = Regex(""""aud"\s*:\s*"([^"]+)"""").find(payload)
        return singleMatch?.groupValues?.getOrNull(1)?.let { listOf(it) } ?: emptyList()
    }
}
