package com.msda.android

import android.content.Context
import com.msda.android.steam.EncryptionHelper
import com.msda.android.steam.SessionHandler
import com.msda.android.steam.SessionInvalidException
import com.msda.android.steam.TimeAligner
import org.json.JSONObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.net.ssl.SSLException
import java.net.SocketTimeoutException

data class ConfirmationAuthContext(
    val steamId: String,
    val identitySecret: String,
    val deviceId: String,
    val sessionId: String,
    val steamLoginSecure: String,
    val accountName: String,
    val sharedSecret: String = "",
    val refreshToken: String = "",
    val accessToken: String = ""
) {
    fun withSession(session: StoredSteamSession): ConfirmationAuthContext {
        return copy(
            steamLoginSecure = session.steamLoginSecure,
            sessionId = session.sessionId,
            refreshToken = session.refreshToken.ifBlank { refreshToken },
            accessToken = session.accessToken.ifBlank { accessToken },
            accountName = session.accountName.ifBlank { accountName }
        )
    }
}

data class TradePartnerSummary(
    val nickname: String,
    val avatarUrl: String?,
    val steamLevel: String
)

data class ConfirmationItem(
    val id: String,
    val nonce: String,
    val type: Int,
    val typeName: String,
    val headline: String,
    val summary: List<String>,
    val iconUrl: String?,
    val creatorId: String,
    val multi: Boolean
)

data class ConfirmationBundle(
    val key: String,
    val title: String,
    val typeName: String,
    val items: List<ConfirmationItem>,
    val partner: TradePartnerSummary?
)

class NeedPasswordException(val accountName: String) : Exception("Password required for $accountName")

object ConfirmationService {
    fun parseAuthPayload(payload: String): ConfirmationAuthContext? {
        if (payload.isBlank()) return null

        val parts = payload.split('|')
        if (parts.size < 6) return null

        return ConfirmationAuthContext(
            steamId = parts[0],
            identitySecret = parts[1],
            deviceId = parts[2],
            sessionId = parts[3],
            steamLoginSecure = parts[4],
            accountName = parts[5],
            sharedSecret = parts.getOrElse(6) { "" },
            refreshToken = parts.getOrElse(7) { "" },
            accessToken = parts.getOrElse(8) { "" }
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadBundles(_auth: ConfirmationAuthContext): List<ConfirmationBundle> {
        throw IllegalStateException("Context required to load confirmations")
    }

    fun loadBundles(context: Context?, auth: ConfirmationAuthContext): List<ConfirmationBundle> {
        if (context == null) return loadBundles(auth)
        return kotlinx.coroutines.runBlocking {
            SessionHandler.handle(context, auth) { renewed, client ->
                loadBundlesAligned(renewed, client)
            }
        }
    }

    /**
     * Load confirmation bundles with automatic session renewal.
     *
     * Proactively renews the session before the request if the stored access token is
     * known to be expired (avoids a guaranteed-failed call), then reactively renews on
     * `needauth`. Renewal goes through [SessionManager] (refresh_token → cached password).
     *
     * [onSessionRenewed] is invoked with the fresh context after any silent renewal so
     * callers can update their in-memory auth. [renewalDepth] caps recursion at one retry.
     */
    suspend fun loadBundlesWithAutoRenew(
        context: Context?,
        auth: ConfirmationAuthContext,
        onSessionRenewed: ((ConfirmationAuthContext) -> Unit)? = null,
        _renewalDepth: Int = 0
    ): List<ConfirmationBundle> {
        @Suppress("UNUSED_VARIABLE")
        val ignoredDepth = _renewalDepth
        if (context == null) return loadBundles(auth)
        val renewed = SessionHandler.ensureValid(context, auth)
        if (renewed != auth) onSessionRenewed?.invoke(renewed)
        return SessionHandler.handle(context, renewed) { latest, client ->
            if (latest != renewed) onSessionRenewed?.invoke(latest)
            loadBundlesAligned(latest, client)
        }
    }

    /**
     * getlist with Steam-aligned HMAC. On clock-skew-ish failures, invalidate cache,
     * force QueryTime once, and retry — still no timer polling.
     */
    private fun loadBundlesAligned(auth: ConfirmationAuthContext, client: OkHttpClient): List<ConfirmationBundle> {
        try {
            val json = getJson(
                "https://steamcommunity.com/mobileconf/getlist?${withConfirmationQuery(auth, "conf", client)}",
                client
            )
            if (json.optBoolean("needauth", false)) throw SessionInvalidException("needauth")
            return parseBundlesOrThrow(json)
        } catch (e: SessionInvalidException) {
            throw e
        } catch (e: Throwable) {
            if (!looksLikeClockSkew(e)) throw e
            TimeAligner.invalidateCache()
            val json = getJson(
                "https://steamcommunity.com/mobileconf/getlist?${
                    withConfirmationQuery(auth, "conf", client, forceRefresh = true)
                }",
                client
            )
            if (json.optBoolean("needauth", false)) throw SessionInvalidException("needauth")
            return parseBundlesOrThrow(json)
        }
    }

    /**
     * Accept/decline a bundle with automatic session renewal on failure.
     * Returns the (possibly renewed) auth context alongside the success flag so the caller
     * can keep using fresh cookies.
     */
    suspend fun respondBundleWithRenew(
        context: Context,
        auth: ConfirmationAuthContext,
        bundle: ConfirmationBundle,
        accept: Boolean,
        onSessionRenewed: ((ConfirmationAuthContext) -> Unit)? = null
    ): Boolean {
        return try {
            val ok = respondBundle(context, auth, bundle, accept)
            if (ok) return true
            // Failure may be a stale session — renew once and retry
            val renewed = SessionManager.renew(context, auth) ?: return false
            onSessionRenewed?.invoke(renewed)
            respondBundle(context, renewed, bundle, accept)
        } catch (_: Throwable) {
            val renewed = SessionManager.renew(context, auth) ?: return false
            onSessionRenewed?.invoke(renewed)
            try { respondBundle(context, renewed, bundle, accept) } catch (_: Throwable) { false }
        }
    }

    suspend fun respondItemWithRenew(
        context: Context,
        auth: ConfirmationAuthContext,
        item: ConfirmationItem,
        accept: Boolean,
        onSessionRenewed: ((ConfirmationAuthContext) -> Unit)? = null
    ): Boolean {
        return try {
            val ok = respondItem(context, auth, item, accept)
            if (ok) return true
            val renewed = SessionManager.renew(context, auth) ?: return false
            onSessionRenewed?.invoke(renewed)
            respondItem(context, renewed, item, accept)
        } catch (_: Throwable) {
            val renewed = SessionManager.renew(context, auth) ?: return false
            onSessionRenewed?.invoke(renewed)
            try { respondItem(context, renewed, item, accept) } catch (_: Throwable) { false }
        }
    }

    fun respondBundle(auth: ConfirmationAuthContext, bundle: ConfirmationBundle, accept: Boolean): Boolean {
        return respondBundle(null, auth, bundle, accept)
    }

    fun respondBundle(context: Context?, auth: ConfirmationAuthContext, bundle: ConfirmationBundle, accept: Boolean): Boolean {
        if (context == null) return false
        return kotlinx.coroutines.runBlocking {
            SessionHandler.handle(context, auth) { renewed, client ->
                val op = if (accept) "allow" else "cancel"
                val time = TimeAligner(client).alignedEpochSecondsBlocking()
                val key = confirmationKey(renewed.identitySecret, time, op)
                val pairs = mutableListOf(
                    "p" to renewed.deviceId,
                    "a" to renewed.steamId,
                    "k" to key,
                    "t" to time.toString(),
                    "m" to "react",
                    "tag" to op,
                    "sessionid" to renewed.sessionId,
                    "op" to op
                )
                if (bundle.items.size > 1) {
                    bundle.items.forEach { item ->
                        pairs += "cid[]" to item.id
                        pairs += "ck[]" to item.nonce
                    }
                    val json = postJson("https://steamcommunity.com/mobileconf/multiajaxop", pairs, client)
                    json.optBoolean("success", false)
                } else {
                    val item = bundle.items.firstOrNull() ?: return@handle false
                    pairs += "cid" to item.id
                    pairs += "ck" to item.nonce
                    val json = getJson("https://steamcommunity.com/mobileconf/ajaxop?${toQuery(pairs)}", client)
                    json.optBoolean("success", false)
                }
            }
        }
    }

    fun respondItem(auth: ConfirmationAuthContext, item: ConfirmationItem, accept: Boolean): Boolean {
        return respondItem(null, auth, item, accept)
    }

    fun respondItem(context: Context?, auth: ConfirmationAuthContext, item: ConfirmationItem, accept: Boolean): Boolean {
        if (context == null) return false
        return kotlinx.coroutines.runBlocking {
            SessionHandler.handle(context, auth) { renewed, client ->
                val op = if (accept) "allow" else "cancel"
                val time = TimeAligner(client).alignedEpochSecondsBlocking()
                val key = confirmationKey(renewed.identitySecret, time, op)
                val query = StringBuilder()
                    .append("p=").append(url(renewed.deviceId))
                    .append("&a=").append(url(renewed.steamId))
                    .append("&k=").append(url(key))
                    .append("&t=").append(time)
                    .append("&m=react")
                    .append("&tag=").append(op)
                    .append("&sessionid=").append(url(renewed.sessionId))
                    .append("&cid=").append(url(item.id))
                    .append("&ck=").append(url(item.nonce))
                    .append("&op=").append(op)
                val json = getJson("https://steamcommunity.com/mobileconf/ajaxop?$query", client)
                json.optBoolean("success", false)
            }
        }
    }

    private fun withConfirmationQuery(
        auth: ConfirmationAuthContext,
        tag: String,
        client: OkHttpClient,
        forceRefresh: Boolean = false
    ): String {
        val time = TimeAligner(client).alignedEpochSecondsBlocking(forceRefresh)
        val key = confirmationKey(auth.identitySecret, time, tag)

        return "p=${url(auth.deviceId)}&a=${url(auth.steamId)}&k=${url(key)}&t=$time&m=react&tag=$tag"
    }

    private fun looksLikeClockSkew(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("invalid") ||
            message.contains("expired") ||
            message.contains("time") ||
            message.contains("clock") ||
            message.contains("confirmation load failed")
    }

    private fun confirmationKey(identitySecret: String, time: Long, tag: String): String {
        return EncryptionHelper.generateConfirmationHash(time, identitySecret, tag)
    }

    private fun getJson(url: String, client: OkHttpClient): JSONObject {
        return executeWithNetworkRetry {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $body")
            JSONObject(body)
        }
    }

    private fun postJson(url: String, pairs: List<Pair<String, String>>, client: OkHttpClient): JSONObject {
        return executeWithNetworkRetry {
            val bodyBuilder = FormBody.Builder()
            pairs.forEach { bodyBuilder.add(it.first, it.second) }
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .post(bodyBuilder.build())
                    .build()
            ).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: $body")
            JSONObject(body)
        }
    }

    private inline fun executeWithNetworkRetry(block: () -> JSONObject): JSONObject {
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastError = e
                if (attempt == 0 && isTransientNetworkError(e)) {
                    Thread.sleep(1_000L)
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("Confirmation request failed")
    }

    private fun isTransientNetworkError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SSLException || current is SocketTimeoutException) return true
            val message = current.message.orEmpty().lowercase()
            if (message.contains("ssl") || message.contains("handshake") || message.contains("timed out")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun toQuery(pairs: List<Pair<String, String>>): String {
        return pairs.joinToString("&") { (k, v) -> "${url(k)}=${url(v)}" }
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun parseBundlesOrThrow(json: JSONObject): List<ConfirmationBundle> {
        if (!json.optBoolean("success", false)) {
            val message = json.optString("message", "unknown")
            throw IllegalStateException("Confirmation load failed: $message")
        }
        val conf = json.optJSONArray("conf") ?: return emptyList()
        val items = mutableListOf<ConfirmationItem>()
        for (i in 0 until conf.length()) {
            val item = conf.optJSONObject(i) ?: continue
            val summary = mutableListOf<String>()
            val summaryArray = item.optJSONArray("summary")
            if (summaryArray != null) {
                for (s in 0 until summaryArray.length()) {
                    summary += summaryArray.optString(s, "")
                }
            }
            items += ConfirmationItem(
                id = item.optString("id", ""),
                nonce = item.optString("nonce", ""),
                type = item.optInt("type", 0),
                typeName = item.optString("type_name", "Unknown"),
                headline = item.optString("headline", ""),
                summary = summary,
                iconUrl = if (item.isNull("icon")) null else item.optString("icon", ""),
                creatorId = item.optString("creator_id", ""),
                multi = item.optBoolean("multi", false)
            )
        }
        return items.groupBy { item ->
            when {
                item.type == 2 -> "trade:${item.headline}"
                item.typeName.contains("Market", ignoreCase = true) -> "market:${item.typeName}"
                else -> "${item.typeName}:${item.creatorId}"
            }
        }.map { (key, groupItems) ->
            val first = groupItems.first()
            ConfirmationBundle(
                key = key,
                title = first.typeName,
                typeName = first.typeName,
                items = groupItems,
                partner = if (first.type == 2) {
                    TradePartnerSummary(
                        nickname = first.headline.ifBlank { "Unknown trader" },
                        avatarUrl = first.iconUrl,
                        steamLevel = "Steam Level: --"
                    )
                } else null
            )
        }
    }
}
