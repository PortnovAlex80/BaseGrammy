package com.alexpo.grammermate.v2.core.domain.validation

import com.alexpo.grammermate.v2.core.domain.model.InputMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [AnswerValidator] — чистой-Kotlin валидации ответа.
 *
 * Покрывает:
 *  - validate: точное совпадение после нормализации, KEYBOARD vs VOICE пути,
 *    альтернативы через `+`, testMode=true → всегда верно;
 *  - shouldShowHint: >= порога (3) → true;
 *  - getHintText: первый принимаемый ответ.
 */
class AnswerValidatorTest {

    private val validator = AnswerValidator()

    // ── validate: точное совпадение (KEYBOARD) ─────────────────────────────

    @Test
    fun `validate returns correct when keyboard input matches after normalization`() {
        val result = validator.validate("perché", listOf("perche"))
        assertThat(result.isCorrect).isTrue()
        assertThat(result.normalizedInput).isEqualTo("perche")
    }

    @Test
    fun `validate returns incorrect when keyboard input differs`() {
        val result = validator.validate("hola", listOf("ciao"))
        assertThat(result.isCorrect).isFalse()
        assertThat(result.normalizedInput).isEqualTo("hola")
    }

    @Test
    fun `validate normalizes whitespace and case before comparison`() {
        val result = validator.validate("  Ciao Mondo  ", listOf("ciao mondo"))
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate keyboard path keeps apostrophe in normalized input`() {
        // Расхождение с v1: апостроф сохраняется → "l'albero" != "lalbero".
        val result = validator.validate("l'albero", listOf("lalbero"))
        assertThat(result.isCorrect).isFalse()
        assertThat(result.normalizedInput).isEqualTo("l'albero")
    }

    // ── validate: VOICE path ───────────────────────────────────────────────

    @Test
    fun `validate voice path uses normalizeForVoice`() {
        // VOICE: апостроф → пробел, поэтому "l'albero" (answer) сравнивается как
        // "l albero" с инпутом "l albero".
        val result = validator.validate(
            input = "l albero",
            acceptedAnswers = listOf("l'albero"),
            inputMode = InputMode.VOICE,
        )
        assertThat(result.isCorrect).isTrue()
        assertThat(result.normalizedInput).isEqualTo("l albero")
    }

    @Test
    fun `validate voice path splits hyphen to space`() {
        val result = validator.validate(
            input = "e mail",
            acceptedAnswers = listOf("e-mail"),
            inputMode = InputMode.VOICE,
        )
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate voice incorrect when input has no match`() {
        val result = validator.validate(
            input = "treno",
            acceptedAnswers = listOf("l'albero"),
            inputMode = InputMode.VOICE,
        )
        assertThat(result.isCorrect).isFalse()
    }

    // ── validate: альтернативы через `+` ──────────────────────────────────

    @Test
    fun `validate accepts any alternative separated by plus`() {
        // CSV multi-answer: одна строка с альтернативами "ciao+salve".
        val result = validator.validate("salve", listOf("ciao+salve"))
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate rejects when input matches none of the alternatives`() {
        val result = validator.validate("hola", listOf("ciao+salve"))
        assertThat(result.isCorrect).isFalse()
    }

    @Test
    fun `validate matches across multiple accepted answers and alternatives`() {
        val result = validator.validate("buongiorno", listOf("ciao+salve", "buongiorno"))
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate voice path also splits answers by plus`() {
        val result = validator.validate(
            input = "salve",
            acceptedAnswers = listOf("ciao+salve"),
            inputMode = InputMode.VOICE,
        )
        assertThat(result.isCorrect).isTrue()
    }

    // ── validate: testMode ─────────────────────────────────────────────────

    @Test
    fun `validate testMode true always returns correct regardless of input`() {
        val result = validator.validate(
            input = "абсолютно неверно",
            acceptedAnswers = listOf("ciao"),
            testMode = true,
        )
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate testMode true with empty answers still correct`() {
        val result = validator.validate(
            input = "x",
            acceptedAnswers = emptyList(),
            testMode = true,
        )
        assertThat(result.isCorrect).isTrue()
    }

    @Test
    fun `validate result carries normalizedInput in testMode too`() {
        val result = validator.validate(
            input = "Perché",
            acceptedAnswers = listOf("x"),
            testMode = true,
        )
        assertThat(result.isCorrect).isTrue()
        assertThat(result.normalizedInput).isEqualTo("perche")
    }

    // ── validate: дефолт hintShown/hintText всегда null/выкл из validate ──

    @Test
    fun `validate result never sets hint fields directly`() {
        val result = validator.validate("ciao", listOf("ciao"))
        assertThat(result.hintShown).isFalse()
        assertThat(result.hintText).isNull()
    }

    // ── shouldShowHint ─────────────────────────────────────────────────────

    @Test
    fun `shouldShowHint false below threshold`() {
        assertThat(validator.shouldShowHint(0)).isFalse()
        assertThat(validator.shouldShowHint(1)).isFalse()
        assertThat(validator.shouldShowHint(2)).isFalse()
    }

    @Test
    fun `shouldShowHint true at threshold`() {
        assertThat(validator.shouldShowHint(3)).isTrue()
    }

    @Test
    fun `shouldShowHint true above threshold`() {
        assertThat(validator.shouldShowHint(4)).isTrue()
        assertThat(validator.shouldShowHint(10)).isTrue()
    }

    // ── getHintText ────────────────────────────────────────────────────────

    @Test
    fun `getHintText returns first accepted answer`() {
        assertThat(validator.getHintText(listOf("ciao", "salve"))).isEqualTo("ciao")
    }

    @Test
    fun `getHintText returns empty string for empty list`() {
        assertThat(validator.getHintText(emptyList())).isEqualTo("")
    }

    @Test
    fun `getHintText returns empty string when first answer is blank`() {
        assertThat(validator.getHintText(listOf("   ", "salve"))).isEqualTo("")
    }

    // ── HINT_THRESHOLD экспонирован ────────────────────────────────────────

    @Test
    fun `HINT_THRESHOLD constant equals three`() {
        assertThat(AnswerValidator.HINT_THRESHOLD).isEqualTo(3)
    }
}
