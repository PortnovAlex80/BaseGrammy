package com.alexpo.grammermate.feature.training

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.alexpo.grammermate.data.CardSessionState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.shared.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.testharness.FakeStreakStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Captures the shipping linear-session behavior before its state rules move out of SessionRunner. */
@RunWith(RobolectricTestRunner::class)
class SessionRunnerLinearTransitionsTest {
    private val cards = listOf(
        SentenceCard("linear-1", "first", listOf("alpha")),
        SentenceCard("linear-2", "second", listOf("beta"))
    )

    private class RecordingStateAccess(initial: TrainingUiState) : TrainingStateAccess {
        private val state = MutableStateFlow(initial)
        override val uiState: StateFlow<TrainingUiState> = state
        val writes = mutableListOf<CardSessionState>()

        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            state.value = transform(state.value)
            writes += state.value.cardSession
        }

        override fun saveProgress() = Unit
    }

    private data class Harness(
        val runner: SessionRunner,
        val state: RecordingStateAccess,
        val input: MutableStateFlow<String>
    )

    private fun harness(
        mode: TrainingScreenMode,
        index: Int = 0,
        inputMode: InputMode = InputMode.KEYBOARD,
        cards: List<SentenceCard> = this.cards,
        submitClockMs: () -> Long = { 1_000L }
    ): Harness {
        val state = RecordingStateAccess(
            TrainingUiState().copy(
                cardSession = CardSessionState(
                    currentCard = cards[index],
                    currentIndex = index,
                    subLessonTotal = cards.size,
                    sessionState = SessionState.ACTIVE,
                    screenMode = mode,
                    inputMode = inputMode
                )
            )
        )
        val input = MutableStateFlow("")
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
            inputTextFlow = input,
            submitClockMs = submitClockMs
        )
        runner.setSessionCardsGeneric(cards)
        return Harness(runner, state, input)
    }

    @Test
    fun `correct middle card writes accepted state then advances and clears input`() {
        val h = harness(TrainingScreenMode.VERB_DRILL)
        h.input.value = "alpha"

        val (result, events) = h.runner.submitAnswer()

        assertTrue(result.accepted)
        assertFalse(result.needsSaveProgress)
        assertEquals(listOf(SessionEvent.PlaySuccess, SessionEvent.RecordCardShow(cards[0])), events)
        assertEquals(listOf(1 to 0, 1 to 1), h.state.writes.map { it.correctCount to it.currentIndex })
        assertEquals(cards[1], h.state.uiState.value.cardSession.currentCard)
        assertEquals(SessionState.ACTIVE, h.state.uiState.value.cardSession.sessionState)
        assertEquals("", h.input.value)
    }

    @Test
    fun `correct final card completes linear session without lesson events`() {
        val h = harness(TrainingScreenMode.VERB_DRILL, index = 1)
        h.input.value = "beta"

        val (result, events) = h.runner.submitAnswer()

        assertTrue(result.accepted)
        assertTrue(result.needsSubLessonComplete)
        assertTrue(result.needsSaveProgress)
        assertEquals(listOf(SessionEvent.PlaySuccess, SessionEvent.RecordCardShow(cards[1])), events)
        assertEquals(1, h.state.writes.size)
        val completed = h.state.uiState.value.cardSession
        assertEquals(1, completed.correctCount)
        assertEquals(1, completed.subLessonFinishedToken)
        assertEquals(null, completed.currentCard)
        assertEquals(SessionState.PAUSED, completed.sessionState)
    }

    @Test
    fun `explicit next on final daily card completes without counting a correct answer`() {
        val h = harness(TrainingScreenMode.DAILY_TRANSLATE, index = 1)

        val events = h.runner.nextCard()

        assertEquals(listOf(SessionEvent.SaveProgress), events)
        assertEquals(1, h.state.writes.size)
        val completed = h.state.uiState.value.cardSession
        assertEquals(0, completed.correctCount)
        assertEquals(1, completed.subLessonFinishedToken)
        assertEquals(null, completed.currentCard)
        assertEquals(SessionState.PAUSED, completed.sessionState)
    }

    @Test
    fun `voice middle answer advances with one new recognition trigger`() {
        val h = harness(TrainingScreenMode.DAILY_VERBS, inputMode = InputMode.VOICE)
        h.input.value = "alpha"

        val (result, _) = h.runner.submitAnswer()

        assertTrue(result.accepted)
        assertEquals(listOf(0, 1), h.state.writes.map { it.voiceTriggerToken })
        assertEquals(cards[1], h.state.uiState.value.cardSession.currentCard)
    }

    @Test
    fun `third wrong answer shows hint without advancing`() {
        val h = harness(TrainingScreenMode.VERB_DRILL)
        listOf("wrong one", "wrong two", "wrong three").forEach { answer ->
            h.input.value = answer
            val (result, events) = h.runner.submitAnswer()
            assertFalse(result.accepted)
            assertEquals(listOf(SessionEvent.PlayError, SessionEvent.SaveProgress), events)
        }

        val current = h.state.uiState.value.cardSession
        assertEquals(3, current.incorrectCount)
        assertEquals(0, current.currentIndex)
        assertEquals(SessionState.HINT_SHOWN, current.sessionState)
        assertEquals("alpha", current.answerText)
    }

    @Test
    fun `starting and exiting a linear session reset its counters and mode`() {
        val h = harness(TrainingScreenMode.NORMAL)
        h.state.updateState {
            it.copy(
                cardSession = it.cardSession.copy(correctCount = 4, activeTimeMs = 9_000),
                boss = it.boss.copy(bossActive = true),
                elite = it.elite.copy(eliteActive = true)
            )
        }
        h.input.value = "stale answer"

        assertEquals(listOf(SessionEvent.SaveProgress), h.runner.startCardSession(cards, TrainingScreenMode.DAILY_TRANSLATE))
        val started = h.state.uiState.value
        assertEquals(TrainingScreenMode.DAILY_TRANSLATE, started.cardSession.screenMode)
        assertEquals(cards[0], started.cardSession.currentCard)
        assertEquals(0, started.cardSession.correctCount)
        assertEquals(0L, started.cardSession.activeTimeMs)
        assertFalse(started.boss.bossActive)
        assertFalse(started.elite.eliteActive)
        assertEquals("", h.input.value)

        assertEquals(listOf(SessionEvent.SaveProgress), h.runner.exitCardSession())
        val exited = h.state.uiState.value.cardSession
        assertEquals(TrainingScreenMode.NORMAL, exited.screenMode)
        assertEquals(null, exited.currentCard)
        assertEquals(SessionState.PAUSED, exited.sessionState)
        assertEquals(0, exited.subLessonTotal)
        assertTrue(h.runner.getSessionCards().isEmpty())
    }

    @Test
    fun `replacing linear cards resets counts but preserves screen mode`() {
        val h = harness(TrainingScreenMode.VERB_DRILL, index = 1)
        h.state.updateState { it.copy(cardSession = it.cardSession.copy(correctCount = 2, activeTimeMs = 5_000)) }
        h.input.value = "stale answer"
        val replacement = listOf(SentenceCard("linear-new", "new", listOf("gamma")))

        assertEquals(listOf(SessionEvent.SaveProgress), h.runner.replaceCards(replacement))
        val current = h.state.uiState.value.cardSession
        assertEquals(TrainingScreenMode.VERB_DRILL, current.screenMode)
        assertEquals(replacement[0], current.currentCard)
        assertEquals(0, current.currentIndex)
        assertEquals(0, current.correctCount)
        assertEquals(0L, current.activeTimeMs)
        assertEquals(1, current.subLessonTotal)
        assertEquals("", h.input.value)
        h.runner.pauseTimer()
    }

    @Test
    fun `same correct text on a new card is accepted inside dedup window`() {
        val repeatedAnswerCards = listOf(
            SentenceCard("repeat-1", "first", listOf("alpha")),
            SentenceCard("repeat-2", "second", listOf("alpha"))
        )
        val h = harness(TrainingScreenMode.VERB_DRILL, cards = repeatedAnswerCards)
        h.input.value = "alpha"
        assertTrue(h.runner.submitAnswer().first.accepted)
        h.input.value = "alpha"

        val second = h.runner.submitAnswer()

        assertTrue("a new card must allow the same answer text", second.first.accepted)
        assertEquals(2, h.state.uiState.value.cardSession.correctCount)
        assertEquals(null, h.state.uiState.value.cardSession.currentCard)
    }

    @Test
    fun `duplicate submission on the same card is still suppressed`() {
        val h = harness(TrainingScreenMode.VERB_DRILL)
        h.input.value = "wrong"
        assertFalse(h.runner.submitAnswer().first.accepted)

        val duplicate = h.runner.submitAnswer()

        assertFalse(duplicate.first.accepted)
        assertTrue(duplicate.second.isEmpty())
        assertEquals(1, h.state.uiState.value.cardSession.incorrectCount)
    }

    @Test
    fun `voice duplicate remains suppressed after advancing to a new card`() {
        val repeatedAnswerCards = listOf(
            SentenceCard("voice-1", "first", listOf("alpha")),
            SentenceCard("voice-2", "second", listOf("alpha"))
        )
        val h = harness(TrainingScreenMode.DAILY_TRANSLATE, inputMode = InputMode.VOICE, cards = repeatedAnswerCards)
        h.input.value = "alpha"
        assertTrue(h.runner.submitAnswer().first.accepted)
        h.input.value = "alpha"

        val duplicate = h.runner.submitAnswer()

        assertFalse(duplicate.first.accepted)
        assertTrue(duplicate.second.isEmpty())
        assertEquals(1, h.state.uiState.value.cardSession.correctCount)
        assertEquals(repeatedAnswerCards[1], h.state.uiState.value.cardSession.currentCard)
    }

    @Test
    fun `same keyboard answer is accepted after replacing a deck with the same card id`() {
        val original = listOf(SentenceCard("reused-id", "first", listOf("alpha")))
        val h = harness(TrainingScreenMode.VERB_DRILL, cards = original)
        h.input.value = "alpha"
        assertTrue(h.runner.submitAnswer().first.accepted)
        h.runner.replaceCards(listOf(SentenceCard("reused-id", "new batch", listOf("alpha"))))
        h.input.value = "alpha"

        val newBatchAnswer = h.runner.submitAnswer()

        assertTrue(newBatchAnswer.first.accepted)
        assertEquals(1, h.state.uiState.value.cardSession.correctCount)
        h.runner.pauseTimer()
    }
}
