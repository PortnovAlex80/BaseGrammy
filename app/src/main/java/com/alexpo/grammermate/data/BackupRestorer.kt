package com.alexpo.grammermate.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Restores progress data from a backup into the app's internal storage.
 *
 * All file writes use [AtomicFileWriter] (temp -> fsync -> rename) to prevent
 * data corruption on crash or power loss.
 *
 * This class is internal to the backup subsystem -- callers should use [BackupManager]
 * as the primary public API.
 */
internal class BackupRestorer(private val context: Context) {

    private val yaml = org.yaml.snakeyaml.Yaml()
    private val logTag = "BackupRestorer"
    private val internalDir = File(context.filesDir, "grammarmate")

    private fun validateBackupContent(content: String, fileName: String): Boolean {
        if (content.isBlank()) return false
        if (fileName.endsWith(".csv", ignoreCase = true)) return content.isNotBlank()
        return try {
            val data = yaml.load<Any>(content)
            when (data) {
                is Map<*, *> -> {
                    val version = data["schemaVersion"]
                    if (version != null && version !is Number) return false
                    true
                }
                is List<*> -> true
                else -> true
            }
        } catch (e: Exception) {
            Log.w(logTag, "Backup validation failed for $fileName", e)
            false
        }
    }

    // region -- Legacy file-path restore --

    /**
     * Restore all backed-up files from [backupPath] to internal storage.
     * Uses file-copy for each known data file.
     */
    fun restoreFromPath(backupPath: String): Boolean {
        val backupSubDir = File(backupPath)
        if (!backupSubDir.exists()) return false
        ensureInternalDir()

        listOf(
            "mastery.yaml", "progress.yaml", "profile.yaml",
            "hidden_cards.yaml", "bad_sentences.yaml", "vocab_progress.yaml"
        ).forEach { name ->
            copyIfExists(backupSubDir, internalDir, name)
            val dest = File(internalDir, name)
            if (dest.exists()) {
                val restored = dest.readText(Charsets.UTF_8)
                if (!validateBackupContent(restored, dest.name)) {
                    dest.delete()
                    Log.w(logTag, "Deleted invalid restored file: ${dest.name}")
                }
            }
        }

        // Streak files
        backupSubDir.listFiles { file ->
            file.name.startsWith("streak_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            AtomicFileWriter.copyAtomic(file, destFile)
            val restored = destFile.readText(Charsets.UTF_8)
            if (!validateBackupContent(restored, destFile.name)) {
                destFile.delete()
                Log.w(logTag, "Deleted invalid restored file: ${destFile.name}")
            }
        }

        // Migrate old single streak.yaml to streak_<languageId>.yaml
        migrateOldStreakFormat(backupSubDir)

        // Drill progress files (flat)
        backupSubDir.listFiles { file ->
            file.name.startsWith("drill_progress_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            AtomicFileWriter.copyAtomic(file, destFile)
            val restored = destFile.readText(Charsets.UTF_8)
            if (!validateBackupContent(restored, destFile.name)) {
                destFile.delete()
                Log.w(logTag, "Deleted invalid restored file: ${destFile.name}")
            }
        }

        // Language-specific verb drill files (new format)
        backupSubDir.listFiles { file ->
            file.name.startsWith("verb_drill_progress_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            AtomicFileWriter.copyAtomic(file, destFile)
            val restored = destFile.readText(Charsets.UTF_8)
            if (!validateBackupContent(restored, destFile.name)) {
                destFile.delete()
                Log.w(logTag, "Deleted invalid restored file: ${destFile.name}")
            }
        }

        backupSubDir.listFiles { file ->
            file.name.startsWith("verb_drill_last_session_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            AtomicFileWriter.copyAtomic(file, destFile)
            val restored = destFile.readText(Charsets.UTF_8)
            if (!validateBackupContent(restored, destFile.name)) {
                destFile.delete()
                Log.w(logTag, "Deleted invalid restored file: ${destFile.name}")
            }
        }

        // Migrate old flat verb drill files to language-specific
        migrateOldVerbDrillFormat(backupSubDir)

        // Pack-scoped drill data (drills/{packId}/) - word mastery only
        restorePackDrillDirs(backupSubDir)

        return true
    }

    // endregion

    // region -- SAF (Storage Access Framework) restore --

    /**
     * Restore from a content-URI obtained via the system file picker.
     * Writes detailed log to restore_log.txt alongside the backup.
     */
    fun restoreFromUri(backupUri: Uri): Boolean {
        val logBuilder = StringBuilder()
        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", java.util.Locale.US
        ).format(java.util.Date())

        logBuilder.appendLine("=== Backup Restore Log ===")
        logBuilder.appendLine("Timestamp: $timestamp")
        logBuilder.appendLine("Backup URI: $backupUri")
        logBuilder.appendLine()

        val rootDir = DocumentFile.fromTreeUri(context, backupUri)
            ?: DocumentFile.fromSingleUri(context, backupUri)
            ?: run {
                logBuilder.appendLine("ERROR: Failed to open backup directory from URI")
                writeRestoreLog(backupUri, logBuilder.toString())
                return false
            }

        logBuilder.appendLine("Root directory: ${rootDir.uri}")

        // Prefer backup_latest subfolder
        val backupDir = rootDir.findFile("backup_latest") ?: rootDir
        val isSubfolder = backupDir != rootDir

        logBuilder.appendLine("Using backup source: ${if (isSubfolder) "backup_latest subfolder" else "root folder"}")
        logBuilder.appendLine("Backup directory: ${backupDir.uri}")
        logBuilder.appendLine("Internal data directory: ${internalDir.absolutePath}")
        logBuilder.appendLine()

        if (!ensureInternalDir()) {
            logBuilder.appendLine("ERROR: Failed to create internal directory")
            writeRestoreLog(backupUri, logBuilder.toString())
            return false
        }

        var copied = false
        val restoredFiles = mutableListOf<String>()
        val missingFiles = mutableListOf<String>()

        // -- Main files --
        logBuilder.appendLine("--- Main Files ---")
        val mainFiles = listOf(
            "mastery.yaml", "progress.yaml", "profile.yaml",
            "hidden_cards.yaml", "bad_sentences.yaml", "vocab_progress.yaml"
        )
        mainFiles.forEach { name ->
            val source = backupDir.findFile(name)
            if (source == null) {
                logBuilder.appendLine("MISSING: $name")
                missingFiles.add(name)
            } else {
                copied = copyDocumentToInternal(source, File(internalDir, name), logBuilder, name, restoredFiles) || copied
            }
        }
        logBuilder.appendLine()

        // -- Streak files --
        logBuilder.appendLine("--- Streak Files ---")
        var streakCount = 0
        backupDir.listFiles().forEach { file ->
            if (file.name?.startsWith("streak_") == true && file.name?.endsWith(".yaml") == true) {
                copied = copyDocumentToInternal(file, File(internalDir, file.name!!), logBuilder, file.name!!, restoredFiles) || copied
                streakCount++
            }
        }
        if (streakCount == 0) logBuilder.appendLine("No streak_*.yaml files found")
        logBuilder.appendLine()

        // -- Old streak migration --
        logBuilder.appendLine("--- Old Format Migration ---")
        val oldStreakFile = backupDir.findFile("streak.yaml")
        if (oldStreakFile != null) {
            try {
                val content = context.contentResolver
                    .openInputStream(oldStreakFile.uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    if (!validateBackupContent(content, "streak.yaml")) {
                        logBuilder.appendLine("SKIPPED: streak.yaml - failed validation")
                    } else {
                        val data = yaml.load<Any>(content) as? Map<*, *>
                        val languageId = data?.get("languageId") as? String ?: "en"
                        val target = File(internalDir, "streak_$languageId.yaml")
                        AtomicFileWriter.writeText(target, content)
                        logBuilder.appendLine("OK: Migrated streak.yaml -> streak_$languageId.yaml")
                        restoredFiles.add("streak_$languageId.yaml (migrated)")
                        copied = true
                    }
                }
            } catch (e: Exception) {
                logBuilder.appendLine("ERROR: Failed to migrate streak.yaml - ${e.message}")
            }
        } else {
            logBuilder.appendLine("No old streak.yaml found (this is normal for new backups)")
        }
        logBuilder.appendLine()

        // -- Drill progress files --
        logBuilder.appendLine("--- Drill Progress Files ---")
        var drillProgressCount = 0
        backupDir.listFiles().forEach { file ->
            if (file.name?.startsWith("drill_progress_") == true && file.name?.endsWith(".yaml") == true) {
                copied = copyDocumentToInternal(file, File(internalDir, file.name!!), logBuilder, file.name!!, restoredFiles) || copied
                drillProgressCount++
            }
        }

        // -- Language-specific verb drill files --
        logBuilder.appendLine("--- Language-Specific Verb Drill Files ---")
        var verbDrillProgressCount = 0
        backupDir.listFiles().forEach { file ->
            if (file.name?.startsWith("verb_drill_progress_") == true && file.name?.endsWith(".yaml") == true) {
                copied = copyDocumentToInternal(file, File(internalDir, file.name!!), logBuilder, file.name!!, restoredFiles) || copied
                verbDrillProgressCount++
            }
        }
        if (verbDrillProgressCount == 0) logBuilder.appendLine("No verb_drill_progress_*.yaml files found")

        var verbDrillSessionCount = 0
        backupDir.listFiles().forEach { file ->
            if (file.name?.startsWith("verb_drill_last_session_") == true && file.name?.endsWith(".yaml") == true) {
                copied = copyDocumentToInternal(file, File(internalDir, file.name!!), logBuilder, file.name!!, restoredFiles) || copied
                verbDrillSessionCount++
            }
        }
        if (verbDrillSessionCount == 0) logBuilder.appendLine("No verb_drill_last_session_*.yaml files found")
        logBuilder.appendLine()

        // -- Old verb drill migration --
        logBuilder.appendLine("--- Old Verb Drill Format Migration ---")
        migrateOldVerbDrillFormatUri(backupDir, logBuilder, restoredFiles)
        copied = restoredFiles.any { it.contains("(migrated)") } || copied
        logBuilder.appendLine()

        // -- Flat-name pack-scoped drill files (from scoped backup) --
        backupDir.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            if (name.startsWith("drills_") && name.endsWith(".yaml")) {
                copied = restoreFlatPackDrillFile(file, name, logBuilder, restoredFiles) || copied
            }
        }
        if (drillProgressCount == 0) logBuilder.appendLine("No drill_progress_*.yaml files found")
        logBuilder.appendLine()

        // -- Mastery data analysis (informational) --
        appendMasteryAnalysis(logBuilder)

        // -- Summary --
        logBuilder.appendLine("=== Summary ===")
        logBuilder.appendLine("Restored files: ${restoredFiles.size}")
        restoredFiles.forEach { logBuilder.appendLine("  + $it") }
        if (missingFiles.isNotEmpty()) {
            logBuilder.appendLine("Missing files: ${missingFiles.size}")
            missingFiles.forEach { logBuilder.appendLine("  - $it") }
        }
        logBuilder.appendLine()
        logBuilder.appendLine("Result: ${if (copied) "SUCCESS" else "FAILED - no files copied"}")

        writeRestoreLog(backupUri, logBuilder.toString())
        return copied
    }

    // endregion

    // region -- Helpers --

    private fun ensureInternalDir(): Boolean {
        if (!internalDir.exists() && !internalDir.mkdirs()) return false
        return true
    }

    private fun copyIfExists(srcDir: File, dstDir: File, fileName: String) {
        val src = File(srcDir, fileName)
        if (src.exists()) {
            AtomicFileWriter.copyAtomic(src, File(dstDir, fileName))
        }
    }

    /**
     * Copy a [DocumentFile] to an internal [target] using [AtomicFileWriter].
     * Returns true on success.
     */
    private fun copyDocumentToInternal(
        source: DocumentFile,
        target: File,
        log: StringBuilder,
        label: String,
        restoredFiles: MutableList<String>
    ): Boolean {
        return try {
            val content = context.contentResolver
                .openInputStream(source.uri)?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                if (!validateBackupContent(content, label)) {
                    log.appendLine("SKIPPED: $label - failed validation")
                    return false
                }
                AtomicFileWriter.writeText(target, content)
                log.appendLine("OK: $label (${content.length} chars)")
                restoredFiles.add(label)
                return true
            }
            log.appendLine("ERROR: $label - empty stream")
            false
        } catch (e: Exception) {
            log.appendLine("ERROR: $label - ${e.message}")
            false
        }
    }

    /**
     * Restore a flat-name drill file from scoped backup:
     * e.g. `drills_{packId}_verb_drill_progress.yaml` -> `verb_drill_progress.yaml` (flat path)
     * e.g. `drills_{packId}_word_mastery.yaml` -> `drills/{packId}/word_mastery.yaml` (pack-scoped)
     */
    private fun restoreFlatPackDrillFile(
        file: DocumentFile,
        name: String,
        log: StringBuilder,
        restoredFiles: MutableList<String>
    ): Boolean {
        val remainder = name.removePrefix("drills_")
        val verbMatch = Regex("^(.+)_verb_drill_progress\\.yaml$").matchEntire(remainder)
        val masteryMatch = Regex("^(.+)_word_mastery\\.yaml$").matchEntire(remainder)

        return try {
            val content = context.contentResolver
                .openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            if (content == null) {
                return false
            }
            if (!validateBackupContent(content, name)) {
                log.appendLine("SKIPPED: $name - failed validation")
                return false
            }

            when {
                verbMatch != null -> {
                    // Verb drill progress goes to flat path (global)
                    val target = File(internalDir, "verb_drill_progress.yaml")
                    AtomicFileWriter.writeText(target, content)
                    log.appendLine("OK: $name -> verb_drill_progress.yaml (${content.length} chars)")
                    restoredFiles.add(name)
                    true
                }
                masteryMatch != null -> {
                    // Word mastery stays pack-scoped
                    val packId = masteryMatch.groupValues[1]
                    val targetDir = File(File(internalDir, "drills"), packId)
                    targetDir.mkdirs()
                    val target = File(targetDir, "word_mastery.yaml")
                    AtomicFileWriter.writeText(target, content)
                    log.appendLine("OK: $name -> drills/$packId/word_mastery.yaml (${content.length} chars)")
                    restoredFiles.add(name)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            log.appendLine("ERROR: $name - ${e.message}")
            false
        }
    }

    /** Migrate legacy `streak.yaml` to `streak_<languageId>.yaml`. */
    private fun migrateOldStreakFormat(backupSubDir: File) {
        val oldStreakFile = File(backupSubDir, "streak.yaml")
        if (!oldStreakFile.exists()) return
        try {
            val content = oldStreakFile.readText()
            if (!validateBackupContent(content, oldStreakFile.name)) {
                Log.w(logTag, "Skipping invalid backup file: ${oldStreakFile.name}")
                return
            }
            val data = yaml.load<Any>(content) as? Map<*, *>
            val languageId = data?.get("languageId") as? String ?: "en"
            val target = File(internalDir, "streak_$languageId.yaml")
            AtomicFileWriter.writeText(target, content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Migrate legacy flat verb drill files to language-specific files. */
    private fun migrateOldVerbDrillFormat(backupSubDir: File) {
        try {
            // Migrate progress file
            val legacyProgressFile = File(backupSubDir, "verb_drill_progress.yaml")
            if (legacyProgressFile.exists()) {
                Log.i(logTag, "Migrating legacy verb_drill_progress.yaml to language-specific files")
                val content = legacyProgressFile.readText()
                if (!validateBackupContent(content, legacyProgressFile.name)) {
                    Log.w(logTag, "Skipping invalid backup file: ${legacyProgressFile.name}")
                    return
                }

                // Try to extract languageId from content
                val data = yaml.load<Any>(content) as? Map<*, *>
                val languageId = data?.get("languageId") as? String ?: "en"
                val target = File(internalDir, "verb_drill_progress_$languageId.yaml")
                AtomicFileWriter.writeText(target, content)
                Log.i(logTag, "Migrated verb_drill_progress.yaml -> verb_drill_progress_$languageId.yaml")
            }

            // Migrate session file
            val legacySessionFile = File(backupSubDir, "verb_drill_last_session.yaml")
            if (legacySessionFile.exists()) {
                Log.i(logTag, "Migrating legacy verb_drill_last_session.yaml to language-specific files")
                val content = legacySessionFile.readText()
                if (!validateBackupContent(content, legacySessionFile.name)) {
                    Log.w(logTag, "Skipping invalid backup file: ${legacySessionFile.name}")
                    return
                }

                // Try to extract languageId from content
                val data = yaml.load<Any>(content) as? Map<*, *>
                val languageId = data?.get("languageId") as? String ?: "en"
                val target = File(internalDir, "verb_drill_last_session_$languageId.yaml")
                AtomicFileWriter.writeText(target, content)
                Log.i(logTag, "Migrated verb_drill_last_session.yaml -> verb_drill_last_session_$languageId.yaml")
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error migrating legacy verb drill files: ${e.message}", e)
        }
    }

    /** Migrate legacy flat verb drill files to language-specific files (SAF/URI version). */
    private fun migrateOldVerbDrillFormatUri(
        backupDir: DocumentFile,
        log: StringBuilder,
        restoredFiles: MutableList<String>
    ) {
        // Migrate progress file
        val oldProgressFile = backupDir.findFile("verb_drill_progress.yaml")
        if (oldProgressFile != null) {
            try {
                val content = context.contentResolver
                    .openInputStream(oldProgressFile.uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    if (!validateBackupContent(content, "verb_drill_progress.yaml")) {
                        log.appendLine("SKIPPED: verb_drill_progress.yaml - failed validation")
                    } else {
                        val data = yaml.load<Any>(content) as? Map<*, *>
                        val languageId = data?.get("languageId") as? String ?: "en"
                        val target = File(internalDir, "verb_drill_progress_$languageId.yaml")
                        AtomicFileWriter.writeText(target, content)
                        log.appendLine("OK: Migrated verb_drill_progress.yaml -> verb_drill_progress_$languageId.yaml")
                        restoredFiles.add("verb_drill_progress_$languageId.yaml (migrated)")
                    }
                }
            } catch (e: Exception) {
                log.appendLine("ERROR: Failed to migrate verb_drill_progress.yaml - ${e.message}")
            }
        } else {
            log.appendLine("No old verb_drill_progress.yaml found (this is normal for new backups)")
        }

        // Migrate session file
        val oldSessionFile = backupDir.findFile("verb_drill_last_session.yaml")
        if (oldSessionFile != null) {
            try {
                val content = context.contentResolver
                    .openInputStream(oldSessionFile.uri)?.bufferedReader()?.use { it.readText() }
                if (content != null) {
                    if (!validateBackupContent(content, "verb_drill_last_session.yaml")) {
                        log.appendLine("SKIPPED: verb_drill_last_session.yaml - failed validation")
                    } else {
                        val data = yaml.load<Any>(content) as? Map<*, *>
                        val languageId = data?.get("languageId") as? String ?: "en"
                        val target = File(internalDir, "verb_drill_last_session_$languageId.yaml")
                        AtomicFileWriter.writeText(target, content)
                        log.appendLine("OK: Migrated verb_drill_last_session.yaml -> verb_drill_last_session_$languageId.yaml")
                        restoredFiles.add("verb_drill_last_session_$languageId.yaml (migrated)")
                    }
                }
            } catch (e: Exception) {
                log.appendLine("ERROR: Failed to migrate verb_drill_last_session.yaml - ${e.message}")
            }
        } else {
            log.appendLine("No old verb_drill_last_session.yaml found (this is normal for new backups)")
        }
    }

    /** Restore drill data from backup. Verb drill progress goes to language-specific path, word mastery stays pack-scoped. */
    private fun restorePackDrillDirs(backupSubDir: File) {
        // Restore language-specific verb drill progress files (new format)
        backupSubDir.listFiles { file ->
            file.name.startsWith("verb_drill_progress_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            val content = file.readText(Charsets.UTF_8)
            if (validateBackupContent(content, file.name)) {
                AtomicFileWriter.writeText(destFile, content)
            } else {
                Log.w(logTag, "Skipping invalid backup file: ${file.name}")
            }
        }

        // Restore language-specific verb drill last session files (new format)
        backupSubDir.listFiles { file ->
            file.name.startsWith("verb_drill_last_session_") && file.name.endsWith(".yaml")
        }?.forEach { file ->
            val destFile = File(internalDir, file.name)
            val content = file.readText(Charsets.UTF_8)
            if (validateBackupContent(content, file.name)) {
                AtomicFileWriter.writeText(destFile, content)
            } else {
                Log.w(logTag, "Skipping invalid backup file: ${file.name}")
            }
        }

        // Migrate old flat verb drill files to language-specific
        migrateOldVerbDrillFormat(backupSubDir)

        // Restore pack-scoped word mastery files
        val backupDrillsDir = File(backupSubDir, "drills")
        if (!backupDrillsDir.exists()) return
        backupDrillsDir.listFiles(java.io.FileFilter { it.isDirectory })?.forEach { packBackupDir ->
            val wordMastery = File(packBackupDir, "word_mastery.yaml")
            if (wordMastery.exists()) {
                val content = wordMastery.readText(Charsets.UTF_8)
                if (validateBackupContent(content, wordMastery.name)) {
                    val targetPackDir = File(File(internalDir, "drills"), packBackupDir.name)
                    targetPackDir.mkdirs()
                    AtomicFileWriter.writeText(File(targetPackDir, "word_mastery.yaml"), content)
                } else {
                    Log.w(logTag, "Skipping invalid backup file: ${wordMastery.name}")
                }
            }
        }
    }

    /** Append mastery data analysis to the restore log (informational only). */
    private fun appendMasteryAnalysis(log: StringBuilder) {
        log.appendLine("--- Mastery Data Analysis ---")
        try {
            val masteryFile = File(internalDir, "mastery.yaml")
            if (masteryFile.exists()) {
                val masteryContent = yaml.load<Any>(masteryFile.readText()) as? Map<*, *>
                val data = masteryContent?.get("data") as? Map<*, *>
                if (data != null) {
                    data.forEach { (lang, lessons) ->
                        log.appendLine("Language: $lang")
                        val lessonsMap = lessons as? Map<*, *>
                        lessonsMap?.forEach { (lessonId, lessonData) ->
                            val ld = lessonData as? Map<*, *>
                            val uniqueShows = ld?.get("uniqueCardShows") as? Number
                            val totalShows = ld?.get("totalCardShows") as? Number
                            val lastShowMs = ld?.get("lastShowDateMs") as? Number
                            val intervalStep = ld?.get("intervalStepIndex") as? Number
                            log.appendLine("  $lessonId: unique=$uniqueShows, total=$totalShows, intervalStep=$intervalStep")
                            if (lastShowMs != null) {
                                val daysSinceShow = (System.currentTimeMillis() - lastShowMs.toLong()) / (24 * 60 * 60 * 1000)
                                log.appendLine("    Last shown: $daysSinceShow days ago")
                            }
                        }
                    }
                } else {
                    log.appendLine("No mastery data found in file")
                }
            } else {
                log.appendLine("Mastery file not found after restore")
            }
        } catch (e: Exception) {
            log.appendLine("ERROR parsing mastery: ${e.message}")
        }
        log.appendLine()
    }

    private fun writeRestoreLog(backupUri: Uri, logContent: String) {
        try {
            val backupDir = DocumentFile.fromTreeUri(context, backupUri)
                ?: DocumentFile.fromSingleUri(context, backupUri)
                ?: return

            val logFile = backupDir.findFile("restore_log.txt")
            if (logFile != null) {
                context.contentResolver.openOutputStream(logFile.uri, "wt")?.use { output ->
                    output.write(logContent.toByteArray())
                }
            } else {
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

    // endregion
}
