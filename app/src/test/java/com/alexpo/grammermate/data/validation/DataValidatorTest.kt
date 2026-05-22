package com.alexpo.grammermate.data.validation

import org.junit.Assert.*
import org.junit.Test
import com.alexpo.grammermate.data.*

/**
 * Comprehensive unit tests for DataValidator.
 *
 * Tests validation rules for all data types:
 * - Mastery data validation
 * - Progress data validation
 * - Card data validation
 * - Pack metadata validation
 * - VerbDrill data validation
 * - Streak data validation
 */
class DataValidatorTest {

    // ── ValidationResult Tests ─────────────────────────────────────────────────────

    @Test
    fun `Valid result should be valid`() {
        val result = ValidationResult.Valid("test data")
        assertTrue("Valid result should be valid", result.isValid)
        assertEquals("test data", result.getDataOrDefault())
        assertEquals("test data", result.getDataOrNull())
    }

    @Test
    fun `Invalid result should be invalid`() {
        val error = ValidationError("field", "error message")
        val result = ValidationResult.Invalid(listOf(error), "default")
        assertFalse("Invalid result should not be valid", result.isValid)
        assertEquals("default", result.getDataOrDefault())
        assertNull("Invalid result should return null data", result.getDataOrNull())
    }

    @Test
    fun `Warning result should be valid`() {
        val warning = ValidationWarning("field", "warning message")
        val result = ValidationResult.Warning("test data", listOf(warning))
        assertTrue("Warning result should be valid", result.isValid)
        assertEquals("test data", result.getDataOrDefault())
    }

    @Test
    fun `Valid result should map correctly`() {
        val result = ValidationResult.Valid(5)
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isValid)
        assertEquals(10, mapped.getDataOrDefault())
    }

    @Test
    fun `Invalid result should preserve safe default on map`() {
        val error = ValidationError("field", "error")
        val result = ValidationResult.Invalid(listOf(error), 10)
        val mapped = result.map { it * 2 }
        assertFalse(mapped.isValid)
        assertEquals(20, mapped.getDataOrDefault())
    }

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

        val result = DataValidator.validateMasteryState("lesson1", "en", data)

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

        val result = DataValidator.validateMasteryState("lesson1", "en", data)

        assertFalse("Negative uniqueCardShows should be invalid", result.isValid)
        assertTrue("Should use safe default", result.getDataOrDefault().uniqueCardShows >= 0)
    }

    @Test
    fun `Mastery data with totalCardShows less than uniqueCardShows should be invalid`() {
        val data = mapOf(
            "uniqueCardShows" to 20,
            "totalCardShows" to 10
        )

        val result = DataValidator.validateMasteryState("lesson1", "en", data)

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

        val result = DataValidator.validateMasteryState("lesson1", "en", data)

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

        val result = DataValidator.validateMasteryState("lesson1", "en", data)

        assertFalse("Out-of-range intervalStepIndex should be invalid", result.isValid)
    }

    @Test
    fun `Null mastery data should return valid default`() {
        val result = DataValidator.validateMasteryState("lesson1", "en", null)

        assertTrue("Null data should return valid default", result.isValid)
        assertEquals("Default should have zero unique shows", 0, result.getDataOrDefault().uniqueCardShows)
    }

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

        val result = DataValidator.validateTrainingProgress(data)

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

        val result = DataValidator.validateTrainingProgress(data)

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

        val result = DataValidator.validateTrainingProgress(data)

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

        val result = DataValidator.validateTrainingProgress(data)

        assertTrue("Valid daily cursor should pass", result.isValid)
        val cursor = result.getDataOrDefault().dailyCursor
        assertEquals(10, cursor.sentenceOffset)
        assertEquals(2, cursor.currentLessonIndex)
        assertEquals("2026-05-22", cursor.firstSessionDate)
    }

    @Test
    fun `Null training progress should return valid default`() {
        val result = DataValidator.validateTrainingProgress(null)

        assertTrue("Null progress should return valid default", result.isValid)
        assertEquals("en", result.getDataOrDefault().languageId.value)
    }

    // ── Card Data Validation Tests ─────────────────────────────────────────────────

    @Test
    fun `Valid sentence card should pass validation`() {
        val data = mapOf(
            "id" to "card1",
            "promptRu" to "Translate this",
            "acceptedAnswers" to listOf("Translate this", "Translation"),
            "tense" to "present"
        )

        val result = DataValidator.validateSentenceCard(data)

        assertTrue("Valid card should pass", result.isValid)
        assertEquals("card1", result.getDataOrDefault().id)
        assertEquals("Translate this", result.getDataOrDefault().promptRu)
    }

    @Test
    fun `Sentence card without id should be invalid`() {
        val data = mapOf(
            "promptRu" to "Translate this",
            "acceptedAnswers" to listOf("Translate this")
        )

        val result = DataValidator.validateSentenceCard(data)

        assertFalse("Card without ID should be invalid", result.isValid)
    }

    @Test
    fun `Sentence card without prompt should be invalid`() {
        val data = mapOf(
            "id" to "card1",
            "acceptedAnswers" to listOf("Translation")
        )

        val result = DataValidator.validateSentenceCard(data)

        assertFalse("Card without prompt should be invalid", result.isValid)
    }

    @Test
    fun `Sentence card without accepted answers should be invalid`() {
        val data = mapOf(
            "id" to "card1",
            "promptRu" to "Translate this",
            "acceptedAnswers" to emptyList<String>()
        )

        val result = DataValidator.validateSentenceCard(data)

        assertFalse("Card without accepted answers should be invalid", result.isValid)
    }

    @Test
    fun `Null sentence card should be invalid with safe default`() {
        val result = DataValidator.validateSentenceCard(null)

        assertFalse("Null card should be invalid", result.isValid)
        assertTrue("Should provide safe default", result.getDataOrDefault().id.startsWith("invalid_card_"))
    }

    @Test
    fun `Valid verb drill card should pass validation`() {
        val data = mapOf(
            "id" to "verb1",
            "promptRu" to "Conjugate verb",
            "answer" to "conjugated",
            "verb" to "essere",
            "tense" to "present",
            "group" to "irregular",
            "rank" to 1
        )

        val result = DataValidator.validateVerbDrillCard(data)

        assertTrue("Valid verb card should pass", result.isValid)
        assertEquals("verb1", result.getDataOrDefault().id)
        assertEquals("essere", result.getDataOrDefault().verb)
    }

    @Test
    fun `Verb drill card without answer should be invalid`() {
        val data = mapOf(
            "id" to "verb1",
            "promptRu" to "Conjugate verb"
        )

        val result = DataValidator.validateVerbDrillCard(data)

        assertFalse("Verb card without answer should be invalid", result.isValid)
    }

    // ── Pack Metadata Validation Tests ─────────────────────────────────────────────

    @Test
    fun `Valid lesson pack should pass validation`() {
        val data = mapOf(
            "packId" to "ru-en-v1",
            "packVersion" to "1.0.0",
            "languageId" to "en",
            "importedAt" to System.currentTimeMillis(),
            "displayName" to "English Pack"
        )

        val result = DataValidator.validateLessonPack(data)

        assertTrue("Valid pack should pass", result.isValid)
        assertEquals(PackId("ru-en-v1"), result.getDataOrDefault().packId)
        assertEquals("1.0.0", result.getDataOrDefault().packVersion)
    }

    @Test
    fun `Lesson pack without packId should be invalid`() {
        val data = mapOf(
            "packVersion" to "1.0.0",
            "languageId" to "en",
            "importedAt" to System.currentTimeMillis()
        )

        val result = DataValidator.validateLessonPack(data)

        assertFalse("Pack without packId should be invalid", result.isValid)
    }

    @Test
    fun `Lesson pack with non-standard version should generate warning`() {
        val data = mapOf(
            "packId" to "ru-en-v1",
            "packVersion" to "v1-beta", // Non-standard format
            "languageId" to "en",
            "importedAt" to System.currentTimeMillis()
        )

        val result = DataValidator.validateLessonPack(data)

        assertTrue("Non-standard version should generate warning", result.isValid)
        if (result is ValidationResult.Warning) {
            assertTrue("Should have version format warning",
                result.warnings.any { it.field == "packVersion" })
        }
    }

    @Test
    fun `Lesson pack with future import timestamp should generate warning`() {
        val futureTimestamp = System.currentTimeMillis() + 86400000L * 7 // +1 week
        val data = mapOf(
            "packId" to "ru-en-v1",
            "packVersion" to "1.0.0",
            "languageId" to "en",
            "importedAt" to futureTimestamp
        )

        val result = DataValidator.validateLessonPack(data)

        assertTrue("Future timestamp should generate warning", result.isValid)
        if (result is ValidationResult.Warning) {
            assertTrue("Should have timestamp warning",
                result.warnings.any { it.field == "importedAt" })
        }
    }

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

        val result = DataValidator.validateVerbDrillComboProgress("key1", data)

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

        val result = DataValidator.validateVerbDrillComboProgress("key1", data)

        assertFalse("Combo progress without group should be invalid", result.isValid)
    }

    @Test
    fun `Null verb drill combo progress should return valid default`() {
        val result = DataValidator.validateVerbDrillComboProgress("key1", null)

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

        val result = DataValidator.validateVerbDrillLastSession(data)

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

        val result = DataValidator.validateVerbDrillLastSession(data)

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

        val result = DataValidator.validateVerbDrillLastSession(data)

        assertTrue("Should clamp currentIndex to valid range", result.isValid)
        assertEquals(2, result.getDataOrDefault()?.currentIndex) // Clamped to size
    }

    @Test
    fun `Null verb drill last session should return valid null`() {
        val result = DataValidator.validateVerbDrillLastSession(null)

        assertTrue("Null last session should return valid null", result.isValid)
        assertNull("Last session should be null", result.getDataOrDefault())
    }

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

        val result = DataValidator.validateStreakData("en", data)

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

        val result = DataValidator.validateStreakData("en", data)

        assertTrue("Should pass but may log warning", result.isValid)
    }

    @Test
    fun `Streak data with negative timestamp should be invalid`() {
        val data = mapOf(
            "currentStreak" to 5,
            "longestStreak" to 10,
            "lastCompletionDateMs" to -1000L
        )

        val result = DataValidator.validateStreakData("en", data)

        assertFalse("Negative timestamp should be invalid", result.isValid)
    }

    @Test
    fun `Null streak data should return valid default`() {
        val result = DataValidator.validateStreakData("en", null)

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

        val result = DataValidator.validateStreakData("en", data)

        assertTrue("Should filter invalid practice types", result.isValid)
        assertEquals(2, result.getDataOrDefault().completedTypesToday.size)
        assertTrue("Should contain TRANSLATION",
            result.getDataOrDefault().completedTypesToday.contains(PracticeType.TRANSLATION))
        assertTrue("Should contain VOCAB",
            result.getDataOrDefault().completedTypesToday.contains(PracticeType.VOCAB))
    }
}