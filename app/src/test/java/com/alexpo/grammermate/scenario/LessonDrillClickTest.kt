package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.PracticeType
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingScreenMode
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.HintCalculator
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
 * Click test scenario for Lesson Drill sub-mode.
 *
 * Tests the complete user journey for the Lesson Drill feature using real SessionRunner
 * with in-memory fake implementations for all dependencies.
 *
 * Drill sub-mode provides additional practice on lesson-specific sentence cards.
 * Unlike standalone VerbDrill, drill sub-mode has no selection screen — the lesson pack
 * author pre-curates drill cards for this lesson's theme and tense.
 *
 * Test Scope:
 * - Entry via Drill tile on Lesson Roadmap
 * - Start Dialog (Fresh Start / Continue buttons)
 * - Green visual indicators for drill mode
 * - ALL drill cards (not capped at 10)
 * - Mastery NOT counted (flowers unchanged)
 * - Parenthetical hints stripped from prompts
 * - Drill progress saved and resumed
 * - Completion returns to Lesson Roadmap
 * - PracticeType.SUB_DRILL recorded
 *
 * Uses no mocks — only real SessionRunner + in-memory fakes.
 *
 * Related specifications:
 * - docs/specification/scenarios/click-test-lesson-drill.md
 * - docs/specification/scenarios/scenario-16-drill-sublesson.md
 */
@RunWith(RobolectricTestRunner::class)
class LessonDrillClickTest {

    private lateinit var stateAccess: FakeTrainingStateAccess
    private lateinit var masteryStore: FakeMasteryStore
    private lateinit var progressStore: FakeProgressStore
    private lateinit var drillProgressStore: FakeDrillProgressStore
    private lateinit var streakStore: FakeStreakStore
    private lateinit var lessonStore: FakeLessonStore
    private lateinit var sessionRunner: SessionRunner
    private lateinit var answerValidator: AnswerValidator
    private lateinit var streakManager: StreakManager
    private lateinit var progressTracker: ProgressTracker

    private val testLanguageId = LanguageId("en")
    private val testLessonId = LessonId("lesson-01")
    private val coroutineScope = CoroutineScope(Dispatchers.Unconfined)

    // Helper to create a test drill card with parenthetical hints
    private fun createDrillCard(id: String, ru: String, answers: List<String>): SentenceCard {
        return SentenceCard(id = id, promptRu = ru, acceptedAnswers = answers)
    }

    // Helper to create a test lesson with drill cards
    private fun createTestLessonWithDrill(): Lesson {
        val mainCards = (1..20).map { i ->
            createDrillCard(
                id = "card-$i",
                ru = "русское слово $i",
                answers = listOf("english word $i")
            )
        }
        // Drill cards with parenthetical hints
        val drillCards = listOf(
            createDrillCard("drill-1", "я говорю (dire) правду (verità)", listOf("dico la verità")),
            createDrillCard("drill-2", "я читаю (leggere) книгу (libro)", listOf("leggo il libro")),
            createDrillCard("drill-3", "он пишет (scrivere) письмо (lettera)", listOf("scrive la lettera")),
            createDrillCard("drill-4", "мы идем (andare) домой (casa)", listOf("andiamo a casa")),
            createDrillCard("drill-5", "ты видишь (vedere) солнце (sole)", listOf("vedi il sole")),
            createDrillCard("drill-6", "они говорят (parlare) по-итальянски", listOf("parlano italiano")),
            createDrillCard("drill-7", "она знает (sapere) ответ (risposta)", listOf("sa la risposta")),
            createDrillCard("drill-8", "вы хотите (volere) кофе (caffè)", listOf("volete il caffè")),
            createDrillCard("drill-9", "я имею (avere) время (tempo)", listOf("ho il tempo")),
            createDrillCard("drill-10", "он делает (fare) упражнения (esercizi)", listOf("fa gli esercizi")),
            createDrillCard("drill-11", "мы слышим (sentire) музыку (musica)", listOf("sentiamo la musica")),
            createDrillCard("drill-12", "ты приходишь (venire) рано", listOf("vieni presto")),
            createDrillCard("drill-13", "она работает (lavorare) много", listOf("lavora molto")),
            createDrillCard("drill-14", "я понимаю (capire) тебя (te)", listOf("capisco te")),
            createDrillCard("drill-15", "вы изучаете (studiare) язык (lingua)", listOf("studiate la lingua"))
        )
        return Lesson(
            id = testLessonId,
            languageId = testLanguageId,
            title = "Test Lesson",
            cards = mainCards,
            drillCards = drillCards
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
        lessonStore.addLesson(createTestLessonWithDrill())

        // Initialize state access with default state
        val initialState = TrainingUiState().copy(
            navigation = com.alexpo.grammermate.data.NavigationState(
                selectedLanguageId = testLanguageId,
                selectedLessonId = testLessonId,
                lessons = listOf(createTestLessonWithDrill())
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

        // Initialize SessionRunner with all dependencies
        sessionRunner = SessionRunner(
            stateAccess = stateAccess,
            appContext = createFakeApplicationContext(),
            coroutineScope = coroutineScope,
            answerValidator = answerValidator,
            wordBankGenerator = WordBankGenerator,
            cardProvider = com.alexpo.grammermate.feature.training.CardProvider(),
            streakManager = streakManager,
            drillProgressStore = drillProgressStore,
            getMastery = { lessonId, langId -> masteryStore.get(lessonId, langId) },
            getSchedule = { lessonId -> null },
            calculateCompletedSubLessons = { subLessons, mastery, lessonId ->
                progressTracker.calculateCompletedSubLessons(subLessons, mastery, testLessonId, listOf(createTestLessonWithDrill()))
            },
            onTimerSaveProgress = { /* No-op for test */ },
            sessionTimerMsSink = null
        )
    }

    // ========================================
    // TEST 1: Entry via Drill Tile on Lesson Roadmap
    // ========================================

    @Test
    fun testEntry_ViaDrillTile() {
        // ARRANGE: Lesson with drill cards exists
        val lesson = createTestLessonWithDrill()

        // ACT: Show drill start dialog (simulates tapping Drill tile)
        sessionRunner.showDrillStartDialog(testLessonId.value)

        // ASSERT: Dialog state is set
        val state = stateAccess.uiState.value
        assertTrue("drillShowStartDialog should be true", state.drill.drillShowStartDialog)

        // ASSERT: Lesson has drill cards (would enable the tile)
        assertTrue("Lesson should have drill cards", lesson.drillCards.isNotEmpty())
        assertEquals("Drill card count should be 15", 15, lesson.drillCards.size)

        // ASSERT: No progress yet (first-time entry)
        assertFalse("drillHasProgress should be false initially", state.drill.drillHasProgress)
    }

    // ========================================
    // TEST 2: Start Dialog - Fresh Start Button
    // ========================================

    @Test
    fun testStartDialog_FreshStartButton() = runBlocking {
        // ARRANGE: Show drill start dialog (simulates tapping Drill tile)
        sessionRunner.showDrillStartDialog(testLessonId.value)
        var state = stateAccess.uiState.value
        assertTrue("Dialog should be visible", state.drill.drillShowStartDialog)
        assertFalse("Should have no progress initially", state.drill.drillHasProgress)

        // ACT: Start fresh (no resume)
        val events = sessionRunner.startDrill(resume = false)

        // ASSERT: Dialog dismissed
        state = stateAccess.uiState.value
        assertFalse("Dialog should be dismissed after start", state.drill.drillShowStartDialog)

        // ASSERT: Drill mode is active
        assertTrue("isDrillMode should be true", state.drill.isDrillMode)

        // ASSERT: Drill session initialized at first card
        assertEquals("drillCardIndex should be 0", 0, state.drill.drillCardIndex)
        assertEquals("drillTotalCards should match drill cards count", 15, state.drill.drillTotalCards)

        // ASSERT: Current card is loaded
        assertNotNull("Current card should not be null", state.cardSession.currentCard)
        assertEquals("First card should be drill-1", "drill-1", state.cardSession.currentCard?.id)

        // ASSERT: Session is PAUSED (drill starts paused, requires play)
        assertEquals("Session should be PAUSED", SessionState.PAUSED, state.cardSession.sessionState)

        // ASSERT: Screen mode is DRILL
        assertEquals("Screen mode should be DRILL", TrainingScreenMode.DRILL, state.cardSession.screenMode)
    }

    // ========================================
    // TEST 3: Start Dialog - Continue Button
    // ========================================

    @Test
    fun testStartDialog_ContinueButton() = runBlocking {
        // ARRANGE: Start drill and make progress
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // Answer first card correctly
        val firstCard = stateAccess.uiState.value.cardSession.currentCard!!
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = firstCard.acceptedAnswers.first())
        ) }
        sessionRunner.submitAnswer()

        // Exit drill mode (saves progress at card index 1)
        sessionRunner.exitDrillMode()

        // ARRANGE: Show drill start dialog again
        sessionRunner.showDrillStartDialog(testLessonId.value)
        var state = stateAccess.uiState.value
        assertTrue("Dialog should be visible", state.drill.drillShowStartDialog)
        assertTrue("Should have progress now", state.drill.drillHasProgress)

        // ACT: Resume from saved position
        val events = sessionRunner.startDrill(resume = true)

        // ASSERT: Resumes at saved position (card index 1)
        state = stateAccess.uiState.value
        assertEquals("drillCardIndex should be 1 (resumed)", 1, state.drill.drillCardIndex)
        assertTrue("isDrillMode should be true", state.drill.isDrillMode)
        assertEquals("Current card should be drill-2", "drill-2", state.cardSession.currentCard?.id)
    }

    // ========================================
    // TEST 4: Drill Mode - Green Visual Indicators
    // ========================================

    @Test
    fun testDrillMode_GreenVisualIndicators() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)

        // ASSERT: isDrillMode flag is set (controls green styling in UI)
        val state = stateAccess.uiState.value
        assertTrue("isDrillMode should be true", state.drill.isDrillMode)

        // ASSERT: Screen mode is DRILL (TrainingScreen uses this for styling)
        assertEquals("Screen mode should be DRILL", TrainingScreenMode.DRILL, state.cardSession.screenMode)

        // NOTE: Visual color verification (DrillPromptGreen, DrillTenseLabelGreen)
        // is handled by UI composables (TrainingScreen.kt) which read isDrillMode
        // This test verifies the state flag that drives those colors
    }

    // ========================================
    // TEST 5: All Drill Cards - Not Just Ten
    // ========================================

    @Test
    fun testAllDrillCards_NotJustTen() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // ASSERT: Total cards matches ALL drill cards (not capped at 10)
        var state = stateAccess.uiState.value
        assertEquals("drillTotalCards should be 15 (all drill cards)", 15, state.drill.drillTotalCards)

        // ASSERT: Can navigate through ALL cards
        var cardCount = 0
        while (state.drill.isDrillMode && cardCount < 20) { // Safety limit
            val currentCard = state.cardSession.currentCard
            assertNotNull("Current card should not be null at index $cardCount", currentCard)

            // Submit correct answer
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard!!.acceptedAnswers.first())
            ) }
            val (result, events) = sessionRunner.submitAnswer()
            assertTrue("Answer should be accepted", result.accepted)

            cardCount++
            state = stateAccess.uiState.value

            // Break if drill finished
            if (!state.drill.isDrillMode) break
        }

        // ASSERT: All 15 drill cards were processed
        assertEquals("Should process all 15 drill cards", 15, cardCount)

        // ASSERT: Drill mode is no longer active after completion
        assertFalse("isDrillMode should be false after completion", state.drill.isDrillMode)
    }

    // ========================================
    // TEST 6: Mastery Not Counted
    // ========================================

    @Test
    fun testMasteryNotCounted() = runBlocking {
        // ARRANGE: Get initial mastery state
        val lessonId = testLessonId.value
        val langId = testLanguageId.value
        val initialMastery = masteryStore.get(lessonId, langId)
        val initialShows = initialMastery?.uniqueCardShows ?: 0

        // ACT: Start drill and complete 3 cards
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        repeat(3) {
            val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
            ) }
            sessionRunner.submitAnswer()
        }

        // ASSERT: Mastery unchanged (drill does NOT count for flowers)
        val finalMastery = masteryStore.get(lessonId, langId)
        val finalShows = finalMastery?.uniqueCardShows ?: 0

        assertEquals(
            "uniqueCardShows should NOT increase (drill doesn't count for mastery)",
            initialShows,
            finalShows
        )

        // ASSERT: shownCardIds unchanged
        assertTrue(
            "shownCardIds should NOT include drill cards",
            finalMastery?.shownCardIds?.isEmpty() != false
        )
    }

    // ========================================
    // TEST 7: PracticeType SUB_DRILL Recorded
    // ========================================

    @Test
    fun testPracticeType_SUB_DRILL() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // Complete one card
        val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
        ) }
        sessionRunner.submitAnswer()

        // ASSERT: PracticeType.SUB_DRILL is the 4th fire type
        // Verify the enum exists
        val types = PracticeType.entries
        assertTrue("PracticeType should have SUB_DRILL", PracticeType.SUB_DRILL in types)

        // SUB_DRILL is the 4th type (after TRANSLATION, VOCAB, VERB)
        assertEquals("SUB_DRILL should be 4th type", PracticeType.SUB_DRILL, types[3])

        // NOTE: Actual fire streak recording happens in TrainingViewModel
        // This test verifies the type enum exists for that recording
    }

    // ========================================
    // TEST 8: Parenthetical Hints Stripped
    // ========================================

    @Test
    fun testParentheticalHintsStripped() {
        // ARRANGE: Raw prompt with parenthetical hints
        val rawPrompt = "я говорю (dire) правду (verità)"

        // ACT: Strip all hints (as done in drill mode)
        val stripped = HintCalculator.calculateEffectiveHints(
            promptRu = rawPrompt,
            encounterCount = 0,
            hintLevel = com.alexpo.grammermate.data.HintLevel.HARD,
            sessionOffset = 0,
            isBossBattle = false,
            isReviewMode = false
        )

        // ASSERT: All parenthetical content removed
        val expected = "я говорю правду"
        assertEquals("Parenthetical hints should be stripped", expected, stripped)

        // TEST: Multiple hints
        val raw2 = "слово1 (trans1) слово2 (trans2) слово3 (trans3)"
        val stripped2 = HintCalculator.calculateEffectiveHints(
            promptRu = raw2,
            encounterCount = 0,
            hintLevel = com.alexpo.grammermate.data.HintLevel.HARD,
            sessionOffset = 0,
            isBossBattle = false,
            isReviewMode = false
        )
        val expected2 = "слово1 слово2 слово3"
        assertEquals("Multiple parenthetical hints should be stripped", expected2, stripped2)

        // TEST: Whitespace handling
        val raw3 = "word(trans)"
        val stripped3 = HintCalculator.calculateEffectiveHints(
            promptRu = raw3,
            encounterCount = 0,
            hintLevel = com.alexpo.grammermate.data.HintLevel.HARD,
            sessionOffset = 0,
            isBossBattle = false,
            isReviewMode = false
        )
        assertEquals("Tight parentheses should be stripped", "word", stripped3)

        // TEST: No hints in original
        val raw4 = "just normal text"
        val stripped4 = HintCalculator.calculateEffectiveHints(
            promptRu = raw4,
            encounterCount = 0,
            hintLevel = com.alexpo.grammermate.data.HintLevel.HARD,
            sessionOffset = 0,
            isBossBattle = false,
            isReviewMode = false
        )
        assertEquals("Text without hints should be unchanged", raw4, stripped4)
    }

    // ========================================
    // TEST 9: Drill Progress Save and Resume
    // ========================================

    @Test
    fun testDrillProgress_SaveAndResume() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // Complete 3 cards
        repeat(3) {
            val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
            ) }
            sessionRunner.submitAnswer()
        }

        // ASSERT: Progress saved after each card
        val savedProgress = drillProgressStore.getDrillProgress(testLessonId.value)
        assertTrue("Progress should be saved", savedProgress >= 0)

        // ACT: Exit drill mode
        sessionRunner.exitDrillMode()

        // ASSERT: Progress still saved after exit
        val progressAfterExit = drillProgressStore.getDrillProgress(testLessonId.value)
        assertTrue("Progress should persist after exit", progressAfterExit > 0)
        assertEquals("Progress should be at card 3", 3, progressAfterExit)

        // ACT: Resume drill
        sessionRunner.startDrill(resume = true)

        // ASSERT: Resumes at saved position
        val state = stateAccess.uiState.value
        assertEquals("Should resume at card index 3", 3, state.drill.drillCardIndex)
        assertEquals("Current card should be drill-4", "drill-4", state.cardSession.currentCard?.id)

        // Complete remaining cards
        sessionRunner.startSession()
        var completedCount = 0
        while (stateAccess.uiState.value.drill.isDrillMode && completedCount < 20) {
            val currentCard = stateAccess.uiState.value.cardSession.currentCard
            if (currentCard == null) break
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
            ) }
            sessionRunner.submitAnswer()
            completedCount++
        }

        // ASSERT: All cards completed
        assertTrue("Should complete all remaining cards", completedCount >= 12)

        // ASSERT: Progress cleared after completion
        val finalProgress = drillProgressStore.getDrillProgress(testLessonId.value)
        assertEquals("Progress should be cleared after completion", -1, finalProgress)
    }

    // ========================================
    // TEST 10: Completion Returns to Lesson Roadmap
    // ========================================

    @Test
    fun testCompletion_ReturnsToLessonRoadmap() = runBlocking {
        // ACT: Start drill and complete all cards
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        val totalCards = stateAccess.uiState.value.drill.drillTotalCards

        repeat(totalCards) {
            val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
            ) }
            sessionRunner.submitAnswer()
        }

        // ASSERT: Drill mode is no longer active
        val finalState = stateAccess.uiState.value
        assertFalse("isDrillMode should be false after completion", finalState.drill.isDrillMode)
        assertEquals("drillCardIndex should reset to 0", 0, finalState.drill.drillCardIndex)
        assertEquals("drillTotalCards should reset to 0", 0, finalState.drill.drillTotalCards)

        // ASSERT: Session is PAUSED
        assertEquals("Session should be PAUSED after completion", SessionState.PAUSED, finalState.cardSession.sessionState)

        // ASSERT: Current card is null (session cleared)
        assertNull("Current card should be null after completion", finalState.cardSession.currentCard)

        // ASSERT: subLessonFinishedToken incremented (signals completion to UI)
        assertTrue("subLessonFinishedToken should be incremented", finalState.cardSession.subLessonFinishedToken > 0)

        // NOTE: Actual navigation back to LessonRoadmapScreen is handled
        // by GrammarMateApp.kt observing the state change and checking
        // returnTo route. This test verifies the state changes that trigger that.
    }

    // ========================================
    // Additional Edge Case Tests
    // ========================================

    @Test
    fun testEmptyDrillCards_NoSessionStarted() = runBlocking {
        // ARRANGE: Create lesson with no drill cards
        val lessonNoDrill = Lesson(
            id = LessonId("lesson-no-drill"),
            languageId = testLanguageId,
            title = "No Drill Lesson",
            cards = emptyList(),
            drillCards = emptyList()
        )
        lessonStore.addLesson(lessonNoDrill)

        stateAccess.updateState { it.copy(
            navigation = it.navigation.copy(
                selectedLessonId = LessonId("lesson-no-drill"),
                lessons = it.navigation.lessons + lessonNoDrill
            )
        ) }

        // ACT: Try to start drill
        val events = sessionRunner.startDrill(resume = false)

        // ASSERT: No events returned (early exit)
        assertTrue("Should return no events for empty drill", events.isEmpty())

        // ASSERT: Drill mode not activated
        val state = stateAccess.uiState.value
        assertFalse("isDrillMode should be false", state.drill.isDrillMode)
    }

    @Test
    fun testDrillInputModeSwitching() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // TEST: Switch to KEYBOARD mode
        sessionRunner.setInputMode(InputMode.KEYBOARD)
        assertEquals("Input mode should be KEYBOARD", InputMode.KEYBOARD, stateAccess.uiState.value.cardSession.inputMode)

        // TEST: Switch to VOICE mode
        sessionRunner.setInputMode(InputMode.VOICE)
        assertEquals("Input mode should be VOICE", InputMode.VOICE, stateAccess.uiState.value.cardSession.inputMode)

        // TEST: Switch to WORD_BANK mode
        sessionRunner.setInputMode(InputMode.WORD_BANK)
        assertEquals("Input mode should be WORD_BANK", InputMode.WORD_BANK, stateAccess.uiState.value.cardSession.inputMode)

        // ASSERT: Drill mode still active
        assertTrue("isDrillMode should remain true", stateAccess.uiState.value.drill.isDrillMode)
    }

    @Test
    fun testDrillWordBankGeneration() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // TEST: Switch to WORD_BANK mode (triggers word bank generation)
        sessionRunner.setInputMode(InputMode.WORD_BANK)

        // ASSERT: Word bank words generated from drill card answers
        val state = stateAccess.uiState.value
        assertTrue("Word bank should have words", state.cardSession.wordBankWords.isNotEmpty())

        // Verify words come from drill card answers
        val currentCard = state.cardSession.currentCard
        assertNotNull("Current card should not be null", currentCard)
        val expectedWords = currentCard!!.acceptedAnswers.first().split(" ")
        assertTrue("Word bank should contain words from drill card",
            state.cardSession.wordBankWords.containsAll(expectedWords))
    }

    @Test
    fun testDrillPauseAndResume() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // ASSERT: Session is ACTIVE
        assertEquals("Session should be ACTIVE", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // TEST: Pause session
        sessionRunner.pauseSession()
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // ASSERT: Drill mode still active during pause
        assertTrue("isDrillMode should remain true during pause", stateAccess.uiState.value.drill.isDrillMode)

        // TEST: Resume session
        sessionRunner.startSession()
        assertEquals("Session should be ACTIVE again", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // Verify can still submit answers
        val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
        ) }
        val (result, events) = sessionRunner.submitAnswer()
        assertTrue("Answer should be accepted after resume", result.accepted)
    }

    @Test
    fun testDrillMidSessionExit() = runBlocking {
        // ACT: Start drill session
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        // Complete 2 cards
        repeat(2) {
            val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
            ) }
            sessionRunner.submitAnswer()
        }

        // ACT: Exit drill mode mid-session
        sessionRunner.exitDrillMode()

        // ASSERT: Drill mode deactivated
        val state = stateAccess.uiState.value
        assertFalse("isDrillMode should be false", state.drill.isDrillMode)
        assertEquals("drillCardIndex should reset to 0", 0, state.drill.drillCardIndex)

        // ASSERT: Progress saved (at index 2)
        val savedProgress = drillProgressStore.getDrillProgress(testLessonId.value)
        assertEquals("Progress should be saved at card index 2", 2, savedProgress)

        // ASSERT: Session paused (currentCard remains for UX - user sees where they left off)
        assertNotNull("Current card should remain (for UX)", state.cardSession.currentCard)
        assertEquals("Session should be PAUSED", SessionState.PAUSED, state.cardSession.sessionState)
    }

    @Test
    fun testDrillStartFresh_ClearsProgress() = runBlocking {
        // ARRANGE: Start drill and make progress
        sessionRunner.startDrill(resume = false)
        sessionRunner.startSession()

        val currentCard = stateAccess.uiState.value.cardSession.currentCard!!
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = currentCard.acceptedAnswers.first())
        ) }
        sessionRunner.submitAnswer()

        sessionRunner.exitDrillMode()

        // ASSERT: Progress exists
        assertTrue("Should have progress", drillProgressStore.hasProgress(testLessonId.value))

        // ACT: Start fresh (not resume)
        sessionRunner.startDrill(resume = false)

        // ASSERT: Starts from beginning
        val state = stateAccess.uiState.value
        assertEquals("drillCardIndex should be 0 (fresh start)", 0, state.drill.drillCardIndex)
        assertEquals("Current card should be first", "drill-1", state.cardSession.currentCard?.id)
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
