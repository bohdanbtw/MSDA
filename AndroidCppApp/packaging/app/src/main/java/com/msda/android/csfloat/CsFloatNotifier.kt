package com.msda.android.csfloat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.msda.android.AppSettings
import com.msda.android.MainActivity
import com.msda.android.R

object CsFloatNotifier {
    const val CHANNEL_ID = "csfloat_sales"
    private const val NOTIFICATION_BASE_ID = 42_600

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.csfloat_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.csfloat_notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyPendingIncrease(context: Context, steamId: String, count: Int, accountName: String) {
        if (steamId.isBlank() || count <= 0) return
        ensureChannel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val label = AppSettings.getAccountLabel(context, steamId)
        val titleAccount = when {
            accountName.isNotBlank() && label.isNotBlank() ->
                context.getString(R.string.hub_account_title_with_label, accountName, label)
            accountName.isNotBlank() -> accountName
            label.isNotBlank() -> label
            else -> steamId.takeLast(6)
        }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_STEAM_ID, steamId)
            putExtra(MainActivity.EXTRA_ACCOUNT_NAME, accountName)
            putExtra(MainActivity.EXTRA_OPEN_CSFLOAT_PENDING, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            steamId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.csfloat_notification_title, titleAccount))
            .setContentText(context.getString(R.string.csfloat_notification_body, count))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_BASE_ID + (steamId.hashCode() and 0x0FFF),
                notification
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — soft-fail; polling still works.
        }
    }

    fun cancelForSteamId(context: Context, steamId: String) {
        if (steamId.isBlank()) return
        try {
            NotificationManagerCompat.from(context).cancel(
                NOTIFICATION_BASE_ID + (steamId.hashCode() and 0x0FFF)
            )
        } catch (_: Throwable) {
        }
    }
}
