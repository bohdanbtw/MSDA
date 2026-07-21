package com.msda.android

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Base64

data class ProxyCheckResult(
    val ok: Boolean,
    val publicIp: String?
)

/**
 * Shared proxy connectivity checks and public-IP lookup (via ipify through the proxy).
 */
object ProxyChecker {
    private const val STEAM_PROBE_URL = "https://steamcommunity.com"
    private const val IP_ECHO_URL = "https://api.ipify.org"

    fun isConfigured(config: AccountProxyConfig): Boolean {
        return config.enabled && config.host.isNotBlank() && config.port in 1..65535
    }

    fun check(config: AccountProxyConfig): ProxyCheckResult {
        if (!isConfigured(config)) {
            return ProxyCheckResult(ok = false, publicIp = null)
        }
        val ok = isProxyWorking(config)
        val ip = if (ok) fetchPublicIp(config) else null
        return ProxyCheckResult(ok = ok, publicIp = ip)
    }

    fun isProxyWorking(config: AccountProxyConfig): Boolean {
        return try {
            val connection = open(STEAM_PROBE_URL, config)
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "MSDA")
            connection.responseCode in 200..399
        } catch (_: Throwable) {
            false
        }
    }

    fun fetchPublicIp(config: AccountProxyConfig?): String? {
        return try {
            val connection = if (config != null && isConfigured(config)) {
                open(IP_ECHO_URL, config)
            } else {
                (URL(IP_ECHO_URL).openConnection() as HttpURLConnection)
            }
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "MSDA")
            val code = connection.responseCode
            if (code !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText().trim() }
                .takeIf { it.isNotBlank() && it.length <= 64 }
        } catch (_: Throwable) {
            null
        }
    }

    private fun open(url: String, config: AccountProxyConfig): HttpURLConnection {
        val proxyType = if (config.type.equals("socks", ignoreCase = true)) {
            Proxy.Type.SOCKS
        } else {
            Proxy.Type.HTTP
        }
        val proxy = Proxy(proxyType, InetSocketAddress(config.host, config.port))
        val connection = URL(url).openConnection(proxy) as HttpURLConnection
        if (proxyType == Proxy.Type.HTTP && config.username.isNotBlank()) {
            val token = Base64.getEncoder()
                .encodeToString("${config.username}:${config.password}".toByteArray())
            connection.setRequestProperty("Proxy-Authorization", "Basic $token")
        }
        return connection
    }
}
