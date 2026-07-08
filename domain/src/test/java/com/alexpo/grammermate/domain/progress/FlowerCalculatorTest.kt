package com.alexpo.grammermate.domain.progress

import com.alexpo.grammermate.domain.model.FlowerState
import com.alexpo.grammermate.domain.model.LessonId
import com.alexpo.grammermate.domain.model.LessonMastery
import com.alexpo.grammermate.domain.model.PackId
import com.alexpo.grammermate.domain.srs.SrsConstants
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.exp

/**
 * Регрессионные тесты для [SpacedRepetitionFormulas] и [FlowerCalculator].
 *
 * Покрывает математику кривой забывания Эббингауза:
 *  - S(n) = S0 * multiplier^n, capped at index 9 (S0=0.9, multiplier=2.2);
 *  - R = e^(-t/S), R(0)=1.0, монотонно убывает;
 *  - health: 1.0 в пределах интервала, decay после, 0 при >=90 дней, floor WILTED;
 *  - FlowerCalculator.calculate: SEED / GONE / переходы состояний;
 *  - getEmoji mapping.
 */
class FlowerCalculatorTest {

    private val packId = PackId("pack")
    private val lessonId = LessonId("lesson")
    private val dayMs = SrsConstants.DAY_MS

    // nowMs = 1000 дней от epoch (произвольная детерминированная точка отсчёта).
    private val nowMs = 1000L * dayMs

    /** LessonMastery с заданным числом показов и дней с последнего показа. */
    private fun mastery(
        uniqueCardShows: Int,
        daysSinceLastShow: Long,
        intervalStepIndex: Int = 0,
    ): LessonMastery {
        val lastShowDateMs = nowMs - daysSinceLastShow * dayMs
        return LessonMastery(
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
    }

    // ── SpacedRepetitionFormulas.calculateStability ───────────────────────

    @Test
    fun `calculateStability at index zero returns base stability`() {
        // S(0) = 0.9 (ноль умножений).
        assertThat(SpacedRepetitionFormulas.calculateStability(0))
            .isWithin(1e-9).of(SrsConstants.BASE_STABILITY_DAYS)
    }

    @Test
    fun `calculateStability grows as base times multiplier to the n`() {
        // S(n) = 0.9 * 2.2^n для n<=9.
        val expected = SrsConstants.BASE_STABILITY_DAYS *
            Math.pow(SrsConstants.STABILITY_MULTIPLIER.toDouble(), 1.0)
        assertThat(SpacedRepetitionFormulas.calculateStability(1))
            .isWithin(1e-9).of(expected)
    }

    @Test
    fun `calculateStability at index nine equals base times multiplier to nine`() {
        val expected = SrsConstants.BASE_STABILITY_DAYS *
            Math.pow(SrsConstants.STABILITY_MULTIPLIER.toDouble(), 9.0)
        assertThat(SpacedRepetitionFormulas.calculateStability(9))
            .isWithin(1e-9).of(expected)
    }

    @Test
    fun `calculateStability capped at index 9 beyond ladder size`() {
        // Для n>=10 (ladder.size=10) clamp к 9 умножениям.
        val capped = SpacedRepetitionFormulas.calculateStability(9)
        val beyond = SpacedRepetitionFormulas.calculateStability(15)
        assertThat(beyond).isWithin(1e-9).of(capped)
    }

    @Test
    fun `calculateStability negative index returns base stability`() {
        assertThat(SpacedRepetitionFormulas.calculateStability(-1))
            .isWithin(1e-9).of(SrsConstants.BASE_STABILITY_DAYS)
    }

    // ── SpacedRepetitionFormulas.calculateRetention ───────────────────────

    @Test
    fun `calculateRetention at zero days is one`() {
        assertThat(SpacedRepetitionFormulas.calculateRetention(0, 0))
            .isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun `calculateRetention negative days is one`() {
        assertThat(SpacedRepetitionFormulas.calculateRetention(-5, 3))
            .isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun `calculateRetention follows Ebbinghaus formula`() {
        // R = e^(-t/S).
        val t = 3
        val step = 2
        val s = SpacedRepetitionFormulas.calculateStability(step)
        val expected = exp(-t.toDouble() / s).toFloat()
        assertThat(SpacedRepetitionFormulas.calculateRetention(t, step))
            .isWithin(1e-5f).of(expected)
    }

    @Test
    fun `calculateRetention monotonically decreases with days`() {
        val step = 0
        var prev = 2.0f
        for (days in 0..20) {
            val r = SpacedRepetitionFormulas.calculateRetention(days, step)
            assertThat(r).isAtMost(prev)
            prev = r
        }
    }

    @Test
    fun `calculateRetention is clamped to range zero to one`() {
        assertThat(SpacedRepetitionFormulas.calculateRetention(0, 0)).isAtMost(1.0f)
        // Очень далёкий день → стремится к 0, но не отрицательный.
        assertThat(SpacedRepetitionFormulas.calculateRetention(1_000_000, 0)).isAtLeast(0f)
    }

    // ── SpacedRepetitionFormulas.calculateHealthPercent ───────────────────

    @Test
    fun `calculateHealthPercent at zero or negative days is one`() {
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(0, 0))
            .isWithin(1e-6f).of(1.0f)
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(-1, 3))
            .isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun `calculateHealthPercent at or beyond gone threshold is zero`() {
        // days >= GONE_THRESHOLD_DAYS (90) → 0.
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(90, 0))
            .isWithin(1e-6f).of(0f)
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(200, 5))
            .isWithin(1e-6f).of(0f)
    }

    @Test
    fun `calculateHealthPercent is one within expected interval`() {
        // step=0 → expected=ladder[0]=1. days<=1 → 1.0.
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(1, 0))
            .isWithin(1e-6f).of(1.0f)
        // step=3 → expected=ladder[3]=7. days=7 → 1.0.
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(7, 3))
            .isWithin(1e-6f).of(1.0f)
    }

    @Test
    fun `calculateHealthPercent decays after expected interval and is floored at wilted`() {
        // step=0, expected=1. days=5 → overdue=4.
        val health = SpacedRepetitionFormulas.calculateHealthPercent(5, 0)
        // decay = e^(-overdue/S), health = WILTED + (1-WILTED)*decay.
        assertThat(health).isAtLeast(SrsConstants.WILTED_THRESHOLD)
        assertThat(health).isLessThan(1.0f)
    }

    @Test
    fun `calculateHealthPercent never drops below wilted threshold before gone`() {
        // Для любого days < 90 — health >= WILTED_THRESHOLD (floor).
        for (days in 1..89) {
            val health = SpacedRepetitionFormulas.calculateHealthPercent(days, 0)
            assertThat(health).isAtLeast(SrsConstants.WILTED_THRESHOLD - 1e-6f)
        }
    }

    @Test
    fun `calculateHealthPercent uses last ladder element for step beyond ladder`() {
        // step=15 (>=size) → expected=ladder.last()=56.
        // days=56 → в пределах → 1.0.
        assertThat(SpacedRepetitionFormulas.calculateHealthPercent(56, 15))
            .isWithin(1e-6f).of(1.0f)
    }

    // ── FlowerCalculator.calculate: null / пусто → SEED ───────────────────

    @Test
    fun `calculate null mastery returns SEED with full health`() {
        val flower = FlowerCalculator.calculate(mastery = null, totalCardsInLesson = 5, nowMs = nowMs)
        assertThat(flower.state).isEqualTo(FlowerState.SEED)
        assertThat(flower.masteryPercent).isWithin(1e-6f).of(0f)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(1f)
        assertThat(flower.scaleMultiplier).isWithin(1e-6f).of(0.5f)
    }

    @Test
    fun `calculate zero unique card shows returns SEED`() {
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 0, daysSinceLastShow = 1),
            totalCardsInLesson = 5,
            nowMs = nowMs,
        )
        assertThat(flower.state).isEqualTo(FlowerState.SEED)
        assertThat(flower.masteryPercent).isWithin(1e-6f).of(0f)
    }

    // ── FlowerCalculator.calculate: GONE при > 90 дней ─────────────────────

    @Test
    fun `calculate more than 90 days since last show returns GONE`() {
        // daysSince БЕЗ +1: 91 день строго > 90 → GONE.
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 150, daysSinceLastShow = 91),
            totalCardsInLesson = 150,
            nowMs = nowMs,
        )
        assertThat(flower.state).isEqualTo(FlowerState.GONE)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(0f)
        assertThat(flower.masteryPercent).isWithin(1e-6f).of(0f)
    }

    // ── FlowerCalculator.calculate: BLOOM (полное mastery, свежий показ) ──

    @Test
    fun `calculate full mastery and fresh show returns BLOOM`() {
        // uniqueCardShows=150 → mastery=1.0; days=1 (<=expected=1) → health=1.0.
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 150, daysSinceLastShow = 1, intervalStepIndex = 0),
            totalCardsInLesson = 150,
            nowMs = nowMs,
        )
        assertThat(flower.state).isEqualTo(FlowerState.BLOOM)
        assertThat(flower.masteryPercent).isWithin(1e-6f).of(1f)
        assertThat(flower.healthPercent).isWithin(1e-6f).of(1f)
        assertThat(flower.scaleMultiplier).isWithin(1e-6f).of(1f)
    }

    @Test
    fun `calculate mastery percent saturates at threshold`() {
        // uniqueCardShows > 150 → mastery всё равно 1.0 (coerceIn).
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 300, daysSinceLastShow = 1),
            totalCardsInLesson = 150,
            nowMs = nowMs,
        )
        assertThat(flower.masteryPercent).isWithin(1e-6f).of(1f)
    }

    // ── FlowerCalculator.calculate: SPROUT (среднее mastery) ──────────────

    @Test
    fun `calculate mid mastery fresh show returns SPROUT`() {
        // uniqueCardShows=75 → mastery=0.5 (0.33..0.66) → SPROUT (health=1.0).
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 75, daysSinceLastShow = 1),
            totalCardsInLesson = 150,
            nowMs = nowMs,
        )
        assertThat(flower.state).isEqualTo(FlowerState.SPROUT)
    }

    // ── FlowerCalculator.determineFlowerState (детерминированные переходы) ─

    @Test
    fun `determineFlowerState days over gone threshold returns GONE`() {
        assertThat(FlowerCalculator.determineFlowerState(1.0f, 1.0f, 91))
            .isEqualTo(FlowerState.GONE)
    }

    @Test
    fun `determineFlowerState health at wilted threshold returns WILTED`() {
        // health <= WILTED (0.5) строго.
        assertThat(FlowerCalculator.determineFlowerState(1.0f, SrsConstants.WILTED_THRESHOLD, 5))
            .isEqualTo(FlowerState.WILTED)
    }

    @Test
    fun `determineFlowerState health below wilted returns WILTED`() {
        assertThat(FlowerCalculator.determineFlowerState(1.0f, 0.3f, 5))
            .isEqualTo(FlowerState.WILTED)
    }

    @Test
    fun `determineFlowerState health between wilted and one returns WILTING`() {
        assertThat(FlowerCalculator.determineFlowerState(1.0f, 0.7f, 5))
            .isEqualTo(FlowerState.WILTING)
    }

    @Test
    fun `determineFlowerState full health low mastery returns SEED`() {
        assertThat(FlowerCalculator.determineFlowerState(0.1f, 1.0f, 1))
            .isEqualTo(FlowerState.SEED)
    }

    @Test
    fun `determineFlowerState full health mid mastery returns SPROUT`() {
        assertThat(FlowerCalculator.determineFlowerState(0.5f, 1.0f, 1))
            .isEqualTo(FlowerState.SPROUT)
    }

    @Test
    fun `determineFlowerState full health high mastery returns BLOOM`() {
        assertThat(FlowerCalculator.determineFlowerState(0.9f, 1.0f, 1))
            .isEqualTo(FlowerState.BLOOM)
    }

    // ── FlowerCalculator.getEmoji ─────────────────────────────────────────

    @Test
    fun `getEmoji returns correct emoji for each state`() {
        assertThat(FlowerCalculator.getEmoji(FlowerState.LOCKED)).isEqualTo("🔒")
        assertThat(FlowerCalculator.getEmoji(FlowerState.SEED)).isEqualTo("🌱")
        assertThat(FlowerCalculator.getEmoji(FlowerState.SPROUT)).isEqualTo("🌿")
        assertThat(FlowerCalculator.getEmoji(FlowerState.BLOOM)).isEqualTo("🌸")
        assertThat(FlowerCalculator.getEmoji(FlowerState.WILTING)).isEqualTo("🥀")
        assertThat(FlowerCalculator.getEmoji(FlowerState.WILTED)).isEqualTo("🍂")
        assertThat(FlowerCalculator.getEmoji(FlowerState.GONE)).isEqualTo("⚫")
    }

    @Test
    fun `getEmojiWithScale returns emoji and scale pair`() {
        val flower = FlowerCalculator.calculate(
            mastery = mastery(uniqueCardShows = 150, daysSinceLastShow = 1),
            totalCardsInLesson = 150,
            nowMs = nowMs,
        )
        val (emoji, scale) = FlowerCalculator.getEmojiWithScale(flower)
        assertThat(emoji).isEqualTo("🌸")
        assertThat(scale).isEqualTo(flower.scaleMultiplier)
    }
}
