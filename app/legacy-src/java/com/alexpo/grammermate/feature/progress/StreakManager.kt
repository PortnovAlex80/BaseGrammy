package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore

/**
 * Higher-level streak management module.
 *
 * Wraps [StreakStore] with message generation for streak milestones.
 * Pure extraction of streak update logic from TrainingViewModel.
 */
class StreakManager(
    private val streakStore: StreakStore
) {

    /**
     * Records a sub-lesson completion for the given language.
     *
     * Handles:
     * - First-time activity (streak becomes 1)
     * - Same-day duplicate (streak unchanged)
     * - Consecutive day (streak incremented)
     * - Gap of 2+ days (streak reset per StreakStore behavior)
     *
     * @return updated [StreakData] and a flag indicating whether this is a
     *         streak-worthy event (i.e. should trigger a celebration).
     */
    fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> {
        return streakStore.recordSubLessonCompletion(languageId)
    }

    /**
     * Records completion of a specific practice type for fire streak tracking.
     *
     * @return updated [StreakData] and a flag indicating whether a new fire was earned.
     */
    fun recordPracticeTypeCompletion(languageId: String, type: PracticeType): Pair<StreakData, Boolean> {
        return streakStore.recordPracticeTypeCompletion(languageId, type)
    }

    /**
     * Generate a celebration message for the given streak count and fire count, or null if
     * no special milestone is reached.
     *
     * @param streakCount current streak in days
     * @param fireCount number of fires earned today
     */
    fun getCelebrationMessage(streakCount: Int, fireCount: Int = 1): String? {
        val fireEmoji = "🔥".repeat(fireCount.coerceIn(1, 4))
        return when {
            fireCount >= 3 -> "$fireEmoji Perfect day! All practice types completed! $streakCount day streak!"
            fireCount == 2 -> "$fireEmoji Great variety! $streakCount day streak!"
            streakCount == 1 -> "$fireEmoji Great start! Day 1!"
            streakCount == 7 -> "$fireEmoji One week streak! Amazing!"
            streakCount == 30 -> "$fireEmoji One month streak! Outstanding!"
            streakCount == 100 -> "$fireEmoji 100 days! Legend!"
            streakCount % 10 == 0 && streakCount > 0 -> "$fireEmoji $streakCount day streak! Keep it up!"
            else -> "$fireEmoji $streakCount day streak!"
        }
    }
}
