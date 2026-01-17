package com.alexpo.grammermate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonLadderCalculatorTest {
    private val nowMs = 10L * 24 * 60 * 60 * 1000L

    @Test
    fun calculate_nullMastery_returnsEmptyMetrics() {
        val metrics = LessonLadderCalculator.calculate(null, nowMs)
        assertNull(metrics.uniqueCardShows)
        assertNull(metrics.daysSinceLastShow)
        assertNull(metrics.intervalLabel)
    }

    @Test
    fun calculate_missingLastShow_returnsEmptyMetrics() {
        val mastery = LessonMasteryState(
            lessonId = "l1",
            languageId = "en",
            uniqueCardShows = 3,
            lastShowDateMs = 0L,
            intervalStepIndex = 1
        )
        val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
        assertNull(metrics.uniqueCardShows)
        assertNull(metrics.daysSinceLastShow)
        assertNull(metrics.intervalLabel)
    }

    @Test
    fun calculate_daysSinceStartsAtOne() {
        val mastery = LessonMasteryState(
            lessonId = "l1",
            languageId = "en",
            uniqueCardShows = 5,
            lastShowDateMs = nowMs,
            intervalStepIndex = 0
        )
        val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
        assertEquals(1, metrics.daysSinceLastShow)
        assertEquals("1-2", metrics.intervalLabel)
    }

    @Test
    fun calculate_intervalBetweenSteps() {
        val mastery = LessonMasteryState(
            lessonId = "l1",
            languageId = "en",
            uniqueCardShows = 12,
            lastShowDateMs = nowMs - 4L * 24 * 60 * 60 * 1000L,
            intervalStepIndex = 3
        )
        val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
        assertEquals(5, metrics.daysSinceLastShow)
        assertEquals("4-7", metrics.intervalLabel)
    }

    @Test
    fun calculate_overdueUsesExpectedStep() {
        val mastery = LessonMasteryState(
            lessonId = "l1",
            languageId = "en",
            uniqueCardShows = 20,
            lastShowDateMs = nowMs - 4L * 24 * 60 * 60 * 1000L,
            intervalStepIndex = 1
        )
        val metrics = LessonLadderCalculator.calculate(mastery, nowMs)
        assertEquals("Просрочка+3", metrics.intervalLabel)
    }
}
