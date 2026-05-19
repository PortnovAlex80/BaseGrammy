package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.EliteState
import com.alexpo.grammermate.data.InputMode
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.Lesson
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.SessionState
import com.alexpo.grammermate.data.SentenceCard
import com.alexpo.grammermate.data.TrainingConfig
import com.alexpo.grammermate.data.TrainingUiState
import com.alexpo.grammermate.feature.progress.ProgressTracker
import com.alexpo.grammermate.feature.progress.StreakManager
import com.alexpo.grammermate.feature.training.AnswerValidator
import com.alexpo.grammermate.feature.training.CardProvider
import com.alexpo.grammermate.feature.training.SessionRunner
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

/**
 * Scenario test for Elite mode sub-mode training session.
 *
 * Tests the complete flow of elite step sessions using real SessionRunner
 * with in-memory fake implementations for all dependencies.
 *
 * Elite mode overview:
 * - 7 steps (TrainingConfig.ELITE_STEP_COUNT)
 * - Each step has eliteSize = ceil(10 * 1.25) = 13 cards
 * - Cards are shuffled from all lesson cards
 * - Speed metrics (voice words per minute) are tracked
 * - Best speeds per step are recorded
 * - Session can be cancelled mid-step
 *
 * Scenario:
 * 1. Create lesson with 20 cards (enough for elite step)
 * 2. Open elite step (via openEliteStep)
 * 3. Verify: eliteActive=true, correct cards loaded (eliteSize)
 * 4. Answer cards correctly with voice input simulation
 * 5. Track speed metrics (voiceActiveMs, voiceWordCount)
 * 6. Complete elite step → verify step advances, speed recorded
 * 7. Cancel elite session → verify state reset
 *
 * Uses no mocks — only real SessionRunner + in-memory fakes.
 */
class EliteModeScenarioTest {

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

    // Helper to create a test lesson with 20 cards (enough for elite)
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
            wordBankGenerator = com.alexpo.grammermate.feature.training.WordBankGenerator,
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
    }

    // ========================================
    // SCENARIO 1: Open elite step and verify state
    // ========================================

    @Test
    fun scenario_openEliteStep_verifiesInitialState() = runBlocking {
        // --- SETUP: Create lesson with 20 cards ---
        val lesson = createTestLesson()

        // --- ACTION 1: Open elite step at index 0 ---
        val openEvents = sessionRunner.openEliteStep(index = 0)

        // --- VERIFY 1: Elite is now active ---
        assertTrue("Elite should be active after openEliteStep",
            stateAccess.uiState.value.elite.eliteActive)

        // --- VERIFY 2: Step index is set correctly ---
        assertEquals("Step index should be 0",
            0, stateAccess.uiState.value.elite.eliteStepIndex)

        // --- VERIFY 3: Session is in PAUSED state (ready to start) ---
        assertEquals("Session should be PAUSED after opening elite step",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // --- VERIFY 4: Elite cards are loaded (should be 13 = ceil(10 * 1.25)) ---
        val expectedEliteSize = sessionRunner.eliteSubLessonSize()
        val sessionCards = sessionRunner.getSessionCards()
        assertEquals("Elite step should load $expectedEliteSize cards",
            expectedEliteSize, sessionCards.size)

        // --- VERIFY 5: Current card is the first elite card ---
        assertNotNull("Current card should not be null",
            stateAccess.uiState.value.cardSession.currentCard)

        // --- VERIFY 6: All session counters are reset for elite ---
        assertEquals("Correct count should be 0 at elite start",
            0, stateAccess.uiState.value.cardSession.correctCount)
        assertEquals("Incorrect count should be 0 at elite start",
            0, stateAccess.uiState.value.cardSession.incorrectCount)
        assertEquals("Active time should be 0 at elite start",
            0L, stateAccess.uiState.value.cardSession.activeTimeMs)
    }

    // ========================================
    // SCENARIO 2: Complete elite step with voice metrics
    // ========================================

    @Test
    fun scenario_completeEliteStep_tracksSpeedAndAdvances() = runBlocking {
        // --- SETUP: Open elite step ---
        sessionRunner.openEliteStep(index = 0)

        // Set VOICE input mode to simulate voice input
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        val initialStepIndex = stateAccess.uiState.value.elite.eliteStepIndex
        val initialBestSpeeds = stateAccess.uiState.value.elite.eliteBestSpeeds

        // --- ACTION: Answer all cards correctly with voice simulation ---
        val sessionCards = sessionRunner.getSessionCards()
        var totalVoiceWords = 0

        for ((index, card) in sessionCards.withIndex()) {
            // Verify we're on the right card
            assertEquals("Should be on card at index $index",
                index, stateAccess.uiState.value.cardSession.currentIndex)

            // Simulate voice input: set voice prompt start time
            sessionRunner.onVoicePromptStarted()

            // Submit correct answer (simulate voice recognition result)
            val correctAnswer = card.acceptedAnswers.first()
            stateAccess.updateState { it.copy(
                cardSession = it.cardSession.copy(inputText = correctAnswer)
            ) }

            // Simulate voice duration by advancing time (not real-time in test)
            // The actual voice timing would be measured by SystemClock

            val (result, events) = sessionRunner.submitAnswer()

            // Verify: Answer was accepted
            assertTrue("Answer should be accepted for card ${card.id}", result.accepted)

            // Count voice words (3+ character words)
            val wordsInAnswer = correctAnswer.split(" ").count { it.length >= 3 }
            totalVoiceWords += wordsInAnswer
        }

        // --- VERIFY 1: Step completed (needsEliteFinish signal) ---
        // The last card submission should trigger elite finish
        // Check that we're back at index 0 (session reset)
        assertEquals("Index should reset to 0 after elite step completion",
            0, stateAccess.uiState.value.cardSession.currentIndex)

        // --- VERIFY 2: Elite is no longer active after step completion ---
        assertFalse("Elite should not be active after step completion",
            stateAccess.uiState.value.elite.eliteActive)

        // --- VERIFY 3: Step index advanced (0 -> 1) ---
        assertEquals("Step index should advance to 1",
            1, stateAccess.uiState.value.elite.eliteStepIndex)

        // --- VERIFY 4: Voice metrics tracked ---
        assertTrue("Voice active time should be > 0",
            stateAccess.uiState.value.cardSession.voiceActiveMs > 0)
        assertTrue("Voice word count should be > 0",
            stateAccess.uiState.value.cardSession.voiceWordCount > 0)

        // --- VERIFY 5: Speed was calculated and recorded ---
        val newBestSpeeds = stateAccess.uiState.value.elite.eliteBestSpeeds
        assertTrue("Best speeds list should be populated",
            newBestSpeeds.isNotEmpty())

        // The speed at step 0 should be recorded (or 0.0 if first run)
        val speedAtStep0 = newBestSpeeds.getOrNull(0)
        assertNotNull("Speed at step 0 should be recorded", speedAtStep0)

        // --- VERIFY 6: Session is PAUSED after completion ---
        assertEquals("Session should be PAUSED after elite step completion",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)
    }

    // ========================================
    // SCENARIO 3: Cancel elite session mid-step
    // ========================================

    @Test
    fun scenario_cancelEliteSession_resetsState() = runBlocking {
        // --- SETUP: Open elite step and answer some cards ---
        sessionRunner.openEliteStep(index = 2)
        sessionRunner.setInputMode(InputMode.VOICE)
        sessionRunner.startSession()

        // Answer first card correctly
        val firstCard = sessionRunner.currentCard()!!
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = firstCard.acceptedAnswers.first())
        ) }
        sessionRunner.submitAnswer()

        // Verify we made progress
        assertTrue("Should have answered at least 1 card correctly",
            stateAccess.uiState.value.cardSession.correctCount > 0)

        // --- ACTION: Cancel elite session ---
        val cancelEvents = sessionRunner.cancelEliteSession()

        // --- VERIFY 1: Elite is no longer active ---
        assertFalse("Elite should not be active after cancel",
            stateAccess.uiState.value.elite.eliteActive)

        // --- VERIFY 2: Step index is preserved (doesn't reset) ---
        // The step index stays at 2 (can retry the same step)
        assertEquals("Step index should remain at 2 after cancel",
            2, stateAccess.uiState.value.elite.eliteStepIndex)

        // --- VERIFY 3: Session is PAUSED ---
        assertEquals("Session should be PAUSED after cancel",
            SessionState.PAUSED, stateAccess.uiState.value.cardSession.sessionState)

        // --- VERIFY 4: Progress is NOT saved (best speeds unchanged) ---
        // Speed tracking only happens on successful completion
        val speeds = stateAccess.uiState.value.elite.eliteBestSpeeds
        assertEquals("Best speeds should remain empty after cancel",
            emptyList<Double>(), speeds)

        // --- VERIFY 5: Session events include SaveProgress and RefreshFlowerStates ---
        assertTrue("Cancel should trigger SaveProgress event",
            cancelEvents.any { it is com.alexpo.grammermate.feature.training.SessionEvent.SaveProgress })
        assertTrue("Cancel should trigger RefreshFlowerStates event",
            cancelEvents.any { it is com.alexpo.grammermate.feature.training.SessionEvent.RefreshFlowerStates })
    }

    // ========================================
    // SCENARIO 4: Elite step size calculation
    // ========================================

    @Test
    fun scenario_eliteStepSize_usesMultiplier() = runBlocking {
        // --- SETUP: Set custom elite size multiplier ---
        sessionRunner.setEliteSizeMultiplier(2.0)
        sessionRunner.setSubLessonSize(10)

        // --- ACTION: Open elite step ---
        sessionRunner.openEliteStep(index = 0)

        // --- VERIFY: Elite step size = ceil(10 * 2.0) = 20 cards ---
        val expectedSize = 20 // ceil(10 * 2.0)
        val actualSize = sessionRunner.getSessionCards().size
        assertEquals("Elite step should load $expectedSize cards with 2.0x multiplier",
            expectedSize, actualSize)
    }

    // ========================================
    // SCENARIO 5: Elite unlock condition
    // ========================================

    @Test
    fun scenario_eliteUnlockCondition_requires12Lessons() = runBlocking {
        // --- VERIFY: Not unlocked with fewer than 12 lessons ---
        val fewLessons = (1..11).map { i ->
            Lesson(
                id = com.alexpo.grammermate.data.LessonId("lesson-$i"),
                languageId = testLanguageId,
                title = "Lesson $i",
                cards = emptyList()
            )
        }
        assertFalse("Elite should not be unlocked with 11 lessons",
            sessionRunner.resolveEliteUnlocked(fewLessons, testMode = false))

        // --- VERIFY: Unlocked with 12 or more lessons ---
        val enoughLessons = (1..12).map { i ->
            Lesson(
                id = com.alexpo.grammermate.data.LessonId("lesson-$i"),
                languageId = testLanguageId,
                title = "Lesson $i",
                cards = emptyList()
            )
        }
        assertTrue("Elite should be unlocked with 12 lessons",
            sessionRunner.resolveEliteUnlocked(enoughLessons, testMode = false))

        // --- VERIFY: Always unlocked in test mode ---
        assertTrue("Elite should always be unlocked in test mode",
            sessionRunner.resolveEliteUnlocked(emptyList(), testMode = true))
    }

    // ========================================
    // SCENARIO 6: Speed calculation
    // ========================================

    @Test
    fun scenario_speedCalculation_wordsPerMinute() {
        // --- TEST 1: Zero active time returns 0.0 ---
        val speed1 = sessionRunner.calculateSpeedPerMinute(activeMs = 0, words = 100)
        assertEquals("Speed should be 0.0 with zero active time",
            0.0, speed1, 0.001)

        // --- TEST 2: 60 seconds, 30 words → 30 WPM ---
        val speed2 = sessionRunner.calculateSpeedPerMinute(activeMs = 60000, words = 30)
        assertEquals("Speed should be 30.0 WPM",
            30.0, speed2, 0.001)

        // --- TEST 3: 30 seconds, 30 words → 60 WPM ---
        val speed3 = sessionRunner.calculateSpeedPerMinute(activeMs = 30000, words = 30)
        assertEquals("Speed should be 60.0 WPM",
            60.0, speed3, 0.001)

        // --- TEST 4: 120 seconds, 60 words → 30 WPM ---
        val speed4 = sessionRunner.calculateSpeedPerMinute(activeMs = 120000, words = 60)
        assertEquals("Speed should be 30.0 WPM",
            30.0, speed4, 0.001)
    }

    // ========================================
    // SCENARIO 7: Elite speeds normalization
    // ========================================

    @Test
    fun scenario_eliteSpeedsNormalization_padsToStepCount() {
        val stepCount = TrainingConfig.ELITE_STEP_COUNT // 7

        // --- TEST 1: Empty list → padded with zeros ---
        val normalized1 = sessionRunner.normalizeEliteSpeeds(emptyList())
        assertEquals("Empty list should be padded to $stepCount zeros",
            stepCount, normalized1.size)
        assertTrue("All elements should be 0.0",
            normalized1.all { it == 0.0 })

        // --- TEST 2: Partial list → padded with zeros ---
        val partial = listOf(10.0, 20.0, 30.0)
        val normalized2 = sessionRunner.normalizeEliteSpeeds(partial)
        assertEquals("Partial list should be padded to $stepCount elements",
            stepCount, normalized2.size)
        assertEquals("First 3 elements should be preserved",
            partial, normalized2.take(3))
        assertEquals("Remaining elements should be zeros",
            List(stepCount - 3) { 0.0 }, normalized2.drop(3))

        // --- TEST 3: Full list → trimmed to step count ---
        val full = (1..10).map { it.toDouble() }
        val normalized3 = sessionRunner.normalizeEliteSpeeds(full)
        assertEquals("Full list should be trimmed to $stepCount elements",
            stepCount, normalized3.size)
        assertEquals("First $stepCount elements should be preserved",
            full.take(stepCount), normalized3)
    }

    // ========================================
    // SCENARIO 8: Complete all elite steps
    // ========================================

    @Test
    fun scenario_completeAllEliteSteps_cyclesThroughSteps() = runBlocking {
        val stepCount = TrainingConfig.ELITE_STEP_COUNT
        val bestSpeedsByStep = mutableMapOf<Int, Double>()

        // --- ACTION: Complete all 7 elite steps ---
        for (step in 0 until stepCount) {
            // Open elite step
            sessionRunner.openEliteStep(index = step)

            // Verify step index
            assertEquals("Step index should be $step",
                step, stateAccess.uiState.value.elite.eliteStepIndex)

            // Start session
            sessionRunner.setInputMode(InputMode.VOICE)
            sessionRunner.startSession()

            // Answer all cards in this step
            val sessionCards = sessionRunner.getSessionCards()
            for (card in sessionCards) {
                stateAccess.updateState { it.copy(
                    cardSession = it.cardSession.copy(inputText = card.acceptedAnswers.first())
                ) }
                sessionRunner.submitAnswer()
            }

            // Record the best speed at this step
            val currentBestSpeeds = stateAccess.uiState.value.elite.eliteBestSpeeds
            val speedAtStep = currentBestSpeeds.getOrNull(step) ?: 0.0
            bestSpeedsByStep[step] = speedAtStep

            // Verify step advanced (or cycled to 0)
            val expectedNextStep = (step + 1) % stepCount
            assertEquals("Step should advance to $expectedNextStep after completing step $step",
                expectedNextStep, stateAccess.uiState.value.elite.eliteStepIndex)
        }

        // --- VERIFY: All steps have been recorded ---
        val finalBestSpeeds = stateAccess.uiState.value.elite.eliteBestSpeeds
        assertEquals("Should have $stepCount speed entries",
            stepCount, finalBestSpeeds.size)

        // --- VERIFY: After completing step 6, it cycles back to step 0 ---
        assertEquals("After completing all steps, should cycle back to step 0",
            0, stateAccess.uiState.value.elite.eliteStepIndex)
    }

    // ========================================
    // SCENARIO 9: Elite session with wrong answers
    // ========================================

    @Test
    fun scenario_eliteSession_wrongAnswersDontAffectStepAdvancement() = runBlocking {
        // --- SETUP: Open elite step ---
        sessionRunner.openEliteStep(index = 0)
        sessionRunner.startSession()

        val sessionCards = sessionRunner.getSessionCards()
        val firstCard = sessionCards.first()

        // --- ACTION: Submit wrong answer ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = "wrong answer")
        ) }
        val (wrongResult, wrongEvents) = sessionRunner.submitAnswer()

        // --- VERIFY: Wrong answer rejected ---
        assertFalse("Wrong answer should be rejected", wrongResult.accepted)

        // --- VERIFY: Still on same card ---
        assertEquals("Should remain on first card after wrong answer",
            0, stateAccess.uiState.value.cardSession.currentIndex)

        // --- ACTION: Submit correct answer ---
        stateAccess.updateState { it.copy(
            cardSession = it.cardSession.copy(inputText = firstCard.acceptedAnswers.first())
        ) }
        val (correctResult, correctEvents) = sessionRunner.submitAnswer()

        // --- VERIFY: Correct answer accepted ---
        assertTrue("Correct answer should be accepted", correctResult.accepted)

        // --- VERIFY: Advanced to next card ---
        assertEquals("Should advance to next card after correct answer",
            1, stateAccess.uiState.value.cardSession.currentIndex)
    }

    // ========================================
    // SCENARIO 10: Elite with different step indices
    // ========================================

    @Test
    fun scenario_openEliteStep_atVariousIndices() = runBlocking {
        val stepCount = TrainingConfig.ELITE_STEP_COUNT

        // Test opening each valid step index
        for (step in 0 until stepCount) {
            // Clear state first
            sessionRunner.cancelEliteSession()

            // Open specific step
            sessionRunner.openEliteStep(index = step)

            // Verify step index is set
            assertEquals("Step index should be $step",
                step, stateAccess.uiState.value.elite.eliteStepIndex)

            // Verify elite is active
            assertTrue("Elite should be active at step $step",
                stateAccess.uiState.value.elite.eliteActive)

            // Verify cards are loaded
            val cards = sessionRunner.getSessionCards()
            assertTrue("Should have cards loaded at step $step",
                cards.isNotEmpty())
        }

        // Test out-of-bounds index (should be coerced to valid range)
        sessionRunner.cancelEliteSession()
        sessionRunner.openEliteStep(index = 999)

        // Should be coerced to last valid index
        assertEquals("Out-of-bounds step should be coerced to last index",
            stepCount - 1, stateAccess.uiState.value.elite.eliteStepIndex)
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Creates a fake Android Application context.
     * SessionRunner uses Application primarily for
     * SystemClock.elapsedRealtime() which is a static method.
     */
    private fun createFakeApplicationContext(): android.app.Application {
        return object : android.app.Application() {
            override fun getClassLoader(): ClassLoader {
                return this.javaClass.classLoader!!
            }
        }
    }
}
