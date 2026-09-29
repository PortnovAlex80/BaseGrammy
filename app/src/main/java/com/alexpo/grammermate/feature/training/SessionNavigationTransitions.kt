package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.SessionCard
import com.alexpo.grammermate.data.SessionState

/** Card-cursor snapshots only. SessionRunner keeps timer, audit, input and event ordering. */
internal object SessionNavigationTransitions {
    fun advance(
        current: CardSessionState,
        index: Int,
        card: SessionCard?,
        incorrectAttempts: Int,
        voiceTriggerToken: Int
    ): CardSessionState = current.copy(
        currentIndex = index,
        currentCard = card,
        lastResult = null,
        answerText = null,
        incorrectAttemptsForCard = incorrectAttempts,
        sessionState = SessionState.ACTIVE,
        voiceTriggerToken = voiceTriggerToken,
        voicePromptStartMs = null
    )

    /** The old prevCard command preserves its current ACTIVE/PAUSED state. */
    fun previous(current: CardSessionState, index: Int, card: SessionCard?): CardSessionState = current.copy(
        currentIndex = index,
        currentCard = card,
        lastResult = null,
        answerText = null,
        incorrectAttemptsForCard = 0,
        voicePromptStartMs = null
    )

    /** Manual arrows always leave the session paused. */
    fun browse(current: CardSessionState, index: Int, card: SessionCard?): CardSessionState = current.copy(
        currentIndex = index,
        currentCard = card,
        lastResult = null,
        answerText = null,
        incorrectAttemptsForCard = 0,
        sessionState = SessionState.PAUSED,
        voicePromptStartMs = null
    )

    /** Card replacement follows the BuildSessionCards event; currentCard is preserved until then. */
    fun selectSubLesson(current: CardSessionState, index: Int): CardSessionState = current.copy(
        activeSubLessonIndex = index.coerceAtLeast(0),
        currentIndex = 0,
        lastResult = null,
        answerText = null,
        sessionState = SessionState.PAUSED
    )

    fun skipTo(current: CardSessionState, index: Int, card: SessionCard): CardSessionState = current.copy(
        currentIndex = index,
        currentCard = card,
        lastResult = null,
        answerText = null,
        incorrectAttemptsForCard = 0
    )

    fun skipEnd(current: CardSessionState): CardSessionState = current.copy(
        sessionState = SessionState.PAUSED,
        lastResult = null,
        answerText = null
    )
}
