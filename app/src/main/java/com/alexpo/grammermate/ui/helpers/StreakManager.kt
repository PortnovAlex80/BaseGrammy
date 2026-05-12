package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Higher-level streak management module.
 *
 * Wraps [StreakStore] with consistent gap-detection and reset logic.
 * The key inconsistency it fixes: [StreakStore.recordSubLessonCompletion] resets
 * a broken streak to **1**, while [StreakStore.getCurrentStreak] resets it to **0**.
 * This manager aligns both paths to reset to **1** so the UI never shows a
 * stale 0-day streak for a user who was active today.
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
     * - Gap of 2+ days (streak reset to 1)
     *
     * @return updated [StreakData] and a flag indicating whether this is a
     *         streak-worthy event (i.e. should trigger a celebration).
     */
    fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> {
        // Before recording, apply consistent gap detection so the stored value
        // is never left at 0 from a previous getCurrentStreak call.
        applyConsistentGapReset(languageId)

        return streakStore.recordSubLessonCompletion(languageId)
    }

    /**
     * Returns the current streak data for [languageId], applying consistent
     * reset behaviour (gap > 1 day resets to 1 instead of 0).
     */
    fun getCurrentStreak(languageId: String): Int {
        val data = streakStore.load(languageId)
        val lastMs = data.lastCompletionDateMs ?: return 0

        if (isGapExceeded(lastMs)) {
            // Consistent reset to 1 (not 0)
            if (data.currentStreak != 1) {
                streakStore.save(data.copy(currentStreak = 1))
            }
            return 1
        }

        return data.currentStreak
    }

    /**
     * Whether a streak celebration dialog should be shown for [languageId].
     *
     * A celebration is warranted when the user just set or extended a streak
     * (streak >= 1) and has not already seen the dialog today.
     * Callers typically gate this with their own "already shown" flag.
     */
    fun shouldShowStreakDialog(languageId: String): Boolean {
        val streak = getCurrentStreak(languageId)
        return streak >= STREAK_DIALOG_THRESHOLD
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Ensures the stored streak is reset to 1 (not 0) when a gap is detected.
     * This bridges the inconsistency between [StreakStore.recordSubLessonCompletion]
     * (resets to 1) and [StreakStore.getCurrentStreak] (resets to 0).
     */
    private fun applyConsistentGapReset(languageId: String) {
        val data = streakStore.load(languageId)
        val lastMs = data.lastCompletionDateMs ?: return

        if (isGapExceeded(lastMs) && data.currentStreak != CONSISTENT_RESET_VALUE) {
            streakStore.save(data.copy(currentStreak = CONSISTENT_RESET_VALUE))
        }
    }

    /**
     * Returns true if more than one calendar day has passed since [lastMs].
     */
    private fun isGapExceeded(lastMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastMs }
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }

        // Same day — no gap
        if (lastCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            lastCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        ) {
            return false
        }

        // Check if last activity was yesterday
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val wasYesterday = lastCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            lastCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

        // Gap exceeded only if last activity was NOT yesterday (and not today, already handled)
        return !wasYesterday
    }

    companion object {
        /** The consistent value to reset a broken streak to. */
        private const val CONSISTENT_RESET_VALUE = 1

        /** Minimum streak length that warrants a celebration dialog. */
        private const val STREAK_DIALOG_THRESHOLD = 1
    }
}
