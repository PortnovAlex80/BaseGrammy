package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var backupManager: BackupManager
    private lateinit var internalDir: File
    private lateinit var backupRoot: File

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        backupManager = BackupManager(context)
        internalDir = File(context.filesDir, "grammarmate")
        backupRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BaseGrammy"
        )
    }

    @After
    fun cleanup() {
        internalDir.deleteRecursively()
        backupRoot.deleteRecursively()
    }

    @Test
    fun restoreFromBackup_createsInternalDirAndCopiesFiles() {
        val backupDir = File(backupRoot, "backup_test").apply { mkdirs() }
        File(backupDir, "mastery.yaml").writeText("mastery: 1")
        File(backupDir, "progress.yaml").writeText("progress: 2")
        File(backupDir, "streak.yaml").writeText("streak: 3")
        File(backupDir, "profile.yaml").writeText("profile: 4")

        val restored = backupManager.restoreFromBackup(backupDir.absolutePath)

        assertTrue(restored)
        assertTrue(internalDir.exists())
        assertEquals("mastery: 1", File(internalDir, "mastery.yaml").readText())
        assertEquals("progress: 2", File(internalDir, "progress.yaml").readText())
        assertEquals("streak: 3", File(internalDir, "streak_en.yaml").readText())
        assertEquals("profile: 4", File(internalDir, "profile.yaml").readText())
    }

    @Test
    fun createBackup_writesToBackupLatestWithProgressAndProfile() {
        internalDir.mkdirs()
        File(internalDir, "mastery.yaml").writeText("mastery: 1")
        File(internalDir, "progress.yaml").writeText("progress: 2")
        File(internalDir, "streak_en.yaml").writeText("streak: 3")
        File(internalDir, "profile.yaml").writeText("profile: 4")

        val created = backupManager.createBackup()

        assertTrue(created)
        val latest = File(backupRoot, "backup_latest")
        assertTrue(latest.exists())
        assertEquals("mastery: 1", File(latest, "mastery.yaml").readText())
        assertEquals("progress: 2", File(latest, "progress.yaml").readText())
        assertEquals("streak: 3", File(latest, "streak_en.yaml").readText())
        assertEquals("profile: 4", File(latest, "profile.yaml").readText())
    }

    @Test
    fun getAvailableBackups_includesBackupLatest() {
        val latest = File(backupRoot, "backup_latest").apply { mkdirs() }
        File(latest, "metadata.txt").writeText("latest")

        val backupDir = File(backupRoot, "backup_2026-01-16_10-00-00").apply { mkdirs() }
        File(backupDir, "metadata.txt").writeText("older")

        val backups = backupManager.getAvailableBackups().map { it.name }

        assertTrue(backups.contains("backup_latest"))
        assertTrue(backups.contains("backup_2026-01-16_10-00-00"))
    }
}
