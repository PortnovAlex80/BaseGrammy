package com.alexpo.grammermate.feature.training

import android.util.Log
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.AuditLogger
import com.alexpo.grammermate.shared.TrainingStateAccess
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns card movement, navigation timers, audit and event order without a second state store. */
internal class SessionNavigator(
    private val stateAccess: TrainingStateAccess,
    private val stateMachine: CardSessionStateMachine,
    private val sessionCards: () -> List<SessionCard>,
    private val activeTimer: SessionActiveTimer,
    private val inputTextFlow: MutableStateFlow<String>,
    private val wordBank: SessionWordBank,
    private val completedSubLessons: (TrainingUiState) -> Int
) {
    private val logTag = "SessionRunner"
    private val audit get() = AuditLogger.getInstanceOrNull()
    private val isLinearSession: Boolean
        get() = LinearSessionTransitions.handles(stateAccess.currentState.cardSession.screenMode)

    private fun updateStateAndClearInput(transform: (TrainingUiState) -> TrainingUiState) {
        inputTextFlow.value = ""
        stateAccess.updateState(transform)
    }

    fun nextCard(triggerVoice: Boolean = false): List<SessionEvent> {
        val state = stateAccess.currentState
        val fromIdx = state.cardSession.currentIndex
        val wasHintShown = state.cardSession.sessionState == SessionState.HINT_SHOWN || stateMachine.hintAnswer != null
        stateMachine.reset()
        val lastIndex = sessionCards().lastIndex
        val isOnLastCard = state.cardSession.currentIndex >= lastIndex && lastIndex >= 0

        // Linear sessions: on last card, signal completion without lesson-level mechanics
        if (isOnLastCard && isLinearSession) {
            activeTimer.pause()
            stateAccess.updateState {
                it.copy(cardSession = LinearSessionTransitions.completeViaNext(it.cardSession))
            }
            Log.d(logTag, "LinearSession: Next pressed on last card — signaling completion")
            return listOf(SessionEvent.SaveProgress)
        }

        // Fix 3: If already on the last card, trigger sub-lesson completion instead of staying put
        if (isOnLastCard && !state.boss.bossActive && !state.elite.eliteActive) {
            activeTimer.pause()
            stateAccess.updateState {
                SessionAnswerTransitions.normalCompleteViaNext(it, completedSubLessons(it))
            }
            Log.d(logTag, "Next pressed on last card — triggering sub-lesson completion")
            return listOf(
                SessionEvent.MarkSubLessonCardsShown(sessionCards()),
                SessionEvent.BuildSessionCards,
                SessionEvent.CheckAndMarkLessonCompleted,
                SessionEvent.RefreshFlowerStates,
                SessionEvent.UpdateStreak,
                SessionEvent.SaveProgress
            )
        }

        val nextIndex = (state.cardSession.currentIndex + 1).coerceAtMost(lastIndex)
        val nextCard = sessionCards().getOrNull(nextIndex)

        if (triggerVoice && stateAccess.currentState.cardSession.inputMode == InputMode.VOICE) {
            stateMachine.triggerVoice()
        }
        updateStateAndClearInput {
            it.copy(cardSession = SessionNavigationTransitions.advance(
                it.cardSession, nextIndex, nextCard, stateMachine.incorrectAttempts, stateMachine.voiceTriggerToken
            ))
        }
        val events = mutableListOf<SessionEvent>()
        // Boss progress: trigger update in orchestrator
        if (state.boss.bossActive) {
            events.add(SessionEvent.AdvanceBossProgress(nextIndex, sessionCards().size))
        }
        // Update word bank if in WORD_BANK mode
        if (stateAccess.currentState.cardSession.inputMode == InputMode.WORD_BANK) {
            wordBank.update()
        }

        if (wasHintShown) {
            activeTimer.resume()
        }
        // Audit: card navigation next
        val toIdx = stateAccess.currentState.cardSession.currentIndex
        audit?.cardNav("next", fromIdx, toIdx, "submit")
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun prevCard(): List<SessionEvent> {
        val state = stateAccess.currentState
        val fromIdx = state.cardSession.currentIndex
        val prevIndex = (state.cardSession.currentIndex - 1).coerceAtLeast(0)
        val prevCard = sessionCards().getOrNull(prevIndex)
        stateMachine.reset()
        updateStateAndClearInput {
            it.copy(cardSession = SessionNavigationTransitions.previous(it.cardSession, prevIndex, prevCard))
        }
        // Audit: card navigation prev
        audit?.cardNav("prev", fromIdx, prevIndex, "manual")
        val events = mutableListOf<SessionEvent>()
        events.add(SessionEvent.SaveProgress)
        return events
    }

    /**
     * Navigate to the next card, pausing first if the session is ACTIVE.
     * Used by UI navigation arrows — always leaves the session in PAUSED state
     * so the user can browse cards without timer pressure.
     * Pressing Play resumes the session.
     */
    fun navigateNext(): List<SessionEvent> = navigateManually(forward = true)

    fun navigatePrev(): List<SessionEvent> = navigateManually(forward = false)

    /** Manual arrows pause before moving; the pause and move each request their own save. */
    private fun navigateManually(forward: Boolean): List<SessionEvent> {
        val events = mutableListOf<SessionEvent>()
        if (stateAccess.currentState.cardSession.sessionState == SessionState.ACTIVE) {
            events.addAll(pauseSession())
        }
        stateMachine.reset()
        val state = stateAccess.currentState
        val fromIdx = state.cardSession.currentIndex
        val targetIndex = if (forward) {
            (fromIdx + 1).coerceAtMost(sessionCards().lastIndex)
        } else {
            (fromIdx - 1).coerceAtLeast(0)
        }
        val targetCard = sessionCards().getOrNull(targetIndex)
        updateStateAndClearInput {
            it.copy(cardSession = SessionNavigationTransitions.browse(it.cardSession, targetIndex, targetCard))
        }
        audit?.cardNav(if (forward) "next" else "prev", fromIdx, targetIndex, "manual")
        if (forward && stateAccess.currentState.cardSession.inputMode == InputMode.WORD_BANK) {
            wordBank.update()
        }
        events.add(SessionEvent.SaveProgress)
        return events
    }

    fun selectSubLesson(index: Int): List<SessionEvent> {
        activeTimer.pause()
        stateMachine.reset()
        val prevState = stateAccess.currentState
        val fromSub = prevState.cardSession.activeSubLessonIndex
        val fromIdx = prevState.cardSession.currentIndex
        updateStateAndClearInput {
            it.copy(cardSession = SessionNavigationTransitions.selectSubLesson(it.cardSession, index))
        }
        // Audit: sub-lesson transition
        audit?.subLessonTransition(fromSub, index, prevState.cardSession.subLessonCount, fromIdx, 0)
        return listOf(SessionEvent.BuildSessionCards, SessionEvent.SaveProgress)
    }

    fun pauseSession(): List<SessionEvent> {
        activeTimer.pause()
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(sessionState = SessionState.PAUSED, voicePromptStartMs = null)) }
        return listOf(SessionEvent.SaveProgress)
    }

    fun skipToNextCard() {
        val state = stateAccess.currentState
        val fromIdx = state.cardSession.currentIndex
        val nextIndex = state.cardSession.currentIndex + 1
        if (nextIndex < sessionCards().size) {
            stateMachine.reset()
            updateStateAndClearInput {
                it.copy(cardSession = SessionNavigationTransitions.skipTo(it.cardSession, nextIndex, sessionCards()[nextIndex]))
            }
            // Audit: skip to next card
            audit?.cardNav("skip", fromIdx, nextIndex, "manual")
        } else {
            activeTimer.pause()
            updateStateAndClearInput {
                it.copy(cardSession = SessionNavigationTransitions.skipEnd(it.cardSession))
            }
        }
    }

}
