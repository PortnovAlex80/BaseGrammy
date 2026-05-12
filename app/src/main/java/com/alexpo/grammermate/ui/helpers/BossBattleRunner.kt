package com.alexpo.grammermate.ui.helpers

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.SentenceCard

/**
 * Pure-logic module for boss battle session lifecycle.
 *
 * Manages card validation, progress tracking, reward thresholds,
 * and session teardown. Returns result objects for the ViewModel
 * to apply to state. Does NOT hold [TrainingStateAccess] --
 * all state changes are returned as data classes.
 *
 * Reward thresholds:
 * - 30% progress -> Bronze
 * - 60% progress -> Silver
 * - 90% progress -> Gold
 *
 * No Android dependencies -- suitable for unit testing without Robolectric.
 */
class BossBattleRunner {

    // ── Result data classes ──────────────────────────────────────────

    /**
     * Result of attempting to start a boss battle.
     */
    data class BossStartResult(
        val success: Boolean,
        val cards: List<SentenceCard>,
        val errorMessage: String? = null,
        val subLessonTotal: Int = 0,
        val subLessonCount: Int = 1
    )

    /**
     * Updated boss progress after card advancement or answer submission.
     * The ViewModel applies these values to state.
     */
    data class BossProgressUpdate(
        val nextProgress: Int,
        val nextReward: BossReward?,
        val rewardMessage: String?,
        val isNewReward: Boolean,
        val shouldPause: Boolean
    )

    /**
     * Result of finishing a boss battle.
     * Contains the final reward and updated reward maps.
     */
    data class BossFinishResult(
        val reward: BossReward?,
        val updatedLessonRewards: Map<String, BossReward>,
        val updatedMegaRewards: Map<String, BossReward>
    )

    /**
     * Result of clearing the boss reward message.
     * Tells the ViewModel whether to resume timer and voice.
     */
    data class BossRewardClearResult(
        val shouldResumeTimer: Boolean,
        val shouldTriggerVoice: Boolean
    )

    // ── Session start ───────────────────────────────────────────────

    /**
     * Validate and start a boss battle session.
     *
     * Receives pre-built cards from [CardProvider.buildBossCards] and validates
     * eligibility based on [completedSubLessonCount], [selectedLessonId], and
     * card availability. Returns a [BossStartResult] for the ViewModel to apply.
     *
     * @param type                     boss type (LESSON, MEGA, ELITE)
     * @param cards                    pre-built card pool from CardProvider
     * @param selectedLessonId         currently selected lesson (required for LESSON/MEGA)
     * @param completedSubLessonCount  completed sub-lessons for unlock check
     * @param testMode                 skips the 15-sublesson unlock requirement
     */
    fun startBoss(
        type: BossType,
        cards: List<SentenceCard>,
        selectedLessonId: String?,
        completedSubLessonCount: Int,
        testMode: Boolean
    ): BossStartResult {
        // Unlock guard: require at least 15 completed sub-lessons (unless test mode or elite)
        if (type != BossType.ELITE && completedSubLessonCount < 15 && !testMode) {
            return BossStartResult(
                success = false,
                cards = emptyList(),
                errorMessage = "Complete at least 15 exercises first"
            )
        }

        // Lesson must be selected for LESSON and MEGA types
        if (type != BossType.ELITE && selectedLessonId == null) {
            return BossStartResult(
                success = false,
                cards = emptyList(),
                errorMessage = "Lesson not selected"
            )
        }

        // Card pool must not be empty
        if (cards.isEmpty()) {
            val message = when (type) {
                BossType.MEGA -> "Mega boss is available after the first lesson"
                BossType.ELITE -> "Elite boss has no cards"
                BossType.LESSON -> "Boss has no cards"
            }
            return BossStartResult(
                success = false,
                cards = emptyList(),
                errorMessage = message
            )
        }

        return BossStartResult(
            success = true,
            cards = cards,
            subLessonTotal = cards.size,
            subLessonCount = 1
        )
    }

    // ── Progress tracking ───────────────────────────────────────────

    /**
     * Calculate updated boss progress after card advancement.
     *
     * Determines the new reward tier, whether a new threshold was crossed,
     * and whether the session should pause for a reward notification.
     *
     * @param progress       new raw progress value (card index)
     * @param currentTotal   total cards in the boss session
     * @param currentReward  the reward tier before this update (nullable)
     * @return [BossProgressUpdate] with all computed values
     */
    fun updateBossProgress(
        progress: Int,
        currentTotal: Int,
        currentReward: BossReward?
    ): BossProgressUpdate {
        val nextProgress = progress.coerceAtMost(currentTotal)
        val nextReward = resolveBossReward(nextProgress, currentTotal)
        val isNewReward = nextReward != null && nextReward != currentReward
        val message = if (isNewReward) {
            bossRewardMessage(nextReward!!)
        } else {
            null
        }
        return BossProgressUpdate(
            nextProgress = nextProgress,
            nextReward = nextReward,
            rewardMessage = message,
            isNewReward = isNewReward,
            shouldPause = isNewReward
        )
    }

    // ── Session finish ──────────────────────────────────────────────

    /**
     * Finish the boss battle and compute final results.
     *
     * Calculates the final reward tier based on progress, then updates
     * the lesson and mega reward maps depending on boss type.
     *
     * @param bossType             the type of boss battle finishing
     * @param bossProgress         final progress value
     * @param bossTotal            total cards in the boss session
     * @param selectedLessonId     currently selected lesson
     * @param currentLessonRewards existing lesson reward map
     * @param currentMegaRewards   existing mega reward map
     * @return [BossFinishResult] with final reward and updated maps
     */
    fun finishBoss(
        bossType: BossType?,
        bossProgress: Int,
        bossTotal: Int,
        selectedLessonId: String?,
        currentLessonRewards: Map<String, BossReward>,
        currentMegaRewards: Map<String, BossReward>
    ): BossFinishResult {
        val reward = resolveBossReward(bossProgress, bossTotal)

        val updatedLessonRewards = if (bossType == BossType.LESSON) {
            if (selectedLessonId != null && reward != null) {
                currentLessonRewards + (selectedLessonId to reward)
            } else {
                currentLessonRewards
            }
        } else {
            currentLessonRewards
        }

        val updatedMegaRewards = if (bossType == BossType.MEGA && reward != null) {
            if (selectedLessonId != null) {
                currentMegaRewards + (selectedLessonId to reward)
            } else {
                currentMegaRewards
            }
        } else {
            currentMegaRewards
        }

        return BossFinishResult(
            reward = reward,
            updatedLessonRewards = updatedLessonRewards,
            updatedMegaRewards = updatedMegaRewards
        )
    }

    // ── Reward message ──────────────────────────────────────────────

    /**
     * Determine whether the timer should resume and voice should trigger
     * after clearing the boss reward message.
     *
     * @param bossActive    whether a boss session is still active
     * @param sessionState  current session state
     * @param currentCard   the current card (must be non-null to resume)
     * @param inputMode     current input mode (voice trigger only if VOICE)
     */
    fun clearBossRewardMessage(
        bossActive: Boolean,
        sessionState: com.alexpo.grammermate.data.SessionState,
        currentCard: SentenceCard?,
        inputMode: com.alexpo.grammermate.data.InputMode
    ): BossRewardClearResult {
        val shouldResume = bossActive &&
            sessionState == com.alexpo.grammermate.data.SessionState.PAUSED &&
            currentCard != null
        val shouldTriggerVoice = shouldResume && inputMode == com.alexpo.grammermate.data.InputMode.VOICE
        return BossRewardClearResult(
            shouldResumeTimer = shouldResume,
            shouldTriggerVoice = shouldTriggerVoice
        )
    }

    // ── Pure functions ──────────────────────────────────────────────

    /**
     * Resolve the reward tier based on progress percentage.
     *
     * Thresholds:
     * - 90% -> Gold
     * - 60% -> Silver
     * - 30% -> Bronze
     * - Below 30% -> null (no reward yet)
     */
    fun resolveBossReward(progress: Int, total: Int): BossReward? {
        if (total <= 0) return null
        val percent = (progress.toDouble() / total.toDouble()) * 100.0
        return when {
            percent >= 90.0 -> BossReward.GOLD
            percent >= 60.0 -> BossReward.SILVER
            percent >= 30.0 -> BossReward.BRONZE
            else -> null
        }
    }

    /**
     * Generate a human-readable reward threshold message.
     */
    fun bossRewardMessage(reward: BossReward): String {
        return when (reward) {
            BossReward.BRONZE -> "Bronze reached"
            BossReward.SILVER -> "Silver reached"
            BossReward.GOLD -> "Gold reached"
        }
    }
}
