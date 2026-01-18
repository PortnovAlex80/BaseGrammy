package com.alexpo.grammermate.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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
    private val logTag = "BackupManager"

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
            val success = if (Build.VERSION.SDK_INT >= 29) {
                if (Environment.isExternalStorageLegacy()) {
                    createBackupLegacy()
                } else {
                    createBackupScoped()
                }
            } else {
                createBackupLegacy()
            }
            Log.d(logTag, "createBackup: success=$success")
            success
        } catch (e: Exception) {
            Log.e(logTag, "createBackup failed", e)
            false
        }
    }

    private fun createBackupLegacy(): Boolean {
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

        return true
    }

    private fun createBackupScoped(): Boolean {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/BaseGrammy/backup_latest/"
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        var wroteAny = false

        // IMPORTANT: on Android 10+ we must overwrite existing MediaStore entries,
        // not insert new ones with the same name, or the system will create "(1)" duplicates.
        // Keep this behavior to ensure backup_latest always has the latest files.
        val pathLike = "%BaseGrammy/backup_latest%"

        fun deleteDuplicateEntries(name: String) {
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val deleted = resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection,
                arrayOf(pathLike, "$name (%")
            )
            if (deleted > 0) {
                Log.d(logTag, "Removed $deleted duplicate entries for $name in backup_latest")
            }
        }

        fun deleteLegacyDuplicateFiles(name: String) {
            val legacyDir = backupDir?.let { File(it, "backup_latest") } ?: return
            legacyDir.listFiles()
                ?.filter { it.name.startsWith("$name (") }
                ?.forEach { file ->
                    if (file.delete()) {
                        Log.d(logTag, "Deleted legacy duplicate file ${file.name}")
                    }
                }
        }

        fun findExistingEntry(name: String): Uri? {
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                arrayOf(pathLike, name),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
            return null
        }

        fun writeFile(name: String, mimeType: String, source: File): Boolean {
            if (!source.exists()) return false
            deleteDuplicateEntries(name)
            deleteLegacyDuplicateFiles(name)
            val existingUri = findExistingEntry(name)
            if (existingUri != null) {
                Log.d(logTag, "Overwriting existing $name in backup_latest")
            }
            val targetUri = existingUri ?: run {
                // If we cannot find a MediaStore entry, try deleting a legacy file to avoid "(1)" duplicates.
                if (existingUri == null) {
                    val legacyFile = backupDir?.let { File(it, "backup_latest/$name") }
                    if (legacyFile?.exists() == true && legacyFile.delete()) {
                        Log.d(logTag, "Deleted legacy $name before MediaStore insert")
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } ?: return false
            resolver.openOutputStream(targetUri, "wt")?.use { output ->
                source.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            return true
        }

        fun writeText(name: String, text: String): Boolean {
            deleteDuplicateEntries(name)
            deleteLegacyDuplicateFiles(name)
            val existingUri = findExistingEntry(name)
            if (existingUri != null) {
                Log.d(logTag, "Overwriting existing $name in backup_latest")
            }
            val targetUri = existingUri ?: run {
                // If we cannot find a MediaStore entry, try deleting a legacy file to avoid "(1)" duplicates.
                if (existingUri == null) {
                    val legacyFile = backupDir?.let { File(it, "backup_latest/$name") }
                    if (legacyFile?.exists() == true && legacyFile.delete()) {
                        Log.d(logTag, "Deleted legacy $name before MediaStore insert")
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } ?: return false
            resolver.openOutputStream(targetUri, "wt")?.use { output ->
                output.write(text.toByteArray())
            } ?: return false
            return true
        }

        wroteAny = writeFile("mastery.yaml", "text/yaml", File(internalDir, "mastery.yaml")) || wroteAny
        wroteAny = writeFile("progress.yaml", "text/yaml", File(internalDir, "progress.yaml")) || wroteAny
        wroteAny = writeFile("profile.yaml", "text/yaml", File(internalDir, "profile.yaml")) || wroteAny

        internalDir.listFiles { file ->
            file.name.startsWith("streak_") && file.name.endsWith(".yaml")
        }?.forEach { streakFile ->
            wroteAny = writeFile(streakFile.name, "text/yaml", streakFile) || wroteAny
        }

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
        wroteAny = writeText("metadata.txt", metadata) || wroteAny

        return wroteAny
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
            .filter { file ->
                file.isDirectory && (
                    file.name?.startsWith("backup_") == true ||
                        file.name == "backup_latest"
                    )
            }
            .sortedByDescending { it.lastModified() }
            .map { dir ->
                val timestamp = if (dir.name == "backup_latest") {
                    "latest"
                } else {
                    dir.name?.removePrefix("backup_") ?: ""
                }
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
        val logBuilder = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        logBuilder.appendLine("=== Backup Restore Log ===")
        logBuilder.appendLine("Timestamp: $timestamp")
        logBuilder.appendLine("Backup URI: $backupUri")
        logBuilder.appendLine()

        return try {
            val rootDir = DocumentFile.fromTreeUri(context, backupUri)
                ?: DocumentFile.fromSingleUri(context, backupUri)
                ?: run {
                    logBuilder.appendLine("ERROR: Failed to open backup directory from URI")
                    writeRestoreLog(backupUri, logBuilder.toString())
                    return false
                }

            logBuilder.appendLine("Root directory: ${rootDir.uri}")

            // Look for backup_latest subfolder first, then fall back to root
            val backupDir = rootDir.findFile("backup_latest") ?: rootDir
            val isSubfolder = backupDir != rootDir

            logBuilder.appendLine("Using backup source: ${if (isSubfolder) "backup_latest subfolder" else "root folder"}")
            logBuilder.appendLine("Backup directory: ${backupDir.uri}")
            logBuilder.appendLine("Internal data directory: ${internalDir.absolutePath}")
            logBuilder.appendLine()

            if (!internalDir.exists() && !internalDir.mkdirs()) {
                logBuilder.appendLine("ERROR: Failed to create internal directory")
                writeRestoreLog(backupUri, logBuilder.toString())
                return false
            }

            var copied = false
            val restoredFiles = mutableListOf<String>()
            val missingFiles = mutableListOf<String>()

            // Restore main files
            logBuilder.appendLine("--- Main Files ---")
            val mainFiles = listOf("mastery.yaml", "progress.yaml", "profile.yaml")
            mainFiles.forEach { name ->
                val source = backupDir.findFile(name)
                if (source == null) {
                    logBuilder.appendLine("MISSING: $name")
                    missingFiles.add(name)
                } else {
                    val target = File(internalDir, name)
                    try {
                        context.contentResolver.openInputStream(source.uri)?.use { input ->
                            target.outputStream().use { output ->
                                val bytes = input.copyTo(output)
                                logBuilder.appendLine("OK: $name (${bytes} bytes)")
                                restoredFiles.add(name)
                                copied = true
                            }
                        }
                    } catch (e: Exception) {
                        logBuilder.appendLine("ERROR: $name - ${e.message}")
                    }
                }
            }
            logBuilder.appendLine()

            // Restore all streak files (streak_en.yaml, streak_ru.yaml, etc.)
            logBuilder.appendLine("--- Streak Files ---")
            var streakCount = 0
            backupDir.listFiles().forEach { file ->
                if (file.name?.startsWith("streak_") == true && file.name?.endsWith(".yaml") == true) {
                    val target = File(internalDir, file.name!!)
                    try {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            target.outputStream().use { output ->
                                val bytes = input.copyTo(output)
                                logBuilder.appendLine("OK: ${file.name} (${bytes} bytes)")
                                restoredFiles.add(file.name!!)
                                streakCount++
                                copied = true
                            }
                        }
                    } catch (e: Exception) {
                        logBuilder.appendLine("ERROR: ${file.name} - ${e.message}")
                    }
                }
            }
            if (streakCount == 0) {
                logBuilder.appendLine("No streak_*.yaml files found")
            }
            logBuilder.appendLine()

            // Migrate old streak.yaml format to new streak_<languageId>.yaml format
            logBuilder.appendLine("--- Old Format Migration ---")
            val oldStreakFile = backupDir.findFile("streak.yaml")
            if (oldStreakFile != null) {
                try {
                    val content = context.contentResolver.openInputStream(oldStreakFile.uri)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val data = yaml.load<Any>(content) as? Map<*, *>
                        val languageId = data?.get("languageId") as? String ?: "en"
                        val target = File(internalDir, "streak_$languageId.yaml")
                        target.writeText(content)
                        logBuilder.appendLine("OK: Migrated streak.yaml -> streak_$languageId.yaml")
                        restoredFiles.add("streak_$languageId.yaml (migrated)")
                        copied = true
                    }
                } catch (e: Exception) {
                    logBuilder.appendLine("ERROR: Failed to migrate streak.yaml - ${e.message}")
                }
            } else {
                logBuilder.appendLine("No old streak.yaml found (this is normal for new backups)")
            }
            logBuilder.appendLine()

            // Parse and log mastery data
            logBuilder.appendLine("--- Mastery Data Analysis ---")
            try {
                val masteryFile = File(internalDir, "mastery.yaml")
                if (masteryFile.exists()) {
                    val masteryContent = yaml.load<Any>(masteryFile.readText()) as? Map<*, *>
                    val data = masteryContent?.get("data") as? Map<*, *>
                    if (data != null) {
                        data.forEach { (lang, lessons) ->
                            logBuilder.appendLine("Language: $lang")
                            val lessonsMap = lessons as? Map<*, *>
                            lessonsMap?.forEach { (lessonId, lessonData) ->
                                val ld = lessonData as? Map<*, *>
                                val uniqueShows = ld?.get("uniqueCardShows") as? Number
                                val totalShows = ld?.get("totalCardShows") as? Number
                                val lastShowMs = ld?.get("lastShowDateMs") as? Number
                                val intervalStep = ld?.get("intervalStepIndex") as? Number
                                logBuilder.appendLine("  $lessonId: unique=$uniqueShows, total=$totalShows, intervalStep=$intervalStep")
                                if (lastShowMs != null) {
                                    val daysSinceShow = (System.currentTimeMillis() - lastShowMs.toLong()) / (24 * 60 * 60 * 1000)
                                    logBuilder.appendLine("    Last shown: $daysSinceShow days ago")
                                }
                            }
                        }
                    } else {
                        logBuilder.appendLine("No mastery data found in file")
                    }
                } else {
                    logBuilder.appendLine("Mastery file not found after restore")
                }
            } catch (e: Exception) {
                logBuilder.appendLine("ERROR parsing mastery: ${e.message}")
            }
            logBuilder.appendLine()

            // Summary
            logBuilder.appendLine("=== Summary ===")
            logBuilder.appendLine("Restored files: ${restoredFiles.size}")
            restoredFiles.forEach { logBuilder.appendLine("  ✓ $it") }
            if (missingFiles.isNotEmpty()) {
                logBuilder.appendLine("Missing files: ${missingFiles.size}")
                missingFiles.forEach { logBuilder.appendLine("  ✗ $it") }
            }
            logBuilder.appendLine()
            logBuilder.appendLine("Result: ${if (copied) "SUCCESS" else "FAILED - no files copied"}")

            writeRestoreLog(backupUri, logBuilder.toString())
            copied
        } catch (e: Exception) {
            logBuilder.appendLine()
            logBuilder.appendLine("FATAL ERROR: ${e.message}")
            logBuilder.appendLine("Stack trace:")
            logBuilder.appendLine(e.stackTraceToString())
            writeRestoreLog(backupUri, logBuilder.toString())
            e.printStackTrace()
            false
        }
    }

    private fun writeRestoreLog(backupUri: Uri, logContent: String) {
        try {
            val backupDir = DocumentFile.fromTreeUri(context, backupUri)
                ?: DocumentFile.fromSingleUri(context, backupUri)
                ?: return

            // Try to write to backup directory
            val logFile = backupDir.findFile("restore_log.txt")
            if (logFile != null) {
                // Overwrite existing log
                context.contentResolver.openOutputStream(logFile.uri, "wt")?.use { output ->
                    output.write(logContent.toByteArray())
                }
            } else {
                // Create new log file
                val newLog = backupDir.createFile("text/plain", "restore_log.txt")
                if (newLog != null) {
                    context.contentResolver.openOutputStream(newLog.uri)?.use { output ->
                        output.write(logContent.toByteArray())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get list of available backups
     */
    fun getAvailableBackups(): List<BackupInfo> {
        if (backupDir == null) return emptyList()

        return backupDir!!.listFiles { file ->
            file.isDirectory && (file.name.startsWith("backup_") || file.name == "backup_latest")
        }?.sortedByDescending { it.lastModified() }?.map { dir ->
            val timestamp = if (dir.name == "backup_latest") {
                "latest"
            } else {
                dir.name.removePrefix("backup_")
            }
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
