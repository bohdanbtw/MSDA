package com.msda.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Base64

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
    private lateinit var imgProxyStatus: ImageView

    private val uiHandler = Handler(Looper.getMainLooper())
    private var activeAuthContext: ConfirmationAuthContext? = null
    private var currentAccountIndex: Int = -1
    private var currentAccountName: String = ""
    private var steamLoginInProgress = false
    private var proxyCheckInProgress = false
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
            uiHandler.postDelayed(this, 5000)
        }
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val scannedText = result.contents?.trim().orEmpty()
        when {
            scannedText.isBlank() -> txtStatus.text = getString(R.string.status_qr_scan_cancelled)
            !QrApprovalService.looksLikeSteamLoginQr(scannedText) -> txtStatus.text = getString(R.string.status_qr_invalid)
            else -> authorizeSteamQr(scannedText)
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
        imgProxyStatus = findViewById(R.id.imgProxyStatus)

        val btnScanQr = findViewById<ImageButton>(R.id.btnScanQr)
        val btnQuickActions = findViewById<ImageButton>(R.id.btnQuickActions)
        val btnAutoConfirm = findViewById<android.widget.Button>(R.id.btnAutoConfirm)
        val txtAppTitle = findViewById<TextView>(R.id.txtAppTitle)

        val selectedIndex = intent.getIntExtra(EXTRA_ACCOUNT_INDEX, -1)
        if (selectedIndex >= 0) {
            currentAccountIndex = selectedIndex
            currentAccountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
            NativeBridge.setActiveAccount(selectedIndex)
            if (currentAccountName.isNotBlank()) {
                txtAppTitle.text = currentAccountName
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
        refreshProxyIndicatorAsync()
        BackgroundSyncScheduler.configure(this)

        txtCode.setOnClickListener {
            copyCurrentCodeToClipboard()
        }

        btnScanQr.setOnClickListener {
            startQrScanner()
        }

        btnAutoConfirm.setOnClickListener {
            showMarketAutoConfirmDialog()
        }

        imgProxyStatus.setOnClickListener {
            showProxySettingsDialog()
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
        updateActiveAuthContext()
        refreshProxyIndicatorAsync()
        uiHandler.post(codeTicker)
        uiHandler.post(confirmationsTicker)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            val selectedIndex = intent.getIntExtra(EXTRA_ACCOUNT_INDEX, -1)
            if (selectedIndex >= 0) {
                currentAccountIndex = selectedIndex
                currentAccountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
                NativeBridge.setActiveAccount(selectedIndex)
                val txtAppTitle = findViewById<TextView>(R.id.txtAppTitle)
                if (currentAccountName.isNotBlank()) {
                    txtAppTitle.text = currentAccountName
                }
                refreshCodeViews()
                updateActiveAuthContext()
                refreshConfirmationsAsync()
                refreshProxyIndicatorAsync()
                txtStatus.text = "Loaded account: $currentAccountName"
            }
        }
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

    private fun startQrScanner() {
        val auth = activeAuthContext
        if (auth == null) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_steam_qr))
            setBeepEnabled(false)
            setOrientationLocked(true)
            addExtra(Intents.Scan.CAMERA_ID, 0)
            addExtra("SCAN_ORIENTATION_LOCKED", true)
        }
        qrScanLauncher.launch(options)
    }

    private fun authorizeSteamQr(scannedText: String) {
        if (currentAccountIndex < 0) {
            txtStatus.text = "Error: No account selected"
            return
        }
        
        NativeBridge.setActiveAccount(currentAccountIndex)
        updateActiveAuthContext()
        
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
        txtStatus.text = getString(R.string.status_qr_authorizing)

        Thread {
            val result = QrApprovalService.approveLoginRequest(this, auth, scannedText)
            runOnUiThread {
                steamLoginInProgress = false
                setAuthenticatingUi(false)
                txtStatus.text = when {
                    result.success -> getString(R.string.status_qr_authorized)
                    result.errorMessage == QrApprovalService.ERROR_NO_REQUESTS -> getString(R.string.status_qr_no_requests)
                    result.errorMessage == QrApprovalService.ERROR_MULTIPLE_REQUESTS -> getString(R.string.status_qr_multiple_requests)
                    result.errorMessage == QrApprovalService.ERROR_TOKEN_MISSING -> getString(R.string.status_qr_token_missing)
                    result.errorMessage == QrApprovalService.ERROR_INVALID_QR -> getString(R.string.status_qr_invalid)
                    else -> result.errorMessage ?: getString(R.string.status_confirmation_failed)
                }
            }
        }.start()
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

    private fun showQuickActionsMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, getString(R.string.back_to_hub))
        menu.menu.add(0, 2, 1, getString(R.string.import_mafile))
        menu.menu.add(0, 6, 2, getString(R.string.export_single_mafile))
        menu.menu.add(0, 7, 3, getString(R.string.auto_market_confirmations))
        menu.menu.add(0, 8, 4, getString(R.string.proxy_settings))
        menu.menu.add(0, 3, 5, getString(R.string.login_steam))
        menu.menu.add(0, 4, 6, getString(R.string.load_confirmations))
        menu.menu.add(0, 5, 7, getString(R.string.settings))

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    startActivity(Intent(this, HubActivity::class.java))
                    finish()
                    true
                }
                2 -> {
                    importMafileLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                    true
                }
                6 -> {
                    exportCurrentAccountMafile()
                    true
                }
                7 -> {
                    showMarketAutoConfirmDialog()
                    true
                }
                8 -> {
                    showProxySettingsDialog()
                    true
                }
                3 -> {
                    promptSteamLogin()
                    true
                }
                4 -> {
                    txtStatus.text = getString(R.string.status_loading_confirmations)
                    refreshConfirmationsAsync()
                    true
                }
                5 -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        menu.show()
    }

    private fun showMarketAutoConfirmDialog() {
        updateActiveAuthContext()
        val auth = activeAuthContext
        if (auth == null || auth.steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        val marketSwitch = Switch(this).apply {
            text = getString(R.string.allow_market_confirmations_on_account)
            isChecked = AppSettings.isMarketAutoConfirmEnabled(this@MainActivity, auth.steamId)
        }

        val tradeSwitch = Switch(this).apply {
            text = getString(R.string.allow_trade_confirmations_on_account)
            isChecked = AppSettings.isTradeAutoConfirmEnabled(this@MainActivity, auth.steamId)
        }

        val giftTradeSwitch = Switch(this).apply {
            text = getString(R.string.allow_gift_trade_confirmations_on_account)
            isChecked = AppSettings.isGiftTradeAutoConfirmEnabled(this@MainActivity, auth.steamId)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(marketSwitch)
            addView(tradeSwitch)
            addView(giftTradeSwitch)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_market_confirmations))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AppSettings.setMarketAutoConfirmEnabled(this, auth.steamId, marketSwitch.isChecked)
                AppSettings.setTradeAutoConfirmEnabled(this, auth.steamId, tradeSwitch.isChecked)
                AppSettings.setGiftTradeAutoConfirmEnabled(this, auth.steamId, giftTradeSwitch.isChecked)

                txtStatus.text = when {
                    marketSwitch.isChecked && tradeSwitch.isChecked && giftTradeSwitch.isChecked -> getString(R.string.auto_confirm_all_types_enabled)
                    marketSwitch.isChecked && tradeSwitch.isChecked -> getString(R.string.auto_confirm_market_trade_enabled)
                    marketSwitch.isChecked && giftTradeSwitch.isChecked -> getString(R.string.auto_confirm_market_gift_enabled)
                    tradeSwitch.isChecked && giftTradeSwitch.isChecked -> getString(R.string.auto_confirm_trade_gift_enabled)
                    marketSwitch.isChecked -> getString(R.string.market_auto_confirm_enabled)
                    tradeSwitch.isChecked -> getString(R.string.trade_auto_confirm_enabled)
                    giftTradeSwitch.isChecked -> getString(R.string.gift_trade_auto_confirm_enabled)
                    else -> getString(R.string.auto_confirm_all_disabled)
                }

                if (marketSwitch.isChecked || tradeSwitch.isChecked || giftTradeSwitch.isChecked) {
                    BackgroundSyncScheduler.configure(this)
                }
            }
            .show()
    }

    private fun exportCurrentAccountMafile() {
        val mafilesDir = File(filesDir, "mafiles")
        
        if (!mafilesDir.exists() || mafilesDir.listFiles()?.isEmpty() == true) {
            txtStatus.text = getString(R.string.export_mafiles_empty)
            return
        }

        try {
            val mafiles = mafilesDir.listFiles()?.filter { 
                it.isFile && it.name.endsWith(".mafile", ignoreCase = true) 
            } ?: emptyList()

            if (mafiles.isEmpty()) {
                txtStatus.text = getString(R.string.export_mafiles_empty)
                return
            }

            val tempExportDir = File(cacheDir, "export_temp_${System.currentTimeMillis()}")
            if (!tempExportDir.mkdirs()) {
                txtStatus.text = "Failed to create export directory"
                return
            }

            try {
                var exported = false
                for (mafile in mafiles) {
                    try {
                        val content = mafile.readText()
                        if (content.contains(currentAccountName, ignoreCase = true)) {
                            val tempMafile = File(tempExportDir, mafile.name)
                            tempMafile.writeText(content)
                            exported = true
                            break
                        }
                    } catch (_: Throwable) {
                        continue
                    }
                }

                if (!exported) {
                    txtStatus.text = "Could not find mafile for current account"
                    tempExportDir.deleteRecursively()
                    return
                }

                val timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val zipFileName = "${currentAccountName}_$timestamp.zip"
                val outputZipFile = File(cacheDir, zipFileName)

                createZipFile(tempExportDir, outputZipFile)
                shareFile(outputZipFile)
                
                txtStatus.text = getString(R.string.export_mafiles_success)
            } finally {
                tempExportDir.deleteRecursively()
            }
        } catch (ex: Exception) {
            txtStatus.text = "Export error: ${ex.message}"
        }
    }

    private fun createZipFile(sourceDir: File, outputZipFile: File) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(outputZipFile)).use { zos ->
            sourceDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val entry = java.util.zip.ZipEntry(file.name)
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
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share mafile backup"))
    }

    private fun updateActiveAuthContext() {
        val payload = try {
            NativeBridge.getActiveConfirmationAuthPayload()
        } catch (_: Throwable) {
            ""
        }

        val parsed = ConfirmationService.parseAuthPayload(payload)
        activeAuthContext = if (parsed == null) {
            null
        } else {
            val savedSession = SessionStore.loadSession(this, parsed.steamId)
            if (savedSession != null) parsed.withSession(savedSession) else parsed
        }
    }

    private fun promptSteamLogin() {
        if (steamLoginInProgress) {
            return
        }

        if (activeAuthContext == null) {
            updateActiveAuthContext()
        }

        val auth = activeAuthContext
        if (auth == null) {
            txtStatus.text = getString(R.string.status_login_unavailable)
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

                if (!result.success) {
                    txtStatus.text = if (result.errorMessage.equals("Login cancelled", ignoreCase = true)) {
                        getString(R.string.status_login_cancelled)
                    } else {
                        formatLoginError(result.errorMessage)
                    }
                    return@showPasswordDialog
                }

                val steamId = result.steamId?.ifBlank { auth.steamId } ?: auth.steamId
                val loginSecure = result.steamLoginSecure.orEmpty()
                val sessionId = (result.sessionId ?: auth.sessionId).orEmpty()

                if (steamId.isBlank() || loginSecure.isBlank() || sessionId.isBlank()) {
                    txtStatus.text = getString(R.string.status_login_capture_failed)
                    return@showPasswordDialog
                }

                SessionStore.saveSession(
                    this,
                    steamId,
                    StoredSteamSession(
                        steamLoginSecure = loginSecure,
                        sessionId = sessionId,
                        refreshToken = result.refreshToken.orEmpty(),
                        accessToken = result.accessToken.orEmpty()
                    )
                )

                updateActiveAuthContext()
                txtStatus.text = getString(R.string.status_login_saved)
                refreshConfirmationsAsync()
            },
            onProgress = { progressText ->
                txtAuthProgress.text = progressText
                txtStatus.text = progressText
            }
        )
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
            var bundles: List<ConfirmationBundle>? = try {
                ConfirmationService.loadBundles(this, auth)
            } catch (ex: Throwable) {
                error = ex.message ?: "Unknown confirmation error"
                null
            }

            if (error == null && bundles != null) {
                try {
                    val marketEnabled = AppSettings.isMarketAutoConfirmEnabled(this, auth.steamId)
                    val tradeEnabled = AppSettings.isTradeAutoConfirmEnabled(this, auth.steamId)
                    val giftTradeEnabled = AppSettings.isGiftTradeAutoConfirmEnabled(this, auth.steamId)
                    if (marketEnabled || tradeEnabled || giftTradeEnabled) {
                        val itemsToAutoAccept = bundles
                            .flatMap { it.items }
                            .filter { item ->
                                (marketEnabled && isStrictMarketConfirmation(item)) ||
                                    (tradeEnabled && isStrictTradeConfirmation(item)) ||
                                    (giftTradeEnabled && isGiftTradeConfirmation(item))
                            }
                            .distinctBy { it.id }

                        itemsToAutoAccept.forEach { item ->
                            try {
                                ConfirmationService.respondItem(this, auth, item, true)
                            } catch (_: Throwable) {
                            }
                        }

                        if (itemsToAutoAccept.isNotEmpty()) {
                            bundles = try {
                                ConfirmationService.loadBundles(this, auth)
                            } catch (_: Throwable) {
                                bundles
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            runOnUiThread {
                if (error != null) {
                    txtStatus.text = getString(R.string.status_confirmation_load_failed, error)
                    if (error.contains("needauth", ignoreCase = true)) {
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

    private fun isStrictMarketConfirmation(item: ConfirmationItem): Boolean {
        return item.type != 2 && item.typeName.contains("market", ignoreCase = true)
    }

    private fun isStrictTradeConfirmation(item: ConfirmationItem): Boolean {
        return item.type == 2 || item.typeName.contains("trade", ignoreCase = true)
    }

    private fun isGiftTradeConfirmation(item: ConfirmationItem): Boolean {
        if (!isStrictTradeConfirmation(item)) {
            return false
        }

        val text = (listOf(item.headline) + item.summary)
            .joinToString(" ")
            .lowercase()

        val giftSignals = listOf(
            "gift",
            "you will receive",
            "you'll receive",
            "for free",
            "without exchange",
            "no items from your inventory",
            "no items from you",
            "0 items from you",
            "nothing to give",
            "sent you a gift"
        )
        val lossSignals = listOf(
            "you will give",
            "you'll give",
            "you are giving",
            "from your inventory",
            "in exchange",
            "for your"
        )

        return giftSignals.any { text.contains(it) } && lossSignals.none { text.contains(it) }
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
                ConfirmationService.respondBundle(this, auth, bundle, accept)
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
                ConfirmationService.respondItem(this, auth, item, accept)
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

    private fun showProxySettingsDialog() {
        updateActiveAuthContext()
        val steamId = activeAuthContext?.steamId.orEmpty()
        if (steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        val current = AppSettings.getAccountProxyConfig(this, steamId)

        val enabledSwitch = Switch(this).apply {
            text = getString(R.string.proxy_enable_on_account)
            isChecked = current.enabled
        }

        val typeGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        val httpOption = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.proxy_type_http)
        }
        val socksOption = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.proxy_type_socks)
        }
        typeGroup.addView(httpOption)
        typeGroup.addView(socksOption)
        if (current.type.equals("socks", ignoreCase = true)) {
            typeGroup.check(socksOption.id)
        } else {
            typeGroup.check(httpOption.id)
        }

        val hostInput = EditText(this).apply {
            hint = getString(R.string.proxy_host_hint)
            setText(current.host)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val portInput = EditText(this).apply {
            hint = getString(R.string.proxy_port_hint)
            setText(if (current.port > 0) current.port.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val userInput = EditText(this).apply {
            hint = getString(R.string.proxy_username_hint)
            setText(current.username)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val passInput = EditText(this).apply {
            hint = getString(R.string.proxy_password_hint)
            setText(current.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(enabledSwitch)
            addView(typeGroup)
            addView(hostInput)
            addView(portInput)
            addView(userInput)
            addView(passInput)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.proxy_settings))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.proxy_clear) { _, _ ->
                AppSettings.clearAccountProxyConfig(this, steamId)
                refreshProxyIndicatorAsync()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val enabled = enabledSwitch.isChecked
                val host = hostInput.text?.toString().orEmpty().trim()
                val port = portInput.text?.toString().orEmpty().toIntOrNull() ?: 0
                val user = userInput.text?.toString().orEmpty().trim()
                val pass = passInput.text?.toString().orEmpty()
                val type = if (typeGroup.checkedRadioButtonId == socksOption.id) "socks" else "http"

                if (enabled && (host.isBlank() || port !in 1..65535)) {
                    txtStatus.text = getString(R.string.proxy_invalid)
                    return@setPositiveButton
                }

                AppSettings.setAccountProxyConfig(
                    this,
                    steamId,
                    AccountProxyConfig(
                        enabled = enabled,
                        type = type,
                        host = host,
                        port = port,
                        username = user,
                        password = pass
                    )
                )
                txtStatus.text = getString(R.string.proxy_saved)
                refreshProxyIndicatorAsync()
            }
            .show()
    }

    private fun refreshProxyIndicatorAsync() {
        val auth = activeAuthContext
        val steamId = auth?.steamId.orEmpty()
        if (steamId.isBlank()) {
            hideProxyIndicator(getString(R.string.proxy_status_unknown))
            return
        }

        val config = AppSettings.getAccountProxyConfig(this, steamId)
        if (!config.enabled || config.host.isBlank() || config.port !in 1..65535) {
            hideProxyIndicator(getString(R.string.proxy_status_disabled))
            return
        }

        if (proxyCheckInProgress) {
            return
        }

        imgProxyStatus.visibility = View.VISIBLE
        proxyCheckInProgress = true
        txtStatus.text = getString(R.string.proxy_checking)

        Thread {
            val ok = isProxyWorking(config)
            runOnUiThread {
                proxyCheckInProgress = false
                if (ok) {
                    setProxyIndicator(true, getString(R.string.proxy_status_working))
                } else {
                    setProxyIndicator(false, getString(R.string.proxy_status_failed))
                }
            }
        }.start()
    }

    private fun setProxyIndicator(working: Boolean, description: String) {
        imgProxyStatus.visibility = View.VISIBLE
        imgProxyStatus.setImageResource(if (working) android.R.drawable.presence_online else android.R.drawable.presence_busy)
        imgProxyStatus.contentDescription = description
    }

    private fun hideProxyIndicator(description: String) {
        imgProxyStatus.visibility = View.GONE
        imgProxyStatus.contentDescription = description
    }

    private fun isProxyWorking(config: AccountProxyConfig): Boolean {
        return try {
            val proxyType = if (config.type.equals("socks", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxy = Proxy(proxyType, InetSocketAddress(config.host, config.port))
            val connection = URL("https://steamcommunity.com").openConnection(proxy) as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "MSDA/1.2.0")

            if (proxyType == Proxy.Type.HTTP && config.username.isNotBlank()) {
                val token = Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray())
                connection.setRequestProperty("Proxy-Authorization", "Basic $token")
            }

            val code = connection.responseCode
            code in 200..399
        } catch (_: Throwable) {
            false
        }
    }
}
