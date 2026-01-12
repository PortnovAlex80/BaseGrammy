package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages backup and restore of user progress data.
 * Saves mastery and progress data to external storage (Downloads/BaseGrammy).
 * Allows recovery after app reinstallation.
 */
class BackupManager(private val context: Context) {
    private val yaml = org.yaml.snakeyaml.Yaml()

    // Backup directory: Downloads/BaseGrammy
    private val backupDir: File? by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, "BaseGrammy").apply {
            if (!exists()) mkdirs()
        }
    }

    // Internal data directory
    private val internalDir = File(context.filesDir, "grammarmate")

    /**
     * Create a backup of all progress data.
     * Saves mastery.yaml and progress.yaml to Downloads/BaseGrammy
     * Returns true if backup succeeded
     */
    fun createBackup(): Boolean {
        return try {
            if (backupDir == null) return false

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val backupSubDir = File(backupDir, "backup_$timestamp")

            if (!backupSubDir.mkdirs()) return false

            // Backup mastery data
            val masteryFile = File(internalDir, "mastery.yaml")
            if (masteryFile.exists()) {
                val backupMasteryFile = File(backupSubDir, "mastery.yaml")
                masteryFile.copyTo(backupMasteryFile, overwrite = true)
            }

            // Backup progress data
            val progressFile = File(internalDir, "progress.yaml")
            if (progressFile.exists()) {
                val backupProgressFile = File(backupSubDir, "progress.yaml")
                progressFile.copyTo(backupProgressFile, overwrite = true)
            }

            // Backup streak data
            val streakFile = File(internalDir, "streak.yaml")
            if (streakFile.exists()) {
                val backupStreakFile = File(backupSubDir, "streak.yaml")
                streakFile.copyTo(backupStreakFile, overwrite = true)
            }

            // Create backup metadata
            createBackupMetadata(backupSubDir, timestamp)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Restore progress from a backup file.
     * User should manually select which backup to restore.
     * Returns true if restore succeeded
     */
    fun restoreFromBackup(backupPath: String): Boolean {
        return try {
            val backupSubDir = File(backupPath)
            if (!backupSubDir.exists()) return false

            // Restore mastery data
            val backupMasteryFile = File(backupSubDir, "mastery.yaml")
            if (backupMasteryFile.exists()) {
                val masteryFile = File(internalDir, "mastery.yaml")
                backupMasteryFile.copyTo(masteryFile, overwrite = true)
            }

            // Restore progress data
            val backupProgressFile = File(backupSubDir, "progress.yaml")
            if (backupProgressFile.exists()) {
                val progressFile = File(internalDir, "progress.yaml")
                backupProgressFile.copyTo(progressFile, overwrite = true)
            }

            // Restore streak data
            val backupStreakFile = File(backupSubDir, "streak.yaml")
            if (backupStreakFile.exists()) {
                val streakFile = File(internalDir, "streak.yaml")
                backupStreakFile.copyTo(streakFile, overwrite = true)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get list of available backups
     */
    fun getAvailableBackups(): List<BackupInfo> {
        if (backupDir == null) return emptyList()

        return backupDir!!.listFiles { file ->
            file.isDirectory && file.name.startsWith("backup_")
        }?.sortedByDescending { it.lastModified() }?.map { dir ->
            val timestamp = dir.name.removePrefix("backup_")
            val metadataFile = File(dir, "metadata.txt")
            val dataSize = calculateDirSize(dir)

            BackupInfo(
                name = dir.name,
                path = dir.absolutePath,
                timestamp = timestamp,
                dataSize = dataSize,
                metadata = if (metadataFile.exists()) metadataFile.readText() else ""
            )
        } ?: emptyList()
    }

    /**
     * Delete a backup
     */
    fun deleteBackup(backupPath: String): Boolean {
        return try {
            val backupDir = File(backupPath)
            backupDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if there's any backup available
     */
    fun hasBackup(): Boolean {
        return getAvailableBackups().isNotEmpty()
    }

    private fun createBackupMetadata(backupDir: File, timestamp: String) {
        try {
            val metadataFile = File(backupDir, "metadata.txt")
            val metadata = """
                Backup created: $timestamp
                App version: 1.0
                Data format: YAML
                Contents:
                - mastery.yaml (flower levels and progress)
                - progress.yaml (training session progress)
                - streak.yaml (daily streak data)
            """.trimIndent()
            metadataFile.writeText(metadata)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calculateDirSize(dir: File): Long {
        return dir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }
    }
}

data class BackupInfo(
    val name: String,
    val path: String,
    val timestamp: String,
    val dataSize: Long,
    val metadata: String
)
