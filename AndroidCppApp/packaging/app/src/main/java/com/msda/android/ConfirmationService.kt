package com.msda.android

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
            accessToken = session.accessToken.ifBlank { accessToken }
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

    fun loadBundles(auth: ConfirmationAuthContext): List<ConfirmationBundle> {
        require(auth.identitySecret.isNotBlank()) { "identity_secret is missing in mafile" }
        require(auth.deviceId.isNotBlank()) { "device_id is missing in mafile" }
        require(auth.sessionId.isNotBlank()) { "sessionid is missing in mafile" }
        require(auth.steamLoginSecure.isNotBlank()) { "steamLoginSecure is missing in mafile" }

        val query = withConfirmationQuery(auth, "conf")
        val url = "https://steamcommunity.com/mobileconf/getlist?$query"
        val json = getJson(url, auth)

        if (json.optBoolean("needauth", false)) {
            throw IllegalStateException("Steam reported needauth=true. Session cookies are invalid or expired.")
        }

        if (!json.optBoolean("success", false)) {
            val message = json.optString("message", "unknown")
            val detail = json.optString("detail", "")
            throw IllegalStateException("Confirmation load failed: $message $detail")
        }

        val conf = json.optJSONArray("conf") ?: return emptyList()
        val items = mutableListOf<ConfirmationItem>()

        for (i in 0 until conf.length()) {
            val item = conf.optJSONObject(i) ?: continue
            val summaryArray = item.optJSONArray("summary")
            val summary = mutableListOf<String>()
            if (summaryArray != null) {
                for (s in 0 until summaryArray.length()) {
                    summary.add(summaryArray.optString(s, ""))
                }
            }

            val iconUrl = if (item.isNull("icon")) null else item.optString("icon", "")

            items.add(
                ConfirmationItem(
                    id = item.optString("id", ""),
                    nonce = item.optString("nonce", ""),
                    type = item.optInt("type", 0),
                    typeName = item.optString("type_name", "Unknown"),
                    headline = item.optString("headline", ""),
                    summary = summary,
                    iconUrl = iconUrl,
                    creatorId = item.optString("creator_id", ""),
                    multi = item.optBoolean("multi", false)
                )
            )
        }

        val grouped = items.groupBy { item ->
            when {
                item.type == 2 -> "trade:${item.headline}"
                item.typeName.contains("Market", ignoreCase = true) -> "market:${item.typeName}"
                else -> "${item.typeName}:${item.creatorId}"
            }
        }

        return grouped.map { (key, groupItems) ->
            val first = groupItems.first()
            val partner = if (first.type == 2) {
                TradePartnerSummary(
                    nickname = first.headline.ifBlank { "Unknown trader" },
                    avatarUrl = first.iconUrl,
                    steamLevel = "Steam Level: --"
                )
            } else {
                null
            }

            ConfirmationBundle(
                key = key,
                title = first.typeName,
                typeName = first.typeName,
                items = groupItems,
                partner = partner
            )
        }
    }

    fun respondBundle(auth: ConfirmationAuthContext, bundle: ConfirmationBundle, accept: Boolean): Boolean {
        val op = if (accept) "allow" else "cancel"
        val time = System.currentTimeMillis() / 1000L
        val key = confirmationKey(auth.identitySecret, time, op)

        val pairs = mutableListOf(
            "p" to auth.deviceId,
            "a" to auth.steamId,
            "k" to key,
            "t" to time.toString(),
            "m" to "react",
            "tag" to op,
            "sessionid" to auth.sessionId,
            "op" to op
        )

        if (bundle.items.size > 1) {
            for (item in bundle.items) {
                pairs.add("cid[]" to item.id)
                pairs.add("ck[]" to item.nonce)
            }
            val json = postJson("https://steamcommunity.com/mobileconf/multiajaxop", pairs, auth)
            return json.optBoolean("success", false)
        }

        val item = bundle.items.firstOrNull() ?: return false
        pairs.add("cid" to item.id)
        pairs.add("ck" to item.nonce)
        val json = getJson("https://steamcommunity.com/mobileconf/ajaxop?${toQuery(pairs)}", auth)
        return json.optBoolean("success", false)
    }

    fun respondItem(auth: ConfirmationAuthContext, item: ConfirmationItem, accept: Boolean): Boolean {
        val op = if (accept) "allow" else "cancel"
        val time = System.currentTimeMillis() / 1000L
        val key = confirmationKey(auth.identitySecret, time, op)

        val query = StringBuilder()
            .append("p=").append(url(auth.deviceId))
            .append("&a=").append(url(auth.steamId))
            .append("&k=").append(url(key))
            .append("&t=").append(time)
            .append("&m=react")
            .append("&tag=").append(op)
            .append("&sessionid=").append(url(auth.sessionId))
            .append("&cid=").append(url(item.id))
            .append("&ck=").append(url(item.nonce))
            .append("&op=").append(op)

        val json = getJson("https://steamcommunity.com/mobileconf/ajaxop?$query", auth)
        return json.optBoolean("success", false)
    }

    private fun withConfirmationQuery(auth: ConfirmationAuthContext, tag: String): String {
        val time = System.currentTimeMillis() / 1000L
        val key = confirmationKey(auth.identitySecret, time, tag)

        return "p=${url(auth.deviceId)}&a=${url(auth.steamId)}&k=${url(key)}&t=$time&m=react&tag=$tag"
    }

    private fun confirmationKey(identitySecret: String, time: Long, tag: String): String {
        val secret = Base64.getDecoder().decode(identitySecret)
        val timeBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(time).array()
        val data = ByteArray(timeBytes.size + tag.length)
        System.arraycopy(timeBytes, 0, data, 0, timeBytes.size)
        val tagBytes = tag.toByteArray(Charsets.UTF_8)
        System.arraycopy(tagBytes, 0, data, timeBytes.size, tagBytes.size)

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data))
    }

    private fun getJson(url: String, auth: ConfirmationAuthContext): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "okhttp/3.12.12")
        connection.setRequestProperty("Accept", "application/json, text/javascript, text/html, application/xml, text/xml, */*")
        connection.setRequestProperty("Accept-Language", "en-US")
        connection.setRequestProperty("Origin", "https://steamcommunity.com")
        connection.setRequestProperty("Referer", "https://steamcommunity.com/mobileconf")

        val loginCookie = if (auth.steamLoginSecure.contains('%')) {
            URLDecoder.decode(auth.steamLoginSecure, "UTF-8")
        } else {
            auth.steamLoginSecure
        }

        val cookieHeader = buildString {
            append("steamLoginSecure=").append(loginCookie)
            append("; sessionid=").append(auth.sessionId)
            append("; steamid=").append(auth.steamId)
            append("; Steam_Language=english")
            append("; mobileClient=android")
            append("; mobileClientVersion=777777 3.6.1")
        }
        connection.setRequestProperty("Cookie", cookieHeader)

        val status = connection.responseCode
        val body = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("HTTP $status from Steam: $err")
        }

        return JSONObject(body)
    }

    private fun postJson(url: String, pairs: List<Pair<String, String>>, auth: ConfirmationAuthContext): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", "okhttp/3.12.12")
        connection.setRequestProperty("Accept", "application/json, text/javascript, text/html, application/xml, text/xml, */*")
        connection.setRequestProperty("Accept-Language", "en-US")
        connection.setRequestProperty("Origin", "https://steamcommunity.com")
        connection.setRequestProperty("Referer", "https://steamcommunity.com/mobileconf")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")

        val loginCookie = if (auth.steamLoginSecure.contains('%')) {
            URLDecoder.decode(auth.steamLoginSecure, "UTF-8")
        } else {
            auth.steamLoginSecure
        }

        val cookieHeader = buildString {
            append("steamLoginSecure=").append(loginCookie)
            append("; sessionid=").append(auth.sessionId)
            append("; steamid=").append(auth.steamId)
            append("; Steam_Language=english")
            append("; mobileClient=android")
            append("; mobileClientVersion=777777 3.6.1")
        }
        connection.setRequestProperty("Cookie", cookieHeader)

        val body = toQuery(pairs)
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val status = connection.responseCode
        val raw = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IllegalStateException("HTTP $status from Steam: $err")
        }

        return JSONObject(raw)
    }

    private fun toQuery(pairs: List<Pair<String, String>>): String {
        return pairs.joinToString("&") { (k, v) -> "${url(k)}=${url(v)}" }
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")
}
