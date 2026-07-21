package com.msda.android.steam

import android.content.Context
import com.msda.android.ConfirmationAuthContext
import com.msda.android.ConfirmationService
import com.msda.android.NativeBridge
import org.json.JSONObject

/**
 * JNI-safe bridge: uses native payload when the bundled .so exports it,
 * otherwise falls back to Kotlin mafile parsing.
 */
object NativeAuthBridge {
    fun confirmationAuthForSteamId(context: Context, steamId: String): ConfirmationAuthContext? {
        val fromNative = runCatching {
            ConfirmationService.parseAuthPayload(NativeBridge.getConfirmationAuthPayloadForSteamId(steamId))
        }.getOrNull()
        if (fromNative != null && fromNative.sharedSecret.isNotBlank()) {
            return AuthContextMerger.merge(context, fromNative)
        }
        return MafileSecretsReader.buildAuthContext(context, steamId)
            ?.let { AuthContextMerger.merge(context, it) }
    }

    fun activeConfirmationAuth(context: Context): ConfirmationAuthContext? {
        val steamId = resolveActiveSteamId(context) ?: return null
        return confirmationAuthForSteamId(context, steamId)
    }

    private fun resolveActiveSteamId(context: Context): String? {
        val fromNative = runCatching {
            val json = JSONObject(NativeBridge.getActiveAccount())
            json.optString("steamId").trim().takeIf { it.isNotBlank() && it != "unknown" }
        }.getOrNull()
        if (!fromNative.isNullOrBlank()) return fromNative

        val accounts = runCatching { NativeBridge.getAccounts() }.getOrDefault("")
        val firstLine = accounts.lines().map { it.trim() }.firstOrNull { it.isNotBlank() }
        val fromLine = firstLine?.split('|')?.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        if (!fromLine.isNullOrBlank()) return fromLine

        return MafileSecretsReader.listSteamIds(context).firstOrNull()
    }
}
