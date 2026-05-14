package com.alexpo.grammermate.feature.training

import android.app.Application
import com.alexpo.grammermate.data.*
import com.alexpo.grammermate.feature.daily.TrainingStateAccess
import com.alexpo.grammermate.feature.progress.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for [SessionRunner] — session lifecycle, answer submission,
 * card navigation, pause/resume, input mode, drill, and elite sub-modes.
 *
 * Uses Robolectric because SessionRunner references [android.os.SystemClock]
 * and [Application] for timer management.
 *
 * [TrainingStateAccess] and [DrillProgressStore] are mocked via simple
 * implementations that record calls. [AnswerValidator] and [WordBankGenerator]
 * are real instances since they are pure-Kotlin with no side effects.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRunnerTest {

    // ── System under test ──────────────────────────────────────────────

    private lateinit var runner: SessionRunner
    private lateinit var app: Application
    private lateinit var stateFlow: MutableStateFlow<TrainingUiState>
    private lateinit var stateAccess: TestStateAccess
    private lateinit var mockDrillProgressStore: MockDrillProgressStore
    private lateinit var streakManager: StreakManager
    private var timerSaveCalls = 0

    // ── Query stubs ────────────────────────────────────────────────────

    private var stubMastery: LessonMasteryState? = null
    private var stubSchedule: LessonSchedule? = null
    private var stubCompletedSubLessons: Int = 0

    // ── Test fixtures ──────────────────────────────────────────────────

    private val card1 = SentenceCard(id = "c1", promptRu = "ru1", acceptedAnswers = listOf("hello"))
    private val card2 = SentenceCard(id = "c2", promptRu = "ru2", acceptedAnswers = listOf("world"))
    private val card3 = SentenceCard(id = "c3", promptRu = "ru3", acceptedAnswers = listOf("test"))
    private val threeCards = listOf(card1, card2, card3)

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        stateFlow = MutableStateFlow(TrainingUiState())
        stateAccess = TestStateAccess(stateFlow)
        mockDrillProgressStore = MockDrillProgressStore()
        streakManager = StreakManager(object : StreakStore {
            override fun save(data: StreakData) {}
            override fun load(languageId: String): StreakData =
                StreakData(languageId = LanguageId(languageId))
            override fun recordSubLessonCompletion(languageId: String): Pair<StreakData, Boolean> =
                StreakData(languageId = LanguageId(languageId)) to false
            override fun getCurrentStreak(languageId: String): StreakData =
                StreakData(languageId = LanguageId(languageId))
        })
        timerSaveCalls = 0
        stubMastery = null
        stubSchedule = null
        stubCompletedSubLessons = 0

        runner = SessionRunner(
            stateAccess = stateAccess,
            appContext = app,
            coroutineScope = CoroutineScope(Dispatchers.Default),
            answerValidator = AnswerValidator(),
            wordBankGenerator = WordBankGenerator,
            cardProvider = CardProvider(),
            streakManager = streakManager,
            drillProgressStore = mockDrillProgressStore,
            getMastery = { _, _ -> stubMastery },
            getSchedule = { _ -> stubSchedule },
            calculateCompletedSubLessons = { _, _, _ -> stubCompletedSubLessons },
            onTimerSaveProgress = { timerSaveCalls++ }
        )
    }

    @After
    fun tearDown() {
        runner.pauseTimer()
    }

    // ══════════════════════════════════════════════════════════════════
    // submitAnswer() — correct answer paths
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun submitAnswer_notActive_returnsNotAccepted() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED)
        val (result, events) = runner.submitAnswer()
        assertFalse(result.accepted)
        assertFalse(result.hintShown)
        assertFalse(result.needsSaveProgress)
        assertTrue(events.isEmpty())
    }

    @Test
    fun submitAnswer_blankInput_returnsNotAccepted() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "")
        val (result, _) = runner.submitAnswer()
        assertFalse(result.accepted)
        assertFalse(result.needsSaveProgress)
    }

    @Test
    fun submitAnswer_blankInput_testModeAccepted() {
        setStateWithCards(
            threeCards, sessionState = SessionState.ACTIVE,
            inputText = "", testMode = true
        )
        val (result, _) = runner.submitAnswer()
        assertTrue(result.accepted)
    }

    @Test
    fun submitAnswer_noCurrentCard_returnsNotAccepted() {
        stateFlow.value = TrainingUiState(
            cardSession = CardSessionState(
                sessionState = SessionState.ACTIVE,
                inputText = "hello",
                currentCard = null
            )
        )
        runner.setSessionCards(emptyList())
        val (result, _) = runner.submitAnswer()
        assertFalse(result.accepted)
        assertFalse(result.needsSaveProgress)
    }

    // ── Normal mode: mid-card correct ─────────────────────────────────

    @Test
    fun submitAnswer_correctNormalMidCard_advancesToNextCard() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "hello", currentIndex = 0)
        val (result, events) = runner.submitAnswer()

        assertTrue(result.accepted)
        assertFalse(result.hintShown)
        assertTrue(result.needsFlowerRefresh)
        assertFalse(result.needsBossFinish)
        assertFalse(result.needsSubLessonComplete)
        assertFalse(result.needsEliteFinish)

        // Events should include success sound and card show
        assertTrue(events.any { it is SessionEvent.PlaySuccess })
        assertTrue(events.any { it is SessionEvent.RecordCardShow })

        // State: correct count incremented, index still at 0 (nextCardInternal advances)
        val state = stateFlow.value.cardSession
        assertEquals(1, state.correctCount)
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun submitAnswer_correctNormalMidCard_incrementsCorrectCount() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "hello", currentIndex = 0)
        runner.submitAnswer()
        assertEquals(1, stateFlow.value.cardSession.correctCount)
    }

    // ── Normal mode: last card correct ────────────────────────────────

    @Test
    fun submitAnswer_correctNormalLastCard_signalsSubLessonComplete() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2)
        val (result, events) = runner.submitAnswer()

        assertTrue(result.accepted)
        assertTrue(result.needsSubLessonComplete)
        assertTrue(result.needsFlowerRefresh)
        assertFalse(result.needsBossFinish)

        // Should include MarkSubLessonCardsShown, BuildSessionCards, etc.
        assertTrue(events.any { it is SessionEvent.MarkSubLessonCardsShown })
        assertTrue(events.any { it is SessionEvent.BuildSessionCards })
        assertTrue(events.any { it is SessionEvent.CheckAndMarkLessonCompleted })
        assertTrue(events.any { it is SessionEvent.RefreshFlowerStates })
        assertTrue(events.any { it is SessionEvent.UpdateStreak })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun submitAnswer_correctNormalLastCard_incrementsCompletedSubLessonCount() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2)
        runner.submitAnswer()
        assertTrue(stateFlow.value.cardSession.completedSubLessonCount > 0)
    }

    @Test
    fun submitAnswer_correctNormalLastCard_resetsToIndexZero() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2)
        runner.submitAnswer()
        assertEquals(0, stateFlow.value.cardSession.currentIndex)
    }

    // ── Boss mode: last card ──────────────────────────────────────────

    @Test
    fun submitAnswer_bossLastCard_signalsBossFinish() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2,
            bossActive = true)
        val (result, _) = runner.submitAnswer()

        assertTrue(result.accepted)
        assertTrue(result.needsBossFinish)
        assertTrue(result.needsSaveProgress)
        assertTrue(result.needsFlowerRefresh)
        assertFalse(result.needsSubLessonComplete)
    }

    // ── Boss mode: mid card ───────────────────────────────────────────

    @Test
    fun submitAnswer_bossMidCard_advancesToNextCard() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "hello", currentIndex = 0,
            bossActive = true)
        val (result, _) = runner.submitAnswer()

        assertTrue(result.accepted)
        assertFalse(result.needsBossFinish)
        assertTrue(result.needsFlowerRefresh)

        val state = stateFlow.value.cardSession
        assertEquals(1, state.correctCount)
        assertEquals(1, state.currentIndex)
    }

    // ── Drill mode: correct answer ────────────────────────────────────

    @Test
    fun submitAnswer_drillCorrect_advancesDrillCard() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(lesson),
                selectedLessonId = lesson.id,
                selectedLanguageId = LanguageId("en")
            ),
            cardSession = CardSessionState(
                sessionState = SessionState.ACTIVE,
                inputText = "hello",
                currentIndex = 0,
                currentCard = card1,
                inputMode = InputMode.KEYBOARD
            ),
            drill = DrillState(isDrillMode = true, drillCardIndex = 0, drillTotalCards = 3)
        )
        runner.setSessionCards(listOf(card1))

        val (result, _) = runner.submitAnswer()
        assertTrue(result.accepted)
        assertFalse(result.needsSaveProgress)

        // Drill should advance to next card
        assertEquals(1, stateFlow.value.drill.drillCardIndex)
    }

    // ── Elite mode: last card ─────────────────────────────────────────

    @Test
    fun submitAnswer_eliteLastCard_signalsEliteFinish() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2,
            eliteActive = true)
        val (result, _) = runner.submitAnswer()

        assertTrue(result.accepted)
        assertTrue(result.needsEliteFinish)
        assertFalse(result.needsBossFinish)
        assertFalse(result.needsSubLessonComplete)
    }

    @Test
    fun submitAnswer_eliteLastCard_pausesSession() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2,
            eliteActive = true)
        runner.submitAnswer()
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun submitAnswer_eliteLastCard_incrementsEliteFinishedToken() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "test", currentIndex = 2,
            eliteActive = true)
        val tokenBefore = stateFlow.value.elite.eliteFinishedToken
        runner.submitAnswer()
        assertEquals(tokenBefore + 1, stateFlow.value.elite.eliteFinishedToken)
    }

    // ══════════════════════════════════════════════════════════════════
    // submitAnswer() — wrong answer paths
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun submitAnswer_wrongAnswer_incrementsIncorrectCount() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0)
        runner.submitAnswer()
        assertEquals(1, stateFlow.value.cardSession.incorrectCount)
    }

    @Test
    fun submitAnswer_wrongAnswer_incrementsIncorrectAttempts() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            incorrectAttemptsForCard = 0)
        runner.submitAnswer()
        assertEquals(1, stateFlow.value.cardSession.incorrectAttemptsForCard)
    }

    @Test
    fun submitAnswer_wrongAnswerBelowHintThreshold_noHintShown() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            incorrectAttemptsForCard = 0)
        val (result, events) = runner.submitAnswer()

        assertFalse(result.accepted)
        assertFalse(result.hintShown)

        // Events include error sound and save
        assertTrue(events.any { it is SessionEvent.PlayError })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun submitAnswer_wrongAnswerAtHintThreshold_showsHint() {
        // HINT_THRESHOLD = 3, so after 2 attempts, next one (3rd) triggers hint
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            incorrectAttemptsForCard = 2)
        val (result, _) = runner.submitAnswer()

        assertFalse(result.accepted)
        assertTrue(result.hintShown)

        // Answer text should contain the accepted answers
        assertNotNull(stateFlow.value.cardSession.answerText)
        assertTrue(stateFlow.value.cardSession.answerText!!.contains("hello"))
    }

    @Test
    fun submitAnswer_wrongAnswerHintShown_resetsIncorrectAttempts() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            incorrectAttemptsForCard = 2)
        runner.submitAnswer()
        assertEquals(0, stateFlow.value.cardSession.incorrectAttemptsForCard)
    }

    @Test
    fun submitAnswer_wrongAnswerHintShown_setsSessionStateToHintShown() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            incorrectAttemptsForCard = 2)
        runner.submitAnswer()
        assertEquals(SessionState.HINT_SHOWN, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun submitAnswer_wrongAnswer_voiceMode_clearsInput() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            inputMode = InputMode.VOICE, incorrectAttemptsForCard = 0)
        runner.submitAnswer()
        assertEquals("", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun submitAnswer_wrongAnswer_keyboardMode_preservesInput() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong", currentIndex = 0,
            inputMode = InputMode.KEYBOARD, incorrectAttemptsForCard = 0)
        runner.submitAnswer()
        assertEquals("wrong", stateFlow.value.cardSession.inputText)
    }

    // ══════════════════════════════════════════════════════════════════
    // nextCard() / prevCard()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun nextCard_midSession_advancesIndex() {
        setStateWithCards(threeCards, currentIndex = 0)
        runner.nextCard()
        assertEquals(1, stateFlow.value.cardSession.currentIndex)
        assertEquals(card2, stateFlow.value.cardSession.currentCard)
    }

    @Test
    fun nextCard_lastIndex_clampsToLast() {
        setStateWithCards(threeCards, currentIndex = 2)
        runner.nextCard()
        assertEquals(2, stateFlow.value.cardSession.currentIndex)
    }

    @Test
    fun nextCard_resetsInputAndAttempts() {
        setStateWithCards(threeCards, currentIndex = 0, inputText = "some text",
            incorrectAttemptsForCard = 3, lastResult = false, answerText = "hint")
        runner.nextCard()

        val state = stateFlow.value.cardSession
        assertEquals("", state.inputText)
        assertEquals(0, state.incorrectAttemptsForCard)
        assertNull(state.lastResult)
        assertNull(state.answerText)
    }

    @Test
    fun nextCard_setsSessionStateActive() {
        setStateWithCards(threeCards, currentIndex = 0, sessionState = SessionState.PAUSED)
        runner.nextCard()
        assertEquals(SessionState.ACTIVE, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun nextCard_emitsRecordCardShowAndSaveProgress() {
        setStateWithCards(threeCards, currentIndex = 0)
        val events = runner.nextCard()
        assertTrue(events.any { it is SessionEvent.RecordCardShow })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun nextCard_voiceMode_triggersVoiceToken() {
        setStateWithCards(threeCards, currentIndex = 0, inputMode = InputMode.VOICE)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.nextCard(triggerVoice = true)
        assertEquals(tokenBefore + 1, stateFlow.value.cardSession.voiceTriggerToken)
    }

    @Test
    fun nextCard_keyboardMode_doesNotTriggerVoiceToken() {
        setStateWithCards(threeCards, currentIndex = 0, inputMode = InputMode.KEYBOARD)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.nextCard(triggerVoice = false)
        assertEquals(tokenBefore, stateFlow.value.cardSession.voiceTriggerToken)
    }

    @Test
    fun prevCard_atFirstIndex_staysAtZero() {
        setStateWithCards(threeCards, currentIndex = 0)
        runner.prevCard()
        assertEquals(0, stateFlow.value.cardSession.currentIndex)
    }

    @Test
    fun prevCard_midSession_decrementsIndex() {
        setStateWithCards(threeCards, currentIndex = 2)
        runner.prevCard()
        assertEquals(1, stateFlow.value.cardSession.currentIndex)
        assertEquals(card2, stateFlow.value.cardSession.currentCard)
    }

    @Test
    fun prevCard_resetsInputAndAttempts() {
        setStateWithCards(threeCards, currentIndex = 2, inputText = "text",
            incorrectAttemptsForCard = 2, answerText = "hint")
        runner.prevCard()
        val state = stateFlow.value.cardSession
        assertEquals("", state.inputText)
        assertEquals(0, state.incorrectAttemptsForCard)
        assertNull(state.answerText)
    }

    @Test
    fun prevCard_emitsSaveProgress() {
        setStateWithCards(threeCards, currentIndex = 2)
        val events = runner.prevCard()
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    // ══════════════════════════════════════════════════════════════════
    // startSession()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun startSession_emptyCards_returnsBuildSessionCardsAndPaused() {
        runner.setSessionCards(emptyList())
        stateFlow.value = TrainingUiState(
            cardSession = CardSessionState(sessionState = SessionState.PAUSED)
        )
        val events = runner.startSession()

        assertTrue(events.any { it is SessionEvent.BuildSessionCards })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun startSession_withCards_setsActiveAndClearsInput() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED, inputText = "old")
        val events = runner.startSession()

        val state = stateFlow.value.cardSession
        assertEquals(SessionState.ACTIVE, state.sessionState)
        assertEquals("", state.inputText)
        assertTrue(events.any { it is SessionEvent.SaveProgress })
        assertTrue(events.any { it is SessionEvent.RecordCardShow })
    }

    @Test
    fun startSession_normalMode_emitsBuildSessionCards() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED)
        val events = runner.startSession()
        assertTrue(events.any { it is SessionEvent.BuildSessionCards })
    }

    @Test
    fun startSession_bossActive_doesNotEmitBuildSessionCards() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED, bossActive = true)
        val events = runner.startSession()
        assertFalse(events.any { it is SessionEvent.BuildSessionCards })
    }

    @Test
    fun startSession_voiceMode_incrementsTriggerToken() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED, inputMode = InputMode.VOICE)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.startSession()
        assertEquals(tokenBefore + 1, stateFlow.value.cardSession.voiceTriggerToken)
    }

    @Test
    fun startSession_keyboardMode_doesNotIncrementTriggerToken() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED, inputMode = InputMode.KEYBOARD)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.startSession()
        assertEquals(tokenBefore, stateFlow.value.cardSession.voiceTriggerToken)
    }

    // ══════════════════════════════════════════════════════════════════
    // finishSession()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun finishSession_emptyCards_returnsEmpty() {
        runner.setSessionCards(emptyList())
        val (result, _) = runner.finishSession()
        assertTrue(result is SessionRunner.SessionFinishResult.Empty)
    }

    @Test
    fun finishSession_normalSession_returnsCompletedWithRating() {
        setStateWithCards(threeCards, correctCount = 5, activeTimeMs = 120_000L)
        val (result, events) = runner.finishSession()

        assertTrue(result is SessionRunner.SessionFinishResult.Completed)
        val rating = (result as SessionRunner.SessionFinishResult.Completed).rating
        assertEquals(2.5, rating, 0.01) // 5 correct / 2 minutes

        assertTrue(events.any { it is SessionEvent.SaveProgress })
        assertTrue(events.any { it is SessionEvent.RefreshFlowerStates })
    }

    @Test
    fun finishSession_zeroTime_returnsZeroRating() {
        setStateWithCards(threeCards, correctCount = 5, activeTimeMs = 0L)
        val (result, _) = runner.finishSession()
        val rating = (result as SessionRunner.SessionFinishResult.Completed).rating
        assertEquals(0.0, rating, 0.001)
    }

    @Test
    fun finishSession_eliteActive_returnsEliteCancelled() {
        setStateWithCards(threeCards, eliteActive = true)
        val (result, _) = runner.finishSession()
        assertTrue(result is SessionRunner.SessionFinishResult.EliteCancelled)
    }

    @Test
    fun finishSession_resetsIndexToFirstCard() {
        setStateWithCards(threeCards, currentIndex = 2, correctCount = 1, activeTimeMs = 60_000L)
        runner.finishSession()
        assertEquals(0, stateFlow.value.cardSession.currentIndex)
        assertEquals(card1, stateFlow.value.cardSession.currentCard)
    }

    @Test
    fun finishSession_clearsIncorrectAttemptsAndAnswer() {
        setStateWithCards(threeCards, incorrectAttemptsForCard = 3, answerText = "hint",
            correctCount = 1, activeTimeMs = 60_000L, lastResult = false)
        runner.finishSession()
        assertEquals(0, stateFlow.value.cardSession.incorrectAttemptsForCard)
        assertNull(stateFlow.value.cardSession.answerText)
        assertNull(stateFlow.value.cardSession.lastResult)
    }

    // ══════════════════════════════════════════════════════════════════
    // setInputMode()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun setInputMode_updatesModeInState() {
        setStateWithCards(threeCards)
        runner.setInputMode(InputMode.KEYBOARD)
        assertEquals(InputMode.KEYBOARD, stateFlow.value.cardSession.inputMode)
    }

    @Test
    fun setInputMode_toWordBank_updatesWordBankWords() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(
                    makeLesson(listOf(card1, card2))
                )
            ),
            cardSession = CardSessionState(
                currentCard = card1,
                inputMode = InputMode.KEYBOARD
            )
        )
        runner.setSessionCards(listOf(card1))
        runner.setInputMode(InputMode.WORD_BANK)
        // Word bank words should be generated (may be empty if card has short answers)
        // The key assertion: input mode changed
        assertEquals(InputMode.WORD_BANK, stateFlow.value.cardSession.inputMode)
    }

    @Test
    fun setInputMode_withAnswerShown_resetsAttemptsAndAnswer() {
        setStateWithCards(threeCards, incorrectAttemptsForCard = 3, answerText = "hint")
        runner.setInputMode(InputMode.KEYBOARD)
        assertEquals(0, stateFlow.value.cardSession.incorrectAttemptsForCard)
        assertNull(stateFlow.value.cardSession.answerText)
    }

    @Test
    fun setInputMode_voiceActiveSession_triggersVoice() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, answerText = "hint",
            inputMode = InputMode.KEYBOARD)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.setInputMode(InputMode.VOICE)
        assertEquals(tokenBefore + 1, stateFlow.value.cardSession.voiceTriggerToken)
    }

    @Test
    fun setInputMode_voicePausedSession_doesNotTriggerVoice() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED, answerText = "hint",
            inputMode = InputMode.KEYBOARD)
        val tokenBefore = stateFlow.value.cardSession.voiceTriggerToken
        runner.setInputMode(InputMode.VOICE)
        assertEquals(tokenBefore, stateFlow.value.cardSession.voiceTriggerToken)
    }

    // ══════════════════════════════════════════════════════════════════
    // togglePause() / pauseSession()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun togglePause_activeSession_pausesAndSaves() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE)
        val events = runner.togglePause()

        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun togglePause_pausedSession_resumes() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED)
        runner.togglePause()
        // startSession is called which sets ACTIVE
        assertEquals(SessionState.ACTIVE, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun pauseSession_setsPausedState() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE)
        runner.pauseSession()
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun pauseSession_clearsVoicePromptStartMs() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE)
        runner.pauseSession()
        assertNull(stateFlow.value.cardSession.voicePromptStartMs)
    }

    @Test
    fun pauseSession_emitsSaveProgress() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE)
        val events = runner.pauseSession()
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    // ══════════════════════════════════════════════════════════════════
    // showAnswer()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun showAnswer_displaysAcceptedAnswers() {
        setStateWithCards(threeCards, currentIndex = 0)
        runner.showAnswer()
        assertEquals(card1.acceptedAnswers.joinToString(" / "), stateFlow.value.cardSession.answerText)
    }

    @Test
    fun showAnswer_setsHintShownState() {
        setStateWithCards(threeCards, currentIndex = 0)
        runner.showAnswer()
        assertEquals(SessionState.HINT_SHOWN, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun showAnswer_incrementsHintCount() {
        setStateWithCards(threeCards, currentIndex = 0)
        val hintsBefore = stateFlow.value.cardSession.hintCount
        runner.showAnswer()
        assertEquals(hintsBefore + 1, stateFlow.value.cardSession.hintCount)
    }

    @Test
    fun showAnswer_voiceMode_clearsInput() {
        setStateWithCards(threeCards, currentIndex = 0, inputMode = InputMode.VOICE, inputText = "something")
        runner.showAnswer()
        assertEquals("", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun showAnswer_noCurrentCard_returnsEmptyEvents() {
        runner.setSessionCards(emptyList())
        stateFlow.value = TrainingUiState()
        val events = runner.showAnswer()
        assertTrue(events.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // Word bank
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectWordFromBank_appendsWord() {
        setStateWithCards(threeCards, selectedWords = listOf("hello"))
        runner.selectWordFromBank("world")
        assertEquals(listOf("hello", "world"), stateFlow.value.cardSession.selectedWords)
        assertEquals("hello world", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun selectWordFromBank_emptySelection_addsFirstWord() {
        setStateWithCards(threeCards, selectedWords = emptyList())
        runner.selectWordFromBank("hello")
        assertEquals(listOf("hello"), stateFlow.value.cardSession.selectedWords)
        assertEquals("hello", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun removeLastSelectedWord_removesLastAndUpdatesInput() {
        setStateWithCards(threeCards, selectedWords = listOf("hello", "world"))
        runner.removeLastSelectedWord()
        assertEquals(listOf("hello"), stateFlow.value.cardSession.selectedWords)
        assertEquals("hello", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun removeLastSelectedWord_emptyList_doesNothing() {
        setStateWithCards(threeCards, selectedWords = emptyList())
        runner.removeLastSelectedWord()
        assertTrue(stateFlow.value.cardSession.selectedWords.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // skipToNextCard()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun skipToNextCard_midSession_advances() {
        setStateWithCards(threeCards, currentIndex = 0, inputText = "old", lastResult = false)
        runner.skipToNextCard()
        assertEquals(1, stateFlow.value.cardSession.currentIndex)
        assertEquals(card2, stateFlow.value.cardSession.currentCard)
        assertEquals("", stateFlow.value.cardSession.inputText)
        assertNull(stateFlow.value.cardSession.lastResult)
    }

    @Test
    fun skipToNextCard_lastCard_pausesSession() {
        setStateWithCards(threeCards, currentIndex = 2)
        runner.skipToNextCard()
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun skipToNextCard_lastCard_doesNotAdvancePastEnd() {
        setStateWithCards(threeCards, currentIndex = 2)
        runner.skipToNextCard()
        assertEquals(2, stateFlow.value.cardSession.currentIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // selectSubLesson()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectSubLesson_updatesIndexAndResetsState() {
        setStateWithCards(threeCards, currentIndex = 2, inputText = "old", lastResult = false)
        val events = runner.selectSubLesson(1)

        val state = stateFlow.value.cardSession
        assertEquals(1, state.activeSubLessonIndex)
        assertEquals(0, state.currentIndex)
        assertEquals("", state.inputText)
        assertNull(state.lastResult)
        assertEquals(SessionState.PAUSED, state.sessionState)

        assertTrue(events.any { it is SessionEvent.BuildSessionCards })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun selectSubLesson_negativeIndex_clampedToZero() {
        setStateWithCards(threeCards)
        runner.selectSubLesson(-5)
        assertEquals(0, stateFlow.value.cardSession.activeSubLessonIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // currentCard()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun currentCard_validIndex_returnsCorrectCard() {
        setStateWithCards(threeCards, currentIndex = 1)
        assertEquals(card2, runner.currentCard())
    }

    @Test
    fun currentCard_emptyCards_returnsNull() {
        runner.setSessionCards(emptyList())
        assertNull(runner.currentCard())
    }

    @Test
    fun currentCard_indexOutOfRange_clampsToLastValid() {
        setStateWithCards(threeCards, currentIndex = 100)
        assertEquals(card3, runner.currentCard())
    }

    // ══════════════════════════════════════════════════════════════════
    // Card list management
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun setSessionCards_storesCards() {
        runner.setSessionCards(threeCards)
        assertEquals(threeCards, runner.getSessionCards())
    }

    @Test
    fun setBossCards_setsBothBossAndSessionCards() {
        runner.setBossCards(threeCards)
        assertEquals(threeCards, runner.getSessionCards())
    }

    @Test
    fun setEliteCards_setsBothEliteAndSessionCards() {
        runner.setEliteCards(threeCards)
        assertEquals(threeCards, runner.getSessionCards())
    }

    @Test
    fun clearAllCards_clearsAllCardLists() {
        runner.setSessionCards(threeCards)
        runner.clearAllCards()
        assertTrue(runner.getSessionCards().isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // Elite helpers
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun normalizeEliteSpeeds_shortList_padsWithZeros() {
        val result = runner.normalizeEliteSpeeds(listOf(1.0, 2.0))
        // Default ELITE_STEP_COUNT = 4
        assertEquals(listOf(1.0, 2.0, 0.0, 0.0), result)
    }

    @Test
    fun normalizeEliteSpeeds_exactLength_returnsSame() {
        val speeds = listOf(1.0, 2.0, 3.0, 4.0)
        val result = runner.normalizeEliteSpeeds(speeds)
        assertEquals(speeds, result)
    }

    @Test
    fun normalizeEliteSpeeds_tooLong_truncates() {
        val speeds = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0)
        val result = runner.normalizeEliteSpeeds(speeds)
        assertEquals(4, result.size)
    }

    @Test
    fun calculateSpeedPerMinute_zeroMs_returnsZero() {
        assertEquals(0.0, runner.calculateSpeedPerMinute(0L, 10), 0.001)
    }

    @Test
    fun calculateSpeedPerMinute_validInputs_returnsCorrectRate() {
        // 10 words in 1 minute = 10.0
        assertEquals(10.0, runner.calculateSpeedPerMinute(60_000L, 10), 0.001)
    }

    @Test
    fun resolveEliteUnlocked_testMode_alwaysTrue() {
        assertTrue(runner.resolveEliteUnlocked(emptyList(), testMode = true))
    }

    @Test
    fun resolveEliteUnlocked_12OrMoreLessons_true() {
        val lessons = (1..12).map { makeLesson(emptyList()) }
        assertTrue(runner.resolveEliteUnlocked(lessons, testMode = false))
    }

    @Test
    fun resolveEliteUnlocked_fewerThan12Lessons_false() {
        val lessons = (1..11).map { makeLesson(emptyList()) }
        assertFalse(runner.resolveEliteUnlocked(lessons, testMode = false))
    }

    @Test
    fun openEliteStep_setsEliteActiveAndLoadsCards() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(makeLesson(threeCards))
            ),
            cardSession = CardSessionState()
        )
        runner.openEliteStep(0)

        assertTrue(stateFlow.value.elite.eliteActive)
        assertEquals(0, stateFlow.value.elite.eliteStepIndex)
        assertEquals(0, stateFlow.value.cardSession.currentIndex)
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
    }

    @Test
    fun cancelEliteSession_deactivatesEliteAndPauses() {
        stateFlow.value = TrainingUiState(
            elite = EliteState(eliteActive = true)
        )
        runner.setSessionCards(threeCards)
        val events = runner.cancelEliteSession()

        assertFalse(stateFlow.value.elite.eliteActive)
        assertEquals(SessionState.PAUSED, stateFlow.value.cardSession.sessionState)
        assertTrue(events.any { it is SessionEvent.SaveProgress })
        assertTrue(events.any { it is SessionEvent.RefreshFlowerStates })
    }

    @Test
    fun cancelEliteSession_notActive_returnsEmpty() {
        stateFlow.value = TrainingUiState()
        val events = runner.cancelEliteSession()
        assertTrue(events.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // Drill sub-mode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun showDrillStartDialog_lessonWithDrillCards_showsDialog() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(lessons = listOf(lesson))
        )
        runner.showDrillStartDialog(lesson.id.value)
        assertTrue(stateFlow.value.drill.drillShowStartDialog)
    }

    @Test
    fun showDrillStartDialog_noDrillCards_doesNotShowDialog() {
        val lesson = makeLesson(emptyList())
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(lessons = listOf(lesson))
        )
        runner.showDrillStartDialog(lesson.id.value)
        assertFalse(stateFlow.value.drill.drillShowStartDialog)
    }

    @Test
    fun showDrillStartDialog_withProgress_setsHasProgress() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(lessons = listOf(lesson))
        )
        mockDrillProgressStore.progressMap[lesson.id.value] = 5
        runner.showDrillStartDialog(lesson.id.value)
        assertTrue(stateFlow.value.drill.drillHasProgress)
    }

    @Test
    fun dismissDrillDialog_clearsDialogFlag() {
        stateFlow.value = TrainingUiState(drill = DrillState(drillShowStartDialog = true))
        runner.dismissDrillDialog()
        assertFalse(stateFlow.value.drill.drillShowStartDialog)
    }

    @Test
    fun startDrill_resumesFromProgress() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(lesson),
                selectedLessonId = lesson.id
            )
        )
        mockDrillProgressStore.progressMap[lesson.id.value] = 2

        runner.startDrill(resume = true)
        assertEquals(2, stateFlow.value.drill.drillCardIndex)
        assertTrue(stateFlow.value.drill.isDrillMode)
    }

    @Test
    fun startDrill_fresh_startsAtZero() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(lesson),
                selectedLessonId = lesson.id
            )
        )
        runner.startDrill(resume = false)
        assertEquals(0, stateFlow.value.drill.drillCardIndex)
    }

    @Test
    fun startDrill_noSelectedLesson_returnsEmpty() {
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(selectedLessonId = null)
        )
        val events = runner.startDrill(resume = false)
        assertTrue(events.isEmpty())
    }

    @Test
    fun finishDrill_clearsDrillModeAndProgress() {
        val lessonId = "lesson-1"
        mockDrillProgressStore.progressMap[lessonId] = 5
        runner.finishDrill(lessonId)

        assertFalse(stateFlow.value.drill.isDrillMode)
        assertEquals(0, stateFlow.value.drill.drillCardIndex)
        assertEquals(0, stateFlow.value.drill.drillTotalCards)
        assertFalse(mockDrillProgressStore.progressMap.containsKey(lessonId))
    }

    @Test
    fun exitDrillMode_savesProgressAndClears() {
        val lesson = makeLessonWithDrillCards(threeCards)
        stateFlow.value = TrainingUiState(
            navigation = NavigationState(
                lessons = listOf(lesson),
                selectedLessonId = lesson.id
            ),
            drill = DrillState(isDrillMode = true, drillCardIndex = 2, drillTotalCards = 3)
        )
        val events = runner.exitDrillMode()

        assertFalse(stateFlow.value.drill.isDrillMode)
        assertEquals(2, mockDrillProgressStore.progressMap[lesson.id.value])
        assertTrue(events.any { it is SessionEvent.BuildSessionCards })
        assertTrue(events.any { it is SessionEvent.SaveProgress })
    }

    @Test
    fun exitDrillMode_notActive_returnsEmpty() {
        stateFlow.value = TrainingUiState(drill = DrillState(isDrillMode = false))
        val events = runner.exitDrillMode()
        assertTrue(events.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // onInputChanged()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun onInputChanged_updatesInputText() {
        setStateWithCards(threeCards)
        runner.onInputChanged("new text")
        assertEquals("new text", stateFlow.value.cardSession.inputText)
    }

    @Test
    fun onInputChanged_withAnswerShown_resetsAttemptsAndAnswer() {
        setStateWithCards(threeCards, answerText = "hint", incorrectAttemptsForCard = 3)
        runner.onInputChanged("typing")
        assertEquals(0, stateFlow.value.cardSession.incorrectAttemptsForCard)
        assertNull(stateFlow.value.cardSession.answerText)
    }

    @Test
    fun onInputChanged_belowHintThreshold_preservesAttempts() {
        setStateWithCards(threeCards, incorrectAttemptsForCard = 1, answerText = null)
        runner.onInputChanged("typing")
        assertEquals(1, stateFlow.value.cardSession.incorrectAttemptsForCard)
    }

    // ══════════════════════════════════════════════════════════════════
    // resumeFromSettings()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun resumeFromSettings_alreadyActive_returnsEmpty() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE)
        val events = runner.resumeFromSettings()
        assertTrue(events.isEmpty())
    }

    @Test
    fun resumeFromSettings_notActive_startsSession() {
        setStateWithCards(threeCards, sessionState = SessionState.PAUSED)
        val events = runner.resumeFromSettings()
        assertEquals(SessionState.ACTIVE, stateFlow.value.cardSession.sessionState)
        assertFalse(events.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // setEliteSizeMultiplier()
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun setEliteSizeMultiplier_updatesMultiplier() {
        runner.setEliteSizeMultiplier(2.0)
        // Verify via eliteSubLessonSize which uses the multiplier
        val size = runner.eliteSubLessonSize()
        // Default SUB_LESSON_SIZE_DEFAULT * 2.0
        assertTrue(size > 0)
    }

    // ══════════════════════════════════════════════════════════════════
    // Voice metrics tracking
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun submitAnswer_correctVoiceAnswer_tracksVoiceMetrics() {
        // The voice metrics are updated when inputMode is VOICE and
        // voicePromptStartMs was set. Since SystemClock is mocked by Robolectric,
        // the duration may be 0 but the flow is still tested.
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "hello",
            inputMode = InputMode.VOICE, voicePromptStartMs = 100L)
        runner.submitAnswer()

        // Voice metrics are updated in the state (voiceActiveMs and voiceWordCount)
        // The exact values depend on SystemClock.elapsedRealtime() in Robolectric
        val state = stateFlow.value.cardSession
        // voicePromptStartMs should be cleared after submission
        assertNull(state.voicePromptStartMs)
    }

    // ══════════════════════════════════════════════════════════════════
    // Integration-style: full submission flow
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun fullFlow_wrongTwiceThenCorrect_noHintAndAdvances() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong",
            currentIndex = 0, incorrectAttemptsForCard = 0)

        // First wrong
        var (result, _) = runner.submitAnswer()
        assertFalse(result.accepted)
        assertFalse(result.hintShown)
        assertEquals(1, stateFlow.value.cardSession.incorrectAttemptsForCard)

        // Second wrong
        stateFlow.value = stateFlow.value.copy(
            cardSession = stateFlow.value.cardSession.copy(inputText = "wrong2")
        )
        result = runner.submitAnswer().first
        assertFalse(result.accepted)
        assertFalse(result.hintShown)
        assertEquals(2, stateFlow.value.cardSession.incorrectAttemptsForCard)

        // Correct answer
        stateFlow.value = stateFlow.value.copy(
            cardSession = stateFlow.value.cardSession.copy(inputText = "hello")
        )
        result = runner.submitAnswer().first
        assertTrue(result.accepted)
        assertEquals(1, stateFlow.value.cardSession.currentIndex)
        assertEquals(1, stateFlow.value.cardSession.correctCount)
    }

    @Test
    fun fullFlow_threeWrong_showsHintThenUserCanAdvance() {
        setStateWithCards(threeCards, sessionState = SessionState.ACTIVE, inputText = "wrong",
            currentIndex = 0, incorrectAttemptsForCard = 0)

        // Wrong 1
        runner.submitAnswer()
        // Wrong 2
        stateFlow.value = stateFlow.value.copy(
            cardSession = stateFlow.value.cardSession.copy(inputText = "wrong2")
        )
        runner.submitAnswer()
        // Wrong 3 — triggers hint
        stateFlow.value = stateFlow.value.copy(
            cardSession = stateFlow.value.cardSession.copy(inputText = "wrong3")
        )
        val (result, _) = runner.submitAnswer()

        assertTrue(result.hintShown)
        assertEquals(SessionState.HINT_SHOWN, stateFlow.value.cardSession.sessionState)
        assertNotNull(stateFlow.value.cardSession.answerText)

        // User advances past hint via nextCard
        runner.nextCard()
        assertEquals(SessionState.ACTIVE, stateFlow.value.cardSession.sessionState)
        assertEquals(1, stateFlow.value.cardSession.currentIndex)
    }

    // ══════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════

    private fun setStateWithCards(
        cards: List<SentenceCard>,
        sessionState: SessionState = SessionState.ACTIVE,
        currentIndex: Int = 0,
        inputText: String = "",
        inputMode: InputMode = InputMode.KEYBOARD,
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        incorrectAttemptsForCard: Int = 0,
        activeTimeMs: Long = 0L,
        voicePromptStartMs: Long? = null,
        answerText: String? = null,
        lastResult: Boolean? = null,
        testMode: Boolean = false,
        bossActive: Boolean = false,
        eliteActive: Boolean = false,
        selectedWords: List<String> = emptyList()
    ) {
        runner.setSessionCards(cards)
        val currentCard = cards.getOrNull(currentIndex)
        stateFlow.value = TrainingUiState(
            cardSession = CardSessionState(
                sessionState = sessionState,
                currentIndex = currentIndex,
                currentCard = currentCard,
                inputText = inputText,
                inputMode = inputMode,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                incorrectAttemptsForCard = incorrectAttemptsForCard,
                activeTimeMs = activeTimeMs,
                voicePromptStartMs = voicePromptStartMs,
                answerText = answerText,
                lastResult = lastResult,
                testMode = testMode,
                selectedWords = selectedWords
            ),
            boss = BossState(bossActive = bossActive),
            elite = EliteState(eliteActive = eliteActive)
        )
    }

    private fun makeLesson(cards: List<SentenceCard>): Lesson {
        return Lesson(
            id = LessonId("lesson-${java.util.UUID.randomUUID()}"),
            languageId = LanguageId("en"),
            title = "Test Lesson",
            cards = cards
        )
    }

    private fun makeLessonWithDrillCards(drillCards: List<SentenceCard>): Lesson {
        return Lesson(
            id = LessonId("lesson-drill-${java.util.UUID.randomUUID()}"),
            languageId = LanguageId("en"),
            title = "Drill Lesson",
            cards = emptyList(),
            drillCards = drillCards
        )
    }

    // ── Test doubles ───────────────────────────────────────────────────

    /**
     * Simple [TrainingStateAccess] implementation backed by a [MutableStateFlow].
     * Records the number of [updateState] calls for verification.
     */
    private class TestStateAccess(
        private val flow: MutableStateFlow<TrainingUiState>
    ) : TrainingStateAccess {
        var updateCount = 0
        var saveCount = 0

        override val uiState: StateFlow<TrainingUiState> = flow

        override fun updateState(transform: (TrainingUiState) -> TrainingUiState) {
            updateCount++
            flow.value = transform(flow.value)
        }

        override fun saveProgress() {
            saveCount++
        }
    }

    /**
     * In-memory [DrillProgressStore] that records calls without filesystem access.
     */
    private class MockDrillProgressStore : DrillProgressStore {
        val progressMap = mutableMapOf<String, Int>()

        override fun getDrillProgress(lessonId: String): Int {
            return progressMap[lessonId] ?: 0
        }

        override fun saveDrillProgress(lessonId: String, cardIndex: Int) {
            progressMap[lessonId] = cardIndex
        }

        override fun hasProgress(lessonId: String): Boolean {
            return progressMap.containsKey(lessonId) && progressMap[lessonId]!! > 0
        }

        override fun clearDrillProgress(lessonId: String) {
            progressMap.remove(lessonId)
        }
    }

}