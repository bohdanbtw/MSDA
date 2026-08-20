package com.msda.android.csfloat

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal CSFloat HTTP client stub (device → csfloat.com). No MSDA remote server.
 */
class CsFloatClient(
    private val apiKey: String,
    private val http: OkHttpClient = defaultClient()
) {
    companion object {
        private const val TAG = "CsFloatClient"
        const val BASE_URL = "https://csfloat.com/api/v1"

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun request(method: String, path: String): CsFloatResult<String> {
        val url = "$BASE_URL$path"
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", apiKey.trim())
                .header("Accept", "application/json")
                .header("User-Agent", "MSDA-Android/csfloat-stub")
            val req = when (method.uppercase()) {
                "GET" -> builder.get().build()
                else -> builder.method(method, null).build()
            }
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when {
                    resp.code == 429 -> {
                        val retry = resp.header("Retry-After")?.toLongOrNull()
                        CsFloatResult.RateLimited(retry)
                    }
                    resp.code >= 400 -> CsFloatResult.HttpError(resp.code, body.take(500))
                    else -> CsFloatResult.Ok(body)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CSFloat network error on $method $path", e)
            CsFloatResult.NetworkError(e.message ?: "network error")
        }
    }

    fun me(): CsFloatResult<CsFloatMeSummary> {
        return when (val raw = request("GET", "/me")) {
            is CsFloatResult.Ok -> {
                try {
                    val root = JSONObject(raw.value.ifBlank { "{}" })
                    val user = root.optJSONObject("user") ?: JSONObject()
                    CsFloatResult.Ok(
                        CsFloatMeSummary(
                            userId = user.optString("id", user.optString("steam_id", "")),
                            username = user.optString("username", user.optString("name", "")),
                            balanceCents = user.optInt("balance", 0),
                            pendingBalanceCents = user.optInt("pending_balance", 0)
                        )
                    )
                } catch (e: Exception) {
                    CsFloatResult.NetworkError("JSON parse: ${e.message}")
                }
            }
            is CsFloatResult.HttpError -> raw
            is CsFloatResult.RateLimited -> raw
            is CsFloatResult.NetworkError -> raw
        }
    }

    fun listQueuedTrades(
        states: String = "queued,pending",
        limit: Int = 50
    ): CsFloatResult<List<CsFloatTradeSummary>> {
        val path = "/me/trades?state=$states&limit=$limit&page=0"
        return when (val raw = request("GET", path)) {
            is CsFloatResult.Ok -> {
                try {
                    val text = raw.value.trim()
                    val trades = mutableListOf<CsFloatTradeSummary>()
                    if (text.startsWith("[")) {
                        val arr = org.json.JSONArray(text)
                        for (i in 0 until arr.length()) {
                            trades += parseTrade(arr.optJSONObject(i) ?: continue)
                        }
                    } else {
                        val root = JSONObject(text.ifBlank { "{}" })
                        val arr = root.optJSONArray("trades")
                            ?: root.optJSONArray("data")
                            ?: org.json.JSONArray()
                        for (i in 0 until arr.length()) {
                            trades += parseTrade(arr.optJSONObject(i) ?: continue)
                        }
                    }
                    CsFloatResult.Ok(trades)
                } catch (e: Exception) {
                    CsFloatResult.NetworkError("JSON parse: ${e.message}")
                }
            }
            is CsFloatResult.HttpError -> raw
            is CsFloatResult.RateLimited -> raw
            is CsFloatResult.NetworkError -> raw
        }
    }

    private fun parseTrade(obj: JSONObject): CsFloatTradeSummary {
        return CsFloatTradeSummary(
            id = obj.optString("id", obj.optString("trade_id", "")),
            state = obj.optString("state", ""),
            sellerId = obj.optString("seller_id", "")
        )
    }
}
