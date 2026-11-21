package com.msda.android

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tracks how many of our activities are visible. When the last one stops the
 * app is considered backgrounded and the in-memory unlock flag is cleared, so
 * returning from Recents always shows the lock screen again.
 */
class MsdaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = AppLockState.onActivityStarted()
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = AppLockState.onActivityStopped()
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
