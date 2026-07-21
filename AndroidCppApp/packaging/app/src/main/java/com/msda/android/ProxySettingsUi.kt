package com.msda.android

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object ProxySettingsUi {
    fun showEditor(
        context: Context,
        title: String,
        current: AccountProxyConfig,
        enableLabel: String,
        onClear: () -> Unit,
        onSaved: (AccountProxyConfig) -> Unit
    ) {
        val enabledSwitch = Switch(context).apply {
            text = enableLabel
            isChecked = current.enabled
        }

        val typeGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val httpOption = RadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.proxy_type_http)
        }
        val socksOption = RadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.proxy_type_socks)
        }
        typeGroup.addView(httpOption)
        typeGroup.addView(socksOption)
        if (current.type.equals("socks", ignoreCase = true)) {
            typeGroup.check(socksOption.id)
        } else {
            typeGroup.check(httpOption.id)
        }

        val hostInput = EditText(context).apply {
            hint = context.getString(R.string.proxy_host_hint)
            setText(current.host)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val portInput = EditText(context).apply {
            hint = context.getString(R.string.proxy_port_hint)
            setText(if (current.port > 0) current.port.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val userInput = EditText(context).apply {
            hint = context.getString(R.string.proxy_username_hint)
            setText(current.username)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val passInput = EditText(context).apply {
            hint = context.getString(R.string.proxy_password_hint)
            setText(current.password)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val txtIp = TextView(context).apply {
            text = context.getString(R.string.proxy_public_ip_unknown)
            setPadding(0, 16, 0, 0)
        }

        val container = LinearLayout(context).apply {
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

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.proxy_clear) { _, _ -> onClear() }
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val enabled = enabledSwitch.isChecked
                val host = hostInput.text?.toString().orEmpty().trim()
                val port = portInput.text?.toString().orEmpty().toIntOrNull() ?: 0
                val user = userInput.text?.toString().orEmpty().trim()
                val pass = passInput.text?.toString().orEmpty()
                val type = if (typeGroup.checkedRadioButtonId == socksOption.id) "socks" else "http"

                if (enabled && (host.isBlank() || port !in 1..65535)) {
                    Toast.makeText(context, R.string.proxy_invalid, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val config = AccountProxyConfig(
                    enabled = enabled,
                    type = type,
                    host = host,
                    port = port,
                    username = user,
                    password = pass
                )
                onSaved(config)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    fun runCheckAndNotify(
        context: Context,
        config: AccountProxyConfig,
        statusView: TextView? = null,
        onFinished: ((ProxyCheckResult) -> Unit)? = null
    ) {
        if (!ProxyChecker.isConfigured(config)) {
            statusView?.setText(R.string.proxy_status_disabled)
            onFinished?.invoke(ProxyCheckResult(false, null))
            return
        }
        statusView?.setText(R.string.proxy_checking)
        Toast.makeText(context, R.string.proxy_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val result = ProxyChecker.check(config)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (result.ok) {
                    val msg = if (!result.publicIp.isNullOrBlank()) {
                        context.getString(R.string.proxy_check_ok_with_ip, result.publicIp)
                    } else {
                        context.getString(R.string.proxy_check_ok)
                    }
                    statusView?.text = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    statusView?.setText(R.string.proxy_check_fail_detail)
                    Toast.makeText(context, R.string.proxy_check_fail, Toast.LENGTH_LONG).show()
                }
                onFinished?.invoke(result)
            }
        }.start()
    }
}
