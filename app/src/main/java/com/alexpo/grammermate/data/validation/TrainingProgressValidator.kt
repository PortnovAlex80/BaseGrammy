package com.alexpo.grammermate.data.validation

import android.util.Log
import com.alexpo.grammermate.data.*

/** Validates persisted trainingprogress data at its domain boundary. */
object TrainingProgressValidator {
    private const val TAG = "DataValidator"

    /**
     * Validate TrainingProgress data.
     */
    fun validateTrainingProgress(data: Map<String, Any>?): ValidationResult<TrainingProgress> {
        if (data == null) {
            Log.w(TAG, "Missing training progress data")
            return ValidationResult.valid(TrainingProgress())
        }

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate languageId
        val languageId = (data["languageId"] as? String)?.takeIf { it.isNotBlank() } ?: "en"
        if (languageId.length > 10) {
            warnings.add(ValidationWarning(
                field = "languageId",
                message = "Unusually long languageId: $languageId"
            ))
        }

        // Validate mode
        val mode = try {
            TrainingMode.valueOf(data["mode"] as? String ?: TrainingMode.LESSON.name)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid training mode, using LESSON")
            TrainingMode.LESSON
        }

        // Validate boss rewards
        val bossLessonRewards = (data["bossLessonRewards"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap() ?: emptyMap()

        val bossMegaReward = data["bossMegaReward"] as? String

        val bossMegaRewards = (data["bossMegaRewards"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }
            ?.toMap() ?: emptyMap()

        // Validate voice stats
        val voiceActiveMs = (data["voiceActiveMs"] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
        val voiceWordCount = (data["voiceWordCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate hint count
        val hintCount = (data["hintCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate elite stats
        val eliteStepIndex = (data["eliteStepIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val eliteBestSpeeds = (data["eliteBestSpeeds"] as? List<*>)
            ?.mapNotNull { it as? Number }
            ?.map { it.toDouble() }
            ?.filter { it >= 0 }
            ?: emptyList()

        // Validate current screen
        val currentScreen = (data["currentScreen"] as? String)?.takeIf { it.isNotBlank() } ?: "HOME"

        // Validate packId
        val activePackId = (data["activePackId"] as? String)?.takeIf { it.isNotBlank() }?.let { PackId(it) }
        val lessonId = (data["lessonId"] as? String)?.takeIf { it.isNotBlank() }

        // Validate daily level
        val dailyLevel = (data["dailyLevel"] as? Number)?.toInt()?.coerceIn(0, 100) ?: 0
        val dailyTaskIndex = (data["dailyTaskIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate daily cursor
        val dailyCursor = validateDailyCursor(data["dailyCursor"] as? Map<*, *>)

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Training progress validation failed: ${errors.joinToString()}")
            ValidationResult.invalid(errors, TrainingProgress())
        } else if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Training progress warning: ${it.message}") }
            ValidationResult.warning(
                TrainingProgress(
                    languageId = LanguageId(languageId),
                    mode = mode,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaReward = bossMegaReward,
                    bossMegaRewards = bossMegaRewards,
                    voiceActiveMs = voiceActiveMs,
                    voiceWordCount = voiceWordCount,
                    hintCount = hintCount,
                    eliteStepIndex = eliteStepIndex,
                    eliteBestSpeeds = eliteBestSpeeds,
                    currentScreen = currentScreen,
                    activePackId = activePackId,
                    lessonId = lessonId,
                    dailyLevel = dailyLevel,
                    dailyTaskIndex = dailyTaskIndex,
                    dailyCursor = dailyCursor
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                TrainingProgress(
                    languageId = LanguageId(languageId),
                    mode = mode,
                    bossLessonRewards = bossLessonRewards,
                    bossMegaReward = bossMegaReward,
                    bossMegaRewards = bossMegaRewards,
                    voiceActiveMs = voiceActiveMs,
                    voiceWordCount = voiceWordCount,
                    hintCount = hintCount,
                    eliteStepIndex = eliteStepIndex,
                    eliteBestSpeeds = eliteBestSpeeds,
                    currentScreen = currentScreen,
                    activePackId = activePackId,
                    lessonId = lessonId,
                    dailyLevel = dailyLevel,
                    dailyTaskIndex = dailyTaskIndex,
                    dailyCursor = dailyCursor
                )
            )
        }
    }

    private fun validateDailyCursor(data: Map<*, *>?): DailyCursorState {
        if (data == null) return DailyCursorState()

        val sentenceOffset = (data["sentenceOffset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val currentLessonIndex = (data["currentLessonIndex"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val lastSessionHash = (data["lastSessionHash"] as? Number)?.toInt() ?: 0
        val firstSessionDate = validateIsoDate(data["firstSessionDate"] as? String)
        val firstSessionSentenceCardIds = (data["firstSessionSentenceCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val firstSessionVerbCardIds = (data["firstSessionVerbCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val verbOffset = (data["verbOffset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        return DailyCursorState(
            sentenceOffset = sentenceOffset,
            currentLessonIndex = currentLessonIndex,
            lastSessionHash = lastSessionHash,
            firstSessionDate = firstSessionDate,
            firstSessionSentenceCardIds = firstSessionSentenceCardIds,
            firstSessionVerbCardIds = firstSessionVerbCardIds,
            verbOffset = verbOffset
        )
    }
}
