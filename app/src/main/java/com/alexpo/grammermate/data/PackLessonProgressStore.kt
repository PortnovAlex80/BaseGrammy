package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Store for pack-scoped lesson progress state.
 * Each pack maintains its own lesson progress to prevent cross-pack contamination.
 *
 * Used by TASK-081: Lesson progress isolation bug fix.
 * Follows PackDailyCursorStore pattern for consistency.
 */
interface PackLessonProgressStore {

    /** Load lesson progress for a specific pack. Returns null if not found. */
    fun loadPackProgress(packId: String): PackLessonProgressState?

    /** Save lesson progress for a specific pack. */
    fun savePackProgress(progress: PackLessonProgressState)

    /** Delete lesson progress for a specific pack. */
    fun deletePackProgress(packId: String)

    /** Load all pack lesson progress states. Returns map of packId -> progress state. */
    fun loadAllPackProgress(): Map<String, PackLessonProgressState>

    /** Flush any pending writes to disk immediately. */
    fun flush()

    /**
     * Migrate global lesson progress to pack-scoped storage.
     * Called once during app upgrade to TASK-081.
     *
     * @param globalProgress The global progress state to migrate
     * @param packId The target pack ID to migrate progress to
     */
    fun migrateGlobalToPackScoped(globalProgress: TrainingProgress, packId: PackId)
}

class PackLessonProgressStoreImpl(context: Context) : PackLessonProgressStore {

    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    // In-memory cache for progress data — invalidated on save
    private var progressCache: MutableMap<String, PackLessonProgressState>? = null

    private fun getFileForPack(packId: String): File {
        return File(baseDir, "lesson_progress_$packId.yaml")
    }

    override fun loadPackProgress(packId: String): PackLessonProgressState? {
        return mutex.withLock {
            // Check cache first
            progressCache?.get(packId)?.let { return@withLock it }

            val file = getFileForPack(packId)
            if (!file.exists()) {
                Log.d("PackLessonProgressStore", "No progress file found for pack: $packId")
                return@withLock null
            }

            try {
                val data = file.readText()
                val map = yaml.load(data) as? Map<String, Any>
                if (map != null) {
                    val progress = parseProgressState(map, packId)
                    // Update cache
                    progressCache?.put(packId, progress) ?: run {
                        progressCache = mutableMapOf(packId to progress)
                    }
                    Log.d("PackLessonProgressStore", "Loaded progress for pack: $packId, lessons: ${progress.lessonProgress.size}")
                    return@withLock progress
                }
            } catch (e: Exception) {
                Log.e("PackLessonProgressStore", "Failed to load progress for pack: $packId", e)
            }
            return@withLock null
        }
    }

    override fun savePackProgress(progress: PackLessonProgressState) {
        mutex.withLock {
            val file = getFileForPack(progress.packId)
            try {
                val map = mapOf(
                    "schemaVersion" to schemaVersion,
                    "packId" to progress.packId,
                    "lessonProgress" to progress.lessonProgress.mapValues { (_, lessonProgress) ->
                        mapOf(
                            "currentIndex" to lessonProgress.currentIndex,
                            "correctCount" to lessonProgress.correctCount,
                            "incorrectCount" to lessonProgress.incorrectCount,
                            "incorrectAttemptsForCard" to lessonProgress.incorrectAttemptsForCard,
                            "activeTimeMs" to lessonProgress.activeTimeMs,
                            "state" to lessonProgress.state.name
                        )
                    }
                )
                val yaml = Yaml()
                val data = yaml.dump(map)
                AtomicFileWriter.writeText(file, data)

                // Update cache
                progressCache?.put(progress.packId, progress) ?: run {
                    progressCache = mutableMapOf(progress.packId to progress)
                }

                Log.d("PackLessonProgressStore", "Saved progress for pack: ${progress.packId}, lessons: ${progress.lessonProgress.size}")
            } catch (e: Exception) {
                Log.e("PackLessonProgressStore", "Failed to save progress for pack: ${progress.packId}", e)
                throw IOException("Failed to save pack lesson progress state", e)
            }
        }
    }

    override fun deletePackProgress(packId: String) {
        mutex.withLock {
            val file = getFileForPack(packId)
            if (file.exists()) {
                file.delete()
                // Remove from cache
                progressCache?.remove(packId)
                Log.d("PackLessonProgressStore", "Deleted progress for pack: $packId")
            }
        }
    }

    override fun loadAllPackProgress(): Map<String, PackLessonProgressState> {
        return mutex.withLock {
            // Return all cached progress if available
            progressCache?.let { return@withLock it.toMap() }

            // Otherwise, scan directory for all progress files
            val progressMap = mutableMapOf<String, PackLessonProgressState>()
            baseDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("lesson_progress_") && file.name.endsWith(".yaml")) {
                    // Extract packId from filename: "lesson_progress_ru-en-v1.yaml" → "ru-en-v1"
                    val packId = file.name.removePrefix("lesson_progress_").removeSuffix(".yaml")
                    try {
                        val data = file.readText()
                        val map = yaml.load(data) as? Map<String, Any>
                        if (map != null) {
                            val progress = parseProgressState(map, packId)
                            progressMap[packId] = progress
                        }
                    } catch (e: Exception) {
                        Log.e("PackLessonProgressStore", "Failed to load progress from file: ${file.name}", e)
                    }
                }
            }

            // Update cache
            progressCache = progressMap
            Log.d("PackLessonProgressStore", "Loaded ${progressMap.size} pack lesson progress states")
            return@withLock progressMap
        }
    }

    override fun flush() {
        // All writes are immediate (AtomicFileWriter), so this is a no-op
        // Kept for interface consistency
    }

    private fun parseProgressState(map: Map<String, Any>, packId: String): PackLessonProgressState {
        val lessonProgressMap = (map["lessonProgress"] as? Map<*, *>) ?: emptyMap<String, Any>()
        val lessonProgress = lessonProgressMap.mapNotNull { (lessonId, progressData) ->
            val progressMap = progressData as? Map<*, *> ?: return@mapNotNull null
            val lessonIdStr = lessonId as? String ?: return@mapNotNull null
            lessonIdStr to PackLessonProgressState.LessonProgress(
                currentIndex = (progressMap["currentIndex"] as? Number)?.toInt() ?: 0,
                correctCount = (progressMap["correctCount"] as? Number)?.toInt() ?: 0,
                incorrectCount = (progressMap["incorrectCount"] as? Number)?.toInt() ?: 0,
                incorrectAttemptsForCard = (progressMap["incorrectAttemptsForCard"] as? Number)?.toInt() ?: 0,
                activeTimeMs = (progressMap["activeTimeMs"] as? Number)?.toLong() ?: 0L,
                state = SessionState.valueOf(progressMap["state"] as? String ?: SessionState.PAUSED.name)
            )
        }.toMap()

        return PackLessonProgressState(
            packId = packId,
            lessonProgress = lessonProgress
        )
    }

    override fun migrateGlobalToPackScoped(globalProgress: TrainingProgress, packId: PackId) {
        mutex.withLock {
            val file = getFileForPack(packId.value)

            // Skip if pack-scoped file already exists
            if (file.exists()) {
                Log.d("PackLessonProgressStore", "Pack progress file already exists: $packId")
                return@withLock
            }

            try {
                // Create pack-scoped progress from global state
                // Only migrate if there's actual lesson progress data
                val lessonId = globalProgress.lessonId
                if (lessonId != null) {
                    val lessonProgress = mapOf(
                        lessonId to PackLessonProgressState.LessonProgress(
                            currentIndex = globalProgress.currentIndex,
                            correctCount = globalProgress.correctCount,
                            incorrectCount = globalProgress.incorrectCount,
                            incorrectAttemptsForCard = globalProgress.incorrectAttemptsForCard,
                            activeTimeMs = globalProgress.activeTimeMs,
                            state = globalProgress.state
                        )
                    )

                    val packProgress = PackLessonProgressState(
                        packId = packId.value,
                        lessonProgress = lessonProgress
                    )

                    savePackProgress(packProgress)
                    Log.i("PackLessonProgressStore", "Migrated global progress to pack: $packId, lesson: $lessonId")
                } else {
                    Log.d("PackLessonProgressStore", "No lesson progress in global state to migrate for pack: $packId")
                }
            } catch (e: Exception) {
                Log.e("PackLessonProgressStore", "Failed to migrate global progress to pack: $packId", e)
                throw IOException("Failed to migrate global progress to pack-scoped storage", e)
            }
        }
    }
}