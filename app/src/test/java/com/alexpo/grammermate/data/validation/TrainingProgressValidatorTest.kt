package com.alexpo.grammermate.data.validation

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class TrainingProgressValidatorTest {
    // ── Progress Data Validation Tests ──────────────────────────────────────────────

    @Test
    fun `Valid training progress should pass validation`() {
        val data = mapOf(
            "languageId" to "en",
            "mode" to "LESSON",
            "bossLessonRewards" to mapOf("lesson1" to "BRONZE"),
            "voiceActiveMs" to 1000L,
            "voiceWordCount" to 5,
            "hintCount" to 2,
            "eliteStepIndex" to 1,
            "currentScreen" to "HOME",
            "dailyLevel" to 3,
            "dailyTaskIndex" to 1
        )

        val result = TrainingProgressValidator.validateTrainingProgress(data)

        assertTrue("Valid progress should be valid", result.isValid)
        assertEquals("en", result.getDataOrDefault().languageId.value)
        assertEquals(TrainingMode.LESSON, result.getDataOrDefault().mode)
    }

    @Test
    fun `Training progress with invalid mode should use LESSON as default`() {
        val data = mapOf(
            "languageId" to "en",
            "mode" to "INVALID_MODE"
        )

        val result = TrainingProgressValidator.validateTrainingProgress(data)

        assertTrue("Should use LESSON as default mode", result.isValid)
        assertEquals(TrainingMode.LESSON, result.getDataOrDefault().mode)
    }

    @Test
    fun `Training progress with negative voice stats should be coerced to zero`() {
        val data = mapOf(
            "languageId" to "en",
            "voiceActiveMs" to -100L,
            "voiceWordCount" to -5
        )

        val result = TrainingProgressValidator.validateTrainingProgress(data)

        assertTrue("Should coerce negative values to zero", result.isValid)
        assertEquals(0L, result.getDataOrDefault().voiceActiveMs)
        assertEquals(0, result.getDataOrDefault().voiceWordCount)
    }

    @Test
    fun `Training progress with daily cursor should validate cursor`() {
        val data = mapOf(
            "languageId" to "en",
            "dailyCursor" to mapOf(
                "sentenceOffset" to 10,
                "currentLessonIndex" to 2,
                "lastSessionHash" to 12345,
                "firstSessionDate" to "2026-05-22",
                "firstSessionSentenceCardIds" to listOf("card1", "card2")
            )
        )

        val result = TrainingProgressValidator.validateTrainingProgress(data)

        assertTrue("Valid daily cursor should pass", result.isValid)
        val cursor = result.getDataOrDefault().dailyCursor
        assertEquals(10, cursor.sentenceOffset)
        assertEquals(2, cursor.currentLessonIndex)
        assertEquals("2026-05-22", cursor.firstSessionDate)
    }

    @Test
    fun `Null training progress should return valid default`() {
        val result = TrainingProgressValidator.validateTrainingProgress(null)

        assertTrue("Null progress should return valid default", result.isValid)
        assertEquals("en", result.getDataOrDefault().languageId.value)
    }
}
