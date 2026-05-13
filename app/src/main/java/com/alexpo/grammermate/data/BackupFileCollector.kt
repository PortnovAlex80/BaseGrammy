package com.alexpo.grammermate.data

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Collects files to include in a backup and enumerates available backups.
 *
 * This class is internal to the backup subsystem -- callers should use [BackupManager]
 * as the primary public API.
 */
internal class BackupFileCollector(private val context: Context) {

    private val logTag = "BackupFileCollector"

    // Internal data directory
    private val internalDir = File(context.filesDir, "grammarmate")

    // Backup directory: Downloads/BaseGrammy
    val backupDir: File? by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadsDir, "BaseGrammy").apply {
            if (!exists()) mkdirs()
        }
    }

    // region -- File lists for backup --

    /** Well-known top-level YAML files that should be backed up when present. */
    val mainBackupFileNames: List<String> = listOf(
        "mastery.yaml",
        "progress.yaml",
        "profile.yaml",
        "hidden_cards.yaml",
        "bad_sentences.yaml",
        "vocab_progress.yaml"
    )

    /** List streak files (streak_*.yaml) from internal storage. */
    fun listStreakFiles(): List<File> =
        internalDir.listFiles { file ->
            file.name.startsWith("streak_") && file.name.endsWith(".yaml")
        }?.toList() ?: emptyList()

    /** List drill-progress files (drill_progress_*.yaml) from internal storage. */
    fun listDrillProgressFiles(): List<File> =
        internalDir.listFiles { file ->
            file.name.startsWith("drill_progress_") && file.name.endsWith(".yaml")
        }?.toList() ?: emptyList()

    /** List pack directories under internal drills/. */
    fun listPackDrillDirs(): List<File> {
        val drillsDir = File(internalDir, "drills")
        if (!drillsDir.exists()) return emptyList()
        return drillsDir.listFiles(java.io.FileFilter { it.isDirectory })?.toList() ?: emptyList()
    }

    // endregion

    // region -- Backup metadata --

    fun buildMetadataContent(timestamp: String): String = """
        Backup created: $timestamp
        App version: 1.0
        Data format: YAML
        Contents:
        - mastery.yaml (flower levels and progress)
        - progress.yaml (training session progress)
        - streak.yaml (daily streak data)
        - profile.yaml (user name and settings)
        - hidden_cards.yaml (hidden card IDs)
        - bad_sentences.yaml (reported bad sentences)
        - vocab_progress.yaml (vocab sprint progress)
        - drill_progress_*.yaml (per-language drill progress)
        - drills/{packId}/verb_drill_progress.yaml (verb drill progress per pack)
        - drills/{packId}/word_mastery.yaml (word mastery per pack)
    """.trimIndent()

    fun currentTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    // endregion

    // region -- Available backups enumeration --

    /**
     * List available backups in the legacy file-system backup directory.
     * Returns entries sorted newest-first.
     */
    fun getAvailableBackupsLegacy(): List<BackupInfo> {
        val dir = backupDir ?: return emptyList()
        return dir.listFiles { file ->
            file.isDirectory && (file.name.startsWith("backup_") || file.name == "backup_latest")
        }?.sortedByDescending { it.lastModified() }?.map { backup ->
            val timestamp = if (backup.name == "backup_latest") "latest"
                            else backup.name.removePrefix("backup_")
            BackupInfo(
                name = backup.name,
                path = backup.absolutePath,
                timestamp = timestamp,
                dataSize = safeCalculateDirSize(backup),
                metadata = safeReadText(File(backup, "metadata.txt"))
            )
        } ?: emptyList()
    }

    // endregion

    // region -- Size / read helpers --

    fun calculateDirSize(dir: File): Long =
        dir.walkTopDown().fold(0L) { acc, file ->
            acc + (if (file.isFile) file.length() else 0L)
        }

    fun safeCalculateDirSize(dir: File): Long = try {
        calculateDirSize(dir)
    } catch (_: Exception) { 0L }

    fun safeReadText(file: File): String = try {
        if (file.exists()) file.readText() else ""
    } catch (_: Exception) { "" }

    // endregion
}
