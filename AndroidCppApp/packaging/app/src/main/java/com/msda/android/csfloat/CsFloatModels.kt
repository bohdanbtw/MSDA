package com.msda.android.csfloat

/**
 * CSFloat DTOs. Sale → trade → Guard pipeline comes later; these cover /me and /me/trades.
 */
data class CsFloatMeSummary(
    val userId: String = "",
    val username: String = "",
    val balanceCents: Int = 0,
    val pendingBalanceCents: Int = 0,
    /**
     * T076: opaque hint from `/me` (`actionable_trades` count/array).
     * Null when missing/unparseable → worker must fetch trades list.
     */
    val actionableHint: String? = null
)

data class CsFloatTradeSummary(
    val id: String,
    val state: String = "",
    val sellerId: String = "",
    val buyerSteamId: String = "",
    val marketHashName: String = "",
    val priceCents: Int = 0,
    val assetId: String = "",
    val steamOfferId: String = "",
    val steamOfferState: String = ""
) {
    fun priceUsdLabel(): String {
        if (priceCents <= 0) return "—"
        val dollars = priceCents / 100
        val cents = priceCents % 100
        return "$%d.%02d".format(dollars, cents)
    }
}

sealed class CsFloatResult<out T> {
    data class Ok<T>(val value: T) : CsFloatResult<T>()
    data class HttpError(val code: Int, val body: String) : CsFloatResult<Nothing>()
    data class RateLimited(val retryAfterSec: Long?) : CsFloatResult<Nothing>()
    data class NetworkError(val message: String) : CsFloatResult<Nothing>()
}
