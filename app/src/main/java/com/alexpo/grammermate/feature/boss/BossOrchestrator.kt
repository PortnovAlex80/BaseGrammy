package com.alexpo.grammermate.feature.boss

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.DailySessionState
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.MasteryStore
import com.alexpo.grammermate.data.ProgressStore
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingMode
import com.alexpo.grammermate.data.TrainingProgress
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.SessionRunner

/**
 * Callback interface for cross-module orchestration from [BossOrchestrator].
 */
interface BossCallbacks {
    fun saveProgress()
    fun buildSessionCards()
    fun refreshFlowerStates()
    fun pauseTimer()
    fun resumeTimer()
}

/**
 * Boss battle and Mix Challenge orchestration helper.
 *
 * Coordinates [BossBattleRunner], [CardProvider], and [SessionRunner] for
 * boss battle lifecycle (start, progress, finish, clear) and Mix Challenge
 * session setup. Holds [TrainingStateAccess] for state reads/writes and
 * uses [BossCallbacks] for cross-module coordination.
 *
 * Does NOT duplicate [BossBattleRunner] pure logic -- this class only wires
 * the orchestration between modules.
 */
class BossOrchestrator(
    private val stateAccess: TrainingStateAccess,
    private val callbacks: BossCallbacks,
    private val bossBattleRunner: BossBattleRunner,
    private val cardProvider: CardProvider,
    private val sessionRunner: SessionRunner,
    private val progressStore: ProgressStore,
    private val masteryStore: MasteryStore
) {

    // ── Mix Challenge ──────────────────────────────────────────────────

    /**
     * Start a Mix Challenge (interleaved practice) session.
     *
     * Selects 10 cards from different lessons/tenses with maximum alternation,
     * then sets up a regular training session with those cards. Mastery tracking
     * and SRS intervals work identically to regular training.
     *
     * @return true if the session was started, false if not enough started lessons
     */
    fun startMixChallenge(): Boolean {
        val state = stateAccess.uiState.value
        val languageId = state.navigation.selectedLanguageId
        val lessons = state.navigation.lessons

        // Determine which lessons the user has started
        val startedIds = mutableSetOf<LessonId>()
        for (lesson in lessons) {
            val mastery = masteryStore.get(lesson.id.value, languageId.value)
            if (mastery != null && mastery.uniqueCardShows > 0) {
                startedIds.add(lesson.id)
            }
        }
        // Also include the first lesson even if not started yet
        if (startedIds.isEmpty() && lessons.isNotEmpty()) {
            startedIds.add(lessons.first().id)
        }

        val cards = cardProvider.buildMixChallengeCards(lessons, startedIds, count = 10)
        if (cards.isEmpty()) return false

        callbacks.pauseTimer()
        sessionRunner.clearAllCards()

        sessionRunner.setSessionCards(cards)

        val firstCard = cards.firstOrNull()
        stateAccess.updateState {
            it.resetSessionState().copy(
                navigation = it.navigation.copy(
                    mode = TrainingMode.MIX_CHALLENGE,
                    selectedLessonId = null
                ),
                cardSession = it.cardSession.copy(
                    currentCard = firstCard,
                    subLessonTotal = cards.size,
                    subLessonCount = 1
                ),
                daily = it.daily.copy(dailySession = DailySessionState())
            )
        }
        callbacks.saveProgress()
        return true
    }

    // ── Boss Battle Entry Points ───────────────────────────────────────

    fun startBossLesson() {
        startBoss(BossType.LESSON)
    }

    fun startBossMega() {
        startBoss(BossType.MEGA)
    }

    fun startBossElite() {
        startBoss(BossType.ELITE)
    }

    // ── Boss Battle Lifecycle ──────────────────────────────────────────

    private fun startBoss(type: BossType) {
        callbacks.pauseTimer()
        val state = stateAccess.uiState.value
        val lessons = state.navigation.lessons
        val selectedId = state.navigation.selectedLessonId
        val selectedIndex = lessons.indexOfFirst { it.id == selectedId }
        val cards = cardProvider.buildBossCards(lessons, type, selectedId, selectedIndex)
        val result = bossBattleRunner.startBoss(
            type = type,
            cards = cards,
            selectedLessonId = selectedId?.value,
            completedSubLessonCount = state.cardSession.completedSubLessonCount,
            testMode = state.cardSession.testMode
        )
        if (!result.success) {
            stateAccess.updateState {
                it.copy(boss = it.boss.copy(bossErrorMessage = result.errorMessage))
            }
            return
        }
        sessionRunner.setBossCards(result.cards)
        val firstCard = result.cards.firstOrNull()
        stateAccess.updateState {
            it.copy(
                boss = it.boss.copy(
                    bossActive = true,
                    bossType = type,
                    bossTotal = result.subLessonTotal,
                    bossProgress = 0,
                    bossReward = null,
                    bossRewardMessage = null,
                    bossErrorMessage = null
                ),
                cardSession = it.cardSession.copy(
                    currentIndex = 0,
                    currentCard = firstCard,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    incorrectAttemptsForCard = 0,
                    correctCount = 0,
                    incorrectCount = 0,
                    activeTimeMs = 0L,
                    voiceActiveMs = 0L,
                    voiceWordCount = 0,
                    hintCount = 0,
                    voicePromptStartMs = null,
                    sessionState = SessionState.PAUSED,
                    subLessonTotal = result.subLessonTotal,
                    subLessonCount = 1,
                    activeSubLessonIndex = 0,
                    completedSubLessonCount = 0
                )
            )
        }
    }

    fun finishBoss() {
        callbacks.pauseTimer()
        val state = stateAccess.uiState.value
        val result = bossBattleRunner.finishBoss(
            bossType = state.boss.bossType,
            bossProgress = state.boss.bossProgress,
            bossTotal = state.boss.bossTotal,
            selectedLessonId = state.navigation.selectedLessonId?.value,
            currentLessonRewards = state.boss.bossLessonRewards,
            currentMegaRewards = state.boss.bossMegaRewards
        )
        val progress = progressStore.load()
        val restoredLessonId = progress.lessonId?.let { LessonId(it) } ?: state.navigation.selectedLessonId
        sessionRunner.clearAllCards()
        stateAccess.updateState {
            it.copy(
                boss = it.boss.copy(
                    bossActive = false,
                    bossType = null,
                    bossTotal = 0,
                    bossProgress = 0,
                    bossReward = result.reward ?: it.boss.bossReward,
                    bossRewardMessage = it.boss.bossRewardMessage,
                    bossFinishedToken = it.boss.bossFinishedToken + 1,
                    bossLastType = state.boss.bossType,
                    bossErrorMessage = null,
                    bossLessonRewards = result.updatedLessonRewards,
                    bossMegaRewards = result.updatedMegaRewards
                ),
                navigation = it.navigation.copy(
                    selectedLessonId = restoredLessonId,
                    mode = progress.mode
                ),
                cardSession = it.cardSession.copy(
                    currentIndex = progress.currentIndex,
                    correctCount = progress.correctCount,
                    incorrectCount = progress.incorrectCount,
                    incorrectAttemptsForCard = progress.incorrectAttemptsForCard,
                    activeTimeMs = progress.activeTimeMs,
                    voiceActiveMs = progress.voiceActiveMs,
                    voiceWordCount = progress.voiceWordCount,
                    hintCount = progress.hintCount,
                    voicePromptStartMs = null,
                    inputText = "",
                    lastResult = null,
                    answerText = null,
                    sessionState = SessionState.PAUSED
                )
            )
        }
        callbacks.buildSessionCards()
        callbacks.saveProgress()
        callbacks.refreshFlowerStates()
    }

    fun clearBossRewardMessage() {
        val state = stateAccess.uiState.value
        val clearResult = bossBattleRunner.clearBossRewardMessage(
            bossActive = state.boss.bossActive,
            sessionState = state.cardSession.sessionState,
            currentCard = state.cardSession.currentCard,
            inputMode = state.cardSession.inputMode
        )
        if (clearResult.shouldResumeTimer) {
            callbacks.resumeTimer()
        }
        stateAccess.updateState {
            val trigger = if (clearResult.shouldTriggerVoice) {
                it.cardSession.voiceTriggerToken + 1
            } else {
                it.cardSession.voiceTriggerToken
            }
            it.copy(
                boss = it.boss.copy(bossRewardMessage = null),
                cardSession = it.cardSession.copy(
                    sessionState = if (clearResult.shouldResumeTimer) SessionState.ACTIVE else it.cardSession.sessionState,
                    voiceTriggerToken = trigger,
                    inputText = if (clearResult.shouldResumeTimer) "" else it.cardSession.inputText
                )
            )
        }
    }

    fun clearBossError() {
        stateAccess.updateState {
            it.copy(boss = it.boss.copy(bossErrorMessage = null))
        }
    }

    fun updateBossProgress(progress: Int) {
        val state = stateAccess.uiState.value
        val result = bossBattleRunner.updateBossProgress(
            progress = progress,
            currentTotal = state.boss.bossTotal,
            currentReward = state.boss.bossReward
        )
        if (result.shouldPause) {
            callbacks.pauseTimer()
        }
        stateAccess.updateState {
            it.copy(
                boss = it.boss.copy(
                    bossProgress = result.nextProgress,
                    bossReward = result.nextReward ?: it.boss.bossReward,
                    bossRewardMessage = result.rewardMessage ?: state.boss.bossRewardMessage
                ),
                cardSession = it.cardSession.copy(
                    sessionState = if (result.shouldPause) SessionState.PAUSED else it.cardSession.sessionState
                )
            )
        }
    }

    // ── Pure helper ────────────────────────────────────────────────────

    /**
     * Advance boss progress when the user moves to the next card.
     *
     * Computes the new progress and reward tier. If a new reward threshold is crossed,
     * pauses the session and sets the reward message. Returns true if a new reward
     * was reached (caller should pause the session after nextCard).
     *
     * @param nextIndex     The next card index the session will advance to.
     * @param totalCards    Total cards in the session.
     * @return Pair of (rewardMessageChanged, previousRewardMessage) so the caller
     *         can detect if the pause should be applied after sessionRunner.nextCard().
     */
    data class BossAdvanceResult(
        val rewardMessageChanged: Boolean,
        val previousRewardMessage: String?
    )

    fun advanceBossProgressOnNextCard(nextIndex: Int, totalCards: Int): BossAdvanceResult {
        val state = stateAccess.uiState.value
        val nextProgress = (state.boss.bossProgress.coerceAtLeast(nextIndex)).coerceAtMost(state.boss.bossTotal)
        val nextReward = bossBattleRunner.resolveBossReward(nextProgress, state.boss.bossTotal)
        val isNewReward = nextReward != null && nextReward != state.boss.bossReward
        val rewardMessage = if (isNewReward) {
            bossBattleRunner.bossRewardMessage(nextReward!!)
        } else {
            state.boss.bossRewardMessage
        }
        val previousRewardMessage = state.boss.bossRewardMessage
        if (isNewReward) {
            callbacks.pauseTimer()
        }
        stateAccess.updateState {
            it.copy(
                boss = it.boss.copy(
                    bossProgress = nextProgress,
                    bossReward = nextReward ?: it.boss.bossReward,
                    bossRewardMessage = rewardMessage
                )
            )
        }
        return BossAdvanceResult(
            rewardMessageChanged = rewardMessage != previousRewardMessage,
            previousRewardMessage = previousRewardMessage
        )
    }

    /**
     * Parse boss reward map from string-keyed progress store to typed [BossReward] map.
     */
    fun parseBossRewards(rewardMap: Map<String, String>): Map<String, BossReward> {
        return rewardMap.mapNotNull { (lessonId, reward) ->
            val parsed = runCatching { BossReward.valueOf(reward) }.getOrNull() ?: return@mapNotNull null
            lessonId to parsed
        }.toMap()
    }
}
