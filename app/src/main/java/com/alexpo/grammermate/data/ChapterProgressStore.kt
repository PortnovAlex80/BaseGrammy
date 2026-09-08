package com.alexpo.grammermate.data

import android.content.Context
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Tracks chapter progress per pack.
 * Stores which lessons in each chapter have been started and completed.
 * Pack-scoped, independent progress across chapters.
 *
 * File location: grammarmate/packs/{packId}/chapter_progress.yaml
 *
 * @param context Application context for file paths
 * @param packId Pack identifier for scoping
 */
class ChapterProgressStore(
    context: Context,
    private val packId: String
) {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val packDir = File(baseDir, "packs/$packId")
    private val progressFile = File(packDir, "chapter_progress.yaml")

    /**
     * Loads all chapter progress from disk.
     *
     * @return Map of chapterId -> ChapterProgress. Empty map if file missing or corrupt.
     */
    fun loadAll(): Map<String, ChapterProgress> {
        if (!progressFile.exists()) return emptyMap()

        return try {
            val data = yaml.load<Map<String, Any>>(progressFile.inputStream())
            val schemaVersion = (data["schemaVersion"] as? Int?) ?: 1
            if (schemaVersion != 1) {
                return emptyMap()
            }

            @Suppress("UNCHECKED_CAST")
            val dataMap = data["data"] as? Map<String, Map<String, Any>> ?: return emptyMap()

            dataMap.mapValues { (chapterId, progressData) ->
                ChapterProgress(
                    chapterId = chapterId,
                    lessonsStarted = (progressData["lessonsStarted"] as? Int) ?: 0,
                    lessonsCompleted = (progressData["lessonsCompleted"] as? Int) ?: 0,
                    lastAccessedMs = (progressData["lastAccessedMs"] as? Long) ?: 0L
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    /**
     * Writes all chapter progress to disk via AtomicFileWriter.
     *
     * @param progress Map of chapterId -> ChapterProgress to persist
     */
    fun saveAll(progress: Map<String, ChapterProgress>) {
        val dataMap = progress.mapValues { (_, chapterProgress) ->
            mapOf(
                "lessonsStarted" to chapterProgress.lessonsStarted,
                "lessonsCompleted" to chapterProgress.lessonsCompleted,
                "lastAccessedMs" to chapterProgress.lastAccessedMs
            )
        }

        val output = mapOf(
            "schemaVersion" to 1,
            "data" to dataMap
        )

        AtomicFileWriter.writeText(progressFile, yaml.dump(output))
    }

    /**
     * Gets progress for a single chapter.
     *
     * @param chapterId Chapter identifier
     * @return ChapterProgress if tracked, null otherwise
     */
    fun getProgress(chapterId: String): ChapterProgress? {
        return loadAll()[chapterId]
    }

    /**
     * Inserts or replaces progress for a single chapter.
     * Uses load-modify-save pattern.
     *
     * @param progress ChapterProgress to upsert
     */
    fun upsertProgress(progress: ChapterProgress) {
        val all = loadAll().toMutableMap()
        all[progress.chapterId] = progress
        saveAll(all)
    }

    /**
     * Clears all chapter progress for this pack.
     * Deletes the progress file.
     */
    fun clear() {
        if (progressFile.exists()) {
            progressFile.delete()
        }
    }
}