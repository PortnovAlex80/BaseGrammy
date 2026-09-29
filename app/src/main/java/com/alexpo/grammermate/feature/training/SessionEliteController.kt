package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.ceil

/** Owns elite step entry, cancellation and sizing; answer grading remains in SessionRunner. */
internal class SessionEliteController(
    private val stateAccess: TrainingStateAccess,
    private val stateMachine: CardSessionStateMachine,
    private val deck: SessionDeck,
    private val activeTimer: SessionActiveTimer,
    private val inputTextFlow: MutableStateFlow<String>
) {
    val stepCount: Int = TrainingConfig.ELITE_STEP_COUNT
    private var subLessonSize = TrainingConfig.SUB_LESSON_SIZE_DEFAULT
    private var sizeMultiplier = TrainingConfig.ELITE_SIZE_MULTIPLIER
    private val audit get() = AuditLogger.getInstanceOrNull()

    fun setSubLessonSize(size: Int) {
        subLessonSize = size
    }

    fun setSizeMultiplier(multiplier: Double) {
        sizeMultiplier = multiplier
    }

    fun eliteSubLessonSize(): Int = ceil(subLessonSize * sizeMultiplier).toInt()

    private fun buildCards(): List<SessionCard> {
        val cards = stateAccess.uiState.value.navigation.lessons.flatMap { it.cards }
        if (cards.isEmpty()) return emptyList()
        return cards.shuffled().take(eliteSubLessonSize())
    }

    private fun updateStateAndClearInput(transform: (TrainingUiState) -> TrainingUiState) {
        inputTextFlow.value = ""
        stateAccess.updateState(transform)
    }

    fun openStep(index: Int): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        val stepIndex = index.coerceIn(0, stepCount - 1)
        val cards = buildCards()
        deck.replace(cards)
        val firstCard = cards.firstOrNull()
        updateStateAndClearInput {
            it.copy(
                elite = it.elite.copy(eliteActive = true, eliteStepIndex = stepIndex),
                cardSession = it.cardSession.copy(
                    currentIndex = 0, currentCard = firstCard, lastResult = null,
                    answerText = null, incorrectAttemptsForCard = 0,
                    correctCount = 0, incorrectCount = 0, activeTimeMs = 0L,
                    voiceActiveMs = 0L, voiceWordCount = 0, hintCount = 0,
                    voicePromptStartMs = null, sessionState = SessionState.PAUSED,
                    subLessonTotal = cards.size, subLessonCount = stepCount,
                    activeSubLessonIndex = stepIndex, completedSubLessonCount = 0
                )
            )
        }
        audit?.eliteStepStart(stepIndex, stepCount, cards.size)
        return listOf(SessionEvent.SaveProgress)
    }

    fun cancel(): List<SessionEvent> {
        if (!stateAccess.uiState.value.elite.eliteActive) return emptyList()
        activeTimer.pause()
        stateMachine.reset()
        updateStateAndClearInput {
            it.copy(
                elite = it.elite.copy(eliteActive = false),
                cardSession = it.cardSession.copy(
                    sessionState = SessionState.PAUSED, currentIndex = 0,
                    lastResult = null, answerText = null,
                    incorrectAttemptsForCard = 0, voicePromptStartMs = null
                )
            )
        }
        return listOf(SessionEvent.SaveProgress, SessionEvent.RefreshFlowerStates)
    }

    fun resolveUnlocked(lessons: List<Lesson>, testMode: Boolean): Boolean =
        testMode || lessons.size >= 12

    fun normalizeSpeeds(speeds: List<Double>): List<Double> =
        if (speeds.size >= stepCount) speeds.take(stepCount)
        else speeds + List(stepCount - speeds.size) { 0.0 }

    fun calculateSpeedPerMinute(activeMs: Long, words: Int): Double {
        val minutes = activeMs / 60000.0
        if (minutes <= 0.0) return 0.0
        return words / minutes
    }
}
