package com.alexpo.grammermate.data.validation

import android.util.Log
import com.alexpo.grammermate.data.*

/** Validates persisted streakdata data at its domain boundary. */
object StreakDataValidator {
    private const val TAG = "DataValidator"

    /**
     * Validate StreakData.
     */
    fun validateStreakData(languageId: String, data: Map<String, Any>?): ValidationResult<StreakData> {
        if (data == null) {
            Log.w(TAG, "Missing streak data for language=$languageId - using default")
            val defaultStreak = createDefaultStreakData(languageId)
            return ValidationResult.warning(
                defaultStreak,
                ValidationWarning(
                    field = "streak",
                    message = "Streak data missing for language=$languageId. Using default streak."
                )
            )
        }

        val errors = mutableListOf<ValidationError>()

        // Validate currentStreak
        val currentStreak = (data["currentStreak"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val longestStreak = (data["longestStreak"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        if (currentStreak > longestStreak) {
            Log.w(TAG, "currentStreak ($currentStreak) > longestStreak ($longestStreak)")
        }

        // Validate lastCompletionDateMs
        val lastCompletionDateMs = (data["lastCompletionDateMs"] as? Number)?.toLong()
        if (lastCompletionDateMs != null && lastCompletionDateMs < 0) {
            errors.add(ValidationError(
                field = "lastCompletionDateMs",
                message = "Negative last completion timestamp: $lastCompletionDateMs"
            ))
        }
        if (lastCompletionDateMs != null && lastCompletionDateMs > System.currentTimeMillis() + 86400000L) {
            Log.w(TAG, "Last completion timestamp in the future: $lastCompletionDateMs")
        }

        // Validate totalSubLessonsCompleted
        val totalSubLessonsCompleted = (data["totalSubLessonsCompleted"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate completedTypesToday
        val completedTypesToday = (data["completedTypesToday"] as? List<*>)
            ?.mapNotNull { it as? String }
            ?.mapNotNull { type ->
                try { PracticeType.valueOf(type) } catch (e: Exception) { null }
            }
            ?.toSet()
            ?: emptySet()

        // Validate todayFireCount
        val todayFireCount = (data["todayFireCount"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        // Validate lastFireDateMs
        val lastFireDateMs = (data["lastFireDateMs"] as? Number)?.toLong()
        if (lastFireDateMs != null && lastFireDateMs < 0) {
            errors.add(ValidationError(
                field = "lastFireDateMs",
                message = "Negative last fire timestamp: $lastFireDateMs"
            ))
        }

        return if (errors.isNotEmpty()) {
            Log.w(TAG, "Streak data validation failed for language=$languageId: ${errors.joinToString()}")
            ValidationResult.invalid(errors, createDefaultStreakData(languageId))
        } else {
            ValidationResult.valid(
                StreakData(
                    languageId = LanguageId(languageId),
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    lastCompletionDateMs = lastCompletionDateMs?.coerceIn(0, System.currentTimeMillis() + 86400000L),
                    totalSubLessonsCompleted = totalSubLessonsCompleted,
                    completedTypesToday = completedTypesToday,
                    todayFireCount = todayFireCount,
                    lastFireDateMs = lastFireDateMs?.coerceIn(0, System.currentTimeMillis() + 86400000L)
                )
            )
        }
    }

    private fun createDefaultStreakData(languageId: String) = StreakData(
        languageId = LanguageId(languageId)
    )
}
