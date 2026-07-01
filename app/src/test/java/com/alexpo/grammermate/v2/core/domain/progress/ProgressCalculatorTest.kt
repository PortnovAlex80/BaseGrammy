package com.alexpo.grammermate.v2.core.domain.progress

import com.alexpo.grammermate.v2.core.domain.model.Chapter
import com.alexpo.grammermate.v2.core.domain.model.ChapterId
import com.alexpo.grammermate.v2.core.domain.model.FlowerState
import com.alexpo.grammermate.v2.core.domain.model.LessonId
import com.alexpo.grammermate.v2.core.domain.model.LessonMastery
import com.alexpo.grammermate.v2.core.domain.model.PackId
import com.alexpo.grammermate.v2.core.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты для [ChapterProgressCalculator] и [PackProgressCalculator].
 *
 * ChapterProgress:
 *  - started = uniqueCardShows > 0;
 *  - completed = completedAtMs != null (НЕ intervalStepIndex>=3);
 *  - lastAccessedMs = max lastShowDateMs.
 *
 * PackFlower:
 *  - depth = средняя mastery по начатым;
 *  - health по worst-case recency;
 *  - GONE/WILTED/WILTING/SEED/SPROUT/BLOOM.
 */
class ProgressCalculatorTest {

    private val packId = PackId("pack")
    private val chapterId = ChapterId("chapter")
    private val dayMs = SrsConstants.DAY_MS
    private val nowMs = 1000L * dayMs

    /** Глава с N уроками. */
    private fun chapter(lessonIds: List<LessonId>): Chapter = Chapter(
        id = chapterId,
        packId = packId,
        order = 0,
        title = "t",
        subtitle = null,
        storyFile = null,
        lessonIds = lessonIds,
    )

    /** LessonMastery. */
    private fun mastery(
        uniqueCardShows: Int = 0,
        lastShowDateMs: Long = 0L,
        intervalStepIndex: Int = 0,
        completedAtMs: Long? = null,
    ): LessonMastery = LessonMastery(
        packId = packId,
        lessonId = LessonId("unused"),
        uniqueCardShows = uniqueCardShows,
        totalCardShows = uniqueCardShows,
        lastShowDateMs = lastShowDateMs,
        intervalStepIndex = intervalStepIndex,
        srsState = null,
        dueAtMs = 0L,
        completedAtMs = completedAtMs,
        shownCardIds = emptySet(),
        cardEncounterCounts = emptyMap(),
    )

    // ════════════════════════════════════════════════════════════════════════
    //  ChapterProgressCalculator
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `chapter calculate empty lesson list returns zeros`() {
        val progress = ChapterProgressCalculator.calculate(chapter(emptyList()), emptyMap())
        assertThat(progress.lessonsStarted).isEqualTo(0)
        assertThat(progress.lessonsCompleted).isEqualTo(0)
        assertThat(progress.lastAccessedMs).isEqualTo(0L)
        assertThat(progress.totalLessons).isEqualTo(0)
    }

    @Test
    fun `chapter started counts lessons with unique card shows greater than zero`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val l3 = LessonId("l3")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 5),
            l2 to mastery(uniqueCardShows = 0),
            // l3 без mastery → не начат.
        )
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1, l2, l3)), states)
        assertThat(progress.lessonsStarted).isEqualTo(1)
        assertThat(progress.totalLessons).isEqualTo(3)
    }

    @Test
    fun `chapter completed counts lessons with completedAtMs not null`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 5, completedAtMs = 1000L),
            l2 to mastery(uniqueCardShows = 5, intervalStepIndex = 5, completedAtMs = null),
        )
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1, l2)), states)
        // Только l1 завершён (completedAtMs!=null), несмотря на intervalStepIndex=5 у l2.
        assertThat(progress.lessonsCompleted).isEqualTo(1)
    }

    @Test
    fun `chapter completed NOT triggered by high intervalStepIndex alone`() {
        // Доказательство инварианта: completed = completedAtMs!=null, НЕ step>=3.
        val l1 = LessonId("l1")
        val states = mapOf(l1 to mastery(uniqueCardShows = 10, intervalStepIndex = 9, completedAtMs = null))
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1)), states)
        assertThat(progress.lessonsCompleted).isEqualTo(0)
    }

    @Test
    fun `chapter lastAccessedMs is max lastShowDateMs across lessons`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val l3 = LessonId("l3")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 1, lastShowDateMs = 100L),
            l2 to mastery(uniqueCardShows = 1, lastShowDateMs = 500L),
            l3 to mastery(uniqueCardShows = 1, lastShowDateMs = 300L),
        )
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1, l2, l3)), states)
        assertThat(progress.lastAccessedMs).isEqualTo(500L)
    }

    @Test
    fun `chapter progress ratio is completed over total`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val l3 = LessonId("l3")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 5, completedAtMs = 1L),
            l2 to mastery(uniqueCardShows = 5, completedAtMs = 1L),
        )
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1, l2, l3)), states)
        assertThat(progress.totalLessons).isEqualTo(3)
        assertThat(progress.lessonsCompleted).isEqualTo(2)
        assertThat(progress.progress).isWithin(1e-6f).of(2f / 3f)
    }

    @Test
    fun `chapter lessons without mastery count as neither started nor completed`() {
        val l1 = LessonId("l1")
        val progress = ChapterProgressCalculator.calculate(chapter(listOf(l1)), emptyMap())
        assertThat(progress.lessonsStarted).isEqualTo(0)
        assertThat(progress.lessonsCompleted).isEqualTo(0)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PackProgressCalculator
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `pack flower empty lesson list returns SEED with full health`() {
        val flower = PackProgressCalculator.calculatePackFlower(emptyList(), emptyMap(), nowMs)
        assertThat(flower.flowerState).isEqualTo(FlowerState.SEED)
        assertThat(flower.depth).isWithin(1e-6f).of(0f)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(1f)
        assertThat(flower.totalLessons).isEqualTo(0)
        assertThat(flower.completedLessons).isEqualTo(0)
    }

    @Test
    fun `pack flower no started lessons returns SEED`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 0),
            l2 to mastery(uniqueCardShows = 0),
        )
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1, l2), states, nowMs)
        assertThat(flower.flowerState).isEqualTo(FlowerState.SEED)
        assertThat(flower.depth).isWithin(1e-6f).of(0f)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `pack flower depth is average mastery over started lessons`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 75, lastShowDateMs = nowMs),  // depth 0.5
            l2 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs), // depth 1.0
        )
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1, l2), states, nowMs)
        // среднее (0.5 + 1.0)/2 = 0.75.
        assertThat(flower.depth).isWithin(1e-4f).of(0.75f)
    }

    @Test
    fun `pack flower BLOOM when full depth and fresh show`() {
        val l1 = LessonId("l1")
        val states = mapOf(l1 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        // depth=1.0, daysSince=0 → health=1.0 → BLOOM.
        assertThat(flower.flowerState).isEqualTo(FlowerState.BLOOM)
        assertThat(flower.depth).isWithin(1e-6f).of(1f)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(1f)
        assertThat(flower.scaleMultiplier).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `pack flower GONE when last show over 90 days ago`() {
        val l1 = LessonId("l1")
        // daysSince БЕЗ +1: 95 дней > 90 → GONE.
        val states = mapOf(l1 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs - 95 * dayMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        assertThat(flower.flowerState).isEqualTo(FlowerState.GONE)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(0f)
    }

    @Test
    fun `pack flower SPROUT at mid depth with fresh show`() {
        val l1 = LessonId("l1")
        val states = mapOf(l1 to mastery(uniqueCardShows = 75, lastShowDateMs = nowMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        // depth=0.5 (0.33..0.66) → SPROUT.
        assertThat(flower.flowerState).isEqualTo(FlowerState.SPROUT)
    }

    @Test
    fun `pack flower SEED at low depth with fresh show`() {
        val l1 = LessonId("l1")
        // depth = 10/150 ≈ 0.066 < 0.33 → SEED.
        val states = mapOf(l1 to mastery(uniqueCardShows = 10, lastShowDateMs = nowMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        assertThat(flower.flowerState).isEqualTo(FlowerState.SEED)
    }

    @Test
    fun `pack flower completedLessons counts completedAtMs not null`() {
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val l3 = LessonId("l3")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 5, completedAtMs = 1L),
            l2 to mastery(uniqueCardShows = 5, completedAtMs = 1L),
            l3 to mastery(uniqueCardShows = 5, completedAtMs = null),
        )
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1, l2, l3), states, nowMs)
        assertThat(flower.completedLessons).isEqualTo(2)
        assertThat(flower.totalLessons).isEqualTo(3)
    }

    @Test
    fun `pack flower depth clamps each lesson depth to at most one`() {
        val l1 = LessonId("l1")
        // uniqueCardShows > 150 → lesson depth coerceMost(1.0).
        val states = mapOf(l1 to mastery(uniqueCardShows = 300, lastShowDateMs = nowMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        assertThat(flower.depth).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `pack flower scale is depth times health clamped to 0_5 min`() {
        val l1 = LessonId("l1")
        // depth=0, но начат → scale = 0*1 = 0 → coerceIn(0.5,1.0) = 0.5.
        val states = mapOf(l1 to mastery(uniqueCardShows = 1, lastShowDateMs = nowMs))
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1), states, nowMs)
        assertThat(flower.scaleMultiplier).isAtLeast(0.5f)
    }

    @Test
    fun `pack flower health uses worst-case recency (max lastShowDateMs)`() {
        // Самый свежий показ (max lastShowDateMs) определяет recency пака.
        // Сравниваем с «оба свежих»: worst-case = max, поэтому health пака равен
        // health одного свежего урока, а не усреднённого со старым.
        val l1 = LessonId("l1")
        val l2 = LessonId("l2")
        val states = mapOf(
            l1 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs - 50 * dayMs), // старый
            l2 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs - 1 * dayMs),  // свежий (вчера)
        )
        val flower = PackProgressCalculator.calculatePackFlower(listOf(l1, l2), states, nowMs)

        // Эталон: только свежий урок → тот же recency (max = вчера) → то же health.
        val refStates = mapOf(l2 to mastery(uniqueCardShows = 150, lastShowDateMs = nowMs - 1 * dayMs))
        val refFlower = PackProgressCalculator.calculatePackFlower(listOf(l2), refStates, nowMs)

        assertThat(flower.healthPercent).isWithin(1e-5f).of(refFlower.healthPercent)
        // Свежий показ (1 день) при step 0 → health < 1 (overdue=0? нет: expected=1, days=1 <=1 → 1.0).
    }
}
