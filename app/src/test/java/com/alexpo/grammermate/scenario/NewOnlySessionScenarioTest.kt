package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.CardSessionStateModel
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.LessonSchedule
import com.alexpo.grammermate.data.ScheduledSubLesson
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SubLessonType
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.SessionEvent
import com.alexpo.grammermate.feature.training.SessionRunner
import com.alexpo.grammermate.feature.training.WordBankGenerator
import com.alexpo.grammermate.testharness.FakeDrillProgressStore
import com.alexpo.grammermate.testharness.FakeLessonStore
import com.alexpo.grammermate.testharness.FakeMasteryStore
import com.alexpo.grammermate.testharness.FakeProgressStore
import com.alexpo.grammermate.testharness.FakeStreakStore
import com.alexpo.grammermate.testharness.FakeTrainingStateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)

/**
 * Scenario test for a NEW_ONLY sub-lesson training session.
 *
 * Tests the complete flow of a training session using real SessionRunner
 * with in-memory fake implementations for all dependencies.
 *
 * Scenario:
 * 1. Create lesson with 10 NEW_ONLY cards
 * 2. Start session
 * 3. Answer cards 1-5 correctly → verify cursor advances
 * 4. Answer card 6 incorrectly → verify cursor stays
 * 5. Answer card 6 correctly → verify cursor advances
 * 6. Complete remaining cards
 * 7. Verify final state
 *
 * Uses no mocks — only real SessionRunner + in-memory fakes.
 */
class NewOnlySessionScenarioTest {

    private lateinit var stateAccess: FakeTrainingStateAccess
    private lateinit var masteryStore: FakeMasteryStore
    private lateinit var progressStore: FakeProgressStore
    private lateinit var drillProgressStore: FakeDrillProgressStore
    private lateinit var streakStore: FakeStreakStore
    private lateinit var lessonStore: FakeLessonStore
    private lateinit var sessionRunner: SessionRunner
    private lateinit var cardProvider: CardProvider
    private lateinit var answerValidator: AnswerValidator
    private lateinit var streakManager: StreakManager
    private lateinit var progressTracker: ProgressTracker

    private val testLanguageId = LanguageId("en")
    private val testLessonId = LessonId("lesson-01")
    private val coroutineScope = CoroutineScope(Dispatchers.Unconfined)

    // Helper to create a test card
    private fun createCard(id: String, ru: String, answers: List<String>): SentenceCard {
        return SentenceCard(id = id, promptRu = ru, acceptedAnswers = answers)
    }

    // Helper to create a test lesson with 10 cards
    private fun createTestLesson(): Lesson {
        val cards = (1..10).map { i ->
            createCard(
                id = "card-$i",
                ru = "русское слово $i",
                answers = listOf("english word $i")
            )
        }
        return Lesson(
            id = testLessonId,
            languageId = testLanguageId,
            title = "Test Lesson",
            cards = cards
        )
    }

    @Before
    fun setup() {
        // Initialize all fake stores
        masteryStore = FakeMasteryStore()
        progressStore = FakeProgressStore()
        drillProgressStore = FakeDrillProgressStore()
        streakStore = FakeStreakStore()
        lessonStore = FakeLessonStore()

        // Add test lesson to lesson store
        lessonStore.addLesson(createTestLesson())

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = com.alexpo.grammermate.data.NavigationState(
                selectedLanguageId = testLanguageId,
                selectedLessonId = testLessonId,
                lessons = listOf(createTestLesson())
            )
        )
        stateAccess = FakeTrainingStateAccess(initialState)

        // Initialize core components
        answerValidator = AnswerValidator()
        streakManager = StreakManager(streakStore)
        progressTracker = ProgressTracker(
            stateAccess = stateAccess,
            masteryStore = masteryStore,
            progressStore = progressStore,
            lessonStore = lessonStore
        )
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
            getMastery = { lessonId, langId -> masteryStore.get(lessonId, langId) },
            getSchedule = { lessonId -> null }, // Will be set during test
            calculateCompletedSubLessons = { subLessons, mastery, lessonId ->
                progressTracker.calculateCompletedSubLessons(subLessons, mastery, testLessonId, listOf(createTestLesson()))
            },
            onTimerSaveProgress = { /* No-op for test */ },
            sessionTimerMsSink = null
        )
    }

    // ========================================
    // SCENARIO: Complete NEW_ONLY session flow
    // ========================================

    @Test
    fun scenario_newOnlySession_completeFlow() = runBlocking {
        // --- STEP 1: Build schedule with NEW_ONLY sub-lessons ---
        val lesson = createTestLesson()
        val schedule = LessonSchedule(
            lessonId = testLessonId,
            subLessons = listOf(
                ScheduledSubLesson(SubLessonType.NEW_ONLY, lesson.cards)
            )
        )

        // --- STEP 2: Set session cards and start session ---
        sessionRunner.setSessionCards(lesson.cards)

        // Start the session
        val startEvents = sessionRunner.startSession()

        // Verify: Session is now ACTIVE
        assertEquals("Session should be ACTIVE after start",
            SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // Verify: First card is loaded
        assertNotNull("Current card should not be null", stateAccess.uiState.value.cardSession.currentCard)
        assertEquals("First card should be card-1", "card-1", stateAccess.uiState.value.cardSession.currentCard?.id)

        // Verify: Cursor is at index 0
        assertEquals("Initial cursor should be at index 0", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // Verify: Session is not complete
        assertFalse("Session should not be complete initially", isSessionComplete())

        // --- STEP 3: Answer cards 1-5 correctly, verify cursor advances ---
        for (i in 0 until 5) {
            val expectedCardIndex = i
            val expectedCardId = "card-${i + 1}"

            // Verify we're on the right card
            assertEquals("Should be on card $expectedCardId",
                expectedCardIndex, stateAccess.uiState.value.cardSession.currentIndex)
            assertEquals("Card ID should match",
                expectedCardId, stateAccess.uiState.value.cardSession.currentCard?.id)

            // Submit correct answer
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }

            val (result, events) = sessionRunner.submitAnswer()

            // Verify: Answer was accepted
            assertTrue("Answer should be accepted for card $expectedCardId", result.accepted)
            assertFalse("No hint should be shown for correct answer", result.hintShown)

            // Verify: Cursor advanced (unless it was the last card)
            if (i < 4) { // Not last card in this batch
                assertEquals("Cursor should advance after correct answer on card $expectedCardId",
                    expectedCardIndex + 1, stateAccess.uiState.value.cardSession.currentIndex)
            }
        }

        // --- STEP 4: Answer card 6 incorrectly, verify cursor stays ---
        val card6Index = 5
        val card6Id = "card-6"

        // Verify we're on card 6
        assertEquals("Should be on card 6", card6Index, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Card ID should be card-6", card6Id, stateAccess.uiState.value.cardSession.currentCard?.id)

        // Submit incorrect answer
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong answer")
        ) }

        val (wrongResult, wrongEvents) = sessionRunner.submitAnswer()

        // Verify: Answer was rejected
        assertFalse("Wrong answer should be rejected", wrongResult.accepted)
        assertFalse("No hint should be shown yet (only 1 attempt)", wrongResult.hintShown)

        // Verify: Cursor stayed at card 6
        assertEquals("Cursor should stay at card 6 after wrong answer",
            card6Index, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should still be on card-6", card6Id, stateAccess.uiState.value.cardSession.currentCard?.id)

        // Verify: Incorrect count increased
        assertEquals("Incorrect count should be 1", 1, stateAccess.uiState.value.cardSession.incorrectCount)

        // --- STEP 5: Answer card 6 correctly, verify cursor advances ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 6")
        ) }

        val (correctResult, correctEvents) = sessionRunner.submitAnswer()

        // Verify: Answer was accepted
        assertTrue("Correct answer should be accepted", correctResult.accepted)

        // Verify: Cursor advanced to card 7
        assertEquals("Cursor should advance to card 7 after correct answer",
            card6Index + 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on card-7", "card-7", stateAccess.uiState.value.cardSession.currentCard?.id)

        // --- STEP 6: Complete remaining cards (7-10) ---
        for (i in 6 until 10) {
            val cardNum = i + 1
            val correctAnswer = "english word $cardNum"

            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }

            val (result, events) = sessionRunner.submitAnswer()

            assertTrue("Answer should be accepted for card $cardNum", result.accepted)
        }

        // --- STEP 7: Verify final state ---
        // After submitting the last card, the session should signal completion
        // The state machine should handle this by triggering sub-lesson completion

        // Verify: All cards were answered correctly (except the one wrong attempt)
        assertEquals("Correct count should be 10", 10, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("Incorrect count should be 1", 1, stateAccess.uiState.value.cardSession.incorrectCount)

        // Verify: Session is in appropriate state (PAUSED after last card)
        // Note: The actual completion handling is done via SessionEvents
        assertEquals("Session should be PAUSED after completing all cards",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    @Test
    fun scenario_newOnlySession_incorrectAnswerStaysOnCard() = runBlocking {
        // --- SETUP: Create lesson and start session ---
        val lesson = createTestLesson()
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // --- TEST: Submit 3 incorrect answers to trigger hint ---
        val card1Id = "card-1"
        assertEquals("Should start on card-1", card1Id, stateAccess.uiState.value.cardSession.currentCard?.id)

        // First incorrect answer
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "wrong 1")) }
        var (result, events) = sessionRunner.submitAnswer()
        assertFalse("First wrong answer should be rejected", result.accepted)
        assertFalse("Hint should not be shown yet", result.hintShown)
        assertEquals("Should still be on card-1", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // Second incorrect answer
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "wrong 2")) }
        result = sessionRunner.submitAnswer().first
        assertFalse("Second wrong answer should be rejected", result.accepted)
        assertFalse("Hint should not be shown yet", result.hintShown)
        assertEquals("Should still be on card-1", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // Third incorrect answer (triggers hint)
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "wrong 3")) }
        result = sessionRunner.submitAnswer().first
        assertFalse("Third wrong answer should be rejected", result.accepted)
        assertTrue("Hint should be shown after 3 attempts", result.hintShown)
        assertEquals("Should STILL be on card-1 (even with hint)", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // Verify: Session state is HINT_SHOWN
        assertEquals("Session should be in HINT_SHOWN state",
            SessionState.HINT_SHOWN, stateAccess.uiState.value.cardSession.sessionState)

        // --- TEST: After hint, typing and submitting correct answer advances ---
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "english word 1")) }
        result = sessionRunner.submitAnswer().first
        assertTrue("Correct answer after hint should be accepted", result.accepted)

        // After correct answer, should advance to next card
        assertEquals("Should advance to card-2 after correct answer", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on card-2", "card-2", stateAccess.uiState.value.cardSession.currentCard?.id)
    }

    @Test
    fun scenario_newOnlySession_voiceModeTrigger() = runBlocking {
        // --- SETUP: Create lesson and start session in VOICE mode ---
        val lesson = createTestLesson()
        sessionRunner.setSessionCards(lesson.cards)

        // Set input mode to VOICE before starting
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        // Verify: Voice trigger token is set (indicates voice recognition should start)
        val voiceTriggerToken = stateAccess.uiState.value.cardSession.voiceTriggerToken
        assertTrue("Voice trigger token should be > 0 in VOICE mode", voiceTriggerToken > 0)

        // --- TEST: Wrong answer in VOICE mode auto-retries ---
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "wrong")) }
        var (result, events) = sessionRunner.submitAnswer()
        assertFalse("Wrong answer should be rejected", result.accepted)

        // In VOICE mode, voice trigger token should increment to trigger retry
        val newVoiceTriggerToken = stateAccess.uiState.value.cardSession.voiceTriggerToken
        assertTrue("Voice trigger token should increment after wrong answer in VOICE mode",
            newVoiceTriggerToken > voiceTriggerToken)

        // --- TEST: Correct answer advances ---
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "english word 1")) }
        result = sessionRunner.submitAnswer().first
        assertTrue("Correct answer should be accepted", result.accepted)
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
    }

    @Test
    fun scenario_newOnlySession_pauseAndResume() = runBlocking {
        // --- SETUP: Create lesson and start session ---
        val lesson = createTestLesson()
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // Verify: Session is ACTIVE
        assertEquals("Session should be ACTIVE", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // --- TEST: Pause session ---
        val pauseEvents = sessionRunner.pauseSession()
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // --- TEST: Submit answer while paused (should work) ---
        stateAccess.updateState { it.copy(cardSession = it.cardSession.copy(inputText = "english word 1")) }
        val (result, events) = sessionRunner.submitAnswer()
        assertTrue("Answer should be accepted even while paused", result.accepted)

        // After correct answer, session should resume and advance
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
    }

    @Test
    fun scenario_newOnlySession_navigation() = runBlocking {
        // --- SETUP: Create lesson and start session ---
        val lesson = createTestLesson()
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // --- TEST: Navigate next (should pause and advance) ---
        val navNextEvents = sessionRunner.navigateNext()
        assertEquals("Session should be PAUSED after navigateNext", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on card-2", "card-2", stateAccess.uiState.value.cardSession.currentCard?.id)

        // --- TEST: Navigate prev (should stay on same card index) ---
        val navPrevEvents = sessionRunner.navigatePrev()
        assertEquals("Session should remain PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should go back to card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should be on card-1", "card-1", stateAccess.uiState.value.cardSession.currentCard?.id)
    }

    // ========================================
    // Helper methods
    // ========================================

    private fun isSessionComplete(): Boolean {
        return sessionRunner.isComplete
    }

    /**
     * Creates a fake Android Application context.
     * Since we can't use real Android context in unit tests,
     * we create a minimal stub that satisfies the compiler.
     *
     * Note: SessionRunner uses Application primarily for
     * SystemClock.elapsedRealtime() which doesn't actually
     * need a context reference — it's a static method.
     */
    private fun createFakeApplicationContext(): android.app.Application {
        // This is a stub — in real unit tests without Android,
        // we'd need to use Robolectric or mock this.
        // For this scenario test, we're relying on the fact
        // that SessionRunner only uses Application for
        // SystemClock which is static.
        return object : android.app.Application() {
            override fun getClassLoader(): ClassLoader {
                return this.javaClass.classLoader!!
            }
        }
    }
}
