package com.msda.android

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        BackgroundSyncScheduler.disable(this)

        val group = findViewById<RadioGroup>(R.id.radioThemeGroup)
        val txtPinLockStatus = findViewById<TextView>(R.id.txtPinLockStatus)
        val btnSetPin = findViewById<Button>(R.id.btnSetPinLock)
        val btnRemovePin = findViewById<Button>(R.id.btnRemovePinLock)
        val switchBiometric = findViewById<Switch>(R.id.switchBiometricUnlock)
        val btnExport = findViewById<Button>(R.id.btnExportMafiles)
        val btnDefaultProxy = findViewById<Button>(R.id.btnDefaultProxy)
        val txtDefaultProxyStatus = findViewById<TextView>(R.id.txtDefaultProxyStatus)
        val btnCheckUpdates = findViewById<Button>(R.id.btnCheckUpdates)
        val btnOpenRelease = findViewById<Button>(R.id.btnOpenRelease)
        val txtGithubLink = findViewById<TextView>(R.id.txtGithubLink)

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
            when (newMode) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            restartToHub()
        }

        btnSetPin.setOnClickListener {
            showPinSetupDialog(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
        }

        btnRemovePin.setOnClickListener {
            confirmPinRemoval(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
        }

        switchBiometric.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!buttonView.isPressed) {
                return@setOnCheckedChangeListener
            }

            if (!AppSettings.hasPinLock(this)) {
                switchBiometric.isChecked = false
                Toast.makeText(this, getString(R.string.biometric_requires_pin), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked && !isBiometricAvailable()) {
                switchBiometric.isChecked = false
                Toast.makeText(this, getString(R.string.biometric_unavailable), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            AppSettings.setBiometricUnlockEnabled(this, isChecked)
        }

        btnExport.setOnClickListener {
            exportMafiles()
        }

        btnDefaultProxy.setOnClickListener {
            showDefaultProxyDialog(txtDefaultProxyStatus)
        }

        btnCheckUpdates.setOnClickListener {
            UpdateChecker.checkManually(this)
        }

        btnOpenRelease.setOnClickListener {
            openLatestRelease()
        }

        txtGithubLink.setOnClickListener {
            openGitHubRepository()
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
        switchBiometric.isChecked = AppSettings.isBiometricUnlockEnabled(this)
    }

    private fun showPinSetupDialog(
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
                dialog.setOnShowListener {
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

        AlertDialog.Builder(this)
            .setTitle(R.string.pin_remove_confirm_title)
            .setMessage(R.string.pin_remove_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove_pin_lock) { _, _ ->
                AppSettings.clearPinLock(this)
                Toast.makeText(this, getString(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                refreshSecurityViews(txtPinLockStatus, btnSetPin, btnRemovePin, switchBiometric)
            }
            .show()
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
                Toast.makeText(this, "Failed to create export directory", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Export error: ${ex.message}", Toast.LENGTH_LONG).show()
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
        startActivity(Intent.createChooser(intent, "Share backup"))
    }

    private fun restartToHub() {
        val intent = Intent(this, HubActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun applyThemePreference() {
        when (AppSettings.getThemeMode(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun openGitHubRepository() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://github.com/bohdanbtw/MSDA")
        }
        startActivity(intent)
    }

    private fun openLatestRelease() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val url = try {
                UpdateChecker.fetchLatestRelease()?.htmlUrl
                    ?: "https://github.com/bohdanbtw/MSDA/releases/latest"
            } catch (_: Throwable) {
                "https://github.com/bohdanbtw/MSDA/releases/latest"
            }
            if (!isFinishing) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }
}
