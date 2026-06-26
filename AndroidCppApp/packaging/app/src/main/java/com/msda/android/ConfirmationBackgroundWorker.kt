package com.msda.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msda.android.steam.AuthContextMerger
import com.msda.android.steam.NativeAuthBridge

class ConfirmationBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!AppSettings.isBackgroundSyncEnabled(applicationContext)) {
            return Result.success()
        }

        try {
            initializeNativeAccounts()

            val pendingCount = loadPendingConfirmationsCount()
            if (pendingCount > 0 && AppSettings.isPushConfirmationsEnabled(applicationContext) && canPostNotifications()) {
                postNotification(pendingCount)
            }

            return Result.success()
        } finally {
            BackgroundSyncScheduler.scheduleNextAlarm(applicationContext)
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

    private suspend fun loadPendingConfirmationsCount(): Int {
        val accountsRaw = try {
            NativeBridge.getAccounts()
        } catch (_: Throwable) {
            return 0
        }

        var total = 0
        val lines = accountsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val parts = line.split('|')
            val steamId = parts.getOrNull(2).orEmpty()
            if (steamId.isBlank()) continue

            try {
                // By-steamId payload — race-free, does not change the active account
                val base = NativeAuthBridge.confirmationAuthForSteamId(applicationContext, steamId) ?: continue
                var activeSession = base

                var bundles = try {
                    ConfirmationService.loadBundlesWithAutoRenew(
                        context = applicationContext,
                        auth = activeSession,
                        onSessionRenewed = { newAuth -> activeSession = newAuth }
                    )
                } catch (_: NeedPasswordException) {
                    continue
                } catch (_: Throwable) {
                    continue
                }

                val marketEnabled = AppSettings.isMarketAutoConfirmEnabled(applicationContext, steamId)
                val tradeEnabled = AppSettings.isTradeAutoConfirmEnabled(applicationContext, steamId)
                val giftTradeEnabled = AppSettings.isGiftTradeAutoConfirmEnabled(applicationContext, steamId)

                if (marketEnabled || tradeEnabled || giftTradeEnabled) {
                    val accepted = ConfirmationAutoAccept.acceptMatching(
                        context = applicationContext,
                        auth = activeSession,
                        bundles = bundles,
                        marketEnabled = marketEnabled,
                        tradeEnabled = tradeEnabled,
                        giftTradeEnabled = giftTradeEnabled,
                        onSessionRenewed = { newAuth -> activeSession = newAuth }
                    )

                    if (accepted > 0) {
                        bundles = try {
                            ConfirmationService.loadBundlesWithAutoRenew(
                                context = applicationContext,
                                auth = activeSession,
                                onSessionRenewed = { newAuth -> activeSession = newAuth }
                            )
                        } catch (_: Throwable) {
                            bundles
                        }
                    }
                }

                total += bundles.sumOf { it.items.size }
            } catch (_: Throwable) {
            }
        }

        return total
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postNotification(count: Int) {
        val channelId = "msda_confirmations"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.notification_channel_confirmations),
                NotificationManager.IMPORTANCE_HIGH
            )
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, HubActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(applicationContext.getString(R.string.notification_title_confirmations))
            .setContentText(applicationContext.getString(R.string.notification_text_confirmations, count))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(2001, notification)
    }
}
