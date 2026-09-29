package com.alexpo.grammermate.data.validation

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class MasteryStateValidationTest {
    // ── Mastery Data Validation Tests ───────────────────────────────────────────────

    @Test
    fun `Valid mastery data should pass validation`() {
        val data = mapOf(
            "uniqueCardShows" to 10,
            "totalCardShows" to 15,
            "lastShowDateMs" to System.currentTimeMillis(),
            "intervalStepIndex" to 3,
            "completedAtMs" to null,
            "shownCardIds" to listOf("card1", "card2"),
            "cardEncounterCounts" to mapOf("card1" to 2)
        )

        @Suppress("UNCHECKED_CAST")
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", data as Map<String, Any>?)

        assertTrue("Valid mastery data should be valid", result.isValid)
        assertEquals(10, result.getDataOrDefault().uniqueCardShows)
        assertEquals(15, result.getDataOrDefault().totalCardShows)
    }

    @Test
    fun `Mastery data with negative uniqueCardShows should be invalid`() {
        val data = mapOf(
            "uniqueCardShows" to -5,
            "totalCardShows" to 10
        )

        @Suppress("UNCHECKED_CAST")
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", data as Map<String, Any>?)

        assertFalse("Negative uniqueCardShows should be invalid", result.isValid)
        assertTrue("Should use safe default", result.getDataOrDefault().uniqueCardShows >= 0)
    }

    @Test
    fun `Mastery data with totalCardShows less than uniqueCardShows should be invalid`() {
        val data = mapOf(
            "uniqueCardShows" to 20,
            "totalCardShows" to 10
        )

        @Suppress("UNCHECKED_CAST")
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", data as Map<String, Any>?)

        assertFalse("totalCardShows < uniqueCardShows should be invalid", result.isValid)
    }

    @Test
    fun `Mastery data with future timestamp should generate warning`() {
        val futureTimestamp = System.currentTimeMillis() + 86400000L * 2 // +2 days
        val data = mapOf(
            "uniqueCardShows" to 10,
            "totalCardShows" to 15,
            "lastShowDateMs" to futureTimestamp
        )

        @Suppress("UNCHECKED_CAST")
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", data as Map<String, Any>?)

        assertTrue("Future timestamp should generate warning, not error", result.isValid)
        if (result is ValidationResult.Warning) {
            assertTrue("Should have warning about future timestamp",
                result.warnings.any { it.field == "lastShowDateMs" })
        }
    }

    @Test
    fun `Mastery data with out-of-range intervalStepIndex should be invalid`() {
        val data = mapOf(
            "uniqueCardShows" to 10,
            "totalCardShows" to 15,
            "intervalStepIndex" to 999 // Way beyond MAX_MASTERY_STEP
        )

        @Suppress("UNCHECKED_CAST")
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", data as Map<String, Any>?)

        assertFalse("Out-of-range intervalStepIndex should be invalid", result.isValid)
    }

    @Test
    fun `Null mastery data should return valid default`() {
        val result = MasteryStateValidator.validateMasteryState("lesson1", "en", null)

        assertTrue("Null data should return valid default", result.isValid)
        assertEquals("Default should have zero unique shows", 0, result.getDataOrDefault().uniqueCardShows)
    }
}
