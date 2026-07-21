package com.msda.android

import android.content.Context
import com.msda.android.steam.SessionHandler

/**
 * Compatibility wrapper kept for existing call sites.
 */
object SessionManager {
    suspend fun renew(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext? {
        return runCatching { SessionHandler.ensureValid(context, auth) }.getOrNull()
    }
}
