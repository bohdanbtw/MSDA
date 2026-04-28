package com.msda.android

import android.content.Context
import java.io.File

object PasswordBackupHelper {
    private const val DB_FILE_NAME = "steam_passwords.db"

    /**
     * Copies the encrypted password database to the chosen output file.
     * Returns true on success.
     */
    fun backupPasswords(context: Context, outputFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_FILE_NAME)
            if (!dbFile.exists()) return false
            dbFile.copyTo(outputFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Replaces the current encrypted password database with a backup file.
     * Any currently open database handles may be invalid after this call.
     * Returns true on success.
     */
    fun restorePasswords(context: Context, backupFile: File): Boolean {
        if (!backupFile.exists()) return false
        return try {
            val destFile = context.getDatabasePath(DB_FILE_NAME)

            // Remove the existing database before copying the backup
            if (destFile.exists()) {
                if (!destFile.delete()) return false
            }

            backupFile.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
