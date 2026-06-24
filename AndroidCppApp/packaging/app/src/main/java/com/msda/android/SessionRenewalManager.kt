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
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
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
                        val payload = NativeBridge.getConfirmationAuthPayloadForSteamId(steamId)
                        val baseAuth = ConfirmationService.parseAuthPayload(payload) ?: continue
                        val savedSession = SessionStore.loadSession(applicationContext, steamId)
                        val auth = if (savedSession != null) baseAuth.withSession(savedSession) else baseAuth

                        // Renew when the token is expired/near-expiry, OR when expiry is
                        // unknown (legacy/migrated sessions) to bootstrap exp tracking once.
                        val expiryUnknown = (savedSession?.sessionExpiresAtMs ?: 0L) <= 0L
                        val shouldRenew = SessionStore.isSessionExpired(applicationContext, steamId) || expiryUnknown
                        if (!shouldRenew) continue

                        // Nothing to renew with — skip silently (user must log in once)
                        if (auth.refreshToken.isBlank() &&
                            PasswordManager.getPassword(applicationContext, auth.accountName).isNullOrBlank()
                        ) continue

                        val renewed = SessionManager.renew(applicationContext, auth)
                        if (renewed != null) renewedCount++
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
