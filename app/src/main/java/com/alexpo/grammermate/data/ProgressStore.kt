package com.alexpo.grammermate.data

import android.content.Context
import android.util.Log
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.alexpo.grammermate.data.validation.DataValidator

interface ProgressStore {

    fun load(): TrainingProgress

    fun exists(): Boolean

    fun save(progress: TrainingProgress)

    fun clear()
}

class ProgressStoreImpl(private val context: Context) : ProgressStore {
    private val yaml = Yaml()
    private val baseDir = File(context.filesDir, "grammarmate")
    private val file = File(baseDir, "progress.yaml")
    private val schemaVersion = 1
    private val mutex = ReentrantLock()

    override fun exists(): Boolean = mutex.withLock { file.exists() && file.length() > 0L }

    override fun load(): TrainingProgress = mutex.withLock {
        if (!file.exists() || file.length() == 0L) return TrainingProgress()
        val raw = try { yaml.load<Any>(file.readText()) } catch (_: Exception) { null } ?: return TrainingProgress()
        val data = when (raw) {
            is Map<*, *> -> raw
            else -> return TrainingProgress()
        }
        val payload = (data["data"] as? Map<*, *>) ?: data

        // Validate training progress before using it
        @Suppress("UNCHECKED_CAST")
        val validationResult = DataValidator.validateTrainingProgress(payload as? Map<String, Any>)

        return when (validationResult) {
            is com.alexpo.grammermate.data.validation.ValidationResult.Valid -> validationResult.data
            is com.alexpo.grammermate.data.validation.ValidationResult.Invalid -> {
                Log.w("ProgressStore", "Using safe default for corrupted progress data")
                validationResult.safeDefault
            }
            is com.alexpo.grammermate.data.validation.ValidationResult.Warning -> validationResult.data
        }
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
}
