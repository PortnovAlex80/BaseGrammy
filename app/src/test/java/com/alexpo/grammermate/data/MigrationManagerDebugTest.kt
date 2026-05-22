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

@RunWith(RobolectricTestRunner::class)
class MigrationManagerDebugTest {
    private lateinit var context: Context
    private lateinit var migrationManager: MigrationManager
    private lateinit var testDir: File
    private lateinit var testDataDir: File
    private lateinit var testFile: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        migrationManager = MigrationManager(context)

        // Create test directory structure
        testDir = File(context.filesDir, "test_migration_debug")
        testDataDir = File(testDir, "grammarmate")
        testDataDir.mkdirs()

        // Create test file
        testFile = File(testDataDir, "test.yaml")
        testFile.writeText("original content")
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun debugBackupCreation() {
        println("Test file path: ${testFile.absolutePath}")
        println("Test file exists: ${testFile.exists()}")
        println("Test file content: ${testFile.readText()}")

        val backupPath = migrationManager.createBackup("debug-test", listOf(testFile))
        println("Backup path: $backupPath")
        assertNotNull("Backup path should not be null", backupPath)

        val backupDir = File(backupPath!!)
        println("Backup dir exists: ${backupDir.exists()}")
        assertTrue("Backup directory should exist", backupDir.exists())

        val manifestFile = File(backupDir, "MANIFEST.txt")
        println("Manifest file exists: ${manifestFile.exists()}")
        assertTrue("Manifest file should exist", manifestFile.exists())

        println("Manifest content:")
        println(manifestFile.readText())
    }

    @Test
    fun debugRollback() {
        val backupPath = migrationManager.createBackup("debug-rollback", listOf(testFile))
        assertNotNull("Backup path should not be null", backupPath)

        // Modify original file
        testFile.writeText("modified content")
        println("Modified content: ${testFile.readText()}")

        // Rollback
        val rollbackSuccess = migrationManager.rollbackFromBackup(backupPath!!)
        println("Rollback success: $rollbackSuccess")
        assertTrue("Rollback should succeed", rollbackSuccess)

        // Check restored content
        val restoredContent = testFile.readText()
        println("Restored content: $restoredContent")
        assertEquals("Content should be restored", "original content", restoredContent)
    }
}
