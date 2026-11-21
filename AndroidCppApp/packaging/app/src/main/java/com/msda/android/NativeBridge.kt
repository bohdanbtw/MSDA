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

    // By-steamId accessors that do not change the active account. These are the canonical
    // code/seconds API for all callers, including background receivers, workers and widget
    // ticks. setActiveAccount() is reserved for explicit UI-thread user selection only.
    external fun getConfirmationAuthPayloadForSteamId(steamId: String): String
    external fun getCodeForSteamId(steamId: String): String

    /**
     * Remaining seconds in the 30s Steam Guard window for [steamId]; -1 for an unknown ID.
     * Derived from the wall clock with a read-only account check. The native core exposes the
     * equivalent `secondsToNextCodeForSteamId` (and a JNI wrapper) as the canonical API, but the
     * committed prebuilt libmsda_android.so does not export that symbol yet, so compute locally
     * rather than calling a missing JNI method at runtime.
     */
    fun getSecondsToNextCodeForSteamId(steamId: String): Int {
        val accountsCsv = runCatching { getAccounts() }.getOrDefault("")
        if (!AccountScopedSteamId.isKnownSteamId(accountsCsv, steamId)) return -1
        return AccountScopedSteamId.secondsRemaining(System.currentTimeMillis() / 1000L)
    }

    /** Resolve the Steam ID for a 0-based account index without touching native global state. */
    fun steamIdForAccountIndex(index: Int): String =
        AccountScopedSteamId.fromAccountsCsv(runCatching { getAccounts() }.getOrDefault(""), index)

    /** Steam ID of the active account, read-only (no global mutation). */
    fun activeSteamId(): String =
        AccountScopedSteamId.fromActiveAccountJson(runCatching { getActiveAccount() }.getOrDefault("{}"))

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
