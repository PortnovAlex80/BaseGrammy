package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.model.LanguageId
import com.alexpo.grammermate.domain.model.PracticeType
import com.alexpo.grammermate.domain.model.StreakData
import com.alexpo.grammermate.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [StreakCalculator] — обновления серии дней.
 *
 * Инварианты (с учётом унификации v1-бага):
 *  - lastCompletionDateMs == null → currentStreak = 1;
 *  - тот же день (diff==0) → без изменений;
 *  - вчера (diff==1) → +1;
 *  - разрыв ≥ 2 дней (diff>=2) → сброс на 1 (НЕ 0!);
 *  - longestStreak = max(longest, newCurrent);
 *  - день = epochMs / DAY_MS.
 */
class StreakCalculatorTest {

    private val dayMs = SrsConstants.DAY_MS
    private val nowMs = 10_000L * dayMs

    private fun streak(
        current: Int = 0,
        longest: Int = 0,
        lastCompletionDateMs: Long? = null,
        totalSubLessons: Int = 0,
    ): StreakData = StreakData(
        languageId = LanguageId("it"),
        currentStreak = current,
        longestStreak = longest,
        lastCompletionDateMs = lastCompletionDateMs,
        totalSubLessonsCompleted = totalSubLessons,
        completedTypesToday = emptySet(),
        todayFireCount = 0,
        lastFireDateMs = null,
    )

    // ── lastCompletion == null → 1 ─────────────────────────────────────────

    @Test
    fun `computeNextStreak first completion sets current to one`() {
        val result = StreakCalculator.computeNextStreak(streak(), nowMs)
        assertThat(result.currentStreak).isEqualTo(1)
        assertThat(result.longestStreak).isEqualTo(1)
        assertThat(result.lastCompletionDateMs).isEqualTo(nowMs)
        assertThat(result.totalSubLessonsCompleted).isEqualTo(1)
    }

    // ── тот же день (diff==0) → без изменений ──────────────────────────────

    @Test
    fun `computeNextStreak same day keeps current streak unchanged`() {
        val current = streak(current = 5, longest = 7, lastCompletionDateMs = nowMs)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(5)
        // longest сохранён.
        assertThat(result.longestStreak).isEqualTo(7)
    }

    @Test
    fun `computeNextStreak same day still increments totalSubLessons`() {
        val current = streak(current = 3, lastCompletionDateMs = nowMs, totalSubLessons = 10)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.totalSubLessonsCompleted).isEqualTo(11)
    }

    // ── вчера (diff==1) → +1 ───────────────────────────────────────────────

    @Test
    fun `computeNextStreak previous day increments current streak`() {
        val yesterday = nowMs - dayMs
        val current = streak(current = 4, longest = 6, lastCompletionDateMs = yesterday)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(5)
        assertThat(result.longestStreak).isEqualTo(6) // 5 < 6 → не обновлён
    }

    @Test
    fun `computeNextStreak previous day updates longest when exceeded`() {
        val yesterday = nowMs - dayMs
        val current = streak(current = 5, longest = 5, lastCompletionDateMs = yesterday)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(6)
        assertThat(result.longestStreak).isEqualTo(6) // новый рекорд
    }

    // ── разрыв ≥ 2 дней → сброс на 1 (НЕ 0) ────────────────────────────────

    @Test
    fun `computeNextStreak two day gap resets current to one not zero`() {
        // Доказательство унификации v1-бага: сброс на 1, не на 0.
        val twoDaysAgo = nowMs - 2 * dayMs
        val current = streak(current = 10, longest = 12, lastCompletionDateMs = twoDaysAgo)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(1)
        assertThat(result.longestStreak).isEqualTo(12) // рекорд сохранён
    }

    @Test
    fun `computeNextStreak large gap resets current to one`() {
        val farPast = nowMs - 100 * dayMs
        val current = streak(current = 50, longest = 99, lastCompletionDateMs = farPast)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(1)
    }

    @Test
    fun `computeNextStreak reset to one then next day continues from one`() {
        // Цепочка: gap (diff>=2) → reset на 1, затем вчера (diff==1) → +1 = 2.
        val farPast = nowMs - 30 * dayMs
        val reset = StreakCalculator.computeNextStreak(
            streak(current = 8, lastCompletionDateMs = farPast),
            nowMs - 2 * dayMs, // два дня назад: diff=30-2>=2 → reset на 1
        )
        assertThat(reset.currentStreak).isEqualTo(1)
        // Затем вчера от момента reset → diff==1 → продолжение серии → 2.
        val next = StreakCalculator.computeNextStreak(reset, nowMs - dayMs)
        assertThat(next.currentStreak).isEqualTo(2)
    }

    // ── lastCompletionDateMs всегда обновляется на nowMs ───────────────────

    @Test
    fun `computeNextStreak always updates lastCompletionDateMs to nowMs`() {
        val current = streak(current = 3, longest = 3, lastCompletionDateMs = nowMs - dayMs)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.lastCompletionDateMs).isEqualTo(nowMs)
    }

    // ── longestStreak = max ────────────────────────────────────────────────

    @Test
    fun `computeNextStreak longestStreak tracks maximum across reset`() {
        // После сброса на 1 при рекорде 20 — longest остаётся 20.
        val current = streak(current = 20, longest = 20, lastCompletionDateMs = nowMs - 30 * dayMs)
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.currentStreak).isEqualTo(1)
        assertThat(result.longestStreak).isEqualTo(20)
    }

    @Test
    fun `computeNextStreak first completion longestStreak equals one`() {
        val result = StreakCalculator.computeNextStreak(streak(longest = 0), nowMs)
        assertThat(result.longestStreak).isEqualTo(1)
    }

    // ── День = epochMs / DAY_MS ────────────────────────────────────────────

    @Test
    fun `computeNextStreak day boundary uses epochMs divided by DAY_MS`() {
        // nowMs в пределах того же календарного дня что и lastCompletion (по dayMs),
        // но с разницей меньше дня → diff=0 → без изменений.
        val sameDayLater = nowMs + 5L // несколько мс спустя — тот же день
        val current = streak(current = 2, lastCompletionDateMs = nowMs)
        val result = StreakCalculator.computeNextStreak(current, sameDayLater)
        assertThat(result.currentStreak).isEqualTo(2)
    }

    @Test
    fun `computeNextStreak preserves non-streak fields`() {
        // completedTypesToday/todayFireCount/lastFireDateMs сохраняются как есть.
        val current = StreakData(
            languageId = LanguageId("it"),
            currentStreak = 1,
            longestStreak = 1,
            lastCompletionDateMs = nowMs - dayMs,
            totalSubLessonsCompleted = 5,
            completedTypesToday = setOf(PracticeType.VERB),
            todayFireCount = 3,
            lastFireDateMs = 999L,
        )
        val result = StreakCalculator.computeNextStreak(current, nowMs)
        assertThat(result.completedTypesToday).containsExactly(PracticeType.VERB)
        assertThat(result.todayFireCount).isEqualTo(3)
        assertThat(result.lastFireDateMs).isEqualTo(999L)
    }

    @Test
    fun `computeNextStreak builds a streak across consecutive days`() {
        // Симуляция 3-дневной серии.
        var s = streak()
        s = StreakCalculator.computeNextStreak(s, 1L * dayMs)       // день 1 → current=1
        s = StreakCalculator.computeNextStreak(s, 2L * dayMs)       // день 2 → current=2
        s = StreakCalculator.computeNextStreak(s, 3L * dayMs)       // день 3 → current=3
        assertThat(s.currentStreak).isEqualTo(3)
        assertThat(s.longestStreak).isEqualTo(3)
        assertThat(s.totalSubLessonsCompleted).isEqualTo(3)
    }
}
