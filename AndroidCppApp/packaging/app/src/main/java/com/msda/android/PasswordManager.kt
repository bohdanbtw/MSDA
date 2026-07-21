package com.msda.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordManager {
    private const val PREFS_NAME = "msda_passwords"
    private const val TAG = "PasswordManager"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEYSTORE_ALIAS = "pw_master_key"

    /**
     * Returns a per‑installation AES key stored in Android Keystore.
     * The key is generated once and persists across app upgrades.
     * If retrieval/generation fails, a fallback key is generated,
     * which may render previously stored passwords unreadable.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        return try {
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey ?: throw IllegalStateException("Missing key entry")
            } else {
                generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load master key, generating new one", e)
            // Delete any existing alias before generating a new one
            try { keyStore.deleteEntry(KEYSTORE_ALIAS) } catch (_: Exception) {}
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        try {
            val secretKey = getOrCreateMasterKey()
            val iv = ByteArray(12) // 96-bit IV for GCM
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val combined = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(accountName, combined)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save password for $accountName", e)
        }
    }

    fun getPassword(context: Context, accountName: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedKey = prefs.all.keys.firstOrNull { it.equals(accountName, ignoreCase = true) } ?: accountName
            val combinedBase64 = prefs.getString(storedKey, null) ?: return null
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
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
