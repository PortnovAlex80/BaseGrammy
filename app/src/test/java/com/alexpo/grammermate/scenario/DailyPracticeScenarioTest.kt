package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.data.VerbDrillCard
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.SessionRunner
import com.alexpo.grammermate.feature.training.WordBankGenerator
import com.alexpo.grammermate.testharness.FakeMasteryStore
import com.alexpo.grammermate.testharness.FakeProgressStore
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Test

/**
 * Scenario test for Daily Practice session flow.
 *
 * Tests the complete flow of a daily practice session using real SessionRunner
 * with in-memory fake implementations for all dependencies.
 *
 * Daily Practice consists of 3 blocks:
 * 1. TRANSLATE (10 sentence cards) - rendered via TrainingScreen with DAILY_TRANSLATE mode
 * 2. VOCAB (5 flashcard cards) - rendered inline within DailyPracticeScreen (not tested here)
 * 3. VERBS (10 verb conjugation cards) - rendered via TrainingScreen with DAILY_VERBS mode
 *
 * This test focuses on the translate and verbs blocks which use SessionRunner.
 *
 * Scenario:
 * 1. Start daily translate session (via startDailyTranslateSession)
 * 2. Verify: screenMode=DAILY_TRANSLATE, cards loaded, session ACTIVE
 * 3. Answer correctly → verify advances
 * 4. Complete block → verify subLessonFinishedToken incremented
 * 5. Start daily verbs session (via startDailyVerbsSession)
 * 6. Verify: verb conjugation cards loaded, screenMode=DAILY_VERBS
 * 7. Complete verbs block
 * 8. Exit daily session (via exitDailySession)
 * 9. Verify: screenMode=NORMAL, session cleared, currentCard=null
 *
 * Uses no mocks — only real SessionRunner + in-memory fakes.
 */
@RunWith(RobolectricTestRunner::class)
class DailyPracticeScenarioTest {

    private lateinit var stateAccess: FakeTrainingStateAccess
    private lateinit var masteryStore: FakeMasteryStore
    private lateinit var progressStore: FakeProgressStore
    private lateinit var streakStore: FakeStreakStore
    private lateinit var sessionRunner: SessionRunner
    private lateinit var answerValidator: AnswerValidator
    private lateinit var streakManager: StreakManager
    private lateinit var cardProvider: CardProvider

    private val testLanguageId = LanguageId("en")
    private val coroutineScope = CoroutineScope(Dispatchers.Unconfined)

    // Helper to create a sentence card for translation block
    private fun createSentenceCard(id: String, ru: String, answers: List<String>): SentenceCard {
        return SentenceCard(id = id, promptRu = ru, acceptedAnswers = answers)
    }

    // Helper to create a verb drill card for verbs block
    private fun createVerbCard(id: String, ru: String, answer: String, verb: String, tense: String): VerbDrillCard {
        return VerbDrillCard(
            id = id,
            promptRu = ru,
            answer = answer,
            verb = verb,
            tense = tense,
            group = "group-1"
        )
    }

    @Before
    fun setup() {
        // Initialize all fake stores
        masteryStore = FakeMasteryStore()
        progressStore = FakeProgressStore()
        streakStore = FakeStreakStore()

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = com.alexpo.grammermate.data.NavigationState(
                selectedLanguageId = testLanguageId
            )
        )
        stateAccess = FakeTrainingStateAccess(initialState)

        // Initialize core components
        answerValidator = AnswerValidator()
        streakManager = StreakManager(streakStore)
        cardProvider = CardProvider()

        // Initialize SessionRunner with all dependencies
        sessionRunner = SessionRunner(
            stateAccess = stateAccess,
            appContext = createFakeApplicationContext(),
            coroutineScope = coroutineScope,
            answerValidator = answerValidator,
            wordBankGenerator = WordBankGenerator,
            cardProvider = cardProvider,
            streakManager = streakManager,
            drillProgressStore = drillProgressStore,
            getMastery = { _, _ -> null },
            getSchedule = { null },
            calculateCompletedSubLessons = { _, _, _ -> 0 },
            onTimerSaveProgress = { /* No-op for test */ },
            sessionTimerMsSink = null
        )
    }

    // ========================================
    // SCENARIO: Daily Practice translate block
    // ========================================

    @Test
    fun scenario_dailyPractice_startTranslateSession() = runBlocking {
        // --- SETUP: Create 10 sentence cards for translate block ---
        val translateCards = (1..10).map { i ->
            createSentenceCard(
                id = "trans-$i",
                ru = "русское предложение $i",
                answers = listOf("english sentence $i")
            )
        }

        // --- ACTION: Start daily translate session ---
        val events = sessionRunner.startDailyTranslateSession(translateCards)

        // --- ASSERT: Verify screen mode is DAILY_TRANSLATE ---
        assertEquals(
            "Screen mode should be DAILY_TRANSLATE",
            TrainingScreenMode.DAILY_TRANSLATE,
            stateAccess.uiState.value.cardSession.screenMode
        )

        // --- ASSERT: Verify session is ACTIVE ---
        assertEquals(
            "Session should be ACTIVE",
            SessionState.ACTIVE,
            stateAccess.uiState.value.cardSession.sessionState
        )

        // --- ASSERT: Verify first card is loaded ---
        assertNotNull("Current card should not be null", stateAccess.uiState.value.cardSession.currentCard)
        assertEquals(
            "First card should be trans-1",
            "trans-1",
            stateAccess.uiState.value.cardSession.currentCard?.id
        )

        // --- ASSERT: Verify cursor is at index 0 ---
        assertEquals(
            "Initial cursor should be at index 0",
            0,
            stateAccess.uiState.value.cardSession.currentIndex
        )

        // --- ASSERT: Verify sub-lesson total is set to card count ---
        assertEquals(
            "Sub-lesson total should match card count",
            translateCards.size,
            stateAccess.uiState.value.cardSession.subLessonTotal
        )

        // --- ASSERT: Verify counters are reset ---
        assertEquals(
            "Correct count should be 0",
            0,
            stateAccess.uiState.value.cardSession.correctCount
        )
        assertEquals(
            "Incorrect count should be 0",
            0,
            stateAccess.uiState.value.cardSession.incorrectCount
        )
    }

    @Test
    fun scenario_dailyPractice_translateBlock_answerAndAdvance() = runBlocking {
        // --- SETUP: Start translate session with 3 cards ---
        val translateCards = (1..3).map { i ->
            createSentenceCard(
                id = "trans-$i",
                ru = "русское $i",
                answers = listOf("english $i")
            )
        }
        sessionRunner.startDailyTranslateSession(translateCards)

        // --- ACTION 1: Answer first card correctly ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 1")
        ) }
        var (result, events) = sessionRunner.submitAnswer()

        // --- ASSERT 1: Answer accepted ---
        assertTrue("First answer should be accepted", result.accepted)
        assertFalse("No hint should be shown", result.hintShown)

        // --- ASSERT 2: Cursor advanced to card 2 ---
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on trans-2", "trans-2", stateAccess.uiState.value.cardSession.currentCard?.id)

        // --- ACTION 2: Answer second card correctly ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 2")
        ) }
        result = sessionRunner.submitAnswer().first

        // --- ASSERT 3: Cursor advanced to card 3 ---
        assertEquals("Should advance to card 3", 2, stateAccess.uiState.value.cardSession.currentIndex)

        // --- ACTION 3: Answer third (last) card correctly ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 3")
        ) }
        result = sessionRunner.submitAnswer().first

        // --- ASSERT 4: Last card signals completion ---
        assertTrue("Last answer should be accepted", result.accepted)
        assertTrue("Last card should signal sub-lesson complete", result.needsSubLessonComplete)

        // --- ASSERT 5: Session is PAUSED after completion ---
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertNull("Current card should be null after completion", stateAccess.uiState.value.cardSession.currentCard)

        // --- ASSERT 6: subLessonFinishedToken incremented ---
        assertTrue(
            "subLessonFinishedToken should be incremented",
            stateAccess.uiState.value.cardSession.subLessonFinishedToken > 0
        )

        // --- ASSERT 7: All cards counted as correct ---
        assertEquals("All 3 cards should be correct", 3, stateAccess.uiState.value.cardSession.correctCount)
    }

    @Test
    fun scenario_dailyPractice_translateBlock_wrongAnswerStaysOnCard() = runBlocking {
        // --- SETUP: Start translate session ---
        val translateCards = listOf(
            createSentenceCard("trans-1", "русское 1", listOf("english 1")),
            createSentenceCard("trans-2", "русское 2", listOf("english 2"))
        )
        sessionRunner.startDailyTranslateSession(translateCards)

        // --- ACTION: Submit wrong answer ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong answer")
        ) }
        val (result, events) = sessionRunner.submitAnswer()

        // --- ASSERT: Answer rejected, cursor stayed ---
        assertFalse("Wrong answer should be rejected", result.accepted)
        assertEquals("Should stay on card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should still be on trans-1", "trans-1", stateAccess.uiState.value.cardSession.currentCard?.id)

        // --- ASSERT: Incorrect count increased ---
        assertEquals("Incorrect count should be 1", 1, stateAccess.uiState.value.cardSession.incorrectCount)

        // --- ACTION: Submit correct answer ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 1")
        ) }
        val (correctResult, _) = sessionRunner.submitAnswer()

        // --- ASSERT: Now cursor advances ---
        assertTrue("Correct answer should be accepted", correctResult.accepted)
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
    }

    // ========================================
    // SCENARIO: Daily Practice verbs block
    // ========================================

    @Test
    fun scenario_dailyPractice_startVerbsSession() = runBlocking {
        // --- SETUP: Create 10 verb conjugation cards for verbs block ---
        val verbCards = (1..10).map { i ->
            createVerbCard(
                id = "verb-$i",
                ru = "я глагол $i",
                answer = "io verbo $i",
                verb = "verbo$i",
                tense = "present"
            )
        }

        // --- ACTION: Start daily verbs session ---
        val events = sessionRunner.startDailyVerbsSession(verbCards)

        // --- ASSERT: Verify screen mode is DAILY_VERBS ---
        assertEquals(
            "Screen mode should be DAILY_VERBS",
            TrainingScreenMode.DAILY_VERBS,
            stateAccess.uiState.value.cardSession.screenMode
        )

        // --- ASSERT: Verify session is ACTIVE ---
        assertEquals(
            "Session should be ACTIVE",
            SessionState.ACTIVE,
            stateAccess.uiState.value.cardSession.sessionState
        )

        // --- ASSERT: Verify first card is loaded ---
        assertNotNull("Current card should not be null", stateAccess.uiState.value.cardSession.currentCard)
        assertEquals(
            "First card should be verb-1",
            "verb-1",
            stateAccess.uiState.value.cardSession.currentCard?.id
        )

        // --- ASSERT: Verify verb conjugation cards are stored in state ---
        assertEquals(
            "verbConjugationCards should contain all verb cards",
            verbCards.size,
            stateAccess.uiState.value.cardSession.verbConjugationCards.size
        )

        // --- ASSERT: Verify counters are reset ---
        assertEquals(
            "Correct count should be 0",
            0,
            stateAccess.uiState.value.cardSession.correctCount
        )
    }

    @Test
    fun scenario_dailyPractice_verbsBlock_completeFlow() = runBlocking {
        // --- SETUP: Start verbs session with 3 verb cards ---
        val verbCards = (1..3).map { i ->
            createVerbCard(
                id = "verb-$i",
                ru = "я глагол $i",
                answer = "io verbo $i",
                verb = "verbo$i",
                tense = "present"
            )
        }
        sessionRunner.startDailyVerbsSession(verbCards)

        // --- ACTION: Complete all verb cards ---
        for (i in 1..3) {
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "io verbo $i")
            ) }
            val (result, _) = sessionRunner.submitAnswer()
            assertTrue("Card $i answer should be accepted", result.accepted)
        }

        // --- ASSERT: All cards completed, session PAUSED ---
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertNull("Current card should be null", stateAccess.uiState.value.cardSession.currentCard)
        assertEquals("All 3 cards should be correct", 3, stateAccess.uiState.value.cardSession.correctCount)
        assertTrue("subLessonFinishedToken should be incremented", stateAccess.uiState.value.cardSession.subLessonFinishedToken > 0)
    }

    // ========================================
    // SCENARIO: Daily Practice session exit
    // ========================================

    @Test
    fun scenario_dailyPractice_exitSession() = runBlocking {
        // --- SETUP: Start and partially complete a translate session ---
        val translateCards = (1..5).map { i ->
            createSentenceCard(
                id = "trans-$i",
                ru = "русское $i",
                answers = listOf("english $i")
            )
        }
        sessionRunner.startDailyTranslateSession(translateCards)

        // Answer first card correctly
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 1")
        ) }
        sessionRunner.submitAnswer()

        // Verify session is active and on card 2
        assertEquals("Should be on card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Screen mode should be DAILY_TRANSLATE", TrainingScreenMode.DAILY_TRANSLATE, stateAccess.uiState.value.cardSession.screenMode)

        // --- ACTION: Exit daily session ---
        val exitEvents = sessionRunner.exitDailySession()

        // --- ASSERT: Screen mode reset to NORMAL ---
        assertEquals(
            "Screen mode should be NORMAL after exit",
            TrainingScreenMode.NORMAL,
            stateAccess.uiState.value.cardSession.screenMode
        )

        // --- ASSERT: Session is PAUSED ---
        assertEquals(
            "Session should be PAUSED after exit",
            SessionState.PAUSED,
            stateAccess.uiState.value.cardSession.sessionState
        )

        // --- ASSERT: Current card cleared ---
        assertNull(
            "Current card should be null after exit",
            stateAccess.uiState.value.cardSession.currentCard
        )

        // --- ASSERT: Cursor reset to 0 ---
        assertEquals(
            "Cursor should be reset to 0",
            0,
            stateAccess.uiState.value.cardSession.currentIndex
        )

        // --- ASSERT: Input text cleared ---
        assertEquals(
            "Input text should be empty",
            "",
            stateAccess.uiState.value.cardSession.inputText
        )
    }

    // ========================================
    // SCENARIO: Full daily practice flow (translate → verbs → exit)
    // ========================================

    @Test
    fun scenario_dailyPractice_fullFlow() = runBlocking {
        // --- STEP 1: Start and complete translate block ---
        val translateCards = (1..3).map { i ->
            createSentenceCard(
                id = "trans-$i",
                ru = "русское $i",
                answers = listOf("english $i")
            )
        }

        sessionRunner.startDailyTranslateSession(translateCards)

        // Verify translate session started
        assertEquals("Screen mode should be DAILY_TRANSLATE", TrainingScreenMode.DAILY_TRANSLATE, stateAccess.uiState.value.cardSession.screenMode)
        var initialToken = stateAccess.uiState.value.cardSession.subLessonFinishedToken

        // Complete all translate cards
        for (i in 1..3) {
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english $i")
            ) }
            sessionRunner.submitAnswer()
        }

        // Verify translate block completed
        assertTrue("subLessonFinishedToken should increment after translate block",
            stateAccess.uiState.value.cardSession.subLessonFinishedToken > initialToken)

        // --- STEP 2: Start and complete verbs block ---
        val verbCards = (1..3).map { i ->
            createVerbCard(
                id = "verb-$i",
                ru = "я глагол $i",
                answer = "io verbo $i",
                verb = "verbo$i",
                tense = "present"
            )
        }

        sessionRunner.startDailyVerbsSession(verbCards)

        // Verify verbs session started
        assertEquals("Screen mode should be DAILY_VERBS", TrainingScreenMode.DAILY_VERBS, stateAccess.uiState.value.cardSession.screenMode)
        initialToken = stateAccess.uiState.value.cardSession.subLessonFinishedToken

        // Complete all verb cards
        for (i in 1..3) {
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "io verbo $i")
            ) }
            sessionRunner.submitAnswer()
        }

        // Verify verbs block completed
        assertTrue("subLessonFinishedToken should increment after verbs block",
            stateAccess.uiState.value.cardSession.subLessonFinishedToken > initialToken)

        // --- STEP 3: Exit daily session ---
        sessionRunner.exitDailySession()

        // Verify final state
        assertEquals("Screen mode should be NORMAL", TrainingScreenMode.NORMAL, stateAccess.uiState.value.cardSession.screenMode)
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertNull("Current card should be null", stateAccess.uiState.value.cardSession.currentCard)
    }

    // ========================================
    // SCENARIO: Input mode handling in daily practice
    // ========================================

    @Test
    fun scenario_dailyPractice_voiceModeTrigger() = runBlocking {
        // --- SETUP: Start translate session in VOICE mode ---
        val translateCards = listOf(
            createSentenceCard("trans-1", "русское 1", listOf("english 1"))
        )

        // Set input mode to VOICE before starting
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startDailyTranslateSession(translateCards)

        // --- ASSERT: Voice trigger token is set ---
        val voiceTriggerToken = stateAccess.uiState.value.cardSession.voiceTriggerToken
        assertTrue("Voice trigger token should be > 0 in VOICE mode", voiceTriggerToken > 0)

        // --- ACTION: Submit correct answer ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 1")
        ) }
        val (result, _) = sessionRunner.submitAnswer()

        // --- ASSERT: Answer accepted ---
        assertTrue("Correct answer should be accepted", result.accepted)
    }

    @Test
    fun scenario_dailyPractice_wordBankMode() = runBlocking {
        // --- SETUP: Start translate session in WORD_BANK mode ---
        val translateCards = listOf(
            createSentenceCard("trans-1", "русское 1", listOf("english word")),
            createSentenceCard("trans-2", "русское 2", listOf("another answer"))
        )

        sessionRunner.startDailyTranslateSession(translateCards)
        sessionRunner.setInputMode(InputMode.WORD_BANK)

        // --- ASSERT: Word bank is populated ---
        assertTrue(
            "Word bank should be populated in WORD_BANK mode",
            stateAccess.uiState.value.cardSession.wordBankWords.isNotEmpty()
        )

        // --- ACTION: Select word from bank ---
        sessionRunner.selectWordFromBank("english word")

        // --- ASSERT: Input text updated ---
        assertEquals(
            "Input text should contain selected word",
            "english word",
            stateAccess.uiState.value.cardSession.inputText
        )

        // --- ACTION: Submit answer ---
        val (result, _) = sessionRunner.submitAnswer()

        // --- ASSERT: Answer accepted ---
        assertTrue("Answer from word bank should be accepted", result.accepted)
    }

    // ========================================
    // SCENARIO: Session state transitions
    // ========================================

    @Test
    fun scenario_dailyPractice_pauseAndResume() = runBlocking {
        // --- SETUP: Start translate session ---
        val translateCards = listOf(
            createSentenceCard("trans-1", "русское 1", listOf("english 1"))
        )
        sessionRunner.startDailyTranslateSession(translateCards)

        // --- ASSERT: Session is ACTIVE ---
        assertEquals("Session should be ACTIVE", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // --- ACTION: Pause session ---
        sessionRunner.pauseSession()

        // --- ASSERT: Session is PAUSED ---
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // --- ACTION: Submit answer while paused (should work) ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english 1")
        ) }
        val (result, _) = sessionRunner.submitAnswer()

        // --- ASSERT: Answer accepted and session resumed ---
        assertTrue("Answer should be accepted while paused", result.accepted)
        assertEquals("Session should resume to ACTIVE after correct answer", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    @Test
    fun scenario_dailyPractice_navigation() = runBlocking {
        // --- SETUP: Start translate session with 3 cards ---
        val translateCards = (1..3).map { i ->
            createSentenceCard(
                id = "trans-$i",
                ru = "русское $i",
                answers = listOf("english $i")
            )
        }
        sessionRunner.startDailyTranslateSession(translateCards)

        // --- ACTION: Navigate next (should pause and advance) ---
        sessionRunner.navigateNext()

        // --- ASSERT: Session PAUSED, advanced to card 2 ---
        assertEquals("Session should be PAUSED after navigateNext", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on trans-2", "trans-2", stateAccess.uiState.value.cardSession.currentCard?.id)

        // --- ACTION: Navigate prev (should stay on same card index) ---
        sessionRunner.navigatePrev()

        // --- ASSERT: Back to card 1 ---
        assertEquals("Session should remain PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should go back to card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on trans-1", "trans-1", stateAccess.uiState.value.cardSession.currentCard?.id)
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Creates a fake Android Application context.
     * SessionRunner uses Application primarily for SystemClock.elapsedRealtime()
     * which is a static method and doesn't actually need a context reference.
     */
    private fun createFakeApplicationContext(): android.app.Application {
        return object : android.app.Application() {
            override fun getClassLoader(): ClassLoader {
                return this.javaClass.classLoader!!
            }
        }
    }
}
