package com.msda.android

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

    // New: session renewal and fallback
    external fun tryRefreshSession(steamId: String): Boolean
    external fun reauthWithPassword(steamId: String, password: String): Boolean

    // New: stable device identifier
    external fun getPermanentDeviceId(): String
}
