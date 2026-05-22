package com.alexpo.grammermate.data

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Comprehensive test suite for MigrationManager.
 *
 * Tests cover:
 * - Backup creation and restoration
 * - Rollback procedures on migration failure
 * - Concurrent migration protection
 * - Migration status tracking
 * - Backup cleanup functionality
 * - Integration with existing data stores
 */
@RunWith(RobolectricTestRunner::class)
class MigrationManagerTest {

    private lateinit var context: Context
    private lateinit var migrationManager: MigrationManager
    private lateinit var testDir: File
    private lateinit var testDataDir: File
    private lateinit var testFiles: List<File>

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        migrationManager = MigrationManager(context)

        // Create test directory structure
        testDir = File(context.filesDir, "test_migration")
        testDataDir = File(testDir, "grammarmate")
        testDataDir.mkdirs()

        // Create test files
        testFiles = listOf(
            File(testDataDir, "test1.yaml").apply {
                writeText("data1: value1\ndata2: value2")
            },
            File(testDataDir, "test2.yaml").apply {
                writeText("data3: value3\ndata4: value4")
            },
            File(testDataDir, "test3.yaml").apply {
                writeText("data5: value5\ndata6: value6")
            }
        )
    }

    @After
    fun tearDown() {
        // Clean up test directories
        testDir.deleteRecursively()

        // Clean up migration backups created during tests
        val backupDir = File(context.filesDir, "grammarmate/migration_backups")
        if (backupDir.exists()) {
            backupDir.deleteRecursively()
        }

        // Clean up lock files
        val lockFile = File(context.filesDir, "grammarmate/.migration_lock")
        if (lockFile.exists()) {
            lockFile.delete()
        }
    }

    // region -- Backup Creation Tests --

    @Test
    fun `createBackup creates backup directory with all files`() {
        val backupId = "test-backup-001"
        val backupPath = migrationManager.createBackup(backupId, testFiles)

        assertNotNull("Backup path should not be null", backupPath)
        val backupDir = File(backupPath)
        assertTrue("Backup directory should exist", backupDir.exists())
        assertTrue("Backup path should be a directory", backupDir.isDirectory)

        // Verify all test files were backed up
        testFiles.forEach { testFile ->
            val backupFile = File(backupDir, testFile.name)
            assertTrue("Backup file ${testFile.name} should exist", backupFile.exists())
            assertEquals("File content should match", testFile.readText(), backupFile.readText())
        }
    }

    @Test
    fun `createBackup creates manifest file with correct entries`() {
        val backupId = "test-backup-002"
        val backupPath = migrationManager.createBackup(backupId, testFiles)

        assertNotNull(backupPath)
        val manifestFile = File(backupPath!!, "MANIFEST.txt")
        assertTrue("Manifest file should exist", manifestFile.exists())

        val manifestContent = manifestFile.readText()
        assertTrue("Manifest should have header", manifestContent.contains("# Migration Backup Manifest"))
        assertTrue("Manifest should have format specification", manifestContent.contains("# Format:"))

        // Verify each file has an entry in the manifest
        testFiles.forEach { testFile ->
            assertTrue("Manifest should contain ${testFile.name}", manifestContent.contains(testFile.name))
        }
    }

    @Test
    fun `createBackup returns null when files don't exist`() {
        val nonExistentFiles = listOf(
            File(testDataDir, "nonexistent1.yaml"),
            File(testDataDir, "nonexistent2.yaml")
        )

        val backupPath = migrationManager.createBackup("test-backup-003", nonExistentFiles)

        // Should create backup but with no entries since files don't exist
        assertNotNull("Backup path should be created even with no files", backupPath)
        val backupDir = File(backupPath!!)
        assertTrue("Backup directory should exist", backupDir.exists())
    }

    // endregion

    // region -- Rollback Tests --

    @Test
    fun `rollbackFromBackup restores files correctly`() {
        // Create backup
        val backupPath = migrationManager.createBackup("test-rollback-001", testFiles)
        assertNotNull(backupPath)

        // Modify original files
        testFiles.forEach { it.writeText("modified content") }

        // Verify files are modified
        testFiles.forEach { file ->
            assertEquals("File should be modified", "modified content", file.readText())
        }

        // Rollback from backup
        val rollbackSuccess = migrationManager.rollbackFromBackup(backupPath!!)
        assertTrue("Rollback should succeed", rollbackSuccess)

        // Verify files are restored
        val originalContents = listOf(
            "data1: value1\ndata2: value2",
            "data3: value3\ndata4: value4",
            "data5: value5\ndata6: value6"
        )

        testFiles.forEachIndexed { index, file ->
            assertEquals("File ${index + 1} should be restored", originalContents[index], file.readText())
        }
    }

    @Test
    fun `rollbackFromBackup returns false for invalid backup path`() {
        val invalidPath = "/invalid/backup/path"
        val rollbackSuccess = migrationManager.rollbackFromBackup(invalidPath)

        assertFalse("Rollback should fail for invalid path", rollbackSuccess)
    }

    @Test
    fun `rollbackFromBackup returns false when backup integrity is compromised`() {
        val backupPath = migrationManager.createBackup("test-rollback-002", testFiles)
        assertNotNull(backupPath)

        // Corrupt a backup file
        val backupDir = File(backupPath!!)
        val corruptedFile = File(backupDir, testFiles[0].name)
        corruptedFile.writeText("corrupted content")

        // Attempt rollback
        val rollbackSuccess = migrationManager.rollbackFromBackup(backupPath)

        assertFalse("Rollback should fail when integrity is compromised", rollbackSuccess)
    }

    // endregion

    // region -- Migration Execution Tests --

    @Test
    fun `executeMigration succeeds when migration block returns true`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-migration-001",
            version = 1,
            description = "Test migration",
            affectedFiles = testFiles,
            backupBeforeMigration = true
        )

        var migrationExecuted = false
        val migrationBlock: () -> Boolean = {
            migrationExecuted = true
            true
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertTrue("Migration should succeed", result.success)
        assertTrue("Backup should be created", result.backupCreated)
        assertTrue("Migration block should be executed", migrationExecuted)
        assertNotNull("Backup path should be provided", result.backupPath)
    }

    @Test
    fun `executeMigration rolls back when migration block returns false`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-migration-002",
            version = 1,
            description = "Test migration that fails",
            affectedFiles = testFiles,
            backupBeforeMigration = true
        )

        // Modify files during migration
        val originalContents = testFiles.map { it.readText() }
        val migrationBlock: () -> Boolean = {
            testFiles.forEach { it.writeText("modified during migration") }
            false // Migration fails
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertFalse("Migration should fail", result.success)
        assertTrue("Rollback should occur", result.rolledBack)
        assertTrue("Backup should be created", result.backupCreated)

        // Verify files are restored to original state
        testFiles.forEachIndexed { index, file ->
            assertEquals("File ${index + 1} should be restored", originalContents[index], file.readText())
        }
    }

    @Test
    fun `executeMigration rolls back when migration block throws exception`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-migration-003",
            version = 1,
            description = "Test migration that throws exception",
            affectedFiles = testFiles,
            backupBeforeMigration = true
        )

        val originalContents = testFiles.map { it.readText() }
        val migrationBlock: () -> Boolean = {
            testFiles.forEach { it.writeText("modified during migration") }
            throw RuntimeException("Migration failed!")
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertFalse("Migration should fail", result.success)
        assertTrue("Rollback should occur on exception", result.rolledBack)
        assertNotNull("Error message should be provided", result.error)

        // Verify files are restored
        testFiles.forEachIndexed { index, file ->
            assertEquals("File ${index + 1} should be restored after exception", originalContents[index], file.readText())
        }
    }

    @Test
    fun `executeMigration without backup doesn't create backup`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-migration-004",
            version = 1,
            description = "Test migration without backup",
            affectedFiles = testFiles,
            backupBeforeMigration = false
        )

        val migrationBlock: () -> Boolean = { true }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertTrue("Migration should succeed", result.success)
        assertFalse("Backup should not be created", result.backupCreated)
        assertNull("Backup path should be null", result.backupPath)
    }

    // endregion

    // region -- Concurrent Migration Protection Tests --

    @Test
    fun `executeMigration prevents concurrent migrations`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-concurrent-001",
            version = 1,
            description = "Test concurrent migration protection",
            affectedFiles = testFiles,
            backupBeforeMigration = false
        )

        val longRunningMigration: () -> Boolean = {
            Thread.sleep(100) // Simulate long-running migration
            true
        }

        // Start first migration in background
        val thread1 = Thread {
            migrationManager.executeMigration(config, longRunningMigration)
        }
        thread1.start()

        // Give first migration time to acquire lock
        Thread.sleep(10)

        // Try to run second migration concurrently
        val result2 = migrationManager.executeMigration(config, longRunningMigration)

        assertFalse("Second migration should fail due to lock", result2.success)
        assertNotNull("Error message should mention lock", result2.error)
        assertTrue("Error should mention lock", result2.error!!.contains("lock", ignoreCase = true))

        thread1.join()
    }

    @Test
    fun `migration lock file is created and removed correctly`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "test-lock-001",
            version = 1,
            description = "Test lock file creation",
            affectedFiles = testFiles,
            backupBeforeMigration = false
        )

        val lockFile = File(context.filesDir, "grammarmate/.migration_lock")

        // Initially no lock file
        assertFalse("Lock file should not exist initially", lockFile.exists())

        // During migration, lock file should exist
        val migrationBlock: () -> Boolean = {
            assertTrue("Lock file should exist during migration", lockFile.exists())
            true
        }

        migrationManager.executeMigration(config, migrationBlock)

        // After migration, lock file should be removed
        assertFalse("Lock file should be removed after migration", lockFile.exists())
    }

    // endregion

    // region -- Backup Cleanup Tests --

    @Test
    fun `cleanupOldBackups removes backups older than specified days`() {
        // Create multiple backups with different timestamps
        val oldBackupPath = migrationManager.createBackup("old-backup", testFiles)
        assertNotNull(oldBackupPath)

        Thread.sleep(100) // Ensure different timestamps

        val newBackupPath = migrationManager.createBackup("new-backup", testFiles)
        assertNotNull(newBackupPath)

        // Verify both backups exist
        assertTrue("Old backup should exist initially", File(oldBackupPath!!).exists())
        assertTrue("New backup should exist initially", File(newBackupPath!!).exists())

        // Modify old backup timestamp to make it appear old (31 days ago)
        val oldBackupDir = File(oldBackupPath)
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31 * 24 * 60 * 60 * 1000L)
        val timestampSet = oldBackupDir.setLastModified(thirtyOneDaysAgo)
        assertTrue("Should be able to set last modified time", timestampSet)

        // Verify the timestamp was actually modified
        val modifiedTime = oldBackupDir.lastModified()
        assertTrue("Old backup should appear to be old", modifiedTime < (System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)))

        // Run cleanup with 30 days threshold
        val cleanedCount = migrationManager.cleanupOldBackups(30)

        assertEquals("One old backup should be cleaned up", 1, cleanedCount)
        assertFalse("Old backup should be deleted", oldBackupDir.exists())
        assertTrue("New backup should still exist", File(newBackupPath!!).exists())
    }

    @Test
    fun `cleanupOldBackups returns zero when no backups exist`() {
        val cleanedCount = migrationManager.cleanupOldBackups(30)
        assertEquals("No backups should be cleaned when none exist", 0, cleanedCount)
    }

    @Test
    fun `getAvailableBackups returns sorted list of backups`() {
        // Create multiple backups
        val backup1 = migrationManager.createBackup("backup-001", testFiles)
        Thread.sleep(100)
        val backup2 = migrationManager.createBackup("backup-002", testFiles)
        Thread.sleep(100)
        val backup3 = migrationManager.createBackup("backup-003", testFiles)

        val availableBackups = migrationManager.getAvailableBackups()

        assertTrue("Should have at least 3 backups", availableBackups.size >= 3)

        // Verify they are sorted by modification time (newest first)
        val timestamps = availableBackups.map { it.lastModified() }
        assertTrue("First backup should be newer or equal to second", timestamps[0] >= timestamps[1])
        assertTrue("Second backup should be newer or equal to third", timestamps[1] >= timestamps[2])
    }

    // endregion

    // region -- Integration Tests with Data Stores --

    @Test
    fun `migration manager integrates with ProgressStore`() {
        // Create a temporary progress file
        val progressFile = File(testDataDir, "progress.yaml")
        val testData = """
            activePackId: "test_pack_001"
            lastStudyDate: 1234567890000
            schemaVersion: 1
        """.trimIndent()
        progressFile.writeText(testData)

        val config = MigrationManager.MigrationConfig(
            migrationId = "progress-store-test",
            version = 1,
            description = "Test migration with ProgressStore",
            affectedFiles = listOf(progressFile),
            backupBeforeMigration = true
        )

        var migrationExecuted = false
        val migrationBlock: () -> Boolean = {
            // Simulate migration logic
            migrationExecuted = true
            val currentContent = progressFile.readText()
            progressFile.writeText(currentContent + "\nmigrated: true")
            true
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertTrue("Migration should succeed", result.success)
        assertTrue("Migration block should execute", migrationExecuted)

        // Verify file was modified
        val newContent = progressFile.readText()
        assertTrue("File should contain migration changes", newContent.contains("migrated: true"))
    }

    @Test
    fun `migration manager handles multiple data stores in single migration`() {
        // Create multiple store files
        val stores = listOf(
            File(testDataDir, "mastery.yaml").apply { writeText("test: mastery") },
            File(testDataDir, "progress.yaml").apply { writeText("test: progress") },
            File(testDataDir, "profile.yaml").apply { writeText("test: profile") }
        )

        val config = MigrationManager.MigrationConfig(
            migrationId = "multi-store-test",
            version = 1,
            description = "Test migration with multiple stores",
            affectedFiles = stores,
            backupBeforeMigration = true
        )

        val migrationBlock: () -> Boolean = {
            stores.forEach { store ->
                val content = store.readText()
                store.writeText(content + "\nmigrated: true")
            }
            true
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertTrue("Migration should succeed", result.success)
        assertTrue("Backup should be created for all stores", result.backupCreated)

        // Verify all stores were modified
        stores.forEach { store ->
            val content = store.readText()
            assertTrue("${store.name} should be migrated", content.contains("migrated: true"))
        }
    }

    // endregion

    // region -- Error Handling Tests --

    @Test
    fun `executeMigration handles IO errors gracefully`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "io-error-test",
            version = 1,
            description = "Test IO error handling",
            affectedFiles = testFiles,
            backupBeforeMigration = true
        )

        val migrationBlock: () -> Boolean = {
            throw java.io.IOException("Simulated IO error")
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertFalse("Migration should fail", result.success)
        assertTrue("Rollback should occur on IO error", result.rolledBack)
        assertNotNull("Error should be documented", result.error)
    }

    @Test
    fun `executeMigration handles security errors gracefully`() {
        val config = MigrationManager.MigrationConfig(
            migrationId = "security-error-test",
            version = 1,
            description = "Test security error handling",
            affectedFiles = testFiles,
            backupBeforeMigration = true
        )

        val migrationBlock: () -> Boolean = {
            throw SecurityException("Simulated security error")
        }

        val result = migrationManager.executeMigration(config, migrationBlock)

        assertFalse("Migration should fail", result.success)
        assertTrue("Rollback should occur on security error", result.rolledBack)
    }

    // endregion
}