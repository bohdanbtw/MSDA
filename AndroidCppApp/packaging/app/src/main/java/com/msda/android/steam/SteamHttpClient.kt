package com.msda.android.steam

import android.content.Context
import com.msda.android.AppSettings
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object SteamHttpClient {
    private const val USER_AGENT = "okhttp/3.12.12"

    fun create(context: Context, steamId: String): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .cookieJar(AdmissionHelper.PerAccountCookieJar(steamId))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(request)
            }

        val proxyConfig = AppSettings.getAccountProxyConfig(context, steamId)
        if (proxyConfig.enabled && proxyConfig.host.isNotBlank() && proxyConfig.port in 1..65535) {
            val proxyType = if (proxyConfig.type.equals("socks", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            builder.proxy(Proxy(proxyType, InetSocketAddress(proxyConfig.host, proxyConfig.port)))
            if (proxyType == Proxy.Type.HTTP && proxyConfig.username.isNotBlank()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(proxyConfig.username, proxyConfig.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }
        }

        return builder.build()
    }

    fun newRequest(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Origin", "https://steamcommunity.com")
            .header("Referer", "https://steamcommunity.com/mobileconf")
            .header("X-Requested-With", "com.valvesoftware.android.steam.community")
    }
}
