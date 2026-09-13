package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Уровни подсказок.
 *
 * Главное, что здесь закреплено: MEDIUM обязан отличаться и от EASY, и от
 * HARD на обычной карточке — с одной скобкой-подсказкой. Именно этого не было:
 * "половина" пряталась поскобочно, скобка у 99% карточек одна, а offset
 * задаётся раз на сессию, поэтому MEDIUM целую сессию совпадал либо с EASY,
 * либо с HARD.
 */
class HintCalculatorTest {

    private val prompt = "Я покупаю дом (compro, una casa)"

    private fun hint(level: HintLevel, offset: Int = 0, encounters: Int = 1) =
        HintCalculator.calculateEffectiveHints(
            promptRu = prompt,
            encounterCount = encounters,
            hintLevel = level,
            sessionOffset = offset
        )

    @Test
    fun easy_keepsWholeHint() {
        assertThat(hint(HintLevel.EASY)).isEqualTo("Я покупаю дом (compro, una casa)")
    }

    @Test
    fun hard_removesHintEntirely() {
        assertThat(hint(HintLevel.HARD)).isEqualTo("Я покупаю дом")
    }

    @Test
    fun medium_differsFromEasyAndHard_atEveryOffset() {
        // регрессия: при любом offset MEDIUM не должен схлопываться в EASY/HARD
        for (offset in 0 until 20) {
            val medium = hint(HintLevel.MEDIUM, offset)
            assertThat(medium).isNotEqualTo(hint(HintLevel.EASY, offset))
            assertThat(medium).isNotEqualTo(hint(HintLevel.HARD, offset))
        }
    }

    @Test
    fun medium_keepsHalfOfLemmasAndMarksTheRest() {
        // 2 леммы -> показывается одна, вторая заменена многоточием
        assertThat(hint(HintLevel.MEDIUM, offset = 0))
            .isEqualTo("Я покупаю дом (compro, …)")
    }

    @Test
    fun medium_offsetShiftsWindowButKeepsSourceOrder() {
        val four = "Если бы я купил дом (se, avessi, comprato, casa)"
        fun at(offset: Int) = HintCalculator.calculateEffectiveHints(
            promptRu = four, encounterCount = 1,
            hintLevel = HintLevel.MEDIUM, sessionOffset = offset
        )
        assertThat(at(0)).isEqualTo("Если бы я купил дом (se, avessi, …)")
        assertThat(at(1)).isEqualTo("Если бы я купил дом (…, avessi, comprato, …)")
        assertThat(at(2)).isEqualTo("Если бы я купил дом (…, comprato, casa)")
        // окно циклится, а не выходит за границы
        assertThat(at(3)).isEqualTo(at(0))
    }

    @Test
    fun medium_singleLemmaHintStaysWhole() {
        // резать нечего — подсказка из одной леммы показывается целиком
        val single = "Тот самый купленный дом — большой (comprato)"
        val out = HintCalculator.calculateEffectiveHints(
            promptRu = single, encounterCount = 1,
            hintLevel = HintLevel.MEDIUM, sessionOffset = 0
        )
        assertThat(out).isEqualTo(single)
    }

    @Test
    fun scheduler_stripsHintAfterThirdEncounter_evenOnEasy() {
        // расписание всё ещё главнее выбранного уровня
        assertThat(hint(HintLevel.EASY, encounters = 3)).isEqualTo("Я покупаю дом")
    }

    @Test
    fun scheduler_secondEncounterOnEasy_behavesAsMedium() {
        assertThat(hint(HintLevel.EASY, encounters = 2))
            .isEqualTo(hint(HintLevel.MEDIUM, encounters = 1))
    }

    @Test
    fun bossBattle_alwaysStripsHint() {
        val out = HintCalculator.calculateEffectiveHints(
            promptRu = prompt, encounterCount = 1,
            hintLevel = HintLevel.EASY, sessionOffset = 0, isBossBattle = true
        )
        assertThat(out).isEqualTo("Я покупаю дом")
    }

    @Test
    fun reviewMode_keepsHintOnEasy() {
        val out = HintCalculator.calculateEffectiveHints(
            promptRu = prompt, encounterCount = 99,
            hintLevel = HintLevel.EASY, sessionOffset = 0, isReviewMode = true
        )
        assertThat(out).isEqualTo(prompt)
    }
}
