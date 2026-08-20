package com.msda.android.csfloat

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background CSFloat check.
 *
 * Polls CSFloat for queued/pending trades only. Does **not** call Steam Guard
 * or confirmation APIs. Notifies only when queued count increases after a baseline.
 */
class CsFloatSaleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val onlySteamId = inputData.getString(CsFloatScheduler.KEY_STEAM_ID)?.trim().orEmpty()
        val ready = if (onlySteamId.isNotEmpty()) {
            // One-shot Check-now for a single account (API key required).
            if (CsFloatSecureStore.hasApiKey(applicationContext, onlySteamId)) {
                listOf(onlySteamId)
            } else {
                emptyList()
            }
        } else {
            CsFloatAccountSettings.readySteamIds(applicationContext)
        }
        if (ready.isEmpty()) {
            Log.d(TAG, "No opted-in CSFloat accounts with API keys; skipping")
            if (onlySteamId.isEmpty()) {
                CsFloatScheduler.cancel(applicationContext)
            }
            return Result.success()
        }

        var sawRetryable = false
        for (steamId in ready) {
            val apiKey = CsFloatSecureStore.getApiKey(applicationContext, steamId) ?: continue
            val client = CsFloatClient(apiKey)
            var accountName = ""

            when (val me = client.me()) {
                is CsFloatResult.Ok -> {
                    accountName = me.value.username
                    Log.d(TAG, "CSFloat /me ok for …${steamId.takeLast(4)}")
                }
                is CsFloatResult.RateLimited -> {
                    Log.w(TAG, "CSFloat rate-limited for …${steamId.takeLast(4)}")
                    sawRetryable = true
                    continue
                }
                is CsFloatResult.HttpError -> {
                    Log.w(TAG, "CSFloat /me HTTP ${me.code}")
                    if (me.code in 500..599) sawRetryable = true
                    continue
                }
                is CsFloatResult.NetworkError -> {
                    Log.w(TAG, "CSFloat /me network")
                    sawRetryable = true
                    continue
                }
            }

            when (val trades = client.listQueuedTrades()) {
                is CsFloatResult.Ok -> {
                    val count = trades.value.size
                    Log.d(TAG, "CSFloat trades …${steamId.takeLast(4)}: $count queued/pending")
                    handleQueuedCount(steamId, accountName, count)
                }
                is CsFloatResult.RateLimited -> {
                    Log.w(TAG, "CSFloat trades rate-limited")
                    sawRetryable = true
                }
                is CsFloatResult.HttpError -> {
                    Log.w(TAG, "CSFloat trades HTTP ${trades.code}")
                    if (trades.code in 500..599) sawRetryable = true
                }
                is CsFloatResult.NetworkError -> {
                    Log.w(TAG, "CSFloat trades network")
                    sawRetryable = true
                }
            }
        }

        return if (sawRetryable) Result.retry() else Result.success()
    }

    private fun handleQueuedCount(steamId: String, accountName: String, count: Int) {
        val baselined = CsFloatAccountSettings.hasQueuedBaseline(applicationContext, steamId)
        val last = CsFloatAccountSettings.getLastQueuedCount(applicationContext, steamId)
        if (!baselined) {
            // First successful poll: store baseline, do not spam notify.
            CsFloatAccountSettings.setLastQueuedCount(applicationContext, steamId, count, baselined = true)
            return
        }
        if (count > last) {
            CsFloatNotifier.notifyPendingIncrease(applicationContext, steamId, count, accountName)
        }
        CsFloatAccountSettings.setLastQueuedCount(applicationContext, steamId, count, baselined = true)
    }

    companion object {
        private const val TAG = "CsFloatSaleWorker"
    }
}
