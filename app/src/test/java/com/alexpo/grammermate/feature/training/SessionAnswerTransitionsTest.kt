package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.EliteState
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionAnswerTransitionsTest {
    private val voice = SessionAnswerTransitions.VoiceContribution(activeMs = 1_000L, words = 3)

    @Test
    fun bossMidAndLastRecordVoiceButLeaveProgressToRunner() {
        val card = CardSessionState(
            correctCount = 2,
            incorrectAttemptsForCard = 2,
            answerText = "hint",
            voiceActiveMs = 4_000L,
            voiceWordCount = 8,
            voicePromptStartMs = 123L
        )
        val mid = SessionAnswerTransitions.bossMid(card, voice)
        val last = SessionAnswerTransitions.bossLast(card, voice)

        assertEquals(3, mid.correctCount)
        assertEquals(true, mid.lastResult)
        assertEquals(null, last.lastResult)
        assertEquals(5_000L, last.voiceActiveMs)
        assertEquals(11, last.voiceWordCount)
        assertEquals(null, last.voicePromptStartMs)
        assertEquals(0, last.incorrectAttemptsForCard)
        assertEquals(null, last.answerText)
        assertEquals(card.currentIndex, mid.currentIndex)
    }

    @Test
    fun normalLastClampsActiveIndexAndRecordsCompletionOnce() {
        val state = TrainingUiState(cardSession = CardSessionState(
            correctCount = 4,
            currentIndex = 6,
            activeSubLessonIndex = 1,
            completedSubLessonCount = 1,
            subLessonCount = 3,
            subLessonFinishedToken = 7,
            voiceActiveMs = 2_000L,
            voiceWordCount = 5
        ))
        val completed = SessionAnswerTransitions.normalLast(state, voice, actualCompletedCount = 5).cardSession

        assertEquals(SessionState.PAUSED, completed.sessionState)
        assertEquals(5, completed.correctCount)
        assertEquals(0, completed.currentIndex)
        assertEquals(2, completed.activeSubLessonIndex)
        assertEquals(5, completed.completedSubLessonCount)
        assertEquals(8, completed.subLessonFinishedToken)
        assertEquals(3_000L, completed.voiceActiveMs)
        assertEquals(8, completed.voiceWordCount)
    }

    @Test
    fun explicitNextCompletesWithoutCreditingCorrectAnswerOrVoice() {
        val state = TrainingUiState(cardSession = CardSessionState(
            correctCount = 2,
            currentIndex = 4,
            subLessonCount = 3,
            completedSubLessonCount = 1,
            voiceActiveMs = 500L,
            voiceWordCount = 7,
            answerText = "hint"
        ))
        val completed = SessionAnswerTransitions.normalCompleteViaNext(state, 2).cardSession
        assertEquals(2, completed.correctCount)
        assertEquals(500L, completed.voiceActiveMs)
        assertEquals(7, completed.voiceWordCount)
        assertEquals(2, completed.completedSubLessonCount)
        assertEquals(1, completed.subLessonFinishedToken)
        assertEquals(null, completed.answerText)
        assertEquals(SessionState.PAUSED, completed.sessionState)
    }

    @Test
    fun normalMidRecordsAnswerWithoutAdvancingCard() {
        val card = CardSessionState(currentIndex = 2, correctCount = 1)
        val accepted = SessionAnswerTransitions.normalMid(card, voice)
        assertEquals(2, accepted.currentIndex)
        assertEquals(2, accepted.correctCount)
        assertEquals(true, accepted.lastResult)
        assertEquals(SessionState.ACTIVE, accepted.sessionState)
    }

    @Test
    fun eliteLastScoresPreAnswerVoiceMetricsAndAdvancesStep() {
        val state = TrainingUiState(
            cardSession = CardSessionState(
                currentIndex = 4,
                voiceActiveMs = 60_000L,
                voiceWordCount = 120
            ),
            elite = EliteState(
                eliteActive = true,
                eliteStepIndex = 1,
                eliteBestSpeeds = listOf(100.0, 110.0),
                eliteFinishedToken = 3
            )
        )
        val completed = SessionAnswerTransitions.eliteLast(state, voice, stepCount = 3)

        assertEquals(1, completed.stepIndex)
        assertEquals(120.0, completed.speed, 0.001)
        assertEquals(110.0, completed.previousBest, 0.001)
        assertEquals(listOf(100.0, 120.0, 0.0), completed.state.elite.eliteBestSpeeds)
        assertEquals(2, completed.state.elite.eliteStepIndex)
        assertEquals(4, completed.state.elite.eliteFinishedToken)
        assertFalse(completed.state.elite.eliteActive)
        assertEquals(61_000L, completed.state.cardSession.voiceActiveMs)
        assertEquals(123, completed.state.cardSession.voiceWordCount)
        assertEquals(SessionState.PAUSED, completed.state.cardSession.sessionState)
    }
}
