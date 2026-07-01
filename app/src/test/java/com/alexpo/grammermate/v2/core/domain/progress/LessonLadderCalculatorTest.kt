package com.alexpo.grammermate.v2.core.domain.progress

import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.LessonMastery
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [LessonLadderCalculator] — метрик лесенки интервалов.
 *
 * Отличие от FlowerCalculator: daysSince считается С +1.
 *  - null/uniqueCardShows<=0/lastShow<=0 → все поля null;
 *  - daysSince = ((nowMs-lastShow)/DAY_MS) + 1;
 *  - просрочка если daysSince > expectedInterval → "Просрочка+N";
 *  - иначе buildIntervalLabel "a-b".
 */
class LessonLadderCalculatorTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")
    private val dayMs = SrsConstants.DAY_MS
    private val nowMs = 1000L * dayMs

    /** LessonMastery с заданными показами, шагом и lastShow. */
    private fun mastery(
        uniqueCardShows: Int = 5,
        lastShowDateMs: Long,
        intervalStepIndex: Int = 0,
    ): LessonMastery = LessonMastery(
        packId = packId,
        lessonId = lessonId,
        uniqueCardShows = uniqueCardShows,
        totalCardShows = uniqueCardShows,
        lastShowDateMs = lastShowDateMs,
        intervalStepIndex = intervalStepIndex,
        srsState = null,
        dueAtMs = 0L,
        completedAtMs = null,
        shownCardIds = emptySet(),
        cardEncounterCounts = emptyMap(),
    )

    // ── null / нет данных → все поля null ──────────────────────────────────

    @Test
    fun `calculate null mastery returns all null fields`() {
        val metrics = LessonLadderCalculator.calculate(null, nowMs)
        assertThat(metrics.uniqueCardShows).isNull()
        assertThat(metrics.daysSinceLastShow).isNull()
        assertThat(metrics.intervalLabel).isNull()
    }

    @Test
    fun `calculate zero unique card shows returns all null fields`() {
        val m = mastery(uniqueCardShows = 0, lastShowDateMs = nowMs - dayMs)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.uniqueCardShows).isNull()
        assertThat(metrics.intervalLabel).isNull()
    }

    @Test
    fun `calculate non-positive lastShowDateMs returns all null fields`() {
        val m = mastery(lastShowDateMs = 0L)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.daysSinceLastShow).isNull()
        assertThat(metrics.intervalLabel).isNull()
    }

    // ── daysSince = ((nowMs - lastShow)/DAY_MS) + 1 ────────────────────────

    @Test
    fun `calculate daysSince adds one to day difference`() {
        // lastShow был ровно 3 дня назад → (3 дня / DAY_MS)=3 + 1 = 4.
        val m = mastery(lastShowDateMs = nowMs - 3 * dayMs, intervalStepIndex = 0)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.daysSinceLastShow).isEqualTo(4)
        assertThat(metrics.uniqueCardShows).isEqualTo(5)
    }

    @Test
    fun `calculate daysSince same day is one`() {
        // lastShow сегодня → diff=0 +1 = 1.
        val m = mastery(lastShowDateMs = nowMs, intervalStepIndex = 0)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.daysSinceLastShow).isEqualTo(1)
    }

    @Test
    fun `calculate daysSince clamps negative to zero plus one`() {
        // lastShow в будущем (nowMs+day) → diff=-1 → coerceAtLeast(0)=0 +1 = 1.
        val m = mastery(lastShowDateMs = nowMs + dayMs, intervalStepIndex = 0)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.daysSinceLastShow).isEqualTo(1)
    }

    // ── Просрочка ──────────────────────────────────────────────────────────

    @Test
    fun `calculate returns overdue label when daysSince exceeds expected interval`() {
        // step=0 → expected=ladder[0]=1. daysSince=5 (>1) → просрочка +4.
        val m = mastery(lastShowDateMs = nowMs - 4 * dayMs, intervalStepIndex = 0)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        // daysSince = 4+1 = 5; overdue = 5-1 = 4.
        assertThat(metrics.intervalLabel).isEqualTo("Просрочка+4")
    }

    @Test
    fun `calculate overdue uses correct expected interval per step`() {
        // step=3 → expected=ladder[3]=7. lastShow 10 дней назад → daysSince=11 > 7 → overdue=4.
        val m = mastery(lastShowDateMs = nowMs - 10 * dayMs, intervalStepIndex = 3)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.intervalLabel).isEqualTo("Просрочка+4")
    }

    @Test
    fun `calculate no overdue when daysSince within expected interval`() {
        // step=3 → expected=7. daysSince=4 (<7) → НЕ просрочка → intervalLabel.
        val m = mastery(lastShowDateMs = nowMs - 3 * dayMs, intervalStepIndex = 3)
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.daysSinceLastShow).isEqualTo(4)
        assertThat(metrics.intervalLabel).isNotEqualTo("Просрочка+0")
        assertThat(metrics.intervalLabel).doesNotContain("Просрочка")
    }

    // ── buildIntervalLabel ─────────────────────────────────────────────────

    @Test
    fun `buildIntervalLabel empty ladder returns dash`() {
        assertThat(LessonLadderCalculator.buildIntervalLabel(5, emptyList())).isEqualTo("-")
    }

    @Test
    fun `buildIntervalLabel single element returns n-n`() {
        assertThat(LessonLadderCalculator.buildIntervalLabel(5, listOf(7))).isEqualTo("7-7")
    }

    @Test
    fun `buildIntervalLabel days within first bucket returns first range`() {
        // days <= ladder[0] → "ladder[0]-ladder[1]".
        val ladder = listOf(1, 2, 4, 7)
        assertThat(LessonLadderCalculator.buildIntervalLabel(1, ladder)).isEqualTo("1-2")
    }

    @Test
    fun `buildIntervalLabel days in middle bucket returns that range`() {
        // ladder = [1,2,4,7]; days=4 → 4<=4 → "2-4" (index где days<=ladder[i]=2).
        val ladder = listOf(1, 2, 4, 7)
        assertThat(LessonLadderCalculator.buildIntervalLabel(4, ladder)).isEqualTo("2-4")
    }

    @Test
    fun `buildIntervalLabel days beyond all returns last range`() {
        val ladder = listOf(1, 2, 4, 7)
        assertThat(LessonLadderCalculator.buildIntervalLabel(100, ladder)).isEqualTo("4-7")
    }

    @Test
    fun `buildIntervalLabel uses default ladder when not provided in calculate`() {
        // calculate без явного ladder использует SrsConstants.INTERVAL_LADDER_DAYS.
        val m = mastery(lastShowDateMs = nowMs - 3 * dayMs, intervalStepIndex = 5)
        // step=5 → expected=ladder[5]=14; daysSince=4 (<14) → buildIntervalLabel.
        val metrics = LessonLadderCalculator.calculate(m, nowMs)
        assertThat(metrics.intervalLabel).isNotNull()
        assertThat(metrics.intervalLabel).doesNotContain("Просрочка")
    }
}
