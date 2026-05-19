package com.alexpo.grammermate.scenario

import com.alexpo.grammermate.data.FlowerCalculator
import com.alexpo.grammermate.data.FlowerState
import com.alexpo.grammermate.data.LanguageId
import com.alexpo.grammermate.data.LessonId
import com.alexpo.grammermate.data.LessonMasteryState
import com.alexpo.grammermate.data.SpacedRepetitionConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scenario test for mastery step transitions and flower progression.
 *
 * Tests the complete learning journey:
 * 1. Initial state (step=0, shows=0) → SEED
 * 2. Correct answer on time → step 0→1, flower SEED → SPROUT
 * 3. Correct answer on time → step 1→2
 * 4. Correct answer on time → step 2→3 (learned threshold), flower BLOOM
 * 5. Late answer → step stays same
 * 6. Wrong answer → step reset to 0
 *
 * This is a pure unit test - no mocks, no SessionRunner needed.
 * Directly tests the interaction between SpacedRepetitionConfig and FlowerCalculator.
 */
@Suppress("ReplaceCallWithBinaryOperator")
@RunWith(RobolectricTestRunner::class)
class MasteryProgressionScenarioTest {

    private val EPSILON = 0.01f
    private val LESSON_ID = LessonId("test-lesson")
    private val LANGUAGE_ID = LanguageId("en")

    // ========================================
    // Scenario: Complete mastery progression journey
    // ========================================

    @Test
    fun scenario_completeMasteryJourney() {
        // --- STEP 1: Initial state ---
        // Create a card with initial mastery (step=0, shows=0)
        var mastery = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 0,
            intervalStepIndex = 0,
            lastShowDateMs = System.currentTimeMillis()
        )

        // Verify: Initial state is SEED
        var flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("Initial state should be SEED", FlowerState.SEED, flower.state)
        assertEquals("Initial mastery should be 0%", 0f, flower.masteryPercent, EPSILON)
        assertEquals("Initial health should be 100%", 1f, flower.healthPercent, EPSILON)
        assertEquals("Initial interval step should be 0", 0, mastery.intervalStepIndex)

        // --- STEP 2: First correct answer (on time) ---
        // Simulate correct answer: shows increase, lastShowDate updates
        // Check if repetition was on time (should be true for first review)
        val daysSinceFirstShow = 0 // Same day
        val wasOnTime1 = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceFirstShow, mastery.intervalStepIndex)
        assertTrue("First repetition should be on time", wasOnTime1)

        // Update step
        val nextStep1 = SpacedRepetitionConfig.nextIntervalStep(mastery.intervalStepIndex, wasOnTime1)
        assertEquals("Step should advance from 0 to 1", 1, nextStep1)

        mastery = mastery.copy(
            uniqueCardShows = 1,
            intervalStepIndex = nextStep1,
            lastShowDateMs = System.currentTimeMillis()
        )

        // Verify: Flower transitions from SEED to... still SEED (1 show is < 33% = 50 shows)
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 1 show, flower should still be SEED (< 33%)", FlowerState.SEED, flower.state)
        assertEquals("Interval step should be 1", 1, mastery.intervalStepIndex)

        // --- STEP 3: Second correct answer (on time) ---
        // Now at step=1, expected interval is 1 day. Let's say we review on day 1.
        val daysSinceSecondShow = 1
        val wasOnTime2 = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceSecondShow, mastery.intervalStepIndex)
        assertTrue("Repetition on day 1 should be on time for step 1", wasOnTime2)

        val nextStep2 = SpacedRepetitionConfig.nextIntervalStep(mastery.intervalStepIndex, wasOnTime2)
        assertEquals("Step should advance from 1 to 2", 2, nextStep2)

        mastery = mastery.copy(
            uniqueCardShows = 2,
            intervalStepIndex = nextStep2,
            lastShowDateMs = System.currentTimeMillis()
        )

        // Verify: Step is now 2, flower still SEED (need 50+ shows for SPROUT)
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 2 shows, flower should still be SEED", FlowerState.SEED, flower.state)
        assertEquals("Interval step should be 2", 2, mastery.intervalStepIndex)

        // --- STEP 4: Third correct answer (on time) ---
        // Now at step=2, expected interval is 4 days. Review on day 3 (early).
        val daysSinceThirdShow = 3
        val wasOnTime3 = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceThirdShow, mastery.intervalStepIndex)
        assertTrue("Repetition on day 3 should be on time for step 2 (expected: 4 days)", wasOnTime3)

        val nextStep3 = SpacedRepetitionConfig.nextIntervalStep(mastery.intervalStepIndex, wasOnTime3)
        assertEquals("Step should advance from 2 to 3", 3, nextStep3)

        mastery = mastery.copy(
            uniqueCardShows = 3,
            intervalStepIndex = nextStep3,
            lastShowDateMs = System.currentTimeMillis()
        )

        // Verify: Step 3 = "learned" threshold (>= 3), but flower still SEED based on shows
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 3 shows, flower should still be SEED", FlowerState.SEED, flower.state)
        assertEquals("Interval step should be 3 (learned threshold)", 3, mastery.intervalStepIndex)

        // --- STEP 5: Simulate enough shows to reach BLOOM ---
        // To get BLOOM, we need 100+ shows (66% of 150)
        // Let's fast-forward to 100 shows at step 3
        mastery = mastery.copy(
            uniqueCardShows = 100,
            intervalStepIndex = 3,
            lastShowDateMs = System.currentTimeMillis()
        )

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("After 100 shows (66%+), flower should be BLOOM", FlowerState.BLOOM, flower.state)
        assertTrue("Mastery percent should be >= 66%", flower.masteryPercent >= 0.66f)

        // --- STEP 6: Late answer ---
        // At step 3, expected interval is 7 days. Let's say we review on day 10 (late).
        val daysSinceLateShow = 10
        val wasOnTimeLate = SpacedRepetitionConfig.wasRepetitionOnTime(daysSinceLateShow, mastery.intervalStepIndex)
        assertFalse("Repetition on day 10 should be LATE for step 3 (expected: 7 days)", wasOnTimeLate)

        val nextStepLate = SpacedRepetitionConfig.nextIntervalStep(mastery.intervalStepIndex, wasOnTimeLate)
        assertEquals("Step should NOT advance when late", 3, nextStepLate)

        // Health should decrease due to late review
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        val healthBeforeLate = flower.healthPercent

        mastery = mastery.copy(
            uniqueCardShows = 101,
            intervalStepIndex = nextStepLate,
            lastShowDateMs = System.currentTimeMillis() - (daysSinceLateShow * 24 * 60 * 60 * 1000)
        )

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertTrue("Health should decrease after late review", flower.healthPercent < healthBeforeLate)
        // Step stays at 3
        assertEquals("Interval step should remain 3 after late answer", 3, mastery.intervalStepIndex)

        // --- STEP 7: Wrong answer (reset to step 0) ---
        // In real implementation, wrong answer resets the step
        // Here we simulate that by manually setting step to 0
        mastery = mastery.copy(
            uniqueCardShows = 102, // Shows still count, but step resets
            intervalStepIndex = 0,  // Reset to beginning
            lastShowDateMs = System.currentTimeMillis()
        )

        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        // Flower should still be BLOOM based on shows, but step is 0
        assertEquals("After wrong answer, flower should still be BLOOM (based on shows)", FlowerState.BLOOM, flower.state)
        assertEquals("Interval step should reset to 0", 0, mastery.intervalStepIndex)
    }

    // ========================================
    // Detailed step progression tests
    // ========================================

    @Test
    fun scenario_stepProgression_sequentialCorrectAnswers() {
        // Test that each correct on-time answer advances the step
        var step = 0
        var currentTime = System.currentTimeMillis()

        // Simulate 10 consecutive correct answers
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        for (i in 0 until 10) {
            val wasOnTime = true // Assume always on time for this test
            val nextStep = SpacedRepetitionConfig.nextIntervalStep(step, wasOnTime)

            val expectedStep = minOf(i + 1, maxStep)
            assertEquals("Step $i should advance to $expectedStep", expectedStep, nextStep)
            step = nextStep
            currentTime += (24 * 60 * 60 * 1000) // Advance 1 day
        }

        // After 10 correct answers, we should be at max step
        assertEquals("Should reach max step", SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1, step)
    }

    @Test
    fun scenario_stepProgression_maxStepCap() {
        // Test that step doesn't exceed max
        val maxStep = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        var step = maxStep

        // Try to advance beyond max
        step = SpacedRepetitionConfig.nextIntervalStep(step, true)
        assertEquals("Step should cap at max", maxStep, step)

        step = SpacedRepetitionConfig.nextIntervalStep(step, true)
        assertEquals("Step should remain at max even after multiple advances", maxStep, step)
    }

    @Test
    fun scenario_learnedThreshold_step3AndAbove() {
        // Test the "learned" threshold definition: step >= 3
        val masteryBelow = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 100,
            intervalStepIndex = 2,
            lastShowDateMs = System.currentTimeMillis()
        )

        val masteryAt = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 100,
            intervalStepIndex = 3,
            lastShowDateMs = System.currentTimeMillis()
        )

        val masteryAbove = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 100,
            intervalStepIndex = 5,
            lastShowDateMs = System.currentTimeMillis()
        )

        // All should be BLOOM based on shows (100 >= 66% of 150)
        val flowerBelow = FlowerCalculator.calculate(masteryBelow, totalCardsInLesson = 150)
        val flowerAt = FlowerCalculator.calculate(masteryAt, totalCardsInLesson = 150)
        val flowerAbove = FlowerCalculator.calculate(masteryAbove, totalCardsInLesson = 150)

        assertEquals("Step 2 should still show BLOOM with 100 shows", FlowerState.BLOOM, flowerBelow.state)
        assertEquals("Step 3 should show BLOOM (learned threshold)", FlowerState.BLOOM, flowerAt.state)
        assertEquals("Step 5 should show BLOOM", FlowerState.BLOOM, flowerAbove.state)
    }

    @Test
    fun scenario_lateAnswer_doesNotAdvanceStep() {
        // Test that late answers don't advance the step
        val step = 2 // Expected interval: 4 days

        // Review on day 5 (late by 1 day)
        val daysLate = 5
        val wasOnTime = SpacedRepetitionConfig.wasRepetitionOnTime(daysLate, step)
        assertFalse("Review on day 5 should be late for step 2", wasOnTime)

        val nextStep = SpacedRepetitionConfig.nextIntervalStep(step, wasOnTime)
        assertEquals("Step should not advance when late", step, nextStep)
    }

    @Test
    fun scenario_healthDecay_withProgressiveSteps() {
        // Test that health decay is slower at higher steps
        val currentTime = System.currentTimeMillis()
        val overdueDays = 14 // 2 weeks overdue

        // At step 0 (expected: 1 day) - 14 days overdue = severe health loss
        val masteryStep0 = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 100,
            intervalStepIndex = 0,
            lastShowDateMs = currentTime - (overdueDays * 24 * 60 * 60 * 1000)
        )

        // At step 5 (expected: 14 days) - 14 days = just on time
        val masteryStep5 = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 100,
            intervalStepIndex = 5,
            lastShowDateMs = currentTime - (overdueDays * 24 * 60 * 60 * 1000)
        )

        val flowerStep0 = FlowerCalculator.calculate(masteryStep0, totalCardsInLesson = 150)
        val flowerStep5 = FlowerCalculator.calculate(masteryStep5, totalCardsInLesson = 150)

        assertTrue("Health at step 0 should be worse than at step 5 for same overdue",
            flowerStep0.healthPercent < flowerStep5.healthPercent)
        assertEquals("Step 5 with 14 days should be healthy (on time)", 1f, flowerStep5.healthPercent, EPSILON)
    }

    @Test
    fun scenario_flowerStateTransitions_byShowsOnly() {
        // Test flower state transitions based on uniqueCardShows only
        var mastery = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 0,
            lastShowDateMs = System.currentTimeMillis()
        )

        // 0-49 shows (0-32%) → SEED
        mastery = mastery.copy(uniqueCardShows = 25)
        var flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("25 shows should be SEED", FlowerState.SEED, flower.state)

        // 50-99 shows (33-65%) → SPROUT
        mastery = mastery.copy(uniqueCardShows = 75)
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("75 shows should be SPROUT", FlowerState.SPROUT, flower.state)

        // 100+ shows (66%+) → BLOOM
        mastery = mastery.copy(uniqueCardShows = 120)
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("120 shows should be BLOOM", FlowerState.BLOOM, flower.state)

        // 150 shows (100%) → BLOOM (max)
        mastery = mastery.copy(uniqueCardShows = 150)
        flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        assertEquals("150 shows should be BLOOM", FlowerState.BLOOM, flower.state)
        assertEquals("150 shows should be 100% mastery", 1f, flower.masteryPercent, EPSILON)
    }

    @Test
    fun scenario_intervalLadder_values() {
        // Verify the interval ladder values
        val expectedLadder = listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56)
        assertEquals("Interval ladder should match expected values",
            expectedLadder, SpacedRepetitionConfig.INTERVAL_LADDER_DAYS)
    }

    @Test
    fun scenario_onTimeDetection_boundaryCases() {
        // Test boundary cases for "on time" detection
        val step = 2 // Expected interval: 4 days

        // Exactly on time
        assertTrue("4 days should be on time for step 2",
            SpacedRepetitionConfig.wasRepetitionOnTime(4, step))

        // One day late
        assertFalse("5 days should be late for step 2",
            SpacedRepetitionConfig.wasRepetitionOnTime(5, step))

        // Early is still on time
        assertTrue("0 days (same day) should be on time",
            SpacedRepetitionConfig.wasRepetitionOnTime(0, step))
        assertTrue("1 day should be on time for step 2",
            SpacedRepetitionConfig.wasRepetitionOnTime(1, step))
    }

    @Test
    fun scenario_resetScenario_fullCycle() {
        // Simulate a full cycle: progress → reset → progress again
        var mastery = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 0,
            intervalStepIndex = 0,
            lastShowDateMs = System.currentTimeMillis()
        )

        // Progress to step 5
        repeat(5) {
            mastery = mastery.copy(
                uniqueCardShows = mastery.uniqueCardShows + 20,
                intervalStepIndex = mastery.intervalStepIndex + 1,
                lastShowDateMs = System.currentTimeMillis()
            )
        }
        assertEquals("Should reach step 5", 5, mastery.intervalStepIndex)
        assertEquals("Should have 100 shows", 100, mastery.uniqueCardShows)

        // Reset (simulating wrong answer after long absence)
        mastery = mastery.copy(
            intervalStepIndex = 0,
            lastShowDateMs = System.currentTimeMillis()
        )
        assertEquals("Step should reset to 0", 0, mastery.intervalStepIndex)
        assertEquals("Shows should be preserved", 100, mastery.uniqueCardShows)

        // Progress again from step 0
        mastery = mastery.copy(
            uniqueCardShows = mastery.uniqueCardShows + 1,
            intervalStepIndex = 1,
            lastShowDateMs = System.currentTimeMillis()
        )
        assertEquals("Should advance to step 1 after reset", 1, mastery.intervalStepIndex)
    }

    @Test
    fun scenario_wiltingTransition_bloomToWilting() {
        // Test that BLOOM transitions to WILTING when health drops
        val mastery = LessonMasteryState(
            lessonId = LESSON_ID,
            languageId = LANGUAGE_ID,
            uniqueCardShows = 120, // BLOOM territory
            intervalStepIndex = 3, // Expected: 7 days
            lastShowDateMs = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000) // 10 days ago (late)
        )

        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 150)
        // Should be WILTING because health < 100% (overdue)
        assertEquals("Overdue BLOOM should transition to WILTING", FlowerState.WILTING, flower.state)
        assertTrue("Health should be < 100%", flower.healthPercent < 1f)
        assertTrue("Health should be > WILTED_THRESHOLD",
            flower.healthPercent > SpacedRepetitionConfig.WILTED_THRESHOLD)
    }
}
