package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
     * Saves mastery.yaml and progress.yaml to Downloads/BaseGrammy/backup_latest
     * Overwrites previous backup to avoid creating multiple backup folders
     * Returns true if backup succeeded
     */
    fun createBackup(): Boolean {
        return try {
            if (backupDir == null) return false

            val backupSubDir = File(backupDir, "backup_latest")
            if (!backupSubDir.exists()) {
                if (!backupSubDir.mkdirs()) return false
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

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

            // Backup streak data (all streak files)
            internalDir.listFiles { file ->
                file.name.startsWith("streak_") && file.name.endsWith(".yaml")
            }?.forEach { streakFile ->
                val backupStreakFile = File(backupSubDir, streakFile.name)
                streakFile.copyTo(backupStreakFile, overwrite = true)
            }

            // Remove old streak.yaml if exists (migration to new format)
            val oldStreakBackup = File(backupSubDir, "streak.yaml")
            if (oldStreakBackup.exists()) {
                oldStreakBackup.delete()
            }

            // Backup user profile data
            val profileFile = File(internalDir, "profile.yaml")
            if (profileFile.exists()) {
                val backupProfileFile = File(backupSubDir, "profile.yaml")
                profileFile.copyTo(backupProfileFile, overwrite = true)
            }

            // Create/update backup metadata
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
            if (!internalDir.exists() && !internalDir.mkdirs()) return false

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

            // Restore all streak files
            backupSubDir.listFiles { file ->
                file.name.startsWith("streak_") && file.name.endsWith(".yaml")
            }?.forEach { backupStreakFile ->
                val streakFile = File(internalDir, backupStreakFile.name)
                backupStreakFile.copyTo(streakFile, overwrite = true)
            }

            // Migrate old streak.yaml format to new streak_<languageId>.yaml format
            val oldStreakFile = File(backupSubDir, "streak.yaml")
            if (oldStreakFile.exists()) {
                try {
                    val content = oldStreakFile.readText()
                    val data = yaml.load<Any>(content) as? Map<*, *>
                    val languageId = data?.get("languageId") as? String ?: "en"
                    val streakFile = File(internalDir, "streak_$languageId.yaml")
                    oldStreakFile.copyTo(streakFile, overwrite = true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Restore user profile data
            val backupProfileFile = File(backupSubDir, "profile.yaml")
            if (backupProfileFile.exists()) {
                val profileFile = File(internalDir, "profile.yaml")
                backupProfileFile.copyTo(profileFile, overwrite = true)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAvailableBackups(treeUri: Uri): List<BackupInfo> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isDirectory && (it.name?.startsWith("backup_") == true) }
            .sortedByDescending { it.lastModified() }
            .map { dir ->
                val timestamp = dir.name?.removePrefix("backup_") ?: ""
                val metadataFile = dir.findFile("metadata.txt")
                val dataSize = safeCalculateDirSize(dir)

                BackupInfo(
                    name = dir.name ?: "",
                    path = dir.uri.toString(),
                    uri = dir.uri.toString(),
                    timestamp = timestamp,
                    dataSize = dataSize,
                    metadata = safeReadText(metadataFile)
                )
            }
    }

    fun restoreFromBackupUri(backupUri: Uri): Boolean {
        return try {
            val backupDir = DocumentFile.fromTreeUri(context, backupUri)
                ?: DocumentFile.fromSingleUri(context, backupUri)
                ?: return false
            if (!internalDir.exists() && !internalDir.mkdirs()) return false
            var copied = false

            // Restore main files
            val mainFiles = listOf("mastery.yaml", "progress.yaml", "profile.yaml")
            mainFiles.forEach { name ->
                val source = backupDir.findFile(name) ?: return@forEach
                val target = File(internalDir, name)
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    copied = true
                }
            }

            // Restore all streak files (streak_en.yaml, streak_ru.yaml, etc.)
            backupDir.listFiles().forEach { file ->
                if (file.name?.startsWith("streak_") == true && file.name?.endsWith(".yaml") == true) {
                    val target = File(internalDir, file.name!!)
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                        copied = true
                    }
                }
            }

            // Migrate old streak.yaml format to new streak_<languageId>.yaml format
            val oldStreakFile = backupDir.findFile("streak.yaml")
            if (oldStreakFile != null) {
                try {
                    val content = context.contentResolver.openInputStream(oldStreakFile.uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val data = yaml.load<Any>(content) as? Map<*, *>
                        val languageId = data?.get("languageId") as? String ?: "en"
                        val target = File(internalDir, "streak_$languageId.yaml")
                        target.writeText(content)
                        copied = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            copied
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
            val dataSize = safeCalculateDirSize(dir)

            BackupInfo(
                name = dir.name,
                path = dir.absolutePath,
                timestamp = timestamp,
                dataSize = dataSize,
                metadata = safeReadText(metadataFile)
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
                - profile.yaml (user name and settings)
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

    private fun safeReadText(file: File): String {
        return try {
            if (file.exists()) file.readText() else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun safeReadText(file: DocumentFile?): String {
        return try {
            if (file == null || !file.exists()) return ""
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun safeCalculateDirSize(dir: File): Long {
        return try {
            calculateDirSize(dir)
        } catch (e: Exception) {
            0L
        }
    }

    private fun safeCalculateDirSize(dir: DocumentFile): Long {
        return try {
            dir.listFiles().sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }
}

data class BackupInfo(
    val name: String,
    val path: String,
    val uri: String? = null,
    val timestamp: String,
    val dataSize: Long,
    val metadata: String
)
