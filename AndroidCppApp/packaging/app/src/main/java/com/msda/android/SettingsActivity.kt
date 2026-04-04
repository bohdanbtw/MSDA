package com.msda.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

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
}
