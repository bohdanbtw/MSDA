package com.msda.android

import android.content.Context
import com.msda.android.steam.SessionHandler

/**
 * Compatibility wrapper kept for existing call sites. [renew] delegates to
 * [SessionHandler.ensureValid], which serializes renewal via the shared per-Steam-ID lock.
 */
object SessionManager {
    suspend fun renew(context: Context, auth: ConfirmationAuthContext): ConfirmationAuthContext? {
        return runCatching { SessionHandler.ensureValid(context, auth) }.getOrNull()
    }
}
