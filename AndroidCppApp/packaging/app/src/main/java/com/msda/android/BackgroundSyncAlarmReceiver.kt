package com.msda.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BackgroundSyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BackgroundSyncScheduler.ACTION_BACKGROUND_SYNC_ALARM) {
            return
        }

        BackgroundSyncScheduler.enqueueNow(context)
        BackgroundSyncScheduler.scheduleNextAlarm(context)
    }
}
