package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.EliteState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.NavigationState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingConfig
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

    @Test
    fun thirdWrongAnswerPausesWithHintAndKeepsErrorEventOrder() {
        val (runner, access) = runnerFor(TrainingUiState(cardSession = CardSessionState(
            currentCard = card,
            sessionState = SessionState.ACTIVE,
            inputMode = InputMode.KEYBOARD
        )))

        repeat(3) { attempt ->
            runner.onInputChanged("wrong-$attempt")
            val (result, events) = runner.submitAnswer()
            assertEquals(listOf(SessionEvent.PlayError, SessionEvent.SaveProgress), events)
            assertEquals(attempt == 2, result.hintShown)
        }
        assertEquals(3, access.uiState.value.cardSession.incorrectCount)
        assertEquals(0, access.uiState.value.cardSession.incorrectAttemptsForCard)
        assertEquals("correct", access.uiState.value.cardSession.answerText)
        assertEquals(SessionState.HINT_SHOWN, access.uiState.value.cardSession.sessionState)
    }

    @Test
    fun reviewRestartResetsCardSessionAndTriggersHardVoiceMode() {
        val (runner, access) = runnerFor(TrainingUiState(cardSession = CardSessionState(
            correctCount = 3,
            incorrectCount = 2,
            inputMode = InputMode.KEYBOARD
        )))

        assertEquals(
            listOf(SessionEvent.RecordCardShow(card), SessionEvent.SaveProgress),
            runner.startReview(listOf(card), HintLevel.HARD)
        )
        val session = access.uiState.value.cardSession
        assertEquals(SessionState.ACTIVE, session.sessionState)
        assertEquals(card, session.currentCard)
        assertEquals(0, session.correctCount)
        assertEquals(0, session.incorrectCount)
        assertEquals(InputMode.VOICE, session.inputMode)
        assertEquals(1, session.voiceTriggerToken)
    }

    @Test
    fun eliteStepOpenAndCancelKeepDeckAndEventContract() {
        val lesson = Lesson(LessonId("elite-lesson"), LanguageId("it"), "Elite", listOf(card))
        val (runner, access) = runnerFor(TrainingUiState(
            navigation = NavigationState(lessons = listOf(lesson)),
            cardSession = CardSessionState(correctCount = 3)
        ))

        assertEquals(listOf(SessionEvent.SaveProgress), runner.openEliteStep(999))
        assertEquals(card, access.uiState.value.cardSession.currentCard)
        assertEquals(0, access.uiState.value.cardSession.correctCount)
        assertEquals(TrainingConfig.ELITE_STEP_COUNT - 1, access.uiState.value.elite.eliteStepIndex)
        assertTrue(access.uiState.value.elite.eliteActive)

        assertEquals(
            listOf(SessionEvent.SaveProgress, SessionEvent.RefreshFlowerStates),
            runner.cancelEliteSession()
        )
        assertEquals(false, access.uiState.value.elite.eliteActive)
        assertEquals(SessionState.PAUSED, access.uiState.value.cardSession.sessionState)
    }
}
