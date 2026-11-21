package com.msda.android

import org.json.JSONObject

/**
 * Pure helpers that turn already-fetched native data into the Steam ID used for
 * account-scoped code/seconds reads. Kept free of JNI/state so these can be unit tested
 * and so callers never need to mutate the process-global active account just to read a code.
 */
object AccountScopedSteamId {
    /** `getAccounts()` rows look like `index|accountName|steamId`. */
    fun fromAccountsCsv(accountsCsv: String, index: Int): String {
        if (index < 0) return ""
        val line = accountsCsv.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .getOrNull(index) ?: return ""
        return line.split('|').getOrNull(2)?.trim().orEmpty()
    }

    /** `getActiveAccount()` returns a JSON object containing a `steamId` field. */
    fun fromActiveAccountJson(json: String): String = runCatching {
        JSONObject(json).optString("steamId").trim()
    }.getOrDefault("")

    /** True when `getAccounts()` output contains the given Steam ID. */
    fun isKnownSteamId(accountsCsv: String, steamId: String): Boolean =
        steamId.isNotBlank() && accountsCsv.lines().any {
            it.split('|').getOrNull(2)?.trim() == steamId
        }

    /**
     * Steam Guard codes rotate on a global 30-second Unix-time window, identical for every
     * account. Deriving the countdown from the clock keeps it account-scoped without any
     * global native state.
     *
     * Exact definition (matching AccountManager::secondsToNextCode /
     * secondsToNextCodeForSteamId in AccountManager.cpp): `30 - (now % 30)`, yielding a
     * value in 1..30. A timestamp exactly on a window boundary (`now % 30 == 0`) yields 30;
     * one second before the next boundary (`now % 30 == 29`) yields 1.
     */
    fun secondsRemaining(nowEpochSeconds: Long): Int {
        // floorMod keeps the phase in 0..29 for any input, so the result is always 1..30.
        val phase = Math.floorMod(nowEpochSeconds, 30L)
        return (30L - phase).toInt()
    }
}
