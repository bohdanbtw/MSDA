package com.msda.android.steam

/**
 * Last line of defence against leaking secrets through exception messages and
 * logs. Raw Steam responses contain access/refresh JWTs, session cookies and
 * nonces; none of that may ever reach a user-visible error string.
 */
object ErrorSanitizer {
    private const val DEFAULT_MAX_LEN = 160
    private const val REDACTED = "<redacted>"

    // key=value / key: value / "key": "value" pairs (query strings, JSON, logs).
    private val KEY_VALUE = Regex(
        "(?i)\\b(access_token|refresh_token|steamLoginSecure|steam_login_secure|" +
            "sessionid|session_id|nonce|password|passwd|shared_secret|identity_secret|" +
            "signature|code|auth|token|key|secret)\\b([\\s\"'=:]+)([^\\s&,\"'}<]+)"
    )
    private val JWT = Regex("[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{6,}")
    private val LONG_OPAQUE = Regex("[A-Za-z0-9+/=_-]{32,}")
    private val COOKIE = Regex("(?i)\\b(steamLoginSecure|sessionid)=[^;,\\s]+")

    /** Removes credentials from an arbitrary string. Safe to call on JSON or a URL. */
    fun redact(raw: String): String {
        var text = raw
        text = COOKIE.replace(text) { match -> "${match.groupValues[1]}=$REDACTED" }
        text = KEY_VALUE.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED"
        }
        text = JWT.replace(text, REDACTED)
        // Anything still long and opaque after the targeted redactions is
        // probably a token that used an unfamiliar field name.
        text = LONG_OPAQUE.replace(text) { match ->
            if (match.value.equals(REDACTED, ignoreCase = true)) match.value else REDACTED
        }
        return text
    }

    /** Redacts a raw response body and caps its length before it goes into an exception. */
    fun preview(raw: String, maxLen: Int = DEFAULT_MAX_LEN): String {
        val safe = redact(raw)
        return if (safe.length <= maxLen) safe else safe.substring(0, maxLen) + "..."
    }

    /** Convenience overload for raw HTTP response bytes. */
    fun preview(raw: ByteArray, maxLen: Int = DEFAULT_MAX_LEN): String {
        if (raw.isEmpty()) return "body=<empty>"
        return "body=${preview(String(raw, Charsets.UTF_8), maxLen)}"
    }
}
