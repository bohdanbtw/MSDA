package com.msda.android.csfloat

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Schedules a long-interval CSFloat background job when ≥1 account is opted in + keyed.
 * Also supports a one-shot "Check now" unique work (T079). No Steam Guard traffic.
 */
object CsFloatScheduler {
    private const val TAG = "CsFloatScheduler"
    const val UNIQUE_WORK_NAME = "csfloat_sale_check_periodic"
    const val CHECK_NOW_WORK_NAME = "csfloat_sale_check_now"
    const val KEY_STEAM_ID = "steam_id"

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

    /**
     * One-shot unique work for the CSFloat dialog "Check now" button (T079).
     * Scoped to [steamId] when keyed + enabled. KEEP so spam taps do not stack.
     */
    fun enqueueCheckNow(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        if (!CsFloatAccountSettings.isEnabled(context, steamId) ||
            !CsFloatSecureStore.hasApiKey(context, steamId)
        ) {
            Log.d(TAG, "Check now skipped — account not ready …${steamId.takeLast(4)}")
            return false
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CsFloatSaleWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_STEAM_ID to steamId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHECK_NOW_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Enqueued CSFloat check-now for …${steamId.takeLast(4)}")
        return true
    }

    fun checkNowWorkInfos(context: Context): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(CHECK_NOW_WORK_NAME)
    }

    fun isCheckNowActive(infos: List<WorkInfo>?): Boolean {
        if (infos.isNullOrEmpty()) return false
        return infos.any { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.BLOCKED
        }
    }
}
