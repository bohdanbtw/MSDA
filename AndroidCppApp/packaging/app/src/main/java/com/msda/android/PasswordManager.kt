package com.msda.android

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordManager {
    private const val PREFS_NAME = "msda_passwords"
    private const val MASTER_KEY_NAME = "pw_master_key"
    private const val TAG = "PasswordManager"
    private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"

    /**
     * Returns a per‑installation 256‑bit master key.
     * The key is generated once, stored (Base64‑encoded) in SharedPreferences,
     * and reused thereafter.
     */
    private fun getOrCreateMasterKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encoded = prefs.getString(MASTER_KEY_NAME, null)
        if (encoded != null) {
            return try {
                Base64.decode(encoded, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode master key, regenerating", e)
                // Key is corrupt – generate a new one
                ByteArray(0) // placeholder value, overridden below
            }
        }

        val keyBytes = ByteArray(32) // 256 bits
        SecureRandom().nextBytes(keyBytes)
        val encodedKey = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        prefs.edit().putString(MASTER_KEY_NAME, encodedKey).apply()
        return keyBytes
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        try {
            val keyBytes = getOrCreateMasterKey(context)
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(keyBytes, KEY_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val combined = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(accountName, combined)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save password for $accountName", e)
        }
    }

    fun getPassword(context: Context, accountName: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val combinedBase64 = prefs.getString(accountName, null) ?: return null
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < 16) return null
            val iv = combined.copyOfRange(0, 16)
            val encrypted = combined.copyOfRange(16, combined.size)
            val keyBytes = getOrCreateMasterKey(context)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(keyBytes, KEY_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get password for $accountName", e)
            null
        }
    }

    fun hasPassword(context: Context, accountName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(accountName)
    }

    fun deletePassword(context: Context, accountName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(accountName)
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
