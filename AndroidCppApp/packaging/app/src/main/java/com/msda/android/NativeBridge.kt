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

    // New: session renewal and fallback (with stable device id)
    external fun tryRefreshSession(steamId: String, deviceId: String): Boolean
    external fun reauthWithPassword(steamId: String, password: String, deviceId: String): Boolean

    // New: stable device identifier (Kotlin side)
    fun getPermanentDeviceId(context: Context): String =
        AppSettings.getPermanentDeviceId(context)
}
