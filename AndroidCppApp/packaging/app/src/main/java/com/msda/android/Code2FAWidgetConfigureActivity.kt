package com.msda.android

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class Code2FAWidgetConfigureActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_widget_configure)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        loadPersistedMafiles()
        renderAccounts()
    }

    private fun renderAccounts() {
        val list = findViewById<LinearLayout>(R.id.widgetAccountList)
        val empty = findViewById<TextView>(R.id.txtWidgetConfigureEmpty)
        list.removeAllViews()

        val rows = try {
            NativeBridge.getAccounts().lines().map { it.trim() }.filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }

        if (rows.isEmpty()) {
            empty.visibility = View.VISIBLE
            return
        }
        empty.visibility = View.GONE

        for (line in rows) {
            val parts = line.split('|')
            val index = parts.firstOrNull()?.toIntOrNull() ?: -1
            val name = when {
                parts.size >= 2 && parts[1].isNotBlank() -> parts[1]
                parts.isNotEmpty() -> parts[0]
                else -> line
            }
            val steamId = if (parts.size >= 3) parts[2] else ""
            val label = if (steamId.isNotBlank()) AppSettings.getAccountLabel(this, steamId) else ""
            val title = if (label.isNotBlank()) {
                getString(R.string.hub_account_title_with_label, name, label)
            } else {
                name
            }

            val row = TextView(this).apply {
                text = if (steamId.isNotBlank()) "$title\n$steamId" else title
                textSize = 16f
                setPadding(16, 20, 16, 20)
                setOnClickListener {
                    finishWithSelection(index, name, steamId)
                }
            }
            list.addView(row)
        }
    }

    private fun finishWithSelection(index: Int, name: String, steamId: String) {
        AppSettings.setWidgetAccount(this, appWidgetId, steamId, index, name)
        val manager = AppWidgetManager.getInstance(this)
        Code2FAWidgetProvider.updateAppWidget(this, manager, appWidgetId)
        Code2FAWidgetProvider.requestUpdateAll(this)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun loadPersistedMafiles() {
        val importDir = File(filesDir, "mafiles")
        if (!importDir.exists() || !importDir.isDirectory) return
        val hasMafiles = importDir.listFiles()?.any {
            it.isFile && it.name.endsWith(".mafile", ignoreCase = true)
        } == true
        if (!hasMafiles) return
        try {
            NativeBridge.importMafilesFromFolder(importDir.absolutePath)
        } catch (_: Throwable) {
        }
    }
}
