package com.msda.android.csfloat

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules a long-interval CSFloat background job when ≥1 account is opted in + keyed.
 * Exponential backoff on worker retries; no aggressive polling.
 */
object CsFloatScheduler {
    private const val TAG = "CsFloatScheduler"
    const val UNIQUE_WORK_NAME = "csfloat_sale_check_periodic"

    fun refresh(context: Context) {
        val ready = CsFloatAccountSettings.readySteamIds(context)
        if (ready.isEmpty()) {
            cancel(context)
            return
        }

        val intervalMinutes = ready
            .map { CsFloatAccountSettings.getPollIntervalMinutes(context, it) }
            .minOrNull()
            ?: CsFloatAccountSettings.DEFAULT_INTERVAL_MINUTES

        val clamped = intervalMinutes.coerceAtLeast(CsFloatAccountSettings.MIN_INTERVAL_MINUTES)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<CsFloatSaleWorker>(
            clamped, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d(TAG, "Scheduled CSFloat work every ${clamped}m for ${ready.size} account(s)")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.d(TAG, "Cancelled CSFloat periodic work")
    }
}
