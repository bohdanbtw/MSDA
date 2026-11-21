package com.msda.android

/**
 * Process-only app-lock state.
 *
 * This is deliberately never persisted to disk: a new process always starts
 * locked, and the flag is cleared as soon as no activity of the app is visible
 * any more (i.e. the user left for the Home screen / Recents).
 */
object AppLockState {
    @Volatile
    var unlocked: Boolean = false
        private set

    private var startedActivities = 0

    /** Called after a successful PIN or biometric authentication. */
    fun unlock() {
        unlocked = true
    }

    /** Forces the app back into the locked state. */
    fun lock() {
        unlocked = false
    }

    fun onActivityStarted() {
        synchronized(this) {
            startedActivities++
        }
    }

    fun onActivityStopped() {
        synchronized(this) {
            if (startedActivities > 0) {
                startedActivities--
            }
            if (startedActivities == 0) {
                unlocked = false
            }
        }
    }

    /** Resets both the flag and the visibility counter (used by tests). */
    internal fun reset() {
        synchronized(this) {
            startedActivities = 0
            unlocked = false
        }
    }

    internal fun startedActivityCount(): Int = synchronized(this) { startedActivities }
}
