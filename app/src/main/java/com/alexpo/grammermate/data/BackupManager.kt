package com.alexpo.grammermate.data

import android.content.Context
import android.app.Application
import android.net.Uri
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
 * Saves mastery and progress data to internal app-private storage.
 * Export via SAF for user-controlled sharing.
 *
 * File enumeration is delegated to [BackupFileCollector].
 * Restore logic is delegated to [BackupRestorer].
 * All internal file writes use [AtomicFileWriter].
 */
class BackupManagerImpl(private val context: Context) : BackupManager {

    private val logTag = "BackupManager"

    private val collector = BackupFileCollector(context)
    private val restorer = BackupRestorer(context)

    // Expose backupDir for callers that need it (e.g. getAvailableBackups).
    private val backupDir: File? get() = collector.backupDir

    // Internal data directory.
    private val internalDir = File(context.filesDir, "grammarmate")

    // region -- Public API --

    /**
     * Create a backup of all progress data.
     * Returns true if backup succeeded.
     */
    override fun createBackup(): Boolean {
        return try {
            MasteryStoreImpl.drainBeforeRestore(context)
            val success = createBackupToInternal()
            Log.d(logTag, "createBackup: success=$success")
            success
        } catch (e: Exception) {
            Log.e(logTag, "createBackup failed", e)
            false
        }
    }

    override fun restoreFromBackup(backupPath: String): Boolean {
        return try {
            restoreWithCacheBarrier { restorer.restoreFromPath(backupPath) }
        } catch (e: Exception) {
            Log.e(logTag, "restoreFromBackup failed", e)
            false
        }
    }

    override fun restoreFromBackupUri(backupUri: Uri): Boolean {
        return try {
            restoreWithCacheBarrier { restorer.restoreFromUri(backupUri) }
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

    private fun restoreWithCacheBarrier(restore: () -> Boolean): Boolean {
        MasteryStoreImpl.drainBeforeRestore(context)
        val restored = restore()
        if (restored) {
            MasteryStoreImpl.invalidateAfterRestore(context)
            (context.applicationContext as? Application)?.let {
                StoreFactory.getInstance(it).invalidateAfterRestore()
            }
        }
        return restored
    }

    // endregion

    // region -- Internal backup --

    private fun createBackupToInternal(): Boolean {
        val dir = backupDir ?: return false
        val backupSubDir = File(dir, "backup_latest")
        val stagingDir = File(dir, ".backup_staging")
        val previousDir = File(dir, ".backup_previous")
        if (stagingDir.exists() && !stagingDir.deleteRecursively()) return false
        if (!stagingDir.mkdirs()) return false
        val timestamp = collector.currentTimestamp()
        return try {
            BackupSnapshotFiles.listIn(internalDir).forEach { (relativePath, source) ->
                AtomicFileWriter.copyAtomic(source, File(stagingDir, relativePath))
            }
            writeBackupMetadata(stagingDir, timestamp)
            if (previousDir.exists() && !previousDir.deleteRecursively()) return false
            if (backupSubDir.exists() && !backupSubDir.renameTo(previousDir)) return false
            if (!stagingDir.renameTo(backupSubDir)) {
                if (previousDir.exists()) previousDir.renameTo(backupSubDir)
                return false
            }
            if (previousDir.exists()) previousDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(logTag, "Failed to build backup snapshot", e)
            stagingDir.deleteRecursively()
            false
        }
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
