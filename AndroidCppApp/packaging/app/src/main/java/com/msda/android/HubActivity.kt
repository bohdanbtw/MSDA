package com.msda.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

class HubActivity : AppCompatActivity() {
    private lateinit var txtHubStatus: TextView
    private lateinit var txtAccountsEmpty: TextView
    private lateinit var accountsListContainer: LinearLayout

    private val importMafileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            txtHubStatus.text = getString(R.string.status_import_cancelled)
            return@registerForActivityResult
        }

        importSelectedMafile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        txtHubStatus = findViewById(R.id.txtHubStatus)
        txtAccountsEmpty = findViewById(R.id.txtHubAccountsEmpty)
        accountsListContainer = findViewById(R.id.hubAccountsListContainer)

        val btnAddAccount = findViewById<Button>(R.id.btnHubAddAccount)
        val btnSettings = findViewById<ImageButton>(R.id.btnHubSettings)

        val startupLoaded = loadPersistedMafiles()
        txtHubStatus.text = if (startupLoaded) {
            getString(R.string.status_saved_mafiles_loaded)
        } else {
            getString(R.string.hub_welcome)
        }

        renderAccounts()
        BackgroundSyncScheduler.configure(this)

        btnAddAccount.setOnClickListener {
            importMafileLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemePreference()
        BackgroundSyncScheduler.configure(this)
        renderAccounts()
    }

    private fun applyThemePreference() {
        when (AppSettings.getThemeMode(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun renderAccounts() {
        val accountsRaw = try {
            NativeBridge.getAccounts()
        } catch (ex: Throwable) {
            txtHubStatus.text = getString(R.string.status_native_error, ex.message ?: "unknown")
            ""
        }

        val rows = accountsRaw
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        accountsListContainer.removeAllViews()

        if (rows.isEmpty()) {
            txtAccountsEmpty.visibility = View.VISIBLE
            accountsListContainer.visibility = View.GONE
            return
        }

        txtAccountsEmpty.visibility = View.GONE
        accountsListContainer.visibility = View.VISIBLE

        for (line in rows) {
            val row = createAccountRow(line)
            accountsListContainer.addView(row)
        }
    }

    private fun createAccountRow(line: String): View {
        val parts = line.split('|')
        val index = parts.firstOrNull()?.toIntOrNull() ?: -1
        val name = when {
            parts.size >= 2 && parts[1].isNotBlank() -> parts[1]
            parts.isNotEmpty() -> parts[0]
            else -> line
        }
        val steamId = if (parts.size >= 3) parts[2] else ""

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 14)
            background = getDrawable(android.R.drawable.list_selector_background)

            addView(TextView(this@HubActivity).apply {
                text = name
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            if (steamId.isNotBlank()) {
                addView(TextView(this@HubActivity).apply {
                    text = steamId
                })
            }

            setOnClickListener {
                if (index >= 0) {
                    NativeBridge.setActiveAccount(index)
                    startActivity(
                        Intent(this@HubActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_ACCOUNT_INDEX, index)
                            .putExtra(MainActivity.EXTRA_ACCOUNT_NAME, name)
                    )
                }
            }
        }
    }

    private fun loadPersistedMafiles(): Boolean {
        val importDir = File(filesDir, "mafiles")
        if (!importDir.exists() || !importDir.isDirectory) {
            return false
        }

        val hasMafiles = importDir.listFiles()?.any { it.isFile && it.name.endsWith(".mafile", ignoreCase = true) } == true
        if (!hasMafiles) {
            return false
        }

        return try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
            false
        }
    }

    private fun importSelectedMafile(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: "imported.mafile"
        if (!fileName.endsWith(".mafile", ignoreCase = true)) {
            txtHubStatus.text = getString(R.string.status_invalid_file)
            return
        }

        val importDir = File(filesDir, "mafiles")
        if (!importDir.exists()) {
            importDir.mkdirs()
        }

        val targetFile = File(importDir, fileName)

        val copied = try {
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Throwable) {
            false
        }

        if (!copied) {
            txtHubStatus.text = getString(R.string.status_copy_failed)
            return
        }

        val imported = try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
            false
        }

        txtHubStatus.text = if (imported) {
            getString(R.string.status_import_success)
        } else {
            getString(R.string.status_import_failed)
        }

        renderAccounts()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return null

        cursor.use {
            if (!it.moveToFirst()) return null
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index < 0) return null
            return it.getString(index)
        }
    }
}
