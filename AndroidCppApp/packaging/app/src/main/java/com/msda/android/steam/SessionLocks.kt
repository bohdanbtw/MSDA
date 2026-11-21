package com.msda.android.steam

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide serialization for session state.
 *
 * A single [Mutex] per Steam ID is shared by every renewal, login and persistence path, so two
 * writers can never rotate the same account's tokens concurrently. Helpers named `...Unlocked`
 * are only safe to call while [withLock] is held for that Steam ID.
 */
object SessionLocks {
    private val bySteamId = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(steamId: String, action: suspend () -> T): T {
        return bySteamId.getOrPut(steamId) { Mutex() }.withLock { action() }
    }
}
