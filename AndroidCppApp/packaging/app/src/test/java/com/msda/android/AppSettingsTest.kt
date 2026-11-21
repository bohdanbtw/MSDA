package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    /**
     * Local JVM tests have no Android Keystore, so PasswordManager.encrypt() fails
     * closed (returns null) and AppSettings never writes the proxy password. Assert
     * that no SharedPreferences value contains the submitted secret in plaintext.
     */
    private fun assertNoPlaintextPersisted(context: TestContext, secret: String) {
        if (secret.isEmpty()) return
        context.prefs.all.values.filterIsInstance<String>().forEach { value ->
            assertFalse("Plaintext password was persisted: $value", value == secret || value.contains(secret))
        }
    }

    @Test
    fun isValidPin_acceptsOnlyFourDigits() {
        assertTrue(AppSettings.isValidPin("1234"))
        assertTrue(AppSettings.isValidPin("0000"))
        assertFalse(AppSettings.isValidPin("123"))
        assertFalse(AppSettings.isValidPin("12345"))
        assertFalse(AppSettings.isValidPin("12a4"))
        assertFalse(AppSettings.isValidPin("    "))
        assertFalse(AppSettings.isValidPin(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPinLock_rejectsInvalidPin() {
        AppSettings.setPinLock(TestContext(), "12ab")
    }

    @Test
    fun pinLock_persistsSaltedHashAndVerifies() {
        val context = TestContext()

        assertFalse(AppSettings.hasPinLock(context))
        assertFalse(AppSettings.verifyPinLock(context, "1234"))

        AppSettings.setPinLock(context, "1234")

        assertTrue(AppSettings.hasPinLock(context))
        assertTrue(AppSettings.isAppLockEnabled(context))
        assertTrue(AppSettings.verifyPinLock(context, "1234"))
        assertFalse(AppSettings.verifyPinLock(context, "4321"))
        assertFalse(AppSettings.verifyPinLock(context, "12ab"))

        val prefs = context.getSharedPreferences("msda_ui", 0)
        val storedHash = prefs.getString("app_pin_hash", null)!!
        assertNotEquals("1234", storedHash)
        assertTrue(prefs.getString("app_pin_salt", null)!!.isNotBlank())
        assertEquals(64, storedHash.length)

        AppSettings.clearPinLock(context)

        assertFalse(AppSettings.hasPinLock(context))
        assertFalse(AppSettings.verifyPinLock(context, "1234"))
        assertFalse(prefs.contains("app_pin_salt"))
    }

    @Test
    fun settingPinAgain_changesSaltAndHash() {
        val context = TestContext()
        AppSettings.setPinLock(context, "1234")
        val firstHash = context.getSharedPreferences("msda_ui", 0).getString("app_pin_hash", null)

        AppSettings.setPinLock(context, "1234")
        val secondHash = context.getSharedPreferences("msda_ui", 0).getString("app_pin_hash", null)

        assertNotEquals(firstHash, secondHash)
        assertTrue(AppSettings.verifyPinLock(context, "1234"))
    }

    @Test
    fun biometricUnlock_requiresPinLock() {
        val context = TestContext()

        AppSettings.setBiometricUnlockEnabled(context, true)
        assertFalse(AppSettings.isBiometricUnlockEnabled(context))

        AppSettings.setPinLock(context, "1234")
        AppSettings.setBiometricUnlockEnabled(context, true)
        assertTrue(AppSettings.isBiometricUnlockEnabled(context))

        AppSettings.clearPinLock(context)
        assertFalse(AppSettings.isBiometricUnlockEnabled(context))
    }

    @Test
    fun accountProxyConfig_roundTripsAndNormalizes() {
        val context = TestContext()
        val steamId = "76561198000000000"

        AppSettings.setAccountProxyConfig(
            context, steamId,
            AccountProxyConfig(
                enabled = true,
                type = "SOCKS",
                host = "  proxy.example.com  ",
                port = 1080,
                username = "  user  ",
                password = "secret"
            )
        )

        val stored = AppSettings.getAccountProxyConfig(context, steamId)
        assertEquals(true, stored.enabled)
        assertEquals("socks", stored.type)
        assertEquals("proxy.example.com", stored.host)
        assertEquals(1080, stored.port)
        assertEquals("user", stored.username)
        // No Keystore in local JVM tests -> encryption fails closed and the
        // password is not persisted. It must never read back as plaintext.
        assertEquals("", stored.password)
        assertNoPlaintextPersisted(context, "secret")

        AppSettings.setAccountProxyConfig(
            context, steamId,
            AccountProxyConfig(true, "unexpected", "host", 8080, "", "")
        )
        assertEquals("http", AppSettings.getAccountProxyConfig(context, steamId).type)

        AppSettings.clearAccountProxyConfig(context, steamId)
        assertEquals(
            AccountProxyConfig(false, "http", "", 0, "", ""),
            AppSettings.getAccountProxyConfig(context, steamId)
        )

        // Blank steam ids are ignored entirely.
        AppSettings.setAccountProxyConfig(context, " ", AccountProxyConfig(true, "http", "h", 1, "", ""))
        assertEquals(AccountProxyConfig(false, "http", "", 0, "", ""), AppSettings.getAccountProxyConfig(context, " "))
    }

    @Test
    fun defaultProxyConfig_roundTripsAndClears() {
        val context = TestContext()

        assertEquals(AccountProxyConfig(false, "http", "", 0, "", ""), AppSettings.getDefaultProxyConfig(context))

        AppSettings.setDefaultProxyConfig(
            context,
            AccountProxyConfig(true, "socks", " default.example ", 9050, " user ", "pw")
        )
        val stored = AppSettings.getDefaultProxyConfig(context)
        assertEquals(true, stored.enabled)
        assertEquals("socks", stored.type)
        assertEquals("default.example", stored.host)
        assertEquals(9050, stored.port)
        assertEquals("user", stored.username)
        // No Keystore in local JVM tests -> password is not persisted; never plaintext.
        assertEquals("", stored.password)
        assertNoPlaintextPersisted(context, "pw")

        AppSettings.clearDefaultProxyConfig(context)
        assertEquals(AccountProxyConfig(false, "http", "", 0, "", ""), AppSettings.getDefaultProxyConfig(context))
    }

    @Test
    fun resolveProxyConfig_prefersConfiguredAccountOverDefault() {
        val context = TestContext()
        val steamId = "76561198000000000"
        val default = AccountProxyConfig(true, "http", "default.example", 8080, "d", "dp")
        AppSettings.setDefaultProxyConfig(context, default)
        // No Keystore in local JVM tests -> passwords are not persisted, so any
        // stored config reads back with an empty password.
        val expectedDefault = default.copy(password = "")

        // No per-account config -> default.
        assertEquals(expectedDefault, AppSettings.resolveProxyConfig(context, steamId))

        // Valid per-account config wins.
        val account = AccountProxyConfig(true, "socks", "account.example", 1080, "a", "ap")
        AppSettings.setAccountProxyConfig(context, steamId, account)
        assertEquals(account.copy(password = ""), AppSettings.resolveProxyConfig(context, steamId))

        // Disabled per-account config falls back to default.
        AppSettings.setAccountProxyConfig(context, steamId, account.copy(enabled = false))
        assertEquals(expectedDefault, AppSettings.resolveProxyConfig(context, steamId))

        // Enabled but incomplete per-account config also falls back to default.
        AppSettings.setAccountProxyConfig(context, steamId, account.copy(host = "  "))
        assertEquals(expectedDefault, AppSettings.resolveProxyConfig(context, steamId))
        AppSettings.setAccountProxyConfig(context, steamId, account.copy(port = 0))
        assertEquals(expectedDefault, AppSettings.resolveProxyConfig(context, steamId))
    }
}
