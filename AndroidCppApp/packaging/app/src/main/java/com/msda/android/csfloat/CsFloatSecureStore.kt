package com.msda.android.csfloat

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

/**
 * Per-account CSFloat API keys encrypted with Android Keystore (AES-GCM),
 * matching [com.msda.android.PasswordManager] / [com.msda.android.SessionStore].
 */
object CsFloatSecureStore {
    private const val PREFS = "msda_csfloat_secrets"
    private const val TAG = "CsFloatSecureStore"
    private const val KEYSTORE_ALIAS = "csfloat_api_master_key"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_PREFIX = "api_key_"

    private fun getMasterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        return try {
            if (ks.containsAlias(KEYSTORE_ALIAS)) {
                (ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Master key load failed, regenerating", e)
            try {
                ks.deleteEntry(KEYSTORE_ALIAS)
            } catch (_: Exception) {
            }
            generateKey()
        }
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .also { it.init(spec) }.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val key = getMasterKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String): String? {
        return try {
            val data = Base64.decode(ciphertext, Base64.NO_WRAP)
            if (data.size < 12) return null
            val iv = data.copyOfRange(0, 12)
            val encrypted = data.copyOfRange(12, data.size)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decrypt failed", e)
            null
        }
    }

    fun saveApiKey(context: Context, steamId: String, apiKey: String) {
        if (steamId.isBlank()) return
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(context, steamId)
            return
        }
        try {
            val blob = encrypt(trimmed)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("$KEY_PREFIX$steamId", blob)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save CSFloat API key for $steamId", e)
        }
    }

    fun getApiKey(context: Context, steamId: String): String? {
        if (steamId.isBlank()) return null
        return try {
            val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("$KEY_PREFIX$steamId", null) ?: return null
            decrypt(blob)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load CSFloat API key for $steamId", e)
            null
        }
    }

    fun hasApiKey(context: Context, steamId: String): Boolean {
        return !getApiKey(context, steamId).isNullOrBlank()
    }

    fun clearApiKey(context: Context, steamId: String) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX$steamId")
            .apply()
    }
}
