package com.msda.android

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import com.msda.android.steam.AdmissionHelper
import com.msda.android.steam.LoginV2Executor
import com.msda.android.steam.MafileRepository
import com.msda.android.steam.MafileSecretsReader
import com.msda.android.steam.NativeAuthBridge
import com.msda.android.steam.SessionHandler
import com.msda.android.steam.SteamSessionData
import com.msda.android.steam.SteamTokenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SteamAuthResult(
    val success: Boolean,
    val steamId: String? = null,
    val steamLoginSecure: String? = null,
    val sessionId: String? = null,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val sessionExpiresAtMs: Long = 0L,
    val errorMessage: String? = null
)

object SteamAuthService {
    fun showPasswordDialog(
        context: Context,
        accountName: String,
        onResult: (SteamAuthResult) -> Unit,
        onProgress: ((String) -> Unit)? = null
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 16, 48, 16)
        }
        val passwordInput = EditText(context).apply {
            hint = "Steam Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(passwordInput)

        AlertDialog.Builder(context)
            .setTitle("Steam Login")
            .setMessage("Enter your Steam password to continue")
            .setView(container)
            .setPositiveButton("Login") { dialog, _ ->
                dialog.dismiss()
                val password = passwordInput.text.toString()
                if (password.isBlank()) {
                    onResult(SteamAuthResult(false, errorMessage = "Password cannot be empty"))
                } else {
                    performLogin(context, accountName, password, onResult, onProgress)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                onResult(SteamAuthResult(false, errorMessage = "Login cancelled"))
            }
            .show()
    }

    suspend fun refreshSessionUsingToken(
        refreshToken: String,
        steamId: String,
        existingSessionId: String = ""
    ): SteamAuthResult {
        return SteamAuthResult(
            success = false,
            steamId = steamId.ifBlank { null },
            sessionId = existingSessionId.ifBlank { null },
            refreshToken = refreshToken.ifBlank { null },
            errorMessage = "Use SessionHandler for token refresh"
        )
    }

    suspend fun refreshSessionUsingPassword(
        context: Context,
        accountName: String,
        password: String,
        steamId: String = ""
    ): SteamAuthResult {
        val payload = if (steamId.isNotBlank()) {
            NativeAuthBridge.confirmationAuthForSteamId(context, steamId)
        } else {
            NativeAuthBridge.activeConfirmationAuth(context)
        }
        val auth = payload
            ?: return SteamAuthResult(false, errorMessage = "Failed to parse account data")
        if (accountName.isNotBlank() && !auth.accountName.equals(accountName, ignoreCase = true)) {
            return SteamAuthResult(false, errorMessage = "Account mismatch for password relogin")
        }
        return runCatching {
            val renewed = SessionHandler.loginAgain(context, auth, password)
            SteamAuthResult(
                success = true,
                steamId = renewed.steamId,
                steamLoginSecure = renewed.steamLoginSecure,
                sessionId = renewed.sessionId,
                refreshToken = renewed.refreshToken,
                accessToken = renewed.accessToken,
                sessionExpiresAtMs = parseJwtExpMs(renewed.accessToken)
            )
        }.getOrElse { SteamAuthResult(false, errorMessage = it.message ?: "Password login failed") }
    }

    fun parseJwtExpMs(token: String): Long {
        return runCatching { SteamTokenHelper.parse(token).expiresAtMs }.getOrDefault(0L)
    }

    private fun performLogin(
        context: Context,
        accountName: String,
        password: String,
        onResult: (SteamAuthResult) -> Unit,
        onProgress: ((String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                emitProgress(onProgress, "Authenticating... preparing account")
                val auth = resolveAuthForAccount(context, accountName)
                    ?: error("Failed to parse account data")
                emitProgress(onProgress, "Authenticating... Steam LoginV2")
                val login = LoginV2Executor(context, auth.steamId).login(
                    accountName = accountName,
                    password = password,
                    sharedSecret = auth.sharedSecret
                )
                val session = SteamSessionData(
                    steamId = login.steamId,
                    accountName = login.accountName,
                    sessionId = login.sessionId,
                    steamLoginSecure = login.steamLoginSecure,
                    refreshToken = login.refreshToken,
                    accessToken = login.accessToken
                )
                MafileRepository(context).writeSessionBlocking(session)
                AdmissionHelper.seedMobileSessionCookies(login.steamId, session)
                PasswordManager.savePassword(context, accountName, password)
                SteamAuthResult(
                    success = true,
                    steamId = login.steamId,
                    steamLoginSecure = login.steamLoginSecure,
                    sessionId = login.sessionId,
                    refreshToken = login.refreshToken,
                    accessToken = login.accessToken,
                    sessionExpiresAtMs = login.expiresAtMs
                )
            } catch (ex: Throwable) {
                SteamAuthResult(false, errorMessage = ex.message ?: "Login failed")
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    private suspend fun emitProgress(onProgress: ((String) -> Unit)?, message: String) {
        if (onProgress == null) return
        withContext(Dispatchers.Main) { onProgress(message) }
    }

    private fun resolveAuthForAccount(context: Context, accountName: String): ConfirmationAuthContext? {
        val active = NativeAuthBridge.activeConfirmationAuth(context)
        if (active != null && active.accountName.equals(accountName, ignoreCase = true)) {
            return active
        }

        for (steamId in MafileSecretsReader.listSteamIds(context)) {
            val auth = NativeAuthBridge.confirmationAuthForSteamId(context, steamId) ?: continue
            if (auth.accountName.equals(accountName, ignoreCase = true)) {
                return auth
            }
        }

        return active
    }
}
