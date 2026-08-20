package com.msda.android.csfloat

import android.content.Context

/**
 * Non-secret CSFloat prefs (opt-in + poll interval). API keys live in [CsFloatSecureStore].
 */
object CsFloatAccountSettings {
    private const val PREFS = "msda_csfloat_ui"
    private const val KEY_ENABLED_PREFIX = "enabled_"
    private const val KEY_INTERVAL_PREFIX = "interval_min_"

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

    fun clearAccount(context: Context, steamId: String) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_ENABLED_PREFIX$steamId")
            .remove("$KEY_INTERVAL_PREFIX$steamId")
            .apply()
        CsFloatSecureStore.clearApiKey(context, steamId)
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
