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

/** Signals that an existing Keystore key could not be read. */
class MasterKeyReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PasswordManager {
    private const val PREFS_NAME = "msda_passwords"
    private const val TAG = "PasswordManager"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val KEYSTORE_ALIAS = "pw_master_key"

    /**
     * Returns the per-installation AES key from Android Keystore.
     *
     * A key is generated only on genuine first use (no alias present). Once an alias
     * exists, a read failure is surfaced as [MasterKeyReadException] instead of being
     * "fixed" by deleting and regenerating the key — doing so silently orphans every
     * ciphertext that was encrypted with the original key.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return generateKey()
        }

        val entry = try {
            keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        } catch (e: Exception) {
            throw MasterKeyReadException("Existing master key could not be read", e)
        }
        return entry?.secretKey
            ?: throw MasterKeyReadException("Existing master key entry is missing or not a secret key")
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

    /**
     * Encrypts a secret with the Keystore-backed master key. The result is a
     * Base64 string of the 12-byte IV followed by the AES-GCM ciphertext (tag
     * included), so callers can persist a single opaque value.
     */
    fun encrypt(plain: String): String? {
        return try {
            val secretKey = getOrCreateMasterKey()
            val iv = ByteArray(12) // 96-bit IV for GCM
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        } catch (e: MasterKeyReadException) {
            Log.e(TAG, "Cannot encrypt: existing master key is unreadable", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt value", e)
            null
        }
    }

    /** Reverses [encrypt]; returns null on corrupt data or Keystore errors. */
    fun decrypt(combinedBase64: String): String? {
        return try {
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: MasterKeyReadException) {
            // Surface keystore read failures distinctly: callers must not treat
            // intact-but-unreadable ciphertext as a missing value and wipe it.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt value", e)
            null
        }
    }

    /** All key operations use this single normalized form so casing never forks storage. */
    private fun normalizeAccountKey(accountName: String): String = accountName.trim().lowercase()

    fun savePassword(context: Context, accountName: String, password: String) {
        val key = normalizeAccountKey(accountName)
        val combined = encrypt(password)
        if (combined == null) {
            Log.e(TAG, "Failed to save password for $accountName")
            return
        }
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, combined)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save password for $accountName", e)
        }
    }

    fun getPassword(context: Context, accountName: String): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = normalizeAccountKey(accountName)
            // Prefer the normalized key; fall back to a legacy mixed-case entry.
            val storedKey = prefs.all.keys.firstOrNull { it.equals(key, ignoreCase = true) } ?: key
            val combinedBase64 = prefs.getString(storedKey, null) ?: return null
            decrypt(combinedBase64)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get password for $accountName", e)
            null
        }
    }

    fun hasPassword(context: Context, accountName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = normalizeAccountKey(accountName)
        return prefs.contains(key) || prefs.all.keys.any { it.equals(key, ignoreCase = true) }
    }

    fun deletePassword(context: Context, accountName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = normalizeAccountKey(accountName)
        val keysToRemove = prefs.all.keys.filter { it.equals(key, ignoreCase = true) }.toMutableSet()
        keysToRemove += key
        prefs.edit().apply {
            keysToRemove.forEach { remove(it) }
        }.apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
