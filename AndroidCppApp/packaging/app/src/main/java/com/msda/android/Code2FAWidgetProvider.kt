package com.msda.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

class Code2FAWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MafileImportManager.ensureImported(context) { requestUpdateAll(context) }
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleBoundaryUpdate(context)
    }

    override fun onEnabled(context: Context) {
        MafileImportManager.ensureImported(context) { requestUpdateAll(context) }
        scheduleBoundaryUpdate(context)
    }

    override fun onDisabled(context: Context) {
        cancelBoundaryUpdate(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            AppSettings.clearWidgetAccount(context, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH && intent.action != ACTION_BOUNDARY_UPDATE) {
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, Code2FAWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            onUpdate(context, manager, ids)
        } else {
            cancelBoundaryUpdate(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.msda.android.action.WIDGET_CODE_REFRESH"
        private const val ACTION_BOUNDARY_UPDATE = "com.msda.android.action.WIDGET_CODE_BOUNDARY"
        private const val CODE_PERIOD_MS = 30_000L
        private const val BOUNDARY_DELAY_MS = 250L

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_code_2fa)
            val steamId = AppSettings.getWidgetSteamId(context, appWidgetId)
            val accountName = AppSettings.getWidgetAccountName(context, appWidgetId)
            val accountIndex = AppSettings.getWidgetAccountIndex(context, appWidgetId)
            // Resolve an index-only config to a Steam ID once. All reads below are
            // account-scoped so this receiver never mutates the global active account.
            val resolvedSteamId = steamId.ifBlank { NativeBridge.steamIdForAccountIndex(accountIndex) }

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
            views.setOnClickPendingIntent(R.id.widgetRoot, configurePending)

            val refreshIntent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId + 1,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPending)

            if (steamId.isBlank() && accountIndex < 0) {
                views.setTextViewText(R.id.widgetAccountName, context.getString(R.string.widget_no_account))
                views.setTextViewText(R.id.widgetCode, context.getString(R.string.widget_code_placeholder))
                showTimerPlaceholder(context, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val label = if (resolvedSteamId.isNotBlank()) AppSettings.getAccountLabel(context, resolvedSteamId) else ""
            val title = when {
                accountName.isNotBlank() && label.isNotBlank() ->
                    context.getString(R.string.hub_account_title_with_label, accountName, label)
                accountName.isNotBlank() -> accountName
                else -> context.getString(R.string.widget_name)
            }
            views.setTextViewText(R.id.widgetAccountName, title)

            val code = try {
                if (resolvedSteamId.isNotBlank()) NativeBridge.getCodeForSteamId(resolvedSteamId).trim()
                else ""
            } catch (_: Throwable) {
                ""
            }

            val seconds = try {
                // Kotlin-computed, account-scoped countdown: works with the committed
                // prebuilt libmsda_android.so (no new JNI symbol) and never mutates the
                // process-global active account on a background widget tick.
                if (resolvedSteamId.isNotBlank()) {
                    NativeBridge.getSecondsToNextCodeForSteamId(resolvedSteamId)
                } else {
                    -1
                }
            } catch (_: Throwable) {
                -1
            }

            if (code.isBlank()) {
                views.setTextViewText(R.id.widgetCode, context.getString(R.string.widget_account_missing))
                showTimerPlaceholder(context, views)
            } else {
                views.setTextViewText(R.id.widgetCode, code)
                if (seconds >= 0) {
                    // The Chronometer counts down locally in the widget host; no per-second
                    // broadcast or wakeup is needed to animate the remaining seconds.
                    views.setChronometer(
                        R.id.widgetCountdown,
                        SystemClock.elapsedRealtime() + seconds * 1000L,
                        context.getString(R.string.widget_timer_format),
                        true
                    )
                    views.setViewVisibility(R.id.widgetCountdown, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetTimer, View.GONE)
                } else {
                    showTimerPlaceholder(context, views)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun requestUpdateAll(context: Context) {
            // super.onReceive() only dispatches onUpdate when APPWIDGET_UPDATE carries
            // EXTRA_APPWIDGET_IDS. Without them the broadcast is a no-op, so include the
            // currently attached widget IDs to make every refresh call site effective.
            // No 1-second tick and no direct onUpdate() call: the receiver routes this
            // single broadcast through onUpdate() exactly once.
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, Code2FAWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun showTimerPlaceholder(context: Context, views: RemoteViews) {
            views.setViewVisibility(R.id.widgetCountdown, View.GONE)
            views.setTextViewText(R.id.widgetTimer, context.getString(R.string.widget_timer_placeholder))
            views.setViewVisibility(R.id.widgetTimer, View.VISIBLE)
        }

        /**
         * Re-arms a single, non-waking alarm just after the next TOTP rollover. The Chronometer
         * animates the countdown, so this only refreshes the code itself (~30s), never every
         * second. updatePeriodMillis remains a passive safety net.
         */
        private fun scheduleBoundaryUpdate(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = boundaryPendingIntent(context)
            val triggerAt = nextBoundaryElapsedRealtime()
            try {
                // An exact alarm is only a best-effort alignment to the rollover and never
                // wakes the device. Android 12+ requires SCHEDULE_EXACT_ALARM, so fall back
                // to an inexact non-waking alarm when it is not available.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    // set() with ELAPSED_REALTIME is inexact and non-waking: the intended
                    // fallback whenever exact alarms are not permitted.
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                }
            } catch (_: SecurityException) {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
            } catch (_: Throwable) {
            }
        }

        private fun cancelBoundaryUpdate(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            alarmManager.cancel(boundaryPendingIntent(context))
        }

        private fun nextBoundaryElapsedRealtime(): Long {
            val wallNow = System.currentTimeMillis()
            val nextBoundary = ((wallNow / CODE_PERIOD_MS) + 1) * CODE_PERIOD_MS + BOUNDARY_DELAY_MS
            return SystemClock.elapsedRealtime() + (nextBoundary - wallNow)
        }

        private fun boundaryPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, Code2FAWidgetProvider::class.java).apply {
                action = ACTION_BOUNDARY_UPDATE
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
