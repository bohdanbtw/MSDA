package com.msda.android

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordManager {
    private const val PREFS_NAME = "msda_passwords"
    private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"

    // Use a static seed to derive a deterministic AES key (not tied to device ID)
    private val KEY_BYTES: ByteArray by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("com.msda.android.PasswordVault.salt".toByteArray(Charsets.UTF_8))
        digest.digest() // 256-bit key
    }

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        val p = getPrefs(context)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val keySpec = SecretKeySpec(KEY_BYTES, KEY_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val combined = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        p.edit().putString(accountName, combined).apply()
    }

    fun getPassword(context: Context, accountName: String): String? {
        val p = getPrefs(context)
        val combinedBase64 = p.getString(accountName, null) ?: return null
        return try {
            val combined = Base64.decode(combinedBase64, Base64.NO_WRAP)
            if (combined.size < 16) return null
            val iv = combined.copyOfRange(0, 16)
            val encrypted = combined.copyOfRange(16, combined.size)
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val keySpec = SecretKeySpec(KEY_BYTES, KEY_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun hasPassword(context: Context, accountName: String): Boolean {
        val p = getPrefs(context)
        return p.contains(accountName)
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
