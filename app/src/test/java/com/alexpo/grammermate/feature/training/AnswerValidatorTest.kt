package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.Normalizer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AnswerValidator].
 *
 * Pure JUnit 4 — no Android dependencies. The [Normalizer] object is injected
 * directly (it is a pure-Kotlin singleton with no Android deps).
 */
class AnswerValidatorTest {

    private lateinit var validator: AnswerValidator

    @Before
    fun setUp() {
        validator = AnswerValidator(Normalizer)
    }

    // ------------------------------------------------------------------ //
    // validate() — exact match
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_exactSingleAnswer_returnsCorrect() {
        val result = validator.validate("ciao", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_exactMatchAmongMultipleAnswers_returnsCorrect() {
        val result = validator.validate("buongiorno", listOf("ciao", "buongiorno", "salve"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_wrongAnswer_returnsIncorrect() {
        val result = validator.validate("hello", listOf("ciao", "salve"))
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_inputMatchesSecondAnswer_returnsCorrect() {
        val result = validator.validate("salve", listOf("ciao", "salve"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_inputMatchesLastAnswer_returnsCorrect() {
        val result = validator.validate("arrivederci", listOf("ciao", "salve", "arrivederci"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — multi-answer split by "+"
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_multiAnswerFirstAlternative_returnsCorrect() {
        val result = validator.validate("ciao", listOf("ciao+salve"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_multiAnswerSecondAlternative_returnsCorrect() {
        val result = validator.validate("salve", listOf("ciao+salve"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_multiAnswerWrongValue_returnsIncorrect() {
        val result = validator.validate("hello", listOf("ciao+salve"))
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_multipleEntriesEachWithPlus_returnsCorrectForAnyAlternative() {
        val accepted = listOf("ciao+salve", "buongiorno+buondi")
        assertTrue(validator.validate("ciao", accepted).isCorrect)
        assertTrue(validator.validate("salve", accepted).isCorrect)
        assertTrue(validator.validate("buongiorno", accepted).isCorrect)
        assertTrue(validator.validate("buondi", accepted).isCorrect)
        assertFalse(validator.validate("hello", accepted).isCorrect)
    }

    @Test
    fun testValidate_threeAlternativesInOneEntry_allMatch() {
        val result1 = validator.validate("ciao", listOf("ciao+salve+buongiorno"))
        val result2 = validator.validate("salve", listOf("ciao+salve+buongiorno"))
        val result3 = validator.validate("buongiorno", listOf("ciao+salve+buongiorno"))
        assertTrue(result1.isCorrect)
        assertTrue(result2.isCorrect)
        assertTrue(result3.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — case insensitive matching
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_upperCaseInput_matchesLowercaseAnswer() {
        val result = validator.validate("CIAO", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_mixedCaseInput_matchesAnswer() {
        val result = validator.validate("Ciao", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_upperCaseAnswer_matchesLowercaseInput() {
        val result = validator.validate("ciao", listOf("CIAO"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_allUpperCaseBothSides_matches() {
        val result = validator.validate("BUONGIORNO", listOf("BUONGIORNO"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — empty answer
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_emptyInput_nonEmptyAnswers_returnsIncorrect() {
        val result = validator.validate("", listOf("ciao"))
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_blankInput_spacesOnly_returnsIncorrect() {
        val result = validator.validate("   ", listOf("ciao"))
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_emptyAcceptedAnswers_nonEmptyInput_returnsIncorrect() {
        val result = validator.validate("ciao", emptyList())
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_bothEmpty_returnsCorrect() {
        // Empty normalized input will equal empty normalized accepted answer
        val result = validator.validate("", listOf(""))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — special characters, apostrophes, accents
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_inputWithAccent_matchesAnswerWithoutAccent() {
        // Normalizer strips diacritics: perché == perche
        val result = validator.validate("perche", listOf("perché"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_inputWithAccent_matchesAnswerWithoutAccent_reverse() {
        val result = validator.validate("perché", listOf("perche"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_punctuationInInput_strippedAndMatches() {
        // Normalizer strips punctuation
        val result = validator.validate("ciao!", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_punctuationInAnswer_strippedAndMatches() {
        val result = validator.validate("ciao", listOf("ciao!"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_multiplePunctuationChars_stripped() {
        val result = validator.validate("ciao!!!???", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_apostropheInAnswer_notStripped_butPunctuationIs() {
        // The Normalizer does NOT strip apostrophes (not in the punctuation list).
        // However, if the answer is "l'amico" the apostrophe remains after normalization.
        val result = validator.validate("l'amico", listOf("l'amico"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_hyphenPreserved_inInput() {
        // Hyphens are kept by Normalizer
        val result = validator.validate("e-stato", listOf("e-stato"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_extraSpacesInInput_normalized() {
        val result = validator.validate("  ciao   mondo  ", listOf("ciao mondo"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_tabAndNewlineInInput_normalized() {
        val result = validator.validate("ciao\tmondo\n", listOf("ciao mondo"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_accentInMultiAnswer_plusSeparator() {
        val result = validator.validate("perche", listOf("perché+ciao"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_commaAndPeriod_strippedFromInput() {
        val result = validator.validate("ciao, mondo.", listOf("ciao mondo"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — testMode=true vs false
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_testModeTrue_wrongAnswer_returnsCorrect() {
        val result = validator.validate("absolutely wrong", listOf("ciao"), testMode = true)
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_testModeTrue_emptyAnswer_returnsCorrect() {
        val result = validator.validate("", listOf("ciao"), testMode = true)
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_testModeTrue_emptyAcceptedAnswers_returnsCorrect() {
        val result = validator.validate("anything", emptyList(), testMode = true)
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_testModeFalse_wrongAnswer_returnsIncorrect() {
        val result = validator.validate("wrong", listOf("ciao"), testMode = false)
        assertFalse(result.isCorrect)
    }

    @Test
    fun testValidate_testModeFalse_correctAnswer_returnsCorrect() {
        val result = validator.validate("ciao", listOf("ciao"), testMode = false)
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_testModeDefault_wrongAnswer_returnsIncorrect() {
        // Default testMode is false
        val result = validator.validate("wrong", listOf("ciao"))
        assertFalse(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // validate() — ValidationResult fields
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_result_containsNormalizedInput() {
        val result = validator.validate("  CIAO!!!  ", listOf("ciao"))
        assertEquals("ciao", result.normalizedInput)
    }

    @Test
    fun testValidate_result_normalizedInputHasStrippedPunctuationAndLowercase() {
        val result = validator.validate("Perché???", listOf("perche"))
        assertEquals("perche", result.normalizedInput)
    }

    @Test
    fun testValidate_result_hintShownAlwaysFalse() {
        // validate() does not set hintShown — that is managed by shouldShowHint()
        val result = validator.validate("ciao", listOf("ciao"))
        assertFalse(result.hintShown)
    }

    @Test
    fun testValidate_result_hintTextAlwaysNull() {
        // validate() does not set hintText — that is managed by getHintText()
        val result = validator.validate("ciao", listOf("ciao"))
        assertNull(result.hintText)
    }

    @Test
    fun testValidate_wrongAnswer_resultFieldsCorrect() {
        val result = validator.validate("hello", listOf("ciao"))
        assertFalse(result.isCorrect)
        assertEquals("hello", result.normalizedInput)
        assertFalse(result.hintShown)
        assertNull(result.hintText)
    }

    // ------------------------------------------------------------------ //
    // shouldShowHint() — boundary values
    // ------------------------------------------------------------------ //

    @Test
    fun testShouldShowHint_zeroAttempts_returnsFalse() {
        assertFalse(validator.shouldShowHint(0))
    }

    @Test
    fun testShouldShowHint_oneAttempt_returnsFalse() {
        assertFalse(validator.shouldShowHint(1))
    }

    @Test
    fun testShouldShowHint_twoAttempts_returnsFalse() {
        assertFalse(validator.shouldShowHint(2))
    }

    @Test
    fun testShouldShowHint_threeAttempts_returnsTrue() {
        // HINT_THRESHOLD = 3, so 3 >= 3 is true
        assertTrue(validator.shouldShowHint(3))
    }

    @Test
    fun testShouldShowHint_fourAttempts_returnsTrue() {
        assertTrue(validator.shouldShowHint(4))
    }

    @Test
    fun testShouldShowHint_largeNumber_returnsTrue() {
        assertTrue(validator.shouldShowHint(100))
    }

    @Test
    fun testShouldShowHint_negativeAttempts_returnsFalse() {
        assertFalse(validator.shouldShowHint(-1))
    }

    // ------------------------------------------------------------------ //
    // getHintText() — hint content
    // ------------------------------------------------------------------ //

    @Test
    fun testGetHintText_singleAnswer_returnsThatAnswer() {
        assertEquals("ciao", validator.getHintText(listOf("ciao")))
    }

    @Test
    fun testGetHintText_multipleAnswers_returnsFirst() {
        assertEquals("ciao", validator.getHintText(listOf("ciao", "salve", "buongiorno")))
    }

    @Test
    fun testGetHintText_emptyList_returnsEmptyString() {
        assertEquals("", validator.getHintText(emptyList()))
    }

    @Test
    fun testGetHintText_firstAnswerIsBlank_returnsBlank() {
        // ifBlank returns "" for blank strings
        assertEquals("", validator.getHintText(listOf("")))
    }

    @Test
    fun testHintText_firstAnswerIsWhitespace_returnsBlank() {
        assertEquals("", validator.getHintText(listOf("   ")))
    }

    @Test
    fun testGetHintText_secondAnswerNotBlank_firstIsBlank_returnsBlank() {
        // getHintText only returns the first answer, even if second is non-blank
        assertEquals("", validator.getHintText(listOf("", "ciao")))
    }

    // ------------------------------------------------------------------ //
    // HINT_THRESHOLD constant
    // ------------------------------------------------------------------ //

    @Test
    fun testHintThreshold_equalsThree() {
        assertEquals(3, AnswerValidator.HINT_THRESHOLD)
    }

    // ------------------------------------------------------------------ //
    // Edge cases — multi-answer with spaces around +
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_plusWithSpaces_splitsOnPlusButNormalizationHandlesSpaces() {
        // "ciao + salve" splits into "ciao " and " salve" — normalization trims
        val result = validator.validate("ciao", listOf("ciao + salve"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // Edge cases — normalization through Normalizer
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_timeFormat_colonStripped() {
        // Normalizer converts "8:30" to "8" (time pattern)
        val result = validator.validate("8", listOf("8:30"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_inputIsTimeFormat_colonStripped() {
        val result = validator.validate("8:30", listOf("8"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_bracketsAndBraces_stripped() {
        val result = validator.validate("ciao", listOf("[ciao]"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_semicolon_stripped() {
        val result = validator.validate("ciao", listOf("ciao;"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // Edge cases — whitespace normalization
    // ------------------------------------------------------------------ //

    @Test
    fun testValidate_multipleSpacesBetweenWords_normalizedToSingle() {
        val result = validator.validate("ciao  mondo", listOf("ciao mondo"))
        assertTrue(result.isCorrect)
    }

    @Test
    fun testValidate_leadingTrailingSpaces_trimmed() {
        val result = validator.validate("  ciao  ", listOf("ciao"))
        assertTrue(result.isCorrect)
    }

    // ------------------------------------------------------------------ //
    // Integration-like: validate + shouldShowHint workflow
    // ------------------------------------------------------------------ //

    @Test
    fun testWorkflow_wrongAnswers_triggerHintAtThreshold() {
        val acceptedAnswers = listOf("ciao+salve")
        var attempts = 0

        // Attempt 1: wrong
        val r1 = validator.validate("hello", acceptedAnswers)
        assertFalse(r1.isCorrect)
        attempts++
        assertFalse(validator.shouldShowHint(attempts))

        // Attempt 2: wrong
        val r2 = validator.validate("hola", acceptedAnswers)
        assertFalse(r2.isCorrect)
        attempts++
        assertFalse(validator.shouldShowHint(attempts))

        // Attempt 3: wrong — hint threshold reached
        val r3 = validator.validate("bonjour", acceptedAnswers)
        assertFalse(r3.isCorrect)
        attempts++
        assertTrue(validator.shouldShowHint(attempts))
        assertEquals("ciao+salve", validator.getHintText(acceptedAnswers))
    }

    @Test
    fun testWorkflow_correctAnswer_resetsImplicitly() {
        val acceptedAnswers = listOf("ciao")
        // Simulate: 2 wrong, then correct, then 1 wrong — hint should NOT show
        validator.validate("wrong1", acceptedAnswers)
        validator.validate("wrong2", acceptedAnswers)
        // Correct answer would typically reset attempts in the ViewModel;
        // AnswerValidator itself is stateless — caller manages attempt count.
        assertFalse(validator.shouldShowHint(0)) // reset by caller
        validator.validate("wrong3", acceptedAnswers)
        assertFalse(validator.shouldShowHint(1)) // only 1 attempt after reset
    }
}
