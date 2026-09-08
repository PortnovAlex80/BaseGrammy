package com.alexpo.grammermate.feature.progress

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.ScheduledSubLesson

/**
 * Result types for [ProgressRestorer] methods.
 * Replaces [ProgressCallbacks] — each method returns a list of results
 * instead of calling callbacks.
 *
 * Query-style callbacks that need return values use the callback-in-result
 * pattern (e.g. [NormalizeEliteSpeeds] with callback).
 */
sealed class ProgressResult {
    object None : ProgressResult()
    data class RebuildSchedules(val lessons: List<Lesson>) : ProgressResult()
    object BuildSessionCards : ProgressResult()
    object RefreshFlowerStates : ProgressResult()
    data class NormalizeEliteSpeeds(val speeds: List<Double>, val callback: (List<Double>) -> Unit) : ProgressResult()
    data class ResolveEliteUnlocked(val lessons: List<Lesson>, val testMode: Boolean, val callback: (Boolean) -> Unit) : ProgressResult()
    data class ParseBossRewards(val rewardMap: Map<String, String>, val callback: (Map<String, BossReward>) -> Unit) : ProgressResult()
}
