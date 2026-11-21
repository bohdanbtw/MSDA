package com.msda.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyCheckerTest {
    private fun config(
        enabled: Boolean = true,
        type: String = "http",
        host: String = "proxy.example.com",
        port: Int = 8080,
        username: String = "",
        password: String = ""
    ) = AccountProxyConfig(enabled, type, host, port, username, password)

    @Test
    fun isConfigured_requiresEnabledHostAndValidPort() {
        assertTrue(ProxyChecker.isConfigured(config()))

        assertFalse(ProxyChecker.isConfigured(config(enabled = false)))
        assertFalse(ProxyChecker.isConfigured(config(host = "")))
        assertFalse(ProxyChecker.isConfigured(config(host = "   ")))
        assertFalse(ProxyChecker.isConfigured(config(port = 0)))
        assertFalse(ProxyChecker.isConfigured(config(port = -1)))
        assertFalse(ProxyChecker.isConfigured(config(port = 65_536)))

        assertTrue(ProxyChecker.isConfigured(config(port = 1)))
        assertTrue(ProxyChecker.isConfigured(config(port = 65_535)))
        assertTrue(ProxyChecker.isConfigured(config(type = "socks", username = "u", password = "p")))
    }

    @Test
    fun check_unconfiguredProxyReturnsNoResultWithoutNetwork() {
        val result = ProxyChecker.check(config(enabled = false))
        assertFalse(result.ok)
        assertNull(result.publicIp)

        val blankHost = ProxyChecker.check(config(host = " "))
        assertFalse(blankHost.ok)
        assertNull(blankHost.publicIp)
    }
}
