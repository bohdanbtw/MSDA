package com.msda.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BackgroundSyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Background confirmation polling is disabled.
        BackgroundSyncScheduler.disable(context)
    }
}
