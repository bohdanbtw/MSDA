package com.msda.android

import android.content.Context
import androidx.work.*
import androidx.work.ListenableWorker.Result
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object SessionRenewalManager {
    private const val UNIQUE_WORK_NAME = "session_renewal_periodic"
    private const val RENEWAL_INTERVAL_MINUTES = 15L

    /**
     * Start (or update) the periodic session renewal work.
     * Only active when background confirmations are enabled.
     */
    fun schedule(context: Context) {
        if (!AppSettings.isBackgroundConfirmationsEnabled(context)) {
            cancel(context)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SessionRefreshWorker>(
            RENEWAL_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    class SessionRefreshWorker(
        appContext: Context,
        params: WorkerParameters
    ) : Worker(appContext, params) {

        override fun doWork(): Result {
            return try {
                val accountsJson = NativeBridge.getAccounts()
                val jsonArray = JSONArray(accountsJson)
                var successCount = 0

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val steamId = obj.optString("steamId", "")
                    val deviceId = obj.optString("deviceId", "")

                    if (steamId.isNotEmpty() && deviceId.isNotEmpty()) {
                        val refreshed = NativeBridge.tryRefreshSession(steamId, deviceId)
                        if (refreshed) successCount++
                    }
                }

                // Logging can be added here if desired
                Result.success()
            } catch (e: Exception) {
                // Network errors, missing accounts, etc. – retry later
                Result.retry()
            }
        }
    }
}
