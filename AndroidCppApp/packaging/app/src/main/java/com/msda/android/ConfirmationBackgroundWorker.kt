package com.msda.android

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Disabled: background confirmation polling caused Steam rate limits. */
class ConfirmationBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        BackgroundSyncScheduler.disable(applicationContext)
        return Result.success()
    }
}
