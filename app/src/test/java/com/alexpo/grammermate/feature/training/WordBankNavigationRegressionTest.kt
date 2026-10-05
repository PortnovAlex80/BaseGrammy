package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.HintLevel
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Banks and selections follow every public card movement command. */
@RunWith(RobolectricTestRunner::class)
class WordBankNavigationRegressionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val state = FakeTrainingStateAccess(TrainingUiState())
    private val cards = listOf(
        SentenceCard("bank-review-1", "First", listOf("alpha")),
        SentenceCard("bank-review-2", "Second", listOf("beta"))
    )
    private val runner = SessionRunner(
        stateAccess = state,
        appContext = ApplicationProvider.getApplicationContext<Application>(),
        coroutineScope = scope,
        answerValidator = AnswerValidator(),
        wordBankGenerator = WordBankGenerator,
        cardProvider = CardProvider(),
        streakManager = StreakManager(FakeStreakStore()),
        getMastery = { _, _ -> null },
        getSchedule = { _ -> null },
        getHiddenCardIds = { emptySet() },
        calculateCompletedSubLessons = { _, _, _, _ -> 0 },
        onTimerSaveProgress = {}
    )

    @After fun cleanup() { scope.cancel() }

    @Test fun correctDailyAnswerRegeneratesBankForTheNextCard() {
        runner.startCardSession(cards, TrainingScreenMode.DAILY_TRANSLATE)
        runner.setInputMode(InputMode.WORD_BANK)
        runner.selectWordFromBank("alpha")
        runner.submitAnswer()
        assertEquals(cards[1], state.currentState.cardSession.currentCard)
        assertEquals("The second card must offer its own answer", listOf("beta"), state.currentState.cardSession.wordBankWords)
        assertEquals(emptyList<String>(), state.currentState.cardSession.selectedWords)
    }

    @Test fun backwardNavigationRegeneratesBankForThePreviousCard() {
        // The review route is shipped and uses the same NORMAL navigator.
        runner.startReview(cards, HintLevel.EASY)
        runner.setInputMode(InputMode.WORD_BANK)
        runner.navigateNext()
        assertEquals(listOf("beta"), state.currentState.cardSession.wordBankWords)
        runner.navigatePrev()
        assertEquals(cards[0], state.currentState.cardSession.currentCard)
        assertEquals("The previous card must offer its own answer", listOf("alpha"), state.currentState.cardSession.wordBankWords)
    }
}
