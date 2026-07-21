package com.msda.android

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

object AccountLabelHelper {
    fun showEditDialog(context: Context, steamId: String, onSaved: (() -> Unit)? = null) {
        if (steamId.isBlank()) return

        val current = AppSettings.getAccountLabel(context, steamId)
        val input = EditText(context).apply {
            hint = context.getString(R.string.account_label_hint)
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT
            setSelection(text?.length ?: 0)
        }

        val presets = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val labels = listOf(
                R.string.account_label_preset_main,
                R.string.account_label_preset_bot,
                R.string.account_label_preset_farm
            )
            for (resId in labels) {
                addView(Button(context).apply {
                    text = context.getString(resId)
                    textSize = 12f
                    setOnClickListener {
                        input.setText(context.getString(resId))
                        input.setSelection(input.text?.length ?: 0)
                    }
                })
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 0)
            addView(input)
            addView(presets)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.account_label_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.account_label_clear) { _, _ ->
                AppSettings.setAccountLabel(context, steamId, "")
                Toast.makeText(context, R.string.account_label_saved, Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
            }
            .setPositiveButton(R.string.account_label_save) { _, _ ->
                AppSettings.setAccountLabel(context, steamId, input.text?.toString().orEmpty())
                Toast.makeText(context, R.string.account_label_saved, Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
            }
            .show()
    }
}
