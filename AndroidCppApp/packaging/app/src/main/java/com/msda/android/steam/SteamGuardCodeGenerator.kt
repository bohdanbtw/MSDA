package com.msda.android.steam

import android.content.Context
import com.msda.android.NativeBridge
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SteamGuardCodeGenerator {
    private const val ALPHABET = "23456789BCDFGHJKMNPQRTVWXY"

    fun forSteamId(steamId: String): String {
        return runCatching { NativeBridge.getCodeForSteamId(steamId).trim() }.getOrDefault("")
    }

    suspend fun forLogin(context: Context, steamId: String, sharedSecret: String): String {
        if (sharedSecret.isNotBlank()) {
            val time = TimeAligner(SteamHttpClient.create(context, steamId)).alignedEpochSeconds()
            return generateFromSharedSecret(sharedSecret, time)
        }
        val nativeCode = forSteamId(steamId)
        if (nativeCode.isNotBlank()) return nativeCode
        throw IllegalStateException("Steam Guard code is unavailable")
    }

    fun generateFromSharedSecret(sharedSecret: String, unixTimeSeconds: Long): String {
        val key = Base64.getDecoder().decode(sharedSecret)
        val timeSlice = unixTimeSeconds / 30L
        val message = ByteBuffer.allocate(8).putLong(timeSlice).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(message)
        val offset = hash.last().toInt() and 0x0F
        var codePoint = (hash[offset].toInt() and 0x7F shl 24) or
            (hash[offset + 1].toInt() and 0xFF shl 16) or
            (hash[offset + 2].toInt() and 0xFF shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val out = StringBuilder()
        repeat(5) {
            out.append(ALPHABET[codePoint % ALPHABET.length])
            codePoint /= ALPHABET.length
        }
        return out.toString()
    }
}
