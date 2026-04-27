package com.msda.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import android.util.Log
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordManager {
    private const val ALGORITHM = "AES"
    private const val MODE = "GCM"
    private const val PADDING = "NoPadding"
    private const val TAG = "PasswordManager"
    private const val DB_NAME = "steam_passwords.db"

    fun getDeviceEncryptionKey(context: Context): SecretKey {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, 0, keyBytes.size, ALGORITHM)
    }

    fun encryptPassword(context: Context, password: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("$ALGORITHM/$MODE/$PADDING")
        val key = getDeviceEncryptionKey(context)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        return Pair(encrypted, iv)
    }

    fun decryptPassword(context: Context, encrypted: ByteArray, iv: ByteArray): String? {
        return try {
            val cipher = Cipher.getInstance("$ALGORITHM/$MODE/$PADDING")
            val key = getDeviceEncryptionKey(context)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            null
        }
    }

    fun savePassword(context: Context, accountName: String, password: String) {
        val (encrypted, iv) = encryptPassword(context, password)
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS passwords (
                account_name TEXT PRIMARY KEY,
                encrypted_password BLOB,
                iv BLOB,
                created_at INTEGER
            )
        """)
        db.execSQL(
            "INSERT OR REPLACE INTO passwords VALUES (?, ?, ?, ?)",
            arrayOf(accountName, encrypted, iv, System.currentTimeMillis())
        )
        db.close()
    }

    fun getPassword(context: Context, accountName: String): String? {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery(
            "SELECT encrypted_password, iv FROM passwords WHERE account_name = ?",
            arrayOf(accountName)
        )
        val result = if (cursor.moveToFirst()) {
            val encrypted = cursor.getBlob(0)
            val iv = cursor.getBlob(1)
            decryptPassword(context, encrypted, iv)
        } else {
            null
        }
        cursor.close()
        db.close()
        return result
    }

    fun deletePassword(context: Context, accountName: String) {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        db.delete("passwords", "account_name = ?", arrayOf(accountName))
        db.close()
    }

    fun listSavedAccounts(context: Context): List<String> {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        val cursor = db.rawQuery("SELECT account_name FROM passwords", null)
        val accounts = mutableListOf<String>()
        while (cursor.moveToNext()) {
            accounts.add(cursor.getString(0))
        }
        cursor.close()
        db.close()
        return accounts
    }
}
