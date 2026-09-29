package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionNavigationTransitionsTest {
    private val card = SentenceCard("target", "prompt", listOf("answer"))

    @Test
    fun automaticAdvanceResetsResultAndActivatesNewCard() {
        val current = CardSessionState(
            sessionState = SessionState.HINT_SHOWN,
            currentIndex = 0,
            incorrectAttemptsForCard = 2,
            answerText = "old",
            voicePromptStartMs = 50L
        )
        val next = SessionNavigationTransitions.advance(current, 1, card, 0, 7)
        assertEquals(SessionState.ACTIVE, next.sessionState)
        assertEquals(1, next.currentIndex)
        assertEquals(card, next.currentCard)
        assertEquals(null, next.answerText)
        assertEquals(0, next.incorrectAttemptsForCard)
        assertEquals(7, next.voiceTriggerToken)
        assertEquals(null, next.voicePromptStartMs)
    }

    @Test
    fun manualBrowsePausesButPreviousCommandPreservesState() {
        val current = CardSessionState(sessionState = SessionState.ACTIVE, currentIndex = 1)
        assertEquals(SessionState.PAUSED, SessionNavigationTransitions.browse(current, 0, card).sessionState)
        assertEquals(SessionState.ACTIVE, SessionNavigationTransitions.previous(current, 0, card).sessionState)
    }

    @Test
    fun subLessonSelectionWaitsForCardRebuildAndSkipEndDoesNotCreditAnswer() {
        val current = CardSessionState(
            currentCard = card,
            currentIndex = 4,
            correctCount = 2,
            answerText = "old"
        )
        val selected = SessionNavigationTransitions.selectSubLesson(current, 3)
        assertEquals(card, selected.currentCard)
        assertEquals(0, selected.currentIndex)
        assertEquals(3, selected.activeSubLessonIndex)
        val skipped = SessionNavigationTransitions.skipEnd(current)
        assertEquals(2, skipped.correctCount)
        assertEquals(SessionState.PAUSED, skipped.sessionState)
        assertEquals(null, skipped.answerText)
    }
}
