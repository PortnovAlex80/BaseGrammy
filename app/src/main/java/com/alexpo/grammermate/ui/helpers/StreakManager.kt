package com.alexpo.grammermate.ui.helpers

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
     * Generate a celebration message for the given streak count, or null if
     * no special milestone is reached.
     */
    fun getCelebrationMessage(streakCount: Int): String? {
        return when {
            streakCount == 1 -> "🔥 Great start! Day 1 streak!"
            streakCount == 3 -> "🔥 3 days streak! You're on fire!"
            streakCount == 7 -> "🔥 7 days streak! One week! Amazing!"
            streakCount == 14 -> "🔥 14 days streak! Two weeks! Incredible!"
            streakCount == 30 -> "🔥 30 days streak! One month! Outstanding!"
            streakCount == 100 -> "🔥 100 days streak! You're a legend!"
            streakCount % 10 == 0 -> "🔥 $streakCount days streak! Keep it up!"
            else -> "🔥 $streakCount days streak!"
        }
    }
}
