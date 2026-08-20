package com.msda.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/**
 * Background confirmation sync is disabled to avoid Steam rate limits.
 * Calls here only cancel leftover confirmation alarms/work from older app versions.
 * Session renewal is opt-in and owned by [SessionRenewalManager] + [AppSettings].
 */
object BackgroundSyncScheduler {
    private const val IMMEDIATE_WORK_NAME = "msda_confirmation_sync_now"
    private const val ALARM_REQUEST_CODE = 7001
    const val ACTION_BACKGROUND_SYNC_ALARM = "com.msda.android.ACTION_BACKGROUND_SYNC_ALARM"

    fun configure(context: Context) {
        disable(context)
        refreshSessionRenewal(context)
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        cancelAlarm(context)
        AppSettings.setBackgroundConfirmationsEnabled(context, false)
        AppSettings.setPushConfirmationsEnabled(context, false)
        // Do not cancel SessionRenewalManager here — opt-in setting owns that lifecycle.
    }

    /** Schedule or cancel proactive session renewal from the Settings toggle (default OFF). */
    fun refreshSessionRenewal(context: Context) {
        if (AppSettings.isSessionRenewalEnabled(context)) {
            SessionRenewalManager.schedule(context)
        } else {
            SessionRenewalManager.cancel(context)
        }
    }

    fun enqueueNow(context: Context) {
        // no-op — confirmations are manual only
    }

    fun scheduleNextAlarm(context: Context, delayMs: Long = 0L) {
        // no-op
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent(context))
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BackgroundSyncAlarmReceiver::class.java).apply {
            action = ACTION_BACKGROUND_SYNC_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
