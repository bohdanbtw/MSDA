package com.msda.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom
import org.json.JSONObject

data class StoredSteamSession(
    val steamLoginSecure: String,
    val sessionId: String,
    val refreshToken: String = "",
    val accessToken: String = "",
    val accountName: String = "",
    /** Expiry timestamp of the access token in epoch-milliseconds (0 = unknown). */
    val sessionExpiresAtMs: Long = 0L
)

object SessionStore {
    private const val PREFS = "msda_sessions_v2"
    private const val TAG = "SessionStore"
    private const val KEYSTORE_ALIAS = "session_master_key"
    private const val CIPHER = "AES/GCM/NoPadding"

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
            try { ks.deleteEntry(KEYSTORE_ALIAS) } catch (_: Exception) {}
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

    fun saveSession(context: Context, steamId: String, session: StoredSteamSession) {
        try {
            val json = JSONObject().apply {
                put("steamLoginSecure", session.steamLoginSecure)
                put("sessionid", session.sessionId)
                put("refreshToken", session.refreshToken)
                put("accessToken", session.accessToken)
                put("accountName", session.accountName)
                put("sessionExpiresAtMs", session.sessionExpiresAtMs)
            }.toString()
            val blob = encrypt(json)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("s_$steamId", blob).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save session for $steamId", e)
        }
    }

    fun loadSession(context: Context, steamId: String): StoredSteamSession? {
        return try {
            val blob = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("s_$steamId", null) ?: return migrateLegacy(context, steamId)
            val plain = decrypt(blob) ?: return migrateLegacy(context, steamId)
            val j = JSONObject(plain)
            val login = j.optString("steamLoginSecure", "").takeIf { it.isNotBlank() } ?: return null
            val sid = j.optString("sessionid", "").takeIf { it.isNotBlank() } ?: return null
            StoredSteamSession(
                steamLoginSecure = login,
                sessionId = sid,
                refreshToken = j.optString("refreshToken", ""),
                accessToken = j.optString("accessToken", ""),
                accountName = j.optString("accountName", ""),
                sessionExpiresAtMs = j.optLong("sessionExpiresAtMs", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session for $steamId", e)
            null
        }
    }

    /** Migrate plaintext prefs from the old schema (v1) into the encrypted store. */
    private fun migrateLegacy(context: Context, steamId: String): StoredSteamSession? {
        return try {
            val old = context.getSharedPreferences("msda_sessions", Context.MODE_PRIVATE)
            val login = old.getString("$steamId.steamLoginSecure", null)?.takeIf { it.isNotBlank() } ?: return null
            val sid   = old.getString("$steamId.sessionid", null)?.takeIf { it.isNotBlank() } ?: return null
            val session = StoredSteamSession(
                steamLoginSecure = login,
                sessionId = sid,
                refreshToken = old.getString("$steamId.refreshToken", "") ?: "",
                accessToken  = old.getString("$steamId.accessToken",  "") ?: "",
                accountName  = old.getString("$steamId.accountName",  "") ?: ""
            )
            // Persist into encrypted store and wipe old entry
            saveSession(context, steamId, session)
            old.edit()
                .remove("$steamId.steamLoginSecure")
                .remove("$steamId.sessionid")
                .remove("$steamId.refreshToken")
                .remove("$steamId.accessToken")
                .remove("$steamId.accountName")
                .apply()
            session
        } catch (e: Exception) {
            null
        }
    }

    fun getAccountName(context: Context, steamId: String): String? =
        loadSession(context, steamId)?.accountName?.takeIf { it.isNotBlank() }

    /** True if the stored session is expired (or expiry unknown and token may be stale). */
    fun isSessionExpired(context: Context, steamId: String): Boolean {
        val session = loadSession(context, steamId) ?: return true
        if (session.sessionExpiresAtMs <= 0L) return false
        val skewMs = 5 * 60 * 1000L
        return System.currentTimeMillis() >= (session.sessionExpiresAtMs - skewMs)
    }
}
