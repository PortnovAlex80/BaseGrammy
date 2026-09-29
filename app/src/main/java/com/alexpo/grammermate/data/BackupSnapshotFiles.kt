package com.alexpo.grammermate.data

import java.io.File

/** The progress files owned by backup/restore. Installed content and app configuration stay untouched. */
internal object BackupSnapshotFiles {
    private val topLevel = setOf(
        "mastery.yaml", "progress.yaml", "profile.yaml", "hidden_cards.yaml",
        "bad_sentences.yaml", "vocab_progress.yaml", "word_mastery.yaml"
    )
    private val packFiles = setOf(
        "word_mastery.yaml", "verb_drill_progress.yaml", "verb_drill_last_session.yaml",
        "aux_drill_progress.yaml", "vocab_progress.yaml"
    )
    val packLegacyNames: Set<String> = packFiles
    private val prefixes = listOf(
        "streak_", "drill_progress_", "verb_drill_progress_",
        "verb_drill_last_session_", "aux_drill_progress_", "lesson_progress_", "daily_cursor_"
    )

    fun isManaged(relativePath: String): Boolean {
        val parts = relativePath.replace('\\', '/').split('/')
        if (parts.size == 1) {
            val name = parts[0]
            return name in topLevel || (name.endsWith(".yaml") && prefixes.any(name::startsWith))
        }
        return parts.size == 3 && parts[0] == "drills" &&
            parts[1].isNotBlank() && parts[1] != "." && parts[1] != ".." &&
            parts[2] in packFiles
    }

    fun listIn(root: File): Map<String, File> {
        val result = linkedMapOf<String, File>()
        root.listFiles()?.filter(File::isFile)?.forEach { file ->
            if (isManaged(file.name)) result[file.name] = file
        }
        File(root, "drills").listFiles()?.filter(File::isDirectory)?.forEach { packDir ->
            packDir.listFiles()?.filter(File::isFile)?.forEach { file ->
                val path = "drills/${packDir.name}/${file.name}"
                if (isManaged(path)) result[path] = file
            }
        }
        return result
    }
}
