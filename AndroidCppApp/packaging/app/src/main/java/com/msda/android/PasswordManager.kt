package com.msda.android

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PasswordManager {
    private const val PREFS_NAME = "msda_encrypted_passwords"

    private var encryptedPrefs: SharedPreferences? = null

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        if (encryptedPrefs == null) {
            try {
                val spec = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                encryptedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    spec,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                // Fallback to regular SharedPreferences if encryption fails
                encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
        return encryptedPrefs!!
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(accountName, password).apply()
    }

    fun getPassword(context: Context, accountName: String): String? {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(accountName, null)
    }

    fun hasPassword(context: Context, accountName: String): Boolean {
        return getPassword(context, accountName) != null
    }

    fun deletePassword(context: Context, accountName: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().remove(accountName).apply()
    }

    fun clearAll(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().clear().apply()
    }
}
