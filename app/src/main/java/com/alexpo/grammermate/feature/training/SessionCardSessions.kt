package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns linear and review deck replacement commands; the deck and UI state remain shared sources. */
internal class SessionCardSessions(
    private val stateAccess: TrainingStateAccess,
    private val stateMachine: CardSessionStateMachine,
    private val deck: SessionDeck,
    private val activeTimer: SessionActiveTimer,
    private val inputTextFlow: MutableStateFlow<String>,
    private val wordBank: SessionWordBank
) {
    private val audit get() = AuditLogger.getInstanceOrNull()

    private fun updateStateAndClearInput(transform: (TrainingUiState) -> TrainingUiState) {
        inputTextFlow.value = ""
        stateAccess.updateState(transform)
    }

    fun start(cards: List<SessionCard>, mode: TrainingScreenMode): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        deck.replace(cards)
        val firstCard = cards.firstOrNull()
        if (stateAccess.currentState.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            LinearSessionTransitions.start(it, cards, mode, stateMachine.voiceTriggerToken)
        }
        if (firstCard != null) {
            activeTimer.resume()
            // A carried WORD_BANK input mode still needs a fresh bank for this deck.
            wordBank.update()
        }
        return listOf(SessionEvent.SaveProgress)
    }

    fun exit(): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        deck.replace(emptyList())
        updateStateAndClearInput { LinearSessionTransitions.exit(it) }
        return listOf(SessionEvent.SaveProgress)
    }

    fun startReview(cards: List<SessionCard>, hintLevel: HintLevel): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        deck.replace(cards)
        val firstCard = cards.firstOrNull()
        audit?.reviewStart(
            stateAccess.currentState.navigation.selectedLessonId?.value ?: "",
            hintLevel.name
        )
        if (ReviewSessionTransitions.defaultInputMode(hintLevel) == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            ReviewSessionTransitions.start(it, cards, hintLevel, stateMachine.voiceTriggerToken)
        }
        if (firstCard != null) {
            activeTimer.resume()
            wordBank.update()
            return listOf(SessionEvent.RecordCardShow(firstCard), SessionEvent.SaveProgress)
        }
        return listOf(SessionEvent.SaveProgress)
    }

    fun replace(cards: List<SessionCard>): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        deck.replace(cards)
        val firstCard = cards.firstOrNull()
        if (stateAccess.currentState.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            LinearSessionTransitions.replace(it, cards, stateMachine.voiceTriggerToken)
        }
        if (firstCard != null) {
            activeTimer.resume()
            wordBank.update()
        }
        audit?.cardsReplaced(cards.size, "more")
        return listOf(SessionEvent.SaveProgress)
    }

    /** Advance after a correct non-final linear answer without lesson-level effects. */
    fun advanceLinearCard() {
        val state = stateAccess.currentState
        val nextIndex = state.cardSession.currentIndex + 1
        val nextCard = deck.cards.getOrNull(nextIndex)
        stateMachine.reset()
        if (state.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            it.copy(cardSession = LinearSessionTransitions.advance(
                it.cardSession, nextIndex, nextCard,
                stateMachine.incorrectAttempts, stateMachine.voiceTriggerToken
            ))
        }
    }
}
