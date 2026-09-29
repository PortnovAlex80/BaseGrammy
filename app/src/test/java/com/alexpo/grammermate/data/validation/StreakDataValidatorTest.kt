package com.alexpo.grammermate.data.validation

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class StreakDataValidatorTest {
    // ── Streak Data Validation Tests ────────────────────────────────────────────────

    @Test
    fun `Valid streak data should pass validation`() {
        val data = mapOf(
            "currentStreak" to 5,
            "longestStreak" to 10,
            "lastCompletionDateMs" to System.currentTimeMillis(),
            "totalSubLessonsCompleted" to 50,
            "completedTypesToday" to listOf("TRANSLATION", "VOCAB"),
            "todayFireCount" to 2,
            "lastFireDateMs" to System.currentTimeMillis()
        )

        val result = StreakDataValidator.validateStreakData("en", data)

        assertTrue("Valid streak data should pass", result.isValid)
        assertEquals(5, result.getDataOrDefault().currentStreak)
        assertEquals(10, result.getDataOrDefault().longestStreak)
        assertEquals(2, result.getDataOrDefault().todayFireCount)
    }

    @Test
    fun `Streak data with currentStreak greater than longestStreak should generate warning`() {
        val data = mapOf(
            "currentStreak" to 15,
            "longestStreak" to 10,
            "lastCompletionDateMs" to System.currentTimeMillis()
        )

        val result = StreakDataValidator.validateStreakData("en", data)

        assertTrue("Should pass but may log warning", result.isValid)
    }

    @Test
    fun `Streak data with negative timestamp should be invalid`() {
        val data = mapOf(
            "currentStreak" to 5,
            "longestStreak" to 10,
            "lastCompletionDateMs" to -1000L
        )

        val result = StreakDataValidator.validateStreakData("en", data)

        assertFalse("Negative timestamp should be invalid", result.isValid)
    }

    @Test
    fun `Null streak data should return valid default`() {
        val result = StreakDataValidator.validateStreakData("en", null)

        assertTrue("Null streak data should return valid default", result.isValid)
        assertEquals("en", result.getDataOrDefault().languageId.value)
        assertEquals(0, result.getDataOrDefault().currentStreak)
    }

    @Test
    fun `Streak data with invalid practice type should filter it out`() {
        val data = mapOf(
            "currentStreak" to 5,
            "longestStreak" to 10,
            "completedTypesToday" to listOf("TRANSLATION", "INVALID_TYPE", "VOCAB")
        )

        val result = StreakDataValidator.validateStreakData("en", data)

        assertTrue("Should filter invalid practice types", result.isValid)
        assertEquals(2, result.getDataOrDefault().completedTypesToday.size)
        assertTrue("Should contain TRANSLATION",
            result.getDataOrDefault().completedTypesToday.contains(PracticeType.TRANSLATION))
        assertTrue("Should contain VOCAB",
            result.getDataOrDefault().completedTypesToday.contains(PracticeType.VOCAB))
    }
}
