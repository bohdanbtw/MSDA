package com.msda.android

import android.content.Context

object NativeBridge {
    init {
        System.loadLibrary("msda_android")
    }

    external fun importMafilesFromFolder(folderPath: String): Boolean
    external fun getAccounts(): String
    external fun setActiveAccount(index: Int): Boolean
    external fun getActiveAccount(): String
    external fun getActiveCode(): String
    external fun getSecondsToNextCode(): Int
    external fun getActiveConfirmationAuthPayload(): String

    external fun updateSessionTokens(
        steamId: String,
        sessionId: String,
        steamLoginSecure: String,
        refreshToken: String,
        accessToken: String
    ): Boolean

    /** Updates session fields in the .mafile on disk for long-term persistence. */
    external fun updateMafileSessionTokens(
        steamId: String,
        sessionId: String,
        steamLoginSecure: String,
        refreshToken: String,
        accessToken: String
    ): Boolean

    // New: stable device identifier (Kotlin side)
    fun getPermanentDeviceId(context: Context): String =
        AppSettings.getPermanentDeviceId(context)
}
