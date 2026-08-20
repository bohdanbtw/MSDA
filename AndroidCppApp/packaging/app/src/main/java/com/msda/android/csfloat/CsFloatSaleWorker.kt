package com.msda.android.csfloat

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background CSFloat check skeleton.
 *
 * Polls CSFloat for queued/pending trades only. Does **not** call Steam Guard
 * or confirmation APIs — that stays manual / sale-triggered in later slices.
 */
class CsFloatSaleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ready = CsFloatAccountSettings.readySteamIds(applicationContext)
        if (ready.isEmpty()) {
            Log.d(TAG, "No opted-in CSFloat accounts with API keys; skipping")
            CsFloatScheduler.cancel(applicationContext)
            return Result.success()
        }

        var sawRetryable = false
        for (steamId in ready) {
            val apiKey = CsFloatSecureStore.getApiKey(applicationContext, steamId) ?: continue
            val client = CsFloatClient(apiKey)

            when (val me = client.me()) {
                is CsFloatResult.Ok -> {
                    Log.d(
                        TAG,
                        "CSFloat /me ok for $steamId user=${me.value.username} " +
                            "balance=${me.value.balanceCents}"
                    )
                }
                is CsFloatResult.RateLimited -> {
                    Log.w(TAG, "CSFloat rate-limited for $steamId retryAfter=${me.retryAfterSec}")
                    sawRetryable = true
                    continue
                }
                is CsFloatResult.HttpError -> {
                    Log.w(TAG, "CSFloat /me HTTP ${me.code} for $steamId")
                    if (me.code in 500..599) sawRetryable = true
                    continue
                }
                is CsFloatResult.NetworkError -> {
                    Log.w(TAG, "CSFloat /me network for $steamId: ${me.message}")
                    sawRetryable = true
                    continue
                }
            }

            when (val trades = client.listQueuedTrades()) {
                is CsFloatResult.Ok -> {
                    Log.d(TAG, "CSFloat trades for $steamId: ${trades.value.size} queued/pending")
                    // Future: accept sale + Steam trade + Guard only for real offers.
                }
                is CsFloatResult.RateLimited -> {
                    Log.w(TAG, "CSFloat trades rate-limited for $steamId")
                    sawRetryable = true
                }
                is CsFloatResult.HttpError -> {
                    Log.w(TAG, "CSFloat trades HTTP ${trades.code} for $steamId")
                    if (trades.code in 500..599) sawRetryable = true
                }
                is CsFloatResult.NetworkError -> {
                    Log.w(TAG, "CSFloat trades network for $steamId: ${trades.message}")
                    sawRetryable = true
                }
            }
        }

        return if (sawRetryable) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "CsFloatSaleWorker"
    }
}
