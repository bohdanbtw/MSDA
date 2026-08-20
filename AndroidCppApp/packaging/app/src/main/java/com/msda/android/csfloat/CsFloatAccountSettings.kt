package com.msda.android.csfloat

import android.content.Context

/**
 * Non-secret CSFloat prefs (opt-in + poll interval). API keys live in [CsFloatSecureStore].
 */
object CsFloatAccountSettings {
    private const val PREFS = "msda_csfloat_ui"
    private const val KEY_ENABLED_PREFIX = "enabled_"
    private const val KEY_INTERVAL_PREFIX = "interval_min_"
    private const val KEY_LAST_QUEUED_COUNT_PREFIX = "last_queued_count_"
    private const val KEY_LAST_QUEUED_BASELINED_PREFIX = "last_queued_baselined_"
    private const val KEY_LAST_CHECK_AT_PREFIX = "last_check_at_"

    /** WorkManager periodic floor is 15; we default higher for battery. */
    const val DEFAULT_INTERVAL_MINUTES = 30L
    const val MIN_INTERVAL_MINUTES = 15L
    const val MAX_INTERVAL_MINUTES = 240L

    fun isEnabled(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_ENABLED_PREFIX$steamId", false)
    }

    fun setEnabled(context: Context, steamId: String, enabled: Boolean) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$KEY_ENABLED_PREFIX$steamId", enabled)
            .apply()
    }

    fun getPollIntervalMinutes(context: Context, steamId: String): Long {
        if (steamId.isBlank()) return DEFAULT_INTERVAL_MINUTES
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("$KEY_INTERVAL_PREFIX$steamId", DEFAULT_INTERVAL_MINUTES)
        return raw.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    fun setPollIntervalMinutes(context: Context, steamId: String, minutes: Long) {
        if (steamId.isBlank()) return
        val clamped = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("$KEY_INTERVAL_PREFIX$steamId", clamped)
            .apply()
    }

    fun getLastQueuedCount(context: Context, steamId: String): Int {
        if (steamId.isBlank()) return 0
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("$KEY_LAST_QUEUED_COUNT_PREFIX$steamId", 0)
    }

    fun hasQueuedBaseline(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_LAST_QUEUED_BASELINED_PREFIX$steamId", false)
    }

    fun setLastQueuedCount(context: Context, steamId: String, count: Int, baselined: Boolean = true) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("$KEY_LAST_QUEUED_COUNT_PREFIX$steamId", count.coerceAtLeast(0))
            .putBoolean("$KEY_LAST_QUEUED_BASELINED_PREFIX$steamId", baselined)
            .putLong("$KEY_LAST_CHECK_AT_PREFIX$steamId", System.currentTimeMillis())
            .apply()
    }

    fun getLastCheckAtMs(context: Context, steamId: String): Long {
        if (steamId.isBlank()) return 0L
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("$KEY_LAST_CHECK_AT_PREFIX$steamId", 0L)
    }

    /** Drop last-check / queued baseline (status strip → Never). Does not touch enable or key. */
    fun clearCheckStatus(context: Context, steamId: String) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_LAST_QUEUED_COUNT_PREFIX$steamId")
            .remove("$KEY_LAST_QUEUED_BASELINED_PREFIX$steamId")
            .remove("$KEY_LAST_CHECK_AT_PREFIX$steamId")
            .apply()
    }

    fun clearAccount(context: Context, steamId: String) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_ENABLED_PREFIX$steamId")
            .remove("$KEY_INTERVAL_PREFIX$steamId")
            .remove("$KEY_LAST_QUEUED_COUNT_PREFIX$steamId")
            .remove("$KEY_LAST_QUEUED_BASELINED_PREFIX$steamId")
            .remove("$KEY_LAST_CHECK_AT_PREFIX$steamId")
            .apply()
        CsFloatSecureStore.clearApiKey(context, steamId)
        CsFloatNotifier.cancelForSteamId(context, steamId)
    }

    /** SteamIds that opted in (may still lack an API key). */
    fun enabledSteamIds(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(KEY_ENABLED_PREFIX) && value == true) {
                key.removePrefix(KEY_ENABLED_PREFIX).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    fun readySteamIds(context: Context): List<String> {
        return enabledSteamIds(context).filter { id ->
            CsFloatSecureStore.hasApiKey(context, id)
        }
    }
}
