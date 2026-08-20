package com.msda.android

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.msda.android.steam.AuthContextMerger
import com.msda.android.steam.NativeAuthBridge
import com.msda.android.steam.SessionHandler
import java.util.concurrent.TimeUnit

/**
 * Proactive session renewal. Runs periodically to rotate each account's refresh token
 * before it can expire, so a user who logged in once stays logged in indefinitely as long
 * as they (or this worker) touch the account within Steam's refresh-token lifetime.
 *
 * Renewal is race-free: it reads each account by steamId and never changes the native
 * active account, so it cannot disturb the account the user is viewing in the foreground.
 */
object SessionRenewalManager {
    private const val TAG = "SessionRenewalManager"
    private const val UNIQUE_WORK_NAME = "session_renewal_periodic"
    private const val RENEWAL_INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SessionRefreshWorker>(
            RENEWAL_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
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
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            if (!AppSettings.isSessionRenewalEnabled(applicationContext)) {
                cancel(applicationContext)
                return Result.success()
            }
            return try {
                initializeNativeAccounts()
                var renewedCount = 0

                val accountsRaw = NativeBridge.getAccounts()
                val lines = accountsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }

                for (line in lines) {
                    val parts = line.split('|')
                    val steamId = parts.getOrNull(2).orEmpty()
                    if (steamId.isBlank()) continue

                    try {
                        // By-steamId payload — does NOT change the native active account
                        val auth = NativeAuthBridge.confirmationAuthForSteamId(applicationContext, steamId)
                            ?: continue

                        if (!AuthContextMerger.isSessionNearExpiry(applicationContext, steamId)) continue

                        // Nothing to renew with — skip silently (user must log in once)
                        if (auth.refreshToken.isBlank() &&
                            PasswordManager.getPassword(applicationContext, auth.accountName).isNullOrBlank()
                        ) continue

                        // SessionHandler.ensureValid renews tokens only — never mobileconf/getlist.
                        runCatching { SessionHandler.ensureValid(applicationContext, auth) }
                            .onSuccess { renewedCount++ }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to renew session for $steamId", e)
                    }
                }

                Log.d(TAG, "Proactive renewal completed: $renewedCount account(s) refreshed")
                Result.success()
            } catch (e: Exception) {
                Log.w(TAG, "Session renewal worker failed", e)
                Result.retry()
            }
        }

        private fun initializeNativeAccounts() {
            try {
                val importDir = java.io.File(applicationContext.filesDir, "mafiles")
                if (importDir.exists() && importDir.isDirectory) {
                    NativeBridge.importMafilesFromFolder(importDir.absolutePath)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
