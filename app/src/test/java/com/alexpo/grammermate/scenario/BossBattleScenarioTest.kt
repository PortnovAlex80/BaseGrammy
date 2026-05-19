package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.BossReward
import com.alexpo.grammermate.data.BossState
import com.alexpo.grammermate.data.BossType
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.boss.BossBattleRunner
import com.alexpo.grammermate.feature.boss.BossOrchestrator
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Test

/**
 * Scenario test for Boss Battle training session.
 *
 * Tests the complete flow of a boss battle using real SessionRunner
 * with in-memory fake implementations for all dependencies.
 *
 * Scenario:
 * 1. Create lesson with 15 cards (enough for boss unlock)
 * 2. Start boss battle (via BossOrchestrator)
 * 3. Verify: bossActive=true, bossProgress=0, bossTotal set
 * 4. Answer boss cards correctly → verify progress increases
 * 5. Reach reward thresholds (30%, 60%, 90%) → verify reward messages
 * 6. Reach last card → verify needsBossFinish signal
 * 7. Finish boss → verify reward shown, bossActive=false
 *
 * Uses no mocks — only real SessionRunner + BossOrchestrator + in-memory fakes.
 */
@RunWith(RobolectricTestRunner::class)
class BossBattleScenarioTest {

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
    private lateinit var bossBattleRunner: BossBattleRunner
    private lateinit var bossOrchestrator: BossOrchestrator

    private val testLanguageId = LanguageId("en")
    private val testLessonId = LessonId("lesson-01")
    private val coroutineScope = CoroutineScope(Dispatchers.Unconfined)

    // Helper to create a test card
    private fun createCard(id: String, ru: String, answers: List<String>): com.alexpo.grammermate.data.SentenceCard {
        return com.alexpo.grammermate.data.SentenceCard(id = id, promptRu = ru, acceptedAnswers = answers)
    }

    // Helper to create a test lesson with enough cards for boss
    private fun createTestLesson(): Lesson {
        val cards = (1..20).map { i ->
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
            ),
            cardSession = com.alexpo.grammermate.data.CardSessionState(
                completedSubLessonCount = 15, // Unlock boss (requires 15)
                subLessonCount = 20
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
        bossBattleRunner = BossBattleRunner()

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
            getSchedule = { lessonId -> null },
            calculateCompletedSubLessons = { subLessons, mastery, lessonId ->
                progressTracker.calculateCompletedSubLessons(subLessons, mastery, testLessonId, listOf(createTestLesson()))
            },
            onTimerSaveProgress = { /* No-op for test */ },
            sessionTimerMsSink = null
        )

        // Initialize BossOrchestrator
        bossOrchestrator = BossOrchestrator(
            stateAccess = stateAccess,
            bossBattleRunner = bossBattleRunner,
            cardProvider = cardProvider,
            sessionRunner = sessionRunner,
            progressStore = progressStore,
            masteryStore = masteryStore
        )
    }

    // ========================================
    // SCENARIO: Complete Boss Battle flow
    // ========================================

    @Test
    fun scenario_bossBattle_completeFlow() = runBlocking {
        // --- STEP 1: Start boss battle ---
        val startCommands = bossOrchestrator.startBossLesson()

        // Verify: Boss is now active
        val bossState = bossOrchestrator.stateFlow.value
        assertTrue("Boss should be active after start", bossState.bossActive)
        assertEquals("Boss type should be LESSON", BossType.LESSON, bossState.bossType)
        assertEquals("Boss progress should start at 0", 0, bossState.bossProgress)
        assertTrue("Boss total should be > 0", bossState.bossTotal > 0)
        assertNull("Boss reward should be null initially", bossState.bossReward)

        // Verify: Session is PAUSED (boss starts paused)
        assertEquals("Session should be PAUSED after boss start",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // Verify: Cards are loaded
        assertNotNull("Current card should not be null", stateAccess.uiState.value.cardSession.currentCard)
        assertTrue("Sub-lesson total should match boss cards",
            stateAccess.uiState.value.cardSession.subLessonTotal > 0)

        // --- STEP 2: Start the active session ---
        sessionRunner.startSession()

        assertEquals("Session should be ACTIVE after start",
            SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        // --- STEP 3: Answer cards and verify progress tracking ---
        val totalCards = bossOrchestrator.stateFlow.value.bossTotal
        var correctCount = 0

        for (i in 0 until totalCards) {
            val currentCardIndex = stateAccess.uiState.value.cardSession.currentIndex
            val cardNum = i + 1

            // Submit correct answer
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english word $cardNum")
            ) }

            val (result, events) = sessionRunner.submitAnswer()

            // Verify: Answer was accepted
            assertTrue("Answer $cardNum should be accepted", result.accepted)
            assertFalse("No hint should be shown for correct answer", result.hintShown)

            correctCount++

            // Check boss finish signal on last card
            if (i == totalCards - 1) {
                assertTrue("Last card should signal boss finish", result.needsBossFinish)
            } else {
                assertFalse("Mid-card should not signal boss finish", result.needsBossFinish)
            }

            // Check progress updates after each card
            val updatedBossState = bossOrchestrator.stateFlow.value
            assertTrue("Boss progress should be >= $i", updatedBossState.bossProgress >= i)

            // Verify correct count increases
            assertEquals("Correct count should be $cardNum", cardNum, stateAccess.uiState.value.cardSession.correctCount)
        }

        // --- STEP 4: Finish boss battle ---
        val finishCommands = bossOrchestrator.finishBoss()

        // Verify: Boss is no longer active
        val finalBossState = bossOrchestrator.stateFlow.value
        assertFalse("Boss should not be active after finish", finalBossState.bossActive)
        assertNull("Boss type should be null after finish", finalBossState.bossType)
        assertEquals("Boss progress should reset to 0", 0, finalBossState.bossProgress)

        // Verify: Reward was assigned (based on 100% completion)
        assertNotNull("Boss reward should not be null after 100% completion", finalBossState.bossReward)
        assertEquals("Final reward should be GOLD for 100%", BossReward.GOLD, finalBossState.bossReward)
        assertTrue("Boss finished token should increment", finalBossState.bossFinishedToken > 0)

        // Verify: Session is PAUSED after boss
        assertEquals("Session should be PAUSED after boss finish",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    @Test
    fun scenario_bossBattle_rewardThresholds() = runBlocking {
        // --- STEP 1: Start boss battle ---
        bossOrchestrator.startBossLesson()
        sessionRunner.startSession()

        val totalCards = bossOrchestrator.stateFlow.value.bossTotal

        // --- STEP 2: Verify Bronze threshold (30%) ---
        val bronzeIndex = (totalCards * 0.3).toInt()

        // Answer cards up to bronze threshold
        for (i in 0 until bronzeIndex) {
            val cardNum = i + 1
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english word $cardNum")
            ) }
            sessionRunner.submitAnswer()

            // Manually update boss progress (normally done by nextCard)
            val nextIndex = i + 1
            bossOrchestrator.advanceBossProgressOnNextCard(nextIndex, totalCards)
        }

        var bossState = bossOrchestrator.stateFlow.value
        assertTrue("Boss progress should reach ~30%", bossState.bossProgress >= bronzeIndex)
        assertEquals("Bronze reward should be set", BossReward.BRONZE, bossState.bossReward)
        assertNotNull("Reward message should be set", bossState.bossRewardMessage)

        // --- STEP 3: Verify Silver threshold (60%) ---
        val silverIndex = (totalCards * 0.6).toInt()

        // Answer more cards
        for (i in bronzeIndex until silverIndex) {
            val cardNum = i + 1
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english word $cardNum")
            ) }
            sessionRunner.submitAnswer()

            bossOrchestrator.advanceBossProgressOnNextCard(i + 1, totalCards)
        }

        bossState = bossOrchestrator.stateFlow.value
        assertTrue("Boss progress should reach ~60%", bossState.bossProgress >= silverIndex)
        assertEquals("Silver reward should be set", BossReward.SILVER, bossState.bossReward)

        // --- STEP 4: Verify Gold threshold (90%) ---
        val goldIndex = (totalCards * 0.9).toInt()

        for (i in silverIndex until goldIndex) {
            val cardNum = i + 1
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english word $cardNum")
            ) }
            sessionRunner.submitAnswer()

            bossOrchestrator.advanceBossProgressOnNextCard(i + 1, totalCards)
        }

        bossState = bossOrchestrator.stateFlow.value
        assertTrue("Boss progress should reach ~90%", bossState.bossProgress >= goldIndex)
        assertEquals("Gold reward should be set", BossReward.GOLD, bossState.bossReward)
    }

    @Test
    fun scenario_bossBattle_midCardAdvances() = runBlocking {
        // --- SETUP: Start boss battle ---
        bossOrchestrator.startBossLesson()
        sessionRunner.startSession()

        val initialIndex = stateAccess.uiState.value.cardSession.currentIndex
        assertEquals("Initial index should be 0", 0, initialIndex)

        // --- TEST: Correct answer on mid-card advances ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }

        val (result, events) = sessionRunner.submitAnswer()

        assertTrue("Answer should be accepted", result.accepted)
        assertFalse("Should not be boss finish on mid-card", result.needsBossFinish)

        // Verify: Card advanced
        val newIndex = stateAccess.uiState.value.cardSession.currentIndex
        assertEquals("Index should advance to 1", 1, newIndex)
    }

    @Test
    fun scenario_bossBattle_wrongAnswerStaysOnCard() = runBlocking {
        // --- SETUP: Start boss battle ---
        bossOrchestrator.startBossLesson()
        sessionRunner.startSession()

        // --- TEST: Wrong answer stays on card ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong answer")
        ) }

        val (result, events) = sessionRunner.submitAnswer()

        assertFalse("Wrong answer should be rejected", result.accepted)
        assertEquals("Index should stay at 0", 0, stateAccess.uiState.value.cardSession.currentIndex)

        // --- TEST: Correct answer after wrong advances ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }

        val (correctResult, correctEvents) = sessionRunner.submitAnswer()

        assertTrue("Correct answer should be accepted", correctResult.accepted)
        assertEquals("Index should advance to 1", 1, stateAccess.uiState.value.cardSession.currentIndex)
    }

    @Test
    fun scenario_bossBattle_pauseAndResume() = runBlocking {
        // --- SETUP: Start boss battle ---
        bossOrchestrator.startBossLesson()
        sessionRunner.startSession()

        assertTrue("Boss should be active", bossOrchestrator.stateFlow.value.bossActive)

        // --- TEST: Pause session ---
        sessionRunner.pauseSession()
        assertEquals("Session should be PAUSED", SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // --- TEST: Boss remains active during pause ---
        assertTrue("Boss should remain active during pause", bossOrchestrator.stateFlow.value.bossActive)

        // --- TEST: Resume and answer ---
        sessionRunner.startSession()
        assertEquals("Session should be ACTIVE", SessionState.ACTIVE, stateAccess.uiState.value.cardSession.sessionState)

        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "english word 1")
        ) }

        val (result, events) = sessionRunner.submitAnswer()
        assertTrue("Answer should be accepted after resume", result.accepted)
    }

    @Test
    fun scenario_bossBattle_clearRewardMessage() = runBlocking {
        // --- SETUP: Start boss, reach bronze, trigger reward message ---
        bossOrchestrator.startBossLesson()
        sessionRunner.startSession()

        val totalCards = bossOrchestrator.stateFlow.value.bossTotal
        val bronzeIndex = (totalCards * 0.3).toInt()

        for (i in 0 until bronzeIndex) {
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = "english word ${i + 1}")
            ) }
            sessionRunner.submitAnswer()
            bossOrchestrator.advanceBossProgressOnNextCard(i + 1, totalCards)
        }

        var bossState = bossOrchestrator.stateFlow.value
        assertNotNull("Reward message should be set", bossState.bossRewardMessage)

        // --- TEST: Clear reward message ---
        val clearCommands = bossOrchestrator.clearBossRewardMessage()

        bossState = bossOrchestrator.stateFlow.value
        assertNull("Reward message should be cleared", bossState.bossRewardMessage)
        assertTrue("Boss should still be active", bossState.bossActive)
    }

    @Test
    fun scenario_bossBattle_testModeBypass() = runBlocking {
        // --- SETUP: Set test mode and low sub-lesson count ---
        stateAccess.setState(
            stateAccess.uiState.value.copy(
                cardSession = stateAccess.uiState.value.cardSession.copy(
                    testMode = true,
                    completedSubLessonCount = 1, // Less than 15
                    subLessonCount = 20
                )
            )
        )

        // --- TEST: Boss should start in test mode despite low completion ---
        val startCommands = bossOrchestrator.startBossLesson()

        val bossState = bossOrchestrator.stateFlow.value
        assertTrue("Boss should start in test mode", bossState.bossActive)
        assertEquals("Boss type should be LESSON", BossType.LESSON, bossState.bossType)
    }

    @Test
    fun scenario_bossBattle_noLessonSelected_fails() = runBlocking {
        // --- SETUP: Clear selected lesson ---
        stateAccess.setState(
            stateAccess.uiState.value.copy(
                navigation = stateAccess.uiState.value.navigation.copy(
                    selectedLessonId = null
                )
            )
        )

        // --- TEST: Boss start should fail ---
        val startCommands = bossOrchestrator.startBossLesson()

        val bossState = bossOrchestrator.stateFlow.value
        assertFalse("Boss should not start without lesson", bossState.bossActive)
        assertNull("Boss type should be null", bossState.bossType)
        assertNotNull("Error message should be set", bossState.bossErrorMessage)
    }

    @Test
    fun scenario_bossBattle_insufficientSubLessons_fails() = runBlocking {
        // --- SETUP: Set low sub-lesson completion ---
        stateAccess.setState(
            stateAccess.uiState.value.copy(
                cardSession = stateAccess.uiState.value.cardSession.copy(
                    testMode = false,
                    completedSubLessonCount = 5, // Less than 15
                    subLessonCount = 20
                )
            )
        )

        // --- TEST: Boss start should fail ---
        val startCommands = bossOrchestrator.startBossLesson()

        val bossState = bossOrchestrator.stateFlow.value
        assertFalse("Boss should not start with insufficient completion", bossState.bossActive)
        assertNotNull("Error message should be set", bossState.bossErrorMessage)
        assertTrue("Error should mention completion requirement",
            bossState.bossErrorMessage?.contains("Complete") == true)
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
