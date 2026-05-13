package com.alexpo.grammermate.feature.boss

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossState
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Boss battle and Mix Challenge orchestration helper.
 *
 * Coordinates [BossBattleRunner], [CardProvider], and [SessionRunner] for
 * boss battle lifecycle (start, progress, finish, clear) and Mix Challenge
 * session setup. Holds [TrainingStateAccess] for state reads/writes and
 * returns [BossCommand] lists for cross-module coordination.
 *
 * Does NOT duplicate [BossBattleRunner] pure logic -- this class only wires
 * the orchestration between modules.
 */
class BossOrchestrator(
    private val stateAccess: TrainingStateAccess,
    private val bossBattleRunner: BossBattleRunner,
    private val cardProvider: CardProvider,
    private val sessionRunner: SessionRunner,
    private val progressStore: ProgressStore,
    private val masteryStore: MasteryStore
) {

    // ── Owned state flow ─────────────────────────────────────────────────
    private val _state = MutableStateFlow(BossState())
    val stateFlow: StateFlow<BossState> = _state

    // ── Mix Challenge ──────────────────────────────────────────────────

    /**
     * Start a Mix Challenge (interleaved practice) session.
     *
     * Selects 10 cards from different lessons/tenses with maximum alternation,
     * then sets up a regular training session with those cards. Mastery tracking
     * and SRS intervals work identically to regular training.
     *
     * @return Pair of (success, commands). Commands should be executed by the ViewModel.
     */
    fun startMixChallenge(): Pair<Boolean, List<BossCommand>> {
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
        if (cards.isEmpty()) return false to emptyList()

        sessionRunner.clearAllCards()

        sessionRunner.setSessionCards(cards)

        val firstCard = cards.firstOrNull()
        // Reset boss state (owned by this orchestrator)
        _state.update { BossState() }
        // Write core state: navigation + cardSession
        stateAccess.updateState {
            it.copy(
                cardSession = it.cardSession.copy(
                    sessionState = SessionState.PAUSED,
                    currentCard = firstCard,
                    subLessonTotal = cards.size,
                    subLessonCount = 1
                ),
                drill = com.alexpo.grammermate.data.DrillState(),
                navigation = it.navigation.copy(
                    mode = TrainingMode.MIX_CHALLENGE,
                    selectedLessonId = null
                )
            )
        }
        return true to listOf(BossCommand.PauseTimer, BossCommand.SaveProgress, BossCommand.ResetStory, BossCommand.ResetVocabSprint, BossCommand.ResetDailySession)
    }

    // ── Boss Battle Entry Points ───────────────────────────────────────

    fun startBossLesson(): List<BossCommand> = startBoss(BossType.LESSON)

    fun startBossMega(): List<BossCommand> = startBoss(BossType.MEGA)

    fun startBossElite(): List<BossCommand> = startBoss(BossType.ELITE)

    // ── Boss Battle Lifecycle ──────────────────────────────────────────

    private fun startBoss(type: BossType): List<BossCommand> {
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
            _state.update { it.copy(bossErrorMessage = result.errorMessage) }
            return listOf(BossCommand.PauseTimer)
        }
        sessionRunner.setBossCards(result.cards)
        val firstCard = result.cards.firstOrNull()
        // Update boss state (owned by this orchestrator)
        _state.update {
            it.copy(
                bossActive = true,
                bossType = type,
                bossTotal = result.subLessonTotal,
                bossProgress = 0,
                bossReward = null,
                bossRewardMessage = null,
                bossErrorMessage = null
            )
        }
        // Update core state: cardSession
        stateAccess.updateState { s ->
            s.copy(
                cardSession = s.cardSession.copy(
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
        return listOf(BossCommand.PauseTimer)
    }

    fun finishBoss(): List<BossCommand> {
        val state = stateAccess.uiState.value
        val bossState = _state.value
        val result = bossBattleRunner.finishBoss(
            bossType = bossState.bossType,
            bossProgress = bossState.bossProgress,
            bossTotal = bossState.bossTotal,
            selectedLessonId = state.navigation.selectedLessonId?.value,
            currentLessonRewards = bossState.bossLessonRewards,
            currentMegaRewards = bossState.bossMegaRewards
        )
        val progress = progressStore.load()
        val restoredLessonId = progress.lessonId?.let { LessonId(it) } ?: state.navigation.selectedLessonId
        sessionRunner.clearAllCards()
        // Update boss state (owned by this orchestrator)
        _state.update {
            it.copy(
                bossActive = false,
                bossType = null,
                bossTotal = 0,
                bossProgress = 0,
                bossReward = result.reward ?: it.bossReward,
                bossRewardMessage = it.bossRewardMessage,
                bossFinishedToken = it.bossFinishedToken + 1,
                bossLastType = bossState.bossType,
                bossErrorMessage = null,
                bossLessonRewards = result.updatedLessonRewards,
                bossMegaRewards = result.updatedMegaRewards
            )
        }
        // Update core state: navigation + cardSession
        stateAccess.updateState { s ->
            s.copy(
                navigation = s.navigation.copy(
                    selectedLessonId = restoredLessonId,
                    mode = progress.mode
                ),
                cardSession = s.cardSession.copy(
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
        return listOf(BossCommand.PauseTimer, BossCommand.BuildSessionCards, BossCommand.SaveProgress, BossCommand.RefreshFlowerStates)
    }

    fun clearBossRewardMessage(): List<BossCommand> {
        val state = stateAccess.uiState.value
        val clearResult = bossBattleRunner.clearBossRewardMessage(
            bossActive = _state.value.bossActive,
            sessionState = state.cardSession.sessionState,
            currentCard = state.cardSession.currentCard,
            inputMode = state.cardSession.inputMode
        )
        val commands = mutableListOf<BossCommand>()
        if (clearResult.shouldResumeTimer) {
            commands.add(BossCommand.ResumeTimer)
        }
        // Update boss state (owned by this orchestrator)
        _state.update { it.copy(bossRewardMessage = null) }
        // Update core state: cardSession
        stateAccess.updateState { s ->
            val trigger = if (clearResult.shouldTriggerVoice) {
                s.cardSession.voiceTriggerToken + 1
            } else {
                s.cardSession.voiceTriggerToken
            }
            s.copy(
                cardSession = s.cardSession.copy(
                    sessionState = if (clearResult.shouldResumeTimer) SessionState.ACTIVE else s.cardSession.sessionState,
                    voiceTriggerToken = trigger,
                    inputText = if (clearResult.shouldResumeTimer) "" else s.cardSession.inputText
                )
            )
        }
        return commands
    }

    fun clearBossError() {
        _state.update { it.copy(bossErrorMessage = null) }
    }

    fun updateBossProgress(progress: Int): List<BossCommand> {
        val bossState = _state.value
        val result = bossBattleRunner.updateBossProgress(
            progress = progress,
            currentTotal = bossState.bossTotal,
            currentReward = bossState.bossReward
        )
        val commands = mutableListOf<BossCommand>()
        if (result.shouldPause) {
            commands.add(BossCommand.PauseTimer)
        }
        // Update boss state (owned by this orchestrator)
        _state.update {
            it.copy(
                bossProgress = result.nextProgress,
                bossReward = result.nextReward ?: it.bossReward,
                bossRewardMessage = result.rewardMessage ?: bossState.bossRewardMessage
            )
        }
        // Update core state: cardSession sessionState
        if (result.shouldPause) {
            stateAccess.updateState { s ->
                s.copy(cardSession = s.cardSession.copy(sessionState = SessionState.PAUSED))
            }
        }
        return commands
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
     * @return Pair of (BossAdvanceResult, commands) so the caller
     *         can detect if the pause should be applied after sessionRunner.nextCard().
     */
    data class BossAdvanceResult(
        val rewardMessageChanged: Boolean,
        val previousRewardMessage: String?
    )

    fun advanceBossProgressOnNextCard(nextIndex: Int, totalCards: Int): Pair<BossAdvanceResult, List<BossCommand>> {
        val bossState = _state.value
        val nextProgress = (bossState.bossProgress.coerceAtLeast(nextIndex)).coerceAtMost(bossState.bossTotal)
        val nextReward = bossBattleRunner.resolveBossReward(nextProgress, bossState.bossTotal)
        val isNewReward = nextReward != null && nextReward != bossState.bossReward
        val rewardMessage = if (isNewReward) {
            bossBattleRunner.bossRewardMessage(nextReward!!)
        } else {
            bossState.bossRewardMessage
        }
        val previousRewardMessage = bossState.bossRewardMessage
        val commands = mutableListOf<BossCommand>()
        if (isNewReward) {
            commands.add(BossCommand.PauseTimer)
        }
        // Update boss state (owned by this orchestrator)
        _state.update {
            it.copy(
                bossProgress = nextProgress,
                bossReward = nextReward ?: it.bossReward,
                bossRewardMessage = rewardMessage
            )
        }
        return BossAdvanceResult(
            rewardMessageChanged = rewardMessage != previousRewardMessage,
            previousRewardMessage = previousRewardMessage
        ) to commands
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

    // ── State management helpers ────────────────────────────────────────

    /**
     * Initialize boss rewards from persisted progress.
     * Called during ViewModel init.
     */
    fun initRewards(lessonRewards: Map<String, BossReward>, megaRewards: Map<String, BossReward>) {
        _state.update { it.copy(bossLessonRewards = lessonRewards, bossMegaRewards = megaRewards) }
    }

    /**
     * Reset boss state to defaults.
     * Called during session resets.
     */
    fun resetState() {
        _state.update { BossState() }
    }

    /**
     * Full reset preserving reward maps.
     * Called during full session resets (language change, pack import).
     */
    fun resetStateKeepRewards() {
        _state.update { it.copy(
            bossActive = false,
            bossType = null,
            bossTotal = 0,
            bossProgress = 0,
            bossReward = null,
            bossRewardMessage = null,
            bossFinishedToken = 0,
            bossLastType = null,
            bossErrorMessage = null
        ) }
    }
}
