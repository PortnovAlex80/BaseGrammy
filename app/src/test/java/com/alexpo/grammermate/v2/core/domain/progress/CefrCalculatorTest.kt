package com.alexpo.grammermate.v2.core.domain.progress

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [CefrCalculator] — маппинг медианы рангов на CEFR.
 *
 * Алгоритм:
 *  - пустой список → "—";
 *  - < 10 рангов → "A1";
 *  - медиана рангов; пороги: <500→A1, <1000→A2, <2000→B1, <4000→B2, <8000→C1, иначе C2.
 */
class CefrCalculatorTest {

    // ── Пустой список ──────────────────────────────────────────────────────

    @Test
    fun `calculate empty list returns dash`() {
        assertThat(CefrCalculator.calculate(emptyList())).isEqualTo("—")
    }

    // ── Меньше 10 рангов → A1 ──────────────────────────────────────────────

    @Test
    fun `calculate fewer than 10 ranks returns A1 regardless of values`() {
        assertThat(CefrCalculator.calculate(listOf(1, 2, 3))).isEqualTo("A1")
        assertThat(CefrCalculator.calculate((1..9).toList())).isEqualTo("A1")
        // Даже если бы медиана была C2 — размер < 10 форсит A1.
        assertThat(CefrCalculator.calculate(listOf(10000, 20000, 30000, 40000))).isEqualTo("A1")
    }

    // ── Пороговые значения (10+ рангов) ────────────────────────────────────

    @Test
    fun `calculate median below 500 returns A1`() {
        // 10 одинаковых рангов = 100 → медиана 100 < 500 → A1.
        assertThat(CefrCalculator.calculate(List(10) { 100 })).isEqualTo("A1")
    }

    @Test
    fun `calculate median below 1000 returns A2`() {
        // медиана 700.
        assertThat(CefrCalculator.calculate(List(10) { 700 })).isEqualTo("A2")
    }

    @Test
    fun `calculate median below 2000 returns B1`() {
        assertThat(CefrCalculator.calculate(List(10) { 1500 })).isEqualTo("B1")
    }

    @Test
    fun `calculate median below 4000 returns B2`() {
        assertThat(CefrCalculator.calculate(List(10) { 3000 })).isEqualTo("B2")
    }

    @Test
    fun `calculate median below 8000 returns C1`() {
        assertThat(CefrCalculator.calculate(List(10) { 5000 })).isEqualTo("C1")
    }

    @Test
    fun `calculate median at or above 8000 returns C2`() {
        assertThat(CefrCalculator.calculate(List(10) { 8000 })).isEqualTo("C2")
        assertThat(CefrCalculator.calculate(List(10) { 12000 })).isEqualTo("C2")
    }

    // ── Медиана считается корректно ────────────────────────────────────────

    @Test
    fun `calculate uses median for odd count`() {
        // 11 элементов, центральный = rank 500 → медиана 500 → НЕ < 500 → A2.
        val ranks = List(5) { 100 } + listOf(500) + List(5) { 900 }
        assertThat(CefrCalculator.calculate(ranks)).isEqualTo("A2")
    }

    @Test
    fun `calculate uses average of two middle for even count`() {
        // 10 элементов: среднее двух центральных.
        // Сортированный: пять 400 и пять 600 → центральные 400,600 → медиана 500.
        val ranks = List(5) { 400 } + List(5) { 600 }
        // медиана (400+600)/2 = 500 → НЕ <500 → A2.
        assertThat(CefrCalculator.calculate(ranks)).isEqualTo("A2")
    }

    @Test
    fun `calculate handles unsorted input via internal sort`() {
        // Перемешанные ранги с медианой < 500.
        val ranks = listOf(900, 50, 300, 100, 200, 80, 150, 250, 60, 120)
        assertThat(CefrCalculator.calculate(ranks)).isEqualTo("A1")
    }

    @Test
    fun `calculate boundary exactly 500 is not A1 but A2`() {
        // медиана ровно 500: 500 < 500 → false → проверка <1000 → A2.
        assertThat(CefrCalculator.calculate(List(10) { 500 })).isEqualTo("A2")
    }
}
