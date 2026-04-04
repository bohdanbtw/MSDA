package com.msda.android

import android.content.Context

object AppSettings {
    private const val PREFS = "msda_ui"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
    private const val KEY_PUSH_CONFIRMATIONS_ENABLED = "push_confirmations_enabled"

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun isBackgroundSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
    }

    fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled).apply()
    }

    fun isPushConfirmationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PUSH_CONFIRMATIONS_ENABLED, true)
    }

    fun setPushConfirmationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PUSH_CONFIRMATIONS_ENABLED, enabled).apply()
    }
}
