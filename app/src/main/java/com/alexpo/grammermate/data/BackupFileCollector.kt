package com.alexpo.grammermate.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Locates internal backups and builds their human-readable metadata.
 *
 * This class is internal to the backup subsystem -- callers should use [BackupManager]
 * as the primary public API.
 */
internal class BackupFileCollector(private val context: Context) {

    val backupDir: File? by lazy {
        File(context.filesDir, "grammarmate/backups").apply {
            if (!exists()) mkdirs()
        }
    }

    // region -- Backup metadata --

    fun buildMetadataContent(timestamp: String): String = """
        Backup created: $timestamp
        App version: 1.0
        Data format: YAML
        Restore contract: replace all managed progress files with this snapshot
        Contents:
        - mastery.yaml (flower levels and progress)
        - progress.yaml (training session progress)
        - streak_*.yaml (daily streak data per language)
        - profile.yaml (user name and settings)
        - hidden_cards.yaml (hidden card IDs)
        - bad_sentences.yaml (reported bad sentences)
        - vocab_progress.yaml (vocab sprint progress)
        - word_mastery.yaml (legacy word mastery)
        - drill_progress_*.yaml (per-language drill progress)
        - verb_drill_progress_{languageId}.yaml (legacy per-language verb drill progress)
        - verb_drill_last_session_{languageId}.yaml (legacy verb drill last session state per language)
        - aux_drill_progress_{languageId}.yaml (legacy aux drill progress per language)
        - lesson_progress_*.yaml (per-pack lesson progress)
        - daily_cursor_*.yaml (per-pack daily cursor)
        - drills/{packId}/word_mastery.yaml (word mastery per pack)
        - drills/{packId}/verb_drill_progress.yaml (verb drill progress per pack)
        - drills/{packId}/verb_drill_last_session.yaml (verb drill last session per pack)
        - drills/{packId}/aux_drill_progress.yaml (aux drill progress per pack)
        - drills/{packId}/vocab_progress.yaml (vocab progress per pack)
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
