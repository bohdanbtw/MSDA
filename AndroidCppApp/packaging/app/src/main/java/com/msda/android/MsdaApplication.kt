package com.msda.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Tracks how many of our activities are visible. When the last one stops the
 * app is considered backgrounded and the in-memory unlock flag is cleared, so
 * returning from Recents always shows the lock screen again.
 *
 * A short grace period is used before the actual relock: rotation and
 * [Activity.recreate] stop the old activity and immediately start a new one,
 * and must not kick the user back to the lock screen. A genuine background
 * transition has no follow-up start, so the deferred relock still fires.
 */
class MsdaApplication : Application() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRelock: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = onActivityStartedInternal()
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = onActivityStoppedInternal(activity)
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun onActivityStartedInternal() {
        // Another activity became visible before the deferred relock ran — this
        // is a configuration change / recreate / normal activity switch.
        cancelPendingRelock()
        AppLockState.onActivityStarted()
    }

    private fun onActivityStoppedInternal(activity: Activity) {
        // Keep the visibility counter balanced across the stop/start pair even
        // during rotation, but never relock synchronously from a stop callback.
        AppLockState.onActivityStopped(clearUnlock = false)
        if (AppLockState.startedActivityCount() != 0) return

        cancelPendingRelock()
        val relock = Runnable {
            pendingRelock = null
            // A start that arrived in the meantime cancels this runnable.
            if (AppLockState.startedActivityCount() == 0) {
                AppLockState.lock()
            }
        }
        pendingRelock = relock
        handler.postDelayed(relock, RELOCK_GRACE_MS)
    }

    private fun cancelPendingRelock() {
        pendingRelock?.let { handler.removeCallbacks(it) }
        pendingRelock = null
    }

    private companion object {
        const val RELOCK_GRACE_MS = 700L
    }
}
