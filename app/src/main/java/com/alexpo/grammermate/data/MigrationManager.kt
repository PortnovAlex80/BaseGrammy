package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Manages data migrations with automatic backup and rollback capabilities.
 *
 * Features:
 * - Automatic backup before migration
 * - Rollback mechanism on failure
 * - Migration status tracking (pending/completed/failed)
 * - Concurrent migration protection via file locking
 * - Automatic cleanup of old backups (> 30 days)
 *
 * @param context Application context for file operations
 */
class MigrationManager(private val context: Context) {

    private val logTag = "MigrationManager"
    private val migrationLock = ReentrantLock()

    // Directory structure
    private val internalDir: File get() = File(context.filesDir, "grammarmate")
    private val backupsDir: File get() = File(internalDir, "migration_backups")
    private val lockFile: File get() = File(internalDir, ".migration_lock")

    // Date formatter for backup directory names
    private val backupDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Represents the status of a migration operation.
     */
    sealed class MigrationStatus {
        object Pending : MigrationStatus()
        object Completed : MigrationStatus()
        data class Failed(val error: String) : MigrationStatus()
    }

    /**
     * Configuration for migration operations.
     */
    data class MigrationConfig(
        val migrationId: String,
        val version: Int,
        val description: String,
        val affectedFiles: List<File>,
        val backupBeforeMigration: Boolean = true
    )

    /**
     * Result of a migration operation.
     */
    data class MigrationResult(
        val success: Boolean,
        val backupCreated: Boolean,
        val backupPath: String? = null,
        val error: String? = null,
        val rolledBack: Boolean = false
    )

    // region -- Public Migration API --

    /**
     * Executes a migration with automatic backup and rollback on failure.
     *
     * @param config Migration configuration
     * @param migrationBlock The migration logic to execute
     * @return MigrationResult with outcome details
     */
    fun executeMigration(
        config: MigrationConfig,
        migrationBlock: () -> Boolean
    ): MigrationResult {
        // Acquire lock to prevent concurrent migrations
        if (!acquireMigrationLock()) {
            Log.e(logTag, "Failed to acquire migration lock - another migration may be in progress")
            return MigrationResult(
                success = false,
                backupCreated = false,
                error = "Could not acquire migration lock - concurrent migration detected"
            )
        }

        try {
            return executeMigrationInternal(config, migrationBlock)
        } finally {
            releaseMigrationLock()
        }
    }

    /**
     * Creates a manual backup for the specified files.
     *
     * @param backupId Unique identifier for this backup
     * @param files List of files to backup
     * @return Path to the created backup directory, or null if failed
     */
    fun createBackup(backupId: String, files: List<File>): String? {
        return try {
            // Ensure backups directory exists
            if (!backupsDir.exists() && !backupsDir.mkdirs()) {
                Log.e(logTag, "Failed to create backups directory: ${backupsDir.absolutePath}")
                return null
            }

            val timestamp = backupDateFormat.format(Date())
            val backupDir = File(backupsDir, "migration-${backupId}-${timestamp}")

            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(logTag, "Failed to create backup directory: ${backupDir.absolutePath}")
                return null
            }

            val backup = MigrationBackup.create(backupDir, files)
            if (!backup.saveManifest()) {
                Log.e(logTag, "Failed to save backup manifest")
                backupDir.deleteRecursively()
                return null
            }

            Log.i(logTag, "Created backup at: ${backupDir.absolutePath}")
            backupDir.absolutePath
        } catch (e: Exception) {
            Log.e(logTag, "Failed to create backup", e)
            null
        }
    }

    /**
     * Rolls back a migration by restoring from the specified backup.
     *
     * @param backupPath Path to the backup directory
     * @return true if rollback succeeded, false otherwise
     */
    fun rollbackFromBackup(backupPath: String): Boolean {
        return try {
            val backupDir = File(backupPath)
            if (!backupDir.exists()) {
                Log.e(logTag, "Backup directory not found: $backupPath")
                return false
            }

            val backup = MigrationBackup.load(backupDir)
            if (backup == null) {
                Log.e(logTag, "Failed to load backup manifest from: $backupPath")
                return false
            }

            if (!backup.verifyIntegrity()) {
                Log.e(logTag, "Backup integrity check failed for: $backupPath")
                return false
            }

            backup.restore(internalDir)
            Log.i(logTag, "Successfully rolled back from backup: $backupPath")
            true
        } catch (e: Exception) {
            Log.e(logTag, "Failed to rollback from backup", e)
            false
        }
    }

    /**
     * Cleans up old backup directories older than the specified number of days.
     *
     * @param daysToKeep Number of days to keep backups (default: 30)
     * @return Number of backups cleaned up
     */
    fun cleanupOldBackups(daysToKeep: Int = 30): Int {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        var cleanedCount = 0

        try {
            if (!backupsDir.exists()) return 0

            backupsDir.listFiles()?.forEach { backupDir ->
                if (backupDir.isDirectory && backupDir.name.startsWith("migration-")) {
                    if (backupDir.lastModified() < cutoffTime) {
                        if (backupDir.deleteRecursively()) {
                            cleanedCount++
                            Log.d(logTag, "Cleaned up old backup: ${backupDir.name}")
                        } else {
                            Log.w(logTag, "Failed to delete old backup: ${backupDir.name}")
                        }
                    }
                }
            }

            Log.i(logTag, "Cleaned up $cleanedCount old backups (older than $daysToKeep days)")
            return cleanedCount
        } catch (e: Exception) {
            Log.e(logTag, "Failed to cleanup old backups", e)
            return cleanedCount
        }
    }

    /**
     * Gets the list of available migration backups.
     *
     * @return List of backup directories sorted by modification time (newest first)
     */
    fun getAvailableBackups(): List<File> {
        return try {
            if (!backupsDir.exists()) return emptyList()

            backupsDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("migration-")
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(logTag, "Failed to get available backups", e)
            emptyList()
        }
    }

    // endregion

    // region -- Internal Migration Logic --

    private fun executeMigrationInternal(
        config: MigrationConfig,
        migrationBlock: () -> Boolean
    ): MigrationResult {
        var backupPath: String? = null
        var backupCreated = false

        // Step 1: Create backup if configured
        if (config.backupBeforeMigration) {
            backupPath = createBackup(config.migrationId, config.affectedFiles)
            if (backupPath == null) {
                return MigrationResult(
                    success = false,
                    backupCreated = false,
                    error = "Failed to create backup before migration"
                )
            }
            backupCreated = true
            Log.i(logTag, "Created backup at: $backupPath")
        }

        // Step 2: Execute migration
        return try {
            val success = migrationBlock()

            if (success) {
                Log.i(logTag, "Migration ${config.migrationId} completed successfully")
                MigrationResult(
                    success = true,
                    backupCreated = backupCreated,
                    backupPath = backupPath
                )
            } else {
                Log.w(logTag, "Migration ${config.migrationId} returned false - attempting rollback")

                // Step 3: Rollback on failure
                val rolledBack = if (backupPath != null) {
                    rollbackFromBackup(backupPath)
                } else {
                    false
                }

                MigrationResult(
                    success = false,
                    backupCreated = backupCreated,
                    backupPath = backupPath,
                    error = "Migration block returned false",
                    rolledBack = rolledBack
                )
            }
        } catch (e: Exception) {
            Log.e(logTag, "Migration ${config.migrationId} failed with exception - attempting rollback", e)

            // Step 3: Rollback on exception
            val rolledBack = if (backupPath != null) {
                rollbackFromBackup(backupPath)
            } else {
                false
            }

            MigrationResult(
                success = false,
                backupCreated = backupCreated,
                backupPath = backupPath,
                error = "Migration failed with exception: ${e.message}",
                rolledBack = rolledBack
            )
        }
    }

    // endregion

    // region -- Concurrent Migration Protection --

    private fun acquireMigrationLock(): Boolean {
        return migrationLock.tryLock() && tryCreateLockFile()
    }

    private fun releaseMigrationLock() {
        try {
            migrationLock.unlock()
            lockFile.delete()
        } catch (e: Exception) {
            Log.e(logTag, "Failed to release migration lock", e)
        }
    }

    private fun tryCreateLockFile(): Boolean {
        return try {
            // Ensure parent directory exists
            lockFile.parentFile?.mkdirs()

            if (lockFile.exists()) {
                // Check if the lock is stale (older than 1 hour)
                val lockAge = System.currentTimeMillis() - lockFile.lastModified()
                if (lockAge > 60 * 60 * 1000) {
                    Log.w(logTag, "Found stale migration lock file - removing it")
                    lockFile.delete()
                } else {
                    Log.w(logTag, "Migration lock file exists - another migration may be in progress")
                    return false
                }
            }

            lockFile.createNewFile()
            true
        } catch (e: Exception) {
            Log.e(logTag, "Failed to create migration lock file", e)
            false
        }
    }

    // endregion
}

/**
 * Represents a migration backup with manifest and integrity verification.
 */
class MigrationBackup private constructor(
    private val backupDir: File,
    internal val entries: List<BackupEntry>
) {

    private val logTag = "MigrationBackup"
    private val manifestFile: File get() = File(backupDir, "MANIFEST.txt")

    data class BackupEntry(
        val originalPath: String,
        val backupPath: String,
        val checksum: String,
        val fileSize: Long,
        val lastModified: Long
    )

    companion object {
        fun create(backupDir: File, files: List<File>): MigrationBackup {
            val entries = files.mapNotNull { file ->
                if (!file.exists()) return@mapNotNull null

                val backupFile = File(backupDir, file.name)
                try {
                    // Copy file to backup directory
                    AtomicFileWriter.copyAtomic(file, backupFile)

                    BackupEntry(
                        originalPath = file.absolutePath,
                        backupPath = backupFile.absolutePath,
                        checksum = calculateChecksum(backupFile),
                        fileSize = backupFile.length(),
                        lastModified = backupFile.lastModified()
                    )
                } catch (e: Exception) {
                    Log.e("MigrationBackup", "Failed to backup file: ${file.absolutePath}", e)
                    null
                }
            }

            return MigrationBackup(backupDir, entries)
        }

        fun load(backupDir: File): MigrationBackup? {
            val manifestFile = File(backupDir, "MANIFEST.txt")
            if (!manifestFile.exists()) return null

            return try {
                val lines = manifestFile.readLines()
                val entries = lines
                    .filter { line -> line.isNotBlank() && !line.trimStart().startsWith("#") }
                    .mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size >= 5) {
                            BackupEntry(
                                originalPath = parts[0].trim(),
                                backupPath = parts[1].trim(),
                                checksum = parts[2].trim(),
                                fileSize = parts[3].trim().toLongOrNull() ?: 0L,
                                lastModified = parts[4].trim().toLongOrNull() ?: 0L
                            )
                        } else null
                    }

                MigrationBackup(backupDir, entries)
            } catch (e: Exception) {
                Log.e("MigrationBackup", "Failed to load backup manifest", e)
                null
            }
        }

        private fun calculateChecksum(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } > 0) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Saves the backup manifest to disk.
     */
    fun saveManifest(): Boolean {
        return try {
            val content = buildString {
                appendLine("# Migration Backup Manifest")
                appendLine("# Created: ${Date()}")
                appendLine("# Format: originalPath|backupPath|checksum|fileSize|lastModified")
                appendLine()

                entries.forEach { entry ->
                    appendLine("${entry.originalPath}|${entry.backupPath}|${entry.checksum}|${entry.fileSize}|${entry.lastModified}")
                }
            }

            AtomicFileWriter.writeText(manifestFile, content)
            true
        } catch (e: Exception) {
            Log.e(logTag, "Failed to save backup manifest", e)
            false
        }
    }

    /**
     * Verifies the integrity of all backup entries.
     */
    fun verifyIntegrity(): Boolean {
        return entries.all { entry ->
            try {
                val backupFile = File(entry.backupPath)
                if (!backupFile.exists()) {
                    Log.e(logTag, "Backup file missing: ${entry.backupPath}")
                    return@all false
                }

                val currentChecksum = calculateChecksum(backupFile)
                if (currentChecksum != entry.checksum) {
                    Log.e(logTag, "Checksum mismatch for: ${entry.backupPath}")
                    return@all false
                }

                true
            } catch (e: Exception) {
                Log.e(logTag, "Failed to verify entry: ${entry.backupPath}", e)
                false
            }
        }
    }

    /**
     * Restores all files from this backup to the target directory.
     */
    fun restore(targetDir: File): Boolean {
        return try {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.e(logTag, "Failed to create target directory: ${targetDir.absolutePath}")
                return false
            }

            entries.forEach { entry ->
                val backupFile = File(entry.backupPath)
                val originalFile = File(entry.originalPath)

                if (backupFile.exists()) {
                    // Ensure parent directory exists
                    originalFile.parentFile?.mkdirs()

                    // Copy file back to original location
                    AtomicFileWriter.copyAtomic(backupFile, originalFile)
                    Log.d(logTag, "Restored: ${originalFile.absolutePath}")
                } else {
                    Log.w(logTag, "Backup file not found: ${entry.backupPath}")
                }
            }

            true
        } catch (e: Exception) {
            Log.e(logTag, "Failed to restore backup", e)
            false
        }
    }
}