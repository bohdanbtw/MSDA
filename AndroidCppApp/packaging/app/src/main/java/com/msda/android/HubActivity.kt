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

        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val accountContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
            background = getDrawable(android.R.drawable.list_selector_background)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

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

        val deleteButton = android.widget.Button(this).apply {
            text = "Delete"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                200,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.RED)
            setTextColor(android.graphics.Color.WHITE)
            visibility = View.GONE
            setOnClickListener {
                showDeleteAccountConfirmation(name)
            }
        }

        rowContainer.addView(accountContent)
        rowContainer.addView(deleteButton)

        var startX = 0f
        val deleteButtonWidth = 200f
        var isExpanded = false

        accountContent.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val currentX = event.x
                    val deltaX = startX - currentX
                    
                    if (kotlin.math.abs(deltaX) > 10) {
                        if (deltaX >= 0) {
                            deleteButton.visibility = View.VISIBLE
                            
                            val revealProgress = (deltaX / deleteButtonWidth).coerceIn(0f, 1f)
                            accountContent.translationX = -(revealProgress * deleteButtonWidth)
                            deleteButton.translationX = deleteButtonWidth - (revealProgress * deleteButtonWidth)
                        } else if (deltaX < 0 && isExpanded) {
                            val revealProgress = ((deleteButtonWidth + deltaX) / deleteButtonWidth).coerceIn(0f, 1f)
                            accountContent.translationX = -(revealProgress * deleteButtonWidth)
                            deleteButton.translationX = deleteButtonWidth - (revealProgress * deleteButtonWidth)
                        }
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val endX = event.x
                    val deltaX = kotlin.math.abs(startX - endX)
                    val revealPercentage = ((-accountContent.translationX) / deleteButtonWidth).coerceIn(0f, 1f)
                    
                    if (deltaX > 10) {
                        if (revealPercentage >= 0.5f) {
                            isExpanded = true
                            snapToExpanded(deleteButton, accountContent, deleteButtonWidth)
                        } else {
                            isExpanded = false
                            snapToCollapsed(deleteButton, accountContent)
                        }
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isExpanded) {
                        snapToExpanded(deleteButton, accountContent, deleteButtonWidth)
                    } else {
                        snapToCollapsed(deleteButton, accountContent)
                    }
                    true
                }
                else -> false
            }
        }

        return rowContainer
    }

    private fun snapToExpanded(deleteButton: android.widget.Button, content: LinearLayout, deleteButtonWidth: Float) {
        val contentAnimator = android.animation.ObjectAnimator.ofFloat(content, "translationX", content.translationX, -deleteButtonWidth)
        val buttonAnimator = android.animation.ObjectAnimator.ofFloat(deleteButton, "translationX", deleteButton.translationX, 0f)
        
        android.animation.AnimatorSet().apply {
            duration = 200
            playTogether(contentAnimator, buttonAnimator)
            start()
        }
    }

    private fun snapToCollapsed(deleteButton: android.widget.Button, content: LinearLayout) {
        val contentAnimator = android.animation.ObjectAnimator.ofFloat(content, "translationX", content.translationX, 0f)
        val buttonAnimator = android.animation.ObjectAnimator.ofFloat(deleteButton, "translationX", deleteButton.translationX, 200f)
        
        android.animation.AnimatorSet().apply {
            duration = 200
            playTogether(contentAnimator, buttonAnimator)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    deleteButton.visibility = View.GONE
                }
            })
            start()
        }
    }

    private fun animateRevealDelete(deleteButton: android.widget.Button, content: LinearLayout) {
        deleteButton.visibility = View.VISIBLE
        deleteButton.translationX = 200f

        val animationSet = android.animation.AnimatorSet()
        
        val deleteButtonSlide = android.animation.ObjectAnimator.ofFloat(deleteButton, "translationX", 200f, 0f)
        val contentSlide = android.animation.ObjectAnimator.ofFloat(content, "translationX", 0f, -200f)
        val deleteButtonFade = android.animation.ObjectAnimator.ofFloat(deleteButton, "alpha", 0f, 1f)
        
        animationSet.apply {
            duration = 350
            playTogether(deleteButtonSlide, contentSlide, deleteButtonFade)
            start()
        }
    }

    private fun animateHideDelete(deleteButton: android.widget.Button, content: LinearLayout) {
        val animationSet = android.animation.AnimatorSet()
        
        val deleteButtonSlide = android.animation.ObjectAnimator.ofFloat(deleteButton, "translationX", 0f, 200f)
        val contentSlide = android.animation.ObjectAnimator.ofFloat(content, "translationX", -200f, 0f)
        val deleteButtonFade = android.animation.ObjectAnimator.ofFloat(deleteButton, "alpha", 1f, 0f)
        
        animationSet.apply {
            duration = 350
            playTogether(deleteButtonSlide, contentSlide, deleteButtonFade)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    deleteButton.visibility = View.GONE
                    deleteButton.translationX = 0f
                }
            })
            start()
        }
    }

    private fun showDeleteAccountConfirmation(accountName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Delete account \"$accountName\"?\n\nIf you didn't save your mafile, you won't be able to access this account again.")
            .setNegativeButton("Cancel") { _, _ -> }
            .setPositiveButton("Delete account") { _, _ ->
                deleteAccount(accountName)
            }
            .show()
    }

    private fun deleteAccount(accountName: String) {
        val importDir = File(filesDir, "mafiles")
        if (!importDir.exists()) return

        val mafiles = importDir.listFiles { file ->
            file.isFile && file.name.endsWith(".mafile", ignoreCase = true)
        } ?: emptyArray()

        mafiles.forEach { currentFile ->
            try {
                if (currentFile.inputStream().use { it.readBytes().decodeToString().contains(accountName, ignoreCase = true) }) {
                    currentFile.delete()
                }
            } catch (_: Throwable) {
                try {
                    currentFile.delete()
                } catch (_: Throwable) {
                }
            }
        }

        try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
        }

        txtHubStatus.text = "Account deleted: $accountName"
        renderAccounts()
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
