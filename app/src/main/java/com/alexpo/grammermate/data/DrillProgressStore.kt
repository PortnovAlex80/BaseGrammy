package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

class DrillProgressStore(private val context: Context) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")

    private fun getFile(lessonId: String): File {
        return File(baseDir, "drill_progress_$lessonId.yaml")
    }

    fun getDrillProgress(lessonId: String): Int {
        val file = getFile(lessonId)
        if (!file.exists()) return -1
        val raw = runCatching { yaml.load<Any>(file.readText()) }.getOrNull() ?: return -1
        val data = raw as? Map<*, *> ?: return -1
        val idx = (data["cardIndex"] as? Number)?.toInt() ?: -1
        return if (idx > 0) idx else -1
    }

    fun saveDrillProgress(lessonId: String, cardIndex: Int) {
        baseDir.mkdirs()
        val file = getFile(lessonId)
        val payload = mapOf(
            "lessonId" to lessonId,
            "cardIndex" to cardIndex
        )
        AtomicFileWriter.writeText(file, yaml.dump(payload))
    }

    fun hasProgress(lessonId: String): Boolean {
        return getDrillProgress(lessonId) > 0
    }

    fun clearDrillProgress(lessonId: String) {
        val file = getFile(lessonId)
        if (file.exists()) file.delete()
    }
}
