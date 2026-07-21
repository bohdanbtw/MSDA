package com.msda.android.steam

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class TimeAligner(private val client: OkHttpClient) {
    suspend fun alignedEpochSeconds(): Long {
        val request = Request.Builder()
            .url("https://api.steampowered.com/ITwoFactorService/QueryTime/v0001")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            return System.currentTimeMillis() / 1000L
        }
        val serverTime = JSONObject(body)
            .optJSONObject("response")
            ?.optLong("server_time", 0L)
            ?: 0L
        return if (serverTime > 0L) serverTime else System.currentTimeMillis() / 1000L
    }
}
