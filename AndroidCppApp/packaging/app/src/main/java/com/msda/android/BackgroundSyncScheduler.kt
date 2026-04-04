package com.msda.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object BackgroundSyncScheduler {
    private const val IMMEDIATE_WORK_NAME = "msda_confirmation_sync_now"
    private const val ALARM_INTERVAL_MS = 1 * 60 * 1000L
    private const val ALARM_REQUEST_CODE = 7001
    const val ACTION_BACKGROUND_SYNC_ALARM = "com.msda.android.ACTION_BACKGROUND_SYNC_ALARM"

    fun configure(context: Context) {
        if (!AppSettings.isBackgroundSyncEnabled(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
            cancelAlarm(context)
            return
        }

        enqueueNow(context)
        scheduleNextAlarm(context, ALARM_INTERVAL_MS)
    }

    fun enqueueNow(context: Context) {
        if (!AppSettings.isBackgroundSyncEnabled(context)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateRequest = OneTimeWorkRequestBuilder<ConfirmationBackgroundWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    fun scheduleNextAlarm(context: Context, delayMs: Long = ALARM_INTERVAL_MS) {
        if (!AppSettings.isBackgroundSyncEnabled(context)) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent(context)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent
        )
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
