package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface ProgressStore {

    fun load(): TrainingProgress

    fun save(progress: TrainingProgress)

    fun clear()

    /**
     * Migrate global daily cursor to pack-scoped cursor files.
     * Called once during app upgrade to TASK-080.
     *
     * @param activePackId The currently active pack ID to migrate global cursor to
     * @param packCursorStore The pack-scoped cursor store to save migrated data
     * @return true if migration was performed, false if no data to migrate
     */
    fun migrateGlobalDailyCursorToPackScoped(
        activePackId: String?,
        packCursorStore: PackDailyCursorStore
    ): Boolean

    /**
     * Migrate global lesson progress to pack-scoped lesson progress files.
     * Called once during app upgrade to TASK-081.
     *
     * @param activePackId The currently active pack ID to migrate global progress to
     * @param packProgressStore The pack-scoped progress store to save migrated data
     * @return true if migration was performed, false if no data to migrate
     */
    fun migrateGlobalLessonProgressToPackScoped(
        activePackId: String?,
        packProgressStore: PackLessonProgressStore
    ): Boolean
}

class ProgressStoreImpl(private val context: Context) : ProgressStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "progress.yaml")
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    override fun load(): TrainingProgress = mutex.withLock {
        if (!file.exists() || file.length() == 0L) return TrainingProgress()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: return TrainingProgress()
        val data = when (raw) {
            is Map<*, *> -> raw
            else -> return TrainingProgress()
        }
        val payload = (data["data"] as? Map<*, *>) ?: data
        return TrainingProgress(
            languageId = LanguageId(payload["languageId"] as? String ?: "en"),
            mode = TrainingMode.valueOf(payload["mode"] as? String ?: TrainingMode.LESSON.name),
            bossLessonRewards = (payload["bossLessonRewards"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val lessonId = key as? String ?: return@mapNotNull null
                val reward = value as? String ?: return@mapNotNull null
                lessonId to reward
            }?.toMap() ?: emptyMap(),
            bossMegaReward = payload["bossMegaReward"] as? String,
            bossMegaRewards = (payload["bossMegaRewards"] as? Map<*, *>)?.mapNotNull { (key, value) ->
                val lessonId = key as? String ?: return@mapNotNull null
                val reward = value as? String ?: return@mapNotNull null
                lessonId to reward
            }?.toMap() ?: emptyMap(),
            voiceActiveMs = (payload["voiceActiveMs"] as? Number)?.toLong() ?: 0L,
            voiceWordCount = (payload["voiceWordCount"] as? Number)?.toInt() ?: 0,
            hintCount = (payload["hintCount"] as? Number)?.toInt() ?: 0,
            eliteStepIndex = (payload["eliteStepIndex"] as? Number)?.toInt() ?: 0,
            eliteBestSpeeds = (payload["eliteBestSpeeds"] as? List<*>)?.mapNotNull { it as? Number }
                ?.map { it.toDouble() }
                ?: emptyList(),
            currentScreen = payload["currentScreen"] as? String ?: "HOME",
            activePackId = (payload["activePackId"] as? String)?.let { PackId(it) },
            dailyLevel = (payload["dailyLevel"] as? Number)?.toInt() ?: 0,
            dailyTaskIndex = (payload["dailyTaskIndex"] as? Number)?.toInt() ?: 0,
            dailyCursor = run {
                val cursorPayload = payload["dailyCursor"] as? Map<*, *>
                DailyCursorState(
                    sentenceOffset = (cursorPayload?.get("sentenceOffset") as? Number)?.toInt() ?: 0,
                    currentLessonIndex = (cursorPayload?.get("currentLessonIndex") as? Number)?.toInt() ?: 0,
                    lastSessionHash = (cursorPayload?.get("lastSessionHash") as? Number)?.toInt() ?: 0,
                    firstSessionDate = cursorPayload?.get("firstSessionDate") as? String ?: "",
                    firstSessionSentenceCardIds = (cursorPayload?.get("firstSessionSentenceCardIds") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList(),
                    firstSessionVerbCardIds = (cursorPayload?.get("firstSessionVerbCardIds") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList()
                )
            }
        )
    }

    override fun save(progress: TrainingProgress) {
        mutex.withLock {
            val payload = linkedMapOf(
                "languageId" to progress.languageId.value,
                "mode" to progress.mode.name,
                "bossLessonRewards" to progress.bossLessonRewards,
                "bossMegaReward" to progress.bossMegaReward,
                "bossMegaRewards" to progress.bossMegaRewards,
                "voiceActiveMs" to progress.voiceActiveMs,
                "voiceWordCount" to progress.voiceWordCount,
                "hintCount" to progress.hintCount,
                "eliteStepIndex" to progress.eliteStepIndex,
                "eliteBestSpeeds" to progress.eliteBestSpeeds,
                "currentScreen" to progress.currentScreen,
                "activePackId" to progress.activePackId?.value,
                "dailyLevel" to progress.dailyLevel,
                "dailyTaskIndex" to progress.dailyTaskIndex,
                "dailyCursor" to linkedMapOf(
                    "sentenceOffset" to progress.dailyCursor.sentenceOffset,
                    "currentLessonIndex" to progress.dailyCursor.currentLessonIndex,
                    "lastSessionHash" to progress.dailyCursor.lastSessionHash,
                    "firstSessionDate" to progress.dailyCursor.firstSessionDate,
                    "firstSessionSentenceCardIds" to progress.dailyCursor.firstSessionSentenceCardIds,
                    "firstSessionVerbCardIds" to progress.dailyCursor.firstSessionVerbCardIds
                )
            )
            val data = linkedMapOf(
                "schemaVersion" to schemaVersion,
                "data" to payload
            )
            try {
                AtomicFileWriter.writeText(file, yaml.dump(data))
                Log.i("ProgressStore", "Successfully saved training progress: ${file.name} (${file.length()} bytes)")
            } catch (e: IOException) {
                Log.e("ProgressStore", "Failed to save training progress: ${file.name}", e)
                throw e
            } catch (e: Exception) {
                Log.e("ProgressStore", "Unexpected error saving training progress: ${file.name}", e)
                throw IOException("Failed to save training progress", e)
            }
        }
    }

    override fun clear() = mutex.withLock {
        if (file.exists()) file.delete()
    }

    override fun migrateGlobalDailyCursorToPackScoped(
        activePackId: String?,
        packCursorStore: PackDailyCursorStore
    ): Boolean = mutex.withLock {
        // Check if there's existing global daily cursor data to migrate
        val progress = load()
        val globalCursor = progress.dailyCursor

        // Check if global cursor has meaningful data (not defaults)
        val hasDataToMigrate = globalCursor.sentenceOffset > 0 ||
            globalCursor.currentLessonIndex > 0 ||
            globalCursor.firstSessionDate.isNotEmpty() ||
            globalCursor.firstSessionSentenceCardIds.isNotEmpty() ||
            globalCursor.firstSessionVerbCardIds.isNotEmpty() ||
            globalCursor.verbOffset > 0

        if (!hasDataToMigrate) {
            Log.d("ProgressStore", "No global cursor data to migrate")
            return@withLock false
        }

        // Determine target pack ID for migration
        val targetPackId = activePackId ?: progress.activePackId?.value
        if (targetPackId == null) {
            Log.w("ProgressStore", "Cannot migrate global cursor: no active pack ID available")
            return@withLock false
        }

        try {
            // Create pack-scoped cursor state from global cursor
            val packCursor = PackDailyCursorState(
                packId = targetPackId,
                sentenceOffset = globalCursor.sentenceOffset,
                currentLessonIndex = globalCursor.currentLessonIndex,
                lastSessionHash = globalCursor.lastSessionHash,
                firstSessionDate = globalCursor.firstSessionDate,
                firstSessionSentenceCardIds = globalCursor.firstSessionSentenceCardIds,
                firstSessionVerbCardIds = globalCursor.firstSessionVerbCardIds,
                verbOffset = globalCursor.verbOffset
            )

            // Save to pack-specific file
            packCursorStore.savePackCursor(packCursor)
            Log.i("ProgressStore", "Migrated global cursor to pack: $targetPackId")

            // Clear global cursor from progress.yaml
            val clearedProgress = progress.copy(dailyCursor = DailyCursorState())
            save(clearedProgress)
            Log.i("ProgressStore", "Cleared global cursor from progress.yaml after migration")

            return@withLock true
        } catch (e: Exception) {
            Log.e("ProgressStore", "Failed to migrate global cursor to pack: $targetPackId", e)
            return@withLock false
        }
    }

    override fun migrateGlobalLessonProgressToPackScoped(
        activePackId: String?,
        packProgressStore: PackLessonProgressStore
    ): Boolean = mutex.withLock {
        // Read raw YAML file to access legacy lesson progress fields
        if (!file.exists() || file.length() == 0L) {
            Log.d("ProgressStore", "No progress file found for migration")
            return@withLock false
        }

        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null }
        if (raw == null) {
            Log.d("ProgressStore", "Failed to read progress file for migration")
            return@withLock false
        }

        val data = when (raw) {
            is Map<*, *> -> raw
            else -> {
                Log.d("ProgressStore", "Progress file has invalid format for migration")
                return@withLock false
            }
        }
        val payload = (data["data"] as? Map<*, *>) ?: data

        // Extract legacy lesson progress fields from raw data
        val currentIndex = (payload["currentIndex"] as? Number)?.toInt() ?: 0
        val correctCount = (payload["correctCount"] as? Number)?.toInt() ?: 0
        val incorrectCount = (payload["incorrectCount"] as? Number)?.toInt() ?: 0
        val incorrectAttemptsForCard = (payload["incorrectAttemptsForCard"] as? Number)?.toInt() ?: 0
        val activeTimeMs = (payload["activeTimeMs"] as? Number)?.toLong() ?: 0L
        val lessonId = payload["lessonId"] as? String
        val stateString = payload["state"] as? String ?: SessionState.PAUSED.name

        // Check if there's meaningful lesson progress data to migrate
        val hasDataToMigrate = currentIndex > 0 ||
            correctCount > 0 ||
            incorrectCount > 0 ||
            activeTimeMs > 0L

        if (!hasDataToMigrate) {
            Log.d("ProgressStore", "No global lesson progress data to migrate")
            return@withLock false
        }

        // Determine target pack ID for migration
        val targetPackId = activePackId ?: (payload["activePackId"] as? String)
        if (targetPackId == null) {
            Log.w("ProgressStore", "Cannot migrate lesson progress: no active pack ID available")
            return@withLock false
        }

        try {
            // Get lesson ID from legacy data
            if (lessonId == null) {
                Log.w("ProgressStore", "Cannot migrate lesson progress: no lesson ID in legacy data")
                return@withLock false
            }

            // Parse session state
            val state = try {
                SessionState.valueOf(stateString)
            } catch (e: Exception) {
                Log.w("ProgressStore", "Invalid session state in legacy data: $stateString")
                SessionState.PAUSED
            }

            // Create pack-scoped lesson progress from legacy data
            val lessonProgressMap = mapOf(
                lessonId to PackLessonProgressState.LessonProgress(
                    currentIndex = currentIndex,
                    correctCount = correctCount,
                    incorrectCount = incorrectCount,
                    incorrectAttemptsForCard = incorrectAttemptsForCard,
                    activeTimeMs = activeTimeMs,
                    state = state
                )
            )

            val packProgress = PackLessonProgressState(
                packId = targetPackId,
                lessonProgress = lessonProgressMap
            )

            // Save to pack-specific file
            packProgressStore.savePackProgress(packProgress)
            Log.i("ProgressStore", "Migrated global lesson progress to pack: $targetPackId, lesson: $lessonId")

            // Clear legacy lesson progress fields from progress.yaml by reloading and saving
            // (the new save() method won't write these fields back)
            val progress = load()
            save(progress)
            Log.i("ProgressStore", "Cleared global lesson progress from progress.yaml after migration")

            return@withLock true
        } catch (e: Exception) {
            Log.e("ProgressStore", "Failed to migrate lesson progress to pack: $targetPackId", e)
            return@withLock false
        }
    }
}
