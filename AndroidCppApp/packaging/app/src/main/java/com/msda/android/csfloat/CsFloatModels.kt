package com.msda.android.csfloat

/**
 * Lightweight CSFloat DTOs for the Android spike.
 * Full sale → trade → Guard pipeline comes later; these cover /me and /me/trades.
 */
data class CsFloatMeSummary(
    val userId: String = "",
    val username: String = "",
    val balanceCents: Int = 0,
    val pendingBalanceCents: Int = 0
)

data class CsFloatTradeSummary(
    val id: String,
    val state: String = "",
    val sellerId: String = ""
)

sealed class CsFloatResult<out T> {
    data class Ok<T>(val value: T) : CsFloatResult<T>()
    data class HttpError(val code: Int, val body: String) : CsFloatResult<Nothing>()
    data class RateLimited(val retryAfterSec: Long?) : CsFloatResult<Nothing>()
    data class NetworkError(val message: String) : CsFloatResult<Nothing>()
}
