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
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRunnerInputTest {
    private val card = SentenceCard("input-1", "prompt", listOf("hello"))

    private fun harness(level: HintLevel): Triple<SessionRunner, FakeTrainingStateAccess, MutableStateFlow<String>> {
        val state = FakeTrainingStateAccess(TrainingUiState(cardSession = CardSessionState(
            currentCard = card,
            sessionState = SessionState.ACTIVE,
            hintLevel = level,
            inputMode = InputMode.KEYBOARD
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
        runner.setSessionCards(listOf(card))
        return Triple(runner, state, input)
    }

    @Test
    fun unavailableWordBankFallsBackWithoutLaunchingVoice() {
        val (runner, state, input) = harness(HintLevel.HARD)
        runner.setInputMode(InputMode.WORD_BANK)

        assertEquals(InputMode.VOICE, state.uiState.value.cardSession.inputMode)
        assertEquals(0, state.uiState.value.cardSession.voiceTriggerToken)
        assertEquals("pending", input.value)
    }

    @Test
    fun voiceModeAndTypedInputUseTheSharedFlow() {
        val (runner, state, input) = harness(HintLevel.EASY)
        runner.setInputMode(InputMode.VOICE)
        assertEquals(1, state.uiState.value.cardSession.voiceTriggerToken)
        runner.onVoicePromptStarted()
        assertNotNull(state.uiState.value.cardSession.voicePromptStartMs)

        runner.onInputChanged("hello")
        assertEquals("hello", input.value)
        runner.clearTypedInput()
        assertEquals("", input.value)
    }

    @Test
    fun easyWordBankRegeneratesExistingModeWithoutKeepingTypedText() {
        val (runner, state, input) = harness(HintLevel.EASY)
        runner.setInputMode(InputMode.WORD_BANK)

        assertEquals(InputMode.WORD_BANK, state.uiState.value.cardSession.inputMode)
        assertEquals(listOf("hello"), state.uiState.value.cardSession.wordBankWords)
        assertEquals("", input.value)
    }
}
