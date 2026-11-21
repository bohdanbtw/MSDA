package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLockStateTest {
    @Before
    fun setUp() {
        AppLockState.reset()
    }

    @Test
    fun freshProcessStartsLocked() {
        assertFalse(AppLockState.unlocked)
        assertEquals(0, AppLockState.startedActivityCount())
    }

    @Test
    fun unlockAndLockToggleTheFlag() {
        AppLockState.unlock()
        assertTrue(AppLockState.unlocked)

        AppLockState.lock()
        assertFalse(AppLockState.unlocked)
    }

    @Test
    fun stoppingLastVisibleActivityRelocks() {
        AppLockState.onActivityStarted()
        AppLockState.unlock()
        assertTrue(AppLockState.unlocked)

        AppLockState.onActivityStopped()
        assertFalse(AppLockState.unlocked)
    }

    @Test
    fun switchingBetweenActivitiesKeepsAppUnlocked() {
        AppLockState.onActivityStarted()
        AppLockState.unlock()
        AppLockState.onActivityStarted()

        AppLockState.onActivityStopped()
        assertTrue(AppLockState.unlocked)

        AppLockState.onActivityStopped()
        assertFalse(AppLockState.unlocked)
    }

    @Test
    fun strayStopCallbacksDoNotUnderflowTheCounter() {
        AppLockState.onActivityStopped()
        assertEquals(0, AppLockState.startedActivityCount())

        AppLockState.onActivityStarted()
        AppLockState.unlock()
        AppLockState.onActivityStopped()
        AppLockState.onActivityStopped()
        assertEquals(0, AppLockState.startedActivityCount())
        assertFalse(AppLockState.unlocked)
    }
}
