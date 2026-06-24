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

object SessionRenewalManager {
    private const val TAG = "SessionRenewalManager"
    private const val UNIQUE_WORK_NAME = "session_renewal_periodic"
    private const val RENEWAL_INTERVAL_MINUTES = 15L

    /**
     * Start (or update) the periodic session renewal work.
     * Runs when background sync is enabled to refresh tokens before they expire.
     */
    fun schedule(context: Context) {
        if (!AppSettings.isBackgroundSyncEnabled(context)) {
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
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return try {
                initializeNativeAccounts()
                var renewedCount = 0

                val accountsRaw = NativeBridge.getAccounts()
                val lines = accountsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }

                for (line in lines) {
                    val parts = line.split('|')
                    val index = parts.firstOrNull()?.toIntOrNull() ?: continue
                    val accountName = parts.getOrNull(1).orEmpty()
                    val steamId = parts.getOrNull(2).orEmpty()
                    if (steamId.isBlank()) continue

                    try {
                        if (!NativeBridge.setActiveAccount(index)) continue

                        val payload = NativeBridge.getActiveConfirmationAuthPayload()
                        val baseAuth = ConfirmationService.parseAuthPayload(payload) ?: continue
                        val savedSession = SessionStore.loadSession(applicationContext, steamId)
                        val auth = if (savedSession != null) baseAuth.withSession(savedSession) else baseAuth

                        val refreshToken = auth.refreshToken
                        if (refreshToken.isBlank()) continue

                        val refreshed = SteamAuthService.refreshSessionUsingToken(
                            refreshToken = refreshToken,
                            steamId = auth.steamId,
                            existingSessionId = auth.sessionId,
                            existingSteamLoginSecure = auth.steamLoginSecure
                        )

                        if (!refreshed.success ||
                            refreshed.steamLoginSecure.isNullOrBlank() ||
                            refreshed.sessionId.isNullOrBlank()
                        ) {
                            continue
                        }

                        SessionPersistence.saveSession(
                            applicationContext,
                            steamId,
                            StoredSteamSession(
                                steamLoginSecure = refreshed.steamLoginSecure,
                                sessionId = refreshed.sessionId,
                                refreshToken = refreshed.refreshToken ?: refreshToken,
                                accessToken = refreshed.accessToken ?: auth.accessToken,
                                accountName = accountName.ifBlank { auth.accountName }
                            )
                        )
                        renewedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to renew session for $steamId", e)
                    }
                }

                Log.d(TAG, "Proactive session renewal completed: $renewedCount account(s) refreshed")
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
