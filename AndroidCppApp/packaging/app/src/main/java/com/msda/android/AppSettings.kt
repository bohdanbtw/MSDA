package com.msda.android

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

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
    private const val KEY_BACKGROUND_SYNC_ENABLED = "background_sync_enabled"
    private const val KEY_PUSH_CONFIRMATIONS_ENABLED = "push_confirmations_enabled"
    private const val KEY_APP_PIN_HASH = "app_pin_hash"
    private const val KEY_APP_PIN_SALT = "app_pin_salt"
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

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, "system") ?: "system"
    }

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode).commit()
    }

    fun isBackgroundSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
            || prefs.getBoolean(KEY_PUSH_CONFIRMATIONS_ENABLED, false)
    }

    fun isBackgroundConfirmationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_SYNC_ENABLED, false)
    }

    fun setBackgroundConfirmationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKGROUND_SYNC_ENABLED, enabled)
            .apply()
    }

    fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        setBackgroundConfirmationsEnabled(context, enabled)
    }

    fun isPushConfirmationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PUSH_CONFIRMATIONS_ENABLED, false)
    }

    fun setPushConfirmationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PUSH_CONFIRMATIONS_ENABLED, enabled)
            .apply()
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

    fun hasPinLock(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_PIN_HASH, null).isNullOrBlank()
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return hasPinLock(context)
    }

    fun setPinLock(context: Context, pin: String) {
        require(isValidPin(pin))

        val salt = UUID.randomUUID().toString()
        val hash = hashPin(pin, salt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_PIN_SALT, salt)
            .putString(KEY_APP_PIN_HASH, hash)
            .apply()
    }

    fun clearPinLock(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_APP_PIN_HASH)
            .remove(KEY_APP_PIN_SALT)
            .putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
            .apply()
    }

    fun verifyPinLock(context: Context, pin: String): Boolean {
        if (!isValidPin(pin)) {
            return false
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val salt = prefs.getString(KEY_APP_PIN_SALT, null).orEmpty()
        val storedHash = prefs.getString(KEY_APP_PIN_HASH, null).orEmpty()
        if (salt.isBlank() || storedHash.isBlank()) {
            return false
        }

        return hashPin(pin, salt) == storedHash
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

    private fun hashPin(pin: String, salt: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
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
            password = prefs.getString("$KEY_PROXY_PASSWORD_PREFIX$steamId", "").orEmpty()
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
            .putString("$KEY_PROXY_PASSWORD_PREFIX$steamId", config.password)
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
}
