package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for SpacedRepetitionConfig - защита алгоритма кривой забывания Эббингауза.
 *
 * Покрывает требования:
 * - FR-9.1.1-9.1.4: Алгоритм Эббингауза
 * - FR-9.2.1-9.2.4: Лестница интервалов
 * - FR-9.3.1-9.3.5: Расчет здоровья цветка
 */
class SpacedRepetitionConfigTest {

    // Epsilon для сравнения float чисел
    private val EPSILON = 0.01f

    // ========================================
    // 1.1 Расчет стабильности памяти
    // ========================================

    @Test
    fun calculateStability_firstStep_returnsBaseStability() {
        // FR-9.1.2: Базовая стабильность для первого повторения
        val stability = SpacedRepetitionConfig.calculateStability(0)
        assertEquals(0.9, stability, 0.01)
    }

    @Test
    fun calculateStability_negativeIndex_returnsBaseStability() {
        // Граничный случай: отрицательный индекс
        val stability = SpacedRepetitionConfig.calculateStability(-1)
        assertEquals(0.9, stability, 0.01)
    }

    @Test
    fun calculateStability_secondStep_returnsMultipliedStability() {
        // FR-9.1.3: Стабильность увеличивается с каждым повторением
        val stability = SpacedRepetitionConfig.calculateStability(1)
        // S = 0.9 * 2.2 = 1.98
        assertEquals(1.98, stability, 0.01)
    }

    @Test
    fun calculateStability_maxStep_returnsCorrectStability() {
        // FR-9.1.4: Максимальная стабильность на последнем шаге лестницы
        val maxIndex = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val stability = SpacedRepetitionConfig.calculateStability(maxIndex)
        // S = 0.9 * 2.2^9 (так как 10 шагов в лестнице)
        assertTrue("Stability should grow exponentially", stability > 100.0)
    }

    @Test
    fun calculateStability_beyondMax_capsAtMaxStep() {
        // Проверка, что стабильность не растёт после max шага
        val maxIndex = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val stabilityAtMax = SpacedRepetitionConfig.calculateStability(maxIndex)
        val stabilityBeyondMax = SpacedRepetitionConfig.calculateStability(maxIndex + 5)
        assertEquals(stabilityAtMax, stabilityBeyondMax, 0.01)
    }

    // ========================================
    // 1.2 Расчет retention (удержания)
    // ========================================

    @Test
    fun calculateRetention_zeroDays_returns100Percent() {
        // FR-9.1.1: В день повторения retention = 100%
        val retention = SpacedRepetitionConfig.calculateRetention(0, 0)
        assertEquals(1.0f, retention, EPSILON)
    }

    @Test
    fun calculateRetention_negativeDays_returns100Percent() {
        // Граничный случай: отрицательные дни
        val retention = SpacedRepetitionConfig.calculateRetention(-1, 0)
        assertEquals(1.0f, retention, EPSILON)
    }

    @Test
    fun calculateRetention_oneDayFirstStep_returnsExpectedRetention() {
        // FR-9.1.1: R = e^(-t/S) = e^(-1/0.9) ≈ 0.33 (33%)
        val retention = SpacedRepetitionConfig.calculateRetention(1, 0)
        assertTrue("Retention after 1 day should be ~33%", retention in 0.30f..0.36f)
    }

    @Test
    fun calculateRetention_longTime_approachesZero() {
        // FR-9.1.1: При большом времени retention → 0
        val retention = SpacedRepetitionConfig.calculateRetention(365, 0)
        assertTrue("Retention after 1 year should be close to 0", retention < 0.01f)
    }

    @Test
    fun calculateRetention_neverExceedsOne() {
        // Property: retention всегда в диапазоне [0, 1]
        for (days in 0..100) {
            for (step in 0..9) {
                val retention = SpacedRepetitionConfig.calculateRetention(days, step)
                assertTrue("Retention must be <= 1.0", retention <= 1.0f)
                assertTrue("Retention must be >= 0.0", retention >= 0.0f)
            }
        }
    }

    @Test
    fun calculateRetention_higherStepSlowerDecay() {
        // Property: На более высоких шагах retention падает медленнее
        val retention1 = SpacedRepetitionConfig.calculateRetention(7, 0)
        val retention2 = SpacedRepetitionConfig.calculateRetention(7, 5)
        assertTrue("Higher step should have higher retention", retention2 > retention1)
    }

    // ========================================
    // 1.3 Расчет здоровья цветка
    // ========================================

    @Test
    fun calculateHealthPercent_zeroDays_returns100Percent() {
        // FR-9.3.1: В день показа здоровье = 100%
        val health = SpacedRepetitionConfig.calculateHealthPercent(0, 0)
        assertEquals(1.0f, health, EPSILON)
    }

    @Test
    fun calculateHealthPercent_withinInterval_returns100Percent() {
        // FR-9.3.2: В пределах ожидаемого интервала здоровье = 100%
        val health = SpacedRepetitionConfig.calculateHealthPercent(1, 0) // step 0 = 1 day
        assertEquals(1.0f, health, EPSILON)

        val health2 = SpacedRepetitionConfig.calculateHealthPercent(7, 3) // step 3 = 7 days
        assertEquals(1.0f, health2, EPSILON)
    }

    @Test
    fun calculateHealthPercent_overdueDecays_from100to50Percent() {
        // FR-9.3.3: При просрочке здоровье падает от 100% до 50%
        val health = SpacedRepetitionConfig.calculateHealthPercent(10, 0) // step 0 = 1 day, overdue 9 days
        assertTrue("Health should decay below 100%", health < 1.0f)
        assertTrue("Health should be above 50%", health >= 0.5f)
    }

    @Test
    fun calculateHealthPercent_ninetyDays_returnsZero() {
        // FR-9.3.5: После 90 дней цветок исчезает (health = 0)
        val health = SpacedRepetitionConfig.calculateHealthPercent(90, 0)
        assertEquals(0.0f, health, EPSILON)

        val health91 = SpacedRepetitionConfig.calculateHealthPercent(91, 0)
        assertEquals(0.0f, health91, EPSILON)
    }

    @Test
    fun calculateHealthPercent_exactly89Days_notGone() {
        // FR-9.3.5: На 89-й день ещё не исчез
        val health = SpacedRepetitionConfig.calculateHealthPercent(89, 0)
        assertTrue("Health should be > 0 at day 89", health > 0.0f)
    }

    @Test
    fun calculateHealthPercent_neverBelowWiltedThreshold() {
        // FR-9.3.4: Здоровье не падает ниже WILTED_THRESHOLD (50%), кроме GONE
        for (days in 0..89) {
            for (step in 0..9) {
                val health = SpacedRepetitionConfig.calculateHealthPercent(days, step)
                assertTrue(
                    "Health at day $days step $step should be >= 0.5",
                    health >= SpacedRepetitionConfig.WILTED_THRESHOLD || abs(health - SpacedRepetitionConfig.WILTED_THRESHOLD) < EPSILON
                )
            }
        }
    }

    @Test
    fun calculateHealthPercent_exponentialDecayFormula() {
        // FR-9.3.3: Проверка экспоненциального затухания
        val health1 = SpacedRepetitionConfig.calculateHealthPercent(5, 0)
        val health2 = SpacedRepetitionConfig.calculateHealthPercent(10, 0)
        val health3 = SpacedRepetitionConfig.calculateHealthPercent(20, 0)

        // Health должно падать экспоненциально, т.е. не линейно
        val diff1 = health1 - health2
        val diff2 = health2 - health3
        assertTrue("Decay should be exponential, not linear", diff1 > diff2)
    }

    // ========================================
    // 1.4 Лестница интервалов
    // ========================================

    @Test
    fun nextIntervalStep_onTime_advancesStep() {
        // FR-9.2.1: При повторении вовремя продвигаемся вперёд
        val nextStep = SpacedRepetitionConfig.nextIntervalStep(0, wasOnTime = true)
        assertEquals(1, nextStep)

        val nextStep2 = SpacedRepetitionConfig.nextIntervalStep(5, wasOnTime = true)
        assertEquals(6, nextStep2)
    }

    @Test
    fun nextIntervalStep_late_keepsCurrentStep() {
        // FR-9.2.2: При опоздании остаёмся на текущем шаге
        val nextStep = SpacedRepetitionConfig.nextIntervalStep(3, wasOnTime = false)
        assertEquals(3, nextStep)
    }

    @Test
    fun nextIntervalStep_maxStep_staysAtMax() {
        // FR-9.2.3: На максимальном шаге остаёмся на месте
        val maxIndex = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS.size - 1
        val nextStep = SpacedRepetitionConfig.nextIntervalStep(maxIndex, wasOnTime = true)
        assertEquals(maxIndex, nextStep)
    }

    @Test
    fun nextIntervalStep_negativeStep_coercesToZero() {
        // Граничный случай: отрицательный шаг
        val nextStep = SpacedRepetitionConfig.nextIntervalStep(-5, wasOnTime = false)
        assertEquals(0, nextStep)
    }

    @Test
    fun wasRepetitionOnTime_withinInterval_returnsTrue() {
        // FR-9.2.4: В пределах интервала = вовремя
        val onTime = SpacedRepetitionConfig.wasRepetitionOnTime(1, 0) // step 0 = 1 day
        assertTrue(onTime)

        val onTime2 = SpacedRepetitionConfig.wasRepetitionOnTime(7, 3) // step 3 = 7 days
        assertTrue(onTime2)
    }

    @Test
    fun wasRepetitionOnTime_overdue_returnsFalse() {
        // FR-9.2.4: Превышение интервала = опоздание
        val late = SpacedRepetitionConfig.wasRepetitionOnTime(2, 0) // step 0 = 1 day, показали через 2 дня
        assertFalse(late)

        val late2 = SpacedRepetitionConfig.wasRepetitionOnTime(8, 3) // step 3 = 7 days, показали через 8 дней
        assertFalse(late2)
    }

    @Test
    fun intervalLadderDays_hasCorrectValues() {
        // FR-9.2.1: Проверка значений лестницы интервалов
        val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
        assertEquals(10, ladder.size)
        assertEquals(listOf(1, 2, 4, 7, 10, 14, 20, 28, 42, 56), ladder)
    }

    @Test
    fun intervalLadderDays_isMonotonicallyIncreasing() {
        // Property: Лестница должна быть монотонно возрастающей
        val ladder = SpacedRepetitionConfig.INTERVAL_LADDER_DAYS
        for (i in 0 until ladder.size - 1) {
            assertTrue("Ladder should be increasing at index $i", ladder[i] < ladder[i + 1])
        }
    }

    // ========================================
    // 1.5 Константы и пороги
    // ========================================

    @Test
    fun constants_masteryThreshold_equals150() {
        // FR-9.1.2: Порог мастерства = 150 показов
        assertEquals(150, SpacedRepetitionConfig.MASTERY_THRESHOLD)
    }

    @Test
    fun constants_wiltedThreshold_equals50Percent() {
        // FR-9.3.4: Порог увядания = 50%
        assertEquals(0.5f, SpacedRepetitionConfig.WILTED_THRESHOLD, EPSILON)
    }

    @Test
    fun constants_goneThresholdDays_equals90() {
        // FR-9.3.5: Порог исчезновения = 90 дней
        assertEquals(90, SpacedRepetitionConfig.GONE_THRESHOLD_DAYS)
    }

    @Test
    fun constants_baseStability_isPositive() {
        // FR-9.1.2: Базовая стабильность положительная
        val stability = SpacedRepetitionConfig.calculateStability(0)
        assertTrue("Base stability should be positive", stability > 0)
    }

    @Test
    fun constants_stabilityGrowth_isExponential() {
        // FR-9.1.3: Стабильность растёт экспоненциально
        val s0 = SpacedRepetitionConfig.calculateStability(0)
        val s1 = SpacedRepetitionConfig.calculateStability(1)
        val s2 = SpacedRepetitionConfig.calculateStability(2)

        val ratio1 = s1 / s0
        val ratio2 = s2 / s1

        // Отношения должны быть примерно равны (экспоненциальный рост)
        assertEquals(ratio1, ratio2, 0.1)
    }
}
