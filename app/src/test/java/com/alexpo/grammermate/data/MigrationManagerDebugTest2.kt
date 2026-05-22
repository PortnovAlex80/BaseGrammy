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
class MigrationManagerDebugTest2 {
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
        testDir = File(context.filesDir, "test_migration_debug2")
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
    fun debugManifestLoading() {
        val backupPath = migrationManager.createBackup("debug-manifest", listOf(testFile))
        assertNotNull("Backup path should not be null", backupPath)

        val backupDir = File(backupPath!!)
        assertTrue("Backup directory should exist", backupDir.exists())

        val manifestFile = File(backupDir, "MANIFEST.txt")
        assertTrue("Manifest file should exist", manifestFile.exists())

        println("Manifest file content:")
        println(manifestFile.readText())

        // Try to load the backup
        val backup = MigrationBackup.load(backupDir)
        assertNotNull("Backup should load successfully", backup)
        
        if (backup != null) {
            println("Backup loaded successfully with ${backup.entries.size} entries")
            backup.entries.forEach { entry ->
                println("Entry: ${entry.originalPath} -> ${entry.backupPath}")
            }
            
            // Check integrity
            val integrity = backup.verifyIntegrity()
            println("Integrity check: $integrity")
            assertTrue("Backup integrity should pass", integrity)
            
            // Try restore
            testFile.writeText("modified content")
            println("Content before restore: ${testFile.readText()}")
            
            val restoreResult = backup.restore(testDataDir)
            println("Restore result: $restoreResult")
            assertTrue("Restore should succeed", restoreResult)
            
            println("Content after restore: ${testFile.readText()}")
            assertEquals("Content should be restored", "original content", testFile.readText())
        }
    }
}
