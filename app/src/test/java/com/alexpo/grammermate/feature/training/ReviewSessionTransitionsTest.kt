package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.EliteState
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSessionTransitionsTest {
    private val card = SentenceCard("review-1", "prompt", listOf("answer"))
    private val stale = TrainingUiState(
        cardSession = CardSessionState(
            sessionState = SessionState.HINT_SHOWN,
            currentIndex = 5,
            correctCount = 4,
            incorrectCount = 3,
            activeTimeMs = 8_000L,
            voiceActiveMs = 2_000L,
            voiceWordCount = 10,
            answerText = "old hint",
            wordBankWords = listOf("old"),
            selectedWords = listOf("old"),
            screenMode = TrainingScreenMode.VERB_DRILL
        ),
        boss = BossState(bossActive = true),
        elite = EliteState(eliteActive = true)
    )

    @Test
    fun reviewStartsCleanWithOneCardAndVoiceForHardLevel() {
        val next = ReviewSessionTransitions.start(stale, listOf(card), HintLevel.HARD, voiceTriggerToken = 9)
        val session = next.cardSession

        assertEquals(SessionState.ACTIVE, session.sessionState)
        assertEquals(card, session.currentCard)
        assertEquals(0, session.currentIndex)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.incorrectCount)
        assertEquals(0L, session.activeTimeMs)
        assertEquals(0L, session.voiceActiveMs)
        assertEquals(0, session.voiceWordCount)
        assertEquals(null, session.answerText)
        assertTrue(session.wordBankWords.isEmpty())
        assertTrue(session.selectedWords.isEmpty())
        assertEquals(TrainingScreenMode.NORMAL, session.screenMode)
        assertTrue(session.isReviewMode)
        assertEquals(InputMode.VOICE, session.inputMode)
        assertEquals(9, session.voiceTriggerToken)
        assertFalse(next.boss.bossActive)
        assertFalse(next.elite.eliteActive)
    }

    @Test
    fun emptyEasyReviewIsPausedAndUsesKeyboard() {
        val next = ReviewSessionTransitions.start(stale, emptyList(), HintLevel.EASY, voiceTriggerToken = 0)
        assertEquals(SessionState.PAUSED, next.cardSession.sessionState)
        assertEquals(null, next.cardSession.currentCard)
        assertEquals(0, next.cardSession.subLessonTotal)
        assertEquals(InputMode.KEYBOARD, next.cardSession.inputMode)
    }
}
