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

    private fun loadPendingConfirmationsCount(): Int {
        val accountsRaw = try {
            NativeBridge.getAccounts()
        } catch (_: Throwable) {
            return 0
        }

        var total = 0
        val lines = accountsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (line in lines) {
            val parts = line.split('|')
            val index = parts.firstOrNull()?.toIntOrNull() ?: continue
            val steamId = parts.getOrNull(2).orEmpty()
            if (steamId.isBlank()) continue

            try {
                if (!NativeBridge.setActiveAccount(index)) continue

                val payload = NativeBridge.getActiveConfirmationAuthPayload()
                val base = ConfirmationService.parseAuthPayload(payload) ?: continue
                val withSession = SessionStore.loadSession(applicationContext, steamId)
                    ?.let { base.withSession(it) }
                    ?: continue

                var bundles = ConfirmationService.loadBundles(applicationContext, withSession)

                val marketEnabled = AppSettings.isMarketAutoConfirmEnabled(applicationContext, steamId)
                val tradeEnabled = AppSettings.isTradeAutoConfirmEnabled(applicationContext, steamId)
                val giftTradeEnabled = AppSettings.isGiftTradeAutoConfirmEnabled(applicationContext, steamId)

                if (marketEnabled || tradeEnabled || giftTradeEnabled) {
                    val itemsToAutoAccept = bundles
                        .flatMap { it.items }
                        .filter { item ->
                            (marketEnabled && isStrictMarketConfirmation(item)) ||
                                (tradeEnabled && isStrictTradeConfirmation(item)) ||
                                (giftTradeEnabled && isGiftTradeConfirmation(item))
                        }
                        .distinctBy { it.id }

                    itemsToAutoAccept.forEach { item ->
                        try {
                            ConfirmationService.respondItem(applicationContext, withSession, item, true)
                        } catch (_: Throwable) {
                        }
                    }

                    if (itemsToAutoAccept.isNotEmpty()) {
                        bundles = try {
                            ConfirmationService.loadBundles(applicationContext, withSession)
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

    private fun isStrictMarketConfirmation(item: ConfirmationItem): Boolean {
        return item.type != 2 && item.typeName.contains("market", ignoreCase = true)
    }

    private fun isStrictTradeConfirmation(item: ConfirmationItem): Boolean {
        return item.type == 2 || item.typeName.contains("trade", ignoreCase = true)
    }

    private fun isGiftTradeConfirmation(item: ConfirmationItem): Boolean {
        if (!isStrictTradeConfirmation(item)) {
            return false
        }

        val text = (listOf(item.headline) + item.summary)
            .joinToString(" ")
            .lowercase()

        val giftSignals = listOf(
            "gift",
            "you will receive",
            "you'll receive",
            "for free",
            "without exchange",
            "no items from your inventory",
            "no items from you",
            "0 items from you",
            "nothing to give",
            "sent you a gift"
        )
        val lossSignals = listOf(
            "you will give",
            "you'll give",
            "you are giving",
            "from your inventory",
            "in exchange",
            "for your"
        )

        return giftSignals.any { text.contains(it) } && lossSignals.none { text.contains(it) }
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
