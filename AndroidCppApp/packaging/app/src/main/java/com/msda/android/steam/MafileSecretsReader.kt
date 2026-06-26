package com.msda.android.steam

import android.content.Context
import com.msda.android.ConfirmationAuthContext
import org.json.JSONObject
import java.io.File

/** Read authenticator secrets from on-disk mafiles without JNI. */
object MafileSecretsReader {
    data class MafileSecrets(
        val steamId: String,
        val accountName: String,
        val sharedSecret: String,
        val identitySecret: String,
        val deviceId: String
    )

    fun listSteamIds(context: Context): List<String> {
        return mafilesDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mafile", ignoreCase = true) }
            ?.mapNotNull { file -> readSecretsFromFile(file)?.steamId }
            ?.distinct()
            .orEmpty()
    }

    fun steamIdForFile(file: File): String? = readSecretsFromFile(file)?.steamId

    fun read(context: Context, steamId: String): MafileSecrets? {
        if (steamId.isBlank()) return null
        return findMafileForSteamId(context, steamId)?.let { readSecretsFromFile(it) }
    }

    fun buildAuthContext(context: Context, steamId: String): ConfirmationAuthContext? {
        val secrets = read(context, steamId) ?: return null
        val session = MafileRepository(context).readSession(steamId)
        return ConfirmationAuthContext(
            steamId = secrets.steamId,
            identitySecret = secrets.identitySecret,
            deviceId = secrets.deviceId,
            sessionId = session?.sessionId.orEmpty(),
            steamLoginSecure = session?.steamLoginSecure.orEmpty(),
            accountName = session?.accountName?.ifBlank { secrets.accountName } ?: secrets.accountName,
            sharedSecret = secrets.sharedSecret,
            refreshToken = session?.refreshToken.orEmpty(),
            accessToken = session?.accessToken.orEmpty()
        )
    }

    private fun readSecretsFromFile(file: File): MafileSecrets? {
        val json = try {
            JSONObject(file.readText())
        } catch (_: Throwable) {
            return null
        }
        val steamId = firstNonBlank(
            json.optString("steamid"),
            json.optString("SteamID"),
            json.optJSONObject("SessionData")?.optString("SteamID")
        )
        if (steamId.isBlank() || steamId == "unknown") return null
        val sharedSecret = firstNonBlank(json.optString("shared_secret"))
        val identitySecret = firstNonBlank(json.optString("identity_secret"))
        val deviceId = firstNonBlank(json.optString("device_id"))
        if (sharedSecret.isBlank() || identitySecret.isBlank() || deviceId.isBlank()) return null
        return MafileSecrets(
            steamId = steamId,
            accountName = firstNonBlank(json.optString("account_name"), json.optString("AccountName")),
            sharedSecret = sharedSecret,
            identitySecret = identitySecret,
            deviceId = deviceId
        )
    }

    private fun findMafileForSteamId(context: Context, steamId: String): File? {
        return mafilesDir(context).listFiles()
            ?.firstOrNull { file ->
                if (!file.isFile || !file.name.endsWith(".mafile", ignoreCase = true)) return@firstOrNull false
                readSecretsFromFile(file)?.steamId == steamId
            }
    }

    private fun mafilesDir(context: Context): File = File(context.filesDir, "mafiles")

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }
}
