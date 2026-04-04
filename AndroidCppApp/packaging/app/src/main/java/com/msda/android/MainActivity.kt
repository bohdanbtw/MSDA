package com.msda.android

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.io.File
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACCOUNT_INDEX = "extra_account_index"
        const val EXTRA_ACCOUNT_NAME = "extra_account_name"
    }

    private lateinit var txtStatus: TextView
    private lateinit var txtCode: TextView
    private lateinit var txtCodeTimer: TextView
    private lateinit var progressCodeWindow: ProgressBar
    private lateinit var txtConfirmationsHeader: TextView
    private lateinit var confirmationsContainer: LinearLayout
    private lateinit var authProgressRow: LinearLayout
    private lateinit var txtAuthProgress: TextView
    private lateinit var btnRefreshConfirmations: ImageButton

    private val uiHandler = Handler(Looper.getMainLooper())
    private var activeAuthContext: ConfirmationAuthContext? = null
    private var steamLoginInProgress = false
    private var lastSuccessfulBundles: List<ConfirmationBundle> = emptyList()
    private val expandedBundleKeys = mutableSetOf<String>()

    private val codeTicker = object : Runnable {
        override fun run() {
            refreshCodeViews()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private val confirmationsTicker = object : Runnable {
        override fun run() {
            refreshConfirmationsAsync()
            uiHandler.postDelayed(this, 15000)
        }
    }

    private val importMafileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            txtStatus.text = getString(R.string.status_import_cancelled)
            return@registerForActivityResult
        }

        importSelectedMafile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtCode = findViewById(R.id.txtCode)
        txtCodeTimer = findViewById(R.id.txtCodeTimer)
        progressCodeWindow = findViewById(R.id.progressCodeWindow)
        txtConfirmationsHeader = findViewById(R.id.txtConfirmationsHeader)
        confirmationsContainer = findViewById(R.id.confirmationsContainer)
        authProgressRow = findViewById(R.id.authProgressRow)
        txtAuthProgress = findViewById(R.id.txtAuthProgress)
        btnRefreshConfirmations = findViewById(R.id.btnRefreshConfirmations)

        val btnQuickActions = findViewById<ImageButton>(R.id.btnQuickActions)
        val txtAppTitle = findViewById<TextView>(R.id.txtAppTitle)

        val selectedIndex = intent.getIntExtra(EXTRA_ACCOUNT_INDEX, -1)
        if (selectedIndex >= 0) {
            NativeBridge.setActiveAccount(selectedIndex)
            val selectedName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
            if (selectedName.isNotBlank()) {
                txtAppTitle.text = selectedName
            }
        }

        val startupLoaded = loadPersistedMafiles()
        txtStatus.text = if (startupLoaded) {
            getString(R.string.status_saved_mafiles_loaded)
        } else {
            getString(R.string.status_started)
        }

        refreshCodeViews()
        updateActiveAuthContext()
        refreshConfirmationsAsync()
        BackgroundSyncScheduler.configure(this)

        txtCode.setOnClickListener {
            copyCurrentCodeToClipboard()
        }

        btnQuickActions.setOnClickListener { showQuickActionsMenu(it) }

        btnRefreshConfirmations.setOnClickListener {
            txtStatus.text = getString(R.string.status_loading_confirmations)
            refreshConfirmationsAsync()
        }
    }

    private fun applyThemePreference() {
        when (AppSettings.getThemeMode(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(codeTicker)
        uiHandler.post(confirmationsTicker)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(codeTicker)
        uiHandler.removeCallbacks(confirmationsTicker)
    }

    private fun copyCurrentCodeToClipboard() {
        val code = try {
            NativeBridge.getActiveCode().trim()
        } catch (_: Throwable) {
            ""
        }

        if (code.isBlank()) {
            return
        }

        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("MSDA 2FA Code", code))
        Toast.makeText(this, getString(R.string.code_copied), Toast.LENGTH_LONG).show()
    }

    private fun showQuickActionsMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, getString(R.string.login_steam))
        menu.menu.add(0, 3, 1, getString(R.string.back_to_hub))
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> promptSteamLogin()
                3 -> finish()
            }
            true
        }
        menu.show()
    }

    private fun updateActiveAuthContext() {
        val payload = try {
            NativeBridge.getActiveConfirmationAuthPayload()
        } catch (_: Throwable) {
            ""
        }

        val parsed = ConfirmationService.parseAuthPayload(payload)
        activeAuthContext = parsed?.let { context ->
            val saved = SessionStore.loadSession(this, context.steamId)
            if (saved != null) context.withSession(saved) else context
        }
    }

    private fun promptSteamLogin() {
        val auth = activeAuthContext
        if (auth == null) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        if (steamLoginInProgress) {
            return
        }

        steamLoginInProgress = true
        setAuthenticatingUi(true)

        SteamAuthService.showPasswordDialog(
            context = this,
            accountName = auth.accountName,
            onResult = { result ->
                steamLoginInProgress = false
                setAuthenticatingUi(false)

                if (result.success && result.steamId != null && result.steamLoginSecure != null && result.sessionId != null) {
                    SessionStore.saveSession(
                        this,
                        result.steamId,
                        StoredSteamSession(
                            steamLoginSecure = result.steamLoginSecure,
                            sessionId = result.sessionId
                        )
                    )
                    updateActiveAuthContext()
                    txtStatus.text = getString(R.string.status_login_saved)
                    refreshConfirmationsAsync()
                } else {
                    txtStatus.text = formatLoginError(result.errorMessage)
                }
            },
            onProgress = { message ->
                txtAuthProgress.text = message
                txtStatus.text = message
            }
        )
    }

    private fun setAuthenticatingUi(visible: Boolean) {
        authProgressRow.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            txtAuthProgress.text = getString(R.string.status_authentificating)
            txtStatus.text = getString(R.string.status_authentificating)
        }
    }

    private fun formatLoginError(rawError: String?): String {
        val message = rawError?.trim().orEmpty()
        if (message.isBlank()) {
            return getString(R.string.status_login_failed_generic)
        }

        val normalized = message.lowercase()
        if (normalized.startsWith("steam login failed") || normalized.startsWith("stem login failed")) {
            return message.replaceFirst("Stem login failed", "Steam login failed", ignoreCase = true)
        }

        return getString(R.string.status_login_failed, message)
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
            txtStatus.text = getString(R.string.status_invalid_file)
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
            txtStatus.text = getString(R.string.status_copy_failed)
            return
        }

        val imported = try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
            false
        }

        txtStatus.text = if (imported) {
            getString(R.string.status_import_success)
        } else {
            getString(R.string.status_import_failed)
        }

        refreshCodeViews()
        updateActiveAuthContext()
        refreshConfirmationsAsync()

        if (imported) {
            promptSteamLogin()
        }
    }

    private fun refreshCodeViews() {
        val code = try {
            NativeBridge.getActiveCode()
        } catch (_: Throwable) {
            ""
        }

        val secondsRemaining = try {
            NativeBridge.getSecondsToNextCode()
        } catch (_: Throwable) {
            30
        }

        if (code.isBlank()) {
            txtCode.text = getString(R.string.code_missing)
        } else {
            txtCode.text = getString(R.string.code_value, code)
        }

        val safeRemaining = secondsRemaining.coerceIn(1, 30)
        txtCodeTimer.text = getString(R.string.code_timer_value, safeRemaining.toString())
        progressCodeWindow.max = 30
        progressCodeWindow.progress = (30 - safeRemaining).coerceIn(0, 30)
    }

    private fun refreshConfirmationsAsync() {
        if (steamLoginInProgress) {
            return
        }

        val auth = activeAuthContext ?: run {
            lastSuccessfulBundles = emptyList()
            renderBundles(emptyList())
            return
        }

        Thread {
            var error: String? = null
            val bundles = try {
                ConfirmationService.loadBundles(auth)
            } catch (ex: Throwable) {
                error = ex.message ?: "Unknown confirmation error"
                null
            }

            runOnUiThread {
                if (error != null) {
                    txtStatus.text = getString(R.string.status_confirmation_load_failed, error)
                    if (error!!.contains("needauth", ignoreCase = true)) {
                        promptSteamLogin()
                    }
                    // Keep current list visible on transient errors.
                    return@runOnUiThread
                }

                val stable = bundles ?: emptyList()
                lastSuccessfulBundles = stable
                val existingKeys = stable.map { it.key }.toSet()
                expandedBundleKeys.retainAll(existingKeys)
                renderBundles(stable)
            }
        }.start()
    }

    private fun renderBundles(bundles: List<ConfirmationBundle>) {
        confirmationsContainer.removeAllViews()

        txtConfirmationsHeader.visibility = View.VISIBLE
        btnRefreshConfirmations.visibility = View.VISIBLE

        if (bundles.isEmpty()) {
            confirmationsContainer.visibility = View.GONE
            return
        }

        confirmationsContainer.visibility = View.VISIBLE

        for (bundle in bundles) {
            confirmationsContainer.addView(createBundleView(bundle))
        }
    }

    private fun createBundleView(bundle: ConfirmationBundle): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 12
            layoutParams = params
            background = getDrawable(R.drawable.bg_surface_card)
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnAcceptBundle = ImageButton(this).apply {
            setImageResource(android.R.drawable.checkbox_on_background)
            contentDescription = getString(R.string.confirm_accept)
            setBackgroundColor(0x00000000)
            setOnClickListener { respondToBundle(bundle, true) }
        }

        val btnDeclineBundle = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            contentDescription = getString(R.string.confirm_decline)
            setBackgroundColor(0x00000000)
            setOnClickListener { respondToBundle(bundle, false) }
        }

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.weight = 1f
            layoutParams = params
            setPadding(12, 0, 12, 0)
        }

        val title = TextView(this).apply {
            text = bundle.typeName
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val count = TextView(this).apply {
            text = "${bundle.items.size} item(s)"
        }

        center.addView(title)
        center.addView(count)

        val expanded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expandedBundleKeys.contains(bundle.key)) View.VISIBLE else View.GONE
            setPadding(0, 10, 0, 0)
        }

        bundle.items.forEachIndexed { index, item ->
            expanded.addView(createConfirmationItemRow(item))
            if (index < bundle.items.lastIndex) {
                expanded.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0x22000000)
                })
            }
        }

        center.setOnClickListener {
            if (expanded.visibility == View.VISIBLE) {
                expanded.visibility = View.GONE
                expandedBundleKeys.remove(bundle.key)
            } else {
                expanded.visibility = View.VISIBLE
                expandedBundleKeys.add(bundle.key)
            }
        }

        row.addView(btnAcceptBundle)
        row.addView(center)
        row.addView(btnDeclineBundle)

        card.addView(row)
        card.addView(expanded)

        return card
    }

    private fun createConfirmationItemRow(item: ConfirmationItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(72, 72)
            setImageResource(android.R.drawable.sym_def_app_icon)
        }

        if (!item.iconUrl.isNullOrBlank()) {
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(item.iconUrl).openStream())
                    runOnUiThread { icon.setImageBitmap(bmp) }
                } catch (_: Throwable) {
                }
            }.start()
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12, 0, 12, 0)
        }

        val headline = TextView(this).apply {
            text = item.headline.ifBlank { item.typeName }
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val summaryText = item.summary.filter { it.isNotBlank() }.joinToString(" · ")
        val summary = TextView(this).apply {
            text = summaryText
        }

        textColumn.addView(headline)
        if (summaryText.isNotBlank()) {
            textColumn.addView(summary)
        }

        val btnAccept = ImageButton(this).apply {
            setImageResource(android.R.drawable.checkbox_on_background)
            contentDescription = getString(R.string.confirm_accept)
            setBackgroundColor(0x00000000)
            setOnClickListener { respondToItem(item, true) }
        }

        val btnDecline = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_delete)
            contentDescription = getString(R.string.confirm_decline)
            setBackgroundColor(0x00000000)
            setOnClickListener { respondToItem(item, false) }
        }

        row.addView(icon)
        row.addView(textColumn)
        row.addView(btnAccept)
        row.addView(btnDecline)

        return row
    }

    private fun respondToBundle(bundle: ConfirmationBundle, accept: Boolean) {
        val auth = activeAuthContext ?: return

        Thread {
            val ok = try {
                ConfirmationService.respondBundle(auth, bundle, accept)
            } catch (_: Throwable) {
                false
            }

            runOnUiThread {
                txtStatus.text = if (ok) {
                    if (accept) getString(R.string.status_confirmation_accepted) else getString(R.string.status_confirmation_declined)
                } else {
                    getString(R.string.status_confirmation_failed)
                }

                refreshConfirmationsAsync()
            }
        }.start()
    }

    private fun respondToItem(item: ConfirmationItem, accept: Boolean) {
        val auth = activeAuthContext ?: return

        Thread {
            val ok = try {
                ConfirmationService.respondItem(auth, item, accept)
            } catch (_: Throwable) {
                false
            }

            runOnUiThread {
                txtStatus.text = if (ok) {
                    if (accept) getString(R.string.status_confirmation_accepted) else getString(R.string.status_confirmation_declined)
                } else {
                    getString(R.string.status_confirmation_failed)
                }

                refreshConfirmationsAsync()
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return null

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index < 0) {
                return null
            }

            return it.getString(index)
        }
    }
}
