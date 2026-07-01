package com.alexpo.grammermate.v2.core.domain.training

import com.alexpo.grammermate.v2.core.domain.model.HintLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [HintCalculator] — чистого расчёта видимости подсказок.
 *
 * Подсказки — текст в круглых скобках. effective = min(schedulerFraction, userFraction).
 *  - schedulerFraction: review=1.0, encounter≤1=1.0, encounter=2=0.5, иначе 0.0;
 *  - userFraction: EASY=1.0, MEDIUM=0.5, HARD=0.0.
 */
class HintCalculatorTest {

    private val promptWithHints = "Переведи (подсказка1) слово (подсказка2) сюда (подсказка3)"

    // ── calculateEffectiveHints: boss → stripAll всегда ────────────────────

    @Test
    fun `calculateEffectiveHints boss battle always strips all hints`() {
        // Даже при encounter=0 и EASY — boss форсит stripAll.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 0,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
            isBossBattle = true,
        )
        // Regex \s*\([^)]+\) съедает лидирующий пробел вместе со скобкой → одинарные пробелы.
        assertThat(result).doesNotContain("(подсказка1)")
        assertThat(result).isEqualTo("Переведи слово сюда")
    }

    @Test
    fun `calculateEffectiveHints boss overrides review mode`() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = "a (x) b",
            encounterCount = 5,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
            isBossBattle = true,
            isReviewMode = true,
        )
        assertThat(result).isEqualTo("a b")
    }

    // ── calculateEffectiveHints: schedulerFraction ─────────────────────────

    @Test
    fun `calculateEffectiveHints encounter zero returns prompt unchanged when EASY`() {
        // scheduler=1.0 (encounter<=1), user=1.0 (EASY) → effective 1.0 → без изменений.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 0,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
        )
        assertThat(result).isEqualTo(promptWithHints)
    }

    @Test
    fun `calculateEffectiveHints encounter one returns prompt unchanged when EASY`() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 1,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
        )
        assertThat(result).isEqualTo(promptWithHints)
    }

    @Test
    fun `calculateEffectiveHints encounter two applies stripHalf when EASY`() {
        // scheduler=0.5, user=1.0 → effective 0.5 → stripHalf.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
        )
        // НЕ равен исходному (часть подсказок убрана).
        assertThat(result).isNotEqualTo(promptWithHints)
        // Но и не весь — проверим что остались скобки.
        assertThat(result).contains("(")
    }

    @Test
    fun `calculateEffectiveHints encounter three or more strips all when EASY`() {
        // scheduler=0.0, user=1.0 → effective 0.0 → stripAll.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 3,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
        )
        assertThat(result).doesNotContain("(")
        assertThat(result).isEqualTo("Переведи слово сюда")
    }

    @Test
    fun `calculateEffectiveHints review mode returns prompt unchanged when EASY`() {
        // scheduler=1.0 (review), user=1.0 → 1.0.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 3,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
            isReviewMode = true,
        )
        assertThat(result).isEqualTo(promptWithHints)
    }

    // ── calculateEffectiveHints: userFraction ──────────────────────────────

    @Test
    fun `calculateEffectiveHints HARD always strips all for first encounter`() {
        // scheduler=1.0 (encounter0), user=0.0 (HARD) → effective 0.0 → stripAll.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 0,
            hintLevel = HintLevel.HARD,
            sessionOffset = 0,
        )
        assertThat(result).doesNotContain("(")
    }

    @Test
    fun `calculateEffectiveHints MEDIUM applies stripHalf for first encounter`() {
        // scheduler=1.0, user=0.5 → 0.5 → stripHalf.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 0,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 0,
        )
        assertThat(result).isNotEqualTo(promptWithHints)
        assertThat(result).contains("(")
    }

    @Test
    fun `calculateEffectiveHints MEDIUM encounter two strips all`() {
        // scheduler=0.5, user=0.5 → 0.5 → stripHalf (effective=0.5 → stripHalf).
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 0,
        )
        assertThat(result).isNotEqualTo(promptWithHints)
        assertThat(result).contains("(")
    }

    @Test
    fun `calculateEffectiveHints HARD encounter two strips all`() {
        // scheduler=0.5, user=0.0 → 0.0 → stripAll.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.HARD,
            sessionOffset = 0,
        )
        assertThat(result).doesNotContain("(")
    }

    @Test
    fun `calculateEffectiveHints effective takes minimum of two fractions`() {
        // scheduler=0.0 (encounter3), user=0.5 (MEDIUM) → min=0.0 → stripAll.
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 3,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 0,
        )
        assertThat(result).doesNotContain("(")
    }

    // ── stripAll ───────────────────────────────────────────────────────────

    @Test
    fun `stripAll removes all parenthetical groups`() {
        // Лидирующий пробел перед скобкой захватывается regex → одинарные пробелы.
        assertThat(HintCalculator.stripAll("a (1) b (2) c")).isEqualTo("a b c")
    }

    @Test
    fun `stripAll removes leading space before parenthetical`() {
        // Regex \s*\([^)]+\) захватывает лидирующий пробел.
        assertThat(HintCalculator.stripAll("word (hint)")).isEqualTo("word")
    }

    @Test
fun `stripAll leaves text without parens unchanged`() {
    assertThat(HintCalculator.stripAll("no hints here")).isEqualTo("no hints here")
}

    @Test
    fun `stripAll on empty string returns empty`() {
        assertThat(HintCalculator.stripAll("")).isEqualTo("")
    }

    // ── stripHalf ──────────────────────────────────────────────────────────

    @Test
    fun `stripHalf with offset zero keeps even-indexed parens removes odd`() {
        // 3 скобки: index0→show, index1→remove, index2→show.
        val result = HintCalculator.stripHalf("a (1) b (2) c (3)", offset = 0)
        assertThat(result).contains("(1)")
        assertThat(result).doesNotContain("(2)")
        assertThat(result).contains("(3)")
    }

    @Test
    fun `stripHalf with offset one inverts the pattern`() {
        // offset=1: index0→(0+1)%2=1→remove, index1→(1+1)%2=0→show, index2→(2+1)%2=1→remove.
        val result = HintCalculator.stripHalf("a (1) b (2) c (3)", offset = 1)
        assertThat(result).doesNotContain("(1)")
        assertThat(result).contains("(2)")
        assertThat(result).doesNotContain("(3)")
    }

    @Test
    fun `stripHalf with single parenthetical and offset zero keeps it`() {
        val result = HintCalculator.stripHalf("word (hint)", offset = 0)
        assertThat(result).contains("(hint)")
    }

    @Test
    fun `stripHalf with single parenthetical and offset one removes it`() {
        val result = HintCalculator.stripHalf("word (hint)", offset = 1)
        assertThat(result).doesNotContain("(hint)")
    }

    @Test
    fun `stripHalf leaves text without parens unchanged`() {
        assertThat(HintCalculator.stripHalf("no parens", offset = 0)).isEqualTo("no parens")
    }

    @Test
    fun `stripHalf is deterministic for same offset`() {
        val a = HintCalculator.stripHalf("a (1) b (2) c (3) d (4)", offset = 0)
        val b = HintCalculator.stripHalf("a (1) b (2) c (3) d (4)", offset = 0)
        assertThat(a).isEqualTo(b)
    }
}
