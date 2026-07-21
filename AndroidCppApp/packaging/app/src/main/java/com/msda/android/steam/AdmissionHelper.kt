package com.msda.android.steam

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object AdmissionHelper {
    private const val STEAM_COMMUNITY = "steamcommunity.com"
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    class PerAccountCookieJar(private val steamId: String) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val key = key(steamId)
            val current = store.getOrPut(key) { mutableListOf() }
            synchronized(current) {
                cookies.forEach { next ->
                    current.removeAll { it.name == next.name && it.domain == next.domain && it.path == next.path }
                    current.add(next)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val key = key(steamId)
            val current = store[key] ?: return emptyList()
            val now = System.currentTimeMillis()
            return synchronized(current) {
                current.filter { (it.expiresAt >= now) && url.host.endsWith(it.domain.removePrefix(".")) }
            }
        }
    }

    fun seedMobileSessionCookies(steamId: String, session: SteamSessionData) {
        val cookies = mutableListOf<Cookie>()
        val webTradeEligibility = URLEncoder.encode(
            """{"allowed":1,"allowed_at_time":0,"steam_guard_required_days":15,"new_device_cooldown_days":0,"time_checked":${System.currentTimeMillis() / 1000}}""",
            "UTF-8"
        )
        cookies += cookie("steamLoginSecure", session.steamLoginSecure)
        cookies += cookie("steamRefresh_steam", session.refreshToken, host = "login.steampowered.com")
        cookies += cookie("sessionid", session.sessionId)
        cookies += cookie("steamid", session.steamId)
        cookies += cookie("mobileClient", "android")
        cookies += cookie("mobileClientVersion", "777777 3.6.1")
        cookies += cookie("Steam_Language", "english")
        cookies += cookie("webTradeEligibility", webTradeEligibility)
        store[key(steamId)] = cookies
    }

    fun clear(steamId: String) {
        store.remove(key(steamId))
    }

    private fun cookie(name: String, value: String, host: String = STEAM_COMMUNITY): Cookie {
        return Cookie.Builder()
            .name(name)
            .value(value)
            .domain(host)
            .path("/")
            .httpOnly()
            .secure()
            .build()
    }

    private fun key(steamId: String): String = steamId.ifBlank { "__anonymous__" }
}
