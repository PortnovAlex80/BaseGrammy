package com.alexpo.grammermate.data.validation

import com.alexpo.grammermate.data.*
import org.junit.Assert.*
import org.junit.Test

class VerbDrillStateValidatorTest {
    // ── VerbDrill Progress Validation Tests ────────────────────────────────────────

    @Test
    fun `Valid verb drill combo progress should pass validation`() {
        val data = mapOf(
            "group" to "regular_are",
            "tense" to "Presente",
            "totalCards" to 50,
            "everShownCardIds" to listOf("card1", "card2"),
            "todayShownCardIds" to listOf("card1"),
            "lastDate" to "2026-05-22"
        )

        val result = VerbDrillStateValidator.validateVerbDrillComboProgress("key1", data)

        assertTrue("Valid combo progress should pass", result.isValid)
        assertEquals("regular_are", result.getDataOrDefault().group)
        assertEquals("Presente", result.getDataOrDefault().tense)
        assertEquals(50, result.getDataOrDefault().totalCards)
    }

    @Test
    fun `Verb drill combo progress without group should be invalid`() {
        val data = mapOf(
            "tense" to "Presente",
            "totalCards" to 50
        )

        val result = VerbDrillStateValidator.validateVerbDrillComboProgress("key1", data)

        assertFalse("Combo progress without group should be invalid", result.isValid)
    }

    @Test
    fun `Unfiltered verb combo progress survives validation but whitespace does not`() {
        val unfiltered = mapOf(
            "group" to "",
            "tense" to "",
            "totalCards" to 4,
            "everShownCardIds" to listOf("card1")
        )
        val valid = VerbDrillStateValidator.validateVerbDrillComboProgress("||", unfiltered)
        assertTrue("Empty filters are the persisted unfiltered selection", valid.isValid)
        assertEquals("", valid.getDataOrDefault().group)
        assertEquals("", valid.getDataOrDefault().tense)
        assertEquals(setOf("card1"), valid.getDataOrDefault().everShownCardIds)

        val corrupt = VerbDrillStateValidator.validateVerbDrillComboProgress(
            "||", unfiltered + ("group" to "  ")
        )
        assertFalse("Whitespace is not the unfiltered sentinel", corrupt.isValid)
    }

    @Test
    fun `Null verb drill combo progress should return valid default`() {
        val result = VerbDrillStateValidator.validateVerbDrillComboProgress("key1", null)

        assertTrue("Null combo progress should return valid default", result.isValid)
        assertEquals("unknown", result.getDataOrDefault().group)
        assertEquals("unknown", result.getDataOrDefault().tense)
    }

    @Test
    fun `Valid verb drill last session should pass validation`() {
        val data = mapOf(
            "selectedTense" to "Presente",
            "selectedGroup" to "regular_are",
            "sortByFrequency" to true,
            "todayShownCardIds" to listOf("card1", "card2"),
            "sessionCardIds" to listOf("card1", "card2", "card3"),
            "currentIndex" to 1,
            "packId" to "ru-en-v1"
        )

        val result = VerbDrillStateValidator.validateVerbDrillLastSession(data)

        assertTrue("Valid last session should pass", result.isValid)
        assertNotNull("Last session should not be null", result.getDataOrDefault())
        assertEquals("Presente", result.getDataOrDefault()?.selectedTense)
        assertEquals(1, result.getDataOrDefault()?.currentIndex)
    }

    @Test
    fun `Verb drill last session with duplicate card IDs should generate warning`() {
        val data = mapOf(
            "sortByFrequency" to false,
            "sessionCardIds" to listOf("card1", "card2", "card1"), // Duplicate
            "currentIndex" to 0
        )

        val result = VerbDrillStateValidator.validateVerbDrillLastSession(data)

        assertTrue("Duplicate cards should generate warning", result.isValid)
        if (result is ValidationResult.Warning) {
            assertTrue("Should have duplicate card warning",
                result.warnings.any { it.field == "sessionCardIds" })
        }
    }

    @Test
    fun `Verb drill last session currentIndex out of range should be clamped`() {
        val data = mapOf(
            "sortByFrequency" to false,
            "sessionCardIds" to listOf("card1", "card2"),
            "currentIndex" to 999 // Way out of range
        )

        val result = VerbDrillStateValidator.validateVerbDrillLastSession(data)

        assertTrue("Should clamp currentIndex to valid range", result.isValid)
        assertEquals(2, result.getDataOrDefault()?.currentIndex) // Clamped to size
    }

    @Test
    fun `Null verb drill last session should return valid null`() {
        val result = VerbDrillStateValidator.validateVerbDrillLastSession(null)

        assertTrue("Null last session should return valid null", result.isValid)
        assertNull("Last session should be null", result.getDataOrDefault())
    }
}
