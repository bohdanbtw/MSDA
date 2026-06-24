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
     * Schedule proactive session renewal every 15 minutes.
     * Always active when the device has network — independent of background confirmations toggle.
     */
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

                        // Skip if session is still fresh (has expiry and not near expiry)
                        if (!SessionStore.isSessionExpired(applicationContext, steamId)) {
                            continue
                        }

                        val refreshToken = auth.refreshToken
                        val renewed = if (refreshToken.isNotBlank()) {
                            // Primary: use GenerateAccessTokenForApp
                            val r = SteamAuthService.refreshSessionUsingToken(
                                refreshToken = refreshToken,
                                steamId = auth.steamId
                            )
                            if (r.success) r else tryPasswordFallback(auth, accountName)
                        } else {
                            tryPasswordFallback(auth, accountName)
                        } ?: continue

                        if (!renewed.success ||
                            renewed.steamLoginSecure.isNullOrBlank() ||
                            renewed.sessionId.isNullOrBlank()
                        ) continue

                        SessionPersistence.saveSession(
                            applicationContext,
                            steamId,
                            StoredSteamSession(
                                steamLoginSecure = renewed.steamLoginSecure,
                                sessionId = renewed.sessionId,
                                refreshToken = renewed.refreshToken ?: refreshToken,
                                accessToken = renewed.accessToken ?: auth.accessToken,
                                accountName = accountName.ifBlank { auth.accountName },
                                sessionExpiresAtMs = renewed.sessionExpiresAtMs
                            )
                        )
                        renewedCount++
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

        private suspend fun tryPasswordFallback(
            auth: ConfirmationAuthContext,
            @Suppress("UNUSED_PARAMETER") accountName: String
        ): SteamAuthResult? {
            if (auth.accountName.isBlank()) return null
            val password = PasswordManager.getPassword(applicationContext, auth.accountName) ?: return null
            return try {
                SteamAuthService.refreshSessionUsingPassword(
                    accountName = auth.accountName,
                    password = password
                ).takeIf { it.success }
            } catch (_: Exception) {
                null
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
