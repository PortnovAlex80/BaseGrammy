package com.alexpo.grammermate.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Replaces the managed progress snapshot. All sources are read and validated
 * before touching internal files; installed packs and app configuration are excluded.
 */
internal class BackupRestorer(private val context: Context) {
    private val yaml = Yaml()
    private val internalDir = File(context.filesDir, "grammarmate")

    fun restoreFromPath(backupPath: String): Boolean {
        val root = File(backupPath).let { File(it, "backup_latest").takeIf(File::isDirectory) ?: it }
        if (!root.isDirectory) return false
        return try {
            val snapshot = linkedMapOf<String, String>()
            BackupSnapshotFiles.listIn(root).forEach { (path, file) -> snapshot[path] = file.readText() }
            addLegacyFiles(root.listFiles()?.filter { it.isFile && isLegacyFile(it.name) }
                .orEmpty().map { it.name to it.readText() }, snapshot)
            applySnapshot(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from path", e)
            false
        }
    }

    fun restoreFromUri(backupUri: Uri): Boolean {
        val tree = DocumentFile.fromTreeUri(context, backupUri)
            ?: DocumentFile.fromSingleUri(context, backupUri)
            ?: return false
        val root = tree.findFile("backup_latest")?.takeIf(DocumentFile::isDirectory) ?: tree
        val log = StringBuilder("Backup source: ${root.uri}\n")
        val success = try {
            val snapshot = linkedMapOf<String, String>()
            val legacy = mutableListOf<Pair<String, String>>()
            root.listFiles().forEach { file ->
                val name = file.name ?: return@forEach
                if (file.isFile) {
                    if (BackupSnapshotFiles.isManaged(name)) {
                        snapshot[name] = readDocument(file)
                    } else if (isLegacyFile(name)) {
                        legacy += name to readDocument(file)
                    }
                } else if (name == "drills" && file.isDirectory) {
                    file.listFiles().filter(DocumentFile::isDirectory).forEach { pack ->
                        val packId = pack.name ?: return@forEach
                        pack.listFiles().filter(DocumentFile::isFile).forEach { child ->
                            val childName = child.name ?: return@forEach
                            val path = "drills/$packId/$childName"
                            if (BackupSnapshotFiles.isManaged(path)) {
                                snapshot[path] = readDocument(child)
                            }
                        }
                    }
                }
            }
            addLegacyFiles(legacy, snapshot)
            applySnapshot(snapshot).also {
                log.appendLine("Result: ${if (it) "SUCCESS" else "FAILED"}")
                log.appendLine("Progress files: ${snapshot.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from URI", e)
            log.appendLine("ERROR: ${e.message}")
            false
        }
        writeRestoreLog(tree, log.toString())
        return success
    }

    private fun readDocument(file: DocumentFile): String =
        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read ${file.name}")

    private fun applySnapshot(snapshot: Map<String, String>): Boolean {
        if (snapshot.isEmpty() || snapshot.keys.any { !BackupSnapshotFiles.isManaged(it) }) return false
        if (snapshot.any { (name, content) -> !isValid(content, name) }) return false
        if (!internalDir.exists() && !internalDir.mkdirs()) return false

        val existing = BackupSnapshotFiles.listIn(internalDir)
        val previous = existing.mapValues { (_, file) -> file.readBytes() }
        return try {
            // A missing progress file in the backup means empty progress for that feature.
            existing.filterKeys { it !in snapshot }.values.forEach { file ->
                if (!file.delete()) error("Cannot delete stale ${file.name}")
            }
            snapshot.forEach { (path, content) ->
                AtomicFileWriter.writeText(File(internalDir, path), content)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed; rolling back previous progress files", e)
            BackupSnapshotFiles.listIn(internalDir).filterKeys { it !in previous }.values.forEach(File::delete)
            previous.forEach { (path, bytes) ->
                try {
                    AtomicFileWriter.writeText(File(internalDir, path), bytes.toString(Charsets.UTF_8))
                } catch (rollbackError: Exception) {
                    Log.e(TAG, "Failed to roll back $path", rollbackError)
                }
            }
            false
        }
    }

    private fun isValid(content: String, name: String): Boolean {
        if (content.isBlank()) return false
        return try {
            val value = yaml.load<Any>(content)
            when (value) {
                is Map<*, *> -> value["schemaVersion"]?.let { it is Number } ?: true
                is List<*> -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Invalid backup file: $name", e)
            false
        }
    }

    private fun addLegacyFiles(files: List<Pair<String, String>>, snapshot: MutableMap<String, String>) {
        files.forEach { (name, content) ->
            val target = when {
                name == "streak.yaml" -> "streak_${languageId(content)}.yaml"
                name == "verb_drill_progress.yaml" -> "verb_drill_progress_${languageId(content)}.yaml"
                name == "verb_drill_last_session.yaml" -> "verb_drill_last_session_${languageId(content)}.yaml"
                name.startsWith("drills_") && name.endsWith(".yaml") -> {
                    val remainder = name.removePrefix("drills_")
                    val fileName = BackupSnapshotFiles.packLegacyNames.firstOrNull { remainder.endsWith("_$it") }
                    fileName?.let { "drills/${remainder.removeSuffix("_$it")}/$it" }
                }
                else -> null
            }
            if (target != null && BackupSnapshotFiles.isManaged(target)) {
                snapshot.putIfAbsent(target, content)
            }
        }
    }

    private fun isLegacyFile(name: String): Boolean =
        name in setOf("streak.yaml", "verb_drill_progress.yaml", "verb_drill_last_session.yaml") ||
            (name.startsWith("drills_") && name.endsWith(".yaml"))

    private fun languageId(content: String): String = try {
        ((yaml.load<Any>(content) as? Map<*, *>)?.get("languageId") as? String)
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) } ?: "en"
    } catch (_: Exception) {
        "en"
    }

    private fun writeRestoreLog(tree: DocumentFile, content: String) {
        try {
            val log = tree.findFile("restore_log.txt") ?: tree.createFile("text/plain", "restore_log.txt")
            log?.let { context.contentResolver.openOutputStream(it.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray())
            } }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write restore log", e)
        }
    }

    companion object {
        private const val TAG = "BackupRestorer"
    }
}
