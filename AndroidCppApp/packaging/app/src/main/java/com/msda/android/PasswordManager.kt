package com.msda.android

import android.content.Context
import android.content.SharedPreferences

object PasswordManager {
    private const val PREFS_NAME = "msda_passwords"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        val p = getPrefs(context)
        p.edit().putString(accountName, password).apply()
    }

    fun getPassword(context: Context, accountName: String): String? {
        val p = getPrefs(context)
        return p.getString(accountName, null)
    }

    fun hasPassword(context: Context, accountName: String): Boolean {
        return getPassword(context, accountName) != null
    }

    fun deletePassword(context: Context, accountName: String) {
        val p = getPrefs(context)
        p.edit().remove(accountName).apply()
    }

    fun clearAll(context: Context) {
        val p = getPrefs(context)
        p.edit().clear().apply()
    }
}
