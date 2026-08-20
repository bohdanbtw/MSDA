package com.msda.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import android.widget.Toast
import com.msda.android.steam.TimeAligner
import java.io.File

class Code2FAWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ensureMafilesLoaded(context)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleNextTick(context)
    }

    override fun onEnabled(context: Context) {
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            AppSettings.clearWidgetAccount(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_COPY -> {
                val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    copyCodeForWidget(context, appWidgetId)
                }
            }
            ACTION_TICK, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, Code2FAWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    onUpdate(context, manager, ids)
                } else {
                    cancelTick(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.msda.android.action.WIDGET_CODE_TICK"
        const val ACTION_COPY = "com.msda.android.action.WIDGET_CODE_COPY"
        private const val EXTRA_APPWIDGET_ID = "msda_widget_id"
        private const val TICK_INTERVAL_MS = 1000L

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_code_2fa)
            val steamId = AppSettings.getWidgetSteamId(context, appWidgetId)
            val accountName = AppSettings.getWidgetAccountName(context, appWidgetId)
            val accountIndex = AppSettings.getWidgetAccountIndex(context, appWidgetId)

            val configureIntent = Intent(context, Code2FAWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val configurePending = PendingIntent.getActivity(
                context,
                appWidgetId,
                configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Title / root → reconfigure; code → copy
            views.setOnClickPendingIntent(R.id.widgetAccountName, configurePending)
            views.setOnClickPendingIntent(R.id.widgetTimer, configurePending)
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePending)
            views.setOnClickPendingIntent(R.id.widgetCode, copyPendingIntent(context, appWidgetId))

            if (steamId.isBlank() && accountIndex < 0) {
                views.setTextViewText(R.id.widgetAccountName, context.getString(R.string.widget_no_account))
                views.setTextViewText(R.id.widgetCode, context.getString(R.string.widget_code_placeholder))
                views.setTextViewText(R.id.widgetTimer, context.getString(R.string.widget_timer_placeholder))
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val label = if (steamId.isNotBlank()) AppSettings.getAccountLabel(context, steamId) else ""
            val title = when {
                accountName.isNotBlank() && label.isNotBlank() ->
                    context.getString(R.string.hub_account_title_with_label, accountName, label)
                accountName.isNotBlank() -> accountName
                else -> context.getString(R.string.widget_name)
            }
            views.setTextViewText(R.id.widgetAccountName, title)

            val code = try {
                when {
                    steamId.isNotBlank() -> NativeBridge.getCodeForSteamId(steamId).trim()
                    accountIndex >= 0 -> {
                        NativeBridge.setActiveAccount(accountIndex)
                        NativeBridge.getActiveCode().trim()
                    }
                    else -> ""
                }
            } catch (_: Throwable) {
                ""
            }

            val seconds = resolveSecondsRemaining(accountIndex)

            if (code.isBlank()) {
                views.setTextViewText(R.id.widgetCode, context.getString(R.string.widget_account_missing))
                views.setTextViewText(R.id.widgetTimer, context.getString(R.string.widget_timer_placeholder))
            } else {
                views.setTextViewText(R.id.widgetCode, code)
                views.setTextViewText(
                    R.id.widgetTimer,
                    context.getString(R.string.code_timer_value, seconds.coerceIn(1, 30))
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun requestUpdateAll(context: Context) {
            val intent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }

        private fun resolveSecondsRemaining(accountIndex: Int): Int {
            try {
                if (accountIndex >= 0) {
                    NativeBridge.setActiveAccount(accountIndex)
                    val native = NativeBridge.getSecondsToNextCode()
                    if (native in 0..30) {
                        return if (native == 0) 30 else native
                    }
                }
            } catch (_: Throwable) {
            }
            // steamId-bound widgets (or native miss): derive from Steam-aligned epoch when cached.
            val epoch = if (TimeAligner.hasFreshCache()) {
                TimeAligner.cachedAlignedEpochSeconds()
            } else {
                System.currentTimeMillis() / 1000L
            }
            val mod = (epoch % 30L).toInt()
            return if (mod == 0) 30 else 30 - mod
        }

        private fun copyCodeForWidget(context: Context, appWidgetId: Int) {
            ensureMafilesLoaded(context)
            val steamId = AppSettings.getWidgetSteamId(context, appWidgetId)
            val accountIndex = AppSettings.getWidgetAccountIndex(context, appWidgetId)
            val code = try {
                when {
                    steamId.isNotBlank() -> NativeBridge.getCodeForSteamId(steamId).trim()
                    accountIndex >= 0 -> {
                        NativeBridge.setActiveAccount(accountIndex)
                        NativeBridge.getActiveCode().trim()
                    }
                    else -> ""
                }
            } catch (_: Throwable) {
                ""
            }
            if (code.isBlank()) {
                Toast.makeText(context, context.getString(R.string.code_copy_failed), Toast.LENGTH_SHORT).show()
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MSDA 2FA", code))
            Toast.makeText(context, context.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
        }

        private fun copyPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = ACTION_COPY
                putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context,
                10_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun ensureMafilesLoaded(context: Context) {
            val importDir = File(context.filesDir, "mafiles")
            if (!importDir.exists() || !importDir.isDirectory) return
            val hasMafiles = importDir.listFiles()?.any {
                it.isFile && it.name.endsWith(".mafile", ignoreCase = true)
            } == true
            if (!hasMafiles) return
            try {
                NativeBridge.importMafilesFromFolder(importDir.absolutePath)
            } catch (_: Throwable) {
            }
        }

        private fun scheduleNextTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = tickPendingIntent(context)
            val triggerAt = SystemClock.elapsedRealtime() + TICK_INTERVAL_MS
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            } catch (_: Throwable) {
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending
                )
            }
        }

        private fun cancelTick(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(tickPendingIntent(context))
        }

        private fun tickPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = ACTION_TICK
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
