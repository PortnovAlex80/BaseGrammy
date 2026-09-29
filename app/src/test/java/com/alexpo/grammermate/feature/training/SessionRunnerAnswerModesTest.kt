package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.EliteState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRunnerAnswerModesTest {
    private val card = SentenceCard("answer-mode-1", "prompt", listOf("correct"))

    private fun runnerFor(state: TrainingUiState): Pair<SessionRunner, FakeTrainingStateAccess> {
        val access = FakeTrainingStateAccess(state)
        val runner = SessionRunner(
            stateAccess = access,
            appContext = ApplicationProvider.getApplicationContext<Application>(),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            answerValidator = AnswerValidator(),
            wordBankGenerator = WordBankGenerator,
            cardProvider = CardProvider(),
            streakManager = StreakManager(FakeStreakStore()),
            getMastery = { _, _ -> null },
            getSchedule = { _ -> null },
            getHiddenCardIds = { emptySet() },
            calculateCompletedSubLessons = { _, _, _, _ -> 2 },
            onTimerSaveProgress = {},
            inputTextFlow = MutableStateFlow("correct")
        )
        runner.setSessionCards(listOf(card))
        return runner to access
    }

    @Test
    fun normalLastAnswerKeepsCompletionEventOrder() {
        val (runner, access) = runnerFor(TrainingUiState(cardSession = CardSessionState(
            currentCard = card,
            subLessonCount = 3,
            inputMode = InputMode.KEYBOARD
        )))

        val (result, events) = runner.submitAnswer()

        assertTrue(result.needsSubLessonComplete)
        assertEquals(listOf(
            SessionEvent.PlaySuccess,
            SessionEvent.RecordCardShow(card),
            SessionEvent.MarkSubLessonCardsShown(listOf(card)),
            SessionEvent.BuildSessionCards,
            SessionEvent.CheckAndMarkLessonCompleted,
            SessionEvent.RefreshFlowerStates,
            SessionEvent.UpdateStreak,
            SessionEvent.SaveProgress
        ), events)
        assertEquals(SessionState.PAUSED, access.uiState.value.cardSession.sessionState)
        assertEquals(2, access.uiState.value.cardSession.completedSubLessonCount)
    }

    @Test
    fun explicitNextOnFinalNormalCardCompletesWithoutCorrectCredit() {
        val (runner, access) = runnerFor(TrainingUiState(cardSession = CardSessionState(
            currentCard = card,
            subLessonCount = 3,
            correctCount = 4,
            inputMode = InputMode.KEYBOARD
        )))

        val events = runner.nextCard()

        assertEquals(listOf(
            SessionEvent.MarkSubLessonCardsShown(listOf(card)),
            SessionEvent.BuildSessionCards,
            SessionEvent.CheckAndMarkLessonCompleted,
            SessionEvent.RefreshFlowerStates,
            SessionEvent.UpdateStreak,
            SessionEvent.SaveProgress
        ), events)
        assertEquals(4, access.uiState.value.cardSession.correctCount)
        assertEquals(2, access.uiState.value.cardSession.completedSubLessonCount)
    }

    @Test
    fun eliteLastAnswerScoresBeforeFinalVoiceAndOnlyRequestsSave() {
        val (runner, access) = runnerFor(TrainingUiState(
            cardSession = CardSessionState(
                currentCard = card,
                inputMode = InputMode.KEYBOARD,
                voiceActiveMs = 60_000L,
                voiceWordCount = 120
            ),
            elite = EliteState(eliteActive = true, eliteBestSpeeds = listOf(100.0))
        ))

        val (result, events) = runner.submitAnswer()

        assertTrue(result.needsEliteFinish)
        assertEquals(listOf(
            SessionEvent.PlaySuccess,
            SessionEvent.RecordCardShow(card),
            SessionEvent.SaveProgress
        ), events)
        assertEquals(120.0, access.uiState.value.elite.eliteBestSpeeds.first(), 0.001)
        assertEquals(SessionState.PAUSED, access.uiState.value.cardSession.sessionState)
    }
}
