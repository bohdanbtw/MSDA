package com.msda.android

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsActivity : BaseLockActivity() {
    private val reauthHandler = Handler(Looper.getMainLooper())
    private var reauthLockoutRunnable: Runnable? = null
    private var suppressingBiometricToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val group = findViewById<RadioGroup>(R.id.radioThemeGroup)
        val txtPinLockStatus = findViewById<TextView>(R.id.txtPinLockStatus)
        val btnSetPin = findViewById<Button>(R.id.btnSetPinLock)
        val btnRemovePin = findViewById<Button>(R.id.btnRemovePinLock)
        val switchBiometric = findViewById<Switch>(R.id.switchBiometricUnlock)
        val btnExport = findViewById<Button>(R.id.btnExportMafiles)
        val btnDefaultProxy = findViewById<Button>(R.id.btnDefaultProxy)
        val txtDefaultProxyStatus = findViewById<TextView>(R.id.txtDefaultProxyStatus)
        val layoutVersionFooter = findViewById<android.widget.LinearLayout>(R.id.layoutVersionFooter)
        val txtUpdateAvailable = findViewById<TextView>(R.id.txtUpdateAvailable)

        when (AppSettings.getThemeMode(this)) {
            "light" -> group.check(R.id.radioThemeLight)
            "dark" -> group.check(R.id.radioThemeDark)
            else -> group.check(R.id.radioThemeSystem)
        }

        refreshSecurityViews(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
        refreshDefaultProxyStatus(txtDefaultProxyStatus)

        group.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioThemeLight -> "light"
                R.id.radioThemeDark -> "dark"
                else -> "system"
            }

            if (newMode == AppSettings.getThemeMode(this)) {
                return@setOnCheckedChangeListener
            }

            AppSettings.setThemeMode(this, newMode)
            AppSettings.applyTheme(this)
            // Recreate in place; clearing the task would tear down the whole app.
            recreate()
        }

        btnSetPin.setOnClickListener {
            showPinSetupDialog(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
        }

        btnRemovePin.setOnClickListener {
            confirmPinRemoval(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
        }

        switchBiometric.setOnCheckedChangeListener { buttonView, isChecked ->
            if (suppressingBiometricToggle || !buttonView.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (!AppSettings.hasPinLock(this)) {
                setBiometricSwitch(switchBiometric, false)
                Toast.makeText(this, getString(R.string.biometric_requires_pin), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked && !isBiometricAvailable()) {
                setBiometricSwitch(switchBiometric, false)
                Toast.makeText(this, getString(R.string.biometric_unavailable), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            // Toggling fingerprint unlock is a security change too: hold the
            // switch in its old position until re-authentication succeeds.
            setBiometricSwitch(switchBiometric, !isChecked)
            requireReauth {
                AppSettings.setBiometricUnlockEnabled(this, isChecked)
                setBiometricSwitch(switchBiometric, isChecked)
            }
        }

        btnExport.setOnClickListener {
            exportMafiles()
        }

        btnDefaultProxy.setOnClickListener {
            showDefaultProxyDialog(txtDefaultProxyStatus)
        }

        var latestReleaseUrl = "https://github.com/bohdanbtw/MSDA/releases/latest"
        layoutVersionFooter.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestReleaseUrl)))
        }

        refreshUpdateHint(txtUpdateAvailable) { url ->
            latestReleaseUrl = url
        }
    }

    override fun onDestroy() {
        reauthLockoutRunnable?.let { reauthHandler.removeCallbacks(it) }
        reauthLockoutRunnable = null
        // Cancel the update-hint request if the screen is leaving.
        lifecycleScope.cancel()
        super.onDestroy()
    }

    private fun refreshUpdateHint(
        txtUpdateAvailable: TextView,
        onLatestUrl: (String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val latest = UpdateChecker.fetchLatestRelease() ?: return@launch
                if (isFinishing || isDestroyed) return@launch
                onLatestUrl(latest.htmlUrl.ifBlank { "https://github.com/bohdanbtw/MSDA/releases/latest" })
                val local = UpdateChecker.currentVersionName(this@SettingsActivity)
                if (UpdateChecker.isNewer(latest.versionName, local)) {
                    txtUpdateAvailable.visibility = android.view.View.VISIBLE
                }
            } catch (_: Throwable) {
                // Keep footer quiet when GitHub is unreachable; tap still opens releases/latest.
            }
        }
    }

    private fun refreshDefaultProxyStatus(txtDefaultProxyStatus: TextView) {
        val config = AppSettings.getDefaultProxyConfig(this)
        txtDefaultProxyStatus.text = when {
            !ProxyChecker.isConfigured(config) -> getString(R.string.proxy_status_disabled)
            else -> {
                val summary = "${config.type.uppercase()} ${config.host}:${config.port}"
                getString(R.string.proxy_using_default) + " — $summary"
            }
        }
    }

    private fun showDefaultProxyDialog(txtDefaultProxyStatus: TextView) {
        val current = AppSettings.getDefaultProxyConfig(this)
        ProxySettingsUi.showEditor(
            context = this,
            title = getString(R.string.default_proxy_title),
            current = current,
            enableLabel = getString(R.string.default_proxy_enable),
            onClear = {
                AppSettings.clearDefaultProxyConfig(this)
                Toast.makeText(this, getString(R.string.proxy_saved), Toast.LENGTH_SHORT).show()
                refreshDefaultProxyStatus(txtDefaultProxyStatus)
            },
            onSaved = { config ->
                AppSettings.setDefaultProxyConfig(this, config)
                Toast.makeText(this, getString(R.string.proxy_saved), Toast.LENGTH_SHORT).show()
                refreshDefaultProxyStatus(txtDefaultProxyStatus)
                if (config.enabled) {
                    ProxySettingsUi.runCheckAndNotify(this, config, txtDefaultProxyStatus)
                }
            }
        )
    }

    private fun refreshSecurityViews(
        txtPinLockStatus: TextView,
        btnSetPin: Button,
        btnRemovePin: Button,
        switchBiometric: Switch
    ) {
        val hasPin = AppSettings.hasPinLock(this)
        txtPinLockStatus.text = getString(if (hasPin) R.string.pin_lock_enabled else R.string.pin_lock_not_set)
        btnSetPin.text = getString(if (hasPin) R.string.change_pin_lock else R.string.set_pin_lock)
        btnRemovePin.isEnabled = hasPin
        btnRemovePin.alpha = if (hasPin) 1f else 0.5f
        switchBiometric.isEnabled = hasPin && isBiometricAvailable()
        setBiometricSwitch(switchBiometric, AppSettings.isBiometricUnlockEnabled(this))
    }

    /** Programmatic switch updates must not re-enter the checked-change listener. */
    private fun setBiometricSwitch(switchBiometric: Switch, checked: Boolean) {
        suppressingBiometricToggle = true
        switchBiometric.isChecked = checked
        suppressingBiometricToggle = false
    }

    private fun showPinSetupDialog(
        txtPinLockStatus: TextView,
        btnSetPin: Button,
        btnRemovePin: Button,
        switchBiometric: Switch
    ) {
        requireReauth { showPinSetupForm(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric) }
    }

    private fun showPinSetupForm(
        txtPinLockStatus: TextView,
        btnSetPin: Button,
        btnRemovePin: Button,
        switchBiometric: Switch
    ) {
        val pinInput = EditText(this).apply {
            hint = getString(R.string.pin_setup_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
        }
        val confirmInput = EditText(this).apply {
            hint = getString(R.string.pin_setup_confirm_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(pinInput)
            addView(confirmInput)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pin_setup_title)
            .setMessage(R.string.pin_setup_message)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pin_save, null)
            .create()
            .also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.setOnShowListener {
                    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val pin = pinInput.text?.toString().orEmpty()
                        val confirm = confirmInput.text?.toString().orEmpty()
                        when {
                            !AppSettings.isValidPin(pin) -> Toast.makeText(this, getString(R.string.pin_invalid), Toast.LENGTH_SHORT).show()
                            pin != confirm -> Toast.makeText(this, getString(R.string.pin_mismatch), Toast.LENGTH_SHORT).show()
                            else -> {
                                AppSettings.setPinLock(this, pin)
                                Toast.makeText(this, getString(R.string.pin_saved), Toast.LENGTH_SHORT).show()
                                refreshSecurityViews(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
                                dialog.dismiss()
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun confirmPinRemoval(
        txtPinLockStatus: TextView,
        btnSetPin: Button,
        btnRemovePin: Button,
        switchBiometric: Switch
    ) {
        if (!AppSettings.hasPinLock(this)) {
            return
        }
        requireReauth { showPinRemovalConfirmation(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric) }
    }

    private fun showPinRemovalConfirmation(
        txtPinLockStatus: TextView,
        btnSetPin: Button,
        btnRemovePin: Button,
        switchBiometric: Switch
    ) {
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_remove_confirm_title)
            .setMessage(R.string.pin_remove_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove_pin_lock) { _, _ ->
                AppSettings.clearPinLock(this)
                Toast.makeText(this, getString(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                refreshSecurityViews(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
            }
            .create()
            .also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.setOnShowListener {
                    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                dialog.show()
            }
    }

    // Re-authentication is required before a PIN can be changed or removed. Prefer
    // the strongest device biometric available, falling back to the current PIN.
    private fun requireReauth(onSuccess: () -> Unit) {
        if (!AppSettings.hasPinLock(this)) {
            onSuccess()
            return
        }

        val authenticator = strongestBiometric()
        if (authenticator != null) {
            showBiometricReauth(authenticator, onSuccess)
        } else {
            showCurrentPinDialog(onSuccess)
        }
    }

    private fun strongestBiometric(): Int? {
        val manager = BiometricManager.from(this)
        return when {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricManager.Authenticators.BIOMETRIC_STRONG
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricManager.Authenticators.BIOMETRIC_WEAK
            else -> null
        }
    }

    private fun showBiometricReauth(authenticator: Int, onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                AppSettings.clearPinFailures(this@SettingsActivity)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    showCurrentPinDialog(onSuccess)
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(this@SettingsActivity, getString(R.string.pin_reauth_failed), Toast.LENGTH_SHORT).show()
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.pin_reauth_title))
            .setSubtitle(getString(R.string.pin_reauth_subtitle))
            .setNegativeButtonText(getString(R.string.pin_reauth_use_pin))
            .setAllowedAuthenticators(authenticator)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun showCurrentPinDialog(onSuccess: () -> Unit) {
        val pinInput = EditText(this).apply {
            hint = getString(R.string.pin_current_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.pin_reauth_title)
            .setMessage(R.string.pin_reauth_message)
            .setView(pinInput)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .also { dialog ->
                // Buttons only exist after show(), so bind the positive button
                // from the show listener instead of right after create().
                var positiveButton: Button? = null
                fun cancelLockoutTick() {
                    reauthLockoutRunnable?.let { reauthHandler.removeCallbacks(it) }
                    reauthLockoutRunnable = null
                }
                fun refreshLockoutState() {
                    val button = positiveButton ?: return
                    val remaining = AppSettings.getPinLockoutRemainingMillis(this@SettingsActivity)
                    button.isEnabled = remaining <= 0
                    if (remaining > 0) {
                        val seconds = (remaining + 999) / 1000
                        pinInput.error = getString(R.string.unlock_too_many_attempts, seconds)
                        cancelLockoutTick()
                        val tick = object : Runnable {
                            override fun run() {
                                if (!dialog.isShowing) return
                                refreshLockoutState()
                            }
                        }
                        reauthLockoutRunnable = tick
                        reauthHandler.postDelayed(tick, 1_000L)
                    } else {
                        pinInput.error = null
                        cancelLockoutTick()
                    }
                }

                dialog.setOnShowListener {
                    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    refreshLockoutState()
                    positiveButton?.setOnClickListener {
                        if (AppSettings.getPinLockoutRemainingMillis(this@SettingsActivity) > 0) {
                            refreshLockoutState()
                            return@setOnClickListener
                        }
                        val currentPin = pinInput.text?.toString().orEmpty()
                        if (AppSettings.verifyPinLock(this@SettingsActivity, currentPin)) {
                            AppSettings.clearPinFailures(this@SettingsActivity)
                            cancelLockoutTick()
                            dialog.dismiss()
                            onSuccess()
                        } else {
                            // Same exponential backoff as LockActivity.
                            AppSettings.recordPinFailure(this@SettingsActivity)
                            pinInput.error = getString(R.string.pin_reauth_failed)
                            pinInput.text?.clear()
                            refreshLockoutState()
                        }
                    }
                }
                dialog.setOnDismissListener { cancelLockoutTick() }
                dialog.show()
            }
    }

    private fun isBiometricAvailable(): Boolean {
        return BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun exportMafiles() {
        val mafilesDir = File(filesDir, "mafiles")

        if (!mafilesDir.exists() || mafilesDir.listFiles()?.isEmpty() == true) {
            Toast.makeText(this, getString(R.string.export_mafiles_empty), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val tempExportDir = File(cacheDir, "export_temp_${System.currentTimeMillis()}")
            if (!tempExportDir.mkdirs()) {
                Toast.makeText(this, getString(R.string.export_dir_failed), Toast.LENGTH_SHORT).show()
                return
            }

            try {
                for (mafile in mafilesDir.listFiles() ?: emptyArray()) {
                    if (mafile.isFile && mafile.name.endsWith(".mafile", ignoreCase = true)) {
                        try {
                            val tempMafile = File(tempExportDir, mafile.name)
                            mafile.copyTo(tempMafile, overwrite = true)
                        } catch (_: Throwable) {
                            continue
                        }
                    }
                }

                val tempFiles = tempExportDir.listFiles()
                if (tempFiles == null || tempFiles.isEmpty()) {
                    Toast.makeText(this, getString(R.string.export_mafiles_empty), Toast.LENGTH_SHORT).show()
                    tempExportDir.deleteRecursively()
                    return
                }

                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val zipFileName = "MSDA_Backup_$timestamp.zip"
                val outputZipFile = File(cacheDir, zipFileName)

                createZipFile(tempExportDir, outputZipFile)
                shareFile(outputZipFile)
                Toast.makeText(this, getString(R.string.export_mafiles_success), Toast.LENGTH_SHORT).show()
            } finally {
                tempExportDir.deleteRecursively()
            }
        } catch (ex: Exception) {
            Toast.makeText(this, getString(R.string.export_error, ex.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun createZipFile(sourceDir: File, outputZipFile: File) {
        ZipOutputStream(FileOutputStream(outputZipFile)).use { zos ->
            sourceDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    file.inputStream().use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_backup)))
    }
}
