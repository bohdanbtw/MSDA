package com.msda.android

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class LockActivity : AppCompatActivity() {
    private lateinit var txtStatus: TextView
    private lateinit var dotViews: List<TextView>
    private val enteredPin = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)

        if (!AppSettings.isAppLockEnabled(this)) {
            openHub()
            return
        }

        setContentView(R.layout.activity_lock)

        txtStatus = findViewById(R.id.txtLockStatus)
        dotViews = listOf(
            findViewById(R.id.pinDot1),
            findViewById(R.id.pinDot2),
            findViewById(R.id.pinDot3),
            findViewById(R.id.pinDot4)
        )

        setupPinButtons()

        findViewById<Button>(R.id.btnPinDelete).setOnClickListener {
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

        if (biometricEnabled && biometricAvailable) {
            biometricButton.post { showBiometricPrompt() }
        }
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

        digitButtonIds.forEach { (buttonId, digit) ->
            findViewById<Button>(buttonId).setOnClickListener {
                appendDigit(digit)
            }
        }
    }

    private fun appendDigit(digit: String) {
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
            openHub()
            return
        }

        txtStatus.text = getString(R.string.unlock_pin_error)
        enteredPin.clear()
        updatePinDots()
    }

    private fun updatePinDots() {
        dotViews.forEachIndexed { index, textView ->
            textView.text = if (index < enteredPin.length) "●" else "○"
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
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

    private fun applyThemePreference() {
        when (AppSettings.getThemeMode(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
