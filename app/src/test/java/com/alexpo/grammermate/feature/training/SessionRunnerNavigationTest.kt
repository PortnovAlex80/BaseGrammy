package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.HintLevel
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRunnerNavigationTest {
    private val cards = listOf(
        SentenceCard("nav-1", "first", listOf("one")),
        SentenceCard("nav-2", "second", listOf("two"))
    )

    private fun harness(
        index: Int = 0,
        inputMode: InputMode = InputMode.KEYBOARD
    ): Triple<SessionRunner, FakeTrainingStateAccess, MutableStateFlow<String>> {
        val state = FakeTrainingStateAccess(TrainingUiState(cardSession = CardSessionState(
            sessionState = SessionState.ACTIVE,
            currentIndex = index,
            currentCard = cards[index],
            inputMode = inputMode,
            hintLevel = HintLevel.EASY
        )))
        val input = MutableStateFlow("pending")
        val runner = SessionRunner(
            stateAccess = state,
            appContext = ApplicationProvider.getApplicationContext<Application>(),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            answerValidator = AnswerValidator(),
            wordBankGenerator = WordBankGenerator,
            cardProvider = CardProvider(),
            streakManager = StreakManager(FakeStreakStore()),
            getMastery = { _, _ -> null },
            getSchedule = { _ -> null },
            getHiddenCardIds = { emptySet() },
            calculateCompletedSubLessons = { _, _, _, _ -> 0 },
            onTimerSaveProgress = {},
            inputTextFlow = input
        )
        runner.setSessionCards(cards)
        return Triple(runner, state, input)
    }

    @Test
    fun manualArrowsPauseBeforeMoveAndClearInputWithoutGrading() {
        val (runner, state, input) = harness()

        assertEquals(listOf(SessionEvent.SaveProgress, SessionEvent.SaveProgress), runner.navigateNext())
        assertEquals(SessionState.PAUSED, state.uiState.value.cardSession.sessionState)
        assertEquals(cards[1], state.uiState.value.cardSession.currentCard)
        assertEquals(1, state.uiState.value.cardSession.currentIndex)
        assertEquals(0, state.uiState.value.cardSession.correctCount)
        assertEquals("", input.value)

        input.value = "again"
        assertEquals(listOf(SessionEvent.SaveProgress), runner.navigatePrev())
        assertEquals(cards[0], state.uiState.value.cardSession.currentCard)
        assertEquals(SessionState.PAUSED, state.uiState.value.cardSession.sessionState)
        assertEquals("", input.value)
    }

    @Test
    fun skipAtEndPausesAndClearsInputWithoutCorrectCredit() {
        val (runner, state, input) = harness(index = 1)

        runner.skipToNextCard()

        assertEquals(SessionState.PAUSED, state.uiState.value.cardSession.sessionState)
        assertEquals(0, state.uiState.value.cardSession.correctCount)
        assertEquals(null, state.uiState.value.cardSession.lastResult)
        assertEquals("", input.value)
    }

    @Test
    fun automaticAdvanceRefreshesWordBankForTheNewCard() {
        val (runner, state, input) = harness(inputMode = InputMode.WORD_BANK)
        runner.selectWordFromBank("one")
        assertEquals("one", input.value)

        assertEquals(listOf(SessionEvent.SaveProgress), runner.nextCard())

        assertEquals(cards[1], state.uiState.value.cardSession.currentCard)
        assertEquals(listOf("two"), state.uiState.value.cardSession.wordBankWords)
        assertEquals(emptyList<String>(), state.uiState.value.cardSession.selectedWords)
        assertEquals("", input.value)
    }
}
