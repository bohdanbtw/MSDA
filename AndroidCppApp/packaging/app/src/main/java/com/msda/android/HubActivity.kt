package com.msda.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.msda.android.steam.NativeAuthBridge
import com.msda.android.steam.SessionHandler
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HubActivity : BaseLockActivity() {
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
        checkSessionAndPromptIfNeeded()
        UpdateChecker.checkOnLaunch(this)

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
        renderAccounts()
        checkSessionAndPromptIfNeeded()
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
        val label = if (steamId.isNotBlank()) AppSettings.getAccountLabel(this, steamId) else ""

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
                text = if (label.isNotBlank()) {
                    getString(R.string.hub_account_title_with_label, name, label)
                } else {
                    name
                }
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            if (steamId.isNotBlank()) {
                addView(TextView(this@HubActivity).apply {
                    text = steamId
                })
                addView(TextView(this@HubActivity).apply {
                    text = getString(R.string.account_label_edit)
                    textSize = 12f
                    setTextColor(0xFF6688AA.toInt())
                    setOnClickListener {
                        AccountLabelHelper.showEditDialog(this@HubActivity, steamId) {
                            renderAccounts()
                            Code2FAWidgetProvider.requestUpdateAll(this@HubActivity)
                        }
                    }
                })
            }

            setOnClickListener {
                if (index >= 0) {
                    NativeBridge.setActiveAccount(index)
                    startActivity(
                        Intent(this@HubActivity, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_ACCOUNT_INDEX, index)
                            .putExtra(MainActivity.EXTRA_ACCOUNT_NAME, name)
                            .putExtra(MainActivity.EXTRA_STEAM_ID, steamId)
                    )
                }
            }

            setOnLongClickListener {
                copyAccountCode(index, name, steamId)
                true
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

    private fun copyAccountCode(index: Int, name: String, steamId: String) {
        // Resolve index-only rows to a Steam ID without mutating the active account;
        // a background widget tick must never decide which code lands on the clipboard.
        val scopedSteamId = steamId.ifBlank { NativeBridge.steamIdForAccountIndex(index) }
        val code = try {
            if (scopedSteamId.isNotBlank()) NativeBridge.getCodeForSteamId(scopedSteamId).trim()
            else ""
        } catch (_: Throwable) {
            ""
        }

        if (code.isBlank()) {
            Toast.makeText(this, getString(R.string.code_copy_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), code))
        Toast.makeText(this, getString(R.string.code_copied_for_account, name), Toast.LENGTH_SHORT).show()
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

    private fun showDeleteAccountConfirmation(accountName: String) {
        val accountsRaw = try {
            NativeBridge.getAccounts()
        } catch (ex: Throwable) {
            txtHubStatus.text = getString(R.string.status_native_error, ex.message ?: "unknown")
            return
        }

        val targetSteamIds = AccountDeletionHelper.resolveTargetSteamIds(accountsRaw, accountName)
        if (targetSteamIds.isEmpty()) {
            val message = getString(R.string.status_delete_unresolved, accountName)
            txtHubStatus.text = message
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        val steamIdsText = getString(R.string.delete_confirm_steam_ids, targetSteamIds.joinToString("\n"))
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_message, accountName, steamIdsText))
            .setNegativeButton(R.string.delete_confirm_cancel) { _, _ -> }
            .setPositiveButton(R.string.delete_confirm_action) { _, _ ->
                deleteAccount(accountName, targetSteamIds)
            }
            .show()
    }

    private fun deleteAccount(accountName: String, targetSteamIds: List<String>) {
        val importDir = File(filesDir, "mafiles")
        if (accountName.isBlank() || !importDir.exists() || !importDir.isDirectory) return

        val steamIdsToPurge = try {
            NativeBridge.getAccounts().lines()
                .map { it.trim() }.filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('|')
                    val name = when {
                        parts.getOrNull(1).orEmpty().isNotBlank() -> parts[1]
                        parts.isNotEmpty() -> parts[0]
                        else -> line
                    }
                    val steamId = parts.getOrNull(2).orEmpty().trim()
                    if (name.equals(accountName, ignoreCase = true) && steamId.isNotBlank()) steamId else null
                }
        } catch (_: Throwable) {
            emptyList()
        }

        val mafiles = importDir.listFiles { file ->
            file.isFile && file.name.endsWith(".mafile", ignoreCase = true)
        }?.toList().orEmpty()

        val classification = AccountDeletionHelper.classifyFiles(mafiles, targetSteamIds)
        val issues = mutableListOf<String>()
        classification.unreadable.forEach { file ->
            issues.add(getString(R.string.status_delete_verify_failed, file.name))
        }

        var deletedCount = 0
        classification.toDelete.forEach { file ->
            if (file.delete()) {
                deletedCount++
            } else {
                issues.add(getString(R.string.status_delete_file_failed, file.name))
            }
        }

        // Delete only mafiles whose parsed steam ID belongs to the removed account.
        // A name/substring test (or an unreadable file) must never delete another account.
        mafiles.forEach { currentFile ->
            try {
                val steamId = MafileImportHelper.parseSteamId(currentFile)
                if (steamId != null && steamIdsToPurge.contains(steamId) && currentFile.delete()) {
                    deletedCount++
                }
            } catch (_: Throwable) {
            }
        }

        if (deletedCount > 0) {
            targetSteamIds.forEach { steamId ->
                try { SessionStore.delete(this, steamId) } catch (_: Throwable) {}
                try { AppSettings.clearAccountProxyConfig(this, steamId) } catch (_: Throwable) {}
                try { AppSettings.setAccountLabel(this, steamId, "") } catch (_: Throwable) {}
            }
            try { PasswordManager.deletePassword(this, accountName) } catch (_: Throwable) {}
            try {
                NativeBridge.importMafilesFromFolder(importDir.absolutePath)
            } catch (_: Throwable) {
                issues.add(getString(R.string.status_delete_reimport_failed))
            }
        }

        val summary = if (deletedCount > 0) {
            getString(R.string.status_delete_success, accountName, deletedCount)
        } else {
            getString(R.string.status_delete_nothing, accountName)
        }
        txtHubStatus.text = if (issues.isEmpty()) {
            summary
        } else {
            summary + "\n" + issues.joinToString("\n")
        }
        if (issues.isNotEmpty()) {
            Toast.makeText(this, txtHubStatus.text, Toast.LENGTH_LONG).show()
        }
        renderAccounts()
        Code2FAWidgetProvider.requestUpdateAll(this)
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

    private fun checkSessionAndPromptIfNeeded() {
        // Intentionally empty: do not renew sessions for every account on hub open.
    }

    fun handleNeedPassword(accountName: String) {
        SteamAuthService.showPasswordDialog(
            this,
            accountName,
            onResult = { result ->
                if (isFinishing || isDestroyed) return@showPasswordDialog
                if (!result.success) {
                    txtHubStatus.text = getString(R.string.status_login_failed, result.errorMessage.orEmpty())
                    return@showPasswordDialog
                }

                val sessionToSave = if (result.steamLoginSecure != null && result.sessionId != null) {
                    StoredSteamSession(
                        steamLoginSecure = result.steamLoginSecure,
                        sessionId = result.sessionId,
                        refreshToken = result.refreshToken.orEmpty(),
                        accessToken = result.accessToken.orEmpty(),
                        accountName = accountName,
                        sessionExpiresAtMs = result.sessionExpiresAtMs
                    )
                } else {
                    null
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    if (sessionToSave != null) {
                        SessionPersistence.saveSession(this@HubActivity, result.steamId.orEmpty(), sessionToSave)
                    }
                    withContext(Dispatchers.Main) {
                        if (isFinishing || isDestroyed) return@withContext
                        renderAccounts()
                        txtHubStatus.text = getString(R.string.status_login_saved)
                    }
                }
            },
            onProgress = { message ->
                txtHubStatus.text = message
            }
        )
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

        MafileImportHelper.clearSessionStoreForMafile(this, targetFile)

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
        if (imported) {
            promptLoginAfterImportIfNeeded(targetFile)
        }
    }

    private fun promptLoginAfterImportIfNeeded(mafile: File) {
        val steamId = MafileImportHelper.parseSteamId(mafile) ?: run {
            checkSessionAndPromptIfNeeded()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val base = NativeAuthBridge.confirmationAuthForSteamId(this@HubActivity, steamId) ?: return@launch
                var effective = base

                if (MafileImportHelper.canSilentRenew(this@HubActivity, effective)) {
                    val renewed = SessionHandler.ensureValid(this@HubActivity, effective)
                    if (renewed != effective) effective = renewed
                }

                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    when {
                        MafileImportHelper.hasUsableSession(effective) -> {
                            txtHubStatus.text = getString(R.string.status_import_success)
                            checkSessionAndPromptIfNeeded()
                        }
                        MafileImportHelper.needsInteractiveLogin(this@HubActivity, effective) -> {
                            handleNeedPassword(effective.accountName)
                        }
                        MafileImportHelper.canSilentRenew(this@HubActivity, effective) -> {
                            handleNeedPassword(effective.accountName)
                        }
                        else -> checkSessionAndPromptIfNeeded()
                    }
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) checkSessionAndPromptIfNeeded()
                }
            }
        }
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
