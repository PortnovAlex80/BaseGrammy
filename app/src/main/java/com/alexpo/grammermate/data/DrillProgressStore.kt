package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Persists drill mode progress per lesson.
 * Stores the 0-based group index so the user can resume where they left off.
 */
class DrillProgressStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")

    private fun getFile(lessonId: String): File {
        return File(baseDir, "drill_progress_$lessonId.yaml")
    }

    /**
     * Returns the saved 0-based group index, or -1 if no progress exists.
     */
    fun getDrillProgress(lessonId: String): Int {
        val file = getFile(lessonId)
        if (!file.exists()) return -1
        val raw = runCatching { yaml.load<Any>(file.readText()) }.getOrNull() ?: return -1
        val data = raw as? Map<*, *> ?: return -1
        return (data["groupIndex"] as? Number)?.toInt() ?: -1
    }

    /**
     * Save the current group index for a lesson.
     */
    fun saveDrillProgress(lessonId: String, groupIndex: Int) {
        baseDir.mkdirs()
        val file = getFile(lessonId)
        val payload = mapOf(
            "lessonId" to lessonId,
            "groupIndex" to groupIndex
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    /**
     * Returns true if there is saved progress for the given lesson.
     */
    fun hasProgress(lessonId: String): Boolean {
        return getDrillProgress(lessonId) >= 0
    }

    /**
     * Remove saved progress (e.g. when all groups are completed).
     */
    fun clearDrillProgress(lessonId: String) {
        val file = getFile(lessonId)
        if (file.exists()) file.delete()
    }
}
