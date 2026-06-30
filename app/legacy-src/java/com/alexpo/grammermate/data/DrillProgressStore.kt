package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface DrillProgressStore {
    fun getDrillProgress(lessonId: String): Int
    fun saveDrillProgress(lessonId: String, cardIndex: Int)
    fun hasProgress(lessonId: String): Boolean
    fun clearDrillProgress(lessonId: String)
}

class DrillProgressStoreImpl(private val context: Context) : DrillProgressStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val mutex = ReentrantLock()

    private fun getFile(lessonId: String): File {
        return File(baseDir, "drill_progress_$lessonId.yaml")
    }

    override fun getDrillProgress(lessonId: String): Int = mutex.withLock {
        val file = getFile(lessonId)
        if (!file.exists()) return -1
        val raw = runCatching { yaml.load<Any>(file.readText()) }.getOrNull() ?: return -1
        val data = raw as? Map<*, *> ?: return -1
        val idx = (data["cardIndex"] as? Number)?.toInt() ?: -1
        return if (idx > 0) idx else -1
    }

    override fun saveDrillProgress(lessonId: String, cardIndex: Int) {
        mutex.withLock {
            baseDir.mkdirs()
            val file = getFile(lessonId)
            val payload = mapOf(
                "lessonId" to lessonId,
                "cardIndex" to cardIndex
            )
            try {
                AtomicFileWriter.writeText(file, yaml.dump(payload))
                Log.i("DrillProgressStore", "Successfully saved drill progress for lesson $lessonId: ${file.name} (${file.length()} bytes)")
            } catch (e: IOException) {
                Log.e("DrillProgressStore", "Failed to save drill progress for lesson $lessonId: ${file.name}", e)
            } catch (e: Exception) {
                Log.e("DrillProgressStore", "Unexpected error saving drill progress for lesson $lessonId: ${file.name}", e)
            }
        }
    }

    override fun hasProgress(lessonId: String): Boolean {
        return getDrillProgress(lessonId) > 0
    }

    override fun clearDrillProgress(lessonId: String) = mutex.withLock {
        val file = getFile(lessonId)
        if (file.exists()) file.delete()
    }
}
