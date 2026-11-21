package com.msda.android

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockActivity : AppCompatActivity() {
    private lateinit var txtStatus: TextView
    private lateinit var txtPinProgress: TextView
    private lateinit var dotViews: List<TextView>
    private lateinit var pinDigitButtons: List<Button>
    private lateinit var btnPinDelete: Button
    private val enteredPin = StringBuilder()
    private val lockoutHandler = Handler(Looper.getMainLooper())
    private var lockoutRunnable: Runnable? = null
    private var showingLockout = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (!AppSettings.isAppLockEnabled(this)) {
            openHub()
            return
        }

        setContentView(R.layout.activity_lock)

        txtStatus = findViewById(R.id.txtLockStatus)
        txtPinProgress = findViewById(R.id.txtPinProgress)
        dotViews = listOf(
            findViewById(R.id.pinDot1),
            findViewById(R.id.pinDot2),
            findViewById(R.id.pinDot3),
            findViewById(R.id.pinDot4)
        )

        setupPinButtons()

        btnPinDelete = findViewById(R.id.btnPinDelete)
        btnPinDelete.setOnClickListener {
            if (enteredPin.isNotEmpty()) {
                enteredPin.deleteAt(enteredPin.lastIndex)
                updatePinDots()
            }
        }

        findViewById<Button>(R.id.btnLockClose).setOnClickListener {
            finishAffinity()
        }

        val biometricButton = findViewById<Button>(R.id.btnBiometricUnlock)
        val biometricAvailable = isBiometricAvailable()
        val biometricEnabled = AppSettings.isBiometricUnlockEnabled(this)
        biometricButton.visibility = if (biometricEnabled && biometricAvailable) android.view.View.VISIBLE else android.view.View.INVISIBLE
        biometricButton.setOnClickListener {
            showBiometricPrompt()
        }

        updatePinDots()
        applyLockoutState()

        // Only auto-prompt on a fresh creation: re-creating the activity (e.g. a
        // rotation) must not immediately show the prompt again in a loop.
        if (savedInstanceState == null && biometricEnabled && biometricAvailable) {
            biometricButton.post { showBiometricPrompt() }
        }
    }

    override fun onDestroy() {
        cancelLockoutTick()
        super.onDestroy()
    }

    private fun setupPinButtons() {
        val digitButtonIds = listOf(
            R.id.btnPin0 to "0",
            R.id.btnPin1 to "1",
            R.id.btnPin2 to "2",
            R.id.btnPin3 to "3",
            R.id.btnPin4 to "4",
            R.id.btnPin5 to "5",
            R.id.btnPin6 to "6",
            R.id.btnPin7 to "7",
            R.id.btnPin8 to "8",
            R.id.btnPin9 to "9"
        )

        pinDigitButtons = digitButtonIds.map { (buttonId, digit) ->
            findViewById<Button>(buttonId).also { button ->
                button.setOnClickListener {
                    appendDigit(digit)
                }
            }
        }
    }

    private fun appendDigit(digit: String) {
        if (AppSettings.getPinLockoutRemainingMillis(this) > 0) {
            return
        }
        if (enteredPin.length >= 4) {
            return
        }

        enteredPin.append(digit)
        updatePinDots()

        if (enteredPin.length == 4) {
            verifyEnteredPin()
        }
    }

    private fun verifyEnteredPin() {
        val pin = enteredPin.toString()
        if (AppSettings.verifyPinLock(this, pin)) {
            // Both sides of the merge are required: mark this foreground session
            // unlocked and reset the failed-attempt backoff state.
            AppLockState.unlock()
            AppSettings.clearPinFailures(this)
            enteredPin.clear()
            openHub()
            return
        }

        AppSettings.recordPinFailure(this)
        txtStatus.text = getString(R.string.unlock_pin_error)
        enteredPin.clear()
        updatePinDots()
        applyLockoutState()
    }

    private fun applyLockoutState() {
        val remaining = AppSettings.getPinLockoutRemainingMillis(this)
        val lockedOut = remaining > 0
        setPinInputEnabled(!lockedOut)

        if (lockedOut) {
            val seconds = (remaining + 999) / 1000
            txtStatus.text = getString(R.string.unlock_too_many_attempts, seconds)
            showingLockout = true
            scheduleLockoutTick()
        } else {
            cancelLockoutTick()
            if (showingLockout) {
                txtStatus.text = ""
                showingLockout = false
            }
        }
    }

    private fun scheduleLockoutTick() {
        lockoutRunnable?.let { lockoutHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                applyLockoutState()
            }
        }
        lockoutRunnable = runnable
        lockoutHandler.postDelayed(runnable, 1_000L)
    }

    private fun cancelLockoutTick() {
        lockoutRunnable?.let { lockoutHandler.removeCallbacks(it) }
        lockoutRunnable = null
    }

    private fun setPinInputEnabled(enabled: Boolean) {
        pinDigitButtons.forEach { it.isEnabled = enabled }
        btnPinDelete.isEnabled = enabled
        enteredPin.clear()
        updatePinDots()
    }

    private fun updatePinDots() {
        dotViews.forEachIndexed { index, textView ->
            textView.text = if (index < enteredPin.length) "●" else "○"
        }
        txtPinProgress.text = getString(R.string.pin_progress, enteredPin.length, dotViews.size)
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                AppLockState.unlock()
                AppSettings.clearPinFailures(this@LockActivity)
                openHub()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    txtStatus.text = errString
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                txtStatus.text = getString(R.string.unlock_pin_error)
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.unlock_biometric_title))
            .setSubtitle(getString(R.string.unlock_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.unlock_biometric_negative))
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun openHub() {
        startActivity(
            Intent(this, HubActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
