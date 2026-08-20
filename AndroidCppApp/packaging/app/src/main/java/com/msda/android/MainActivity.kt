package com.msda.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
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
import androidx.core.content.ContextCompat
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.msda.android.steam.AdmissionHelper
import com.msda.android.steam.AuthContextMerger
import com.msda.android.steam.NativeAuthBridge
import com.msda.android.steam.MafileRepository
import com.msda.android.steam.SessionInvalidException
import com.msda.android.csfloat.CsFloatAccountSettings
import com.msda.android.csfloat.CsFloatClient
import com.msda.android.csfloat.CsFloatMeSummary
import com.msda.android.csfloat.CsFloatNotifier
import com.msda.android.csfloat.CsFloatResult
import com.msda.android.csfloat.CsFloatScheduler
import com.msda.android.csfloat.CsFloatSecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.URL

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ACCOUNT_INDEX = "extra_account_index"
        const val EXTRA_ACCOUNT_NAME = "extra_account_name"
        const val EXTRA_STEAM_ID = "extra_steam_id"
        const val EXTRA_OPEN_CSFLOAT_PENDING = "extra_open_csfloat_pending"
        /** Gap between Steam confirmation ops (accept-all / trade auto-confirm). */
        private const val STEAM_CONFIRM_GAP_MS = 400L
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
    private lateinit var btnConfirmAll: android.widget.Button
    private lateinit var imgProxyStatus: ImageView
    private lateinit var txtAppTitle: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private var activeAuthContext: ConfirmationAuthContext? = null
    private var currentAccountIndex: Int = -1
    private var currentAccountName: String = ""
    private var currentSteamId: String = ""
    private var steamLoginInProgress = false
    private var lastLoginSuccessAtMs = 0L
    private var proxyCheckInProgress = false
    private var lastSuccessfulBundles: List<ConfirmationBundle> = emptyList()
    private val expandedBundleKeys = mutableSetOf<String>()

    private val codeTicker = object : Runnable {
        override fun run() {
            refreshCodeViews()
            uiHandler.postDelayed(this, 1000)
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

    private var pendingAfterNotifPermission: (() -> Unit)? = null
    private val csFloatNotifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val cont = pendingAfterNotifPermission
            pendingAfterNotifPermission = null
            if (!granted) {
                Toast.makeText(
                    this,
                    getString(R.string.csfloat_notif_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
            // Soft-fail: continue enable/save either way (T077).
            cont?.invoke()
        }

    /** API 33+: prompt for POST_NOTIFICATIONS before CSFloat enable; always continues (soft-fail). */
    private fun withCsFloatNotificationPermission(onContinue: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onContinue()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onContinue()
            return
        }
        pendingAfterNotifPermission = onContinue
        csFloatNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun formatCsFloatUsdCents(cents: Int): String {
        val dollars = cents / 100
        val rem = kotlin.math.abs(cents % 100)
        return "$%d.%02d".format(dollars, rem)
    }

    private fun formatCsFloatBalanceLine(me: CsFloatMeSummary): String {
        return getString(
            R.string.csfloat_balance_line,
            formatCsFloatUsdCents(me.balanceCents),
            formatCsFloatUsdCents(me.pendingBalanceCents)
        )
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
        btnConfirmAll = findViewById(R.id.btnConfirmAll)
        imgProxyStatus = findViewById(R.id.imgProxyStatus)

        val btnScanQr = findViewById<ImageButton>(R.id.btnScanQr)
        val btnQuickActions = findViewById<ImageButton>(R.id.btnQuickActions)
        val btnAutoConfirm = findViewById<android.widget.Button>(R.id.btnAutoConfirm)
        txtAppTitle = findViewById(R.id.txtAppTitle)

        // Load persisted mafiles FIRST so native state is populated before we set the
        // active account — otherwise importMafilesFromFolder() could reset it back to 0.
        val startupLoaded = loadPersistedMafiles()
        txtStatus.text = if (startupLoaded) {
            getString(R.string.status_saved_mafiles_loaded)
        } else {
            getString(R.string.status_started)
        }

        val selectedIndex = intent.getIntExtra(EXTRA_ACCOUNT_INDEX, -1)
        currentSteamId = intent.getStringExtra(EXTRA_STEAM_ID).orEmpty()
        if (selectedIndex >= 0) {
            currentAccountIndex = selectedIndex
            currentAccountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
            NativeBridge.setActiveAccount(selectedIndex)
            if (currentSteamId.isBlank()) {
                currentSteamId = resolveSteamIdForAccountIndex(selectedIndex)
            }
            refreshAccountTitle()
        }

        refreshCodeViews()
        updateActiveAuthContext()
        refreshProxyIndicatorAsync()
        BackgroundSyncScheduler.disable(this)
        renderBundles(emptyList())

        txtCode.setOnClickListener {
            copyCurrentCodeToClipboard()
        }

        txtAppTitle.setOnClickListener {
            showAccountLabelDialog()
        }

        btnScanQr.setOnClickListener {
            startQrScanner()
        }

        btnAutoConfirm.setOnClickListener {
            showTradeAutoConfirmDialog()
        }

        imgProxyStatus.setOnClickListener {
            showProxySettingsDialog()
        }

        btnQuickActions.setOnClickListener { showQuickActionsMenu(it) }

        btnConfirmAll.setOnClickListener {
            confirmAcceptAllVisible()
        }

        btnRefreshConfirmations.setOnClickListener {
            txtStatus.text = getString(R.string.status_loading_confirmations)
            refreshConfirmationsAsync()
        }

        maybeOpenCsFloatPendingFromIntent(intent)
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
    }

    override fun onPause() {
        uiHandler.removeCallbacks(codeTicker)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            val selectedIndex = intent.getIntExtra(EXTRA_ACCOUNT_INDEX, -1)
            if (selectedIndex >= 0) {
                currentAccountIndex = selectedIndex
                currentAccountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
                currentSteamId = intent.getStringExtra(EXTRA_STEAM_ID).orEmpty()
                NativeBridge.setActiveAccount(selectedIndex)
                if (currentSteamId.isBlank()) {
                    currentSteamId = resolveSteamIdForAccountIndex(selectedIndex)
                }
                refreshAccountTitle()
                refreshCodeViews()
                updateActiveAuthContext()
                refreshProxyIndicatorAsync()
                lastSuccessfulBundles = emptyList()
                expandedBundleKeys.clear()
                renderBundles(emptyList())
                txtStatus.text = "Loaded account: $currentAccountName"
            } else {
                val steamId = intent.getStringExtra(EXTRA_STEAM_ID).orEmpty()
                if (steamId.isNotBlank()) {
                    currentSteamId = steamId
                    currentAccountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME).orEmpty()
                        .ifBlank { currentAccountName }
                    refreshAccountTitle()
                    updateActiveAuthContext()
                }
            }
            maybeOpenCsFloatPendingFromIntent(intent)
        }
    }

    private fun maybeOpenCsFloatPendingFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_CSFLOAT_PENDING, false) != true) return
        intent.removeExtra(EXTRA_OPEN_CSFLOAT_PENDING)
        val steamId = currentSteamId.ifBlank { intent.getStringExtra(EXTRA_STEAM_ID).orEmpty() }
        if (steamId.isBlank()) return
        val key = CsFloatSecureStore.getApiKey(this, steamId)
        if (key.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.csfloat_test_no_key), Toast.LENGTH_SHORT).show()
            return
        }
        // Defer until window is ready.
        uiHandler.post {
            if (!isFinishing) showCsFloatPendingSalesDialog(steamId, key)
    }
    }

    private fun refreshAccountTitle() {
        if (!::txtAppTitle.isInitialized) return
        if (currentAccountName.isBlank()) {
            txtAppTitle.text = getString(R.string.app_name)
            return
        }
        val label = AppSettings.getAccountLabel(this, currentSteamId)
        txtAppTitle.text = if (label.isNotBlank()) {
            getString(R.string.hub_account_title_with_label, currentAccountName, label)
        } else {
            currentAccountName
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
            ?: NativeAuthBridge.confirmationAuthForSteamId(this, currentSteamId)
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

        CoroutineScope(Dispatchers.IO).launch {
            val result = QrApprovalService.approveLoginRequest(this@MainActivity, auth, scannedText)
            withContext(Dispatchers.Main) {
                steamLoginInProgress = false
                setAuthenticatingUi(false)
                if (result.success) {
                    updateActiveAuthContext()
                }
                txtStatus.text = when {
                    result.success -> getString(R.string.status_qr_authorized)
                    result.errorMessage == QrApprovalService.ERROR_NO_REQUESTS -> getString(R.string.status_qr_no_requests)
                    result.errorMessage == QrApprovalService.ERROR_MULTIPLE_REQUESTS -> getString(R.string.status_qr_multiple_requests)
                    result.errorMessage == QrApprovalService.ERROR_TOKEN_MISSING -> getString(R.string.status_qr_token_missing)
                    result.errorMessage == QrApprovalService.ERROR_INVALID_QR -> getString(R.string.status_qr_invalid)
                    else -> result.errorMessage ?: getString(R.string.status_confirmation_failed)
                }
            }
        }
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
        menu.menu.add(0, 9, 3, getString(R.string.account_label_edit))
        menu.menu.add(0, 7, 4, getString(R.string.auto_market_confirmations))
        menu.menu.add(0, 10, 5, getString(R.string.csfloat_menu))
        menu.menu.add(0, 8, 6, getString(R.string.proxy_settings))
        menu.menu.add(0, 3, 7, getString(R.string.login_steam))
        menu.menu.add(0, 4, 8, getString(R.string.load_confirmations))
        menu.menu.add(0, 5, 9, getString(R.string.settings))

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
                9 -> {
                    showAccountLabelDialog()
                    true
                }
                7 -> {
                    showTradeAutoConfirmDialog()
                    true
                }
                10 -> {
                    showCsFloatSettingsDialog()
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

    private fun showTradeAutoConfirmDialog() {
        updateActiveAuthContext()
        val auth = activeAuthContext
        if (auth == null || auth.steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        val tradeSwitch = Switch(this).apply {
            text = getString(R.string.allow_trade_confirmations_on_account)
            isChecked = AppSettings.isTradeAutoConfirmEnabled(this@MainActivity, auth.steamId)
        }

        val hint = TextView(this).apply {
            text = getString(R.string.auto_confirm_trades_hint)
            setPadding(0, 16, 0, 8)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(hint)
            addView(tradeSwitch)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.auto_market_confirmations))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AppSettings.setTradeAutoConfirmEnabled(this, auth.steamId, tradeSwitch.isChecked)
                txtStatus.text = if (tradeSwitch.isChecked) {
                    getString(R.string.trade_auto_confirm_enabled)
                } else {
                    getString(R.string.trade_auto_confirm_disabled)
                }
            }
            .show()
    }

    private fun formatCsFloatStatusStrip(steamId: String): String {
        val lastAt = CsFloatAccountSettings.getLastCheckAtMs(this, steamId)
        if (lastAt <= 0L) {
            return getString(R.string.csfloat_status_strip_never)
        }
        val relative = DateUtils.getRelativeTimeSpanString(
            lastAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
        val count = CsFloatAccountSettings.getLastQueuedCount(this, steamId)
        return getString(R.string.csfloat_status_strip, relative, count)
    }

    /** T141: accent pending count when N>0; otherwise secondary text color. */
    private fun applyCsFloatStatusStripStyle(view: TextView, steamId: String) {
        val count = CsFloatAccountSettings.getLastQueuedCount(this, steamId)
        val lastAt = CsFloatAccountSettings.getLastCheckAtMs(this, steamId)
        if (lastAt > 0L && count > 0) {
            view.setTextColor(ContextCompat.getColor(this, R.color.csfloat_pending_accent))
            view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
        } else {
            val tv = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.textColorSecondary, tv, true)
            val color = if (tv.resourceId != 0) {
                ContextCompat.getColor(this, tv.resourceId)
            } else {
                tv.data
            }
            view.setTextColor(color)
            view.setTypeface(android.graphics.Typeface.create(view.typeface, android.graphics.Typeface.NORMAL))
        }
    }

    private fun showCsFloatSettingsDialog() {
        updateActiveAuthContext()
        val auth = activeAuthContext
        if (auth == null || auth.steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }
        val steamId = auth.steamId

        val enableSwitch = Switch(this).apply {
            text = getString(R.string.csfloat_enable)
            isChecked = CsFloatAccountSettings.isEnabled(this@MainActivity, steamId)
        }
        val notifySwitch = Switch(this).apply {
            text = getString(R.string.csfloat_notify_enabled)
            isChecked = CsFloatAccountSettings.isNotifyEnabled(this@MainActivity, steamId)
        }
        val apiKeyInput = EditText(this).apply {
            hint = getString(R.string.csfloat_api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText("")
            // Do not prefill the secret; show placeholder if one is stored.
            if (CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)) {
                hint = getString(R.string.csfloat_api_key_hint) + " (saved)"
            }
        }
        val intervalInput = EditText(this).apply {
            hint = getString(R.string.csfloat_interval_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(
                CsFloatAccountSettings.getPollIntervalMinutes(this@MainActivity, steamId).toString()
            )
        }
        val statusStrip = TextView(this).apply {
            text = formatCsFloatStatusStrip(steamId)
            setPadding(0, 8, 0, 8)
            contentDescription = getString(R.string.csfloat_status_strip_tap_hint)
        }
        fun refreshStatusStrip() {
            statusStrip.text = formatCsFloatStatusStrip(steamId)
            applyCsFloatStatusStripStyle(statusStrip, steamId)
        }
        refreshStatusStrip()
        fun openPendingSales() {
            val typed = apiKeyInput.text?.toString().orEmpty().trim()
            val keyToUse = typed.ifEmpty {
                CsFloatSecureStore.getApiKey(this@MainActivity, steamId).orEmpty()
            }
            if (keyToUse.isBlank()) {
                Toast.makeText(this, getString(R.string.csfloat_test_no_key), Toast.LENGTH_SHORT).show()
                return
            }
            showCsFloatPendingSalesDialog(steamId, keyToUse, onStatusSynced = { refreshStatusStrip() })
        }
        val balanceLine = TextView(this).apply {
            text = ""
            setPadding(0, 4, 0, 4)
            visibility = View.GONE
        }
        val testResult = TextView(this).apply {
            text = ""
            setPadding(0, 12, 0, 4)
        }
        val btnTest = Button(this).apply {
            text = getString(R.string.csfloat_test_connection)
        }
        val btnClearKey = Button(this).apply {
            text = getString(R.string.csfloat_clear_key)
            isEnabled = CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
        }
        val btnPending = Button(this).apply {
            text = getString(R.string.csfloat_pending_sales)
            isEnabled = CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
        }
        val btnCheckNow = Button(this).apply {
            text = getString(R.string.csfloat_check_now)
            isEnabled = CsFloatAccountSettings.isEnabled(this@MainActivity, steamId) &&
                CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
        }
        statusStrip.setOnClickListener { openPendingSales() }
        run {
            val hasKey = CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
            statusStrip.isClickable = hasKey
            statusStrip.isFocusable = hasKey
            statusStrip.alpha = if (hasKey) 1f else 0.55f
        }
        val hint = TextView(this).apply {
            text = getString(R.string.csfloat_settings_hint)
            setPadding(0, 8, 0, 8)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(hint)
            addView(statusStrip)
            addView(balanceLine)
            addView(enableSwitch)
            addView(notifySwitch)
            addView(apiKeyInput)
            addView(intervalInput)
            addView(btnTest)
            addView(btnClearKey)
            addView(btnPending)
            addView(btnCheckNow)
            addView(testResult)
        }

        btnTest.setOnClickListener {
            val typed = apiKeyInput.text?.toString().orEmpty().trim()
            val keyToUse = typed.ifEmpty {
                CsFloatSecureStore.getApiKey(this@MainActivity, steamId).orEmpty()
            }
            if (keyToUse.isBlank()) {
                testResult.text = getString(R.string.csfloat_test_no_key)
                Toast.makeText(this, getString(R.string.csfloat_test_no_key), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnTest.isEnabled = false
            testResult.text = getString(R.string.csfloat_test_testing)
            CoroutineScope(Dispatchers.IO).launch {
                val result = CsFloatClient(keyToUse).me()
                withContext(Dispatchers.Main) {
                    if (isFinishing) return@withContext
                    btnTest.isEnabled = true
                    // Never surface API key or raw response body in UI/logs here.
                    val message = when (result) {
                        is CsFloatResult.Ok -> {
                            balanceLine.text = formatCsFloatBalanceLine(result.value)
                            balanceLine.visibility = View.VISIBLE
                            val name = result.value.username.trim()
                            if (name.isNotEmpty()) {
                                getString(R.string.csfloat_test_ok, name)
                            } else {
                                getString(R.string.csfloat_test_ok_anon)
                            }
                        }
                        is CsFloatResult.HttpError -> {
                            balanceLine.visibility = View.GONE
                            balanceLine.text = ""
                            when (result.code) {
                                401, 403 -> getString(R.string.csfloat_test_unauthorized)
                                else -> getString(R.string.csfloat_test_http_error, result.code)
                            }
                        }
                        is CsFloatResult.RateLimited -> {
                            balanceLine.visibility = View.GONE
                            balanceLine.text = ""
                            getString(R.string.csfloat_test_rate_limited)
                        }
                        is CsFloatResult.NetworkError -> {
                            balanceLine.visibility = View.GONE
                            balanceLine.text = ""
                            getString(R.string.csfloat_test_network)
                        }
                    }
                    testResult.text = message
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun refreshCheckNowEnabled(running: Boolean = false) {
            val ready = CsFloatAccountSettings.isEnabled(this@MainActivity, steamId) &&
                CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
            btnCheckNow.isEnabled = ready && !running
        }

        btnClearKey.setOnClickListener {
            CsFloatSecureStore.clearApiKey(this, steamId)
            CsFloatNotifier.cancelForSteamId(this, steamId)
            CsFloatAccountSettings.clearCheckStatus(this, steamId)
            refreshStatusStrip()
            balanceLine.text = ""
            balanceLine.visibility = View.GONE
            apiKeyInput.setText("")
            apiKeyInput.hint = getString(R.string.csfloat_api_key_hint)
            btnClearKey.isEnabled = false
            btnPending.isEnabled = false
            refreshCheckNowEnabled()
            statusStrip.isClickable = false
            statusStrip.isFocusable = false
            statusStrip.alpha = 0.55f
            // Clearing the key drops this account from readySteamIds → scheduler cancels if empty.
            CsFloatScheduler.refresh(this)
            testResult.text = getString(R.string.csfloat_key_cleared)
            Toast.makeText(this, getString(R.string.csfloat_key_cleared), Toast.LENGTH_SHORT).show()
        }

        btnPending.setOnClickListener { openPendingSales() }

        btnCheckNow.setOnClickListener {
            if (!CsFloatAccountSettings.isEnabled(this, steamId) ||
                !CsFloatSecureStore.hasApiKey(this, steamId)
            ) {
                Toast.makeText(
                    this,
                    getString(R.string.csfloat_check_now_need_enable),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            // Ensure periodic scheduler knows about this account before one-shot.
            CsFloatScheduler.refresh(this)
            if (!CsFloatScheduler.enqueueCheckNow(this, steamId)) {
                Toast.makeText(
                    this,
                    getString(R.string.csfloat_check_now_need_enable),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            btnCheckNow.isEnabled = false
            Toast.makeText(this, getString(R.string.csfloat_check_now_started), Toast.LENGTH_SHORT).show()
        }

        val checkNowObserver = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { infos ->
            if (isFinishing) return@Observer
            val running = CsFloatScheduler.isCheckNowActive(infos)
            refreshCheckNowEnabled(running = running)
            if (!running) {
                refreshStatusStrip()
            }
        }
        val checkNowLiveData = CsFloatScheduler.checkNowWorkInfos(this)
        checkNowLiveData.observe(this, checkNowObserver)

        fun saveCsFloatSettings() {
            val newKey = apiKeyInput.text?.toString().orEmpty().trim()
            if (newKey.isNotEmpty()) {
                CsFloatSecureStore.saveApiKey(this, steamId, newKey)
            }
            val interval = intervalInput.text?.toString()?.toLongOrNull()
                ?: CsFloatAccountSettings.DEFAULT_INTERVAL_MINUTES
            CsFloatAccountSettings.setPollIntervalMinutes(this, steamId, interval)

            val enable = enableSwitch.isChecked
            if (enable && !CsFloatSecureStore.hasApiKey(this, steamId)) {
                CsFloatAccountSettings.setEnabled(this, steamId, false)
                CsFloatAccountSettings.clearCheckStatus(this, steamId)
                CsFloatScheduler.refresh(this)
                txtStatus.text = getString(R.string.csfloat_need_key)
                Toast.makeText(this, getString(R.string.csfloat_need_key), Toast.LENGTH_SHORT).show()
                return
            }

            val wasEnabled = CsFloatAccountSettings.isEnabled(this, steamId)
            fun applyEnableAndSchedule() {
                CsFloatAccountSettings.setEnabled(this@MainActivity, steamId, enable)
                CsFloatAccountSettings.setNotifyEnabled(
                    this@MainActivity,
                    steamId,
                    notifySwitch.isChecked
                )
                if (!notifySwitch.isChecked) {
                    CsFloatNotifier.cancelForSteamId(this@MainActivity, steamId)
                }
                if (!enable) {
                    CsFloatAccountSettings.clearCheckStatus(this@MainActivity, steamId)
                }
                // Disable or empty ready set cancels periodic CSFloat work.
                CsFloatScheduler.refresh(this@MainActivity)
                txtStatus.text = if (enable) {
                    getString(R.string.csfloat_saved)
                } else {
                    getString(R.string.csfloat_disabled)
                }
                Toast.makeText(this@MainActivity, getString(R.string.csfloat_saved), Toast.LENGTH_SHORT).show()
            }

            // T090: one-time dual-bot warning on first enable; never blocks enable.
            if (enable && !wasEnabled && !CsFloatAccountSettings.hasSeenDualBotWarning(this, steamId)) {
                android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.csfloat_dual_bot_title))
                    .setMessage(getString(R.string.csfloat_dual_bot_message))
                    .setPositiveButton(R.string.csfloat_dual_bot_got_it) { _, _ ->
                        CsFloatAccountSettings.setDualBotWarningSeen(this@MainActivity, steamId)
                        applyEnableAndSchedule()
                    }
                    .setOnCancelListener {
                        CsFloatAccountSettings.setDualBotWarningSeen(this@MainActivity, steamId)
                        applyEnableAndSchedule()
                    }
                    .show()
                return
            }

            applyEnableAndSchedule()
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.csfloat_dialog_title))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (enableSwitch.isChecked) {
                    withCsFloatNotificationPermission { saveCsFloatSettings() }
                } else {
                    saveCsFloatSettings()
                }
            }
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    checkNowLiveData.removeObserver(checkNowObserver)
                }
                dialog.show()
            }
    }

    /**
     * Read-only queued/pending CSFloat sales for the active account.
     * Manual Refresh only — no accept, Steam offer, or Guard traffic.
     * Successful loads sync last-check / queued count without posting a notification (T088).
     */
    private fun showCsFloatPendingSalesDialog(
        steamId: String,
        apiKey: String,
        onStatusSynced: (() -> Unit)? = null
    ) {
        val statusView = TextView(this).apply {
            text = getString(R.string.csfloat_pending_loading)
            setPadding(0, 0, 0, 12)
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(listContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 320).toInt()
            )
        }
        val btnRefresh = Button(this).apply {
            text = getString(R.string.csfloat_pending_refresh)
        }
        // T097: empty-state Check now (same one-shot WM path as T079).
        val btnCheckNow = Button(this).apply {
            text = getString(R.string.csfloat_check_now)
            visibility = View.GONE
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(statusView)
            addView(btnRefresh)
            addView(btnCheckNow)
            addView(scroll)
        }

        fun formatPendingEmptyStatus(): String {
            val lastAt = CsFloatAccountSettings.getLastCheckAtMs(this@MainActivity, steamId)
            if (lastAt <= 0L) {
                return getString(R.string.csfloat_pending_empty_never)
            }
            val relative = DateUtils.getRelativeTimeSpanString(
                lastAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
            return getString(R.string.csfloat_pending_empty_last, relative)
        }

        fun refreshEmptyCheckNowEnabled(running: Boolean = false) {
            val ready = CsFloatAccountSettings.isEnabled(this@MainActivity, steamId) &&
                CsFloatSecureStore.hasApiKey(this@MainActivity, steamId)
            btnCheckNow.isEnabled = ready && !running
        }

        fun renderTrades(trades: List<com.msda.android.csfloat.CsFloatTradeSummary>) {
            listContainer.removeAllViews()
            if (trades.isEmpty()) {
                statusView.text = formatPendingEmptyStatus()
                btnCheckNow.visibility = View.VISIBLE
                refreshEmptyCheckNowEnabled(
                    running = CsFloatScheduler.isCheckNowActive(
                        CsFloatScheduler.checkNowWorkInfos(this@MainActivity).value
                    )
                )
                return
            }
            btnCheckNow.visibility = View.GONE
            statusView.text = getString(R.string.csfloat_pending_count, trades.size)
            trades.forEach { trade ->
                val name = trade.marketHashName.ifBlank { getString(R.string.csfloat_pending_unknown_item) }
                val buyer = if (trade.buyerSteamId.isNotBlank()) {
                    trade.buyerSteamId.takeLast(6)
                } else {
                    "—"
                }
                val line = TextView(this).apply {
                    text = getString(
                        R.string.csfloat_pending_row,
                        name,
                        trade.priceUsdLabel(),
                        trade.state.ifBlank { "?" },
                        buyer
                    )
                    setPadding(0, 10, 0, 10)
                }
                listContainer.addView(line)
            }
        }

        fun loadTrades() {
            btnRefresh.isEnabled = false
            btnCheckNow.isEnabled = false
            statusView.text = getString(R.string.csfloat_pending_loading)
            CoroutineScope(Dispatchers.IO).launch {
                val result = CsFloatClient(apiKey).listQueuedTrades()
                withContext(Dispatchers.Main) {
                    if (isFinishing) return@withContext
                    btnRefresh.isEnabled = true
                    when (result) {
                        is CsFloatResult.Ok -> {
                            // Sync UI/worker baseline + seen trade ids; do not notify from foreground refresh.
                            CsFloatAccountSettings.setLastQueuedCount(
                                this@MainActivity,
                                steamId,
                                result.value.size,
                                baselined = true
                            )
                            CsFloatAccountSettings.setSeenTradeIds(
                                this@MainActivity,
                                steamId,
                                result.value.map { it.id }
                            )
                            renderTrades(result.value)
                            onStatusSynced?.invoke()
                        }
                        is CsFloatResult.HttpError -> {
                            listContainer.removeAllViews()
                            btnCheckNow.visibility = View.GONE
                            statusView.text = when (result.code) {
                                401, 403 -> getString(R.string.csfloat_test_unauthorized)
                                else -> getString(R.string.csfloat_test_http_error, result.code)
                            }
                        }
                        is CsFloatResult.RateLimited -> {
                            listContainer.removeAllViews()
                            btnCheckNow.visibility = View.GONE
                            statusView.text = getString(R.string.csfloat_test_rate_limited)
                        }
                        is CsFloatResult.NetworkError -> {
                            listContainer.removeAllViews()
                            btnCheckNow.visibility = View.GONE
                            statusView.text = getString(R.string.csfloat_test_network)
                        }
                    }
                }
            }
        }

        btnRefresh.setOnClickListener { loadTrades() }

        btnCheckNow.setOnClickListener {
            if (!CsFloatAccountSettings.isEnabled(this, steamId) ||
                !CsFloatSecureStore.hasApiKey(this, steamId)
            ) {
                Toast.makeText(
                    this,
                    getString(R.string.csfloat_check_now_need_enable),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            CsFloatScheduler.refresh(this)
            if (!CsFloatScheduler.enqueueCheckNow(this, steamId)) {
                Toast.makeText(
                    this,
                    getString(R.string.csfloat_check_now_need_enable),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            btnCheckNow.isEnabled = false
            Toast.makeText(this, getString(R.string.csfloat_check_now_started), Toast.LENGTH_SHORT).show()
        }

        var checkNowWasRunning = false
        val checkNowObserver = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { infos ->
            if (isFinishing) return@Observer
            val running = CsFloatScheduler.isCheckNowActive(infos)
            if (btnCheckNow.visibility == View.VISIBLE) {
                refreshEmptyCheckNowEnabled(running = running)
            }
            if (checkNowWasRunning && !running && btnCheckNow.visibility == View.VISIBLE) {
                // One-shot finished — reload list + last-checked.
                loadTrades()
            }
            checkNowWasRunning = running
        }
        val checkNowLiveData = CsFloatScheduler.checkNowWorkInfos(this)
        checkNowLiveData.observe(this, checkNowObserver)

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.csfloat_pending_title)
            .setView(root)
            .setNegativeButton(android.R.string.ok, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    checkNowLiveData.removeObserver(checkNowObserver)
                }
                dialog.show()
            }

        loadTrades()
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
        if (currentSteamId.isBlank() && currentAccountIndex >= 0) {
            currentSteamId = resolveSteamIdForAccountIndex(currentAccountIndex)
        }
        activeAuthContext = if (currentSteamId.isNotBlank()) {
            NativeAuthBridge.confirmationAuthForSteamId(this, currentSteamId)
        } else {
            NativeAuthBridge.activeConfirmationAuth(this)
        }
        val auth = activeAuthContext ?: return
        if (MafileImportHelper.hasUsableSession(auth)) {
            MafileRepository(this).readSession(auth.steamId)?.let { session ->
                AdmissionHelper.seedMobileSessionCookies(auth.steamId, session)
            }
        }
    }

    private fun resolveSteamIdForAccountIndex(index: Int): String {
        val lines = runCatching { NativeBridge.getAccounts() }.getOrDefault("")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return lines.getOrNull(index)?.split('|')?.getOrNull(2)?.trim().orEmpty()
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

                SessionPersistence.saveSession(
                    this,
                    steamId,
                    StoredSteamSession(
                        steamLoginSecure = loginSecure,
                        sessionId = sessionId,
                        refreshToken = result.refreshToken.orEmpty(),
                        accessToken = result.accessToken.orEmpty(),
                        accountName = auth.accountName,
                        sessionExpiresAtMs = result.sessionExpiresAtMs
                    )
                )

                activeAuthContext = auth.copy(
                    steamId = steamId,
                    sessionId = sessionId,
                    steamLoginSecure = loginSecure,
                    refreshToken = result.refreshToken.orEmpty(),
                    accessToken = result.accessToken.orEmpty()
                )
                currentSteamId = steamId
                lastLoginSuccessAtMs = System.currentTimeMillis()
                txtStatus.text = getString(R.string.status_login_saved)
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
        MafileImportHelper.importFromUri(
            activity = this,
            uri = uri,
            onStatus = { msg -> txtStatus.text = msg },
            onImported = { _, imported ->
                refreshCodeViews()
                if (imported) {
                    handlePostMafileImport()
                } else {
                    updateActiveAuthContext()
                }
            }
        )
    }

    /**
     * After a mafile import: try silent renewal when possible, otherwise prompt for a password
     * only when the mafile has no live session and no silent renewal path exists.
     */
    private fun handlePostMafileImport() {
        updateActiveAuthContext()
        val auth = activeAuthContext

        CoroutineScope(Dispatchers.IO).launch {
            var effectiveAuth = auth

            if (auth != null && MafileImportHelper.canSilentRenew(this@MainActivity, auth)) {
                val renewed = SessionManager.renew(this@MainActivity, auth)
                if (renewed != null) {
                    effectiveAuth = renewed
                    withContext(Dispatchers.Main) {
                        activeAuthContext = renewed
                        lastLoginSuccessAtMs = System.currentTimeMillis()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                when {
                    effectiveAuth != null && MafileImportHelper.hasUsableSession(effectiveAuth) -> {
                        txtStatus.text = getString(R.string.status_login_saved)
                    }
                    MafileImportHelper.needsInteractiveLogin(this@MainActivity, effectiveAuth) -> {
                        promptSteamLogin()
                    }
                    effectiveAuth != null && MafileImportHelper.canSilentRenew(this@MainActivity, effectiveAuth) -> {
                        promptSteamLogin()
                    }
                    else -> {
                        txtStatus.text = getString(R.string.status_import_success)
                    }
                }
            }
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

    private fun refreshConfirmationsAsync(initialDelayMs: Long = 0L) {
        if (steamLoginInProgress) {
            return
        }

        val auth = activeAuthContext ?: run {
            lastSuccessfulBundles = emptyList()
            renderBundles(emptyList())
            txtStatus.text = getString(R.string.status_login_required)
            return
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (initialDelayMs > 0L) {
                kotlinx.coroutines.delay(initialDelayMs)
            }
            if (!isNetworkOnline()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    txtStatus.text = getString(R.string.status_confirmations_offline)
                }
                return@launch
            }
            val latestAuth = activeAuthContext ?: auth
            var workingAuth = latestAuth
            var error: String? = null
            var sessionExpired = false
            var autoAccepted = 0
            var bundles: List<ConfirmationBundle>? = try {
                ConfirmationService.loadBundlesWithAutoRenew(
                    context = this@MainActivity,
                    auth = workingAuth,
                    onSessionRenewed = { newAuth ->
                        workingAuth = newAuth
                        activeAuthContext = newAuth
                    }
                )
            } catch (ex: NeedPasswordException) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    promptSteamLogin()
                }
                return@launch
            } catch (ex: SessionInvalidException) {
                sessionExpired = true
                null
            } catch (ex: Throwable) {
                error = formatConfirmationLoadError(ex)
                null
            }

            // Auto-accept trades only from this manually loaded list (no background polling).
            if (!sessionExpired && error == null && bundles != null &&
                AppSettings.isTradeAutoConfirmEnabled(this@MainActivity, workingAuth.steamId)
            ) {
                val tradeItems = bundles
                    .flatMap { it.items }
                    .filter { isTradeConfirmation(it) }
                    .distinctBy { it.id }

                for ((index, item) in tradeItems.withIndex()) {
                    if (index > 0) {
                        kotlinx.coroutines.delay(STEAM_CONFIRM_GAP_MS)
                    }
                    try {
                        val ok = ConfirmationService.respondItemWithRenew(
                            context = this@MainActivity,
                            auth = workingAuth,
                            item = item,
                            accept = true,
                            onSessionRenewed = { renewed ->
                                workingAuth = renewed
                                activeAuthContext = renewed
                            }
                        )
                        if (ok) {
                            autoAccepted++
                        } else {
                            // Stop hammering on hard failure / rate limit.
                            break
                        }
                    } catch (ex: Throwable) {
                        if (looksLikeSteamRateLimit(ex)) break
                        break
                    }
                }

                if (autoAccepted > 0) {
                    bundles = try {
                        ConfirmationService.loadBundlesWithAutoRenew(
                            context = this@MainActivity,
                            auth = workingAuth,
                            onSessionRenewed = { renewed ->
                                workingAuth = renewed
                                activeAuthContext = renewed
                            }
                        )
                    } catch (ex: SessionInvalidException) {
                        sessionExpired = true
                        bundles
                    } catch (ex: Throwable) {
                        error = formatConfirmationLoadError(ex)
                        bundles
                    }
                }
            }

            runOnUiThread {
                if (sessionExpired) {
                    showConfirmationsSessionExpiredDialog()
                    return@runOnUiThread
                }
                if (error != null) {
                    val offlineHint = getString(R.string.status_confirmations_offline)
                    txtStatus.text = if (error == offlineHint) {
                        offlineHint
                    } else {
                        getString(R.string.status_confirmation_load_failed, error)
                    }
                    return@runOnUiThread
                }

                val stable = bundles ?: emptyList()
                lastSuccessfulBundles = stable
                val existingKeys = stable.map { it.key }.toSet()
                expandedBundleKeys.retainAll(existingKeys)
                renderBundles(stable)
                txtStatus.text = when {
                    autoAccepted > 0 && stable.isEmpty() ->
                        getString(R.string.status_auto_accepted_trades, autoAccepted)
                    autoAccepted > 0 ->
                        getString(R.string.status_auto_accepted_trades, autoAccepted) +
                            " · " + getString(R.string.status_confirmations_loaded, stable.sumOf { it.items.size })
                    stable.isEmpty() -> getString(R.string.status_confirmations_empty)
                    else -> getString(R.string.status_confirmations_loaded, stable.sumOf { it.items.size })
                }
            }
        }
    }

    private fun isNetworkOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showConfirmationsSessionExpiredDialog() {
        txtStatus.text = getString(R.string.status_confirmations_session_expired)
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirmations_session_expired_title)
            .setMessage(R.string.confirmations_session_expired_message)
            .setPositiveButton(R.string.confirmations_renew_session) { _, _ -> promptSteamLogin() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isTradeConfirmation(item: ConfirmationItem): Boolean {
        return item.type == 2 || item.typeName.contains("trade", ignoreCase = true)
    }

    private fun isMarketConfirmation(item: ConfirmationItem): Boolean {
        return item.typeName.contains("market", ignoreCase = true) ||
            item.typeName.contains("listing", ignoreCase = true)
    }

    private fun formatConfirmationLoadError(ex: Throwable): String {
        var current: Throwable? = ex
        while (current != null) {
            if (current is UnknownHostException || current is ConnectException) {
                return applicationContext.getString(R.string.status_confirmations_offline)
            }
            val message = current.message.orEmpty().lowercase()
            if (
                message.contains("unable to resolve") ||
                message.contains("unknownhost") ||
                message.contains("network is unreachable") ||
                message.contains("failed to connect") ||
                message.contains("no address associated")
            ) {
                return applicationContext.getString(R.string.status_confirmations_offline)
            }
            current = current.cause
        }
        val message = ex.message.orEmpty().lowercase()
        return if (
            message.contains("ssl") ||
            message.contains("handshake") ||
            message.contains("timed out") ||
            message.contains("timeout")
        ) {
            applicationContext.getString(R.string.confirmation_network_error)
        } else {
            ex.message ?: "Unknown confirmation error"
        }
    }

    private fun looksLikeSteamRateLimit(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            val message = current.message.orEmpty().lowercase()
            if (
                message.contains("429") ||
                message.contains("rate limit") ||
                message.contains("too many requests")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun renderBundles(bundles: List<ConfirmationBundle>) {
        confirmationsContainer.removeAllViews()

        if (bundles.isEmpty()) {
            confirmationsContainer.visibility = View.GONE
            btnConfirmAll.visibility = View.GONE
            return
        }

        confirmationsContainer.visibility = View.VISIBLE
        btnConfirmAll.visibility = View.VISIBLE

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

        val typeIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { marginEnd = 8 }
            setImageResource(confirmationTypeIconRes(item))
            contentDescription = getString(confirmationTypeLabelRes(item))
            setColorFilter(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
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

        val relativeTime = formatConfirmationRelativeTime(item)
        val metaParts = mutableListOf<String>()
        if (relativeTime.isNotBlank()) metaParts += relativeTime
        val summaryText = item.summary.filter { it.isNotBlank() }.joinToString(" · ")
        if (summaryText.isNotBlank()) metaParts += summaryText
        val meta = TextView(this).apply {
            text = metaParts.joinToString(" · ")
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
        }

        textColumn.addView(headline)
        if (metaParts.isNotEmpty()) {
            textColumn.addView(meta)
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

        row.addView(typeIcon)
        row.addView(icon)
        row.addView(textColumn)
        row.addView(btnAccept)
        row.addView(btnDecline)

        return row
    }

    private fun confirmationTypeIconRes(item: ConfirmationItem): Int {
        return when {
            isTradeConfirmation(item) -> android.R.drawable.ic_menu_share
            isMarketConfirmation(item) -> android.R.drawable.ic_menu_agenda
            else -> android.R.drawable.ic_menu_info_details
        }
    }

    private fun confirmationTypeLabelRes(item: ConfirmationItem): Int {
        return when {
            isTradeConfirmation(item) -> R.string.confirmation_type_trade
            isMarketConfirmation(item) -> R.string.confirmation_type_market
            else -> R.string.confirmation_type_other
        }
    }

    private fun formatConfirmationRelativeTime(item: ConfirmationItem): String {
        if (item.creationTimeSec <= 0L) return ""
        val createdMs = item.creationTimeSec * 1000L
        return DateUtils.getRelativeTimeSpanString(
            createdMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    private fun respondToBundle(bundle: ConfirmationBundle, accept: Boolean) {
        val auth = activeAuthContext ?: return

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val ok = try {
                ConfirmationService.respondBundleWithRenew(
                    context = this@MainActivity,
                    auth = auth,
                    bundle = bundle,
                    accept = accept,
                    onSessionRenewed = { newAuth -> activeAuthContext = newAuth }
                )
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
        }
    }

    private fun respondToItem(item: ConfirmationItem, accept: Boolean) {
        val auth = activeAuthContext ?: return

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val ok = try {
                ConfirmationService.respondItemWithRenew(
                    context = this@MainActivity,
                    auth = auth,
                    item = item,
                    accept = accept,
                    onSessionRenewed = { newAuth -> activeAuthContext = newAuth }
                )
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
        }
    }

    private fun confirmAcceptAllVisible() {
        val bundles = lastSuccessfulBundles
        val allItems = bundles.flatMap { it.items }
        if (allItems.isEmpty()) {
            Toast.makeText(this, getString(R.string.confirm_all_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val tradeCount = allItems.count { isTradeConfirmation(it) }
        val marketCount = allItems.count { isMarketConfirmation(it) && !isTradeConfirmation(it) }
        val otherCount = allItems.size - tradeCount - marketCount

        val includeTrades = Switch(this).apply {
            text = getString(R.string.confirm_all_include_trades)
            isChecked = false
            visibility = if (tradeCount > 0) View.VISIBLE else View.GONE
        }
        val summary = TextView(this).apply {
            text = getString(
                R.string.confirm_all_breakdown,
                marketCount,
                tradeCount,
                otherCount
            )
            setPadding(0, 0, 0, 16)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(summary)
            if (tradeCount > 0) addView(includeTrades)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_all_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_all_visible) { _, _ ->
                val filtered = if (includeTrades.isChecked || tradeCount == 0) {
                    bundles
                } else {
                    bundles.mapNotNull { bundle ->
                        val kept = bundle.items.filter { !isTradeConfirmation(it) }
                        if (kept.isEmpty()) null else bundle.copy(items = kept)
                    }
                }
                if (filtered.isEmpty() || filtered.sumOf { it.items.size } == 0) {
                    Toast.makeText(this, getString(R.string.confirm_all_empty), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                acceptAllVisibleConfirmations(filtered)
            }
            .show()
    }

    private fun acceptAllVisibleConfirmations(bundles: List<ConfirmationBundle>) {
        val auth = activeAuthContext ?: return
        txtStatus.text = getString(R.string.confirm_all_in_progress)
        btnConfirmAll.isEnabled = false

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            var accepted = 0
            var total = 0
            var workingAuth = auth
            for ((index, bundle) in bundles.withIndex()) {
                if (index > 0) {
                    kotlinx.coroutines.delay(STEAM_CONFIRM_GAP_MS)
                }
                total += bundle.items.size
                val ok = try {
                    ConfirmationService.respondBundleWithRenew(
                        context = this@MainActivity,
                        auth = workingAuth,
                        bundle = bundle,
                        accept = true,
                        onSessionRenewed = { renewed ->
                            workingAuth = renewed
                            activeAuthContext = renewed
                        }
                    )
                } catch (ex: Throwable) {
                    if (looksLikeSteamRateLimit(ex)) {
                        // Stop remaining accepts — do not silent-hammer after 429.
                    }
                    false
                }
                if (ok) {
                    accepted += bundle.items.size
                } else {
                    break
                }
            }

            runOnUiThread {
                btnConfirmAll.isEnabled = true
                txtStatus.text = getString(R.string.confirm_all_done, accepted, total)
                refreshConfirmationsAsync()
            }
        }
    }

    private fun showAccountLabelDialog() {
        val steamId = currentSteamId.ifBlank {
            activeAuthContext?.steamId.orEmpty()
        }
        if (steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }
        AccountLabelHelper.showEditDialog(this, steamId) {
            refreshAccountTitle()
            Code2FAWidgetProvider.requestUpdateAll(this)
        }
    }

    private fun showProxySettingsDialog() {
        updateActiveAuthContext()
        val steamId = activeAuthContext?.steamId.orEmpty().ifBlank { currentSteamId }
        if (steamId.isBlank()) {
            txtStatus.text = getString(R.string.status_login_unavailable)
            return
        }

        val current = AppSettings.getAccountProxyConfig(this, steamId)
        val resolved = AppSettings.resolveProxyConfig(this, steamId)

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

        val txtIp = TextView(this).apply {
            text = when {
                ProxyChecker.isConfigured(current) -> getString(R.string.proxy_using_account)
                ProxyChecker.isConfigured(resolved) -> getString(R.string.proxy_using_default)
                else -> getString(R.string.proxy_status_disabled)
            }
            setPadding(0, 16, 0, 0)
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
            addView(txtIp)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.proxy_settings))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.proxy_clear) { _, _ ->
                AppSettings.clearAccountProxyConfig(this, steamId)
                Toast.makeText(this, getString(R.string.proxy_saved), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, getString(R.string.proxy_invalid), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val config = AccountProxyConfig(
                    enabled = enabled,
                    type = type,
                    host = host,
                    port = port,
                    username = user,
                    password = pass
                )
                AppSettings.setAccountProxyConfig(this, steamId, config)
                txtStatus.text = getString(R.string.proxy_saved)
                if (enabled) {
                    runProxyCheckAfterSave(config)
                } else {
                    Toast.makeText(this, getString(R.string.proxy_saved), Toast.LENGTH_SHORT).show()
                    refreshProxyIndicatorAsync()
                }
            }
            .show()
    }

    private fun runProxyCheckAfterSave(config: AccountProxyConfig) {
        txtStatus.text = getString(R.string.proxy_checking)
        Toast.makeText(this, getString(R.string.proxy_checking), Toast.LENGTH_SHORT).show()
        Thread {
            val result = ProxyChecker.check(config)
            runOnUiThread {
                if (result.ok) {
                    val msg = if (!result.publicIp.isNullOrBlank()) {
                        getString(R.string.proxy_check_ok_with_ip, result.publicIp)
                    } else {
                        getString(R.string.proxy_check_ok)
                    }
                    txtStatus.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    setProxyIndicator(true, msg)
                } else {
                    val msg = getString(R.string.proxy_check_fail_detail)
                    txtStatus.text = msg
                    Toast.makeText(this, getString(R.string.proxy_check_fail), Toast.LENGTH_LONG).show()
                    setProxyIndicator(false, msg)
                }
            }
        }.start()
    }

    private fun refreshProxyIndicatorAsync() {
        val auth = activeAuthContext
        val steamId = auth?.steamId.orEmpty().ifBlank { currentSteamId }
        if (steamId.isBlank()) {
            hideProxyIndicator(getString(R.string.proxy_status_unknown))
            return
        }

        val config = AppSettings.resolveProxyConfig(this, steamId)
        if (!ProxyChecker.isConfigured(config)) {
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
            val result = ProxyChecker.check(config)
            runOnUiThread {
                proxyCheckInProgress = false
                if (result.ok) {
                    val desc = if (!result.publicIp.isNullOrBlank()) {
                        getString(R.string.proxy_check_ok_with_ip, result.publicIp)
                    } else {
                        getString(R.string.proxy_status_working)
                    }
                    setProxyIndicator(true, desc)
                    txtStatus.text = desc
                } else {
                    setProxyIndicator(false, getString(R.string.proxy_status_failed))
                    txtStatus.text = getString(R.string.proxy_status_failed)
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
}
