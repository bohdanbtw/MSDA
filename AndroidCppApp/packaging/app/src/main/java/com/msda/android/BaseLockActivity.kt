package com.msda.android

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Base class for every activity that must not be reachable without passing
 * the PIN/biometric lock at least once in the current foreground session.
 */
open class BaseLockActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        if (AppSettings.isAppLockEnabled(this) && !AppLockState.unlocked) {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }
}
