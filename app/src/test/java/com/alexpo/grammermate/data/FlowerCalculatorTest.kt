package com.alexpo.grammermate.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FlowerCalculator - защита расчета состояния цветков.
 *
 * Покрывает требования:
 * - FR-8.1.1-8.1.7: Определение состояния цветка
 * - FR-8.3.1-8.3.5: Расчет процента мастерства и масштаба
 */
class FlowerCalculatorTest {

    private val EPSILON = 0.01f

    // ========================================
    // 2.1 Базовые состояния
    // ========================================

    @Test
    fun calculate_nullMastery_returnsSeedState() {
        // FR-8.3.5: Урок не начат → SEED state
        val flower = FlowerCalculator.calculate(mastery = null, totalCardsInLesson = 100)
        assertEquals(FlowerState.SEED, flower.state)
        assertEquals(0f, flower.masteryPercent, EPSILON)
        assertEquals(1f, flower.healthPercent, EPSILON)
        assertEquals(0.5f, flower.scaleMultiplier, EPSILON)
    }

    @Test
    fun calculate_zeroShows_returnsSeedState() {
        // FR-8.1.2: 0 показов → SEED
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 0,
            lastShowDateMs = 0L
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.SEED, flower.state)
    }

    @Test
    fun calculate_moreThan90Days_returnsGoneState() {
        // FR-8.1.7: > 90 дней → GONE
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000)
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.GONE, flower.state)
        assertEquals(0f, flower.healthPercent, EPSILON)
    }

    // ========================================
    // 2.2 Определение состояния по проценту мастерства
    // ========================================

    @Test
    fun calculate_0to33PercentMastery_returnsSeed() {
        // FR-8.1.2: 0-33% мастерства → SEED
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 25, // 25/150 = 16.6%
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.SEED, flower.state)
        assertTrue(flower.masteryPercent < 0.33f)
    }

    @Test
    fun calculate_33to66PercentMastery_returnsSprout() {
        // FR-8.1.3: 33-66% мастерства → SPROUT
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 75, // 75/150 = 50%
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.SPROUT, flower.state)
        assertTrue(flower.masteryPercent in 0.33f..0.66f)
    }

    @Test
    fun calculate_66to100PercentMastery_returnsBloom() {
        // FR-8.1.4: 66-100% мастерства → BLOOM
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 120, // 120/150 = 80%
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.BLOOM, flower.state)
        assertTrue(flower.masteryPercent >= 0.66f)
    }

    // ========================================
    // 2.3 Увядание по здоровью
    // ========================================

    @Test
    fun calculate_healthBelow100Percent_returnsWilting() {
        // FR-8.1.5: Здоровье < 100% → WILTING
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 120,
            lastShowDateMs = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000), // 3 дня назад
            intervalStepIndex = 0 // ожидаемый интервал = 1 день, здоровье упало но не критично
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.WILTING, flower.state)
        assertTrue("Health should be < 1.0", flower.healthPercent < 1.0f)
        assertTrue("Health should be > WILTED_THRESHOLD", flower.healthPercent > SpacedRepetitionConfig.WILTED_THRESHOLD + 0.01f)
    }

    @Test
    fun calculate_healthBelowWiltedThreshold_returnsWilted() {
        // FR-8.1.6: Здоровье ≤ 50% → WILTED
        // Нужно создать ситуацию, когда здоровье упадёт до минимума
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 120,
            lastShowDateMs = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000), // 60 дней назад
            intervalStepIndex = 0
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.WILTED, flower.state)
    }

    @Test
    fun calculate_wiltingOverridesBloomState() {
        // FR-8.1.5: Увядание перекрывает состояние BLOOM
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 150, // 100% мастерства
            lastShowDateMs = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000), // 3 дня назад
            intervalStepIndex = 0 // ожидаемый интервал = 1 день
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        // Должен быть WILTING, а не BLOOM
        assertEquals(FlowerState.WILTING, flower.state)
    }

    // ========================================
    // 2.4 Расчет процента мастерства
    // ========================================

    @Test
    fun calculate_50Shows_returns33PercentMastery() {
        // FR-8.3.1: 50 показов → 33% мастерства
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(50f / 150f, flower.masteryPercent, EPSILON)
    }

    @Test
    fun calculate_150Shows_returns100PercentMastery() {
        // FR-8.3.2: 150 показов → 100% мастерства
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 150,
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(1.0f, flower.masteryPercent, EPSILON)
    }

    @Test
    fun calculate_200Shows_capsAt100PercentMastery() {
        // FR-8.3.2: > 150 показов → cap на 100%
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 200,
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(1.0f, flower.masteryPercent, EPSILON)
    }

    // ========================================
    // 2.5 Масштаб цветка
    // ========================================

    @Test
    fun calculate_scaleMultiplier_neverBelow50Percent() {
        // FR-8.3.4: Масштаб не меньше 50%
        for (shows in 0..200 step 10) {
            for (days in 0..89) {
                val mastery = LessonMasteryState(
                    lessonId = "test",
                    languageId = "en",
                    uniqueCardShows = shows,
                    lastShowDateMs = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
                )
                val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
                assertTrue(
                    "Scale should be >= 0.5 for shows=$shows days=$days, got ${flower.scaleMultiplier}",
                    flower.scaleMultiplier >= 0.5f - EPSILON
                )
            }
        }
    }

    @Test
    fun calculate_scaleMultiplier_maxIs100Percent() {
        // FR-8.3.4: Максимальный масштаб = 100%
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 150,
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(1.0f, flower.scaleMultiplier, EPSILON)
    }

    @Test
    fun calculate_scaleMultiplier_isMasteryTimesHealth() {
        // FR-8.3.4: scale = mastery% * health%
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 75, // 50% мастерства
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        // health = 100%, mastery = 50%, scale = 0.5 * 1.0 = 0.5
        assertEquals(0.5f, flower.scaleMultiplier, EPSILON)
    }

    // ========================================
    // 2.6 Emoji представления
    // ========================================

    @Test
    fun getEmoji_returnsCorrectEmojiForEachState() {
        assertEquals("\uD83D\uDD12", FlowerCalculator.getEmoji(FlowerState.LOCKED))
        assertEquals("\uD83C\uDF31", FlowerCalculator.getEmoji(FlowerState.SEED))
        assertEquals("\uD83C\uDF3F", FlowerCalculator.getEmoji(FlowerState.SPROUT))
        assertEquals("\uD83C\uDF38", FlowerCalculator.getEmoji(FlowerState.BLOOM))
        assertEquals("\uD83E\uDD40", FlowerCalculator.getEmoji(FlowerState.WILTING))
        assertEquals("\uD83C\uDF42", FlowerCalculator.getEmoji(FlowerState.WILTED))
        assertEquals("\u26AB", FlowerCalculator.getEmoji(FlowerState.GONE))
    }

    @Test
    fun getEmojiWithScale_returnsPairWithScale() {
        val flower = FlowerVisual(
            state = FlowerState.BLOOM,
            masteryPercent = 0.8f,
            healthPercent = 1.0f,
            scaleMultiplier = 0.8f
        )
        val (emoji, scale) = FlowerCalculator.getEmojiWithScale(flower)
        assertEquals("\uD83C\uDF38", emoji)
        assertEquals(0.8f, scale, EPSILON)
    }

    // ========================================
    // 2.7 Граничные случаи
    // ========================================

    @Test
    fun calculate_exactly150Shows_returns100PercentMastery() {
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 150,
            lastShowDateMs = System.currentTimeMillis()
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(1.0f, flower.masteryPercent, EPSILON)
    }

    @Test
    fun calculate_exactly90Days_notGoneYet() {
        // Ровно 90 дней ещё не GONE (только > 90)
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.WILTED, flower.state) // На 90-й день ещё WILTED
    }

    @Test
    fun calculate_exactly91Days_isGone() {
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = System.currentTimeMillis() - (91L * 24 * 60 * 60 * 1000)
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(FlowerState.GONE, flower.state)
    }

    @Test
    fun calculate_negativeTimestamp_treatedAsZeroDays() {
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = -1000L
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        // Должно обрабатываться как 0 дней → здоровье 100%
        assertEquals(1.0f, flower.healthPercent, EPSILON)
    }

    @Test
    fun calculate_zeroTimestamp_treatedAsZeroDays() {
        val mastery = LessonMasteryState(
            lessonId = "test",
            languageId = "en",
            uniqueCardShows = 50,
            lastShowDateMs = 0L
        )
        val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
        assertEquals(1.0f, flower.healthPercent, EPSILON)
    }

    @Test
    fun calculate_scaleNeverNaN() {
        // Проверка, что scale никогда не NaN
        for (shows in 0..200 step 10) {
            for (days in 0..100) {
                val mastery = LessonMasteryState(
                    lessonId = "test",
                    languageId = "en",
                    uniqueCardShows = shows,
                    lastShowDateMs = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
                )
                val flower = FlowerCalculator.calculate(mastery, totalCardsInLesson = 100)
                assertFalse("Scale should never be NaN", flower.scaleMultiplier.isNaN())
                assertFalse("MasteryPercent should never be NaN", flower.masteryPercent.isNaN())
                assertFalse("HealthPercent should never be NaN", flower.healthPercent.isNaN())
            }
        }
    }
}
