package com.alexpo.grammermate.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

interface BackupManager {

    fun createBackup(): Boolean

    fun restoreFromBackup(backupPath: String): Boolean

    fun restoreFromBackupUri(backupUri: Uri): Boolean

    fun getAvailableBackups(treeUri: Uri): List<BackupInfo>

    fun getAvailableBackups(): List<BackupInfo>

    fun deleteBackup(backupPath: String): Boolean

    fun hasBackup(): Boolean
}

/**
 * Manages backup and restore of user progress data.
 * Saves mastery and progress data to external storage (Downloads/BaseGrammy).
 * Allows recovery after app reinstallation.
 *
 * File enumeration is delegated to [BackupFileCollector].
 * Restore logic is delegated to [BackupRestorer].
 * All internal file writes use [AtomicFileWriter].
 */
class BackupManagerImpl(private val context: Context) : BackupManager {

    private val logTag = "BackupManager"

    private val collector = BackupFileCollector(context)
    private val restorer = BackupRestorer(context)

    // Expose backupDir for callers that need it (e.g. getAvailableBackups legacy).
    private val backupDir: File? get() = collector.backupDir

    // Internal data directory (kept here for legacy createBackup path).
    private val internalDir = File(context.filesDir, "grammarmate")

    // region -- Public API --

    /**
     * Create a backup of all progress data.
     * Returns true if backup succeeded.
     */
    override fun createBackup(): Boolean {
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

    override fun restoreFromBackup(backupPath: String): Boolean {
        return try {
            restorer.restoreFromPath(backupPath)
        } catch (e: Exception) {
            Log.e(logTag, "restoreFromBackup failed", e)
            false
        }
    }

    override fun restoreFromBackupUri(backupUri: Uri): Boolean {
        return try {
            restorer.restoreFromUri(backupUri)
        } catch (e: Exception) {
            Log.e(logTag, "restoreFromBackupUri failed", e)
            false
        }
    }

    override fun getAvailableBackups(treeUri: Uri): List<BackupInfo> {
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
                val timestamp = if (dir.name == "backup_latest") "latest"
                                else dir.name?.removePrefix("backup_") ?: ""
                BackupInfo(
                    name = dir.name ?: "",
                    path = dir.uri.toString(),
                    uri = dir.uri.toString(),
                    timestamp = timestamp,
                    dataSize = safeCalculateDirSize(dir),
                    metadata = safeReadText(dir.findFile("metadata.txt"))
                )
            }
    }

    override fun getAvailableBackups(): List<BackupInfo> {
        return collector.getAvailableBackupsLegacy()
    }

    override fun deleteBackup(backupPath: String): Boolean {
        return try {
            File(backupPath).deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun hasBackup(): Boolean = getAvailableBackups().isNotEmpty()

    // endregion

    // region -- Legacy backup (file-system) --

    private fun createBackupLegacy(): Boolean {
        val dir = backupDir ?: return false
        val backupSubDir = File(dir, "backup_latest")
        if (!backupSubDir.exists() && !backupSubDir.mkdirs()) return false

        val timestamp = collector.currentTimestamp()

        // Copy main files
        collector.mainBackupFileNames.forEach { name ->
            val src = File(internalDir, name)
            if (src.exists()) {
                src.copyTo(File(backupSubDir, name), overwrite = true)
            }
        }

        // Copy streak files
        collector.listStreakFiles().forEach { file ->
            file.copyTo(File(backupSubDir, file.name), overwrite = true)
        }

        // Remove old streak.yaml if present (migration)
        val oldStreakBackup = File(backupSubDir, "streak.yaml")
        if (oldStreakBackup.exists()) oldStreakBackup.delete()

        // Copy drill-progress files
        collector.listDrillProgressFiles().forEach { file ->
            file.copyTo(File(backupSubDir, file.name), overwrite = true)
        }

        // Copy pack-scoped drill data
        collector.listPackDrillDirs().forEach { packDir ->
            val verbProgress = File(packDir, "verb_drill_progress.yaml")
            if (verbProgress.exists()) {
                val targetDir = File(backupSubDir, "drills/${packDir.name}")
                targetDir.mkdirs()
                verbProgress.copyTo(File(targetDir, "verb_drill_progress.yaml"), overwrite = true)
            }
            val wordMastery = File(packDir, "word_mastery.yaml")
            if (wordMastery.exists()) {
                val targetDir = File(backupSubDir, "drills/${packDir.name}")
                targetDir.mkdirs()
                wordMastery.copyTo(File(targetDir, "word_mastery.yaml"), overwrite = true)
            }
        }

        // Write metadata via AtomicFileWriter (fixes violation)
        writeBackupMetadata(backupSubDir, timestamp)

        return true
    }

    // endregion

    // region -- Scoped backup (MediaStore, Android 10+) --

    private fun createBackupScoped(): Boolean {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/BaseGrammy/backup_latest/"
        val timestamp = collector.currentTimestamp()
        var wroteAny = false

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
                val legacyFile = backupDir?.let { File(it, "backup_latest/$name") }
                if (legacyFile?.exists() == true && legacyFile.delete()) {
                    Log.d(logTag, "Deleted legacy $name before MediaStore insert")
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            } ?: return false
            resolver.openOutputStream(targetUri, "wt")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
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
                val legacyFile = backupDir?.let { File(it, "backup_latest/$name") }
                if (legacyFile?.exists() == true && legacyFile.delete()) {
                    Log.d(logTag, "Deleted legacy $name before MediaStore insert")
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

        // Write main files
        collector.mainBackupFileNames.forEach { name ->
            wroteAny = writeFile(name, "text/yaml", File(internalDir, name)) || wroteAny
        }

        // Write streak files
        collector.listStreakFiles().forEach { file ->
            wroteAny = writeFile(file.name, "text/yaml", file) || wroteAny
        }

        // Write drill-progress files
        collector.listDrillProgressFiles().forEach { file ->
            wroteAny = writeFile(file.name, "text/yaml", file) || wroteAny
        }

        // Write pack-scoped drill data (flat names for MediaStore)
        collector.listPackDrillDirs().forEach { packDir ->
            val verbProgress = File(packDir, "verb_drill_progress.yaml")
            if (verbProgress.exists()) {
                wroteAny = writeFile("drills_${packDir.name}_verb_drill_progress.yaml", "text/yaml", verbProgress) || wroteAny
            }
            val wordMastery = File(packDir, "word_mastery.yaml")
            if (wordMastery.exists()) {
                wroteAny = writeFile("drills_${packDir.name}_word_mastery.yaml", "text/yaml", wordMastery) || wroteAny
            }
        }

        // Write metadata
        val metadata = collector.buildMetadataContent(timestamp)
        wroteAny = writeText("metadata.txt", metadata) || wroteAny

        return wroteAny
    }

    // endregion

    // region -- Metadata write (AtomicFileWriter) --

    private fun writeBackupMetadata(backupDir: File, timestamp: String) {
        try {
            val metadataFile = File(backupDir, "metadata.txt")
            val content = collector.buildMetadataContent(timestamp)
            AtomicFileWriter.writeText(metadataFile, content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // endregion

    // region -- DocumentFile helpers --

    private fun safeReadText(file: DocumentFile?): String {
        return try {
            if (file == null || !file.exists()) return ""
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun safeCalculateDirSize(dir: DocumentFile): Long = try {
        dir.listFiles().sumOf { it.length() }
    } catch (_: Exception) { 0L }

    // endregion
}

data class BackupInfo(
    val name: String,
    val path: String,
    val uri: String? = null,
    val timestamp: String,
    val dataSize: Long,
    val metadata: String
)
