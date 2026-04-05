package com.msda.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                AppSettings.setPushConfirmationsEnabled(this, false)
                findViewById<Switch>(R.id.switchPushConfirmations).isChecked = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val group = findViewById<RadioGroup>(R.id.radioThemeGroup)
        val switchBackground = findViewById<Switch>(R.id.switchBackgroundSync)
        val switchPush = findViewById<Switch>(R.id.switchPushConfirmations)
        val btnExport = findViewById<Button>(R.id.btnExportMafiles)
        val txtGithubLink = findViewById<android.widget.TextView>(R.id.txtGithubLink)

        when (AppSettings.getThemeMode(this)) {
            "light" -> group.check(R.id.radioThemeLight)
            "dark" -> group.check(R.id.radioThemeDark)
            else -> group.check(R.id.radioThemeSystem)
        }

        switchBackground.isChecked = AppSettings.isBackgroundSyncEnabled(this)
        switchPush.isChecked = AppSettings.isPushConfirmationsEnabled(this)
        switchPush.isEnabled = switchBackground.isChecked

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

        switchBackground.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setBackgroundSyncEnabled(this, isChecked)
            switchPush.isEnabled = isChecked
            if (!isChecked) {
                switchPush.isChecked = false
                AppSettings.setPushConfirmationsEnabled(this, false)
            }
            BackgroundSyncScheduler.configure(this)
        }

        switchPush.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            AppSettings.setPushConfirmationsEnabled(this, isChecked)
            BackgroundSyncScheduler.configure(this)
        }

        btnExport.setOnClickListener {
            exportMafiles()
        }

        txtGithubLink.setOnClickListener {
            openGitHubRepository()
        }
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
}
