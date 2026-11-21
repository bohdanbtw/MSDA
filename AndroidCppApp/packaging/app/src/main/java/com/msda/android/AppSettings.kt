package com.msda.android

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AccountProxyConfig(
    val enabled: Boolean,
    val type: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)

object AppSettings {
    private const val PREFS = "msda_ui"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_APP_PIN_HASH = "app_pin_hash"
    private const val KEY_APP_PIN_SALT = "app_pin_salt"
    private const val KEY_APP_PIN_VERSION = "app_pin_version"
    private const val KEY_APP_PIN_ITERATIONS = "app_pin_iterations"
    private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
    private const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
    private const val KEY_BIOMETRIC_UNLOCK_ENABLED = "biometric_unlock_enabled"
    private const val KEY_MARKET_AUTO_CONFIRM_PREFIX = "market_auto_confirm_"
    private const val KEY_TRADE_AUTO_CONFIRM_PREFIX = "trade_auto_confirm_"
    private const val KEY_GIFT_TRADE_AUTO_CONFIRM_PREFIX = "gift_trade_auto_confirm_"
    private const val KEY_PROXY_ENABLED_PREFIX = "proxy_enabled_"
    private const val KEY_PROXY_TYPE_PREFIX = "proxy_type_"
    private const val KEY_PROXY_HOST_PREFIX = "proxy_host_"
    private const val KEY_PROXY_PORT_PREFIX = "proxy_port_"
    private const val KEY_PROXY_USERNAME_PREFIX = "proxy_username_"
    private const val KEY_PROXY_PASSWORD_PREFIX = "proxy_password_"
    private const val KEY_DEFAULT_PROXY_ENABLED = "default_proxy_enabled"
    private const val KEY_DEFAULT_PROXY_TYPE = "default_proxy_type"
    private const val KEY_DEFAULT_PROXY_HOST = "default_proxy_host"
    private const val KEY_DEFAULT_PROXY_PORT = "default_proxy_port"
    private const val KEY_DEFAULT_PROXY_USERNAME = "default_proxy_username"
    private const val KEY_DEFAULT_PROXY_PASSWORD = "default_proxy_password"
    private const val KEY_ACCOUNT_LABEL_PREFIX = "account_label_"
    private const val KEY_WIDGET_STEAM_ID_PREFIX = "widget_steam_id_"
    private const val KEY_WIDGET_ACCOUNT_INDEX_PREFIX = "widget_account_index_"
    private const val KEY_WIDGET_ACCOUNT_NAME_PREFIX = "widget_account_name_"
    private const val KEY_PERMANENT_DEVICE_ID = "permanent_device_id"
    private const val KEY_USE_NEBULA_SESSION_STACK = "use_nebula_session_stack"
    // Prefix distinguishes Keystore ciphertext from legacy plaintext proxy passwords.
    private const val ENCRYPTED_VALUE_PREFIX = "enc:"

    // PIN protection: legacy hashes (version 1) are one-shot SHA-256 digests and are
    // transparently upgraded to PBKDF2 on the next successful unlock.
    private const val PIN_KDF_VERSION = 2
    private const val PIN_ITERATIONS = 120_000
    private const val PIN_SALT_BYTES = 16
    private const val PIN_KEY_BITS = 256
    private const val BASE_PIN_BACKOFF_MS = 1_000L
    private const val MAX_PIN_BACKOFF_MS = 300_000L

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).commit()
    }

    /** Single source of truth for applying the stored theme to any activity. */
    fun applyTheme(context: Context) {
        when (getThemeMode(context)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    fun isMarketAutoConfirmEnabled(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_MARKET_AUTO_CONFIRM_PREFIX$steamId", false)
    }

    fun setMarketAutoConfirmEnabled(context: Context, steamId: String, enabled: Boolean) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$KEY_MARKET_AUTO_CONFIRM_PREFIX$steamId", enabled)
            .apply()
    }

    fun isTradeAutoConfirmEnabled(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_TRADE_AUTO_CONFIRM_PREFIX$steamId", false)
    }

    fun setTradeAutoConfirmEnabled(context: Context, steamId: String, enabled: Boolean) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$KEY_TRADE_AUTO_CONFIRM_PREFIX$steamId", enabled)
            .apply()
    }

    fun isGiftTradeAutoConfirmEnabled(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_GIFT_TRADE_AUTO_CONFIRM_PREFIX$steamId", false)
    }

    fun setGiftTradeAutoConfirmEnabled(context: Context, steamId: String, enabled: Boolean) {
        if (steamId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$KEY_GIFT_TRADE_AUTO_CONFIRM_PREFIX$steamId", enabled)
            .apply()
    }

    fun isAnyAutoConfirmEnabled(context: Context, steamId: String): Boolean {
        if (steamId.isBlank()) return false
        return isMarketAutoConfirmEnabled(context, steamId) ||
            isTradeAutoConfirmEnabled(context, steamId) ||
            isGiftTradeAutoConfirmEnabled(context, steamId)
    }

    fun hasPinLock(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_PIN_HASH, null).isNullOrBlank()
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return hasPinLock(context)
    }

    fun setPinLock(context: Context, pin: String) {
        require(isValidPin(pin))

        storePinHash(context, generateSalt(), pin)
        clearPinFailures(context)
    }

    fun clearPinLock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_PIN_HASH)
            .remove(KEY_APP_PIN_SALT)
            .remove(KEY_APP_PIN_VERSION)
            .remove(KEY_APP_PIN_ITERATIONS)
            .putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
            .apply()
        clearPinFailures(context)
    }

    fun verifyPinLock(context: Context, pin: String): Boolean {
        if (!isValidPin(pin)) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltValue = prefs.getString(KEY_APP_PIN_SALT, null).orEmpty()
        val storedHash = prefs.getString(KEY_APP_PIN_HASH, null).orEmpty()
        if (saltValue.isBlank() || storedHash.isBlank()) {
            return false
        }

        if (prefs.getInt(KEY_APP_PIN_VERSION, 1) < PIN_KDF_VERSION) {
            // Legacy installs stored a UUID string salt and a single SHA-256 digest.
            // Verify with the old scheme, then upgrade the stored material so the
            // plaintext-fast hash is gone after the first successful unlock.
            if (!hashPinLegacy(pin, saltValue).equals(storedHash, ignoreCase = true)) {
                return false
            }
            storePinHash(context, generateSalt(), pin)
            return true
        }

        val salt = fromHex(saltValue) ?: return false
        val iterations = prefs.getInt(KEY_APP_PIN_ITERATIONS, PIN_ITERATIONS)
        if (iterations <= 0) {
            return false
        }
        return derivePinHash(pin, salt, iterations).equals(storedHash, ignoreCase = true)
    }

    fun isBiometricUnlockEnabled(context: Context): Boolean {
        return hasPinLock(context) && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
    }

    fun setBiometricUnlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, enabled && hasPinLock(context))
            .apply()
    }

    fun isValidPin(pin: String): Boolean {
        return pin.length == 4 && pin.all { it.isDigit() }
    }

    private fun derivePinHash(pin: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PIN_KEY_BITS)
        return try {
            val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            toHex(hash)
        } finally {
            spec.clearPassword()
        }
    }

    // Kept only to verify and migrate PINs saved by older app versions.
    private fun hashPinLegacy(pin: String, salt: String): String {
        return toHex(MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()))
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(PIN_SALT_BYTES).also { SecureRandom().nextBytes(it) }
    }

    private fun storePinHash(context: Context, salt: ByteArray, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_PIN_SALT, toHex(salt))
            .putString(KEY_APP_PIN_HASH, derivePinHash(pin, salt, PIN_ITERATIONS))
            .putInt(KEY_APP_PIN_VERSION, PIN_KDF_VERSION)
            .putInt(KEY_APP_PIN_ITERATIONS, PIN_ITERATIONS)
            .apply()
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun fromHex(value: String): ByteArray? {
        if (value.length % 2 != 0) return null
        val bytes = ByteArray(value.length / 2)
        for (i in bytes.indices) {
            val high = Character.digit(value[i * 2], 16)
            val low = Character.digit(value[i * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            bytes[i] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    // ---------- Failed-attempt backoff (shared with LockActivity) ----------
    fun getPinFailedAttempts(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PIN_FAILED_ATTEMPTS, 0)
    }

    fun getPinLockoutRemainingMillis(context: Context): Long {
        val until = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
        return maxOf(0L, until - System.currentTimeMillis())
    }

    fun recordPinFailure(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val attempts = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1
        // 1s, 2s, 4s, 8s ... capped at 5 minutes. The exponent is capped so a very
        // large counter can never overflow the shift.
        val exponent = minOf(attempts - 1, 20)
        val backoffMs = minOf(BASE_PIN_BACKOFF_MS shl exponent, MAX_PIN_BACKOFF_MS)
        prefs.edit()
            .putInt(KEY_PIN_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_PIN_LOCKOUT_UNTIL, System.currentTimeMillis() + backoffMs)
            .apply()
    }

    fun clearPinFailures(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PIN_FAILED_ATTEMPTS)
            .remove(KEY_PIN_LOCKOUT_UNTIL)
            .apply()
    }

    /**
     * Encrypts a proxy password with the same Keystore-backed AES-GCM helper used
     * for account passwords. Fails closed: if encryption is unavailable, no
     * plaintext is written to disk.
     */
    private fun encodeProxyPassword(password: String): String {
        if (password.isEmpty()) return ""
        val encrypted = PasswordManager.encrypt(password) ?: return ""
        return "$ENCRYPTED_VALUE_PREFIX$encrypted"
    }

    /**
     * Decrypts a stored proxy password. Unencrypted values left by older builds
     * are migrated on first read; values that claim to be encrypted but cannot
     * be decrypted are dropped rather than treated as plaintext.
     */
    private fun decodeProxyPassword(prefs: SharedPreferences, key: String): String {
        val stored = prefs.getString(key, null).orEmpty()
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(ENCRYPTED_VALUE_PREFIX)) {
            prefs.edit().putString(key, encodeProxyPassword(stored)).apply()
            return stored
        }
        val decrypted = PasswordManager.decrypt(stored.removePrefix(ENCRYPTED_VALUE_PREFIX))
        if (decrypted != null) return decrypted
        prefs.edit().remove(key).apply()
        return ""
    }

    fun getAccountProxyConfig(context: Context, steamId: String): AccountProxyConfig {
        if (steamId.isBlank()) {
            return AccountProxyConfig(false, "http", "", 0, "", "")
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccountProxyConfig(
            enabled = prefs.getBoolean("$KEY_PROXY_ENABLED_PREFIX$steamId", false),
            type = prefs.getString("$KEY_PROXY_TYPE_PREFIX$steamId", "http")?.lowercase().orEmpty().ifBlank { "http" },
            host = prefs.getString("$KEY_PROXY_HOST_PREFIX$steamId", "").orEmpty(),
            port = prefs.getInt("$KEY_PROXY_PORT_PREFIX$steamId", 0),
            username = prefs.getString("$KEY_PROXY_USERNAME_PREFIX$steamId", "").orEmpty(),
            password = decodeProxyPassword(prefs, "$KEY_PROXY_PASSWORD_PREFIX$steamId")
        )
    }

    fun setAccountProxyConfig(context: Context, steamId: String, config: AccountProxyConfig) {
        if (steamId.isBlank()) return

        val normalizedType = if (config.type.equals("socks", ignoreCase = true)) "socks" else "http"

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$KEY_PROXY_ENABLED_PREFIX$steamId", config.enabled)
            .putString("$KEY_PROXY_TYPE_PREFIX$steamId", normalizedType)
            .putString("$KEY_PROXY_HOST_PREFIX$steamId", config.host.trim())
            .putInt("$KEY_PROXY_PORT_PREFIX$steamId", config.port)
            .putString("$KEY_PROXY_USERNAME_PREFIX$steamId", config.username.trim())
            .putString("$KEY_PROXY_PASSWORD_PREFIX$steamId", encodeProxyPassword(config.password))
            .apply()
    }

    fun clearAccountProxyConfig(context: Context, steamId: String) {
        if (steamId.isBlank()) return

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PROXY_ENABLED_PREFIX$steamId")
            .remove("$KEY_PROXY_TYPE_PREFIX$steamId")
            .remove("$KEY_PROXY_HOST_PREFIX$steamId")
            .remove("$KEY_PROXY_PORT_PREFIX$steamId")
            .remove("$KEY_PROXY_USERNAME_PREFIX$steamId")
            .remove("$KEY_PROXY_PASSWORD_PREFIX$steamId")
            .apply()
    }

    fun getDefaultProxyConfig(context: Context): AccountProxyConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AccountProxyConfig(
            enabled = prefs.getBoolean(KEY_DEFAULT_PROXY_ENABLED, false),
            type = prefs.getString(KEY_DEFAULT_PROXY_TYPE, "http")?.lowercase().orEmpty().ifBlank { "http" },
            host = prefs.getString(KEY_DEFAULT_PROXY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_DEFAULT_PROXY_PORT, 0),
            username = prefs.getString(KEY_DEFAULT_PROXY_USERNAME, "").orEmpty(),
            password = decodeProxyPassword(prefs, KEY_DEFAULT_PROXY_PASSWORD)
        )
    }

    fun setDefaultProxyConfig(context: Context, config: AccountProxyConfig) {
        val normalizedType = if (config.type.equals("socks", ignoreCase = true)) "socks" else "http"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEFAULT_PROXY_ENABLED, config.enabled)
            .putString(KEY_DEFAULT_PROXY_TYPE, normalizedType)
            .putString(KEY_DEFAULT_PROXY_HOST, config.host.trim())
            .putInt(KEY_DEFAULT_PROXY_PORT, config.port)
            .putString(KEY_DEFAULT_PROXY_USERNAME, config.username.trim())
            .putString(KEY_DEFAULT_PROXY_PASSWORD, encodeProxyPassword(config.password))
            .apply()
    }

    fun clearDefaultProxyConfig(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEFAULT_PROXY_ENABLED)
            .remove(KEY_DEFAULT_PROXY_TYPE)
            .remove(KEY_DEFAULT_PROXY_HOST)
            .remove(KEY_DEFAULT_PROXY_PORT)
            .remove(KEY_DEFAULT_PROXY_USERNAME)
            .remove(KEY_DEFAULT_PROXY_PASSWORD)
            .apply()
    }

    /**
     * Per-account proxy wins when enabled and valid; otherwise falls back to the shared default.
     */
    fun resolveProxyConfig(context: Context, steamId: String): AccountProxyConfig {
        val account = getAccountProxyConfig(context, steamId)
        if (ProxyChecker.isConfigured(account)) {
            return account
        }
        return getDefaultProxyConfig(context)
    }

    fun getAccountLabel(context: Context, steamId: String): String {
        if (steamId.isBlank()) return ""
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_ACCOUNT_LABEL_PREFIX$steamId", "").orEmpty().trim()
    }

    fun setAccountLabel(context: Context, steamId: String, label: String) {
        if (steamId.isBlank()) return
        val normalized = label.trim()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (normalized.isEmpty()) {
            prefs.remove("$KEY_ACCOUNT_LABEL_PREFIX$steamId")
        } else {
            prefs.putString("$KEY_ACCOUNT_LABEL_PREFIX$steamId", normalized)
        }
        prefs.apply()
    }

    fun getWidgetSteamId(context: Context, appWidgetId: Int): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_WIDGET_STEAM_ID_PREFIX$appWidgetId", "").orEmpty()
    }

    fun getWidgetAccountIndex(context: Context, appWidgetId: Int): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("$KEY_WIDGET_ACCOUNT_INDEX_PREFIX$appWidgetId", -1)
    }

    fun getWidgetAccountName(context: Context, appWidgetId: Int): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$KEY_WIDGET_ACCOUNT_NAME_PREFIX$appWidgetId", "").orEmpty()
    }

    fun setWidgetAccount(
        context: Context,
        appWidgetId: Int,
        steamId: String,
        accountIndex: Int,
        accountName: String
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_WIDGET_STEAM_ID_PREFIX$appWidgetId", steamId)
            .putInt("$KEY_WIDGET_ACCOUNT_INDEX_PREFIX$appWidgetId", accountIndex)
            .putString("$KEY_WIDGET_ACCOUNT_NAME_PREFIX$appWidgetId", accountName)
            .apply()
    }

    fun clearWidgetAccount(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_WIDGET_STEAM_ID_PREFIX$appWidgetId")
            .remove("$KEY_WIDGET_ACCOUNT_INDEX_PREFIX$appWidgetId")
            .remove("$KEY_WIDGET_ACCOUNT_NAME_PREFIX$appWidgetId")
            .apply()
    }

    fun isNebulaSessionStackEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_NEBULA_SESSION_STACK, true)
    }

    fun setNebulaSessionStackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_USE_NEBULA_SESSION_STACK, enabled)
            .apply()
    }

    // ---------- Stable device identifier ----------
    fun getPermanentDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PERMANENT_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_PERMANENT_DEVICE_ID, newId).apply()
        return newId
    }
}
