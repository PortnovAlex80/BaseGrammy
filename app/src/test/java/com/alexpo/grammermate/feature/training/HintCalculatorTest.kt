package com.alexpo.grammermate.feature.training

import com.alexpo.grammermate.data.HintLevel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HintCalculatorTest {

    private val promptWithHints = "я говорю (dire) правду (verità)"
    private val promptNoHints = "я говорю правду"
    private val promptNoParentheticals = "я иду домой"

    @Test
    fun easyFirstEncounter_showsAllHints() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 1,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0
        )
        assertEquals("я говорю (dire) правду (verità)", result)
    }

    @Test
    fun hardFirstEncounter_stripsAllHints() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 1,
            hintLevel = HintLevel.HARD,
            sessionOffset = 0
        )
        assertEquals("я говорю правду", result)
    }

    @Test
    fun mediumSecondEncounter_stripsHalf() {
        // With offset=0: index 0 (dire) shown, index 1 (verità) hidden
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 0
        )
        // (dire) at index 0: (0+0)%2==0 -> shown
        // (verità) at index 1: (1+0)%2==1 -> hidden
        assertEquals("я говорю (dire) правду", result)
    }

    @Test
    fun easyThirdEncounter_stripsAll_schedulerStricter() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 3,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0
        )
        assertEquals("я говорю правду", result)
    }

    @Test
    fun hardThirdEncounter_stripsAll() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 3,
            hintLevel = HintLevel.HARD,
            sessionOffset = 0
        )
        assertEquals("я говорю правду", result)
    }

    @Test
    fun bossBattle_alwaysStripsAll() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 1,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0,
            isBossBattle = true
        )
        assertEquals("я говорю правду", result)
    }

    @Test
    fun noParentheticals_returnsUnmodified() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptNoParentheticals,
            encounterCount = 1,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0
        )
        assertEquals("я иду домой", result)
    }

    @Test
    fun mediumWithOffset_flipsWhichHintsShown() {
        // With offset=1: index 0 hidden, index 1 shown (reversed from offset=0)
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 1
        )
        // (dire) at index 0: (0+1)%2==1 -> hidden
        // (verità) at index 1: (1+1)%2==0 -> shown
        assertEquals("я говорю правду (verità)", result)
    }

    @Test
    fun easySecondEncounter_stripsHalf_schedulerStricter() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 2,
            hintLevel = HintLevel.EASY,
            sessionOffset = 0
        )
        // Scheduler says 50% -> EASY says all -> min is 50%
        assertEquals("я говорю (dire) правду", result)
    }

    @Test
    fun mediumFirstEncounter_showsAll_userNotStricter() {
        val result = HintCalculator.calculateEffectiveHints(
            promptRu = promptWithHints,
            encounterCount = 1,
            hintLevel = HintLevel.MEDIUM,
            sessionOffset = 0
        )
        // Scheduler says all -> MEDIUM says 50% -> min is 50%
        assertEquals("я говорю (dire) правду", result)
    }
}
