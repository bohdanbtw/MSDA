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
                    // Do not retain or log response bodies that could echo credentials.
                    resp.code >= 400 -> CsFloatResult.HttpError(resp.code, "")
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
        val buyer = obj.optJSONObject("buyer")
        val buyerSteamId = firstNonBlank(
            buyer?.optString("steam_id"),
            buyer?.optString("steamid"),
            obj.optString("buyer_id")
        )
        val contract = obj.optJSONObject("contract")
            ?: obj.optJSONObject("item")
            ?: obj.optJSONArray("contract_items")?.optJSONObject(0)
            ?: obj.optJSONArray("items")?.optJSONObject(0)
        val item = contract?.optJSONObject("item") ?: contract
        val marketHashName = firstNonBlank(
            item?.optString("market_hash_name"),
            contract?.optString("market_hash_name"),
            obj.optString("market_hash_name"),
            obj.optString("item_name")
        )
        val priceCents = when {
            obj.has("price") -> obj.optInt("price", 0)
            contract != null && contract.has("price") -> contract.optInt("price", 0)
            else -> 0
        }
        val assetId = firstNonBlank(
            item?.optString("asset_id"),
            contract?.optString("asset_id"),
            obj.optString("asset_id")
        )
        val steamOffer = obj.optJSONObject("steam_offer")
        val steamOfferId = firstNonBlank(
            steamOffer?.optString("id"),
            steamOffer?.optString("offer_id"),
            obj.optString("steam_offer_id")
        )
        val steamOfferState = when {
            steamOffer == null -> ""
            steamOffer.isNull("state") -> ""
            else -> steamOffer.opt("state")?.toString().orEmpty()
        }
        return CsFloatTradeSummary(
            id = firstNonBlank(obj.optString("id"), obj.optString("trade_id")),
            state = obj.optString("state", ""),
            sellerId = firstNonBlank(obj.optString("seller_id"), obj.optString("seller")),
            buyerSteamId = buyerSteamId,
            marketHashName = marketHashName,
            priceCents = priceCents,
            assetId = assetId,
            steamOfferId = steamOfferId,
            steamOfferState = steamOfferState
        )
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            val t = v?.trim().orEmpty()
            if (t.isNotEmpty() && t != "null") return t
        }
        return ""
    }
}
