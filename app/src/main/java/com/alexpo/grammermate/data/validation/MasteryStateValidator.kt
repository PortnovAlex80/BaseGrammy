package com.alexpo.grammermate.data.validation

import android.util.Log
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState

/** Validates persisted lesson mastery before MasteryStore accepts it. */
internal object MasteryStateValidator {
    private const val TAG = "DataValidator"
    private const val MAX_MASTERY_STEP = 150

    /**
     * Validate LessonMasteryState data.
     */
    fun validateMasteryState(
        lessonId: String,
        languageId: String,
        data: Map<String, Any>?
    ): ValidationResult<LessonMasteryState> {
        if (data == null) {
            Log.w(TAG, "Missing mastery data for lesson=$lessonId, language=$languageId - using default state")
            val defaultState = createDefaultMasteryState(lessonId, languageId)
            return ValidationResult.warning(
                defaultState,
                ValidationWarning(
                    field = "mastery",
                    message = "Missing mastery data for lesson=$lessonId, language=$languageId. Using default state."
                )
            )
        }

        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<ValidationWarning>()

        // Validate uniqueCardShows
        val uniqueCardShows = (data["uniqueCardShows"] as? Number)?.toInt() ?: 0
        if (uniqueCardShows < 0) {
            errors.add(ValidationError(
                field = "uniqueCardShows",
                message = "Negative uniqueCardShows: $uniqueCardShows",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }
        if (uniqueCardShows > Lesson.MAIN_POOL_SIZE) {
            warnings.add(ValidationWarning(
                field = "uniqueCardShows",
                message = "uniqueCardShows ($uniqueCardShows) exceeds main pool size (${Lesson.MAIN_POOL_SIZE})"
            ))
        }

        // Validate totalCardShows
        val totalCardShows = (data["totalCardShows"] as? Number)?.toInt() ?: 0
        if (totalCardShows < 0) {
            errors.add(ValidationError(
                field = "totalCardShows",
                message = "Negative totalCardShows: $totalCardShows",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }
        if (totalCardShows < uniqueCardShows) {
            errors.add(ValidationError(
                field = "totalCardShows",
                message = "totalCardShows ($totalCardShows) < uniqueCardShows ($uniqueCardShows)"
            ))
        }

        // Validate lastShowDateMs
        val lastShowDateMs = (data["lastShowDateMs"] as? Number)?.toLong() ?: 0L
        if (lastShowDateMs < 0) {
            errors.add(ValidationError(
                field = "lastShowDateMs",
                message = "Negative timestamp: $lastShowDateMs"
            ))
        }
        if (lastShowDateMs > System.currentTimeMillis() + 86400000L) {
            warnings.add(ValidationWarning(
                field = "lastShowDateMs",
                message = "Timestamp in the future: $lastShowDateMs"
            ))
        }

        // Validate intervalStepIndex
        val intervalStepIndex = (data["intervalStepIndex"] as? Number)?.toInt() ?: 0
        if (intervalStepIndex < 0 || intervalStepIndex > MAX_MASTERY_STEP) {
            errors.add(ValidationError(
                field = "intervalStepIndex",
                message = "intervalStepIndex out of range [0, $MAX_MASTERY_STEP]: $intervalStepIndex",
                userMessage = "Invalid progress data. Progress has been reset."
            ))
        }

        // Forgetting-curve fields. Both are optional and default to 0 — a lesson
        // with lastReviewMs == 0 has never been self-produced and is excluded
        // from the review pool by ReviewSelector.
        val lastReviewMs = ((data["lastReviewMs"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
        val effortAtLastReview = ((data["effortAtLastReview"] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

        // Validate completedAtMs — treat 0 as null (legacy data stored null as 0)
        val completedAtMsRaw = (data["completedAtMs"] as? Number)?.toLong()
        val completedAtMs = if (completedAtMsRaw != null && completedAtMsRaw > 0) completedAtMsRaw else null
        if (completedAtMsRaw != null && completedAtMsRaw < 0) {
            errors.add(ValidationError(
                field = "completedAtMs",
                message = "Negative completion timestamp: $completedAtMsRaw"
            ))
        }
        if (completedAtMs != null && completedAtMs > System.currentTimeMillis() + 86400000L) {
            warnings.add(ValidationWarning(
                field = "completedAtMs",
                message = "Completion timestamp in the future: $completedAtMs"
            ))
        }

        // Validate shownCardIds
        val shownCardIds = (data["shownCardIds"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.toSet()
            ?: emptySet()
        if (shownCardIds.size != uniqueCardShows && uniqueCardShows > 0) {
            warnings.add(ValidationWarning(
                field = "shownCardIds",
                message = "shownCardIds count (${shownCardIds.size}) != uniqueCardShows ($uniqueCardShows)"
            ))
        }

        // Validate cardEncounterCounts
        val cardEncounterCounts = (data["cardEncounterCounts"] as? Map<*, *>)
            ?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toInt()
                if (value == null || value < 0) return@mapNotNull null
                key to value
            }
            ?.toMap()
            ?: emptyMap()

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Mastery data validation failed for lesson=$lessonId: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultMasteryState(lessonId, languageId))
        } else if (warnings.isNotEmpty()) {
            warnings.forEach { Log.w(TAG, "Mastery data warning: ${it.message}") }
            ValidationResult.warning(
                LessonMasteryState(
                    lessonId = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    uniqueCardShows = uniqueCardShows.coerceIn(0, Lesson.MAIN_POOL_SIZE),
                    totalCardShows = totalCardShows.coerceAtLeast(0),
                    lastShowDateMs = lastShowDateMs.coerceAtMost(System.currentTimeMillis() + 86400000L),
                    intervalStepIndex = intervalStepIndex.coerceIn(0, MAX_MASTERY_STEP),
                    completedAtMs = completedAtMs?.coerceIn(0, System.currentTimeMillis() + 86400000L),
                    shownCardIds = shownCardIds,
                    cardEncounterCounts = cardEncounterCounts,
                    lastReviewMs = lastReviewMs,
                    effortAtLastReview = effortAtLastReview
                ),
                warnings
            )
        } else {
            ValidationResult.valid(
                LessonMasteryState(
                    lessonId = LessonId(lessonId),
                    languageId = LanguageId(languageId),
                    uniqueCardShows = uniqueCardShows,
                    totalCardShows = totalCardShows,
                    lastShowDateMs = lastShowDateMs,
                    intervalStepIndex = intervalStepIndex,
                    completedAtMs = completedAtMs,
                    shownCardIds = shownCardIds,
                    cardEncounterCounts = cardEncounterCounts,
                    lastReviewMs = lastReviewMs,
                    effortAtLastReview = effortAtLastReview
                )
            )
        }
    }

    private fun createDefaultMasteryState(lessonId: String, languageId: String) = LessonMasteryState(
        lessonId = LessonId(lessonId),
        languageId = LanguageId(languageId)
    )
}
