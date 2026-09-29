package com.alexpo.grammermate.feature.daily

import com.alexpo.grammermate.data.DailyBlockType
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.StreakData
import com.alexpo.grammermate.data.StreakStore
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.shared.TrainingStateAccess

/** Records each completed daily block's fire and publishes the persisted streak to shared UI state. */
internal class DailyStreakCompletion(
    private val stateAccess: TrainingStateAccess,
    private val streakStore: StreakStore,
    private val streakManager: StreakManager
) {
    fun record(languageId: String, blockTypes: Set<DailyBlockType>): Boolean {
        var lastStreak: StreakData? = null
        var anyNewFire = false
        for (blockType in blockTypes) {
            val practiceType = when (blockType) {
                DailyBlockType.TRANSLATE -> PracticeType.TRANSLATION
                DailyBlockType.VOCAB -> PracticeType.VOCAB
                DailyBlockType.VERBS -> PracticeType.VERB
            }
            val (updated, isNewFire) = streakStore.recordPracticeTypeCompletion(languageId, practiceType)
            lastStreak = updated
            anyNewFire = anyNewFire || isNewFire
        }

        val snapshot = lastStreak ?: return anyNewFire
        val message = if (anyNewFire && snapshot.currentStreak > 0) {
            streakManager.getCelebrationMessage(snapshot.currentStreak, snapshot.todayFireCount)
        } else null
        stateAccess.updateState { state ->
            state.copy(cardSession = state.cardSession.copy(
                currentStreak = snapshot.currentStreak,
                longestStreak = snapshot.longestStreak,
                todayFireCount = snapshot.todayFireCount,
                streakMessage = message,
                streakCelebrationToken = state.cardSession.streakCelebrationToken + if (message != null) 1 else 0
            ))
        }
        return anyNewFire
    }
}
