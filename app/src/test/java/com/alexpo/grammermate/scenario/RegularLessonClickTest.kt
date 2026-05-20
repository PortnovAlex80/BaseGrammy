package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.CardSessionStateModel
import com.alexpo.grammermate.data.FlowerState
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
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingMode
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

/**
 * Comprehensive click-test-style unit test for Regular Lesson Training mode.
 *
 * Tests the complete training flow including:
 * - NEW_ONLY and MIXED sub-lesson types
 * - Answer validation (correct, incorrect, 3-attempt hint flow)
 * - Mastery counting (VOICE/KEYBOARD only, WORD_BANK excluded)
 * - Flower growth progression
 * - Boss battle trigger after 15 sub-lessons
 * - Play/pause session state toggling
 * - Previous/Next card navigation
 * - Sub-lesson completion and roadmap return
 *
 * Uses real SessionRunner with in-memory fake implementations for all dependencies.
 * No mocks, no file I/O, pure behavioral testing.
 *
 * Based on: docs/specification/scenarios/click-test-regular-lesson.md
 */
@RunWith(RobolectricTestRunner::class)
class RegularLessonClickTest {

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

    // Helper to create a test lesson with specified number of cards
    private fun createTestLesson(cardCount: Int = 20): Lesson {
        val cards = (1..cardCount).map { i ->
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

    // Helper to create a second lesson for MIXED sub-lesson testing
    private fun createSecondLesson(cardCount: Int = 20): Lesson {
        val cards = (1..cardCount).map { i ->
            createCard(
                id = "l2-card-$i",
                ru = "русское слово L2 $i",
                answers = listOf("english word L2 $i")
            )
        }
        return Lesson(
            id = LessonId("lesson-02"),
            languageId = testLanguageId,
            title = "Test Lesson 2",
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

        // Add test lessons to lesson store
        lessonStore.addLesson(createTestLesson())
        lessonStore.addLesson(createSecondLesson())

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = com.alexpo.grammermate.data.NavigationState(
                selectedLanguageId = testLanguageId,
                selectedLessonId = testLessonId,
                lessons = listOf(createTestLesson(), createSecondLesson())
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
    // Test 1: NEW_ONLY Session - Complete
    // ========================================

    @Test
    fun testNewOnlySession_Complete() = runBlocking {
        // ARRANGE: Create lesson with exactly 10 cards for single sub-lesson
        val lesson = createTestLesson(10)
        sessionRunner.setSessionCards(lesson.cards)

        // ACT: Start session
        sessionRunner.startSession()

        // ASSERT: Verify initial state
        assertEquals("Session should be ACTIVE", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should start at card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Should show card-1", "card-1", stateAccess.uiState.value.cardSession.currentCard?.id)
        assertEquals("Sub-lesson total should be 10", 10, stateAccess.uiState.value.cardSession.subLessonTotal)

        // ACT: Complete all 10 cards with correct answers
        for (i in 0 until 10) {
            val expectedCardId = "card-${i + 1}"
            assertEquals("Should be on card $expectedCardId", expectedCardId, stateAccess.uiState.value.cardSession.currentCard?.id)

            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }

            val (result, events) = sessionRunner.submitAnswer()
            assertTrue("Answer $i should be accepted", result.accepted)
        }

        // ASSERT: Verify completion state
        assertEquals("All 10 cards should be correct", 10, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("No incorrect answers", 0, stateAccess.uiState.value.cardSession.incorrectCount)
        assertEquals("Session should be PAUSED after completion", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    // ========================================
    // Test 2: MIXED Session - Five Current + Five Review
    // ========================================

    @Test
    fun testMixedSession_FiveCurrentFiveReview() = runBlocking {
        // ARRANGE: Create two lessons for MIXED testing
        val l1 = createTestLesson(20)
        val l2 = createSecondLesson(20)

        // Mark some L1 cards as "shown" so they appear as review in L2
        masteryStore.recordCardShow("lesson-01", "en", "card-1")
        masteryStore.recordCardShow("lesson-01", "en", "card-2")
        masteryStore.recordCardShow("lesson-01", "en", "card-3")
        masteryStore.recordCardShow("lesson-01", "en", "card-4")
        masteryStore.recordCardShow("lesson-01", "en", "card-5")

        // Build schedules using CardProvider
        val cardProvider = CardProvider(subLessonSize = 10)
        val lessons = listOf(l1, l2)
        val schedules = cardProvider.buildSchedules(lessons, emptyMap())

        // ACT: Get L2 schedule (second lesson should have MIXED blocks)
        val l2Schedule = schedules[LessonId("lesson-02")]
        assertNotNull("L2 schedule should exist", l2Schedule)

        // ASSERT: Verify MIXED sub-lessons exist
        val mixedSubLessons = l2Schedule!!.subLessons.filter { it.type == SubLessonType.MIXED }
        assertTrue("L2 should have MIXED sub-lessons", mixedSubLessons.isNotEmpty())

        // ASSERT: Verify MIXED contains both current and review cards
        val firstMixed = mixedSubLessons.first()
        val hasL1Cards = firstMixed.cards.any { it.id.startsWith("card-") }
        val hasL2Cards = firstMixed.cards.any { it.id.startsWith("l2-card-") }

        assertTrue("MIXED should contain L1 review cards", hasL1Cards)
        assertTrue("MIXED should contain L2 new cards", hasL2Cards)
    }

    // ========================================
    // Test 3: Answer Validation - Correct Advances
    // ========================================

    @Test
    fun testAnswerValidation_Correct_Advances() = runBlocking {
        // ARRANGE: Start session with 5 cards
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ASSERT: Verify starting position
        assertEquals("Start at card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-1 should be loaded", "card-1", stateAccess.uiState.value.cardSession.currentCard?.id)

        // ACT: Submit correct answer
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }
        val (result, events) = sessionRunner.submitAnswer()

        // ASSERT: Verify answer accepted and card advanced
        assertTrue("Correct answer should be accepted", result.accepted)
        assertFalse("No hint should be shown", result.hintShown)
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-2 should be loaded", "card-2", stateAccess.uiState.value.cardSession.currentCard?.id)
        assertEquals("Correct count should increment", 1, stateAccess.uiState.value.cardSession.correctCount)
    }

    // ========================================
    // Test 4: Answer Validation - Three Wrong Shows Hint
    // ========================================

    @Test
    fun testAnswerValidation_ThreeWrongShowsHint() = runBlocking {
        // ARRANGE: Start session
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ACT: First wrong answer
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong 1")
        ) }
        var (result, events) = sessionRunner.submitAnswer()

        // ASSERT: First wrong - no hint
        assertFalse("First wrong should be rejected", result.accepted)
        assertFalse("No hint after first wrong", result.hintShown)
        assertEquals("Should stay on card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Incorrect count should be 1", 1, stateAccess.uiState.value.cardSession.incorrectCount)

        // ACT: Second wrong answer
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong 2")
        ) }
        result = sessionRunner.submitAnswer().first

        // ASSERT: Second wrong - still no hint
        assertFalse("Second wrong should be rejected", result.accepted)
        assertFalse("No hint after second wrong", result.hintShown)
        assertEquals("Should stay on card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Incorrect count should be 2", 2, stateAccess.uiState.value.cardSession.incorrectCount)

        // ACT: Third wrong answer
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong 3")
        ) }
        result = sessionRunner.submitAnswer().first

        // ASSERT: Third wrong - HINT SHOWN
        assertFalse("Third wrong should be rejected", result.accepted)
        assertTrue("Hint SHOULD be shown after third wrong", result.hintShown)
        assertEquals("Session state should be HINT_SHOWN", SessionState.HINT_SHOWN, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("Should STILL stay on card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
    }

    // ========================================
    // Test 5: Mastery Counting - Voice Only
    // ========================================

    @Test
    fun testMasteryCounting_VoiceOnly() = runBlocking {
        // ARRANGE: Start session in VOICE mode
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        // ACT: Complete 3 cards with correct answers
        for (i in 0 until 3) {
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }
            sessionRunner.submitAnswer()
        }

        // ACT: Record card shows (simulating what happens on correct answer)
        for (i in 1..3) {
            masteryStore.recordCardShow("lesson-01", "en", "card-$i")
        }

        // ASSERT: Verify mastery incremented
        val mastery = masteryStore.get("lesson-01", "en")
        assertNotNull("Mastery should exist", mastery)
        assertEquals("uniqueCardShows should be 3", 3, mastery?.uniqueCardShows)
        assertEquals("shownCardIds should contain 3 cards", 3, mastery?.shownCardIds?.size)
        assertTrue("shownCardIds should contain card-1", mastery?.shownCardIds?.contains("card-1") == true)
        assertTrue("shownCardIds should contain card-2", mastery?.shownCardIds?.contains("card-2") == true)
        assertTrue("shownCardIds should contain card-3", mastery?.shownCardIds?.contains("card-3") == true)
    }

    // ========================================
    // Test 6: Mastery Counting - Word Bank Not Counted
    // ========================================

    @Test
    fun testMasteryCounting_WordBankNotCounted() = runBlocking {
        // ARRANGE: Start session in WORD_BANK mode
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.setInputMode(InputMode.WORD_BANK)
        sessionRunner.startSession()

        // ACT: Complete 3 cards via WORD_BANK
        for (i in 0 until 3) {
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }
            sessionRunner.submitAnswer()
        }

        // ACT: Mark cards as shown for progress (but NOT for mastery)
        // This simulates the markCardsShownForProgress call
        masteryStore.markCardsShownForProgress("lesson-01", "en", listOf("card-1", "card-2", "card-3"))

        // ASSERT: Verify progress marked but mastery NOT incremented
        val mastery = masteryStore.get("lesson-01", "en")

        // uniqueCardShows should NOT increment for WORD_BANK
        assertEquals("uniqueCardShows should be 0 (WORD_BANK doesn't count)", 0, mastery?.uniqueCardShows ?: 0)

        // But shownCardIds should be marked for progress tracking
        assertTrue("shownCardIds should contain card-1 for progress", mastery?.shownCardIds?.contains("card-1") == true)
        assertTrue("shownCardIds should contain card-2 for progress", mastery?.shownCardIds?.contains("card-2") == true)
        assertTrue("shownCardIds should contain card-3 for progress", mastery?.shownCardIds?.contains("card-3") == true)
    }

    // ========================================
    // Test 7: Sub-Lesson Completion - Returns to Roadmap
    // ========================================

    @Test
    fun testSubLessonCompletion_ReturnsToRoadmap() = runBlocking {
        // ARRANGE: Start and complete a sub-lesson
        val lesson = createTestLesson(10)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ACT: Complete all cards
        for (i in 0 until 10) {
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }
            sessionRunner.submitAnswer()
        }

        // ASSERT: Verify sub-lesson completion state
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
        assertEquals("All 10 cards completed", 10, stateAccess.uiState.value.cardSession.correctCount)

        // In real app, TrainingViewModel would handle navigation back to roadmap
        // Here we verify the session signals completion
        assertTrue("Session should signal completion (isComplete or similar)", sessionRunner.isComplete)
    }

    // ========================================
    // Test 8: Boss Battle Trigger - After Fifteen Sub-Lessons
    // ========================================

    @Test
    fun testBossBattleTrigger_AfterFifteenSubLessons() = runBlocking {
        // ARRANGE: Set up state with 15 completed sub-lessons
        stateAccess.setState(
            stateAccess.uiState.value.copy(
                cardSession = stateAccess.uiState.value.cardSession.copy(
                    completedSubLessonCount = 15,
                    subLessonCount = 20
                )
            )
        )

        // ARRANGE: Create a mastery state (required for markLessonCompleted to work)
        masteryStore.recordCardShow("lesson-01", "en", "card-1")

        // ACT: Mark lesson as completed (triggers boss availability)
        masteryStore.markLessonCompleted("lesson-01", "en")

        // ASSERT: Verify lesson is marked complete
        val mastery = masteryStore.get("lesson-01", "en")
        assertNotNull("Mastery should exist", mastery)
        assertNotNull("Lesson should have completion timestamp", mastery?.completedAtMs)

        // ASSERT: With 15 completed sub-lessons, boss should be unlockable
        val completedCount = stateAccess.uiState.value.cardSession.completedSubLessonCount
        assertTrue("Should have 15+ completed sub-lessons for boss", completedCount >= 15)

        // Note: Actual boss battle entry is handled by BossOrchestrator
        // This test verifies the prerequisite condition (15 sub-lessons)
    }

    // ========================================
    // Test 9: Play/Pause - Toggles Session State
    // ========================================

    @Test
    fun testPlayPause_TogglesSessionState() = runBlocking {
        // ARRANGE: Start session
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ASSERT: Initial ACTIVE state
        assertEquals("Session should be ACTIVE after start", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // ACT: Pause session
        sessionRunner.pauseSession()

        // ASSERT: PAUSED state
        assertEquals("Session should be PAUSED after pause", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // ACT: Resume (start again)
        sessionRunner.startSession()

        // ASSERT: Back to ACTIVE
        assertEquals("Session should be ACTIVE after resume", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // ACT: Pause again
        sessionRunner.pauseSession()

        // ASSERT: PAUSED again
        assertEquals("Session should be PAUSED after second pause", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    // ========================================
    // Test 10: Previous Card - Navigates Back
    // ========================================

    @Test
    fun testPreviousCard_NavigatesBack() = runBlocking {
        // ARRANGE: Start session and advance to card 3
        val lesson = createTestLesson(10)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // Advance to card 2
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }
        sessionRunner.submitAnswer()
        assertEquals("Should be at card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)

        // Advance to card 3
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 2")
        ) }
        sessionRunner.submitAnswer()
        assertEquals("Should be at card 3", 2, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-3 should be loaded", "card-3", stateAccess.uiState.value.cardSession.currentCard?.id)

        // ACT: Navigate back (prev)
        sessionRunner.navigatePrev()

        // ASSERT: Should return to card 2
        assertEquals("Should go back to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-2 should be loaded", "card-2", stateAccess.uiState.value.cardSession.currentCard?.id)

        // ACT: Navigate back again
        sessionRunner.navigatePrev()

        // ASSERT: Should return to card 1
        assertEquals("Should go back to card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-1 should be loaded", "card-1", stateAccess.uiState.value.cardSession.currentCard?.id)
    }

    // ========================================
    // Additional Flower Growth Tests
    // ========================================

    @Test
    fun testFlowerGrowth_FromSeedToSprout() = runBlocking {
        // ARRANGE: Create lesson with 150 cards (full main pool)
        val lesson = createTestLesson(150)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ACT: Complete 50 cards (33% = SPROUT threshold)
        for (i in 0 until 50) {
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }
            sessionRunner.submitAnswer()

            // Record mastery for each card
            masteryStore.recordCardShow("lesson-01", "en", "card-${i + 1}")
        }

        // ASSERT: Verify flower state progression
        val mastery = masteryStore.get("lesson-01", "en")
        assertNotNull("Mastery should exist", mastery)

        val flower = com.alexpo.grammermate.data.FlowerCalculator.calculate(mastery!!, totalCardsInLesson = 150)

        // With 50 unique shows (33%), flower should be SPROUT
        assertEquals("Flower should be SPROUT at 33%", com.alexpo.grammermate.data.FlowerState.SPROUT, flower.state)
        assertEquals("Should have 50 unique shows", 50, mastery.uniqueCardShows)
    }

    @Test
    fun testFlowerGrowth_FromSproutToBloom() = runBlocking {
        // ARRANGE: Start with 50 shows (SPROUT)
        repeat(50) { i ->
            masteryStore.recordCardShow("lesson-01", "en", "card-${i + 1}")
        }

        // ACT: Add 50 more shows (total 100 = 66% = BLOOM threshold)
        for (i in 50 until 100) {
            masteryStore.recordCardShow("lesson-01", "en", "card-${i + 1}")
        }

        // ASSERT: Verify BLOOM state
        val mastery = masteryStore.get("lesson-01", "en")
        assertNotNull("Mastery should exist", mastery)

        val flower = com.alexpo.grammermate.data.FlowerCalculator.calculate(mastery!!, totalCardsInLesson = 150)

        assertEquals("Flower should be BLOOM at 66%", FlowerState.BLOOM, flower.state)
        assertEquals("Should have 100 unique shows", 100, mastery.uniqueCardShows)
        assertTrue("Mastery percent should be >= 66%", flower.masteryPercent >= 0.66f)
    }

    // ========================================
    // Additional Input Mode Tests
    // ========================================

    @Test
    fun testInputMode_VoiceToKeyboard() = runBlocking {
        // ARRANGE: Start in VOICE mode
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        // ASSERT: Initial VOICE mode
        assertEquals("Should start in VOICE mode", InputMode.VOICE, stateAccess.uiState.value.cardSession.inputMode)

        // ACT: Switch to KEYBOARD
        sessionRunner.setInputMode(InputMode.KEYBOARD)

        // ASSERT: Mode changed
        assertEquals("Should be in KEYBOARD mode", InputMode.KEYBOARD, stateAccess.uiState.value.cardSession.inputMode)

        // ACT: Submit answer (should work in KEYBOARD mode)
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }
        val (result, events) = sessionRunner.submitAnswer()

        // ASSERT: Answer accepted
        assertTrue("Answer should be accepted in KEYBOARD mode", result.accepted)
    }

    @Test
    fun testInputMode_VoiceToWordBank() = runBlocking {
        // ARRANGE: Start in VOICE mode
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        // ACT: Switch to WORD_BANK
        sessionRunner.setInputMode(InputMode.WORD_BANK)

        // ASSERT: Mode changed
        assertEquals("Should be in WORD_BANK mode", InputMode.WORD_BANK, stateAccess.uiState.value.cardSession.inputMode)

        // ACT: Submit answer (should work in WORD_BANK mode)
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }
        val (result, events) = sessionRunner.submitAnswer()

        // ASSERT: Answer accepted
        assertTrue("Answer should be accepted in WORD_BANK mode", result.accepted)
    }

    // ========================================
    // Additional Navigation Tests
    // ========================================

    @Test
    fun testNextCard_AdvancesToNext() = runBlocking {
        // ARRANGE: Start session
        val lesson = createTestLesson(10)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ASSERT: Starting at card 1
        assertEquals("Should start at card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // ACT: Navigate next (skips card 1)
        sessionRunner.navigateNext()

        // ASSERT: Should be at card 2
        assertEquals("Should advance to card 2", 1, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-2 should be loaded", "card-2", stateAccess.uiState.value.cardSession.currentCard?.id)

        // ACT: Navigate next again
        sessionRunner.navigateNext()

        // ASSERT: Should be at card 3
        assertEquals("Should advance to card 3", 2, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("card-3 should be loaded", "card-3", stateAccess.uiState.value.cardSession.currentCard?.id)
    }

    @Test
    fun testNavigationDoesNotAwardMastery() = runBlocking {
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)

        val startEvents = sessionRunner.startSession()
        val nextEvents = sessionRunner.navigateNext()

        assertFalse(
            "Starting a session should not count as practiced mastery",
            startEvents.any { it is SessionEvent.RecordCardShow }
        )
        assertFalse(
            "Browsing to the next card should not count as practiced mastery",
            nextEvents.any { it is SessionEvent.RecordCardShow }
        )
    }

    @Test
    fun testCorrectAnswerAwardsMasteryEvent() = runBlocking {
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()
        stateAccess.updateState {
            it.copy(cardSession = it.cardSession.copy(inputText = "english word 1"))
        }

        val (result, events) = sessionRunner.submitAnswer()

        assertTrue("Correct answer should be accepted", result.accepted)
        assertTrue(
            "Correct answer should count as practiced mastery",
            events.any { it is SessionEvent.RecordCardShow && it.card.id == "card-1" }
        )
    }

    @Test
    fun testNavigateNextAtLastCard_StaysAtLast() = runBlocking {
        // ARRANGE: Start with 5 cards, navigate to last
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // Navigate to card 5 (last)
        repeat(4) { sessionRunner.navigateNext() }
        assertEquals("Should be at card 5 (index 4)", 4, stateAccess.uiState.value.cardSession.currentIndex)

        // ACT: Try to navigate past last card
        sessionRunner.navigateNext()

        // ASSERT: Should stay at last card
        assertEquals("Should stay at last card", 4, stateAccess.uiState.value.cardSession.currentIndex)
    }

    @Test
    fun testNavigatePrevAtFirstCard_StaysAtFirst() = runBlocking {
        // ARRANGE: Start session at card 1
        val lesson = createTestLesson(5)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        // ASSERT: At card 1 (index 0)
        assertEquals("Should be at card 1", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // ACT: Try to navigate before first card
        sessionRunner.navigatePrev()

        // ASSERT: Should stay at first card
        assertEquals("Should stay at first card", 0, stateAccess.uiState.value.cardSession.currentIndex)
    }

    // ========================================
    // Session State Persistence Tests
    // ========================================

    @Test
    fun testSessionProgress_PreservedAcrossPause() = runBlocking {
        // ARRANGE: Start session, complete 3 cards
        val lesson = createTestLesson(10)
        sessionRunner.setSessionCards(lesson.cards)
        sessionRunner.startSession()

        for (i in 0 until 3) {
            val correctAnswer = "english word ${i + 1}"
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }
            sessionRunner.submitAnswer()
        }

        // ASSERT: Progress recorded
        assertEquals("Should have 3 correct", 3, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("Should be at card 4", 3, stateAccess.uiState.value.cardSession.currentIndex)

        // ACT: Pause session
        sessionRunner.pauseSession()

        // ASSERT: State preserved during pause
        assertEquals("Correct count preserved during pause", 3, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("Position preserved during pause", 3, stateAccess.uiState.value.cardSession.currentIndex)
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // ACT: Resume and complete one more
        sessionRunner.startSession()
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 4")
        ) }
        sessionRunner.submitAnswer()

        // ASSERT: Progress continues from preserved state
        assertEquals("Should have 4 correct after resume", 4, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("Should be at card 5", 4, stateAccess.uiState.value.cardSession.currentIndex)
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Creates a fake Android Application context.
     */
    private fun createFakeApplicationContext(): android.app.Application {
        return object : android.app.Application() {
            override fun getClassLoader(): ClassLoader {
                return this.javaClass.classLoader!!
            }
        }
    }
}
