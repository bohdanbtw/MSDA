package com.msda.android.steam

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Steam server-time alignment for Guard codes and confirmation HMAC (`t`/`k`).
 * Offset is cached so callers do not hit QueryTime per confirmation item.
 */
class TimeAligner(private val client: OkHttpClient) {
    companion object {
        private const val CACHE_TTL_MS = 5L * 60L * 1000L

        @Volatile
        private var offsetSeconds: Long = 0L

        @Volatile
        private var alignedAtLocalMs: Long = 0L

        fun hasFreshCache(): Boolean {
            val at = alignedAtLocalMs
            return at > 0L && (System.currentTimeMillis() - at) < CACHE_TTL_MS
        }

        /** Local wall clock adjusted by last known Steam offset (0 if never aligned). */
        fun cachedAlignedEpochSeconds(): Long {
            return (System.currentTimeMillis() / 1000L) + offsetSeconds
        }

        fun invalidateCache() {
            alignedAtLocalMs = 0L
            offsetSeconds = 0L
        }

        fun currentOffsetSeconds(): Long = offsetSeconds
    }

    suspend fun alignedEpochSeconds(forceRefresh: Boolean = false): Long {
        if (!forceRefresh && hasFreshCache()) {
            return cachedAlignedEpochSeconds()
        }

        val localBefore = System.currentTimeMillis() / 1000L
        val request = Request.Builder()
            .url("https://api.steampowered.com/ITwoFactorService/QueryTime/v0001")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return if (hasFreshCache()) cachedAlignedEpochSeconds() else localBefore
            }
            val serverTime = JSONObject(body)
                .optJSONObject("response")
                ?.optLong("server_time", 0L)
                ?: 0L
            if (serverTime <= 0L) {
                return if (hasFreshCache()) cachedAlignedEpochSeconds() else localBefore
            }
            offsetSeconds = serverTime - localBefore
            alignedAtLocalMs = System.currentTimeMillis()
            serverTime
        } catch (_: Throwable) {
            if (hasFreshCache()) cachedAlignedEpochSeconds() else localBefore
        }
    }

    /** For ConfirmationService paths that already run inside runBlocking / workers. */
    fun alignedEpochSecondsBlocking(forceRefresh: Boolean = false): Long {
        return runBlocking { alignedEpochSeconds(forceRefresh) }
    }
}
