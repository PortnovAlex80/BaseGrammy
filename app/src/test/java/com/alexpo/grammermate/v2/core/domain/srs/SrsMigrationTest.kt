package com.alexpo.grammermate.v2.core.domain.srs

import com.alexpo.grammermate.v2.core.domain.model.SrsRating
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Регрессионные тесты [SrsMigration] — миграции с legacy-модели v1
 * (дискретный `intervalStepIndex` 0..9 по лестнице дней) на непрерывную
 * FSRS v6 модель DSR.
 *
 * Ключевая идея миграции: при `requestRetention = 0.9` интервал FSRS ≈ S,
 * поэтому старый «ожидаемый интервал в днях» трактуется как приближённое S,
 * а карточка помещается в REVIEW (или LEARNING для step 0).
 *
 * Legacy лестница: `[1, 2, 4, 7, 10, 14, 20, 28, 42, 56]`.
 */
class SrsMigrationTest {

    private val now = 1_000_000_000L

    @Test
    fun `LEGACY_INTERVAL_LADDER_DAYS matches v1 ladder`() {
        assertThat(SrsMigration.LEGACY_INTERVAL_LADDER_DAYS)
            .containsExactly(1, 2, 4, 7, 10, 14, 20, 28, 42, 56).inOrder()
    }

    /** step 0 → LEARNING, S = лестница[0] = 1 день. */
    @Test
    fun `fromLegacyIntervalStep 0 maps to LEARNING with S = ladder 0`() {
        val card = SrsMigration.fromLegacyIntervalStep(0, now)
        assertThat(card.state).isEqualTo(SrsMemoryState.LEARNING)
        assertThat(card.stability).isEqualTo(1.0) // лестница[0]
        assertThat(card.difficulty).isEqualTo(5.0) // нейтральная
        assertThat(card.lastReviewMs).isEqualTo(now)
        assertThat(card.dueAtMs).isEqualTo(now)
        assertThat(card.reps).isEqualTo(0)
        assertThat(card.lapses).isEqualTo(0)
    }

    /** step 9 → REVIEW, S ≈ лестница[9] = 56 дней. */
    @Test
    fun `fromLegacyIntervalStep 9 maps to REVIEW with S = 56 days`() {
        val card = SrsMigration.fromLegacyIntervalStep(9, now)
        assertThat(card.state).isEqualTo(SrsMemoryState.REVIEW)
        assertThat(card.stability).isEqualTo(56.0)
        assertThat(card.difficulty).isEqualTo(5.0)
        assertThat(card.reps).isEqualTo(9)
    }

    /** Все step 0..9 маппятся в правильные stability из лестницы. */
    @Test
    fun `fromLegacyIntervalStep maps each step to correct stability`() {
        for (step in 0..9) {
            val card = SrsMigration.fromLegacyIntervalStep(step, now)
            val expectedS = SrsMigration.LEGACY_INTERVAL_LADDER_DAYS[step].toDouble()
            assertThat(card.stability).isEqualTo(expectedS)
            // step 0 → LEARNING, >=1 → REVIEW
            val expectedState = if (step == 0) SrsMemoryState.LEARNING else SrsMemoryState.REVIEW
            assertThat(card.state).isEqualTo(expectedState)
            assertThat(card.reps).isEqualTo(step)
        }
    }

    /** Stability монотонно растёт с ростом step (прогресс обучения сохраняется). */
    @Test
    fun `migrated stability is monotonically increasing with step`() {
        val stabilities = (0..9).map { SrsMigration.fromLegacyIntervalStep(it, now).stability }
        for (i in 1 until stabilities.size) {
            assertThat(stabilities[i]).isGreaterThan(stabilities[i - 1])
        }
    }

    /** Отрицательный step → NEW (карточка не показывалась): нулевые счётчики, S/D=0. */
    @Test
    fun `fromLegacyIntervalStep negative maps to NEW`() {
        val card = SrsMigration.fromLegacyIntervalStep(-1, now)
        assertThat(card.state).isEqualTo(SrsMemoryState.NEW)
        assertThat(card.stability).isEqualTo(0.0)
        assertThat(card.difficulty).isEqualTo(0.0)
        assertThat(card.lastReviewMs).isEqualTo(0L)
        assertThat(card.reps).isEqualTo(0)
        assertThat(card.lapses).isEqualTo(0)
    }

    /** step за пределами лестницы (например 15) clamp'ится к последнему шагу (56 дней). */
    @Test
    fun `fromLegacyIntervalStep beyond ladder clamps to last step`() {
        val card = SrsMigration.fromLegacyIntervalStep(15, now)
        assertThat(card.stability).isEqualTo(56.0) // последний элемент лестницы
        assertThat(card.state).isEqualTo(SrsMemoryState.REVIEW)
    }

    /** Перегрузка без now использует now = 0 (карточка «просрочена с начала эпохи»). */
    @Test
    fun `fromLegacyIntervalStep single-arg overload uses now 0`() {
        val card = SrsMigration.fromLegacyIntervalStep(3)
        assertThat(card.stability).isEqualTo(7.0) // лестница[3]
        assertThat(card.lastReviewMs).isEqualTo(0L)
        assertThat(card.dueAtMs).isEqualTo(0L)
    }

    /**
     * Сквозной тест миграции: мигрированная REVIEW-карточка (step 5, S=14)
     * корректно обрабатывается [SrsScheduler.schedule] и даёт разумный
     * следующий интервал ≈ S при GOOD.
     *
     * Это доказывает, что мигрированное состояние «совместимо» с движком v2.
     */
    @Test
    fun `migrated REVIEW card is schedulable and preserves pace`() {
        val scheduler = SrsScheduler()
        val params = SrsParams()
        val migrated = SrsMigration.fromLegacyIntervalStep(5, now) // S=14, REVIEW
        assertThat(migrated.stability).isEqualTo(14.0)

        // ответ GOOD через 14 дней → новый интервал должен быть в районе 14+ дней
        val after = scheduler.schedule(
            state = migrated,
            rating = SrsRating.GOOD,
            now = now + 14L * 24 * 60 * 60 * 1000,
            params = params,
        )
        val newIntervalDays = (after.dueAtMs - after.lastReviewMs) / (24 * 60 * 60 * 1000L)
        assertThat(after.stability).isGreaterThan(14.0) // GOOD растит stability
        assertThat(newIntervalDays.toInt()).isAtLeast(14) // темп не потерян
    }

    /** Мигрированная карточка имеет нейтральную сложность 5.0 (середина шкалы). */
    @Test
    fun `migrated card has neutral difficulty 5_0`() {
        for (step in 0..9) {
            assertThat(SrsMigration.fromLegacyIntervalStep(step, now).difficulty).isEqualTo(5.0)
        }
    }
}
