package com.msda.android

import android.content.Context
import java.io.File

/**
 * Imports persisted mafiles at most once per process, off the main thread.
 *
 * Widget refreshes must not re-import/decrypt every mafile on the broadcast thread; callers
 * can pass a callback that refreshes the UI once the (single) import has finished.
 */
object MafileImportManager {
    @Volatile
    private var imported = false

    private val lock = Any()
    private var attempted = false

    fun isImported(): Boolean = imported

    fun ensureImported(context: Context, onImported: (() -> Unit)? = null) {
        if (imported) return
        val appContext = context.applicationContext
        val shouldStart = synchronized(lock) {
            if (attempted) {
                false
            } else {
                attempted = true
                true
            }
        }
        if (!shouldStart) return

        Thread {
            try {
                val importDir = File(appContext.filesDir, "mafiles")
                if (importDir.isDirectory && importDir.listFiles()?.any {
                        it.isFile && it.name.endsWith(".mafile", ignoreCase = true)
                    } == true) {
                    imported = NativeBridge.importMafilesFromFolder(importDir.absolutePath)
                }
            } catch (_: Throwable) {
                imported = false
            }
            if (imported) onImported?.invoke()
        }.apply { name = "msda-mafile-import" }.start()
    }
}
